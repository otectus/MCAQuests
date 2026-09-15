package dev.otectus.mcaquests.quest.reward;

import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.data.RegistryEntryCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.McaQuests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 * Gives an item stack; inserts into the inventory, or, once a per-tick stack budget is exhausted,
 * retains the remainder in the pending-reward ledger ({@link dev.otectus.mcaquests.state.PendingItemRewards})
 * for delivery on a later tick rather than dropping it at the player's feet (spec section 15).
 *
 * <p>{@code enchantments} maps an enchantment id to the <em>literal</em> level to apply, capped by
 * {@code maxRewardEnchantmentLevel}. PORT: the id is kept raw rather than resolved at parse time,
 * because 1.21 makes enchantments a datapack registry that does not exist while quest JSON is read; an
 * id that names nothing is skipped at grant time and reported by {@code ProgressionValidator}.
 */
public record ItemReward(Item item, int count, Map<ResourceLocation, Integer> enchantments) implements QuestReward {

    public static final MapCodec<ItemReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryEntryCodec.of(BuiltInRegistries.ITEM).fieldOf("item").forGetter(ItemReward::item),
            StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "count", 1).forGetter(ItemReward::count),
            StrictCodecs.strictOptional(Codec.unboundedMap(ResourceLocation.CODEC, ExtraCodecs.POSITIVE_INT),
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
        var applied = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (applied.isEmpty()) {
            return Component.translatable("mcaquests.reward.item", item.getDescription(), count);
        }
        // Naming the enchantment in the text keeps the card honest without a hover.
        Component names = null;
        for (var entry : applied.entrySet()) {
            Component name = Enchantment.getFullname(entry.getKey(), entry.getIntValue());
            names = names == null ? name : Component.empty().append(names).append(", ").append(name);
        }
        return Component.translatable("mcaquests.reward.item_enchanted", stack.getHoverName(), count, names);
    }

    /**
     * One stack showing the reward, with the count clamped for the wire: the preview is a single slot
     * with the number on it, so 128 emeralds read as "128" rather than as two slots of 64 and one of 0.
     * The card's text line carries the true number, and {@link #grant} still delivers the full amount,
     * split into proper stacks.
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
