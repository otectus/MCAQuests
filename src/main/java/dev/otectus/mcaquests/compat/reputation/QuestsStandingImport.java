package dev.otectus.mcaquests.compat.reputation;

import dev.otectus.mcaquests.quest.reputation.ReputationTiers;
import dev.otectus.mcaquests.state.VillageStanding;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Selects the retained Quests snapshot without linking the optional Reputation API. */
final class QuestsStandingImport {
    private QuestsStandingImport() {}

    record Community(ResourceLocation dimension, int villageId) {
        String key() {
            return VillageStanding.communityKey(dimension, villageId);
        }
    }

    record Snapshot(int score, Optional<String> highWater, Set<ResourceLocation> titles) {}

    static Map<Community, Snapshot> select(VillageStanding standing, UUID player,
                                            Map<String, Integer> sharedScores,
                                            Map<String, String> sharedHighWater,
                                            boolean mayInheritShared, Set<String> canonicalKeys) {
        Map<Community, Snapshot> selected = new LinkedHashMap<>();
        Set<String> current = standing.communities(player);
        if (!current.isEmpty()) {
            // v1 tags are retained for rollback; adding them to v2 would award that history twice.
            for (String identity : current) {
                parse(identity).filter(community -> !canonicalKeys.contains(community.key()))
                        .ifPresent(community -> selected.put(community, new Snapshot(
                                standing.score(player, community.dimension(), community.villageId()),
                                standing.tierHighWater(player, ReputationTiers.DEFAULT_ID,
                                        community.dimension(), community.villageId()),
                                Set.copyOf(standing.villageTitles(player, community.dimension(),
                                        community.villageId())))));
            }
        } else if (mayInheritShared) {
            ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
            sharedScores.forEach((identity, score) -> VillageStanding.parseLegacyVillageId(identity)
                    .filter(id -> id >= 0).ifPresent(id -> {
                        Community community = new Community(overworld, id);
                        if (!canonicalKeys.contains(community.key())) {
                            selected.put(community, new Snapshot(score,
                                    Optional.ofNullable(sharedHighWater.get(identity))
                                            .filter(tier -> !tier.isBlank()), Set.of()));
                        }
                    }));
        }
        return selected;
    }

    private static Optional<Community> parse(String identity) {
        if (identity == null) return Optional.empty();
        int split = identity.lastIndexOf('/');
        if (split <= 0 || split == identity.length() - 1) return Optional.empty();
        ResourceLocation dimension = ResourceLocation.tryParse(identity.substring(0, split));
        if (dimension == null) return Optional.empty();
        try {
            int id = Integer.parseInt(identity.substring(split + 1));
            return id < 0 ? Optional.empty() : Optional.of(new Community(dimension, id));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
