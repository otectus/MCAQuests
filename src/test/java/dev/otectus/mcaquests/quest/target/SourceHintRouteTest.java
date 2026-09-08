package dev.otectus.mcaquests.quest.target;

import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a {@code source} that names a dimension does about a player who is not in it.
 *
 * <p>The route used to be an {@link Optional}, and an empty one meant two opposite things: "nothing
 * to route, carry on" and "a world away, with no portal in sight". Both fell through to the anchor,
 * block, structure and biome searches, which run in the world the player is standing in — so a source
 * that said {@code the_nether} plus a Nether block handed an Overworld player a marker on the nearest
 * Overworld match. That is the one failure {@link SourceHint} exists to refuse: a confidently wrong
 * destination is worse than none, because the player goes.
 *
 * <p>The decision is a rule about three values and not about the world, so it is tested here rather
 * than against a running server.
 */
class SourceHintRouteTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation NETHER = Level.NETHER.location();

    private static SourceHint source(ResourceLocation dimension) {
        return new SourceHint(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.ofNullable(dimension), Optional.empty());
    }

    @Test
    @DisplayName("a source that names no dimension falls through to the searches")
    void noDimensionFallsThrough() {
        assertEquals(SourceHint.Route.SAME_DIMENSION,
                SourceHint.classifyRoute(source(null).dimension(), Level.OVERWORLD, false));
    }

    @Test
    @DisplayName("a player already in the named dimension falls through to the searches")
    void sameDimensionFallsThrough() {
        assertEquals(SourceHint.Route.SAME_DIMENSION,
                SourceHint.classifyRoute(source(NETHER).dimension(), Level.NETHER, false));
    }

    @Test
    @DisplayName("a portal found in this world is the destination")
    void aFoundRouteWins() {
        assertEquals(SourceHint.Route.ROUTE_FOUND,
                SourceHint.classifyRoute(source(NETHER).dimension(), Level.OVERWORLD, true));
    }

    @Test
    @DisplayName("another dimension with no portal is an instruction, not a search of this world")
    void noRouteIsAnInstruction() {
        assertEquals(SourceHint.Route.OTHER_DIMENSION_NO_ROUTE,
                SourceHint.classifyRoute(source(NETHER).dimension(), Level.OVERWORLD, false));
    }

    @Test
    @DisplayName("the instruction carries the destination's dimension and no geometry")
    void theInstructionHasNothingToDraw() {
        GuidanceTarget target = GuidanceTarget.otherDimension(NETHER);

        assertEquals(GuidanceKind.INSTRUCTION, target.kind());
        assertEquals(Level.NETHER, target.dimension(),
                "the dimension names the world the source is in, so nothing draws it here");
        assertTrue(target.entityId().isEmpty());
        assertTrue(target.label().getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals("mcaquests.guidance.dimension.no_route.nether")
                        && contents.getArgs().length == 1,
                "the Nether gets the wording that names its portal");
    }

    @Test
    @DisplayName("a dimension the mod knows nothing about gets the general wording")
    void aModdedDimensionGetsTheGeneralWording() {
        GuidanceTarget target = GuidanceTarget.otherDimension(
                new ResourceLocation("aether", "the_aether"));

        assertTrue(target.label().getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals("mcaquests.guidance.dimension.no_route"),
                "the mod cannot know how that dimension is entered, so it does not claim to");
    }
}
