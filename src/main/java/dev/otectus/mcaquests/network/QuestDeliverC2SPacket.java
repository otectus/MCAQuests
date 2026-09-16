package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.QuestManager;
import dev.otectus.mcaquests.quest.delivery.DeliveryRequest;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Client to server: hand these goods to this villager, for this obligation.
 *
 * <p><b>Intent only.</b> Nothing here is progress and nothing here is believed: the client says which
 * quest copy, which objective, which villager, how many units and out of which slots, and the server
 * re-derives every one of those facts against its own state before a single item moves. "Items
 * delivered" is never something a client can assert.
 *
 * <p>Two fields exist purely because this is a <em>networked</em> request:
 *
 * <ul>
 *   <li>{@code expectedRevision} is the obligation's committed-unit count as the card the player
 *       clicked was drawn. A delivery's revision is its delivered count — it changes on exactly the
 *       event that makes an authorized quantity wrong — so a mismatch means the card is stale and the
 *       request is refused rather than silently re-interpreted.</li>
 *   <li>{@code requestId} is this one click's identity, claimed once by
 *       {@link dev.otectus.mcaquests.quest.delivery.DeliveryService}. A double click, a resend or a
 *       replay carries the same id and is refused.</li>
 * </ul>
 *
 * <p>{@code instance} names the active quest <em>copy</em>, not the quest: a player who abandoned and
 * re-accepted the same quest holds a different obligation, and an in-flight click from the old card
 * must not be applied to the new one. It is absent for a copy that predates the field, which the
 * server accepts by quest id alone exactly as every older packet did.
 *
 * @param thenComplete deliver and, if that leaves the quest finishable here, turn it in through the
 *                     ordinary validated completion flow — the card's Deliver &amp; complete action.
 *                     Never a second payout path of its own.
 */
public record QuestDeliverC2SPacket(UUID villagerUuid, Optional<UUID> instance, ResourceLocation questId,
                                    int objectiveIndex, int units, IntSet slots, int expectedRevision,
                                    UUID requestId, boolean thenComplete) implements CustomPacketPayload {

    public static final Type<QuestDeliverC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_deliver"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestDeliverC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(QuestDeliverC2SPacket::encode, QuestDeliverC2SPacket::decode);

    /** An objective index no definition can reach, and a cheap ceiling on a forged one. */
    private static final int MAX_OBJECTIVE_INDEX = 256;

    /**
     * Every slot index a player inventory has, so a forged list cannot be longer than the thing it
     * claims to describe. {@link DeliveryRequest} intersects them with its own slot policy afterwards.
     */
    private static final int MAX_SLOT = 41;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** A plain Deliver click: everything outstanding, from the default slot policy. */
    public static QuestDeliverC2SPacket all(UUID villagerUuid, Optional<UUID> instance,
                                            ResourceLocation questId, int objectiveIndex,
                                            int expectedRevision, boolean thenComplete) {
        return new QuestDeliverC2SPacket(villagerUuid, instance, questId, objectiveIndex,
                DeliveryRequest.ALL_UNITS, IntSets.EMPTY_SET, expectedRevision, UUID.randomUUID(),
                thenComplete);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.villagerUuid);
        // Lambdas, not method references: 1.21's FriendlyByteBuf carries both a static
        // writeUUID(ByteBuf, UUID) and the instance method, so the reference is ambiguous.
        buf.writeOptional(this.instance, (b, id) -> b.writeUUID(id));
        buf.writeResourceLocation(this.questId);
        buf.writeVarInt(this.objectiveIndex);
        buf.writeVarInt(this.units + 1); // ALL_UNITS is -1, and a varint is cheapest non-negative
        buf.writeCollection(slotList(this.slots), FriendlyByteBuf::writeVarInt);
        buf.writeVarInt(this.expectedRevision + 1);
        buf.writeUUID(this.requestId);
        buf.writeBoolean(this.thenComplete);
    }

    /**
     * Decodes and bounds in one place.
     *
     * <p>Counts and indices are clamped to shapes a real inventory and a real definition can have, and
     * an impossible slot list is rejected outright rather than trimmed — a packet claiming forty-two
     * slots is not a click, and decoding it as one would hide that.
     */
    public static QuestDeliverC2SPacket decode(RegistryFriendlyByteBuf buf) {
        UUID villager = buf.readUUID();
        Optional<UUID> instance = buf.readOptional(b -> b.readUUID());
        ResourceLocation questId = buf.readResourceLocation();
        int index = buf.readVarInt();
        int units = buf.readVarInt() - 1;
        List<Integer> slots = PacketCollections.readList(buf, FriendlyByteBuf::readVarInt);
        int revision = buf.readVarInt() - 1;
        UUID requestId = buf.readUUID();
        boolean thenComplete = buf.readBoolean();
        if (slots.size() > MAX_SLOT) {
            throw new DecoderException("Delivery names " + slots.size() + " source slots; a player "
                    + "inventory has " + MAX_SLOT);
        }
        return new QuestDeliverC2SPacket(villager, instance, questId,
                Math.max(0, Math.min(MAX_OBJECTIVE_INDEX, index)),
                units == DeliveryRequest.ALL_UNITS
                        ? DeliveryRequest.ALL_UNITS
                        : Math.max(0, Math.min(DeliveryRequest.MAX_UNITS, units)),
                slotSet(slots), Math.max(DeliveryRequest.NO_REVISION, revision), requestId, thenComplete);
    }

    /**
     * Every world and inventory mutation this leads to happens on the server thread, which is where
     * NeoForge already runs a play payload handler, and a player cannot flood it with delivery clicks:
     * the shared request budget is taken first, exactly as every other C2S packet here does.
     */
    public static void handle(QuestDeliverC2SPacket msg, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !PacketRequests.allow(player)) {
            return;
        }
        QuestManager.deliver(player, msg.villagerUuid, msg.request(), msg.thenComplete);
    }

    /** This packet as the shared delivery request. The one and only translation between the two. */
    public DeliveryRequest request() {
        return DeliveryRequest.fromMenuPacket(instance.orElse(null), questId, objectiveIndex, villagerUuid,
                units, slots, expectedRevision, requestId);
    }

    private static List<Integer> slotList(IntSet slots) {
        return slots.intStream().sorted().boxed().toList();
    }

    private static IntSet slotSet(List<Integer> slots) {
        IntSet set = new IntOpenHashSet();
        for (int slot : slots) {
            if (slot >= 0 && slot < MAX_SLOT) {
                set.add(slot);
            }
        }
        return set;
    }
}
