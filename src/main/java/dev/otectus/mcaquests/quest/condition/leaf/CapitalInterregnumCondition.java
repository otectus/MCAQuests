package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.capitals.CapitalsCompat;
import dev.otectus.mcaquests.compat.capitals.CapitalsQueries;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * True while the giver's capital is between sovereigns (MCA Capitals).
 *
 * <pre>{@code {"type": "mcaquests:capital_interregnum", "present": false}}</pre>
 *
 * <p>Written {@code "present": false} far more often than true: an errand for the sovereign has nobody
 * to run it for while the throne stands empty, so the quests that name one gate themselves out of the
 * interregnum rather than being offered and then pausing.
 */
public record CapitalInterregnumCondition(boolean present) implements QuestCondition {

    public static final Codec<CapitalInterregnumCondition> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(Codec.BOOL, "present", true)
                            .forGetter(CapitalInterregnumCondition::present)
            ).apply(instance, CapitalInterregnumCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.CAPITAL_INTERREGNUM;
    }

    @Override
    public boolean test(QuestContext context) {
        Entity giver = context.villager();
        boolean vacant = giver != null && giver.level() instanceof ServerLevel level
                && CapitalsQueries.giverCapital(giver)
                        .map(cap -> CapitalsCompat.bridge().interregnum(level, cap).isPresent())
                        .orElse(false);
        return vacant == present;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.condition.capital_interregnum");
    }
}
