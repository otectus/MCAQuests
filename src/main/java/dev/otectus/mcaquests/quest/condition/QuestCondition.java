package dev.otectus.mcaquests.quest.condition;

import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * A boolean gate on whether a quest may be offered (spec section 13). Registry-driven leaves plus
 * the {@code all_of}/{@code any_of}/{@code not} composites.
 */
public interface QuestCondition {

    /** The registry type for leaf conditions; {@code null} for composites (handled by the codec directly). */
    @Nullable
    QuestConditionType<?> type();

    boolean test(QuestContext context);

    default Component describe() {
        return Component.empty();
    }
}
