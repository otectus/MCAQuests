package dev.otectus.mcaquests.quest.target;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.DisplayNames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Optional;

/**
 * Matches a biome by id ({@code "biome": ...}) or tag ({@code "tag": ...}) — spec sections 13, 14.
 * Biomes are a dynamic registry, so the single id is stored as a {@link ResourceLocation} and the
 * {@link ResourceKey} is built at match time.
 */
public record BiomeTarget(Optional<ResourceLocation> biome, Optional<TagKey<Biome>> tag) {

    public static final MapCodec<BiomeTarget> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.lenientOptionalFieldOf("biome").forGetter(BiomeTarget::biome),
            TagKey.codec(Registries.BIOME).lenientOptionalFieldOf("tag").forGetter(BiomeTarget::tag)
    ).apply(instance, BiomeTarget::new));

    public boolean matches(Holder<Biome> holder) {
        if (biome.isPresent() && holder.is(ResourceKey.create(Registries.BIOME, biome.get()))) {
            return true;
        }
        return tag.isPresent() && holder.is(tag.get());
    }

    /**
     * Whether this level's registries know the biome or tag this names. See
     * {@code StructureTarget.isKnown}: an unknown id makes {@code matches} answer "no" forever, and
     * "I could not check" is reported as fine rather than as broken.
     */
    public boolean isKnown(ServerLevel level) {
        try {
            Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
            if (biome.isPresent()
                    && !registry.containsKey(ResourceKey.create(Registries.BIOME, biome.get()))) {
                return false;
            }
            return tag.isEmpty() || registry.getTag(tag.get()).map(named -> named.size() > 0).orElse(false);
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Cross-field validation surfaced by the owning objective's validator.
     *
     * <p>{@code BiomeTarget} had none at all, so {@code {"biome": {}}} — neither field set — parsed
     * cleanly into a target that can never match anything.
     */
    public void validate(String prefix, List<String> errors) {
        if (biome.isEmpty() && tag.isEmpty()) {
            errors.add(prefix + " must set either 'biome' or 'tag'.");
        }
    }

    /**
     * The nearest position in this biome to {@code from}, or empty.
     *
     * <p>Vanilla's {@code /locatebiome} search, and as costly as {@code StructureTarget.locate} for
     * the same reason — it samples the biome source outward in rings. Throttled by its only caller,
     * {@code LocateCache}, never here.
     *
     * <p>The {@code step} is 32 rather than vanilla's 8: a quest wants "there is warm ocean that
     * way", not the exact first block of it, and the coarser stride cuts the sample count roughly
     * sixteenfold. Fails to empty rather than throwing, for an id naming nothing in this world.
     */
    public Optional<BlockPos> locate(ServerLevel level, BlockPos from, int blockRadius) {
        try {
            Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(
                    this::matches, from, Math.max(64, blockRadius), 32, 64);
            return found == null ? Optional.empty() : Optional.of(found.getFirst());
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    public Component describe() {
        if (biome.isPresent()) {
            return DisplayNames.name(biome.get());
        }
        return tag.map(t -> DisplayNames.tagName(t.location())).orElse(Component.literal("?"));
    }
}
