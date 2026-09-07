package dev.otectus.mcaquests.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Collection decoding that validates the wire length before allocating storage. */
public final class PacketCollections {
    private PacketCollections() {
    }

    /**
     * Every element in our packets consumes at least one byte. Using the remaining payload as the
     * bound rejects impossible lengths without imposing a new limit on existing datapacks. Storage
     * grows as elements actually decode, so a truncated packet cannot reserve a giant backing array.
     */
    public static <T> List<T> readList(FriendlyByteBuf buf, FriendlyByteBuf.Reader<T> reader) {
        int count = buf.readVarInt();
        if (count < 0 || count > buf.readableBytes()) {
            throw new DecoderException("Invalid collection length " + count
                    + " for " + buf.readableBytes() + " remaining bytes");
        }
        List<T> result = new ArrayList<>(Math.min(count, 1024));
        for (int i = 0; i < count; i++) {
            result.add(reader.apply(buf));
        }
        return result;
    }
}
