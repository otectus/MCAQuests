package dev.otectus.mcaquests.quest.delivery;

import dev.otectus.mcaquests.quest.target.VillagerTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may accept a delivery, for the selectors that are pure identity.
 *
 * <p>{@code "recipient": {"mode": "self"}} is what both bundled deliveries use, and it means the
 * villager who gave the quest — one person, by UUID. The failure it has to be proof against is the
 * plausible-looking one: a villager of the same trade, in the same village, standing right next to the
 * giver is <em>not</em> the giver, and handing them the goods must be refused with an explanation
 * rather than accepted because they look close enough.
 *
 * <p>Only identity modes are decided here. {@code profession} is deliberately left live and
 * {@code family} / {@code situation_focus} / {@code capital_role} need the world, so those return no
 * verdict and fall through to the shared objective resolution — which is exactly what the assertion
 * below pins, so a future shortcut cannot start guessing at them.
 */
class DeliveryRecipientTest {

    private static final UUID GIVER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SAME_PROFESSION_STAND_IN = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID NAMED = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    private static VillagerTarget uuidTarget(UUID uuid) {
        return new VillagerTarget(VillagerTarget.Mode.UUID, Optional.empty(), Optional.empty(),
                Optional.of(uuid));
    }

    private static VillagerTarget professionTarget() {
        return new VillagerTarget(VillagerTarget.Mode.PROFESSION,
                Optional.of(new net.minecraft.resources.ResourceLocation("minecraft", "weaponsmith")),
                Optional.empty(), Optional.empty());
    }

    @Test
    @DisplayName("recipient.mode=self accepts the giver and rejects a same-profession stand-in")
    void selfMeansTheGiverAndNobodyElse() {
        assertEquals(Optional.of(true),
                DeliveryRecipientResolver.identityVerdict(VillagerTarget.SELF, null, GIVER, GIVER));
        assertEquals(Optional.of(false),
                DeliveryRecipientResolver.identityVerdict(VillagerTarget.SELF, null, GIVER,
                        SAME_PROFESSION_STAND_IN),
                "a villager of the same trade is not the person the quest named");
    }

    @Test
    @DisplayName("mode=uuid names exactly one villager")
    void uuidModeIsExact() {
        assertEquals(Optional.of(true),
                DeliveryRecipientResolver.identityVerdict(uuidTarget(NAMED), null, GIVER, NAMED));
        assertEquals(Optional.of(false),
                DeliveryRecipientResolver.identityVerdict(uuidTarget(NAMED), null, GIVER, GIVER));
    }

    @Test
    @DisplayName("a bound target wins over the selector, whatever mode it was chosen by")
    void boundTargetIsFinal() {
        assertEquals(Optional.of(true),
                DeliveryRecipientResolver.identityVerdict(professionTarget(), NAMED, GIVER, NAMED));
        assertEquals(Optional.of(false),
                DeliveryRecipientResolver.identityVerdict(professionTarget(), NAMED, GIVER,
                        SAME_PROFESSION_STAND_IN),
                "once a delivery has bound a person, another smith is not a substitute");
    }

    @Test
    @DisplayName("an unbound profession target has no identity verdict and must be resolved against the world")
    void liveModesDeferToTheSharedResolver() {
        assertTrue(DeliveryRecipientResolver.identityVerdict(professionTarget(), null, GIVER, NAMED).isEmpty());
    }
}
