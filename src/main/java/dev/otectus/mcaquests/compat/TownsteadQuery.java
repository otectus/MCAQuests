package dev.otectus.mcaquests.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.data.StrictCodecs;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * One comparison against a Townstead value (Townstead spec §4.2) — the shared contract behind the
 * {@code townstead_value} condition and the state and delta objectives.
 *
 * <p>This is MCA: Quests' own query language, not a pass-through to Townstead's. Paths resolve
 * against our view records through {@link TownsteadPaths}, so a change inside Townstead can never
 * silently alter what a datapack means.
 *
 * <p><b>Queries never mutate.</b> There is no write path here and no operator that could become one.
 *
 * <pre>{@code
 * {
 *   "source": "villager",
 *   "target": "giver",
 *   "path": "needs.hunger",
 *   "operator": "lte",
 *   "value": 30
 * }
 * }</pre>
 *
 * @param missing what to answer when the source, the target, the path or the capability is absent.
 *                Defaults to {@code false}, so an uninstalled feature makes content ineligible rather
 *                than accidentally satisfied.
 */
public record TownsteadQuery(Source source, TownsteadTarget target, List<String> path,
                             Operator operator, Optional<JsonElement> value, boolean missing) {

    /** The maximum characters in a raw path, and the maximum segments it may split into (§4.2). */
    private static final int MAX_PATH_LENGTH = 128;
    private static final int MAX_PATH_DEPTH = 8;
    private static final int MAX_PATTERN_LENGTH = 256;

    /**
     * Compiled {@code matches} patterns, keyed by their source text. Every pattern in here was already
     * compiled once by the codec, so this is a reuse cache rather than a lazy-init: it is bounded by
     * the number of distinct regexes across all loaded datapacks, which is tiny.
     */
    private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

    /** Which Townstead snapshot the path is rooted in. */
    public enum Source {
        VILLAGER("villager"),
        CALENDAR("calendar"),
        BUILDING("building"),
        SPIRIT("spirit"),
        ROOT("root"),
        /** The first path segment is the gene id; the rest walks that gene's definition. */
        GENE("gene");

        private static final Map<String, Source> BY_NAME = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(Source::id, Function.identity()));

        static final Codec<Source> CODEC = Codec.STRING.flatXmap(
                raw -> {
                    Source source = BY_NAME.get(raw.toLowerCase(Locale.ROOT));
                    return source != null ? DataResult.success(source) : DataResult.error(
                            () -> "Unknown Townstead source '" + raw + "'; expected one of " + BY_NAME.keySet());
                },
                source -> DataResult.success(source.id()));

        private final String id;

        Source(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** True when the subject is the server rather than a villager, so {@code target} is ignored. */
        public boolean isGlobal() {
            return this == CALENDAR;
        }
    }

    public enum Operator {
        EQ("eq"), NE("ne"), LT("lt"), LTE("lte"), GT("gt"), GTE("gte"),
        CONTAINS("contains"), IN("in"), MATCHES("matches"), EXISTS("exists");

        private static final Map<String, Operator> BY_NAME = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(Operator::id, Function.identity()));

        static final Codec<Operator> CODEC = Codec.STRING.flatXmap(
                raw -> {
                    Operator operator = BY_NAME.get(raw.toLowerCase(Locale.ROOT));
                    return operator != null ? DataResult.success(operator) : DataResult.error(
                            () -> "Unknown Townstead operator '" + raw + "'; expected one of " + BY_NAME.keySet());
                },
                operator -> DataResult.success(operator.id()));

        private final String id;

        Operator(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** {@code exists} is the only operator that takes no value, and the only one that may omit it. */
        public boolean needsValue() {
            return this != EXISTS;
        }

        boolean isNumericOnly() {
            return this == LT || this == LTE || this == GT || this == GTE;
        }
    }

    /** A JSON value that survives the codec untouched, as {@code TemplateSpec} already does. */
    private static final Codec<JsonElement> JSON = Codec.PASSTHROUGH.flatXmap(
            dynamic -> DataResult.success(dynamic.convert(JsonOps.INSTANCE).getValue()),
            json -> DataResult.success(new Dynamic<>(JsonOps.INSTANCE, json)));

    private static final Codec<List<String>> PATH_CODEC = Codec.STRING.flatXmap(
            TownsteadQuery::parsePath,
            segments -> DataResult.success(String.join(".", segments)));

    public static final MapCodec<TownsteadQuery> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Source.CODEC.fieldOf("source").forGetter(TownsteadQuery::source),
                    StrictCodecs.strictOptional(TownsteadTarget.CODEC, "target", TownsteadTarget.GIVER)
                            .forGetter(TownsteadQuery::target),
                    PATH_CODEC.fieldOf("path").forGetter(TownsteadQuery::path),
                    Operator.CODEC.fieldOf("operator").forGetter(TownsteadQuery::operator),
                    StrictCodecs.strictOptional(JSON, "value").forGetter(TownsteadQuery::value),
                    StrictCodecs.strictOptional(Codec.BOOL, "missing", false).forGetter(TownsteadQuery::missing)
            ).apply(instance, TownsteadQuery::new));

    /** Validated at parse time, so a malformed query fails the reload rather than a quest. */
    public static final Codec<TownsteadQuery> CODEC = MAP_CODEC.codec().flatXmap(
            TownsteadQuery::validate, DataResult::success);

    private static DataResult<List<String>> parsePath(String raw) {
        if (raw.isEmpty()) {
            return DataResult.error(() -> "Townstead path must not be empty");
        }
        if (raw.length() > MAX_PATH_LENGTH) {
            return DataResult.error(() -> "Townstead path is longer than " + MAX_PATH_LENGTH
                    + " characters: '" + raw + "'");
        }
        List<String> segments = new ArrayList<>();
        for (String segment : raw.split("\\.", -1)) {
            if (segment.isEmpty()) {
                return DataResult.error(() -> "Townstead path has an empty segment: '" + raw + "'");
            }
            segments.add(segment);
        }
        if (segments.size() > MAX_PATH_DEPTH) {
            return DataResult.error(() -> "Townstead path is deeper than " + MAX_PATH_DEPTH
                    + " segments: '" + raw + "'");
        }
        return DataResult.success(List.copyOf(segments));
    }

    private static DataResult<TownsteadQuery> validate(TownsteadQuery query) {
        if (query.operator.needsValue() && query.value.isEmpty()) {
            return DataResult.error(() -> "Townstead operator '" + query.operator.id()
                    + "' requires a 'value'; only 'exists' may omit it");
        }
        if (!query.operator.needsValue() && query.value.isPresent()) {
            return DataResult.error(() -> "Townstead operator 'exists' takes no 'value'");
        }
        if (query.operator == Operator.IN && !query.value.map(JsonElement::isJsonArray).orElse(false)) {
            return DataResult.error(() -> "Townstead operator 'in' requires 'value' to be an array");
        }
        if (query.operator != Operator.IN && query.value.map(JsonElement::isJsonArray).orElse(false)) {
            return DataResult.error(() -> "Townstead operator '" + query.operator.id()
                    + "' requires a single 'value', not an array; use 'in' to test a set");
        }
        if (query.operator == Operator.MATCHES) {
            String pattern = query.value.map(TownsteadQuery::text).orElse("");
            if (pattern.length() > MAX_PATTERN_LENGTH) {
                return DataResult.error(() -> "Townstead 'matches' pattern is longer than "
                        + MAX_PATTERN_LENGTH + " characters");
            }
            try {
                PATTERNS.computeIfAbsent(pattern, Pattern::compile);
            } catch (PatternSyntaxException e) {
                return DataResult.error(() -> "Townstead 'matches' pattern is not a valid regular "
                        + "expression: " + e.getMessage());
            }
        }
        return DataResult.success(query);
    }

    // ---------------------------------------------------------------------------------- evaluation

    /**
     * Tests this query against a resolved subject. {@code subject} is the whole snapshot the
     * {@link #source()} names — {@code null} when the source, the target or the capability was
     * unavailable, in which case the answer is {@link #missing()}.
     */
    public boolean test(@Nullable Object subject) {
        return testResolved(subject, path);
    }

    /**
     * As {@link #test(Object)}, against an explicitly supplied path. Needed because the {@code gene}
     * source spends the first path segment naming <em>which</em> gene, so what remains to walk is
     * shorter than {@link #path()} — see {@code TownsteadEvaluation.effectivePath}.
     */
    public boolean testResolved(@Nullable Object subject, List<String> effectivePath) {
        if (subject == null) {
            return missing;
        }
        Optional<Object> resolved = TownsteadPaths.resolve(subject, effectivePath);
        if (operator == Operator.EXISTS) {
            return resolved.isPresent();
        }
        if (resolved.isEmpty()) {
            return missing;
        }
        JsonElement expected = value.orElse(null);
        if (expected == null) {
            return missing;
        }
        return compare(resolved.get(), expected);
    }

    private boolean compare(Object actual, JsonElement expected) {
        return switch (operator) {
            case EQ -> scalarEquals(actual, expected);
            case NE -> !scalarEquals(actual, expected);
            case LT, LTE, GT, GTE -> numericCompare(actual, expected);
            case CONTAINS -> contains(actual, expected);
            case IN -> in(actual, expected);
            case MATCHES -> PATTERNS.getOrDefault(text(expected), NEVER).matcher(text(actual)).matches();
            case EXISTS -> true; // handled above; kept exhaustive rather than throwing
        };
    }

    /** A pattern that matches nothing, used if a {@code matches} query somehow reaches evaluation uncompiled. */
    private static final Pattern NEVER = Pattern.compile("(?!)");

    private boolean numericCompare(Object actual, JsonElement expected) {
        BigDecimal left = number(actual);
        BigDecimal right = number(expected);
        if (left == null || right == null) {
            return missing;
        }
        int cmp = left.compareTo(right);
        return switch (operator) {
            case LT -> cmp < 0;
            case LTE -> cmp <= 0;
            case GT -> cmp > 0;
            case GTE -> cmp >= 0;
            default -> false;
        };
    }

    private static boolean contains(Object actual, JsonElement expected) {
        if (actual instanceof Map<?, ?> map) {
            return map.containsKey(text(expected));
        }
        if (actual instanceof List<?> list) {
            return list.stream().anyMatch(element -> scalarEquals(element, expected));
        }
        return text(actual).toLowerCase(Locale.ROOT).contains(text(expected).toLowerCase(Locale.ROOT));
    }

    private static boolean in(Object actual, JsonElement expected) {
        if (!(expected instanceof JsonArray array)) {
            return false;
        }
        for (JsonElement element : array) {
            if (scalarEquals(actual, element)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Scalar equality with the two normalisations §4.2 asks for. Numbers compare as
     * {@link BigDecimal} so {@code 30} and {@code 30.0} agree, and everything else compares
     * case-insensitively so enum-like values and resource ids are not tripped by capitalisation.
     *
     * <p>A bare id also matches a {@code minecraft:}-namespaced one, but <b>only</b> when the other
     * side actually carries a namespace — so {@code "farmer"} matches {@code "minecraft:farmer"} while
     * a plain word like {@code "work"} is never quietly turned into an id.
     */
    private static boolean scalarEquals(@Nullable Object actual, JsonElement expected) {
        BigDecimal left = number(actual);
        BigDecimal right = number(expected);
        if (left != null && right != null) {
            return left.compareTo(right) == 0;
        }
        String a = text(actual).toLowerCase(Locale.ROOT);
        String b = text(expected).toLowerCase(Locale.ROOT);
        if (a.equals(b)) {
            return true;
        }
        return namespaced(a).equals(namespaced(b)) && (a.indexOf(':') >= 0) != (b.indexOf(':') >= 0);
    }

    private static String namespaced(String id) {
        return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
    }

    @Nullable
    private static BigDecimal number(@Nullable Object value) {
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (value instanceof JsonPrimitive primitive && primitive.isNumber()) {
            return new BigDecimal(primitive.getAsString());
        }
        return null;
    }

    private static String text(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof JsonPrimitive primitive) {
            // Unquoted for every primitive kind, so "30" and 30 render identically for comparison.
            return primitive.getAsString();
        }
        if (value instanceof JsonElement element) {
            return element.toString();
        }
        return String.valueOf(value);
    }

    /** A human-readable rendering for {@code /mcaquests compat townstead explain} and quest text. */
    public String describe() {
        String rendered = value.map(TownsteadQuery::text).orElse("");
        return source.id() + '.' + String.join(".", path) + ' ' + operator.id()
                + (rendered.isEmpty() ? "" : " " + rendered);
    }
}
