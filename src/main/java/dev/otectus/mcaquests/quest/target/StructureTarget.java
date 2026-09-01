package dev.otectus.mcaquests.quest.target;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.DisplayNames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;
import java.util.Optional;

/**
 * Matches a structure by id ({@code "structure": ...}) or tag ({@code "structure_tag": ...}) for the
 * {@code enter_structure} objective. Structures live in a <em>dynamic</em> registry, so the id is
 * stored as a {@link ResourceLocation} and the {@link ResourceKey} is built at match time — and the
 * id can only be checked for well-formedness at datapack load (validated as a warning, not an error).
 * An unknown/unloaded structure simply never matches at runtime (no crash).
 */
public record StructureTarget(Optional<ResourceLocation> structure, Optional<TagKey<Structure>> tag) {

    public static final MapCodec<StructureTarget> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("structure").forGetter(StructureTarget::structure),
            TagKey.codec(Registries.STRUCTURE).optionalFieldOf("structure_tag").forGetter(StructureTarget::tag)
    ).apply(instance, StructureTarget::new));

    /** True when {@code pos} is inside a generated piece of the targeted structure. */
    public boolean matches(ServerLevel level, BlockPos pos) {
        try {
            if (structure.isPresent()) {
                ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, structure.get());
                if (level.structureManager().getStructureWithPieceAt(pos, key).isValid()) {
                    return true;
                }
            }
            if (tag.isPresent() && level.structureManager().getStructureWithPieceAt(pos, tag.get()).isValid()) {
                return true;
            }
        } catch (Throwable ignored) {
            // Unknown structure id in a dynamic registry — treat as "not inside" rather than crashing.
        }
        return false;
    }

    /**
     * Whether this level's registries know the structure or tag this names.
     *
     * <p>An unknown id is not a runtime hiccup, it is a typo: {@code matches} answers "not inside" for it
     * forever, so a quest built on one is permanently uncompletable and says nothing about why. The codec
     * cannot catch it, because structures live in a datapack-driven dynamic registry that does not exist
     * at parse time — the earliest anyone can ask is against a running level, which is what this is for.
     *
     * <p>Answers {@code true} when the registry cannot be read at all. "I could not check" must not be
     * reported as "this is broken".
     */
    public boolean isKnown(ServerLevel level) {
        try {
            Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            if (structure.isPresent()
                    && !registry.containsKey(ResourceKey.create(Registries.STRUCTURE, structure.get()))) {
                return false;
            }
            // A tag with nothing in it is the same dead end as an unknown id, and is the likelier typo.
            return tag.isEmpty() || registry.getTag(tag.get()).map(named -> named.size() > 0).orElse(false);
        } catch (Throwable t) {
            return true;
        }
    }

    public Component describe() {
        if (structure.isPresent()) {
            return DisplayNames.name(structure.get());
        }
        return tag.map(t -> DisplayNames.tagName(t.location())).orElse(Component.literal("?"));
    }

    /** Cross-field validation surfaced by the owning objective's validator. */
    public void validate(String prefix, List<String> errors) {
        if (structure.isEmpty() && tag.isEmpty()) {
            errors.add(prefix + " must set either 'structure' or 'structure_tag'.");
        }
    }
}
