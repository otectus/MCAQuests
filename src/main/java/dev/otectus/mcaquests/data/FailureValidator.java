package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.FailureSpec;
import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Cross-cutting validation of {@code failure} blocks, run after all quests load. Numeric ranges
 * ({@code deadline_ticks > 0}, {@code deadline_time} in 0..24000, {@code retry_after >= 0}) are already
 * enforced by the codec at parse time; this catches the semantic problems a codec cannot:
 *
 * <ul>
 *   <li>A {@code failure} block with no trigger at all — it can never fire, so the author almost
 *       certainly forgot a {@code deadline_*} / {@code require_weather} / {@code fail_on_giver_death}.</li>
 *   <li>A {@code failure_hearts} magnitude larger than the configured hearts clamp (likely a typo).</li>
 *   <li>{@code block_retry} together with {@code retry_after} — contradictory (the lock wins).</li>
 * </ul>
 *
 * Problems are appended to the same error list surfaced by {@code /mcaquests validate} and honour
 * {@code strictJsonValidation}.
 */
public final class FailureValidator {

    private FailureValidator() {
    }

    public static void validate(Map<ResourceLocation, QuestDefinition> loaded, List<String> errors) {
        int heartsClamp = McaQuestsConfig.COMMON.maxHeartsReward.get();
        for (QuestDefinition def : loaded.values()) {
            if (def.failure().isEmpty()) {
                continue;
            }
            FailureSpec failure = def.failure().get();
            ResourceLocation id = def.id();

            if (!failure.hasTrigger()) {
                errors.add("Quest '" + id + "': has a 'failure' block but declares no trigger "
                        + "(none of deadline_ticks, deadline_time, require_weather, fail_on_giver_death) "
                        + "— it can never fail.");
            }
            if (Math.abs(failure.failureHearts()) > heartsClamp) {
                errors.add("Quest '" + id + "': failure_hearts " + failure.failureHearts()
                        + " exceeds the configured hearts clamp of ±" + heartsClamp + " — likely a typo.");
            }
            if (failure.blockRetry() && failure.retryAfterTicks().isPresent()) {
                errors.add("Quest '" + id + "': sets both block_retry and retry_after; "
                        + "block_retry locks the quest permanently, so retry_after is ignored.");
            }
        }
    }
}
