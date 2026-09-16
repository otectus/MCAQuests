package dev.otectus.mcaquests.compat.mca;

import dev.otectus.mcaquests.quest.delivery.DeliveryResult;
import dev.otectus.mcaquests.quest.delivery.DeliveryService;
import dev.otectus.mcaquests.quest.delivery.DeliveryView;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a Gift gesture is allowed to become.
 *
 * <p>Four rules decide that, and each of them is a way the bridge could quietly cheat a player:
 *
 * <ul>
 *   <li>a command that is not {@code "gift"} is never touched, so no other MCA action can be
 *       cancelled by this mod;</li>
 *   <li>a blocked hand-in still belongs to the quest — falling through to an ordinary gift would take
 *       the requested item and credit nothing;</li>
 *   <li>an ambiguous gesture is a question, not a guess: two quests wanting the same item leave the
 *       item where it is;</li>
 *   <li>a replayed request is served once, so a duplicated click cannot be charged twice.</li>
 * </ul>
 *
 * <p>All four are asserted against pure entry points rather than a simulated server, because that is
 * what the routing actually is: the world-dependent half is {@code DeliveryService}'s transaction,
 * which has its own tests.
 */
class McaGiftRoutingTest {

    private static final ResourceLocation CROSSBOWS = ResourceLocation.fromNamespaceAndPath("mcaquests", "last_banner_home");
    private static final ResourceLocation RODS = ResourceLocation.fromNamespaceAndPath("mcaquests", "nether_relay");
    private static final UUID COPY_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID COPY_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private static DeliveryView view(ResourceLocation quest, UUID instance, boolean deliverable,
                                     DeliveryResult reason) {
        return new DeliveryView(quest, instance, 0, Component.literal("Crossbow"), 0, 2, 2,
                Component.literal("Rowan"), UUID.randomUUID(), deliverable, true, false, reason);
    }

    // --- the command filter ----------------------------------------------------------------------

    @Test
    @DisplayName("only the gift command is routed, and nothing else is even looked at")
    void onlyGiftIsRouted() {
        assertTrue(McaGiftHookEvents.routes("gift"));
        for (String other : new String[] {"trade", "inventory", "sethome", "gohome", "procreate",
                "adopt", "execute", "Gift", "gift ", "", null}) {
            assertFalse(McaGiftHookEvents.routes(other), "must not route: " + other);
        }
    }

    @Test
    @DisplayName("a non-gift command passes through without resolving anything at all")
    void nonGiftCommandPassesThrough() {
        // Deliberately with no handler and no player: if this returned anything but PASS_THROUGH it
        // would have had to touch one of them, which is the thing being ruled out.
        assertSame(McaGiftHookEvents.Outcome.PASS_THROUGH,
                McaGiftHookEvents.handle(null, null, "trade"));
        assertSame(McaGiftHookEvents.Outcome.PASS_THROUGH,
                McaGiftHookEvents.handle(null, null, null));
        assertFalse(McaGiftHookEvents.Outcome.PASS_THROUGH.handled(),
                "pass-through must leave MCA's own implementation running");
    }

    // --- what an answer means --------------------------------------------------------------------

    @Test
    @DisplayName("no interested quest leaves MCA's ordinary gift untouched")
    void noQuestMeansOrdinaryGift() {
        assertSame(McaGiftHookEvents.Outcome.PASS_THROUGH,
                McaGiftHookEvents.classify(Optional.empty()));
    }

    @Test
    @DisplayName("units that actually moved are a delivery, and MCA's branch is suppressed")
    void committedUnitsAreADelivery() {
        assertSame(McaGiftHookEvents.Outcome.DELIVERED, McaGiftHookEvents.classify(Optional.of(
                new DeliveryService.Outcome(DeliveryResult.DELIVERED_PARTIAL, 1, 1, 2))));
        assertSame(McaGiftHookEvents.Outcome.DELIVERED, McaGiftHookEvents.classify(Optional.of(
                new DeliveryService.Outcome(DeliveryResult.DELIVERY_SATISFIED, 1, 2, 2))));
        assertTrue(McaGiftHookEvents.Outcome.DELIVERED.handled());
    }

