package dev.otectus.mcaquests.network;

import dev.otectus.mcaquests.quest.QuestMenuStatus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One quest as shown in the menu — an offer or the active quest (spec sections 8, 21). The screen
 * renders these; the {@code questId} drives the Accept/Decline/Complete/Abandon C2S packets.
 *
 * <p>Three things arrived with the interface rewrite, and all three are things the server already
 * knew and simply never said:
 *
 * <ul>
 *   <li>{@code objectives} became {@link CardObjective}s rather than sentences with the counts
 *       written into them, so the client can draw a bar and a state icon instead of reading a number
 *       out of a string it cannot parse.</li>
 *   <li>{@code rewardIcons} are preview stacks from {@code QuestReward.previewIcons()}. The reward
 *       text was always there; the icons are what make "an emerald or a diamond?" a glance rather
 *       than a read.</li>
 *   <li>{@code difficulty} is the band the quest has declared since difficulty existed. It set the
 *       currency reward and was shown to nobody.</li>
 * </ul>
 *
 * <p>Three more arrived with delivery. {@code instance} names <em>this copy</em> of the quest, so a
 * click cannot be applied to a re-accepted one; {@code state} is the card's own state, because a menu
 * showing two active quests had one global status and drew Complete on the in-progress one; and
 * {@code deliverCompletes} is the server's answer to "would paying this finish the quest here", which
 * is the only honest basis for a combined Deliver &amp; complete action.
 *
 * @param difficulty {@code "easy"}, {@code "medium"}, {@code "hard"}, or empty when the quest
 *                   declares none — most do not, and an absent band means "no badge" rather than
 *                   "easy"
 * @param instance   the active quest copy this card was built from, absent for an offer and for the
 *                   informational cards a villager shows when they have nothing
 * @param state      what this one card is: an offer, in progress, ready to hand in, or a line of
 *                   explanation with no buttons at all
 * @param deliverCompletes whether paying this card's single outstanding delivery would leave the
 *                   quest complete <em>and</em> turn-in-able at this villager
 */
public record QuestCard(ResourceLocation questId, Component title, Component chainLabel, Component dialogue,
                        List<CardObjective> objectives, List<Component> rewards,
                        List<ItemStack> rewardIcons, String difficulty,
                        Optional<UUID> instance, QuestMenuStatus state, boolean deliverCompletes) {

    /**
     * The pre-1.6.5 shape: a card with no copy identity, no state of its own and no delivery.
     *
     * <p>Kept for the informational cards — the "nothing for you today" and cooldown lines — which are
     * rendered under a status that draws no buttons, and so genuinely have none of the three.
     */
    public QuestCard(ResourceLocation questId, Component title, Component chainLabel, Component dialogue,
                     List<CardObjective> objectives, List<Component> rewards,
                     List<ItemStack> rewardIcons, String difficulty) {
        this(questId, title, chainLabel, dialogue, objectives, rewards, rewardIcons, difficulty,
                Optional.empty(), QuestMenuStatus.NO_QUESTS, false);
    }

    public static void encode(RegistryFriendlyByteBuf buf, QuestCard card) {
        buf.writeResourceLocation(card.questId);
        NetComponents.write(buf, card.title);
        NetComponents.write(buf, card.chainLabel);
        NetComponents.write(buf, card.dialogue);
        buf.writeCollection(card.objectives, (b, v) -> CardObjective.encode((RegistryFriendlyByteBuf) b, v));
        buf.writeCollection(card.rewards, NetComponents::write);
        buf.writeCollection(card.rewardIcons,
                (b, stack) -> ItemStack.OPTIONAL_STREAM_CODEC.encode((RegistryFriendlyByteBuf) b, stack));
        buf.writeUtf(card.difficulty);
        buf.writeOptional(card.instance, (b, id) -> b.writeUUID(id));
        buf.writeEnum(card.state);
        buf.writeBoolean(card.deliverCompletes);
    }

    public static QuestCard decode(RegistryFriendlyByteBuf buf) {
        return new QuestCard(
                buf.readResourceLocation(),
                NetComponents.read(buf),
                NetComponents.read(buf),
                NetComponents.read(buf),
                PacketCollections.readList(buf, b -> CardObjective.decode((RegistryFriendlyByteBuf) b)),
                PacketCollections.readList(buf, NetComponents::read),
                PacketCollections.readList(buf, b -> ItemStack.OPTIONAL_STREAM_CODEC.decode((RegistryFriendlyByteBuf) b)),
                buf.readUtf(),
                buf.readOptional(b -> b.readUUID()),
                buf.readEnum(QuestMenuStatus.class),
                buf.readBoolean());
    }
}
