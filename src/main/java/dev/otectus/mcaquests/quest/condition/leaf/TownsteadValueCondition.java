package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadQuery;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.target.TownsteadTargetResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Compares one Townstead value against a literal (Townstead spec §5.1) — the general-purpose gate that
 * most Townstead content is written against.
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_value",
 *   "source": "villager",
 *   "target": "giver",
 *   "path": "needs.hunger",
 *   "operator": "lte",
 *   "value": 30
 * }
 * }</pre>
 *
 * <p>The whole contract lives in {@link TownsteadQuery}: paths, operators, limits and the
 * {@code missing} policy are validated at reload, so a malformed comparison fails the datapack rather
 * than a quest. This class only resolves which villager is being asked about.
 */
public record TownsteadValueCondition(TownsteadQuery query) implements QuestCondition {

    public static final Codec<TownsteadValueCondition> CODEC =
            TownsteadQuery.CODEC.xmap(TownsteadValueCondition::new, TownsteadValueCondition::query);

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.TOWNSTEAD_VALUE;
    }

    @Override
    public boolean test(QuestContext context) {
        TownsteadEvaluation evaluation = context.mca().townstead();
        // A global source (the calendar) has no subject villager, so the target is not resolved at all.
        Entity target = query.source().isGlobal()
                ? context.villager()
                : TownsteadTargetResolver
                        .resolveForOffer(query.target(), context.player(), context.villager(), context.level())
                        .orElse(null);
        if (target == null) {
            return query.missing();
        }
        return query.testResolved(evaluation.subject(query, target),
                TownsteadEvaluation.effectivePath(query));
    }

    @Override
    public Component describe() {
        return Component.literal(query.describe());
    }
}
