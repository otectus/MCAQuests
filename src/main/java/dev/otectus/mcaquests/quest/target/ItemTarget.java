package dev.otectus.mcaquests.quest.target;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
        return tag.map(t -> (Component) Component.literal("#" + t.location())).orElse(Component.literal("?"));
    }
}
