package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.compat.RelativeCandidate;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.situation.SituationFocus;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/**
 * An objective whose completion hinges on a specific villager the player must locate — a delivery
 * recipient, or the villager to heal / cure / escort / protect / defend / find. Implementing this lets
 * {@code QuestProgressEvents} highlight (glow) that villager while the quest is active, so it can be
 * found. Returns {@code empty} when the target is unresolved or not currently loaded (nothing to glow).
 *
 * <p>Extends {@link QuestObjective} so the offer-time resolvability check below can be inherited by all
 * seven implementors rather than copied into each. Every class that implements this already implemented
 * {@code QuestObjective} too, so nothing in the mod changes shape; an add-on that implemented
 * {@code VillagerTargeted} alone would now need to implement {@code QuestObjective} as well, which it
 * would have had to do anyway to be registered.
 */
public interface VillagerTargeted extends QuestObjective {

    Optional<LivingEntity> highlightTarget(ServerPlayer player, ActiveQuest active,
                                           ObjectiveProgress progress, ServerLevel level);

    /**
     * The selector this objective binds — whatever a datapack writes as its {@code villager} /
     * {@code recipient} / {@code relative} field. Exposed so callers that only know the objective is
     * villager-targeted (the accept-time binder, the HUD's target line) can reach the selector without
     * an {@code instanceof} chain over every objective type.
     */
    VillagerTarget targetSelector();

    /**
     * Reports the villager this objective bound as lost, once they are.
     *
     * <p>All seven implementors inherit it, so a delivery, a cure, an escort and a rescue all say the same
     * thing in the same place when the person they are about dies or leaves the world. An objective type
     * that overrides this should call {@code super} unless it genuinely means to be silent.
     */
    @Override
    default Optional<Component> unavailableReason(ServerPlayer player, ActiveQuest active,
                                                  ObjectiveProgress progress, ServerLevel level) {
        return ObjectiveSupport.boundTargetLost(targetSelector(), active, progress, level);
    }

    /**
     * Refuses the offer when the villager this objective names does not exist.
     *
     * <p>This is the gate whose absence produced the reported bug. A {@code family} target now states the
     * status its villager must satisfy ({@code require}, defaulting to {@code reachable}), and this asks
     * the giver's candidate list whether anybody does. If nobody does, the quest is not offered at all —
     * rather than being offered, accepted, and then bound to a sibling who died last week and whom MCA
     * merely never removed from the village roll.
     *
     * <p>Reads through the pass's shared {@code McaVillagerSnapshot}, so the family tree is walked once
     * per relation for the whole eligibility pass rather than once per candidate quest.
     *
     * <p>The other modes need no check here: {@code self} is the giver standing in front of the player,
     * and {@code uuid} / {@code profession} resolve live and are allowed to pause rather than be withheld
     * ({@code profession} deliberately re-resolves so a quest does not dead-end when the smith it picked
     * wanders off).
     */
    @Override
    default Optional<Component> unofferableReason(QuestContext context) {
        VillagerTarget selector = targetSelector();
        return switch (selector.mode()) {
            case FAMILY -> {
                List<RelativeCandidate> pool = context.mca().relativeCandidates(selector.effectiveRelation());
                boolean anyone = pool.stream().anyMatch(c -> c.matches(selector.effectiveRequire()));
                yield anyone ? Optional.empty()
                        : Optional.of(Component.translatable("mcaquests.unofferable.no_relative",
                                selector.describe()));
            }
            case SITUATION_FOCUS -> SituationFocus
                    .focalVillager(context.level().getServer(), context.villager(), context.questId())
                    .isPresent()
                    ? Optional.empty()
                    : Optional.of(Component.translatable("mcaquests.unofferable.no_situation_focus"));
            default -> Optional.empty();
        };
    }
}
