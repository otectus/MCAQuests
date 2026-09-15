package dev.otectus.mcaquests.quest.reward;

import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.data.RegistryEntryCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Gives an item stack; inserts into the inventory or drops at the player if full (spec section 15).
 *
 * <p>{@code enchantments} maps an enchantment id to the <em>literal</em> level to apply, capped by
 * {@code maxRewardEnchantmentLevel}. Enchantment ids resolve strictly: an unknown one is a load error.
 */
public record ItemReward(Item item, int count, Map<Enchantment, Integer> enchantments) implements QuestReward {

    public static final Codec<ItemReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RegistryEntryCodec.of(BuiltInRegistries.ITEM).fieldOf("item").forGetter(ItemReward::item),
            StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "count", 1).forGetter(ItemReward::count),
            StrictCodecs.strictOptional(Codec.unboundedMap(
                            RegistryEntryCodec.of(BuiltInRegistries.ENCHANTMENT), ExtraCodecs.POSITIVE_INT),
                    "enchantments", Map.of()).forGetter(ItemReward::enchantments)
    ).apply(instance, ItemReward::new));

    /** The plain, unenchanted form every pre-1.6.4 caller writes. */
    public ItemReward(Item item, int count) {
        this(item, count, Map.of());
    }

    @Override
    public QuestRewardType<?> type() {
        return RewardTypes.ITEM;
    }

    @Override
    public Component describe() {
        ItemStack stack = RewardStacks.build(item, count, enchantments);
        var applied = EnchantmentHelper.getEnchantments(stack);
        if (applied.isEmpty()) {
            return Component.translatable("mcaquests.reward.item", item.getDescription(), count);
        }
        // Naming the enchantment in the text keeps the card honest without a hover.
        Component names = null;
        for (var entry : applied.entrySet()) {
            Component name = entry.getKey().getFullname(entry.getValue());
            names = names == null ? name : Component.empty().append(names).append(", ").append(name);
        }
        return Component.translatable("mcaquests.reward.item_enchanted", stack.getHoverName(), count, names);
    }

    /**
     * One stack showing the reward, with the count clamped for the wire: {@code writeItem} sends the
     * count as a byte, so a preview of more than 127 would wrap. The card's text line carries the true
     * number, and {@link #grant} still delivers the full amount, split into proper stacks.
     */
    @Override
    public List<ItemStack> previewIcons() {
        return List.of(RewardStacks.previewIcon(RewardStacks.build(item, count, enchantments)));
    }

    @Override
    public void grant(ServerPlayer player, @Nullable Entity villager) {
        if (enchantments.isEmpty()) {
            ItemRewardDelivery.grant(player, item, count);
        } else {
            ItemRewardDelivery.grant(player, RewardStacks.build(item, count, enchantments));
        }
        McaQuests.LOGGER.debug("Granted {}x {} to {}", count, item, player.getGameProfile().getName());
    }
}
