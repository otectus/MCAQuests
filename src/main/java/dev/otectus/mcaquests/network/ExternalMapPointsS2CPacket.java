package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.api.ExternalMapPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A complete, per-viewer replacement for one integration's atlas points (1.7.0).
 *
 * <p>The server has already filtered the points to what this viewer may know; the client only draws
 * them. The whole set is sent each time, so a point missing from a later snapshot is gone.
 */
public record ExternalMapPointsS2CPacket(String owner, List<ExternalMapPoint> points) {

    /** More than any integration has a reason to show at once; a larger snapshot is refused. */
    public static final int MAX_POINTS = 128;

    public ExternalMapPointsS2CPacket {
        if (owner == null || !owner.matches("[a-z0-9_.-]{1,64}") || points == null || points.size() > MAX_POINTS) {
            throw new IllegalArgumentException("invalid external map snapshot");
        }
        points = List.copyOf(points);
    }

    public static void encode(ExternalMapPointsS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.owner, 64);
        buf.writeVarInt(msg.points.size());
        for (ExternalMapPoint point : msg.points) {
            buf.writeUtf(point.key(), 160);
            buf.writeResourceLocation(point.dimension().location());
            buf.writeBlockPos(point.position());
            buf.writeUtf(point.label(), 128);
            buf.writeEnum(point.kind());
            buf.writeBoolean(point.approximate());
            buf.writeBoolean(point.lastKnown());
        }
    }

    public static ExternalMapPointsS2CPacket decode(FriendlyByteBuf buf) {
        String owner = buf.readUtf(64);
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_POINTS) {
            throw new IllegalArgumentException("external map snapshot too large");
        }
        List<ExternalMapPoint> points = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String key = buf.readUtf(160);
            ResourceKey<Level> dimension = ExternalMapPoint.dimension(buf.readResourceLocation().toString());
            BlockPos pos = buf.readBlockPos();
            String label = buf.readUtf(128);
            ExternalMapPoint.Kind kind = buf.readEnum(ExternalMapPoint.Kind.class);
            boolean approximate = buf.readBoolean();
            boolean lastKnown = buf.readBoolean();
            points.add(new ExternalMapPoint(key, dimension, pos, label, kind, approximate, lastKnown));
        }
        return new ExternalMapPointsS2CPacket(owner, points);
    }

    public static void handle(ExternalMapPointsS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> acceptOnClient(msg)));
        context.setPacketHandled(true);
    }

    /**
     * Hands the snapshot to Map Atlases' client runtime, reached by name so this common class never links
     * the client-only package. When that backend is absent or unavailable the points are dropped quietly.
     */
    private static void acceptOnClient(ExternalMapPointsS2CPacket msg) {
        try {
            Class.forName("dev.otectus.mcaquests.compat.mapatlases.client.AtlasRuntime")
                    .getMethod("acceptExternal", String.class, List.class)
                    .invoke(null, msg.owner, msg.points);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Optional backend is absent or unavailable.
        }
    }
}
