package dev.otectus.mcaquests.quest.kingdom;

import dev.otectus.mcaquests.compat.KingdomIntegration;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.state.KingdomBindingSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.OptionalInt;

/** Central lifecycle composition used by offers, acceptance, progress, completion, and rewards. */
public final class KingdomQuestLifecycle {
    public enum ActiveStatus { ALLOW, WAIT, FAIL_KINGDOM, FAIL_CIVIC }

    private KingdomQuestLifecycle() {
    }

    public static boolean allowsOffer(QuestDefinition definition, ServerPlayer player, Entity giver) {
        Optional<KingdomLifecycleSpec> lifecycle = definition.kingdomLifecycle();
        if (lifecycle.isEmpty()) return true;
        KingdomLifecycleSpec spec = lifecycle.get();
        if (spec.gate().isPresent() && !gateAndStanding(spec, null, player, giver, false)) return false;
        return spec.civicBuilding().isEmpty()
                || KingdomIntegration.bindBuilding(spec.civicBuilding().get(), player, giver).isPresent();
    }

    /** Captures every requested context before the quest is inserted into player state. */
    public static boolean bindAtAccept(QuestDefinition definition, ActiveQuest active,
                                       ServerPlayer player, Entity giver) {
        Optional<KingdomLifecycleSpec> lifecycle = definition.kingdomLifecycle();
        if (lifecycle.isEmpty()) return true;
        KingdomLifecycleSpec spec = lifecycle.get();
        if (spec.gate().isPresent() && spec.mode() != KingdomLifecycleMode.OFFER_ONLY) {
            Optional<KingdomBindingSnapshot> snapshot = KingdomIntegration.capture(
                    spec.effectiveBindingSubject(), spec.gate().get().explicitSettlementId(), player, giver);
            if (snapshot.isEmpty()) return false;
            active.bindKingdom(snapshot.get());
            if (!gateAndStanding(spec, snapshot.get(), player, giver, true)) return false;
        }
        if (spec.civicBuilding().isPresent()) {
            var binding = KingdomIntegration.bindBuilding(spec.civicBuilding().get(), player, giver);
            if (binding.isEmpty()) return false;
            active.bindCivicBuilding(binding.get());
        }
        return true;
    }

    public static ActiveStatus activeStatus(QuestDefinition definition, ActiveQuest active,
                                            ServerPlayer player, Entity giver) {
        Optional<KingdomLifecycleSpec> lifecycle = definition.kingdomLifecycle();
        if (lifecycle.isEmpty()) return ActiveStatus.ALLOW;
        KingdomLifecycleSpec spec = lifecycle.get();
        ActiveStatus kingdom = switch (spec.mode()) {
            case OFFER_ONLY -> ActiveStatus.ALLOW;
            case BOUND_AT_ACCEPT -> active.kingdomBinding()
                    .filter(snapshot -> gateAndStanding(spec, snapshot, player, giver, true))
                    .map(ignored -> ActiveStatus.ALLOW).orElse(ActiveStatus.WAIT);
            case LIVE -> gateAndStanding(spec, null, player, giver, false)
                    ? ActiveStatus.ALLOW : ActiveStatus.WAIT;
            case FAIL_ON_CHANGE -> changeStatus(spec, active, player, giver);
        };
        if (kingdom != ActiveStatus.ALLOW || spec.civicBuilding().isEmpty()) return kingdom;
        if (active.civicBuildingBinding().isEmpty()) return ActiveStatus.WAIT;
        KingdomIntegration.Recovery recovered = KingdomIntegration.recoverBuilding(
                active.civicBuildingBinding().get(), spec.civicBuilding().get(), player);
        recovered.binding().ifPresent(active::bindCivicBuilding);
        return switch (recovered.status()) {
            case FOUND, REBOUND -> ActiveStatus.ALLOW;
            case WAITING, UNAVAILABLE -> ActiveStatus.WAIT;
            case FAILED -> ActiveStatus.FAIL_CIVIC;
        };
    }

    public static String failureReason(QuestDefinition definition, boolean civic) {
        return definition.kingdomLifecycle().flatMap(spec -> civic
                        ? spec.civicBuilding().flatMap(CivicBuildingSpec::failureReason)
                        : spec.failureReason())
                .orElse("mcaquests.message.kingdom_context_changed");
    }

    private static ActiveStatus changeStatus(KingdomLifecycleSpec spec, ActiveQuest active,
                                             ServerPlayer player, Entity giver) {
        Optional<KingdomBindingSnapshot> bound = active.kingdomBinding();
        if (bound.isEmpty()) return ActiveStatus.WAIT;
        KingdomGateSpec gate = spec.gate().orElseThrow();
        Optional<KingdomBindingSnapshot> current = KingdomIntegration.capture(spec.effectiveBindingSubject(),
                gate.explicitSettlementId(), player, giver);
        return compareChange(bound, current);
    }

    static ActiveStatus compareChange(Optional<KingdomBindingSnapshot> accepted,
                                      Optional<KingdomBindingSnapshot> current) {
        if (accepted.isEmpty() || current.isEmpty()) return ActiveStatus.WAIT;
        return samePoliticalContext(accepted.get(), current.get())
                ? ActiveStatus.ALLOW : ActiveStatus.FAIL_KINGDOM;
    }

    static boolean samePoliticalContext(KingdomBindingSnapshot accepted, KingdomBindingSnapshot current) {
        return accepted.settlementId().equals(current.settlementId())
                && accepted.kingdomId().equals(current.kingdomId())
                && accepted.dimension().equals(current.dimension());
    }

    private static boolean gateAndStanding(KingdomLifecycleSpec lifecycle,
                                           KingdomBindingSnapshot bound,
                                           ServerPlayer player, Entity giver, boolean useBound) {
        KingdomGateSpec gate = lifecycle.gate().orElseThrow();
        if (useBound) {
            if (bound == null || !KingdomIntegration.allowsBound(gate, bound, player)) return false;
        } else if (!KingdomIntegration.allows(gate, player, giver)) {
            return false;
        }
        if (gate.standing().isEmpty()) return true;
        KingdomBindingSnapshot context = bound;
        if (context == null) {
            context = KingdomIntegration.capture(lifecycle.effectiveBindingSubject(),
                    gate.explicitSettlementId(), player, giver).orElse(null);
        }
        if (context == null) return false;
        return KingdomIntegration.standingAllows(gate.standing().get(), context, player);
    }
}
