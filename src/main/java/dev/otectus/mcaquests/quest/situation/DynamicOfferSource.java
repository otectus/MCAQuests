package dev.otectus.mcaquests.quest.situation;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.McaQuestsConfig.ProfessionMatchingMode;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.profession.ProfessionMatcher;
import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.situation.state.SituationInstance;
import dev.otectus.mcaquests.quest.situation.state.SituationSavedData;
import dev.otectus.mcaquests.state.PlayerQuestData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;

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
 * village that this villager is eligible to surface (scope + giver match). The offers then compete with
 * static quests through the existing selection/shaping pipeline.
 *
 * <p>The scope/giver predicates are split out as pure helpers so they are unit-testable without a server.
 */
public final class DynamicOfferSource {

    private DynamicOfferSource() {
    }

    /**
     * Whether this offer's own condition gate lets this villager be the one who asks.
     *
     * <p>Evaluated against the candidate giver, under the situation's synthetic quest id so a condition
     * that asks about quest history means the situation rather than nothing.
     */
    private static boolean offerConditionsPass(SituationDefinition def, ServerPlayer player,
                                               Entity villager, PlayerQuestData data) {
        return def.offer().conditions()
                .map(condition -> condition.test(new QuestContext(player, villager, data,
                        SituationIds.syntheticId(def.id()))))
                .orElse(true);
    }

    public static List<QuestDefinition> collect(ServerPlayer player, Entity villager, PlayerQuestData data,
                                                @Nullable ResourceLocation profession, boolean adult, int hearts) {
        if (!McaQuestsConfig.COMMON.enableSituations.get()) {
            return List.of();
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return List.of();
        }
        OptionalInt villageId = McaCompat.getHomeVillageId(villager);
        if (villageId.isEmpty()) {
            return List.of();
        }
        List<SituationInstance> open = SituationSavedData.get(server).openInstancesInVillage(villageId.getAsInt());
        if (open.isEmpty()) {
            return List.of();
        }

        UUID villagerUuid = villager.getUUID();
        UUID villagerFamily = McaCompat.getFamilyRootId(villager).orElse(null);
        ProfessionMatchingMode mode = McaQuestsConfig.COMMON.professionMatchingMode.get();
        int cap = McaQuestsConfig.COMMON.maxSituationOffersPerMenu.get();

        List<QuestDefinition> offers = new ArrayList<>();
        for (SituationInstance instance : open) {
            if (offers.size() >= cap) {
                break;
            }
            Optional<SituationDefinition> defOpt = SituationRegistry.get(instance.defId());
            if (defOpt.isEmpty() || !defOpt.get().enabled()) {
                continue;
            }
            SituationDefinition def = defOpt.get();
            if (!scopeMatches(def.scope(), instance.villagerUuid().orElse(null), instance.familyRootUuid().orElse(null),
                    villagerUuid, villagerFamily)) {
                continue;
            }
            if (!giverEligible(def.offer().giver(), profession, adult, hearts, mode)) {
                continue;
            }
            // Situation offers are added to the pool after the static filter chain has run, so their
            // conditions have to be tested here or they would never be tested at all.
            if (!offerConditionsPass(def, player, villager, data)) {
                continue;
            }
            // Don't re-offer one this player already accepted from this villager.
            if (data.hasActive(def.syntheticId(), villagerUuid)) {
                continue;
            }
            offers.add(def.toOfferQuestDefinition());
        }
        return offers;
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
