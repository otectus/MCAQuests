package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.DisplayNames;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Enter a specified dimension (spec section 14). Checked on a throttled player tick; sticky once visited. */
public record VisitDimensionObjective(ResourceLocation dimension) implements QuestObjective {

    public static final Codec<VisitDimensionObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(VisitDimensionObjective::dimension)
    ).apply(instance, VisitDimensionObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.VISIT_DIMENSION;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.visit_dimension", DisplayNames.name(dimension));
    }

    /**
     * The way in, from wherever the player is standing — not the dimension itself.
     *
     * <p>This is the case the plan's example was drawn from: a quest that says "go to the Nether"
     * pointed at nothing at all, and a player who did not already know to build a portal had no way
     * to act on it. So the marker is the nearest lit portal <em>in the dimension the player is in</em>,
     * and it stops the instant they step through, because the objective is satisfied and this returns
     * empty. Nothing is left behind pointing at the overworld.
     *
     * <p>Nothing is drawn when there is no portal in range. The mod will not mark open ground and
     * call it a route.
     */
    @Override
    public java.util.Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> guidance(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress)) {
            return java.util.Optional.empty();
        }
        ResourceKey<Level> destination = ResourceKey.create(Registries.DIMENSION, dimension);
        return dev.otectus.mcaquests.quest.guidance.Portals.routeTo(level, player.blockPosition(), destination)
                .map(pos -> dev.otectus.mcaquests.quest.guidance.GuidanceTarget.ofPos(pos, level, dev.otectus.mcaquests.quest.guidance.GuidanceKind.PORTAL,
                        Component.translatable("mcaquests.guidance.route_to", DisplayNames.name(dimension)),
                        ARRIVE_RADIUS, false));
    }

    /** How close counts as standing at the portal. */
    private static final int ARRIVE_RADIUS = 8;

    @Override
    public int required() {
        return 1;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(progress.count(), 1);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= 1;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    public boolean matches(ServerPlayer player) {
        return player.level().dimension().location().equals(dimension);
    }
}
