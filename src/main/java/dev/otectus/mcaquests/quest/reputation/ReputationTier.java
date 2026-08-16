package dev.otectus.mcaquests.quest.reputation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * One named rung on a {@link ReputationTierSet} ladder (spec 0.7.0). {@code threshold} is the inclusive
 * minimum village reputation at which a player is considered to be in this tier; tiers within a set
 * ascend strictly by threshold. {@code grantsTitle}, when present, is awarded once the first time a
 * player's reputation with a village reaches this tier.
 */
public record ReputationTier(String id, int threshold, String name, Optional<ResourceLocation> grantsTitle) {

    public static final Codec<ReputationTier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(ReputationTier::id),
            Codec.INT.fieldOf("threshold").forGetter(ReputationTier::threshold),
            Codec.STRING.fieldOf("name").forGetter(ReputationTier::name),
            ResourceLocation.CODEC.optionalFieldOf("grants_title").forGetter(ReputationTier::grantsTitle)
    ).apply(instance, ReputationTier::new));
}
