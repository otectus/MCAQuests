package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.McaQuestsConfig.ProfessionMatchingMode;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.data.QuestRegistry;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.network.QuestMenuDataS2CPacket;
import dev.otectus.mcaquests.network.QuestNetwork;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.reward.FavorReward;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Server-authoritative quest controller: builds the menu view, and handles accept / decline /
 * turn-in / abandon (spec sections 5, 18, 19, 26). Every client-driven entry point re-resolves and
 * re-validates against server state; the client is never trusted. Reads MCA state via
 * {@link McaCompat} only.
 */
public final class QuestManager {

    private QuestManager() {
    }

    // ---------------------------------------------------------------- packet entry points

    public static void openFromPacket(ServerPlayer player, UUID villagerUuid) {
        Entity villager = resolve(player, villagerUuid);
        if (villager != null) {
            sendMenu(player, villager);
        }
    }

    /** Used by the Phase 0 debug interaction. */
    public static void open(ServerPlayer player, Entity villager) {
        sendMenu(player, villager);
    }

    public static void acceptFromPacket(ServerPlayer player, UUID villagerUuid, ResourceLocation questId, boolean accept) {
        Entity villager = resolve(player, villagerUuid);
        if (villager == null) {
            return;
        }
        if (accept) {
            accept(player, villager, questId);
        }
        // decline is a no-op for now (it simply does not create state); both refresh the menu.
        sendMenu(player, villager);
    }

    public static void turnInFromPacket(ServerPlayer player, UUID villagerUuid, ResourceLocation questId) {
        Entity villager = resolve(player, villagerUuid);
        if (villager == null) {
            return;
        }
        turnIn(player, villager, questId);
        sendMenu(player, villager);
    }

    public static void abandonFromPacket(ServerPlayer player, UUID villagerUuid, ResourceLocation questId) {
        Entity villager = resolve(player, villagerUuid);
        if (villager == null) {
            return;
        }
        abandon(player, villager, questId);
        sendMenu(player, villager);
    }

