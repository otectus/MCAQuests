package dev.otectus.mcaquests.quest.target;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.Optional;

/**
 * Matches a biome by id ({@code "biome": ...}) or tag ({@code "tag": ...}) — spec sections 13, 14.
 * Biomes are a dynamic registry, so the single id is stored as a {@link ResourceLocation} and the
 * {@link ResourceKey} is built at match time.
 */
public record BiomeTarget(Optional<ResourceLocation> biome, Optional<TagKey<Biome>> tag) {

    public static final MapCodec<BiomeTarget> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("biome").forGetter(BiomeTarget::biome),
            TagKey.codec(Registries.BIOME).optionalFieldOf("tag").forGetter(BiomeTarget::tag)
    ).apply(instance, BiomeTarget::new));

    public boolean matches(Holder<Biome> holder) {
        if (biome.isPresent() && holder.is(ResourceKey.create(Registries.BIOME, biome.get()))) {
            return true;
        }
        return tag.isPresent() && holder.is(tag.get());
    }

    public Component describe() {
        if (biome.isPresent()) {
            return Component.literal(biome.get().toString());
        }
        return tag.map(t -> (Component) Component.literal("#" + t.location())).orElse(Component.literal("?"));
    }
}
