package dev.otectus.mcaquests.compat.iceandfire;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shape of {@link IceAndFireRegistryManifest}: every id well-formed, the two groups a
 * quest may name disjoint, and the Community Edition roster exactly what was read out of the 1.2.7
 * jar.
 *
 * <p>The snapshot below is written out longhand rather than derived from the manifest, which would
 * make it tautological. Its job is to make an edit to the manifest a deliberate act: adding an id
 * here means having checked a real jar for it, which is what {@code IceAndFireJarProbeTest} does
 * against the jar itself when one is supplied.
 */
class IceAndFireManifestTest {

    /**
     * Ice &amp; Fire: Community Edition 1.2.7 for 1.20.1 — the ids MCA: Quests expects to find, as
     * entity paths, item paths (seekers and hippogryph armor) and nothing else. Netherite dragon armor
     * is deliberately absent: those four ids are generated from a material enum, so no string in the
     * jar can confirm them and only the runtime registry can.
     */
    private static final List<String> CE_SNAPSHOT = List.of(
            // quest-safe entities
            "fire_dragon", "ice_dragon", "lightning_dragon", "hippogryph", "amphithere", "siren",
            "hippocampus", "sea_serpent", "gorgon", "cyclops", "deathworm", "cockatrice",
            "stymphalian_bird", "troll", "hydra", "ghost", "pixie", "dread_thrall", "dread_ghoul",
            "dread_beast", "dread_scuttler", "dread_lich", "dread_knight", "dread_horse",
            // technical entities a quest must never name
            "stone_statue", "dragon_multipart", "multipart", "hydra_multipart", "cylcops_multipart",
            "dragon_egg", "dragon_arrow", "dragon_skull", "fire_dragon_charge", "ice_dragon_charge",
            "lightning_dragon_charge", "hippogryph_egg", "deathworm_egg", "cockatrice_egg",
            "stymphalian_feather", "stymphalian_arrow", "amphithere_arrow", "sea_serpent_bubbles",
            "sea_serpent_arrow", "chain_tie", "pixie_charge", "tide_trident", "mob_skull",
            "dread_lich_skull", "hydra_breath", "hydra_arrow", "ghost_sword",
            // Community Edition items
            "dragon_seeker", "epic_dragon_seeker", "legendary_dragon_seeker",
            "netherite_hippogryph_armor");

    @Test
    void everyIdIsWellFormedAndInTheIceAndFireNamespace() {
        for (ResourceLocation id : allIds()) {
            assertEquals(IceAndFireRegistryManifest.MOD_ID, id.getNamespace(), id + " is in the wrong namespace");
            assertEquals(id, ResourceLocation.tryParse(id.toString()),
                    id + " does not round-trip through ResourceLocation.tryParse");
        }
    }

    @Test
    void questSafeAndTechnicalEntitiesAreDisjoint() {
        List<ResourceLocation> overlap = new ArrayList<>(IceAndFireRegistryManifest.QUEST_SAFE_ENTITIES);
        overlap.retainAll(IceAndFireRegistryManifest.EXCLUDED_TECHNICAL);

        assertTrue(overlap.isEmpty(), "an id cannot be both quest-safe and technical: " + overlap);
    }

    /**
     * Myrmex are quest-able content in the original mod, but they are never in the quest-safe list:
     * that list is what a shipped quest may name unconditionally, and the hive is capability-gated.
     */
    @Test
    void myrmexAreNotInTheUnconditionalQuestSafeList() {
        List<ResourceLocation> overlap = new ArrayList<>(IceAndFireRegistryManifest.MYRMEX_ENTITIES);
        overlap.retainAll(IceAndFireRegistryManifest.QUEST_SAFE_ENTITIES);

        assertTrue(overlap.isEmpty(), "Myrmex must stay behind the myrmex capability: " + overlap);
    }

    @Test
    void dreadMobsAreAllQuestSafe() {
        assertTrue(IceAndFireRegistryManifest.QUEST_SAFE_ENTITIES
                        .containsAll(IceAndFireRegistryManifest.DREAD_MOBS),
                "the dread hunt names its mobs directly, so each must be quest-safe");
    }

    @Test
    void theGodlySeekerIsNeverRequired() {
        assertTrue(!IceAndFireRegistryManifest.DRAGON_SEEKERS
                        .contains(IceAndFireRegistryManifest.GODLY_DRAGON_SEEKER),
                "no shipped quest may require the godly seeker");
    }

    @Test
    void theCommunityEditionSnapshotMatchesTheManifest() {
        Set<String> expected = new TreeSet<>(CE_SNAPSHOT);
        Set<String> actual = new TreeSet<>();
        for (ResourceLocation id : ceGroups()) {
            actual.add(id.getPath());
        }

        assertEquals(expected, actual);
    }

    private static Set<ResourceLocation> ceGroups() {
        Set<ResourceLocation> ids = new LinkedHashSet<>(IceAndFireRegistryManifest.QUEST_SAFE_ENTITIES);
        ids.addAll(IceAndFireRegistryManifest.EXCLUDED_TECHNICAL);
        ids.addAll(IceAndFireRegistryManifest.DRAGON_SEEKERS);
        ids.add(IceAndFireRegistryManifest.NETHERITE_HIPPOGRYPH_ARMOR);
        return ids;
    }

    private static Set<ResourceLocation> allIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>(ceGroups());
        ids.addAll(IceAndFireRegistryManifest.DRAGONS);
        ids.addAll(IceAndFireRegistryManifest.MYRMEX_ENTITIES);
        ids.addAll(IceAndFireRegistryManifest.DREAD_MOBS);
        ids.addAll(IceAndFireRegistryManifest.NETHERITE_DRAGON_ARMOR);
        ids.addAll(IceAndFireRegistryManifest.STRUCTURES);
        ids.add(IceAndFireRegistryManifest.GODLY_DRAGON_SEEKER);
        return ids;
    }
}
