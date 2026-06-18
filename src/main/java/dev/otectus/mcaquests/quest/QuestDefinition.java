package dev.otectus.mcaquests.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.objective.ObjectiveTypes;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.reward.RewardTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

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
        RepeatRule repeat,
        GiverSpec giver,
        Map<String, QuestText> dialogue,
        List<QuestObjective> objectives,
        List<QuestReward> rewards,
        TurnInSpec turnIn,
        Optional<QuestCondition> conditions) {

    /** Dialogue states (spec section 9). */
    public static final String OFFER = "offer";
    public static final String ACCEPT = "accept";
    public static final String DECLINE = "decline";
    public static final String IN_PROGRESS = "in_progress";
    public static final String READY = "ready";
    public static final String COMPLETE = "complete";
    public static final String COOLDOWN = "cooldown";
    public static final String LOCKED = "locked";

    public static final Codec<QuestDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(QuestDefinition::id),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(QuestDefinition::enabled),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("weight", 1).forGetter(QuestDefinition::weight),
            Codec.STRING.optionalFieldOf("category").forGetter(QuestDefinition::category),
            RepeatRule.CODEC.optionalFieldOf("repeat", RepeatRule.DEFAULT).forGetter(QuestDefinition::repeat),
            GiverSpec.CODEC.fieldOf("giver").forGetter(QuestDefinition::giver),
            Codec.unboundedMap(Codec.STRING, QuestText.CODEC).fieldOf("dialogue").forGetter(QuestDefinition::dialogue),
            ObjectiveTypes.CODEC.listOf().fieldOf("objectives").forGetter(QuestDefinition::objectives),
            RewardTypes.CODEC.listOf().fieldOf("rewards").forGetter(QuestDefinition::rewards),
            TurnInSpec.CODEC.optionalFieldOf("turn_in", TurnInSpec.DEFAULT).forGetter(QuestDefinition::turnIn),
            ConditionTypes.CODEC.optionalFieldOf("conditions").forGetter(QuestDefinition::conditions)
    ).apply(instance, QuestDefinition::new));

    /** Translation key for this quest's display title (spec section 32), e.g. {@code mcaquests.quest.<path>.title}. */
    public String titleKey() {
        return "mcaquests.quest." + id.getPath() + ".title";
    }

    public Component title() {
        return Component.translatable(titleKey());
    }

    /** Resolves a dialogue line for the given state, or {@code fallback} if the quest omits it. */
    public Component dialogueOr(String state, Component fallback) {
        QuestText line = dialogue.get(state);
        return line != null ? line.resolve() : fallback;
    }

    public int cooldownTicks() {
        return repeat.cooldownTicks();
    }
}
