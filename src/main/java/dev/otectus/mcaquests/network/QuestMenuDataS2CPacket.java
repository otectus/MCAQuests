package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.QuestMenuStatus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * Server to client: the villager header (name / profession / hearts / greeting / status) plus the
 * quest cards to render — up to {@code offersPerVillager} offers, or the single active quest (spec
 * sections 20, 21).
 *
 * <p>{@code greeting} is what this villager says on opening the menu, drawn from the shared voice
 * pools and varying with their personality. It is empty whenever no pool has anything for them, and
 * the header simply shows one line fewer.
 *
 * <p>{@code notice} is the result of whatever the player just did — "Delivered 1 of 2", "They have no
 * room to take these" — carried on the refreshed screen rather than only into the chat stream behind
 * it. A delivery that was refused silently is the failure this whole area of the mod exists to fix, so
 * the answer travels with the cards it changed.
 */
public record QuestMenuDataS2CPacket(UUID villagerUuid,
                                     Component villagerName,
                                     Component profession,
                                     int hearts,
                                     Component greeting,
                                     QuestMenuStatus status,
                                     List<QuestCard> cards,
                                     Component notice) implements CustomPacketPayload {

    public static final Type<QuestMenuDataS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_menu_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestMenuDataS2CPacket> STREAM_CODEC =
            CustomPacketPayload.codec(QuestMenuDataS2CPacket::encode, QuestMenuDataS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static QuestMenuDataS2CPacket noQuest(UUID villager, Component name, Component profession, int hearts,
                                                 QuestMenuStatus status) {
        return new QuestMenuDataS2CPacket(villager, name, profession, hearts, Component.empty(), status,
                List.of(), Component.empty());
    }

    public static QuestMenuDataS2CPacket cards(UUID villager, Component name, Component profession, int hearts,
                                               QuestMenuStatus status, List<QuestCard> cards) {
        return cards(villager, name, profession, hearts, Component.empty(), status, cards);
    }

    public static QuestMenuDataS2CPacket cards(UUID villager, Component name, Component profession, int hearts,
                                               Component greeting, QuestMenuStatus status,
                                               List<QuestCard> cards) {
        return new QuestMenuDataS2CPacket(villager, name, profession, hearts, greeting, status, cards,
                Component.empty());
    }

    /** The same screen with a result line on it, for a click that had something to report. */
    public QuestMenuDataS2CPacket withNotice(Component line) {
        return new QuestMenuDataS2CPacket(villagerUuid, villagerName, profession, hearts, greeting, status,
                cards, line == null ? Component.empty() : line);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.villagerUuid);
        NetComponents.write(buf, this.villagerName);
        NetComponents.write(buf, this.profession);
        buf.writeVarInt(this.hearts);
        NetComponents.write(buf, this.greeting);
        buf.writeEnum(this.status);
        buf.writeCollection(this.cards, (b, v) -> QuestCard.encode((RegistryFriendlyByteBuf) b, v));
        NetComponents.write(buf, this.notice);
    }

    public static QuestMenuDataS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new QuestMenuDataS2CPacket(
                buf.readUUID(),
                NetComponents.read(buf),
                NetComponents.read(buf),
                buf.readVarInt(),
                NetComponents.read(buf),
                buf.readEnum(QuestMenuStatus.class),
                PacketCollections.readList(buf, b -> QuestCard.decode((RegistryFriendlyByteBuf) b)),
                NetComponents.read(buf));
    }
}
