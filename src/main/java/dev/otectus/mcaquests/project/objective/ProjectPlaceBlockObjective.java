package dev.otectus.mcaquests.project.objective;

import dev.otectus.mcaquests.data.StrictCodecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.target.BlockTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Place matching blocks inside the project's area (spec 0.4.0). Event-driven and credited by
 * {@code ProjectProgressEvents} only when the placement happens inside the project's scope.
 *
 * <p>{@code border_margin} (1.6.6, default 0) widens a village-bound scope beyond the box spanned by the
 * village's registered buildings, which is all MCA's containment test covers with no margin. A wall or a
 * road naturally runs around the outside of that box, so the bundled building projects ask for 32 —
 * the margin MCA itself uses to decide a player is in the village. The allowance applies to this
 * objective's credit and to the build area shown for it, and to nothing else.
 *
 * <p>Credit is per placement during the phase, one per block position: blocks laid before the phase
 * began do not count, and breaking and re-placing a counted block does not count it again.
 */
public record ProjectPlaceBlockObjective(BlockTarget target, int count, int borderMargin) implements ProjectObjective {

    /** The widest allowance a definition may ask for: MCA's own villager margin is 48. */
    public static final int MAX_BORDER_MARGIN = 64;

    /** The pre-1.6.6 shape: no allowance beyond the village's building box. */
    public ProjectPlaceBlockObjective(BlockTarget target, int count) {
        this(target, count, 0);
    }

    public static final Codec<ProjectPlaceBlockObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockTarget.MAP_CODEC.forGetter(ProjectPlaceBlockObjective::target),
            StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "count", 1).forGetter(ProjectPlaceBlockObjective::count),
            // How far outside the village's registered buildings this work still counts (1.6.6).
            StrictCodecs.strictOptional(Codec.intRange(0, MAX_BORDER_MARGIN), "border_margin", 0)
                    .forGetter(ProjectPlaceBlockObjective::borderMargin)
    ).apply(instance, ProjectPlaceBlockObjective::new));

    @Override
    public ProjectObjectiveType<?> type() {
        return ProjectObjectiveTypes.PROJECT_PLACE_BLOCK;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.project_place_block", count, target.describe());
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    public boolean matches(BlockState placed) {
        return target.matches(placed);
    }

    @Override
    public java.util.List<Component> explain(ProjectObjectiveContext context) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("mcaquests.project.help.place.counts", target.describe()));
        lines.add(context.villageBound()
                ? Component.translatable("mcaquests.project.help.area.village", context.villageName(), borderMargin)
                : Component.translatable("mcaquests.project.help.area.anchor"));
        lines.add(Component.translatable("mcaquests.project.help.place.rule"));
        return lines;
    }
}
