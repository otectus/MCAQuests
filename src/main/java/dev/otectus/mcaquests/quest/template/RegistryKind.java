package dev.otectus.mcaquests.quest.template;

import dev.otectus.mcaquests.quest.DisplayNames;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The registry a {@link RegistryVariable} draws from. Items/blocks/entities live in
 * {@link BuiltInRegistries} (so ids and tag members can be checked at datapack-load time), whereas
 * biomes are a dynamic registry resolved from the live level (validated by format only at load).
 * Dimensions have no vanilla tag system, so {@link #allowsTags()} is false for them.
 */
public enum RegistryKind {
    ITEM("item"),
    BLOCK("block"),
    ENTITY("entity"),
    BIOME("biome"),
    DIMENSION("dimension");

    private final String key;

    RegistryKind(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<RegistryKind> fromKey(String key) {
        for (RegistryKind kind : values()) {
            if (kind.key.equals(key)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /** Whether a {@code tag} pool entry is meaningful for this kind (dimensions have no tags). */
    public boolean allowsTags() {
        return this != DIMENSION;
    }

    /**
     * Whether {@code id} exists in this registry. For dynamic registries (biome/dimension) the answer
     * is unknowable at datapack-load time, so this returns true (format-only validation); their ids are
     * re-checked against the live world when resolved.
     */
    public boolean idExistsForValidation(ResourceLocation id) {
        return switch (this) {
            case ITEM -> BuiltInRegistries.ITEM.containsKey(id);
            case BLOCK -> BuiltInRegistries.BLOCK.containsKey(id);
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(id);
            case BIOME, DIMENSION -> true;
        };
    }

    /** Player-readable name for a resolved id, preferring the registry object's own translated name. */
    public Component display(ResourceLocation id) {
        return switch (this) {
            case ITEM -> BuiltInRegistries.ITEM.getOptional(id)
                    .map(item -> item.getDescription()).orElseGet(() -> DisplayNames.name(id));
            case BLOCK -> BuiltInRegistries.BLOCK.getOptional(id)
                    .map(block -> (Component) block.getName()).orElseGet(() -> DisplayNames.name(id));
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                    .map(type -> type.getDescription()).orElseGet(() -> DisplayNames.name(id));
            case BIOME, DIMENSION -> DisplayNames.name(id);
        };
    }

    /**
     * Expands a tag to its concrete member ids, sorted by id string for deterministic indexing
     * (tag/HolderSet iteration order is not stable). Returns an empty list if the tag is empty or
     * unknown. Biome tags are resolved from the live registry in {@code ctx}.
     */
    public List<ResourceLocation> expandTag(ResourceLocation tag, QuestContext ctx) {
        List<ResourceLocation> ids = new ArrayList<>();
        switch (this) {
            case ITEM -> collectMembers(BuiltInRegistries.ITEM, Registries.ITEM, tag, ids);
            case BLOCK -> collectMembers(BuiltInRegistries.BLOCK, Registries.BLOCK, tag, ids);
            case ENTITY -> collectMembers(BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, tag, ids);
            case BIOME -> {
                Registry<Biome> biomes = ctx.level().registryAccess().registryOrThrow(Registries.BIOME);
                collectMembers(biomes, Registries.BIOME, tag, ids);
            }
            case DIMENSION -> {
                // Dimensions have no tag system; nothing to expand. Validation rejects dimension tags.
            }
        }
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        return ids;
    }

    /**
     * Tag members resolvable without a live world — for {@code item}/{@code block}/{@code entity}, which
     * live in {@link BuiltInRegistries} (used by load-time validation). Biomes/dimensions are dynamic, so
     * this returns an empty list for them (their tags are validated by format only).
     */
    public List<ResourceLocation> staticMembers(ResourceLocation tag) {
        List<ResourceLocation> ids = new ArrayList<>();
        switch (this) {
            case ITEM -> collectMembers(BuiltInRegistries.ITEM, Registries.ITEM, tag, ids);
            case BLOCK -> collectMembers(BuiltInRegistries.BLOCK, Registries.BLOCK, tag, ids);
            case ENTITY -> collectMembers(BuiltInRegistries.ENTITY_TYPE, Registries.ENTITY_TYPE, tag, ids);
            case BIOME, DIMENSION -> {
                // Dynamic registries — not resolvable at datapack-load time.
            }
        }
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        return ids;
    }

    /** Whether this kind's registry is fully known at datapack-load time (static {@link BuiltInRegistries}). */
    public boolean isStatic() {
        return this == ITEM || this == BLOCK || this == ENTITY;
    }

    /**
     * Whether this kind's tags have been bound to its registry yet.
     *
     * <p>{@link #isStatic()} is about <em>ids</em>, which really are known the moment the registry
     * freezes. Tags are not: they are datapack content, bound to the registry only when a reload
     * finishes. Until then {@link #staticMembers} returns an empty list for every tag, so an empty
     * result cannot tell "this tag does not exist" apart from "no tag exists yet" — which is how
     * {@code minecraft:fishes}, a vanilla tag with six items in it, got reported as empty or unknown.
     * Callers that treat emptiness as a finding must ask this first.
     *
     * <p>The question has to be "does any tag actually have members", not "does the registry know
     * any tag names". A registry accumulates {@link net.minecraft.core.HolderSet.Named} entries the
     * moment anything <em>asks</em> for a tag, and in a large modpack plenty of things ask long
     * before the first datapack reload applies; those entries are present but empty. Counting them
     * as bound is what let the {@code minecraft:fishes} report survive the first attempt at this
     * guard. A populated holder set, by contrast, can only come from a real bind.
     */
    public boolean tagsBound() {
        return switch (this) {
            case ITEM -> anyTagPopulated(BuiltInRegistries.ITEM);
            case BLOCK -> anyTagPopulated(BuiltInRegistries.BLOCK);
            case ENTITY -> anyTagPopulated(BuiltInRegistries.ENTITY_TYPE);
            case BIOME, DIMENSION -> false;
        };
    }

    /** True once some tag in {@code registry} holds at least one member, i.e. a bind has happened. */
    private static <T> boolean anyTagPopulated(Registry<T> registry) {
        return registry.getTags().anyMatch(tag -> tag.getSecond().size() > 0);
    }

    private static <T> void collectMembers(Registry<T> registry, ResourceKey<? extends Registry<T>> registryKey,
                                           ResourceLocation tag, List<ResourceLocation> out) {
        TagKey<T> tagKey = TagKey.create(registryKey, tag);
        registry.getTag(tagKey).ifPresent(named ->
                named.forEach(holder -> holder.unwrapKey().ifPresent(key -> out.add(key.location()))));
    }
}
