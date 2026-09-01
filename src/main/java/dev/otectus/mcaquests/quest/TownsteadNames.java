package dev.otectus.mcaquests.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.otectus.mcaquests.compat.TownsteadBuildings;
import dev.otectus.mcaquests.compat.TownsteadQuery;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * Turns Townstead's internal ids into sentences a player can read.
 *
 * <p>This exists because they were not. Every Townstead objective and condition built its card text by
 * dropping a raw id straight into a translated sentence, so a quest that asks a villager to stay at
 * work rendered as <b>"Keep villager.schedule.currentActivity eq work for 30 seconds"</b> — a query
 * path, an operator id and an enum constant, in a line meant for someone playing the game. The rest of
 * the mod already had {@link DisplayNames} for exactly this; the Townstead surface simply never used it.
 *
 * <p>The failure had a second half worth naming: because the raw path is long, the sentence overflowed
 * the offer card and was cut off mid-word. Shortening it is not cosmetic — it is what makes the last
 * objective of a quest visible at all.
 *
 * <p><b>Curated first, humanised second.</b> Everything here goes through
 * {@link Component#translatableWithFallback}, so a curated lang entry wins, a resource pack can
 * override it, and an id nobody has curated — a spirit from a third-party pack, a building family this
 * mod has never heard of — still reads as words rather than as an id. Nothing is ever shown raw.
 *
 * <p>Lookups happen on the client, because these are built server-side into {@link Component}s and the
 * translation is resolved when they are drawn. That is what lets a Portuguese client read Portuguese
 * from an English server.
 */
public final class TownsteadNames {

    private static final String VALUE = "mcaquests.townstead.value.";
    private static final String PREDICATE = "mcaquests.townstead.predicate.";
    private static final String ACTIVITY = "mcaquests.townstead.activity.";
    private static final String SPIRIT = "mcaquests.townstead.spirit.";
    private static final String BUILDING = "mcaquests.townstead.building.";
    private static final String SEASON = "mcaquests.townstead.season.";
    private static final String LIFE_STAGE = "mcaquests.townstead.life_stage.";
    private static final String CLASSIFICATION = "mcaquests.townstead.classification.";
    private static final String OPERATOR = "mcaquests.townstead.operator.";
    /** The same operators with a copula folded in, for a condition's statement form. */
    private static final String OPERATOR_IS = "mcaquests.townstead.operator.is.";

    /**
     * Which vocabulary a path's <em>compared value</em> belongs to.
     *
     * <p>Without this, "requires the season at winter" ships an untranslated id in the one position
     * everybody looks at. The subject was being named and the value was not, which is the same bug one
     * argument to the right.
     */
    private static final java.util.Map<String, String> VALUE_VOCABULARY = java.util.Map.of(
            "schedule.currentactivity", ACTIVITY,
            "schedule.plannedactivity", ACTIVITY,
            "season", SEASON,
            "lifestage", LIFE_STAGE,
            "classification", CLASSIFICATION,
            "primaryid", SPIRIT,
            "secondaryid", SPIRIT,
            "type", BUILDING);

    /**
     * Paths whose {@code eq} comparison reads better as a bare phrase than as a comparison. "Keep them
     * working" is a sentence; "keep their current activity at working" is a translation of a database
     * row.
     */
    private static final List<String> ACTIVITY_PATHS =
            List.of("schedule.currentactivity", "schedule.plannedactivity");

    /**
     * Boolean paths that have a curated predicate phrasing, and its negative, in the shipped locale.
     *
     * <p>Listed rather than discovered. These Components are built on the server and resolved on the
     * client, so asking "does this key have a translation" here asks the <em>server's</em> language —
     * and a dedicated server has only vanilla's, because mod lang files are client assets. The answer
     * would have been no on every multiplayer world and yes in single-player, which is the worst shape
     * a bug can have. {@code TownsteadNamesTest} keeps this list and the locale in step.
     */
    private static final List<String> PREDICATE_PATHS = List.of(
            "villager.schedule.onschedule",
            "villager.needs.collapsed",
            "villager.needs.gated",
            "villager.senior",
            "villager.ageless",
            "villager.immortal");

    private TownsteadNames() {
    }

    // --- vocabularies -------------------------------------------------------------------------

    /** A shift activity as a verb phrase: {@code work} becomes "working". */
    public static Component activity(String id) {
        String key = normalise(id);
        return Component.translatableWithFallback(ACTIVITY + key, DisplayNames.humanize(key));
    }

    /** A village spirit: {@code nautical} becomes "Nautical". */
    public static Component spirit(String id) {
        String key = normalise(id);
        return Component.translatableWithFallback(SPIRIT + key, DisplayNames.humanize(key));
    }

    /**
     * A building family, normalised first so {@code butcher_shop} and {@code butcher} — and every tier
     * of {@code dock} — resolve to one name rather than three near-duplicates.
     */
    public static Component building(String family) {
        String key = normalise(TownsteadBuildings.normalise(family));
        return Component.translatableWithFallback(BUILDING + key, DisplayNames.humanize(key));
    }

    /**
     * A profession, through the same helper the rest of the mod's objectives already use, so
     * "talk to a farmer" and "reach tier 3 as a farmer" name the trade identically.
     */
    public static Component profession(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed != null ? DisplayNames.name(parsed)
                : Component.literal(DisplayNames.humanize(normalise(id)));
    }

    /** A learned skill id. */
    public static Component skill(ResourceLocation id) {
        return DisplayNames.name(id);
    }

    /**
     * The thing a query is about, as a noun phrase: {@code needs.hunger} becomes "their hunger",
     * {@code classification} on the spirit source becomes "the village's character".
     *
     * <p>The fallback humanises the <em>last</em> segment only. A curated entry is always better, but
     * "Hunger Exhaustion" beats "villager.needs.hungerExhaustion" by a wide margin, and it is what an
     * uncurated third-party path will show.
     */
    public static Component subject(TownsteadQuery query) {
        List<String> path = query.path();
        String joined = normalise(String.join(".", path));
        String last = path.isEmpty() ? "" : path.get(path.size() - 1);
        return Component.translatableWithFallback(
                VALUE + query.source().id() + '.' + joined,
                DisplayNames.humanize(splitCamelCase(last)));
    }

    /**
     * A comparison word without a copula: {@code gte} becomes "at least".
     *
     * <p>Used where the sentence already supplies the verb -- "Keep their hunger at least 55 for a
     * minute". See {@link #operatorIs} for the other half of that split.
     */
    public static Component operator(TownsteadQuery.Operator operator) {
        return Component.translatableWithFallback(OPERATOR + operator.id(), operator.id());
    }

    /**
     * The same comparison with a copula: {@code gte} becomes "is at least", {@code eq} just "is".
     *
     * <p>An objective and a condition are different sentence shapes. "Keep their hunger is at least
     * 55" and "Requires their hunger at least 55" are each wrong in the other's frame, and trying to
     * serve both from one word is what produced "Requires the season at winter".
     */
    public static Component operatorIs(TownsteadQuery.Operator operator) {
        return Component.translatableWithFallback(OPERATOR_IS + operator.id(), operator.id());
    }

    // --- composed clauses ---------------------------------------------------------------------

    /**
     * The clause a "hold this true" objective reads with: "them working", or "their hunger at least 60".
     *
     * <p>Two shapes rather than one because the generic comparison, applied to an activity, produces
     * the clumsiest sentence in the mod. An activity equality gets the natural reading; everything else
     * composes subject, operator and value.
     */
    public static Component clause(TownsteadQuery query) {
        return clause(query, false);
    }

    /**
     * The same reading as a standalone statement, for a condition: "their hunger is at least 55".
     */
    public static Component statement(TownsteadQuery query) {
        return clause(query, true);
    }

    private static Component clause(TownsteadQuery query, boolean withCopula) {
        if (isActivityEquality(query)) {
            return Component.translatable("mcaquests.townstead.clause.doing",
                    activity(rawValue(query)));
        }
        Component predicate = predicate(query);
        if (predicate != null) {
            return predicate;
        }
        if (query.operator() == TownsteadQuery.Operator.EXISTS) {
            return Component.translatable("mcaquests.townstead.clause.exists", subject(query));
        }
        return Component.translatable("mcaquests.townstead.clause.compare", subject(query),
                withCopula ? operatorIs(query.operator()) : operator(query.operator()), value(query));
    }

    /**
     * A boolean comparison read as a predicate rather than as a comparison: "them on schedule", not
     * "whether they are on schedule at yes".
     *
     * <p>Null when the path has no curated predicate or the comparison is not a boolean equality, in
     * which case the generic composition handles it. There is no humanised fallback here on purpose:
     * inventing a predicate for an uncurated path would produce worse English than the noun form
     * already gives.
     */
    @javax.annotation.Nullable
    private static Component predicate(TownsteadQuery query) {
        boolean equality = query.operator() == TownsteadQuery.Operator.EQ
                || query.operator() == TownsteadQuery.Operator.NE;
        String raw = rawValue(query);
        if (!equality || !(raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false"))) {
            return null;
        }
        String path = query.source().id() + '.' + normalise(String.join(".", query.path()));
        if (!PREDICATE_PATHS.contains(path)) {
            return null; // no curated phrasing; the generic comparison handles it
        }
        // Both the value and the operator can negate, and two negations are a positive. The opposite
        // of "them collapsed" is "them on their feet", so each path ships its own negative rather than
        // having "not" bolted on the front.
        boolean positive = raw.equalsIgnoreCase("true") == (query.operator() == TownsteadQuery.Operator.EQ);
        return Component.translatable(PREDICATE + path + (positive ? "" : ".not"));
    }

    /**
     * The value side of a comparison, rendered through whichever vocabulary fits the path — so an
     * activity comparison says "working" rather than "work", and a boolean says yes or no rather than
     * printing a Java literal.
     */
    public static Component value(TownsteadQuery query) {
        String raw = rawValue(query);
        if (raw.isEmpty()) {
            return Component.empty();
        }
        String vocabulary = VALUE_VOCABULARY.get(normalise(String.join(".", query.path())));
        if (vocabulary != null) {
            String id = normalise(vocabulary.equals(BUILDING) ? TownsteadBuildings.normalise(raw) : raw);
            return Component.translatableWithFallback(vocabulary + id, DisplayNames.humanize(id));
        }
        if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) {
            return Component.translatable("mcaquests.townstead.value.boolean."
                    + raw.toLowerCase(Locale.ROOT));
        }
        if (ResourceLocation.tryParse(raw) != null && raw.indexOf(':') > 0) {
            // A namespaced id compared as a literal -- a profession, a root, a template. Naming it is
            // always better than printing "minecraft:farmer" in the middle of a sentence.
            return DisplayNames.name(ResourceLocation.tryParse(raw));
        }
        return Component.literal(raw);
    }

    private static boolean isActivityEquality(TownsteadQuery query) {
        return query.operator() == TownsteadQuery.Operator.EQ && isActivityPath(query)
                && !rawValue(query).isEmpty();
    }

    private static boolean isActivityPath(TownsteadQuery query) {
        return ACTIVITY_PATHS.contains(normalise(String.join(".", query.path())));
    }

    /** The literal a query compares against, unquoted; empty when it compares against nothing. */
    private static String rawValue(TownsteadQuery query) {
        JsonElement element = query.value().orElse(null);
        if (element == null) {
            return "";
        }
        return element instanceof JsonPrimitive primitive ? primitive.getAsString() : element.toString();
    }

    private static String normalise(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * {@code hungerExhaustion} becomes {@code hunger_exhaustion}, so the humanised fallback reads as
     * two words. Townstead's snapshot accessors are camel-case and the lang-key convention is not.
     */
    private static String splitCamelCase(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 4);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                out.append('_');
            }
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }
}
