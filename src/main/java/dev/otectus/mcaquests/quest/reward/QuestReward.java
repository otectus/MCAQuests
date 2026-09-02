package dev.otectus.mcaquests.quest.reward;

import dev.otectus.mcaquests.quest.reputation.QuestReputation;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Something granted on quest completion (spec section 15). Registry-driven via {@link RewardTypes}.
 * <b>All rewards are applied server-side only</b>, after objective items are consumed and every
 * validation has passed.
 */
public interface QuestReward {

    QuestRewardType<?> type();

    /** One-line summary for the quest card, e.g. "3x Emerald" or "+25 hearts". */
    Component describe();

    /**
     * Item stacks the quest card may draw as icons beside {@link #describe()}, or an empty list for a
     * reward with nothing to show.
     *
     * <p>Purely cosmetic: this is a <em>preview</em>, never the thing that is granted. {@link #grant}
     * remains the only place a reward is delivered, and a reward that returns a misleading stack here
     * misleads a player without giving them anything.
     *
     * <p>Defaults to empty, so existing reward types — including those registered by add-ons — are
     * unaffected and keep compiling. A reward that grants no item (hearts, reputation, a title) is
     * expected to leave it that way; the card falls back to its text line.
     */
    default List<ItemStack> previewIcons() {
        return List.of();
    }

    /**
     * Deliver the reward. {@code villager} is the quest giver (may be null if it has gone missing);
     * hearts rewards require it.
     */
    void grant(ServerPlayer player, @Nullable Entity villager);

    /**
     * Deliver the reward with everything the quest knew about its giver, whether or not that giver is
     * loaded. A quest completed in the field routinely has no giver entity, and a reward that needs one
     * — hearts, a village-scoped title — was silently dropped. The context carries the giver's identity
     * and village, frozen when the quest was accepted, so those rewards can still land.
     *
     * <p>Defaults to {@link #grant(ServerPlayer, Entity)}, so every existing reward — add-on rewards
     * included — keeps working unchanged. Override this instead when a reward can do something useful
     * without the entity.
     */
    default void grant(ServerPlayer player, @Nullable Entity villager, RewardContext context) {
        grant(player, villager);
    }

    /**
     * What the quest knew about its giver at accept time: enough to grant a reward that would otherwise
     * need the entity. {@code villageId} is empty on a giver who belonged to no resolvable village and
     * on quests accepted before 1.5.1.
     */
    record RewardContext(UUID giverUuid, Component giverName, ResourceLocation dimension,
                         OptionalInt villageId, ResourceLocation questId) {

        /** The giver's community, or empty when no village was frozen onto the quest. */
        public Optional<QuestReputation.Community> community() {
            return villageId.isPresent()
                    ? Optional.of(new QuestReputation.Community(dimension, villageId.getAsInt()))
                    : Optional.empty();
        }

        /** The level the quest was accepted in, or {@code null} if that dimension is gone. */
        @Nullable
        public ServerLevel level(ServerPlayer player) {
            net.minecraft.server.MinecraftServer server = player.getServer();
            return server == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        }
    }
}
