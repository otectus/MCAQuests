package dev.otectus.mcaquests.project;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.OptionalInt;

/**
 * The numbers bundled project definitions carried before a deliberate change, for instances that were
 * already under way when the change shipped (1.7.0).
 *
 * <p>Changing a shipped definition must not silently change a project somebody is halfway through. When
 * 1.7.0 moved Known Far and Wide's spirit objectives to a baseline taken at the project's start, the
 * music-and-market phase's Commercial requirement became cumulative (+10: the inn's 5 and the music
 * store's 5) instead of +5 within the phase. An instance from before 1.7.0 has no project-start reading
 * to measure the new rule from, so it keeps the rule it was started under — measured from its phase,
 * with its old number — and diagnostics say so. Keyed by project id, phase key and objective index.
 */
public final class BundledProjectMigrations {

    private static final Map<String, Integer> LEGACY_SPIRIT_DELTAS = Map.of(
            "mcaquests:townstead_known_far_and_wide|music_and_market|1", 5);

    private BundledProjectMigrations() {
    }

    /** The pre-1.7.0 {@code points_delta} of one bundled spirit objective, when 1.7.0 changed it. */
    public static OptionalInt legacySpiritDelta(ResourceLocation projectId, String phaseKey, int objectiveIndex) {
        Integer delta = LEGACY_SPIRIT_DELTAS.get(projectId + "|" + phaseKey + "|" + objectiveIndex);
        return delta == null ? OptionalInt.empty() : OptionalInt.of(delta);
    }
}
