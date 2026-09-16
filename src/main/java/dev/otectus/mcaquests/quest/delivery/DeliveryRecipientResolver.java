package dev.otectus.mcaquests.quest.delivery;

import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.QuestManager;
import dev.otectus.mcaquests.quest.objective.DeliverToVillagerObjective;
import dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.quest.objective.ObjectiveSupport;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.quest.TurnInMode;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Who is allowed to accept a delivery, and who the player should be told to look for.
 *
 * <p>Four identities are kept apart here, because collapsing any two of them is a bug someone can
 * exploit or be cheated by: the player interacting, the villager who gave the quest, the villager
 * authorized to take the goods, and the container the goods end up in. Only the third is this class's
 * business.
 *
 * <p>Nothing about recipient rules is reinvented. {@code deliver_to_villager} keeps
 * {@link ObjectiveSupport#matchesLocked} — the same resolution the objective line, the highlight and
 * the legacy hand-off have always used — and {@code item_delivery} keeps the quest's authored turn-in
 * policy. What is new is only that the answer is now available <em>before</em> anything is taken, so a
 * refusal can be explained instead of being silent.
 */
public final class DeliveryRecipientResolver {

    private DeliveryRecipientResolver() {
    }

    /**
     * Whether {@code candidate} may take this objective's goods.
     *
     * <p>{@code deliver_to_villager} asks its own recipient selector. {@code item_delivery} has no
     * recipient of its own — its goods go to the giver — so the quest's turn-in policy decides, plus the
     * giver themselves: a {@code self_complete} quest can still be paid into the hands of the villager
     * who asked for it even though the menu never turns it in there.
     */
    public static boolean authorizes(QuestObjective objective, ServerPlayer player, ActiveQuest active,
                                     QuestDefinition def, ObjectiveProgress progress, LivingEntity candidate,
                                     ServerLevel level) {
        if (!McaCompat.isMcaVillager(candidate)) {
            return false;
        }
        if (objective instanceof DeliverToVillagerObjective deliver) {
            Optional<Boolean> byIdentity = identityVerdict(deliver.recipient(), progress.targetUuid(),
                    active.villagerUuid(), candidate.getUUID());
            return byIdentity.orElseGet(() -> ObjectiveSupport.matchesLocked(deliver.recipient(), candidate,
                    player, active, progress, level));
        }
        if (objective instanceof ItemDeliveryObjective) {
            return candidate.getUUID().equals(active.villagerUuid())
                    || QuestManager.canTurnInAt(active, def, candidate);
        }
        return false;
    }

    /**
     * The recipient decision for the selectors that are pure identity, or empty when the world has to
     * be consulted.
     *
     * <p>{@code self}, {@code uuid} and any already-bound target name exactly one villager, and the
     * comparison is a UUID. This matters most for {@code "mode": "self"}, which is what the two bundled
     * crossbow and blaze-rod deliveries use: it means <b>the villager who gave the quest</b>, and a
     * same-profession stand-in standing next to them is not that person, however identical their trade
     * looks. Nothing in the profession, the name or the village may soften that.
     */
    public static Optional<Boolean> identityVerdict(VillagerTarget recipient, @Nullable UUID bound,
                                                    UUID giverUuid, UUID candidateUuid) {
        if (bound != null) {
            return Optional.of(bound.equals(candidateUuid));
        }
        return switch (recipient.mode()) {
            case SELF -> Optional.of(giverUuid.equals(candidateUuid));
            case UUID -> Optional.of(recipient.uuid().map(candidateUuid::equals).orElse(false));
            // profession stays live, family/situation/capital need the world: ask the shared resolver.
            case PROFESSION, FAMILY, SITUATION_FOCUS, CAPITAL_ROLE -> Optional.empty();
        };
    }

    /** The villager this objective's goods are for, if they are currently loaded. */
    public static Optional<LivingEntity> resolve(QuestObjective objective, ServerPlayer player,
                                                 ActiveQuest active, ObjectiveProgress progress,
                                                 ServerLevel level) {
        if (objective instanceof DeliverToVillagerObjective deliver) {
            return ObjectiveSupport.resolveLocked(deliver.recipient(), player, active, progress, level);
        }
        if (objective instanceof ItemDeliveryObjective) {
            return ObjectiveSupport.giver(level, active)
                    .filter(giver -> giver instanceof LivingEntity && McaCompat.isMcaVillager(giver))
                    .map(LivingEntity.class::cast);
        }
        return Optional.empty();
    }

    /** How to name the recipient on a card: their actual name when known, the selector's words otherwise. */
    public static Component describe(QuestObjective objective, ServerPlayer player, ActiveQuest active,
                                     ObjectiveProgress progress, ServerLevel level) {
        if (objective instanceof DeliverToVillagerObjective deliver) {
            return ObjectiveSupport.describeLocked(deliver.recipient(), player, active, progress, level);
        }
        return active.villagerName();
    }

    /**
     * Whether an MCA Gift to {@code candidate} may be routed to this objective at all.
     *
     * <p>Narrower than {@link #authorizes} on purpose: Gift is an unqualified gesture with no quest
     * context attached, so an unrelated villager must keep ordinary Gift behaviour even while a quest
     * elsewhere happens to want the same item. Only the authorized recipient of this obligation can
     * turn a gift into a payment.
     */
    public static boolean giftRoutable(QuestObjective objective, ServerPlayer player, ActiveQuest active,
                                       QuestDefinition def, ObjectiveProgress progress, LivingEntity candidate,
                                       ServerLevel level) {
        if (objective instanceof ItemDeliveryObjective && def.turnIn().mode() == TurnInMode.SELF_COMPLETE) {
            // A self-complete quest never turns in at a villager, so the giver is the only person a
            // gift toward it can mean. "Self-complete" is not permission to gift any villager.
            return candidate.getUUID().equals(active.villagerUuid());
        }
        return authorizes(objective, player, active, def, progress, candidate, level);
    }

    /** True when {@code entity} is a live MCA villager the player is actually standing next to. */
    public static boolean reachable(ServerPlayer player, @Nullable Entity entity) {
        return entity != null && McaCompat.canPlayerInteract(player, entity);
    }
}
