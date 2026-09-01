package dev.otectus.mcaquests.project.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;

import dev.otectus.mcaquests.compat.TownsteadSpiritView;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * A project phase that finishes when the village has grown into something (Townstead spec 5.4).
 *
 * <pre>{@code
 * { "type": "mcaquests:townstead_spirit_project", "spirit": "industrious", "points_delta": 60 }
 * }</pre>
 *
 * <p>{@code points_delta} is measured from a reading frozen when the phase opens, so a village that
 * was already industrious does not hand the phase over for free. The starting value lives in the
 * shared scratch bag, which is why {@code SharedObjectiveProgress} grew one.
 */
public record TownsteadSpiritProjectObjective(Optional<String> spirit, OptionalInt pointsDelta,
                                              OptionalInt targetTier) implements PollingProjectObjective {

    private static final String K_BASELINE = "townstead_spirit_baseline";

    public static final Codec<TownsteadSpiritProjectObjective> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(Codec.STRING, "spirit")
                            .forGetter(TownsteadSpiritProjectObjective::spirit),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "points_delta")
                            .forGetter((TownsteadSpiritProjectObjective o) -> box(o.pointsDelta())),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "target_tier")
                            .forGetter((TownsteadSpiritProjectObjective o) -> box(o.targetTier()))
            ).apply(instance, (s, delta, tier) ->
                    new TownsteadSpiritProjectObjective(s, unbox(delta), unbox(tier))));

    private static Optional<Integer> box(OptionalInt value) {
        return value.isPresent() ? Optional.of(value.getAsInt()) : Optional.empty();
    }

    private static OptionalInt unbox(Optional<Integer> value) {
        return value.map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    @Override
    public ProjectObjectiveType<?> type() {
        return ProjectObjectiveTypes.TOWNSTEAD_SPIRIT;
    }

    @Override
    public int required() {
        return pointsDelta.orElseGet(() -> targetTier.orElse(1));
    }

    @Override
    public boolean poll(MinecraftServer server, ServerLevel level, ProjectDefinition definition,
                        ProjectState state, SharedObjectiveProgress progress) {
        OptionalInt village = state.villageId();
        if (village.isEmpty() || !TownsteadBridge.Holder.get().has(TownsteadCapability.READ_SPIRIT)) {
            return false;
        }
        TownsteadSpiritView view = new TownsteadEvaluation()
                .spirit(level, village.getAsInt()).orElse(null);
        if (view == null) {
            return false;
        }
        int points = spirit.map(view::pointsFor).orElseGet(view::total);

        int reached;
        if (targetTier.isPresent()) {
            reached = view.tier();
        } else {
            if (!progress.extra().contains(K_BASELINE)) {
                progress.extra().putInt(K_BASELINE, points);
                return false; // the pass that sets the start line makes no progress along it
            }
            reached = Math.max(0, points - progress.extra().getInt(K_BASELINE));
        }
        if (reached <= progress.count()) {
            return false; // spirit falls when a building is lost; never walk a village backwards
        }
        progress.setCount(Math.min(required(), reached));
        return true;
    }

    @Override
    public Component describe() {
        boolean tier = targetTier.isPresent();
        if (spirit.isEmpty()) {
            return Component.translatable(tier
                    ? "mcaquests.project.objective.townstead_spirit_tier_any"
                    : "mcaquests.project.objective.townstead_spirit_points_any", required());
        }
        return Component.translatable(tier
                        ? "mcaquests.project.objective.townstead_spirit_tier"
                        : "mcaquests.project.objective.townstead_spirit_points",
                required(), dev.otectus.mcaquests.quest.TownsteadNames.spirit(spirit.get()));
    }
}