    @Test
    @DisplayName("a proof objective is its own outcome: shown, not taken, and not gifted either")
    void proofIsNeitherGiftNorTransfer() {
        assertSame(McaGiftHookEvents.Outcome.PROOF_ACKNOWLEDGED, McaGiftHookEvents.classify(Optional.of(
                new DeliveryService.Outcome(DeliveryResult.PROOF_ACKNOWLEDGED, 0, 0, 2))));
    }

    @Test
    @DisplayName("a blocked hand-in is handled without transfer, never as an ordinary gift")
    void blockedHandInDoesNotFallThrough() {
        for (DeliveryResult blocked : new DeliveryResult[] {DeliveryResult.DESTINATION_FULL,
                DeliveryResult.OBJECTIVE_PAUSED, DeliveryResult.AMBIGUOUS_DELIVERY,
                DeliveryResult.STALE_REQUEST, DeliveryResult.BRIDGE_UNAVAILABLE,
                DeliveryResult.ALREADY_DELIVERED}) {
            McaGiftHookEvents.Outcome outcome =
                    McaGiftHookEvents.classify(Optional.of(DeliveryService.Outcome.of(blocked)));
            assertSame(McaGiftHookEvents.Outcome.HANDLED_WITHOUT_TRANSFER, outcome, blocked.name());
            assertTrue(outcome.handled(), blocked + " must not reach MCA's gift branch");
        }
    }

    // --- which obligation a gesture means ---------------------------------------------------------

    @Test
    @DisplayName("a blocked match is still the villager's business and is not discarded")
    void blockedMatchesSurviveCandidateSelection() {
        assertTrue(DeliveryService.actionable(view(CROSSBOWS, COPY_A, false,
                        DeliveryResult.DESTINATION_FULL)),
                "a full inventory is a refusal to report, not a reason to gift the item away");
        assertTrue(DeliveryService.actionable(view(CROSSBOWS, COPY_A, false,
                DeliveryResult.OBJECTIVE_PAUSED)));
        assertFalse(DeliveryService.actionable(view(CROSSBOWS, COPY_A, false,
                        DeliveryResult.WRONG_RECIPIENT)),
                "an unrelated villager keeps ordinary Gift behaviour");
        assertFalse(DeliveryService.actionable(view(CROSSBOWS, COPY_A, false,
                DeliveryResult.RECIPIENT_UNAVAILABLE)));
    }

    @Test
    @DisplayName("one match is routed outright")
    void singleMatchWins() {
        DeliveryView only = view(CROSSBOWS, COPY_A, true, null);
        assertEquals(Optional.of(only), DeliveryService.chooseFrom(List.of(only), null, null));
    }

    @Test
    @DisplayName("two matches with nothing followed are ambiguous, and the item stays in hand")
    void ambiguousGestureIsRefused() {
        List<DeliveryView> both = List.of(view(CROSSBOWS, COPY_A, true, null),
                view(RODS, COPY_B, true, null));
        assertEquals(Optional.empty(), DeliveryService.chooseFrom(both, null, null));
    }

    @Test
    @DisplayName("the followed quest decides, but only when its own match is unambiguous")
    void trackedQuestBreaksTheTie() {
        DeliveryView crossbows = view(CROSSBOWS, COPY_A, true, null);
        DeliveryView rods = view(RODS, COPY_B, true, null);
        assertEquals(Optional.of(crossbows),
                DeliveryService.chooseFrom(List.of(crossbows, rods), CROSSBOWS, COPY_A));

        // Two copies of the same followed quest is exactly the case a quest id cannot resolve.
        List<DeliveryView> twoCopies = List.of(view(CROSSBOWS, COPY_A, true, null),
                view(CROSSBOWS, COPY_B, true, null));
        assertEquals(Optional.empty(), DeliveryService.chooseFrom(twoCopies, CROSSBOWS, null));
        assertEquals(Optional.of(twoCopies.get(1)),
                DeliveryService.chooseFrom(twoCopies, CROSSBOWS, COPY_B),
                "naming the copy is what makes two copies of one quest separable");
    }

