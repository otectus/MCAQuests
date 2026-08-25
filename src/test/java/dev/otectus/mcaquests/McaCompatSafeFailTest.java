package dev.otectus.mcaquests;

import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.mca.McaBinding;
import dev.otectus.mcaquests.compat.mca.McaHandles;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@link McaCompat} method must return its documented safe default rather than throw, on a
 * non-MCA entity, absent MCA data, or an MCA build this jar has never seen.
 *
 * <h2>Why this covers the whole surface, not a sample</h2>
 *
 * <p>MCA is deliberately absent from the unit-test runtime (see {@code build.gradle}'s
 * {@code testRuntimeClasspath} exclusion), so "MCA is not bound" is the real, exercised state here —
 * not an accident of the arguments. That makes this the direct regression test for the crash that
 * motivated the runtime binding layer: MCA renamed its package root, {@code McaCompat} still imported
 * the old one, and the first MCA reference threw {@code NoClassDefFoundError} out of a
 * {@code PlayerInteractEvent.EntityInteract} handler — killing a dedicated server the instant any
 * player right-clicked any entity. Ten methods, {@code isMcaVillager} among them, had no
 * {@code try/catch} at all at the time.
 *
 * <p>So the assertion this file really makes is the blunt one: <b>call everything, with nothing, and
 * nothing throws.</b> Passing {@code null} is a stand-in for "MCA gave us nothing"; the methods must
 * be equally unbothered by either.
 */
class McaCompatSafeFailTest {

    private static final UUID SOME_UUID = UUID.nameUUIDFromBytes("mcaquests-test".getBytes());

    // --- the binding itself ----------------------------------------------------------------------

    @Test
    void mcaIsGenuinelyAbsentFromTheTestRuntime() {
        assertEquals(McaBinding.Status.ABSENT, McaHandles.resolution().status(),
                "The unit suite must run with MCA absent, so these defaults are the real degradation "
                        + "path rather than an artefact of the arguments. If this fails, MCA has leaked "
                        + "back onto testRuntimeClasspath.");
        assertFalse(McaHandles.available());
        assertNotNull(McaBinding.describe(), "The debug report must work even with nothing bound.");
    }

    // --- identity / display ----------------------------------------------------------------------

    @Test
    void identityAndDisplayAccessorsDegradeQuietly() {
        assertFalse(McaCompat.isMcaVillager(null), "isMcaVillager");
        assertTrue(McaCompat.getProfessionId(null).isEmpty(), "professionId");
        assertEquals(net.minecraft.network.chat.Component.empty(), McaCompat.getProfessionName(null),
                "professionName");
        // No MCA age state to read, and null is not an AgeableMob, so the documented fallback is "adult".
        assertTrue(McaCompat.isAdult(null), "isAdult");
        assertTrue(McaCompat.getAgeStateName(null).isEmpty(), "ageState");
    }

    // --- hearts / movement (all no-ops without MCA) ----------------------------------------------

    @Test
    void heartsAndMovementAreNoOpsWithoutMca() {
        assertEquals(0, McaCompat.getHearts(null, null), "getHearts");
        McaCompat.addHearts(null, null, 5);
        McaCompat.awardHearts(null, SOME_UUID, null, 5);
        McaCompat.queueHeartsForLater(null, SOME_UUID, null, 5);
        McaCompat.setQuestGiverFollow(null, null, true);
        McaCompat.setQuestGiverFollow(null, null, false);
        McaCompat.leadVillagerTo(null, null, null, 0.6);
        McaCompat.stopVillagerLeading(null);
        McaCompat.holdVillagerInPlace(null);
        McaCompat.releaseVillagerHold(null);
        assertFalse(McaCompat.canPlayerInteract(null, null), "canPlayerInteract");
    }

    // --- relationship / family -------------------------------------------------------------------

    @Test
    void booleanDataPointsDefaultFalseOnMissingData() {
        assertFalse(McaCompat.isPlayerSpouse(null, null), "spouse");
        assertFalse(McaCompat.isFamilyOfPlayer(null, null, "any"), "family:any");
        assertFalse(McaCompat.isFamilyOfPlayer(null, null, "child"), "family:child");
        assertFalse(McaCompat.isFamilyOfPlayer(null, null, "parent"), "family:parent");
        assertFalse(McaCompat.isFamilyOfPlayer(null, null, "sibling"), "family:sibling");
        assertFalse(McaCompat.isFamilyOfPlayer(null, null, "grandparent"), "family:grandparent");
        assertFalse(McaCompat.hasHomeVillage(null), "homeVillage");
        assertFalse(McaCompat.hasHome(null), "home");
        assertFalse(McaCompat.relativesWithStatus(null, null, "child", "missing"), "relativeStatus");
        assertFalse(McaCompat.isPlayerMarried(null), "isPlayerMarried");
    }

