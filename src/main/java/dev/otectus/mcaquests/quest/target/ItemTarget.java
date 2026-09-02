package dev.otectus.mcaquests.quest.target;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.DisplayNames;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Matches an item by a single id ({@code "item": "minecraft:wheat"}) or a tag
 * ({@code "tag": "minecraft:logs"}) — spec sections 14, 27. Fields inline into the owning objective.
 */
public record ItemTarget(Optional<Item> item, Optional<TagKey<Item>> tag) {

    public static final MapCodec<ItemTarget> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("item").forGetter(ItemTarget::item),
            TagKey.codec(Registries.ITEM).optionalFieldOf("tag").forGetter(ItemTarget::tag)
    ).apply(instance, ItemTarget::new));

    public boolean matches(ItemStack stack) {
        return (item.isPresent() && stack.is(item.get())) || (tag.isPresent() && stack.is(tag.get()));
    }

    public Component describe() {
        if (item.isPresent()) {
            return item.get().getDescription();
        }
        return tag.map(t -> DisplayNames.tagName(t.location())).orElse(Component.literal("?"));
    }

    /**
     * A stack the card can draw beside this objective, or {@link ItemStack#EMPTY} when there is
     * nothing to show.
     *
     * <p>A tag is represented by its first member — "any log" has no single icon, and picking one is
     * better than picking none. The text beside it still names the tag, so the icon illustrates the
     * requirement rather than narrowing it. An empty or unloaded tag yields nothing rather than
     * throwing: a datapack may name a tag no pack in this instance defines.
     */
    public ItemStack icon() {
        if (item.isPresent()) {
            return new ItemStack(item.get());
        }
        return tag.flatMap(t -> BuiltInRegistries.ITEM.getTag(t)
                        .flatMap(members -> members.stream().findFirst())
                        .map(holder -> new ItemStack(holder.value())))
                .orElse(ItemStack.EMPTY);
    }
}
