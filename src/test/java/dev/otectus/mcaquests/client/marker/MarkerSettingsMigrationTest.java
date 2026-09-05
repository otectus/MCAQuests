package dev.otectus.mcaquests.client.marker;

import dev.otectus.mcaquests.McaQuestsConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * That a config written before 1.5.4 keeps the marker behaviour it already had.
 *
 * <p>{@code questMarkerEdgeIndicator} is a boolean somebody may have turned off deliberately. The new
 * {@code marker.edge.mode} has to answer for it without reading the TOML to find out whether a key was
 * written explicitly, which Forge does not offer: {@code AUTO} is the shipped default, so a config
 * that has never heard of the new key follows the old one, and anyone who picks a mode is no longer
 * asked about the boolean at all.
 */
class MarkerSettingsMigrationTest {

    @Test
    @DisplayName("AUTO follows the deprecated boolean, both ways")
    void autoFollowsTheLegacyBoolean() {
        assertSame(McaQuestsConfig.Client.EdgeIndicatorMode.OFFSCREEN_ONLY,
                MarkerSettings.resolveEdgeMode(McaQuestsConfig.Client.EdgeIndicatorMode.AUTO, true));
        assertSame(McaQuestsConfig.Client.EdgeIndicatorMode.DISABLED,
                MarkerSettings.resolveEdgeMode(McaQuestsConfig.Client.EdgeIndicatorMode.AUTO, false));
    }

    @Test
    @DisplayName("an explicit mode ignores the deprecated boolean entirely")
    void explicitModesWin() {
        assertSame(McaQuestsConfig.Client.EdgeIndicatorMode.DISABLED,
                MarkerSettings.resolveEdgeMode(
                        McaQuestsConfig.Client.EdgeIndicatorMode.DISABLED, true));
        assertSame(McaQuestsConfig.Client.EdgeIndicatorMode.OFFSCREEN_OR_OCCLUDED,
                MarkerSettings.resolveEdgeMode(
                        McaQuestsConfig.Client.EdgeIndicatorMode.OFFSCREEN_OR_OCCLUDED, false));
    }

    @Test
    @DisplayName("the defaults a test with no config attached sees are already resolved")
    void defaultsAreResolved() {
        // Not AUTO: DEFAULTS stands in for a read that failed, and AUTO is a question, not an answer.
        assertSame(McaQuestsConfig.Client.EdgeIndicatorMode.OFFSCREEN_ONLY,
                MarkerSettings.DEFAULTS.edgeMode());
    }
}
