package dev.otectus.mcaquests.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * Component (de)serialization for our payloads. 1.21 removed
 * {@code FriendlyByteBuf.writeComponent/readComponent}; the replacement stream codec needs a
 * {@link RegistryFriendlyByteBuf}, which every play-phase payload buffer is. The methods here keep
 * {@link FriendlyByteBuf} signatures (casting internally) so they slot into
 * {@code writeCollection}/{@code readCollection} call sites exactly where the old
 * {@code FriendlyByteBuf::writeComponent} method references sat.
 */
public final class NetComponents {

    private NetComponents() {
    }

    public static void write(FriendlyByteBuf buf, Component component) {
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode((RegistryFriendlyByteBuf) buf, component);
    }

    public static Component read(FriendlyByteBuf buf) {
        return ComponentSerialization.TRUSTED_STREAM_CODEC.decode((RegistryFriendlyByteBuf) buf);
    }
}
