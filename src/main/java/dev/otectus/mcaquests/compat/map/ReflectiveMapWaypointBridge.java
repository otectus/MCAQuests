package dev.otectus.mcaquests.compat.map;

import dev.otectus.mcaquests.compat.MapWaypointBridge;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Fans one destination out to every mapping mod that bound.
 *
 * <p>Both, one, or neither may be installed, and a player with both should see the waypoint on both —
 * they are two views of the same world, and choosing one for them would be arbitrary. So this is a
 * composite rather than a first-match: {@link #isAvailable()} is true when any backend is usable, and
 * every call goes to all of them.
 *
 * <p>Each backend fails independently. A JourneyMap point release that moves a method leaves the Xaero
 * waypoints working, because the manifests are separate and one unbound member disables exactly the
 * integration that needed it.
 */
public final class ReflectiveMapWaypointBridge implements MapWaypointBridge {

    /**
     * The origin id Xaero files our waypoints under, and the mod id JourneyMap files them under.
     *
     * <p>Both are the mod id, which is what makes {@code removeAllWaypoints} and {@code clear} able to
     * take back everything this mod put on a map without touching anything the player made.
     */
    private static final String MOD_ID = "mcaquests";

    @Nullable
    private final JourneyMapWaypoints journeyMap;
    @Nullable
    private final XaeroWaypoints xaero;

    public ReflectiveMapWaypointBridge() {
        this.journeyMap = JourneyMapWaypoints.resolve(MOD_ID);
        this.xaero = XaeroWaypoints.resolve(new ResourceLocation(MOD_ID, "quests"));
    }

    @Override
    public boolean isAvailable() {
        return (journeyMap != null && journeyMap.isUsable()) || (xaero != null && xaero.isUsable());
    }

    @Override
    public List<String> describe() {
        return report(false);
    }

    @Override
    public List<String> probe() {
        return report(true);
    }

    private List<String> report(boolean withProbe) {
        List<String> lines = new ArrayList<>();
        describe(lines, journeyMap == null ? null : journeyMap.binding(),
                withProbe && journeyMap != null ? journeyMap.probe() : null);
        describe(lines, xaero == null ? null : xaero.binding(),
                withProbe && xaero != null ? xaero.probe() : null);
        if (lines.isEmpty()) {
            lines.add("neither JourneyMap nor Xaero's Minimap is installed");
        }
        return lines;
    }

    private static void describe(List<String> lines, @Nullable MapBinding.Resolution binding,
                                 @Nullable String probe) {
        if (binding == null) {
            return;
        }
        StringBuilder line = new StringBuilder(binding.modName()).append(": ");
        line.append(binding.isBound() ? "bound" : "PARTIAL");
        if (!binding.missing().isEmpty()) {
            line.append(" (unbound: ").append(String.join(", ", binding.missing())).append(')');
        }
        if (probe != null) {
            line.append(" — ").append(probe);
        }
        lines.add(line.toString());
    }

    @Override
    public void publish(String key, BlockPos pos, ResourceKey<Level> dimension, Component label,
                        GuidanceKind kind) {
        if (journeyMap != null && journeyMap.isUsable()) {
            journeyMap.publish(key, pos, dimension, label, kind);
        }
        if (xaero != null && xaero.isUsable()) {
            xaero.publish(key, pos, dimension, label, kind);
        }
    }

    @Override
    public void withdraw(String key) {
        if (journeyMap != null && journeyMap.isUsable()) {
            journeyMap.withdraw(key);
        }
        if (xaero != null && xaero.isUsable()) {
            xaero.withdraw(key);
        }
    }

    @Override
    public void clear() {
        if (journeyMap != null && journeyMap.isUsable()) {
            journeyMap.clear();
        }
        if (xaero != null && xaero.isUsable()) {
            xaero.clear();
        }
    }

    @Override
    public boolean pin(BlockPos pos, ResourceKey<Level> dimension, Component label, GuidanceKind kind) {
        boolean added = false;
        if (journeyMap != null && journeyMap.isUsable()) {
            added |= journeyMap.pin(pos, dimension, label, kind);
        }
        if (xaero != null && xaero.isUsable()) {
            added |= xaero.pin(pos, dimension, label, kind);
        }
        return added;
    }
}
