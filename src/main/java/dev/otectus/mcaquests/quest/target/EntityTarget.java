package dev.otectus.mcaquests.quest.target;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

/** Matches an entity by type id ({@code "entity": ...}) or tag ({@code "tag": ...}) — spec sections 14, 19. */
public record EntityTarget(Optional<EntityType<?>> entity, Optional<TagKey<EntityType<?>>> tag) {

    public static final MapCodec<EntityTarget> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().optionalFieldOf("entity").forGetter(EntityTarget::entity),
            TagKey.codec(Registries.ENTITY_TYPE).optionalFieldOf("tag").forGetter(EntityTarget::tag)
    ).apply(instance, EntityTarget::new));

    public boolean matches(Entity target) {
        EntityType<?> type = target.getType();
        if (entity.isPresent() && type == entity.get()) {
            return true;
        }
        return tag.isPresent() && type.builtInRegistryHolder().is(tag.get());
    }

    public Component describe() {
        if (entity.isPresent()) {
            return entity.get().getDescription();
        }
        return tag.map(t -> (Component) Component.literal("#" + t.location())).orElse(Component.literal("?"));
    }
}
