package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.TurnInMode;
import dev.otectus.mcaquests.quest.objective.BreakBlockObjective;
import dev.otectus.mcaquests.quest.objective.BreedAnimalsObjective;
import dev.otectus.mcaquests.quest.objective.BuildNearLocationObjective;
import dev.otectus.mcaquests.quest.objective.CraftItemObjective;
import dev.otectus.mcaquests.quest.objective.CureVillagerObjective;
import dev.otectus.mcaquests.quest.objective.DefendLocationObjective;
import dev.otectus.mcaquests.quest.objective.DefendVillagerObjective;
import dev.otectus.mcaquests.quest.objective.DeliverToVillagerObjective;
import dev.otectus.mcaquests.quest.objective.FishItemObjective;
import dev.otectus.mcaquests.quest.objective.FtbqCompleteQuestObjective;
import dev.otectus.mcaquests.quest.objective.HealEntityObjective;
import dev.otectus.mcaquests.quest.objective.KillEntityObjective;
import dev.otectus.mcaquests.quest.objective.PlaceBlockObjective;
import dev.otectus.mcaquests.quest.objective.TameAnimalObjective;
import dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective;
import dev.otectus.mcaquests.quest.objective.ObtainItemObjective;
import dev.otectus.mcaquests.quest.objective.QuestObjective;
import dev.otectus.mcaquests.quest.reward.CurrencyReward;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.target.EntityTarget;
import dev.otectus.mcaquests.quest.template.RegistryKind;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

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

    public static void validate(Map<ResourceLocation, QuestDefinition> loaded, List<String> errors,
                                List<String> warnings) {
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
                warnEmptyTags(def, i, objective, warnings);
                checkUnresolvedEntities(def, i, objective, errors, warnings);
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
     * Decides what an {@link EntityTarget} naming an entity type this world does not have is worth.
     *
     * <p>Since 1.5.4 such an id parses ({@code EntityTarget} keeps it instead of failing), which moves
     * the judgement here — and it is a judgement, because the same shape means two opposite things. An
     * id in an optional mod's namespace is content that is simply not installed: the pack is doing what
     * conditional content is supposed to do, the quest is kept, and it is never offerable while the
     * entity is missing. An id in {@code minecraft} or in the namespace of a mod that <em>is</em>
     * loaded cannot be that — it is a misspelling, and reporting it as an error is the only way its
     * author will ever find out.
     */
    private static void checkUnresolvedEntities(QuestDefinition def, int index, QuestObjective objective,
                                                List<String> errors, List<String> warnings) {
        for (EntityTarget target : entityTargets(objective)) {
            ResourceLocation id = target.unresolved().orElse(null);
            if (id == null) {
                continue;
            }
            String where = "Quest '" + def.id() + "': objective[" + index + "] names entity '" + id + "'";
            if (isOptionalContent(id.getNamespace())) {
                warnings.add(where + ", which is not registered here; the quest loads but can never be "
                        + "offered while that content is absent.");
            } else {
                errors.add(where + ", which does not exist. Check the spelling.");
            }
        }
    }

    /** Whether a namespace belongs to a known optional integration or to a mod that is not loaded. */
    private static boolean isOptionalContent(String namespace) {
        if (CompatRegistry.get().forNamespace(namespace).isPresent()) {
            return true;
        }
        if ("minecraft".equals(namespace)) {
            return false;
        }
        ModList list = ModList.get();
        return list != null && !list.isLoaded(namespace);
    }

    /** Every {@link EntityTarget} an objective type carries. */
    private static List<EntityTarget> entityTargets(QuestObjective objective) {
        if (objective instanceof KillEntityObjective kill) {
            return List.of(kill.target());
        }
        if (objective instanceof DefendVillagerObjective defend) {
            return List.of(defend.threat());
        }
        if (objective instanceof DefendLocationObjective defend) {
            return List.of(defend.threat());
        }
        if (objective instanceof BreedAnimalsObjective breed) {
            return List.of(breed.animal());
        }
        if (objective instanceof TameAnimalObjective tame) {
            return List.of(tame.animal());
        }
        return List.of();
    }

    /**
     * Warns when an objective names an item/block/entity tag that nothing is in.
     *
     * <p>An empty tag is not a parse error — the id is well-formed and the codec is satisfied — but it
     * matches nothing at all, so the objective can never advance and says nothing about why. Templates
     * have checked their pools for exactly this since 1.2.0 ({@code TemplateValidator.validatePool});
     * a plain quest could name the same misspelt tag and hear nothing. Guarded by {@code tagsBound()}
     * for the same reason that check is: before the first tag bind, every tag reads empty.
     */
    private static void warnEmptyTags(QuestDefinition def, int index, QuestObjective objective,
                                      List<String> warnings) {
        String where = "Quest '" + def.id() + "': objective[" + index + "]";
        if (objective instanceof ObtainItemObjective obtain) {
            warnEmptyTag(where, RegistryKind.ITEM, obtain.target().tag().map(TagKey::location), warnings);
        } else if (objective instanceof CraftItemObjective craft) {
            warnEmptyTag(where, RegistryKind.ITEM, craft.target().tag().map(TagKey::location), warnings);
        } else if (objective instanceof FishItemObjective fish) {
            warnEmptyTag(where, RegistryKind.ITEM, fish.target().tag().map(TagKey::location), warnings);
        } else if (objective instanceof HealEntityObjective heal) {
            warnEmptyTag(where, RegistryKind.ITEM, heal.item().tag().map(TagKey::location), warnings);
        } else if (objective instanceof CureVillagerObjective cure) {
            warnEmptyTag(where, RegistryKind.ITEM, cure.cureItem().tag().map(TagKey::location), warnings);
        } else if (objective instanceof DeliverToVillagerObjective deliver) {
            warnEmptyTag(where, RegistryKind.ITEM, deliver.item().tag().map(TagKey::location), warnings);
        } else if (objective instanceof BreakBlockObjective breakBlock) {
            warnEmptyTag(where, RegistryKind.BLOCK, breakBlock.target().tag().map(TagKey::location), warnings);
        } else if (objective instanceof PlaceBlockObjective place) {
            warnEmptyTag(where, RegistryKind.BLOCK, place.target().tag().map(TagKey::location), warnings);
        } else if (objective instanceof BuildNearLocationObjective build) {
            warnEmptyTag(where, RegistryKind.BLOCK, build.block().tag().map(TagKey::location), warnings);
        } else if (objective instanceof KillEntityObjective kill) {
            warnEmptyTag(where, RegistryKind.ENTITY, kill.target().tag().map(TagKey::location), warnings);
        } else if (objective instanceof DefendVillagerObjective defend) {
            warnEmptyTag(where, RegistryKind.ENTITY, defend.threat().tag().map(TagKey::location), warnings);
        } else if (objective instanceof DefendLocationObjective defend) {
            warnEmptyTag(where, RegistryKind.ENTITY, defend.threat().tag().map(TagKey::location), warnings);
        } else if (objective instanceof BreedAnimalsObjective breed) {
            warnEmptyTag(where, RegistryKind.ENTITY, breed.animal().tag().map(TagKey::location), warnings);
        } else if (objective instanceof TameAnimalObjective tame) {
            warnEmptyTag(where, RegistryKind.ENTITY, tame.animal().tag().map(TagKey::location), warnings);
        }
    }

    private static void warnEmptyTag(String where, RegistryKind kind, Optional<ResourceLocation> tag,
                                     List<String> warnings) {
        if (tag.isEmpty() || !kind.tagsBound()) {
            return;
        }
        if (kind.staticMembers(tag.get()).isEmpty()) {
            warnings.add(where + " uses " + kind.key() + " tag '" + tag.get()
                    + "' which is empty or unknown, so it can never match.");
        }
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
