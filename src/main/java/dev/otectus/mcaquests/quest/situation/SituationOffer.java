package dev.otectus.mcaquests.quest.situation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.FailureSpec;
import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.OfferShaping;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.QuestText;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.quest.TurnInSpec;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.objective.ObjectiveTypes;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.reward.RewardTypes;
import dev.otectus.mcaquests.quest.template.PlaceholderResolver;
import dev.otectus.mcaquests.quest.template.TemplateSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The dynamic-offer body of a {@link SituationDefinition} (0.8.0): the same author-facing fields as a
 * {@code QuestDefinition} minus identity/chain (a situation offer is never part of a static chain). It
 * is turned into a real {@link QuestDefinition} under the definition's synthetic id so the entire
 * existing quest lifecycle (accept, track, turn in, fail) handles it unchanged.
 */
public record SituationOffer(
        int weight,
        Optional<QuestText> title,
        GiverSpec giver,
        Map<String, QuestText> dialogue,
        List<QuestObjective> objectives,
        List<QuestReward> rewards,
        TurnInSpec turnIn,
        Optional<FailureSpec> failure,
        Optional<TemplateSpec> template,
        OfferShaping offerShaping,
        Optional<QuestCondition> conditions) {

    /** Category stamped on the synthesized quest so the rest of the mod can recognise situation offers. */
    public static final String CATEGORY = "situation";

    public static final Codec<SituationOffer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            net.minecraft.util.ExtraCodecs.POSITIVE_INT.lenientOptionalFieldOf("weight", 1).forGetter(SituationOffer::weight),
            QuestText.CODEC.lenientOptionalFieldOf("title").forGetter(SituationOffer::title),
            GiverSpec.CODEC.lenientOptionalFieldOf("giver", GiverSpec.ANY).forGetter(SituationOffer::giver),
            Codec.unboundedMap(Codec.STRING, QuestText.CODEC).lenientOptionalFieldOf("dialogue", Map.of())
                    .forGetter(SituationOffer::dialogue),
            ObjectiveTypes.CODEC.listOf().lenientOptionalFieldOf("objectives", List.of()).forGetter(SituationOffer::objectives),
            RewardTypes.CODEC.listOf().lenientOptionalFieldOf("rewards", List.of()).forGetter(SituationOffer::rewards),
            TurnInSpec.CODEC.lenientOptionalFieldOf("turn_in", TurnInSpec.DEFAULT).forGetter(SituationOffer::turnIn),
            FailureSpec.CODEC.lenientOptionalFieldOf("failure").forGetter(SituationOffer::failure),
            TemplateSpec.CODEC.lenientOptionalFieldOf("template").forGetter(SituationOffer::template),
            OfferShaping.MAP_CODEC.forGetter(SituationOffer::offerShaping),
            ConditionTypes.CODEC.lenientOptionalFieldOf("conditions").forGetter(SituationOffer::conditions)
    ).apply(instance, SituationOffer::new));

    /** The pre-1.4.1 shape, for callers and tests that predate offer conditions. */
    public SituationOffer(int weight, Optional<QuestText> title, GiverSpec giver,
                          Map<String, QuestText> dialogue, List<QuestObjective> objectives,
                          List<QuestReward> rewards, TurnInSpec turnIn,
                          Optional<FailureSpec> failure, Optional<TemplateSpec> template,
                          OfferShaping offerShaping) {
        this(weight, title, giver, dialogue, objectives, rewards, turnIn, failure, template,
                offerShaping, Optional.empty());
    }

    /**
     * Builds the base {@link QuestDefinition} for this offer under {@code id}.
     *
     * <p>An offer's {@code conditions} are carried onto the derived quest, in addition to the instance's
     * own scope and giver match rather than instead of it. They were dropped entirely before 1.4.1,
     * which meant every {@code townstead_available} gate written into a shipped situation was inert —
     * the JSON read like a gate and was not one. A situation still opens on its signal; conditions
     * decide which villager is allowed to be the one who asks.
     *
     * <p>Chain stays empty: a situation offer is never part of a static chain.
     *
     * <p>{@code failureOverride} is the effective failure block (the caller folds the situation's
     * duration deadline into the author's outcome fields); when empty the author's own {@code failure}
     * is used unchanged.
     */
    public QuestDefinition toQuestDefinition(ResourceLocation id, boolean enabled, Optional<FailureSpec> failureOverride) {
        return new QuestDefinition(
                id,
                enabled,
                weight,
                Optional.of(CATEGORY),
                title,
                RepeatRule.DEFAULT,
                giver,
                dialogue,
                objectives,
                rewards,
                turnIn,
                conditions,
                Optional.empty(),
                failureOverride.isPresent() ? failureOverride : failure,
                template,
                offerShaping,
                // A situation's reputation lives on its own outcome block, not on the quest it
                // renders as, so the derived definition carries none of its own.
                dev.otectus.mcaquests.quest.reputation.QuestReputationBlock.NONE);
    }

    /**
     * A title fallback used when the offer omits one (synthetic quests have no lang key by default).
     * Renders inline placeholders (e.g. {@code {player}}) through {@code resolver}.
     */
    public Component titleOr(Component fallback, PlaceholderResolver resolver) {
        return title.map(text -> text.resolve(resolver)).orElse(fallback);
    }
}
