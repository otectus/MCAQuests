package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.HistoryScope;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.condition.composite.AllOfCondition;
import dev.otectus.mcaquests.quest.condition.leaf.QuestCompletedCondition;
import dev.otectus.mcaquests.quest.objective.ObjectiveTypes;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.reward.RewardTypes;
import dev.otectus.mcaquests.quest.template.PlaceholderResolver;
import dev.otectus.mcaquests.quest.template.TemplateSpec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable, data-loaded quest template (spec section 11). Parsed from
 * {@code data/<ns>/mcaquests/quests/**.json} by {@code QuestDataLoader}.
 */
public record QuestDefinition(
        ResourceLocation id,
        boolean enabled,
        int weight,
        Optional<String> category,
        Optional<QuestText> titleOverride,
        RepeatRule repeat,
        GiverSpec giver,
        Map<String, QuestText> dialogue,
        List<QuestObjective> objectives,
        List<QuestReward> rewards,
        TurnInSpec turnIn,
        Optional<QuestCondition> conditions,
        Optional<ChainSpec> chain,
        Optional<FailureSpec> failure,
        Optional<TemplateSpec> template,
        OfferShaping offerShaping) {

    /** Dialogue states (spec section 9). */
    public static final String OFFER = "offer";
    public static final String ACCEPT = "accept";
    public static final String DECLINE = "decline";
    public static final String IN_PROGRESS = "in_progress";
    public static final String READY = "ready";
    public static final String COMPLETE = "complete";
    public static final String COOLDOWN = "cooldown";
    public static final String LOCKED = "locked";
    public static final String FAILED = "failed";

    public static final Codec<QuestDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(QuestDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(QuestDefinition::enabled),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(QuestDefinition::weight),
            Codec.STRING.optionalFieldOf("category").forGetter(QuestDefinition::category),
            QuestText.CODEC.optionalFieldOf("title").forGetter(QuestDefinition::titleOverride),
            RepeatRule.CODEC.optionalFieldOf("repeat", RepeatRule.DEFAULT).forGetter(QuestDefinition::repeat),
            GiverSpec.CODEC.fieldOf("giver").forGetter(QuestDefinition::giver),
            Codec.unboundedMap(Codec.STRING, QuestText.CODEC).fieldOf("dialogue").forGetter(QuestDefinition::dialogue),
            ObjectiveTypes.CODEC.listOf().optionalFieldOf("objectives", List.of()).forGetter(QuestDefinition::objectives),
            RewardTypes.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(QuestDefinition::rewards),
            TurnInSpec.CODEC.optionalFieldOf("turn_in", TurnInSpec.DEFAULT).forGetter(QuestDefinition::turnIn),
            ConditionTypes.CODEC.optionalFieldOf("conditions").forGetter(QuestDefinition::conditions),
            ChainSpec.CODEC.optionalFieldOf("chain").forGetter(QuestDefinition::chain),
            FailureSpec.CODEC.optionalFieldOf("failure").forGetter(QuestDefinition::failure),
            TemplateSpec.CODEC.optionalFieldOf("template").forGetter(QuestDefinition::template),
            OfferShaping.MAP_CODEC.forGetter(QuestDefinition::offerShaping)
    ).apply(instance, QuestDefinition::new));

    /** Translation key for this quest's display title (spec section 32), e.g. {@code mcaquests.quest.<path>.title}. */
    public String titleKey() {
        return "mcaquests.quest." + id.getPath() + ".title";
    }

    /**
     * The display title, resolving inline {@code {token}} placeholders (e.g. {@code {player}} and template
     * variables) through {@code resolver}.
     */
    public Component title(PlaceholderResolver resolver) {
        return titleOverride.map(text -> text.resolve(resolver)).orElseGet(() -> Component.translatable(titleKey()));
    }

    /**
     * Resolves the dialogue line for {@code state} (filling {@code {token}} placeholders via
     * {@code resolver}), or {@code fallback} if the quest omits that state.
     */
    public Component dialogueOr(String state, Component fallback, PlaceholderResolver resolver) {
        QuestText line = dialogue.get(state);
        if (line == null) {
            return fallback;
        }
        return line.resolve(resolver);
    }

    public boolean isTemplate() {
        return template.isPresent();
    }

    /**
     * A copy of this definition with its objectives/rewards replaced by the concrete ones produced from
     * a resolved template. All other fields (giver, conditions, chain, turn-in, dialogue, …) are
     * preserved, so the rest of the lifecycle treats a concretized template exactly like a normal quest.
     */
    public QuestDefinition withConcrete(TemplateSpec.Concrete concrete) {
        return new QuestDefinition(id, enabled, weight, category, titleOverride, repeat, giver, dialogue,
                concrete.objectives(), concrete.rewards(), turnIn, conditions, chain, failure, template,
                offerShaping);
    }

    /** Datapack offer priority tier, if set (see {@link OfferShaping}). */
    public Optional<Integer> priority() {
        return offerShaping.priority();
    }

    /** Conditional selection-weight bonuses (see {@link OfferShaping}). */
    public List<WeightBonus> weightBonus() {
        return offerShaping.weightBonus();
    }

    /**
     * The selection weight for this offer in {@code context}: the base {@link #weight()} plus every
     * {@link WeightBonus} whose condition holds, clamped to at least 1 (mirrors {@code WeightedPicker},
     * which treats sub-1 weights as 1). Identical to {@code weight()} when no {@code weight_bonus} is set.
     */
    public int effectiveWeight(QuestContext context) {
        int total = weight;
        for (WeightBonus bonus : weightBonus()) {
            total += bonus.evaluate(context);
        }
        return Math.max(1, total);
    }

    public int cooldownTicks() {
        return repeat.cooldownTicks();
    }

    /**
     * The condition gate actually used for offer eligibility: the author's {@code conditions} AND a
     * {@code quest_completed} requirement for each chain {@code prerequisite}. This is the single
     * desugaring point for prerequisites — they reuse the existing condition system, so offer
     * filtering needs no chain-specific logic.
     */
    public Optional<QuestCondition> effectiveConditions() {
        List<ResourceLocation> prerequisites = chain.map(ChainSpec::prerequisites).orElse(List.of());
        if (prerequisites.isEmpty()) {
            return conditions;
        }
        List<QuestCondition> all = new ArrayList<>();
        conditions.ifPresent(all::add);
        for (ResourceLocation prerequisite : prerequisites) {
            // GIVER scope: a chain stage is gated on what the player did with THIS villager, so the same
            // arc can be lived out independently with different villagers (per-villager relationship arcs).
            all.add(new QuestCompletedCondition(prerequisite, HistoryScope.GIVER));
        }
        return Optional.of(new AllOfCondition(all));
    }
}
