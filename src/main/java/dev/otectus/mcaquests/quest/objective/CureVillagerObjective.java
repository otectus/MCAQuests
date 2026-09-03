package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.target.ItemTarget;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * Cure an infected (zombifying) MCA villager. The objective observes MCA's infection state rather
 * than trusting any single action: once the resolved target is seen infected (the {@code seen}
 * flag is latched), a later transition back to not-infected — while the villager is still a living
 * MCA villager — completes it. Using the {@code cure_item} on an infected target also latches the
 * flag (the {@code onInteract} arm), so the cure registers even if the poll never caught the
 * infected moment. <b>HIGH-RISK</b>: MCA's infection/cure model is the authority; if
 * {@code getInfectionProgress} is unavailable the objective simply never completes (no crash).
 */
public record CureVillagerObjective(VillagerTarget villager, ItemTarget cureItem) implements QuestObjective, VillagerTargeted {

    private static final String SEEN_INFECTED = "seen_infected";

    private static final ItemTarget DEFAULT_CURE_ITEM =
            new ItemTarget(Optional.of(Items.GOLDEN_APPLE), Optional.empty());

    public static final MapCodec<CureVillagerObjective> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            VillagerTarget.CODEC.lenientOptionalFieldOf("villager", VillagerTarget.SELF).forGetter(CureVillagerObjective::villager),
            ItemTarget.MAP_CODEC.codec().lenientOptionalFieldOf("cure_item", DEFAULT_CURE_ITEM).forGetter(CureVillagerObjective::cureItem)
    ).apply(instance, CureVillagerObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.CURE_VILLAGER;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.cure_villager", villager.describe());
    }

    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return Component.translatable("mcaquests.objective.cure_villager", villager.describeResolved(player, active, level));
    }

    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                              ServerLevel level) {
        return Component.translatable("mcaquests.objective.cure_villager",
                ObjectiveSupport.describeLocked(villager, player, active, progress, level));
    }

    @Override
    public VillagerTarget targetSelector() {
        return villager;
    }

    @Override
    public Optional<LivingEntity> highlightTarget(ServerPlayer player, ActiveQuest active,
                                                  ObjectiveProgress progress, ServerLevel level) {
        return progress.count() >= 1
                ? Optional.empty()
                : ObjectiveSupport.resolveLocked(villager, player, active, progress, level);
    }

    @Override
    public int required() {
        return 1;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(progress.count(), 1);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= 1;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    /** Per-second poll: latch "seen infected", then complete on the infected → cured transition. */
    public void poll(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (progress.count() >= 1) {
            return;
        }
        Optional<LivingEntity> target = ObjectiveSupport.resolveLocked(villager, player, active, progress, level);
        if (target.isEmpty() || !target.get().isAlive()) {
            return;
        }
        LivingEntity entity = target.get();
        if (McaCompat.isInfected(entity)) {
            progress.extra().putBoolean(SEEN_INFECTED, true);
        } else if (progress.extra().getBoolean(SEEN_INFECTED)) {
            progress.setCount(1); // was infected, now cured, and still a living villager
        }
    }

    /** Arm the cure when the player uses the cure item on an infected target. */
    public void onInteract(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                           LivingEntity target, ItemStack held, ServerLevel level) {
        if (progress.count() >= 1 || !cureItem.matches(held)) {
            return;
        }
        if (ObjectiveSupport.matchesLocked(villager, target, player, active, progress, level)
                && McaCompat.isInfected(target)) {
            progress.extra().putBoolean(SEEN_INFECTED, true);
        }
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        villager.validate("Quest '" + questId + "': objective[" + index + "] villager", errors);
    }
}
