package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Which villagers may offer a quest (spec sections 11, 12). An empty {@code professions} list makes
 * the quest generic (offerable by any MCA villager). Hearts bounds gate by the player's relationship.
 */
public record GiverSpec(List<ResourceLocation> professions, boolean adultOnly, int minHearts, int maxHearts) {

    public static final GiverSpec ANY = new GiverSpec(List.of(), true, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final Codec<GiverSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("professions", List.of()).forGetter(GiverSpec::professions),
            Codec.BOOL.optionalFieldOf("adult_only", true).forGetter(GiverSpec::adultOnly),
            Codec.INT.optionalFieldOf("min_hearts", Integer.MIN_VALUE).forGetter(GiverSpec::minHearts),
            Codec.INT.optionalFieldOf("max_hearts", Integer.MAX_VALUE).forGetter(GiverSpec::maxHearts)
    ).apply(instance, GiverSpec::new));

    public boolean isGeneric() {
        return professions.isEmpty();
    }

    public boolean acceptsProfession(ResourceLocation professionId) {
        return isGeneric() || (professionId != null && professions.contains(professionId));
    }

    public boolean acceptsHearts(int hearts) {
        return hearts >= minHearts && hearts <= maxHearts;
    }
}
