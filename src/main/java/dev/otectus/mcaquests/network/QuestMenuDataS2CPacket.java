package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.QuestClientHandlers;
import dev.otectus.mcaquests.quest.QuestMenuStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server to client: the villager header (name / profession / hearts / greeting / status) plus the
 * quest cards to render — up to {@code offersPerVillager} offers, or the single active quest (spec
 * sections 20, 21).
 *
 * <p>{@code greeting} is what this villager says on opening the menu, drawn from the shared voice
 * pools and varying with their personality. It is empty whenever no pool has anything for them, and
 * the header simply shows one line fewer.
 */
public record QuestMenuDataS2CPacket(UUID villagerUuid,
                                     Component villagerName,
                                     Component profession,
                                     int hearts,
                                     Component greeting,
                                     QuestMenuStatus status,
                                     List<QuestCard> cards) {

    public static QuestMenuDataS2CPacket noQuest(UUID villager, Component name, Component profession, int hearts,
                                                 QuestMenuStatus status) {
        return new QuestMenuDataS2CPacket(villager, name, profession, hearts, Component.empty(), status,
                List.of());
    }

    public static QuestMenuDataS2CPacket cards(UUID villager, Component name, Component profession, int hearts,
                                               QuestMenuStatus status, List<QuestCard> cards) {
        return cards(villager, name, profession, hearts, Component.empty(), status, cards);
    }

    public static QuestMenuDataS2CPacket cards(UUID villager, Component name, Component profession, int hearts,
                                               Component greeting, QuestMenuStatus status,
                                               List<QuestCard> cards) {
        return new QuestMenuDataS2CPacket(villager, name, profession, hearts, greeting, status, cards);
    }

    public static void encode(QuestMenuDataS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
        buf.writeComponent(msg.villagerName);
        buf.writeComponent(msg.profession);
        buf.writeVarInt(msg.hearts);
        buf.writeComponent(msg.greeting);
        buf.writeEnum(msg.status);
        buf.writeCollection(msg.cards, QuestCard::encode);
    }

    public static QuestMenuDataS2CPacket decode(FriendlyByteBuf buf) {
        return new QuestMenuDataS2CPacket(
                buf.readUUID(),
                buf.readComponent(),
                buf.readComponent(),
                buf.readVarInt(),
                buf.readComponent(),
                buf.readEnum(QuestMenuStatus.class),
                buf.readCollection(ArrayList::new, QuestCard::decode));
    }

    public static void handle(QuestMenuDataS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QuestClientHandlers.openMenu(msg)));
        context.setPacketHandled(true);
    }
}
