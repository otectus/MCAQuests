package dev.otectus.mcaquests.quest.situation;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.McaQuestsConfig.ProfessionMatchingMode;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.OfferFilters;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.situation.state.SituationInstance;
import dev.otectus.mcaquests.quest.situation.trigger.VillagerDeathTrigger;
import dev.otectus.mcaquests.quest.situation.state.SituationSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Feeds open situations into the quest offer pipeline as dynamic, time-limited offers (the "Living
 * Village" phase, 0.8.0). Called from {@code QuestManager.eligibleOffers}, it returns the synthetic
 * offer {@link QuestDefinition}s for every open {@link SituationInstance} in the interacted villager's
 * village that this villager is eligible to surface. The offers then compete with static quests through
 * the existing selection/shaping pipeline.
 *
 * <p><b>Two gates, and they are different questions.</b> Whether this <em>instance</em> concerns this
 * villager at all — scope, and that the player has not already taken it — is answered here, because it is
 * about the situation. Everything else is answered by {@link OfferFilters}, exactly as it is for a static
 * quest.
 *
 * <p>That split is new in 1.4.3 and it closes a real hole. Situation offers were appended to the pool
 * <em>after</em> the static filter chain had run, so they skipped cooldowns, repeat rules, the
 * trivially-satisfied check, the Townstead content gate and — once it existed — the check that the
 * villager an objective names actually exists. {@code cure_the_infected} and {@code lost_survey_party}
 * both reached players asking about a relative of the giver, with no gate of any kind.
 *
 * <p>The scope/giver predicates are split out as pure helpers so they are unit-testable without a server.
 */
public final class DynamicOfferSource {

    private DynamicOfferSource() {
    }

    /**
     * The situation offers {@code pass}'s villager can currently surface, each already through the same
     * filter chain a static quest passes.
     */
    public static List<QuestDefinition> collect(OfferFilters.Pass pass) {
        if (!McaQuestsConfig.COMMON.enableSituations.get()) {
            return List.of();
        }
        MinecraftServer server = pass.player().getServer();
        if (server == null) {
            return List.of();
        }
        Entity villager = pass.villager();
        OptionalInt villageId = McaCompat.getHomeVillageId(villager);
        if (villageId.isEmpty()) {
            return List.of();
        }
        List<SituationInstance> open = SituationSavedData.get(server).openInstancesInVillage(villageId.getAsInt());
        if (open.isEmpty()) {
            return List.of();
        }

        UUID villagerUuid = pass.villagerUuid();
        UUID villagerFamily = McaCompat.getFamilyRootId(villager).orElse(null);
        int cap = McaQuestsConfig.COMMON.maxSituationOffersPerMenu.get();

        List<QuestDefinition> offers = new ArrayList<>();
        for (SituationInstance instance : open) {
            if (offers.size() >= cap) {
                break;
            }
            Optional<SituationDefinition> defOpt = SituationRegistry.get(instance.defId());
            if (defOpt.isEmpty()) {
                continue;
            }
            SituationDefinition def = defOpt.get();
            if (!scopeMatches(def.scope(), instance.villagerUuid().orElse(null),
                    instance.familyRootUuid().orElse(null), villagerUuid, villagerFamily)) {
                continue;
            }
            if (!bereavementMatches(def, instance, villager)) {
                continue;
            }
            // Every other question — enabled, giver, hearts, cooldown, repeat rule, conditions, already
            // satisfied, and whether the villager it names exists — is the same question a static quest
            // is asked, so it is asked by the same code.
            QuestDefinition offer = def.toOfferQuestDefinition();
            if (!OfferFilters.passes(pass, offer)) {
                continue;
            }
            offers.add(offer);
        }
        return offers;
    }

    /**
     * Whether this villager is close enough to the death for the situation to be theirs to raise.
     *
     * <p>{@code VillagerDeathTrigger}'s {@code relation} promised in its own javadoc to be "applied at
     * offer eligibility", and no such call site existed: the field was parsed and ignored, so "only a
     * villager who lost a child offers this" was a sentence in a comment and nothing else. This is that
     * call site.
     *
     * <p>{@code any} — the default, and what every shipped situation uses — narrows nothing, so this is
     * free for existing content. Anything else fails closed when the death's villager or the level cannot
     * be read: an unanswerable filter withholds the offer rather than ignoring itself.
     */
    private static boolean bereavementMatches(SituationDefinition def, SituationInstance instance,
                                              Entity villager) {
        if (!(def.trigger() instanceof VillagerDeathTrigger death) || death.relation().equals("any")) {
            return true;
        }
        UUID dead = instance.villagerUuid().orElse(null);
        if (dead == null || !(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        return McaCompat.relativeCandidates(level, villager, death.relation()).stream()
                .anyMatch(candidate -> candidate.uuid().equals(dead));
    }

    /** Whether {@code villager} is in this situation's scope (pure). */
    public static boolean scopeMatches(SituationScope scope, @Nullable UUID focalVillager, @Nullable UUID focalFamily,
                                       UUID villagerUuid, @Nullable UUID villagerFamily) {
        return switch (scope) {
            case VILLAGE -> true;
            case VILLAGER -> focalVillager != null && focalVillager.equals(villagerUuid);
            case FAMILY -> focalFamily != null && focalFamily.equals(villagerFamily);
        };
    }

    /** Whether {@code villager} satisfies the offer's {@link GiverSpec} (adult/hearts/profession) — pure. */
    public static boolean giverEligible(GiverSpec giver, @Nullable ResourceLocation profession, boolean adult,
                                        int hearts, ProfessionMatchingMode mode) {
        if (giver.adultOnly() && !adult) {
            return false;
        }
        if (!giver.acceptsHearts(hearts)) {
            return false;
        }
        return giver.isGeneric() || ProfessionMatcher.matchesAny(giver.professions(), profession, mode);
    }
}
