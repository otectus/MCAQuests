package dev.otectus.mcaquests.client.marker;

import dev.otectus.mcaquests.McaQuestsConfig;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The marker's slice of the client config, read once and held.
 *
 * <p>Sixteen {@code ModConfigSpec} lookups per frame is sixteen map lookups and sixteen boxed values per
 * frame, all to answer questions that change when a player opens a config screen and at no other
 * time. This is that answer, taken once and thrown away on
 * {@code ModConfigEvent} — see {@code QuestClientSetup}.
 *
 * <p>Held in an {@link AtomicReference} because the invalidation runs on the config thread and the
 * read runs on the render thread. Building a fresh snapshot on both sides of a race is harmless: they
 * would be identical.
 *
 * @param enabled       {@code showQuestMarker}
 * @param maxDistance   {@code questMarkerMaxDistance}
 * @param style         {@code questMarkerStyle}
 * @param occlusion     {@code questMarkerOcclusion}
 * @param edgeIndicator {@code questMarkerEdgeIndicator}
 * @param labels        {@code questMarkerLabels}
 * @param highContrast  {@code questMarkerHighContrast}
 * @param reducedMotion {@code questMarkerReducedMotion}
 * @param edgeMode      {@code marker.edge.mode}, already resolved out of {@code AUTO}
 * @param edgeInset     {@code marker.edge.inset}
 * @param edgeSmoothingMs      {@code marker.edge.smoothingMs}
 * @param edgeEnterHysteresisPx {@code marker.edge.enterHysteresisPx}
 * @param edgeExitHysteresisPx  {@code marker.edge.exitHysteresisPx}
 * @param edgeTransitionFrames  {@code marker.edge.transitionFrames}
 * @param edgeShowDistance      {@code marker.edge.showDistance}
 * @param edgeOcclusionSampleMs {@code marker.edge.occlusionSampleMs}
 */
public record MarkerSettings(boolean enabled, int maxDistance,
                             McaQuestsConfig.Client.MarkerStyle style,
                             McaQuestsConfig.Client.MarkerOcclusion occlusion,
                             boolean edgeIndicator,
                             McaQuestsConfig.Client.MarkerLabels labels,
                             boolean highContrast, boolean reducedMotion,
                             McaQuestsConfig.Client.EdgeIndicatorMode edgeMode,
                             int edgeInset, int edgeSmoothingMs,
                             int edgeEnterHysteresisPx, int edgeExitHysteresisPx,
                             int edgeTransitionFrames, boolean edgeShowDistance,
                             int edgeOcclusionSampleMs) {

    /** The shipped defaults, and what a unit test with no config attached sees. */
    public static final MarkerSettings DEFAULTS = new MarkerSettings(true, 256,
            McaQuestsConfig.Client.MarkerStyle.COMPACT,
            McaQuestsConfig.Client.MarkerOcclusion.DIM_OUTLINE, true,
            McaQuestsConfig.Client.MarkerLabels.NEARBY, false, false,
            McaQuestsConfig.Client.EdgeIndicatorMode.OFFSCREEN_ONLY,
            18, 80, 4, 2, 2, true, 50);

    /**
     * What {@code AUTO} means for this config.
     *
     * <p>The spec's migration rule without reading the TOML: {@code AUTO} is the shipped default, so
     * a config written before 1.5.4 -- which has no {@code marker.edge.mode} at all and gets that
     * default -- follows the boolean it does have. Anyone who picks a mode explicitly stops being
     * asked about the boolean.
     */
    public static McaQuestsConfig.Client.EdgeIndicatorMode resolveEdgeMode(
            McaQuestsConfig.Client.EdgeIndicatorMode configured, boolean legacyEdgeIndicator) {
        if (configured != McaQuestsConfig.Client.EdgeIndicatorMode.AUTO) {
            return configured;
        }
        return legacyEdgeIndicator
                ? McaQuestsConfig.Client.EdgeIndicatorMode.OFFSCREEN_ONLY
                : McaQuestsConfig.Client.EdgeIndicatorMode.DISABLED;
    }

    private static final AtomicReference<MarkerSettings> CACHE = new AtomicReference<>();

    /** The current settings, built on first use after every reload. */
    public static MarkerSettings current() {
        MarkerSettings cached = CACHE.get();
        if (cached != null) {
            return cached;
        }
        MarkerSettings built = read();
        CACHE.set(built);
        return built;
    }

    /** Throw the snapshot away; the next frame builds a new one. */
    public static void invalidate() {
        CACHE.set(null);
    }

    private static MarkerSettings read() {
        try {
            McaQuestsConfig.Client client = McaQuestsConfig.CLIENT;
            return new MarkerSettings(
                    client.showQuestMarker.get(),
                    client.questMarkerMaxDistance.get(),
                    client.questMarkerStyle.get(),
                    client.questMarkerOcclusion.get(),
                    client.questMarkerEdgeIndicator.get(),
                    client.questMarkerLabels.get(),
                    client.questMarkerHighContrast.get(),
                    client.questMarkerReducedMotion.get(),
                    resolveEdgeMode(client.markerEdgeMode.get(),
                            client.questMarkerEdgeIndicator.get()),
                    client.markerEdgeInset.get(),
                    client.markerEdgeSmoothingMs.get(),
                    client.markerEdgeEnterHysteresisPx.get(),
                    client.markerEdgeExitHysteresisPx.get(),
                    client.markerEdgeTransitionFrames.get(),
                    client.markerEdgeShowDistance.get(),
                    client.markerEdgeOcclusionSampleMs.get());
        } catch (RuntimeException e) {
            return DEFAULTS; // no config attached (a unit test); the shipped defaults
        }
    }
}