    @Test
    @DisplayName("following an unrelated quest does not make an ambiguous gesture decidable")
    void trackingSomethingElseStaysAmbiguous() {
        List<DeliveryView> both = List.of(view(CROSSBOWS, COPY_A, true, null),
                view(RODS, COPY_B, true, null));
        assertEquals(Optional.empty(), DeliveryService.chooseFrom(both,
                ResourceLocation.fromNamespaceAndPath("mcaquests", "something_else"), null));
    }

    // --- one invocation, one charge ---------------------------------------------------------------

    @Test
    @DisplayName("a request id is served once; a replay of the same click is refused")
    void requestIdsAreClaimedOnce() {
        DeliveryService.resetRequestsForTest();
        try {
            UUID click = UUID.randomUUID();
            assertTrue(DeliveryService.claimRequest(click));
            assertFalse(DeliveryService.claimRequest(click), "the same click must not be charged twice");
            assertTrue(DeliveryService.claimRequest(UUID.randomUUID()), "a different click is its own");
            assertTrue(DeliveryService.claimRequest(null), "a server-driven route has nothing to replay");
            assertTrue(DeliveryService.claimRequest(null));
        } finally {
            DeliveryService.resetRequestsForTest();
        }
    }

    // --- the bridge's own availability ------------------------------------------------------------

    @Test
    @DisplayName("one applied variant is enough, and a later skipped one cannot undo it")
    void appliedIsStickyAcrossVariants() {
        McaGiftHookProbe.resetForTest();
        try {
            assertFalse(McaGiftHookProbe.applied());
            McaGiftHookProbe.mcaPresent(true);
            McaGiftHookProbe.applied("forge.net.mca.entity.interaction.VillagerCommandHandler");
            McaGiftHookProbe.skipped("net.mca.entity.interaction.VillagerCommandHandler",
                    "not present in this MCA build");

            assertTrue(McaGiftHookProbe.applied(),
                    "three of the four variants always skip; that is the healthy case");
            assertFalse(McaGiftHookProbe.unhookedWithMcaPresent());
        } finally {
            McaGiftHookProbe.resetForTest();
        }
    }

    @Test
    @DisplayName("MCA present with no variant applied is the one state worth reporting")
    void mcaPresentWithoutAHookIsReportable() {
        McaGiftHookProbe.resetForTest();
        try {
            McaGiftHookProbe.mcaPresent(true);
            McaGiftHookProbe.failed("forge.net.mca.entity.interaction.VillagerCommandHandler",
                    "handle is absent or has a different signature");

            assertFalse(McaGiftHookProbe.applied());
            assertTrue(McaGiftHookProbe.unhookedWithMcaPresent());
            assertTrue(McaGiftHookProbe.describe().contains("handle is absent"),
                    "the reason has to survive into a bug report: " + McaGiftHookProbe.describe());
        } finally {
            McaGiftHookProbe.resetForTest();
        }
    }

    @Test
    @DisplayName("an absent MCA is not a fault and reports nothing to fix")
    void absentMcaIsNotAFault() {
        McaGiftHookProbe.resetForTest();
        try {
            McaGiftHookProbe.mcaPresent(false);
            McaGiftHookProbe.skipped("net.mca.entity.interaction.VillagerCommandHandler",
                    "MCA is not installed");

            assertFalse(McaGiftHookProbe.unhookedWithMcaPresent());
        } finally {
            McaGiftHookProbe.resetForTest();
        }
    }
}
