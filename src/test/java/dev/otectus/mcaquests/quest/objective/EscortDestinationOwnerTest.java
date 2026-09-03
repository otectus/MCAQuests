package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.quest.target.LocationAnchor;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An escort must not walk one villager to another villager's bed.
 *
 * <p>The {@code bed} and {@code workstation} anchors resolve the <em>giver's</em> home unless the anchor
 * names somebody. That is right for "walk me home", where the giver is the person being walked, and
 * silently wrong the moment the escortee is anyone else — the shipped "one last walk" quest told the
 * player to see an ageing parent "safely back to their bed" and sent them to the parent's child's house
 * instead. Nothing failed: the escort completed, at the wrong building, and the only way to notice was
 * to know both villagers' addresses.
 *
 * <p>So it is a load error now, and this is the test that keeps it one. The bundled pack is checked
 * separately by {@code BuiltinPackValidatesTest}; what is checked here is the rule itself, including
 * that it stays quiet on the three shapes that are fine.
 */
class EscortDestinationOwnerTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation QUEST = ResourceLocation.fromNamespaceAndPath("mcaquests", "test_escort");

    private static VillagerTarget self() {
        return VillagerTarget.SELF;
    }

    private static VillagerTarget parent() {
        return new VillagerTarget(VillagerTarget.Mode.FAMILY, Optional.empty(),
                Optional.of("parent"), Optional.empty(), Optional.of("nearby"));
    }

    private static LocationAnchor bed(Optional<VillagerTarget> owner) {
        return new LocationAnchor(LocationAnchor.Type.BED, Optional.empty(), owner, Optional.empty());
    }

    private static LocationAnchor homeVillage() {
        return new LocationAnchor(LocationAnchor.Type.HOME_VILLAGE, Optional.empty(), Optional.empty(),
                Optional.empty());
    }

    private static List<String> validate(VillagerTarget escortee, LocationAnchor destination) {
        List<String> errors = new ArrayList<>();
        new EscortEntityObjective(escortee, destination, 4, true, true, 6,
                Optional.empty(), Optional.empty()).validate(QUEST, 0, errors);
        return errors;
    }

    @Test
    @DisplayName("escorting somebody else to an unowned bed is refused")
    void unownedBedForAnotherVillagerIsAnError() {
        List<String> errors = validate(parent(), bed(Optional.empty()));

        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("resolves to the GIVER's"), errors.get(0));
    }

    @Test
    @DisplayName("escorting somebody else to their own bed is fine")
    void ownedBedIsAccepted() {
        assertTrue(validate(parent(), bed(Optional.of(parent()))).isEmpty());
    }

    @Test
    @DisplayName("escorting the giver to an unowned bed is fine, because it is theirs")
    void giverToOwnBedIsAccepted() {
        // The common shape by far, and the reason the field defaults to the giver rather than being
        // made mandatory: every pack written before the field existed keeps working untouched.
        assertTrue(validate(self(), bed(Optional.empty())).isEmpty());
    }

    @Test
    @DisplayName("an anchor that was never about one villager is left alone")
    void villageAnchorIsAccepted() {
        assertTrue(validate(parent(), homeVillage()).isEmpty());
    }
}