    @Test
    void everyRelationAndStatusCombinationIsSafe() {
        for (String relation : new String[]{"any", "spouse", "parent", "child", "sibling", "grandparent"}) {
            assertTrue(McaCompat.giverRelativeUuids(null, null, relation).isEmpty(), "relatives:" + relation);
            assertTrue(McaCompat.findGiverRelative(null, null, relation).isEmpty(), "findRelative:" + relation);
            for (String status : new String[]{"alive", "nearby", "missing", "dead", "same_village"}) {
                assertFalse(McaCompat.relativesWithStatus(null, null, relation, status),
                        relation + "/" + status);
            }
        }
        assertFalse(McaCompat.hasMissingRelative(null, null), "hasMissingRelative");
    }

    @Test
    void optionalDataPointsDefaultEmptyOnMissingData() {
        assertTrue(McaCompat.getRelationshipState(null).isEmpty(), "relationshipState");
        assertTrue(McaCompat.getPersonalityName(null).isEmpty(), "personality");
        assertTrue(McaCompat.getMoodValue(null).isEmpty(), "moodValue");
        assertTrue(McaCompat.getMoodName(null).isEmpty(), "moodName");
        assertTrue(McaCompat.getHealthFraction(null).isEmpty(), "healthFraction");
        assertTrue(McaCompat.getFamilyRootId(null).isEmpty(), "familyRootId");
        assertTrue(McaCompat.getRelativeDisplayName(null, SOME_UUID).isEmpty(), "relativeDisplayName");
        assertTrue(McaCompat.getMcaPlayerName(null).isEmpty(), "mcaPlayerName");
    }

    @Test
    void infectionDefaultsToZeroOnMissingData() {
        assertEquals(0f, McaCompat.getInfectionProgress(null), "infectionProgress");
        assertFalse(McaCompat.isInfected(null), "isInfected");
    }

    // --- village -----------------------------------------------------------------------------------

    @Test
    void villageAccessorsDegradeQuietly() {
        assertTrue(McaCompat.getHomeVillageId(null).isEmpty(), "homeVillageId");
        assertTrue(McaCompat.getHomeVillageName(null).isEmpty(), "homeVillageName");
        assertTrue(McaCompat.getHomeVillageCenter(null).isEmpty(), "homeVillageCenter");
        assertTrue(McaCompat.villageName(null, 1).isEmpty(), "villageName");
        assertTrue(McaCompat.villageCenter(null, 1).isEmpty(), "villageCenter");
        assertTrue(McaCompat.findNearestVillageId(null, null, 128).isEmpty(), "findNearestVillageId");
        assertFalse(McaCompat.isWithinVillage(null, 1, null), "isWithinVillage");
        assertFalse(McaCompat.villageExists(null, 1), "villageExists");
        assertFalse(McaCompat.villageContains(null, 1, SOME_UUID), "villageContains");
        assertTrue(McaCompat.getVillageFoodCount(null, 1).isEmpty(), "villageFoodCount");
        assertTrue(McaCompat.loadedVillageResidents(null, 1).isEmpty(), "loadedVillageResidents");
        assertTrue(McaCompat.villageResidentUuids(null, 1).isEmpty(), "villageResidentUuids");
        assertFalse(McaCompat.isRaidActive(null, null), "isRaidActive");
        assertTrue(McaCompat.getRelativeHome(null, SOME_UUID).isEmpty(), "relativeHome");
        assertFalse(McaCompat.isVillageResidentAnywhere(null, SOME_UUID), "villageResidentAnywhere");
    }

    // --- anchors / spawning ------------------------------------------------------------------------

    @Test
    void anchorAndSpawnAccessorsDegradeQuietly() {
        assertTrue(McaCompat.getHomePos(null).isEmpty(), "homePos");
        assertTrue(McaCompat.getWorkstationPos(null).isEmpty(), "workstationPos");
        assertTrue(McaCompat.materializeRelative(null, SOME_UUID, null).isEmpty(), "materializeRelative");
    }

    // --- bounded proximity scans -------------------------------------------------------------------

    @Test
    void proximityScansDefaultEmptyOnMissingData() {
        assertTrue(McaCompat.maxHeartsWithin(null, 16.0).isEmpty(), "maxHeartsWithin");
        assertTrue(McaCompat.bestHeartsVillagerWithin(null, 16.0).isEmpty(), "bestHeartsVillager");
        assertTrue(McaCompat.nearestVillagerWithin(null, 16.0).isEmpty(), "nearestVillagerWithin");
        assertTrue(McaCompat.nearestAdultVillagerWithin(null, 16.0).isEmpty(), "nearestAdultVillager");
    }
}
