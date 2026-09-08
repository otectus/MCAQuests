package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.quest.target.LocationAnchor;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A frozen escort destination belongs to a world, not merely to three numbers.
 *
 * <p>Without the dimension, arrival was tested against whatever level the caller passed, so an
 * escortee standing at (120, 64, -40) in the Nether counted as having arrived at (120, 64, -40) in
 * the Overworld. {@code FrozenLocation}, the sibling record for frozen anchors, has carried its
 * dimension since 1.4.1 for exactly this reason.
 *
 * <p>The keys are spelled out here rather than read from the objective, because they are a save
 * format: a rename would be an escort that silently re-freezes its destination on an existing world,
 * and this test is meant to notice that.
 */
class EscortDestinationDimensionTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final String K_FROZEN = "destFrozen";
    private static final String K_DEST_X = "destX";
    private static final String K_DEST_Y = "destY";
    private static final String K_DEST_Z = "destZ";
    private static final String K_DEST_DIM = "destDim";

    private static final BlockPos DESTINATION = new BlockPos(120, 64, -40);

    private static EscortEntityObjective objective() {
        LocationAnchor home = new LocationAnchor(LocationAnchor.Type.HOME_VILLAGE, Optional.empty(),
                Optional.empty(), Optional.empty());
        return new EscortEntityObjective(VillagerTarget.SELF, home, 6, true, false, 6,
                Optional.empty(), Optional.empty());
    }

    /** A progress bag frozen the way a save carries it, with or without the dimension key. */
    private static ObjectiveProgress frozen(String dimension) {
        ObjectiveProgress progress = new ObjectiveProgress();
        CompoundTag extra = progress.extra();
        extra.putInt(K_DEST_X, DESTINATION.getX());
        extra.putInt(K_DEST_Y, DESTINATION.getY());
        extra.putInt(K_DEST_Z, DESTINATION.getZ());
        if (dimension != null) {
            extra.putString(K_DEST_DIM, dimension);
        }
        extra.putBoolean(K_FROZEN, true);
        return progress;
    }

    @Test
    @DisplayName("a destination frozen with its dimension reads back unchanged")
    void theDimensionRoundTrips() {
        ObjectiveProgress progress = frozen("minecraft:the_nether");

        EscortEntityObjective.FrozenDest dest =
                objective().frozenDest(progress, Level.OVERWORLD.location()).orElseThrow();

        assertEquals(DESTINATION, dest.pos());
        assertEquals(Level.NETHER.location(), dest.dimension(),
                "the world it was frozen in, not the world it is being read in");
    }

    @Test
    @DisplayName("a pre-1.6.3 destination is backfilled from the evaluating world, once")
    void aLegacyDestinationIsBackfilled() {
        ObjectiveProgress progress = frozen(null);

        EscortEntityObjective.FrozenDest first =
                objective().frozenDest(progress, Level.NETHER.location()).orElseThrow();

        // Backfilling with the world the objective is being evaluated in reproduces what 1.6.2 did on
        // this same poll, which is the only answer that cannot change a save in flight.
        assertEquals(Level.NETHER.location(), first.dimension());
        assertEquals("minecraft:the_nether", progress.extra().getString(K_DEST_DIM),
                "the key must be written back, or the answer changes with the player");

        // And from then on it is the destination's own dimension, wherever the player has wandered.
        EscortEntityObjective.FrozenDest second =
                objective().frozenDest(progress, Level.OVERWORLD.location()).orElseThrow();
        assertEquals(Level.NETHER.location(), second.dimension());
    }

    @Test
    @DisplayName("an unfrozen destination stays unfrozen and writes nothing")
    void nothingIsBackfilledBeforeTheFreeze() {
        ObjectiveProgress progress = new ObjectiveProgress();

        assertTrue(objective().frozenDest(progress, Level.OVERWORLD.location()).isEmpty());
        assertFalse(progress.extra().contains(K_DEST_DIM),
                "a destination that was never frozen has no dimension to record");
    }

    @Test
    @DisplayName("arrival is refused in the wrong world and allowed in the right one")
    void arrivalIsDimensionAware() {
        EscortEntityObjective.FrozenDest dest = objective()
                .frozenDest(frozen("minecraft:overworld"), Level.NETHER.location()).orElseThrow();

        assertFalse(EscortEntityObjective.inDestinationDimension(dest, Level.NETHER.location()),
                "the same three coordinates in another world are not the destination");
        assertTrue(EscortEntityObjective.inDestinationDimension(dest, Level.OVERWORLD.location()));
    }

    @Test
    @DisplayName("a corrupt dimension id is treated as absent rather than trusted")
    void anUnparseableDimensionIsBackfilled() {
        ObjectiveProgress progress = frozen("Not A Dimension");

        EscortEntityObjective.FrozenDest dest =
                objective().frozenDest(progress, Level.OVERWORLD.location()).orElseThrow();

        assertEquals(Level.OVERWORLD.location(), dest.dimension());
        assertEquals("minecraft:overworld", progress.extra().getString(K_DEST_DIM));
    }

    @Test
    @DisplayName("the destination's dimension is unrelated to the escort's own fields")
    void theObjectiveItselfIsUnchanged() {
        // A guard on the record's shape: FrozenDest gained a component, and the position must not
        // have quietly swapped places with it in the canonical constructor.
        EscortEntityObjective.FrozenDest dest = new EscortEntityObjective.FrozenDest(
                DESTINATION, Level.END.location(), java.util.OptionalInt.of(7));

        assertEquals(DESTINATION, dest.pos());
        assertEquals(Level.END.location(), dest.dimension());
        assertEquals(7, dest.villageId().getAsInt());
    }

    @Test
    @DisplayName("the dimension is stored as a resource location string")
    void theKeyIsAResourceLocation() {
        ObjectiveProgress progress = frozen(null);
        objective().frozenDest(progress, new ResourceLocation("aether", "the_aether"));

        assertEquals("aether:the_aether", progress.extra().getString(K_DEST_DIM));
    }
}
