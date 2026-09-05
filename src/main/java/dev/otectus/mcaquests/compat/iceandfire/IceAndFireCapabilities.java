package dev.otectus.mcaquests.compat.iceandfire;

import dev.otectus.mcaquests.compat.CapabilityEvidence;
import dev.otectus.mcaquests.compat.CompatCapability;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Turns "what does this installation of Ice &amp; Fire actually contain?" into a fixed set of
 * booleans, by asking the registries rather than the mod.
 *
 * <p>Everything except the two Community Edition mechanics is decided by a registry lookup, because
 * that is the only thing that survives a fork: CE dropped the Myrmex and the original has no Dragon
 * Seekers, but either could change in a point release, and a probe that trusted
 * {@link IceAndFireFlavor} would keep offering a quest for content that is no longer there. The
 * flavour is used only where no registry entry exists to point at.
 *
 * <p>{@link #probe} is pure: it takes predicates rather than reaching for {@code ForgeRegistries}
 * itself, so the whole capability table can be unit-tested — including installations nobody has yet,
 * such as a future CE that re-adds the Myrmex.
 */
public final class IceAndFireCapabilities {

    /** The mod is installed and at least one dragon is registered. Everything else assumes this. */
    public static final String CORE = "iceandfire.core";

    public static final String FIRE_DRAGON = "iceandfire.fire_dragon";
    public static final String ICE_DRAGON = "iceandfire.ice_dragon";
    public static final String LIGHTNING_DRAGON = "iceandfire.lightning_dragon";

    /** The Myrmex hive, absent from Community Edition. Worker or queen is enough to say it is here. */
    public static final String MYRMEX = "iceandfire.myrmex";

    /** All seven Dread mobs. A dread quest names several, so a partial hive is no use. */
    public static final String DREAD_MOBS = "iceandfire.dread_mobs";

    /** The three obtainable Dragon Seekers. The godly one is deliberately not required. */
    public static final String DRAGON_SEEKERS = "iceandfire.dragon_seekers";

    /** All four Netherite dragon armor pieces. */
    public static final String NETHERITE_DRAGON_ARMOR = "iceandfire.netherite_dragon_armor";

    public static final String NETHERITE_HIPPOGRYPH_ARMOR = "iceandfire.netherite_hippogryph_armor";

    /** The three named structures. Only answerable while a {@code RegistryAccess} exists. */
    public static final String STRUCTURES = "iceandfire.structures";

    /** Brushing scales off a tamed dragon. A Community Edition mechanic with no registry entry. */
    public static final String BRUSH_SCALES = "iceandfire.brush_scales";

    /** Blood in the dragon forge. A Community Edition mechanic with no registry entry. */
    public static final String DRAGON_FORGE_BLOOD = "iceandfire.dragon_forge_blood";

    /** Said instead of a missing-id list when the dynamic registries were not available. */
    static final String NO_REGISTRY_ACCESS = "registry access unavailable at this probe";

    private IceAndFireCapabilities() {
    }

    /**
     * The full capability table for one installation.
     *
     * <p>{@code structureExists} is {@code null} whenever the caller had no
     * {@link net.minecraft.core.RegistryAccess} — structures live in a dynamic registry, so outside a
     * loaded world there is nothing to ask. That case reports the capability absent with a detail
     * saying so, rather than guessing, so nobody reads "no structures" as "this world has none".
     *
     * @return the capabilities in a stable order, keyed by id
     */
    public static Map<String, CompatCapability> probe(IceAndFireFlavor flavor,
                                                      boolean modLoaded,
                                                      Predicate<ResourceLocation> entityExists,
                                                      Predicate<ResourceLocation> itemExists,
                                                      @Nullable Predicate<ResourceLocation> structureExists) {
        Map<String, CompatCapability> out = new LinkedHashMap<>();

        List<ResourceLocation> missingDragons = missing(IceAndFireRegistryManifest.DRAGONS, entityExists);
        boolean anyDragon = missingDragons.size() < IceAndFireRegistryManifest.DRAGONS.size();
        put(out, CORE, modLoaded && anyDragon, CapabilityEvidence.REGISTRY_CONFIRMED,
                modLoaded ? describe(missingDragons) : "iceandfire is not loaded");

        put(out, FIRE_DRAGON, entityExists.test(entity("fire_dragon")),
                CapabilityEvidence.REGISTRY_CONFIRMED, "");
        put(out, ICE_DRAGON, entityExists.test(entity("ice_dragon")),
                CapabilityEvidence.REGISTRY_CONFIRMED, "");
        put(out, LIGHTNING_DRAGON, entityExists.test(entity("lightning_dragon")),
                CapabilityEvidence.REGISTRY_CONFIRMED, "");

        // Worker OR queen: those two are what a hive quest can rely on reaching, and demanding the
        // whole caste list would report absent on an install that had merely renamed a swarmer.
        boolean myrmex = entityExists.test(entity("myrmex_worker"))
                || entityExists.test(entity("myrmex_queen"));
        put(out, MYRMEX, myrmex, CapabilityEvidence.REGISTRY_CONFIRMED,
                myrmex ? "" : describe(IceAndFireRegistryManifest.MYRMEX_ENTITIES));

        List<ResourceLocation> missingDread = missing(IceAndFireRegistryManifest.DREAD_MOBS, entityExists);
        put(out, DREAD_MOBS, missingDread.isEmpty(), CapabilityEvidence.REGISTRY_CONFIRMED,
                describe(missingDread));

        List<ResourceLocation> missingSeekers =
                missing(IceAndFireRegistryManifest.DRAGON_SEEKERS, itemExists);
        put(out, DRAGON_SEEKERS, missingSeekers.isEmpty(), CapabilityEvidence.REGISTRY_CONFIRMED,
                describe(missingSeekers));

        List<ResourceLocation> missingArmor =
                missing(IceAndFireRegistryManifest.NETHERITE_DRAGON_ARMOR, itemExists);
        put(out, NETHERITE_DRAGON_ARMOR, missingArmor.isEmpty(), CapabilityEvidence.REGISTRY_CONFIRMED,
                describe(missingArmor));

        boolean hippogryphArmor = itemExists.test(IceAndFireRegistryManifest.NETHERITE_HIPPOGRYPH_ARMOR);
        put(out, NETHERITE_HIPPOGRYPH_ARMOR, hippogryphArmor, CapabilityEvidence.REGISTRY_CONFIRMED,
                hippogryphArmor ? "" : "missing: " + IceAndFireRegistryManifest.NETHERITE_HIPPOGRYPH_ARMOR);

        if (structureExists == null) {
            put(out, STRUCTURES, false, CapabilityEvidence.REGISTRY_CONFIRMED, NO_REGISTRY_ACCESS);
        } else {
            List<ResourceLocation> missingStructures =
                    missing(IceAndFireRegistryManifest.STRUCTURES, structureExists);
            put(out, STRUCTURES, missingStructures.isEmpty(), CapabilityEvidence.REGISTRY_CONFIRMED,
                    describe(missingStructures));
        }

        // The two below have no registry entry to point at, so the flavour is the only witness there
        // is — reported as such, and never used to decide anything a registry could have answered.
        boolean communityEdition = flavor == IceAndFireFlavor.COMMUNITY_EDITION;
        put(out, BRUSH_SCALES, communityEdition, CapabilityEvidence.FLAVOR_DECLARED, "");
        put(out, DRAGON_FORGE_BLOOD, communityEdition, CapabilityEvidence.FLAVOR_DECLARED, "");

        return Collections.unmodifiableMap(out);
    }

    private static void put(Map<String, CompatCapability> out, String id, boolean present,
                            CapabilityEvidence evidence, String detail) {
        out.put(id, new CompatCapability(id, present, evidence, detail));
    }

    private static List<ResourceLocation> missing(List<ResourceLocation> ids,
                                                  Predicate<ResourceLocation> exists) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation id : ids) {
            if (!exists.test(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private static String describe(List<ResourceLocation> missing) {
        if (missing.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("missing: ");
        for (int i = 0; i < missing.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(missing.get(i));
        }
        return sb.toString();
    }

    private static ResourceLocation entity(String path) {
        return new ResourceLocation(IceAndFireRegistryManifest.MOD_ID, path);
    }
}
