package dev.otectus.mcaquests.quest;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.McaQuestsConfig.ProfessionMatchingMode;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.McaVillagerSnapshot;
import dev.otectus.mcaquests.compat.TownsteadContentGate;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.situation.DynamicOfferSource;
import dev.otectus.mcaquests.quest.situation.SituationIds;
import dev.otectus.mcaquests.state.PlayerQuestData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * The one place that decides whether a villager may offer a quest to a player right now.
 *
 * <p>This used to be written out three times: the real chain inside {@code QuestManager.eligibleOffers},
 * a hand-maintained copy in {@code passesIndividualFilters} for the debug command, and a partial
 * re-implementation in {@code DynamicOfferSource} for situation offers. The third one was the expensive
 * mistake — situation offers were appended to the pool <em>after</em> the static chain had run, so they
 * skipped cooldowns, repeat rules, the trivially-satisfied check and the content gate entirely. Two of the
 * shipped situations reached players with a family objective and no gate at all because of it.
 *
 * <p>Now there is one chain, and adding a filter to it necessarily applies to every kind of offer.
 *
 * <h2>Order matters</h2>
 *
 * <p>The filters run cheapest-first, and the two most expensive ones run last on purpose:
 * {@link QuestObjective#isTriviallySatisfied} resolves anchors and targets, and
 * {@link QuestObjective#unofferableReason} walks the giver's family tree. Both only ever run on quests
 * that are eligible in every other respect, and both read through the pass's shared
 * {@link McaVillagerSnapshot} so the tree is walked once per relation rather than once per quest.
 */
public final class OfferFilters {

    private OfferFilters() {
    }

    /** Why a quest is not being offered — {@code null} in {@link Result#reason} means it is. */
    public record Result(boolean passes, @Nullable String reason) {

        private static final Result PASS = new Result(true, null);

        static Result pass() {
            return PASS;
        }

        static Result fail(String reason) {
            return new Result(false, reason);
        }
    }

    /**
     * Everything about the giver that every candidate quest in one pass shares, read once.
     *
     * <p>{@code now} is the monotonic game time, which is what cooldowns are keyed on — deliberately not
     * {@code getDayTime()}, which sleeping and {@code /time set} move backwards.
     */
    public record Pass(ServerPlayer player, Entity villager, PlayerQuestData data,
                       @Nullable ResourceLocation profession, boolean adult, int hearts,
                       long now, ProfessionMatchingMode matching, McaVillagerSnapshot snapshot) {

        public static Pass of(ServerPlayer player, Entity villager, PlayerQuestData data) {
            return new Pass(player, villager, data,
                    McaCompat.getProfessionId(villager).orElse(null),
                    McaCompat.isAdult(villager),
                    McaCompat.getHearts(player, villager),
                    ((ServerLevel) player.level()).getGameTime(),
                    McaQuestsConfig.COMMON.professionMatchingMode.get(),
                    new McaVillagerSnapshot(player, villager));
        }

        public UUID villagerUuid() {
            return villager.getUUID();
        }

        /** A context for {@code def} sharing this pass's snapshot, so MCA state is read once per pass. */
        public QuestContext contextFor(QuestDefinition def) {
            return new QuestContext(player, villager, data, def.id(), snapshot);
        }
    }

    /** Whether {@code def} may be offered in this pass. */
    public static boolean passes(Pass pass, QuestDefinition def) {
        return explain(pass, def).passes();
    }

    /**
     * As {@link #passes}, naming the first filter that refused. Drives {@code /mcaquests debug quest},
     * which previously answered from its own copy of the chain and so could disagree with the menu.
     */
    public static Result explain(Pass pass, QuestDefinition def) {
        UUID villagerUuid = pass.villagerUuid();
        if (!def.enabled()) {
            return Result.fail("DISABLED");
        }
        // A situation offer is only ever as live as its situation. Sessions remember a drawn card for up
        // to offerRefreshTicks, so without this a resolved situation stayed on the menu and Accept did
        // nothing at all (1.5.1).
        if (SituationIds.isSyntheticId(def.id()) && !DynamicOfferSource.isStillOpen(pass, def.id())) {
            return Result.fail("SITUATION_CLOSED (the situation this offer came from is over)");
        }
        if (!def.giver().isGeneric()
                && !ProfessionMatcher.matchesAny(def.giver().professions(), pass.profession(), pass.matching())) {
            return Result.fail("PROFESSION (giver is not one of " + def.giver().professions() + ")");
        }
        if (def.giver().adultOnly() && !pass.adult()) {
            return Result.fail("ADULT_ONLY (this villager is not an adult)");
        }
        if (!def.giver().acceptsHearts(pass.hearts())) {
            return Result.fail("HEARTS (" + pass.hearts() + " is outside the giver's band)");
        }
        if (pass.data().hasActive(def.id(), villagerUuid)) {
            return Result.fail("ALREADY_ACTIVE (with this villager)");
        }
        if (pass.data().history().onCooldown(def.id(), villagerUuid, pass.now())) {
            return Result.fail("ON_COOLDOWN");
        }
        if (def.repeat().type() == RepeatRule.RepeatType.ONCE
                && QuestManager.onceCompletionCount(pass.data(), def, villagerUuid) != 0) {
            return Result.fail("COMPLETED (once-only, with this villager)");
        }
        if (QuestManager.completedThisPeriod(pass.player(), pass.data(), def, villagerUuid)) {
            return Result.fail("COMPLETED (this "
                    + def.repeat().period().map(p -> p.id()).orElse("period") + ")");
        }
        if (!TownsteadContentGate.allowsQuest(def.id(), def.category(), def.offerGroup())) {
            return Result.fail("CONTENT_DISABLED (a compat.townstead.content switch is off)");
        }
        // effectiveConditions() folds chain prerequisites into the condition gate, so a later stage can
        // never be offered before its prerequisites are completed.
        QuestContext context = pass.contextFor(def);
        if (!def.effectiveConditions().map(condition -> condition.test(context)).orElse(true)) {
            return Result.fail("LOCKED (prerequisite or condition unmet)");
        }
        // Never offer a quest that is already done. An escort whose villager is standing at the
        // destination, or a reach_location the player is already inside, would otherwise be accepted and
        // handed straight back for the full reward.
        for (QuestObjective objective : def.objectives()) {
            if (objective.isTriviallySatisfied(context)) {
                return Result.fail("ALREADY_SATISFIED (" + objective.type().id().getPath() + ")");
            }
        }
        // Last, and most expensive: does the thing this quest names actually exist?
        Optional<String> unofferable = unofferableReason(context, def);
        return unofferable.map(reason -> Result.fail("UNRESOLVABLE_TARGET (" + reason + ")"))
                .orElseGet(Result::pass);
    }

    /**
     * The first objective-level reason this quest names something that cannot be found, if any.
     *
     * <p>Kept separate from {@link #explain} because {@code accept} re-runs exactly this check — the world
     * can change between the menu being built and the player clicking the button, and binding a target
     * that no longer resolves is how a quest ends up sitting in the log forever at 0/1.
     */
    public static Optional<String> unofferableReason(QuestContext context, QuestDefinition def) {
        for (QuestObjective objective : def.objectives()) {
            Optional<Component> reason = objective.unofferableReason(context);
            if (reason.isPresent()) {
                return Optional.of(reason.get().getString());
            }
        }
        return Optional.empty();
    }

    /** As {@link #unofferableReason(QuestContext, QuestDefinition)}, as the {@link Component} to show. */
    public static Optional<Component> unofferableComponent(QuestContext context, QuestDefinition def) {
        for (QuestObjective objective : def.objectives()) {
            Optional<Component> reason = objective.unofferableReason(context);
            if (reason.isPresent()) {
                return reason;
            }
        }
        return Optional.empty();
    }
}
