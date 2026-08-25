package dev.otectus.mcaquests.compat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything Townstead knows about one villager, normalised for MCA: Quests (Townstead spec §2.3).
 *
 * <p>Two deliberate differences from Townstead's own {@code TownsteadVillagerSnapshot}:
 *
 * <ul>
 *   <li>{@link #uuid()} is a real {@link UUID}. Townstead ships it as a string; quests key baselines
 *       and frozen targets on UUIDs, and parsing once at the boundary means no objective ever has to
 *       decide what to do with an unparseable one.</li>
 *   <li>Townstead's nested {@code age()} record is flattened away. Its six fields are already
 *       present at the top level of the same snapshot, and carrying both would give pack authors two
 *       spellings of one value ({@code age.lifeStage} and {@code lifeStage}) that could never
 *       disagree but would both have to be documented and supported forever.</li>
 * </ul>
 *
 * <p>Every field is a JDK type. Nothing Townstead-owned escapes this record.
 */
public record TownsteadVillagerView(
        UUID uuid,
        String name,
        String entityType,
        String rootId,
        String lifeStage,
        long biologicalAgeDays,
        int apparentAgeYears,
        boolean immortal,
        boolean ageless,
        boolean senior,
        String personalityId,
        String professionId,
        int professionLevel,
        int professionXp,
        float fertility,
        TownsteadScheduleView schedule,
        TownsteadNeedsView needs,
        Map<String, String> carriedVariants,
        List<String> expressedAlleles,
        Map<String, Float> heritage) {

    public TownsteadVillagerView {
        carriedVariants = Map.copyOf(carriedVariants);
        expressedAlleles = List.copyOf(expressedAlleles);
        heritage = Map.copyOf(heritage);
    }

    /** True when this villager has a Townstead profession at all (players and the jobless do not). */
    public boolean hasProfession() {
        return !professionId.isEmpty();
    }

    /** This villager's share of one ancestry, {@code 0f} when they have none of it. */
    public float heritageOf(String rootId) {
        return heritage.getOrDefault(rootId, 0f);
    }
}
