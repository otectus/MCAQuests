package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.client.QuestClientHandlers;
import dev.otectus.mcaquests.quest.QuestMenuStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server to client: everything the quest menu renders (spec section 20) — the villager header plus,
 * when present, the current quest card (dialogue, objective lines with progress, reward lines).
 */
public record QuestMenuDataS2CPacket(UUID villagerUuid,
                                     Component villagerName,
                                     String professionId,
                                     int hearts,
                                     QuestMenuStatus status,
                                     boolean hasQuest,
                                     String questId,
                                     Component title,
                                     Component dialogue,
                                     List<Component> objectiveLines,
                                     List<Component> rewardLines) {

    public static QuestMenuDataS2CPacket noQuest(UUID villager, Component name, String profession, int hearts,
                                                 QuestMenuStatus status) {
        return new QuestMenuDataS2CPacket(villager, name, profession, hearts, status,
                false, "", Component.empty(), Component.empty(), List.of(), List.of());
    }

    public static QuestMenuDataS2CPacket quest(UUID villager, Component name, String profession, int hearts,
                                               QuestMenuStatus status, ResourceLocation questId, Component title,
                                               Component dialogue, List<Component> objectives, List<Component> rewards) {
        return new QuestMenuDataS2CPacket(villager, name, profession, hearts, status,
                true, questId.toString(), title, dialogue, objectives, rewards);
    }

    public static void encode(QuestMenuDataS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.villagerUuid);
        buf.writeComponent(msg.villagerName);
        buf.writeUtf(msg.professionId);
        buf.writeVarInt(msg.hearts);
        buf.writeEnum(msg.status);
        buf.writeBoolean(msg.hasQuest);
        buf.writeUtf(msg.questId);
        buf.writeComponent(msg.title);
        buf.writeComponent(msg.dialogue);
        buf.writeCollection(msg.objectiveLines, FriendlyByteBuf::writeComponent);
        buf.writeCollection(msg.rewardLines, FriendlyByteBuf::writeComponent);
    }

    public static QuestMenuDataS2CPacket decode(FriendlyByteBuf buf) {
        UUID villager = buf.readUUID();
        Component name = buf.readComponent();
        String profession = buf.readUtf();
        int hearts = buf.readVarInt();
        QuestMenuStatus status = buf.readEnum(QuestMenuStatus.class);
        boolean hasQuest = buf.readBoolean();
        String questId = buf.readUtf();
        Component title = buf.readComponent();
        Component dialogue = buf.readComponent();
        List<Component> objectives = buf.readCollection(ArrayList::new, FriendlyByteBuf::readComponent);
        List<Component> rewards = buf.readCollection(ArrayList::new, FriendlyByteBuf::readComponent);
        return new QuestMenuDataS2CPacket(villager, name, profession, hearts, status,
                hasQuest, questId, title, dialogue, objectives, rewards);
    }

    public static void handle(QuestMenuDataS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QuestClientHandlers.openMenu(msg)));
        context.setPacketHandled(true);
    }
}
