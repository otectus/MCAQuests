package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.TurnInMode;
import dev.otectus.mcaquests.quest.objective.FtbqCompleteQuestObjective;
import dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective;
import dev.otectus.mcaquests.quest.objective.ObtainItemObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.reward.CurrencyReward;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Cross-cutting validation of quest objectives, run after all quests load (spec section 26). Codecs
 * already reject unknown item/block/entity ids and out-of-range counts at parse time; this drives
 * each objective's {@link QuestObjective#validate} hook for the semantic checks a codec cannot express
 * (a {@code villager}/{@code anchor}/{@code structure} target missing the field its mode requires, an
 * unknown family relation, …). Problems are appended to the same error list surfaced by
 * {@code /mcaquests validate} and honour {@code strictJsonValidation}.
 *
 * <p>One rule here is stronger than "report and move on": a quest using {@code ftbq_complete_quest}
 * (spec §18) while FTB Quests itself is not installed has an objective that can <em>never</em> be
 * satisfied, so under lenient validation the whole quest is removed from {@code loaded} (never entering
 * the offer pool) rather than merely logged — a clear log line names the quest so this isn't a silent
 * disappearance. Under {@code strictJsonValidation} it is a hard load error instead, exactly like every
 * other problem this class reports. Absence here means the "ftbquests" <em>mod</em> is not loaded — the
 * {@code enableFtbQuestsIntegration} config toggle is deliberately not consulted: a config-disabled
 * integration still loads these quests, since re-enabling the config (no reload required for that check)
 * lets them start working again (spec §24).
 */
public final class ObjectiveValidator {

    private ObjectiveValidator() {
    }

    public static void validate(Map<ResourceLocation, QuestDefinition> loaded, List<String> errors) {
        boolean strict = McaQuestsConfig.COMMON.strictJsonValidation.get();
        // Computed lazily (at most once) and only if some quest actually uses the objective, so loading
        // a datapack with no ftbq_complete_quest objectives never touches ModList at all.
        Boolean ftbqLoaded = null;
        List<ResourceLocation> toSkip = new ArrayList<>();

        for (QuestDefinition def : loaded.values()) {
            List<QuestObjective> objectives = def.objectives();
            boolean usesFtbqCompleteQuest = false;
            Set<String> possessed = new HashSet<>();
            List<String> unsatisfiable = new ArrayList<>();
            for (int i = 0; i < objectives.size(); i++) {
                QuestObjective objective = objectives.get(i);
                objective.validate(def.id(), i, errors);
                usesFtbqCompleteQuest |= objective instanceof FtbqCompleteQuestObjective;
                // Two objectives asking the player to hold the same item are both satisfied by one stack:
                // the quest reads as "gather ten, then deliver ten" and completes on ten.
                Optional<String> possession = possessionIdentity(objective);
                if (possession.isPresent() && !possessed.add(possession.get())) {
                    unsatisfiable.add("Quest '" + def.id() + "' has two possession objectives for "
                            + possession.get() + "; one stack of it would satisfy both");
                }
            }
            // A turn-in mode that names the professions it wants, and then names none, has no villager
            // anywhere in the world that can accept the quest.
            if (def.turnIn().declaredMode().orElse(null) == TurnInMode.SPECIFIED_PROFESSION
                    && def.turnIn().professions().isEmpty()) {
                unsatisfiable.add("Quest '" + def.id() + "' declares turn_in mode 'specified_profession' "
                        + "with an empty 'professions' list, so it could never be handed in");
            }
            for (String problem : unsatisfiable) {
                if (strict) {
                    errors.add(problem + ".");
                } else {
                    errors.add(problem + "; the quest is skipped at load (lenient mode).");
                    McaQuests.LOGGER.warn("[MCA: Quests] Skipping quest '{}' at load: {}.", def.id(), problem);
                    toSkip.add(def.id());
                }
            }
            List<QuestReward> rewards = def.rewards();
            for (int i = 0; i < rewards.size(); i++) {
                if (rewards.get(i) instanceof CurrencyReward currency) {
                    currency.validate("Quest '" + def.id() + "' reward " + i, errors);
                }
            }
            if (!usesFtbqCompleteQuest) {
                continue;
            }
            if (ftbqLoaded == null) {
                ftbqLoaded = ftbqLoaded();
            }
            if (ftbqLoaded) {
                continue;
            }
            String reason = "Quest '" + def.id() + "' uses objective type 'mcaquests:ftbq_complete_quest' "
                    + "but FTB Quests is not installed; that objective could never be satisfied";
            if (strict) {
                errors.add(reason + ".");
            } else {
                errors.add(reason + "; the quest is skipped at load (lenient mode).");
                McaQuests.LOGGER.warn("[MCA: Quests] Skipping quest '{}' at load: it uses "
                        + "'mcaquests:ftbq_complete_quest' but FTB Quests is not installed, so it "
                        + "could never be completed.", def.id());
                toSkip.add(def.id());
            }
        }

        toSkip.forEach(loaded::remove);
    }

    /**
     * What this objective requires the player to be holding, if it is one of the two that do.
     *
     * <p>An obtain and a delivery of the same {@link net.minecraft.world.item.Item} share an identity:
     * the delivery takes the stack the obtain counted, so a pack author writing both means "gather then
     * hand over" and gets "gather" for free.
     */
    private static Optional<String> possessionIdentity(QuestObjective objective) {
        if (objective instanceof ObtainItemObjective obtain) {
            return obtain.target().item().map(item -> "item " + BuiltInRegistries.ITEM.getKey(item))
                    .or(() -> obtain.target().tag().map(tag -> "tag " + tag.location()));
        }
        if (objective instanceof ItemDeliveryObjective delivery) {
            return Optional.of("item " + BuiltInRegistries.ITEM.getKey(delivery.item()));
        }
        return Optional.empty();
    }

    /** Whether the "ftbquests" mod itself is loaded — deliberately independent of any MCA: Quests config. */
    private static boolean ftbqLoaded() {
        return ModList.get() != null && ModList.get().isLoaded("ftbquests");
    }
}
