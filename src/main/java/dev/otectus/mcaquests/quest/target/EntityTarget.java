package dev.otectus.mcaquests.quest.target;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.DisplayNames;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

/**
 * Matches an entity by type id ({@code "entity": ...}) or tag ({@code "tag": ...}) — spec sections 14, 19.
 *
 * <p><b>An unknown entity id is not a parse error.</b> It used to be: the codec was
 * {@code BuiltInRegistries.ENTITY_TYPE.byNameCodec()}, so one quest naming a creature from an
 * uninstalled mod failed to load, was skipped, and any copy a player had already accepted showed as
 * "Unknown quest" with no way to tell why. Since 1.5.4 the id is kept in {@link #unresolved} instead:
 * the quest still loads and still shows its title, the objective reads as unavailable rather than
 * broken, and if the mod is installed later the same save picks the quest straight back up. Which is
 * the whole point — the alternative was destroying a player's quest because a mod was removed.
 *
 * <p>Encoding is symmetric: whichever id came in goes back out, so a round-trip through the codec on
 * a world that cannot resolve it does not silently drop the target.
 */
public record EntityTarget(Optional<EntityType<?>> entity, Optional<TagKey<EntityType<?>>> tag,
                           Optional<ResourceLocation> unresolved) {

    /**
     * The shape this target had before {@code unresolved} existed, kept so an add-on that builds one
     * in code still compiles. Adding a record component is a source break for every caller of the
     * canonical constructor, and a target built in Java has nothing to be unresolved about.
     */
    public EntityTarget(Optional<EntityType<?>> entity, Optional<TagKey<EntityType<?>>> tag) {
        this(entity, tag, Optional.empty());
    }

    public static final MapCodec<EntityTarget> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.lenientOptionalFieldOf("entity").forGetter(EntityTarget::entityId),
            TagKey.codec(Registries.ENTITY_TYPE).lenientOptionalFieldOf("tag").forGetter(EntityTarget::tag)
    ).apply(instance, EntityTarget::resolving));

    /** Builds a target from a raw id, keeping the id when this world has no such entity type. */
    private static EntityTarget resolving(Optional<ResourceLocation> id,
                                          Optional<TagKey<EntityType<?>>> tag) {
        if (id.isEmpty()) {
            return new EntityTarget(Optional.empty(), tag, Optional.empty());
        }
        Optional<EntityType<?>> resolved = BuiltInRegistries.ENTITY_TYPE.getOptional(id.get());
        return resolved.isPresent()
                ? new EntityTarget(resolved, tag, Optional.empty())
                : new EntityTarget(Optional.empty(), tag, id);
    }

    /** The id that was written, resolved or not — what the codec encodes back. */
    public Optional<ResourceLocation> entityId() {
        return entity.<ResourceLocation>map(BuiltInRegistries.ENTITY_TYPE::getKey).or(() -> unresolved);
    }

    /** True when this names an entity type that does not exist in this world. */
    public boolean isUnresolved() {
        return unresolved.isPresent();
    }

    public boolean matches(Entity target) {
        if (isUnresolved()) {
            // Nothing in this world is the thing this asks for, so nothing may be credited for it.
            return false;
        }
        EntityType<?> type = target.getType();
        if (entity.isPresent() && type == entity.get()) {
            return true;
        }
        return tag.isPresent() && type.builtInRegistryHolder().is(tag.get());
    }

    public Component describe() {
        if (isUnresolved()) {
            return Component.translatable("mcaquests.target.unavailable", unresolved.get().toString());
        }
        if (entity.isPresent()) {
            return entity.get().getDescription();
        }
        return tag.map(t -> DisplayNames.tagName(t.location())).orElse(Component.literal("?"));
    }
}
