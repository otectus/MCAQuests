package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.util.LazyOptional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the reason every player lost their quest data on death from 1.0.0 to 1.4.3.
 *
 * <p>Forge invalidates the capability when it removes the dying player, which happens before
 * {@code PlayerEvent.Clone}. {@code reviveCaps()} revives the provider but not the optional it already
 * handed out, so the clone handler read an empty optional and copied nothing — silently, since
 * {@code ifPresent} on an empty optional is a no-op. The provider must therefore hand out a <em>live</em>
 * optional over the <em>same</em> data instance after an invalidation.
 *
 * <p>The capability is never registered in a unit test, so {@code getCapability} could only ever return
 * empty here; the holder is read through the provider's package-private accessor instead.
 */
class PlayerQuestDataProviderTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("invalidation kills the old optional and hands out a live one over the same data")
    void invalidateReplacesTheHolder() {
        PlayerQuestDataProvider provider = new PlayerQuestDataProvider();
        LazyOptional<PlayerQuestData> before = provider.holder();
        assertTrue(before.isPresent(), "a fresh provider must hand out a present optional");
        PlayerQuestData data = before.orElseThrow(AssertionError::new);

        provider.invalidate();

        assertFalse(before.isPresent(),
                "anything holding the old optional must see the invalidation, as Forge's contract requires");
        LazyOptional<PlayerQuestData> after = provider.holder();
        assertTrue(after.isPresent(), "reviveCaps() only revives the provider, so the holder must be live again");
        assertSame(data, after.orElseThrow(AssertionError::new),
                "the respawned player must get the data the dying player had, not a blank one");
    }

    @Test
    @DisplayName("data loaded before the invalidation is still there after it")
    void loadedDataSurvivesInvalidation() {
        PlayerQuestDataProvider provider = new PlayerQuestDataProvider();
        ResourceLocation project = new ResourceLocation("mcaquests", "test_project");
        PlayerQuestData loaded = new PlayerQuestData();
        ProgressionStats.increment(loaded.stats().projectCompletions(), project, 3);
        provider.deserializeNBT(loaded.save()); // stands in for the save being read off disk

        provider.invalidate();

        PlayerQuestData after = provider.holder().orElseThrow(AssertionError::new);
        assertEquals(3, ProgressionStats.count(after.stats().projectCompletions(), project),
                "the data instance is never replaced, so a load before the invalidation must still be visible");
    }
}
