package dev.otectus.mcaquests.compat.iceandfire;

import dev.otectus.mcaquests.compat.CapabilityEvidence;
import dev.otectus.mcaquests.compat.CompatCapability;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the capability probe says about installations we can describe but not install.
 *
 * <p>Every case here is a real configuration somebody runs — Community Edition with and without the
 * seekers, the original mod with its Myrmex hive, a hypothetical future CE that adds the hive back —
 * and the point of each is the same: the answer comes from the registry contents handed in, never
 * from the flavour. The two exceptions ({@code brush_scales}, {@code dragon_forge_blood}) are the
 * only ones that read the flavour, and they say so through {@link CapabilityEvidence}.
 */
class IceAndFireCapabilitiesTest {

    private static final Predicate<ResourceLocation> NOTHING = id -> false;

    @SafeVarargs
    private static Predicate<ResourceLocation> registry(List<ResourceLocation>... groups) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (List<ResourceLocation> group : groups) {
            ids.addAll(group);
        }
        return ids::contains;
    }

    private static boolean present(Map<String, CompatCapability> caps, String id) {
        return caps.get(id).present();
    }

    /** A Community Edition entity registry: everything quest-safe, no Myrmex. */
    private static Predicate<ResourceLocation> ceEntities() {
        return registry(IceAndFireRegistryManifest.QUEST_SAFE_ENTITIES);
    }

    @Test
    void communityEditionWithEveryCoreIdReportsCoreAndEachDragon() {
        Map<String, CompatCapability> caps = IceAndFireCapabilities.probe(
                IceAndFireFlavor.COMMUNITY_EDITION, true, ceEntities(), NOTHING, null);

        assertTrue(present(caps, IceAndFireCapabilities.CORE));
        assertTrue(present(caps, IceAndFireCapabilities.FIRE_DRAGON));
        assertTrue(present(caps, IceAndFireCapabilities.ICE_DRAGON));
        assertTrue(present(caps, IceAndFireCapabilities.LIGHTNING_DRAGON));
        assertTrue(present(caps, IceAndFireCapabilities.DREAD_MOBS));
    }

    @Test
    void communityEditionWithSeekersReportsThem() {
        Map<String, CompatCapability> caps = IceAndFireCapabilities.probe(
                IceAndFireFlavor.COMMUNITY_EDITION, true, ceEntities(),
                registry(IceAndFireRegistryManifest.DRAGON_SEEKERS), null);

        assertTrue(present(caps, IceAndFireCapabilities.DRAGON_SEEKERS));
    }

    @Test
    void communityEditionWithoutSeekersReportsThemAbsentAndDoesNotThrow() {
        Map<String, CompatCapability> caps = IceAndFireCapabilities.probe(
                IceAndFireFlavor.COMMUNITY_EDITION, true, ceEntities(), NOTHING, null);

        assertFalse(present(caps, IceAndFireCapabilities.DRAGON_SEEKERS));
        assertTrue(caps.get(IceAndFireCapabilities.DRAGON_SEEKERS).detail().contains("dragon_seeker"),
                "a missing group names what is missing, so the status command can say which");
    }

    @Test
    void communityEditionHasNoMyrmex() {
        Map<String, CompatCapability> caps = IceAndFireCapabilities.probe(
                IceAndFireFlavor.COMMUNITY_EDITION, true, ceEntities(), NOTHING, null);

        assertFalse(present(caps, IceAndFireCapabilities.MYRMEX));
    }

    /**
     * The case the whole design exists for: a Community Edition build that re-adds the hive must
     * report it available, because the registry says so and the flavour is not consulted.
     */
    @Test
    void futureCommunityEditionWithMyrmexIdsReportsMyrmex() {
        Map<String, CompatCapability> caps = IceAndFireCapabilities.probe(
                IceAndFireFlavor.COMMUNITY_EDITION, true,
                registry(IceAndFireRegistryManifest.QUEST_SAFE_ENTITIES,
                        IceAndFireRegistryManifest.MYRMEX_ENTITIES),
                NOTHING, null);

        assertTrue(present(caps, IceAndFireCapabilities.MYRMEX));
        assertEquals(CapabilityEvidence.REGISTRY_CONFIRMED,
                caps.get(IceAndFireCapabilities.MYRMEX).evidence());
    }

    @Test
    void aMissingLightningDragonFailsOnlyItsOwnCapability() {
        ResourceLocation lightning = new ResourceLocation("iceandfire", "lightning_dragon");
        Predicate<ResourceLocation> entities = ceEntities();
        Map<String, CompatCapability> caps = IceAndFireCapabilities.probe(
                IceAndFireFlavor.COMMUNITY_EDITION, true,
                id -> entities.test(id) && !id.equals(lightning), NOTHING, null);

        assertFalse(present(caps, IceAndFireCapabilities.LIGHTNING_DRAGON));
        assertTrue(present(caps, IceAndFireCapabilities.FIRE_DRAGON));
        assertTrue(present(caps, IceAndFireCapabilities.ICE_DRAGON));
        assertTrue(present(caps, IceAndFireCapabilities.CORE),
                "core needs any dragon, not all three");
        assertTrue(present(caps, IceAndFireCapabilities.DREAD_MOBS));
    }

    @Test
    void anAbsentModHasNoCore() {
        Map<String, CompatCapability> caps = IceAndFireCapabilities.probe(
                IceAndFireFlavor.NONE, false, NOTHING, NOTHING, null);

        assertFalse(present(caps, IceAndFireCapabilities.CORE));
        assertFalse(present(caps, IceAndFireCapabilities.BRUSH_SCALES));
    }

    @Test
    void theTwoFlavourDeclaredCapabilitiesFollowCommunityEditionAndSaySo() {
        Map<String, CompatCapability> ce = IceAndFireCapabilities.probe(
                IceAndFireFlavor.COMMUNITY_EDITION, true, ceEntities(), NOTHING, null);
        Map<String, CompatCapability> original = IceAndFireCapabilities.probe(
                IceAndFireFlavor.ORIGINAL, true, ceEntities(), NOTHING, null);

        assertTrue(present(ce, IceAndFireCapabilities.BRUSH_SCALES));
        assertTrue(present(ce, IceAndFireCapabilities.DRAGON_FORGE_BLOOD));
        assertFalse(present(original, IceAndFireCapabilities.BRUSH_SCALES));
        assertFalse(present(original, IceAndFireCapabilities.DRAGON_FORGE_BLOOD));
        assertEquals(CapabilityEvidence.FLAVOR_DECLARED,
                ce.get(IceAndFireCapabilities.BRUSH_SCALES).evidence());
    }

    @Test
    void noTechnicalEntityIsQuestSafe() {
        List<ResourceLocation> overlap = new ArrayList<>(IceAndFireRegistryManifest.QUEST_SAFE_ENTITIES);
        overlap.retainAll(IceAndFireRegistryManifest.EXCLUDED_TECHNICAL);

        assertTrue(overlap.isEmpty(), "quest-safe entities must never include a multipart, projectile "
                + "or egg: an objective aimed at one can never complete. Offenders: " + overlap);
    }

    /**
     * Structures live in a dynamic registry. With no {@code RegistryAccess} the honest answer is "not
     * answerable", reported as absent with a detail that says why, so nobody reads it as "this world
     * has no gorgon temples".
     */
    @Test
    void structuresAreAbsentWithAReasonWhenRegistryAccessIsUnavailable() {
        Map<String, CompatCapability> caps = IceAndFireCapabilities.probe(
                IceAndFireFlavor.COMMUNITY_EDITION, true, ceEntities(), NOTHING, null);

        assertFalse(present(caps, IceAndFireCapabilities.STRUCTURES));
        assertEquals(IceAndFireCapabilities.NO_REGISTRY_ACCESS,
                caps.get(IceAndFireCapabilities.STRUCTURES).detail());
    }

    @Test
    void structuresArePresentWhenTheDynamicRegistryHasAllThree() {
        Map<String, CompatCapability> caps = IceAndFireCapabilities.probe(
                IceAndFireFlavor.COMMUNITY_EDITION, true, ceEntities(), NOTHING,
                registry(IceAndFireRegistryManifest.STRUCTURES));

        assertTrue(present(caps, IceAndFireCapabilities.STRUCTURES));
    }
}
