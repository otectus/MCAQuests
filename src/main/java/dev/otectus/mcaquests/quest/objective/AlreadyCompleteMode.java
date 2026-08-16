package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * How {@link FtbqCompleteQuestObjective} treats an FTB Quests quest that is already complete when the
 * MCA: Quests offer is accepted (spec §18).
 */
public enum AlreadyCompleteMode {
    /** An already-completed FTB quest satisfies the objective at accept (the first poll). */
    SATISFY,
    /**
     * The quest's effective offer conditions are auto-wrapped with a {@code not(ftbq_quest_completed)}
     * check (see {@code QuestDefinition#effectiveConditions()}), so the offer never appears once the
     * linked FTB quest is done.
     */
    BLOCK_OFFER;

    public boolean blocksOffer() {
        return this == BLOCK_OFFER;
    }

    public static final Codec<AlreadyCompleteMode> CODEC = Codec.STRING.flatXmap(
            name -> {
                try {
                    return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown already_complete mode: '" + name
                            + "' (expected satisfy/block_offer)");
                }
            },
            value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));
}
