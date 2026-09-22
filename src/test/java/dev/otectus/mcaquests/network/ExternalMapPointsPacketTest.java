package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.api.ExternalMapPoint;
import dev.otectus.mcaquests.support.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalMapPointsPacketTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    @Test
    @DisplayName("a filtered snapshot round-trips, and a bad owner or an oversize snapshot is refused")
    void filteredSnapshotRoundTripsAndRejectsOversize() {
        ExternalMapPoint site = new ExternalMapPoint("site:1", Level.OVERWORLD, new BlockPos(4, 70, -8),
                "Known trade post", ExternalMapPoint.Kind.SITE, false, false);
        ExternalMapPointsS2CPacket packet = new ExternalMapPointsS2CPacket("ultima_kingdoms", List.of(site));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ExternalMapPointsS2CPacket.encode(packet, buf);
            assertEquals(packet, ExternalMapPointsS2CPacket.decode(buf));
        } finally {
            buf.release();
        }

        assertThrows(IllegalArgumentException.class, () -> new ExternalMapPointsS2CPacket("bad owner", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ExternalMapPointsS2CPacket("ultima_kingdoms",
                Collections.nCopies(ExternalMapPointsS2CPacket.MAX_POINTS + 1, site)));
    }
}
