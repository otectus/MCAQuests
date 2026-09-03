package dev.otectus.mcaquests.quest.situation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.FailureSpec;
import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;

/**
 * An immutable, data-loaded situation template (the "Living Village" phase, 0.8.0). Parsed from
 * {@code data/<ns>/mcaquests/situations/**.json} by {@code SituationDataLoader}.
 *
 * <p>A {@code SituationDefinition} pairs a {@link SituationTrigger} (what world event opens it),
 * lifetime/throttle metadata ({@code duration_ticks}, {@code cooldown_ticks}), a {@link SituationScope},
 * the resolution {@link SituationOutcomes}, and the dynamic {@link SituationOffer} surfaced to nearby
 * givers while the situation is open.
 *
 * <p>The offer reuses the full quest lifecycle via a <b>stable synthetic id</b>
 * ({@link #syntheticId()}): one per definition, not per open instance. Per-instance variation comes
 * from frozen template values on the accepted {@code ActiveQuest}, exactly like ordinary template
 * quests, so the base offer definition is always reconstructable from the datapack after a restart.
 */
public record SituationDefinition(
        ResourceLocation id,
        boolean enabled,
        SituationScope scope,
        int durationTicks,
        int cooldownTicks,
        SituationTrigger trigger,
        SituationOutcomes outcomes,
        SituationOffer offer) {

    public static final Codec<SituationDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(SituationDefinition::id),
            Codec.BOOL.lenientOptionalFieldOf("enabled", true).forGetter(SituationDefinition::enabled),
            SituationScope.CODEC.lenientOptionalFieldOf("scope", SituationScope.VILLAGE).forGetter(SituationDefinition::scope),
            ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("duration_ticks", 24000).forGetter(SituationDefinition::durationTicks),
            ExtraCodecs.NON_NEGATIVE_INT.lenientOptionalFieldOf("cooldown_ticks", 24000).forGetter(SituationDefinition::cooldownTicks),
            SituationTriggerTypes.CODEC.fieldOf("trigger").forGetter(SituationDefinition::trigger),
            SituationOutcomes.CODEC.lenientOptionalFieldOf("outcomes", SituationOutcomes.NONE).forGetter(SituationDefinition::outcomes),
            SituationOffer.CODEC.fieldOf("offer").forGetter(SituationDefinition::offer)
    ).apply(instance, SituationDefinition::new));

    /**
     * The stable id of the synthesized offer quest for this definition (see {@link SituationIds}). One per
     * definition, not per open instance, so the base offer is always reconstructable from the datapack.
     */
    public ResourceLocation syntheticId() {
        return SituationIds.syntheticId(id);
    }

    /**
     * The base offer {@link QuestDefinition} for this situation (templates resolved per acceptance), with
     * a {@link FailureSpec} whose {@code deadline_ticks} equals this situation's {@link #durationTicks}.
     * Accepting anchors the quest's start to the instance's open time, so its deadline lands exactly on
     * the situation's master deadline (shared by every player who accepts) and the existing failure /
     * HUD-countdown machinery applies unchanged. Author {@code failure} outcome fields are preserved.
     */
    public QuestDefinition toOfferQuestDefinition() {
        return offer.toQuestDefinition(syntheticId(), enabled, Optional.of(deadlineFailureSpec()));
    }

    /** A {@link FailureSpec} carrying the duration deadline merged with any author outcome fields. */
    private FailureSpec deadlineFailureSpec() {
        FailureSpec author = offer.failure().orElse(null);
        return new FailureSpec(
                Optional.of(durationTicks),
                author != null ? author.deadlineTimeOfDay() : Optional.empty(),
                author != null ? author.requireWeather() : Optional.empty(),
                author != null && author.failOnGiverDeath(),
                author != null ? author.failureHearts() : 0,
                author != null ? author.retryAfterTicks() : Optional.empty(),
                author != null && author.blockRetry());
    }
}