    @Nullable
    private static Entity resolve(ServerPlayer player, UUID villagerUuid) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity entity = level.getEntity(villagerUuid);
        return (entity != null && McaCompat.canPlayerInteract(player, entity)) ? entity : null;
    }

    // ---------------------------------------------------------------- menu construction

    public static void sendMenu(ServerPlayer player, Entity villager) {
        UUID villagerUuid = villager.getUUID();
        Component name = McaCompat.getVillagerDisplayName(villager);
        String profession = McaCompat.getProfessionId(villager).map(ResourceLocation::toString).orElse("");
        int favor = McaCompat.getFavor(player, villager);

        Optional<PlayerQuestData> dataOpt = QuestCapabilities.get(player);
        if (dataOpt.isEmpty()) {
            send(player, QuestMenuDataS2CPacket.noQuest(villagerUuid, name, profession, favor, QuestMenuStatus.NO_QUESTS));
            return;
        }
        PlayerQuestData data = dataOpt.get();

        // 1) Active quests relevant here: given by this villager, or turn-in-able here per their mode.
        List<ActiveQuest> relevant = relevantActiveQuests(player, villager, data);
        if (!relevant.isEmpty()) {
            ActiveQuest active = relevant.get(0);
            Optional<QuestDefinition> defOpt = QuestRegistry.get(active.questId());
            if (defOpt.isEmpty()) {
                // The definition disappeared on a datapack reload — fail gracefully (spec section 36).
                send(player, QuestMenuDataS2CPacket.noQuest(villagerUuid, name, profession, favor, QuestMenuStatus.BLOCKED));
                return;
            }
            QuestDefinition def = defOpt.get();
            boolean ready = isComplete(player, def, active) && canTurnInAt(active, def, villager);
            QuestMenuStatus status = ready ? QuestMenuStatus.READY : QuestMenuStatus.IN_PROGRESS;
            Component dialogue = def.dialogueOr(ready ? QuestDefinition.READY : QuestDefinition.IN_PROGRESS, def.title());
            send(player, QuestMenuDataS2CPacket.quest(villagerUuid, name, profession, favor, status,
                    def.id(), def.title(), dialogue,
                    objectiveLines(player, def, active), rewardLines(def)));
            return;
        }

        // 2) Otherwise offer an eligible quest (if any and the player is under the active cap).
        if (data.activeCount() >= McaQuestsConfig.COMMON.maxActiveQuestsPerPlayer.get()) {
            send(player, QuestMenuDataS2CPacket.noQuest(villagerUuid, name, profession, favor, QuestMenuStatus.NO_QUESTS));
            return;
        }
        List<QuestDefinition> eligible = eligibleOffers(player, villager, data);
        if (eligible.isEmpty()) {
            send(player, QuestMenuDataS2CPacket.noQuest(villagerUuid, name, profession, favor, QuestMenuStatus.NO_QUESTS));
            return;
        }
        long worldDay = ((ServerLevel) player.level()).getDayTime() / 24000L;
        QuestDefinition chosen = pickDeterministic(eligible, offerSeed(player, villagerUuid, worldDay));
        Component dialogue = chosen.dialogueOr(QuestDefinition.OFFER, chosen.title());
        send(player, QuestMenuDataS2CPacket.quest(villagerUuid, name, profession, favor, QuestMenuStatus.OFFER,
                chosen.id(), chosen.title(), dialogue,
                objectiveLines(player, chosen, null), rewardLines(chosen)));
    }

    // ---------------------------------------------------------------- actions

    public static boolean accept(ServerPlayer player, Entity villager, ResourceLocation questId) {
        Optional<QuestDefinition> defOpt = QuestRegistry.get(questId);
        Optional<PlayerQuestData> dataOpt = QuestCapabilities.get(player);
        if (defOpt.isEmpty() || dataOpt.isEmpty()) {
            return false;
        }
        QuestDefinition def = defOpt.get();
        PlayerQuestData data = dataOpt.get();
        UUID villagerUuid = villager.getUUID();

        // Re-validate eligibility server-side; never trust the client's offered id.
        if (!eligibleOffers(player, villager, data).contains(def)) {
            return false;
        }
        if (data.activeCount() >= McaQuestsConfig.COMMON.maxActiveQuestsPerPlayer.get()
                || data.byVillager(villagerUuid).size() >= McaQuestsConfig.COMMON.maxActiveQuestsPerVillager.get()) {
            return false;
        }

        data.add(ActiveQuest.create(questId, villagerUuid,
                McaCompat.getVillagerDisplayName(villager),
                McaCompat.getProfessionId(villager).orElse(null),
                player.level().dimension().location(),
                ((ServerLevel) player.level()).getGameTime(),
                def.objectives().size()));
        return true;
    }

    public static boolean turnIn(ServerPlayer player, Entity villager, ResourceLocation questId) {
        Optional<PlayerQuestData> dataOpt = QuestCapabilities.get(player);
        Optional<QuestDefinition> defOpt = QuestRegistry.get(questId);
        if (dataOpt.isEmpty() || defOpt.isEmpty()) {
            return false;
        }
        PlayerQuestData data = dataOpt.get();
        QuestDefinition def = defOpt.get();
        // Find a completable copy of this quest that may be turned in at THIS villager (mode-aware).
        Optional<ActiveQuest> activeOpt = data.active().stream()
                .filter(aq -> aq.questId().equals(questId) && !aq.rewardClaimed()
                        && canTurnInAt(aq, def, villager) && isComplete(player, def, aq))
                .findFirst();
        return activeOpt.filter(active -> completeQuest(player, villager, def, active, data)).isPresent();
    }

    /** Auto-completion for {@link TurnInMode#SELF_COMPLETE} quests; called from the progress tick. */
    public static void selfComplete(ServerPlayer player, ActiveQuest active) {
        Optional<PlayerQuestData> dataOpt = QuestCapabilities.get(player);
        Optional<QuestDefinition> defOpt = QuestRegistry.get(active.questId());
        if (dataOpt.isEmpty() || defOpt.isEmpty() || active.rewardClaimed()
                || !isComplete(player, defOpt.get(), active)) {
            return;
        }
        completeQuest(player, resolveGiver(player, active), defOpt.get(), active, dataOpt.get());
    }

    /**
     * Atomic, idempotent completion. Claims the reward slot first (blocks packet-spam dup), consumes
     * objective items, then grants rewards (favor last), records cooldown/completion, and removes the
     * quest. {@code grantVillager} receives favor (may be null if the giver is gone).
     */
    private static boolean completeQuest(ServerPlayer player, Entity grantVillager,
                                         QuestDefinition def, ActiveQuest active, PlayerQuestData data) {
        if (active.rewardClaimed()) {
            return false;
        }
        active.setRewardClaimed(true);

        for (int i = 0; i < def.objectives().size(); i++) {
            def.objectives().get(i).consumeOnTurnIn(player, active.progress(i));
        }
        for (QuestReward reward : def.rewards()) {
            if (!(reward instanceof FavorReward)) {
                reward.grant(player, grantVillager);
            }
        }
        for (QuestReward reward : def.rewards()) {
            if (reward instanceof FavorReward) {
                reward.grant(player, grantVillager);
            }
        }

        long now = ((ServerLevel) player.level()).getGameTime();
        data.history().recordCompletion(def.id());
        switch (def.repeat().type()) {
            case COOLDOWN -> data.history().setCooldownUntil(def.id(), active.villagerUuid(), now + def.cooldownTicks());
            case ONCE -> data.history().setCooldownUntil(def.id(), active.villagerUuid(), Long.MAX_VALUE);
            case REPEATABLE -> { /* immediately available again */ }
        }
        data.remove(active);
        return true;
    }

    public static boolean abandon(ServerPlayer player, Entity villager, ResourceLocation questId) {
        Optional<PlayerQuestData> dataOpt = QuestCapabilities.get(player);
        if (dataOpt.isEmpty()) {
            return false;
        }
        Optional<ActiveQuest> active = dataOpt.get().find(questId, villager.getUUID());
        active.ifPresent(dataOpt.get()::remove);
        return active.isPresent();
    }

    // ---------------------------------------------------------------- helpers

    public static boolean isComplete(ServerPlayer player, QuestDefinition def, ActiveQuest active) {
        List<QuestObjective> objectives = def.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            if (!objectives.get(i).isSatisfied(player, active.progress(i))) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code active} may be turned in at {@code villager}, honouring its turn-in mode (spec section 17). */
    public static boolean canTurnInAt(ActiveQuest active, QuestDefinition def, Entity villager) {
        if (!McaCompat.isMcaVillager(villager)) {
            return false;
        }
        boolean isGiver = villager.getUUID().equals(active.villagerUuid());
        return switch (def.turnIn().mode()) {
            case ORIGINAL_GIVER -> isGiver
                    || (McaQuestsConfig.COMMON.allowTurnInToSameProfessionIfOriginalMissing.get()
                        && sameProfessionAsGiver(active, villager));
            case ANY_VILLAGER -> true;
            case SAME_PROFESSION -> isGiver || sameProfessionAsGiver(active, villager);
            case SPECIFIED_PROFESSION -> ProfessionMatcher.matchesAny(def.turnIn().professions(),
                    McaCompat.getProfessionId(villager).orElse(null), profMode());
            case SELF_COMPLETE -> false; // completed automatically, never via the menu
        };
    }

    private static boolean sameProfessionAsGiver(ActiveQuest active, Entity villager) {
        ResourceLocation giverProfession = active.villagerProfession();
        ResourceLocation actual = McaCompat.getProfessionId(villager).orElse(null);
        return giverProfession != null && actual != null
                && ProfessionMatcher.matches(giverProfession, actual, profMode());
    }

    private static ProfessionMatchingMode profMode() {
        return McaQuestsConfig.COMMON.professionMatchingMode.get();
    }

    /** Active quests worth showing at this villager: ones it gave, or ones ready and turn-in-able here. */
    private static List<ActiveQuest> relevantActiveQuests(ServerPlayer player, Entity villager, PlayerQuestData data) {
        List<ActiveQuest> relevant = new ArrayList<>();
        for (ActiveQuest active : data.active()) {
            QuestDefinition def = QuestRegistry.get(active.questId()).orElse(null);
            if (def == null) {
                continue;
            }
            boolean isGiver = active.villagerUuid().equals(villager.getUUID());
            boolean turnInableHere = isComplete(player, def, active) && canTurnInAt(active, def, villager);
            if (isGiver || turnInableHere) {
                relevant.add(active);
            }
        }
        // Surface a ready, turn-in-able quest ahead of an in-progress one.
        relevant.sort(Comparator.comparingInt(active -> {
            QuestDefinition def = QuestRegistry.get(active.questId()).orElse(null);
            boolean ready = def != null && isComplete(player, def, active) && canTurnInAt(active, def, villager);
            return ready ? 0 : 1;
        }));
        return relevant;
    }

    private static Entity resolveGiver(ServerPlayer player, ActiveQuest active) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, active.dimension()));
        return level != null ? level.getEntity(active.villagerUuid()) : null;
    }

    static List<QuestDefinition> eligibleOffers(ServerPlayer player, Entity villager, PlayerQuestData data) {
        long now = ((ServerLevel) player.level()).getGameTime();
        ResourceLocation profession = McaCompat.getProfessionId(villager).orElse(null);
        boolean adult = McaCompat.isAdult(villager);
        int favor = McaCompat.getFavor(player, villager);
        UUID villagerUuid = villager.getUUID();
        ProfessionMatchingMode mode = McaQuestsConfig.COMMON.professionMatchingMode.get();
        return QuestRegistry.all().stream()
                .filter(QuestDefinition::enabled)
                .filter(def -> def.giver().isGeneric()
                        || ProfessionMatcher.matchesAny(def.giver().professions(), profession, mode))
                .filter(def -> !def.giver().adultOnly() || adult)
                .filter(def -> def.giver().acceptsFavor(favor))
                .filter(def -> !data.hasActive(def.id(), villagerUuid))
                .filter(def -> !data.history().onCooldown(def.id(), villagerUuid, now))
                .filter(def -> def.repeat().type() != RepeatRule.RepeatType.ONCE
                        || data.history().completionCount(def.id()) == 0)
                .filter(def -> def.conditions()
                        .map(condition -> condition.test(new QuestContext(player, villager, data, def.id())))
                        .orElse(true))
                .sorted(Comparator.comparing(def -> def.id().toString()))
                .toList();
    }

    private static List<Component> objectiveLines(ServerPlayer player, QuestDefinition def, @Nullable ActiveQuest active) {
        List<Component> lines = new ArrayList<>();
        List<QuestObjective> objectives = def.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective objective = objectives.get(i);
            Component line = objective.describe();
            if (active != null) {
                int current = objective.current(player, active.progress(i));
                line = Component.empty().append(line)
                        .append(Component.literal("  (" + current + "/" + objective.required() + ")"));
            }
            lines.add(line);
        }
        return lines;
    }

    private static List<Component> rewardLines(QuestDefinition def) {
        return def.rewards().stream().map(QuestReward::describe).toList();
    }

    /** Weighted pick seeded so reopening the menu the same day returns the same offer (spec section 18). */
    private static QuestDefinition pickDeterministic(List<QuestDefinition> eligible, long seed) {
        int total = eligible.stream().mapToInt(QuestDefinition::weight).sum();
        if (total <= 0) {
            return eligible.get(0);
        }
        int roll = new Random(seed).nextInt(total);
        int accumulated = 0;
        for (QuestDefinition def : eligible) {
            accumulated += def.weight();
            if (roll < accumulated) {
                return def;
            }
        }
        return eligible.get(eligible.size() - 1);
    }

    private static long offerSeed(ServerPlayer player, UUID villagerUuid, long worldDay) {
        return player.getUUID().hashCode() * 31L + villagerUuid.hashCode() * 17L + worldDay * 1000003L;
    }

    private static void send(ServerPlayer player, QuestMenuDataS2CPacket packet) {
        QuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
