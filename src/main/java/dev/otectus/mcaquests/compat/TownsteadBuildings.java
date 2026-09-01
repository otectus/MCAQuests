package dev.otectus.mcaquests.compat;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The one place a building family name is normalised (spec §2.3, §5.4, risk register).
 *
 * <p>MCA registers buildings under type ids that Townstead contributes to, and the two do not always
 * agree on spelling: MCA: Quests' own bundled content has shipped {@code butcher_shop} since 1.4.0
 * while Townstead 0.7.6 contributes spirit points under {@code butcher}. A condition normalising one
 * way and an anchor the other would disagree about the same building, and the symptom — a quest that
 * says the village has no butcher while standing in front of one — would be almost impossible to
 * diagnose. So every alias resolves here and nowhere else.
 *
 * <p>Tier suffixes are <em>not</em> handled here: {@code dock_l2} is reduced to {@code dock} by
 * {@link TownsteadBuildingView#familyOf}, which owns tier parsing so the two can never drift.
 *
 * <p>{@link #families()} is the vocabulary the validator checks bundled content against. It is not a
 * runtime gate: a datapack may name a family this table has never heard of, because a third-party mod
 * may well have registered one. Only <em>bundled</em> definitions must stay inside it, since those
 * ship with the mod and cannot be fixed by the player.
 */
public final class TownsteadBuildings {

    /**
     * Alias to canonical family. Kept in declaration order so diagnostics list them predictably.
     *
     * <p>Both directions of the butcher pair are present deliberately: whichever spelling the loaded
     * mods use, content written against the other still matches.
     */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    /**
     * The families 0.7.6 demonstrably registers or contributes spirit for, plus the MCA building types
     * MCA: Quests' own content already anchors to. Used to catch a typo in bundled content at build
     * time, which is the only place a typo is unfixable by the person who hits it.
     */
    private static final Set<String> KNOWN = Set.of(
            "armorer", "armory", "bakery", "blacksmith", "bookkeeper", "butcher", "cartographer",
            "dock", "fishermans_hut", "fletcher", "graveyard", "infirmary", "inn", "leatherworker",
            "library", "mason", "music_store", "pen", "prison", "toolsmith", "weaponsmith",
            "weaving_mill", "wool_shed",
            // MCA's own registered building types, which Townstead adds no spirit for but which
            // content may still anchor at.
            "house", "farm", "tavern", "church", "storage");

    static {
        ALIASES.put("butcher_shop", "butcher");
        ALIASES.put("butchershop", "butcher");
        ALIASES.put("fisherman_hut", "fishermans_hut");
        ALIASES.put("fishermans_house", "fishermans_hut");
        ALIASES.put("smithy", "blacksmith");
        ALIASES.put("music_shop", "music_store");
        ALIASES.put("wool_storage", "wool_shed");
    }

    private TownsteadBuildings() {
    }

    /**
     * The canonical family for a raw building type or alias: lowercased, tier suffix removed, alias
     * resolved. An empty or unrecognised id passes through unchanged rather than becoming a guess.
     */
    public static String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String family = TownsteadBuildingView.familyOf(raw.trim().toLowerCase(Locale.ROOT));
        return ALIASES.getOrDefault(family, family);
    }

    /**
     * True when {@code a} and {@code b} name the same building family, whichever spelling and tier
     * each of them used.
     */
    public static boolean sameFamily(String a, String b) {
        String left = normalise(a);
        return !left.isEmpty() && left.equals(normalise(b));
    }

    /** True when this is a family the bundled content and validator recognise. */
    public static boolean isKnownFamily(String raw) {
        return KNOWN.contains(normalise(raw));
    }

    /** The recognised families, for a validation message that tells the author what to write. */
    public static Set<String> families() {
        return KNOWN;
    }

    /**
     * A human-facing name for a family, as a plain string for a translated line's argument. Underscores
     * become spaces; nothing else is invented, because a building family is a datapack-owned id and
     * guessing at a prettier name would misreport a third-party mod's building.
     */
    public static String displayName(String raw) {
        return normalise(raw).replace('_', ' ');
    }
}
