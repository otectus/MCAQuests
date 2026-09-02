package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The bridge when no mapping mod is installed — which is the common case, and a dedicated server's
 * case always.
 *
 * <p>Every method is a no-op rather than a throw, so nothing that publishes a destination has to ask
 * first. {@link #isAvailable()} exists for the one place the answer changes what the player sees: the
 * quest log hides its "add waypoint" button when there is no map to add one to, because a button that
 * silently does nothing is worse than no button.
 */
public final class NoopMapWaypointBridge implements MapWaypointBridge {

    public static final NoopMapWaypointBridge INSTANCE = new NoopMapWaypointBridge();

    private NoopMapWaypointBridge() {
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public List<String> describe() {
        return List.of("no mapping mod bound (JourneyMap and Xaero's Minimap are both optional)");
    }

    @Override
    public List<String> probe() {
        return describe();
    }

    @Override
    public void publish(String key, BlockPos pos, ResourceKey<Level> dimension, Component label,
                        GuidanceKind kind) {
    }

    @Override
    public void withdraw(String key) {
    }

    @Override
    public void clear() {
    }

    @Override
    public boolean pin(BlockPos pos, ResourceKey<Level> dimension, Component label, GuidanceKind kind) {
        return false;
    }
}
