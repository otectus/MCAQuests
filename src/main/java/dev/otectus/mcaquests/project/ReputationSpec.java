package dev.otectus.mcaquests.project;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Independent mod-side village-reputation deltas applied as a project progresses (spec 0.4.0). All
 * default to 0, so reputation only ever changes when a datapack opts in. Reputation is stored per
 * scope identity in {@code ProjectSavedData}; it has no effect on MCA's own reputation systems.
 */
public record ReputationSpec(int onPhaseComplete, int onProjectComplete, int onFail) {

    public static final ReputationSpec NONE = new ReputationSpec(0, 0, 0);

    public static final Codec<ReputationSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("on_phase_complete", 0).forGetter(ReputationSpec::onPhaseComplete),
            Codec.INT.optionalFieldOf("on_project_complete", 0).forGetter(ReputationSpec::onProjectComplete),
            Codec.INT.optionalFieldOf("on_fail", 0).forGetter(ReputationSpec::onFail)
    ).apply(instance, ReputationSpec::new));
}
