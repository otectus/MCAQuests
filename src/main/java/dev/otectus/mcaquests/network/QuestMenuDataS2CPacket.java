package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.QuestMenuStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server to client: the villager header (name / profession / hearts / status) plus the quest cards
 * to render — up to {@code offersPerVillager} offers, or the single active quest (spec sections 20, 21).
 */
public record QuestMenuDataS2CPacket(UUID villagerUuid,
                                     Component villagerName,
                                     Component profession,
                                     int hearts,
                                     QuestMenuStatus status,
                                     List<QuestCard> cards) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuestMenuDataS2CPacket> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_menu_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestMenuDataS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(QuestMenuDataS2CPacket::encode, QuestMenuDataS2CPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static QuestMenuDataS2CPacket noQuest(UUID villager, Component name, Component profession, int hearts,
                                                 QuestMenuStatus status) {
        return new QuestMenuDataS2CPacket(villager, name, profession, hearts, status, List.of());
    }

    public static QuestMenuDataS2CPacket cards(UUID villager, Component name, Component profession, int hearts,
                                               QuestMenuStatus status, List<QuestCard> cards) {
        return new QuestMenuDataS2CPacket(villager, name, profession, hearts, status, cards);
    }

    public static void encode(QuestMenuDataS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
        NetComponents.write(buf, msg.villagerName);
        NetComponents.write(buf, msg.profession);
        buf.writeVarInt(msg.hearts);
        buf.writeEnum(msg.status);
        buf.writeCollection(msg.cards, QuestCard::encode);
    }

    public static QuestMenuDataS2CPacket decode(FriendlyByteBuf buf) {
        return new QuestMenuDataS2CPacket(
                buf.readUUID(),
                NetComponents.read(buf),
                NetComponents.read(buf),
                buf.readVarInt(),
                buf.readEnum(QuestMenuStatus.class),
                buf.readCollection(ArrayList::new, QuestCard::decode));
    }
}
