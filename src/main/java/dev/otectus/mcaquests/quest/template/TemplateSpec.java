package dev.otectus.mcaquests.quest.template;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.objective.ObjectiveTypes;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.reward.RewardTypes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The optional {@code template} block of a {@link dev.otectus.mcaquests.quest.QuestDefinition}: a set
 * of named {@link TemplateVariable}s plus the <em>raw</em> objective/reward JSON that references them
 * via {@code {var}} placeholders. The raw JSON is kept unparsed (placeholders are not valid ids/ints
 * until substituted), then concretized per offer:
 *
 * <ol>
 *   <li>{@link #resolveValues(QuestContext)} picks one concrete {@link ResolvedValue} per variable
 *       (server-side, deterministic per offer) — empty if any pool has no eligible entry;</li>
 *   <li>{@link #toConcrete(ResolvedTemplate)} substitutes those values into the raw JSON and parses it
 *       with the existing objective/reward codecs — empty if the substituted JSON fails to parse.</li>
 * </ol>
 *
 * Dialogue/title placeholders are handled separately by {@link PlaceholderResolver} at render time, so
 * they are not part of this block.
 */
public record TemplateSpec(Map<String, TemplateVariable> variables, JsonElement objectives, JsonElement rewards) {

    /** A {@link JsonElement} field that survives the codec untouched (we only ever parse with JsonOps). */
    private static final Codec<JsonElement> JSON = Codec.PASSTHROUGH.flatXmap(
            dynamic -> DataResult.success(dynamic.convert(JsonOps.INSTANCE).getValue()),
            json -> DataResult.success(new Dynamic<>(JsonOps.INSTANCE, json)));

    public static final Codec<TemplateSpec> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.unboundedMap(Codec.STRING, TemplateVariable.CODEC).fieldOf("variables")
                            .forGetter(TemplateSpec::variables),
                    JSON.fieldOf("objectives").forGetter(TemplateSpec::objectives),
                    JSON.lenientOptionalFieldOf("rewards", new JsonArray()).forGetter(TemplateSpec::rewards)
            ).apply(instance, TemplateSpec::new));

    /** The concrete objectives/rewards produced by substituting a {@link ResolvedTemplate}. */
    public record Concrete(List<QuestObjective> objectives, List<QuestReward> rewards) {
    }

    /**
     * Picks one concrete value per declared variable for this offer. Returns empty if <em>any</em>
     * variable's pool resolves to nothing (e.g. an empty/unknown tag), which the caller treats as
     * "this template cannot be offered right now".
     */
    public Optional<ResolvedTemplate> resolveValues(QuestContext context) {
        Map<String, ResolvedValue> values = new LinkedHashMap<>();
        for (Map.Entry<String, TemplateVariable> entry : variables.entrySet()) {
            Optional<ResolvedValue> resolved = entry.getValue().resolve(entry.getKey(), context);
            if (resolved.isEmpty()) {
                return Optional.empty();
            }
            values.put(entry.getKey(), resolved.get());
        }
        return Optional.of(new ResolvedTemplate(values));
    }

    /**
     * Substitutes {@code values} into the raw objective/reward JSON and parses it. Returns empty if the
     * substituted JSON fails to parse (unresolved placeholder, bad combination) — the caller logs and
     * declines the offer rather than crashing.
     */
    public Optional<Concrete> toConcrete(ResolvedTemplate values) {
        PlaceholderResolver resolver = new PlaceholderResolver(values);
        Optional<List<QuestObjective>> objs = ObjectiveTypes.CODEC.listOf()
                .parse(JsonOps.INSTANCE, resolver.substitute(objectives)).result();
        Optional<List<QuestReward>> rews = RewardTypes.CODEC.listOf()
                .parse(JsonOps.INSTANCE, resolver.substitute(rewards)).result();
        if (objs.isEmpty() || rews.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Concrete(objs.get(), rews.get()));
    }
}
