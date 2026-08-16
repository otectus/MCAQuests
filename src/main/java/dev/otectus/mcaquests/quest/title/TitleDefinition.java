package dev.otectus.mcaquests.quest.title;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Optional datapack definition for a player title (spec 0.7.0), loaded from
 * {@code data/<ns>/mcaquests/titles/**.json}. Titles function even when undefined (the id is displayed),
 * but a definition supplies a display name and declares the intended {@link TitleScope} for validation
 * and UI. The title's id is the file's resource location, set by the loader.
 */
public record TitleDefinition(String name, TitleScope scope) {

    public static final Codec<TitleDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(TitleDefinition::name),
            TitleScope.CODEC.lenientOptionalFieldOf("scope", TitleScope.VILLAGE).forGetter(TitleDefinition::scope)
    ).apply(instance, TitleDefinition::new));
}
