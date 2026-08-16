package dev.otectus.mcaquests.quest.reputation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.IncidentSelector;
import dev.otectus.mcaquests.data.StrictCodecs;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One authored reputation outcome: how much standing a piece of work moves, what kind of deed it is,
 * who hears about it, and which players receive it (spec §29.3–§29.5).
 *
 * <h2>Integer shorthand</h2>
 *
 * <p>The codec accepts either an object or a bare integer, so every pre-1.1.0 datapack that wrote
 *
 * <pre>{@code "reputation": { "on_project_complete": 10 }}</pre>
 *
 * keeps working unchanged and means exactly what it always did. The object form only adds detail the
 * shorthand could not express — the named incident type, the visibility, the recipient set.
 *
 * <h2>Failure and abandonment are opt-in</h2>
 *
 * <p>There is no default penalty for failing or walking away from a quest (§29.3, §33 rule 6). A pack
 * author who wants one writes it; otherwise nothing happens. Punishing a player for abandoning a
 * quest they never had to accept would be a behaviour change imposed on every existing pack.
 */
public record ReputationOutcome(
        int delta,
        Optional<ResourceLocation> incident,
        Optional<String> visibility,
        List<String> tags,
        Recipients recipients) {

    /** Who receives an outcome (§29.4, §29.5). */
    public enum Recipients {

        /** Nobody. What a zero shorthand means, and the safe default for an unauthored outcome. */
        NOBODY,
        /** The single player who resolved the work. The default for a situation success. */
        RESOLVING_PLAYER,
        /** Everyone who contributed to the phase that just completed. The default for a phase. */
        PHASE_CONTRIBUTORS,
        /** Everyone who contributed to the project at least once. The default for completion/failure. */
        ALL_PARTICIPANTS,
        /** Everyone who accepted the situation, whether or not they finished it. */
        ACCEPTED_PARTICIPANTS;

        public static final Codec<Recipients> CODEC = Codec.STRING.comapFlatMap(
                raw -> {
                    for (Recipients value : values()) {
                        if (value.jsonName().equalsIgnoreCase(raw)) {
                            return DataResult.success(value);
                        }
                    }
                    return DataResult.error(() -> "Unknown recipients '" + raw + "' (expected one of: "
                            + String.join(", ", java.util.Arrays.stream(values())
                                    .map(Recipients::jsonName).toList()) + ")");
                },
                Recipients::jsonName);

        public String jsonName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Codec<ReputationOutcome> OBJECT_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    // Strict optional fields: a misspelled recipient set or an unparseable incident id
                    // must be reported, not silently replaced by the default. See StrictCodecs.
                    StrictCodecs.strictOptional(Codec.INT, "delta", 0).forGetter(ReputationOutcome::delta),
                    StrictCodecs.strictOptional(ResourceLocation.CODEC, "incident")
                            .forGetter(ReputationOutcome::incident),
                    StrictCodecs.strictOptional(Codec.STRING, "visibility")
                            .forGetter(ReputationOutcome::visibility),
                    StrictCodecs.strictOptional(Codec.STRING.listOf(), "tags", List.of())
                            .forGetter(ReputationOutcome::tags),
                    StrictCodecs.strictOptional(Recipients.CODEC, "recipients", Recipients.NOBODY)
                            .forGetter(ReputationOutcome::recipients)
            ).apply(instance, ReputationOutcome::new));

    /**
     * Accepts the object form or the legacy bare integer. The integer form leaves
     * {@link #recipients} as {@link Recipients#NOBODY}; each call site substitutes the default its
     * own shorthand has always implied, which is why the shorthand defaults live at the call sites
     * rather than here.
     */
    public static final Codec<ReputationOutcome> CODEC = Codec.either(Codec.INT, OBJECT_CODEC)
            .xmap(
                    either -> either.map(ReputationOutcome::ofShorthand, outcome -> outcome),
                    outcome -> com.mojang.datafixers.util.Either.right(outcome));

    public ReputationOutcome {
        tags = tags == null ? List.of()
                : List.copyOf(tags.stream().map(tag -> tag.toLowerCase(Locale.ROOT)).toList());
        incident = incident == null ? Optional.empty() : incident;
        visibility = visibility == null ? Optional.empty() : visibility;
        recipients = recipients == null ? Recipients.NOBODY : recipients;
    }

    public static ReputationOutcome ofShorthand(int delta) {
        return new ReputationOutcome(delta, Optional.empty(), Optional.empty(), List.of(),
                Recipients.NOBODY);
    }

    public static final ReputationOutcome NONE = ofShorthand(0);

    /** True when this outcome would do nothing at all. */
    public boolean isNoOp() {
        return delta == 0 && incident.isEmpty();
    }

    /** This outcome with {@code fallback} substituted when no explicit recipient set was authored. */
    public ReputationOutcome withDefaultRecipients(Recipients fallback) {
        return recipients == Recipients.NOBODY && !isNoOp()
                ? new ReputationOutcome(delta, incident, visibility, tags, fallback)
                : this;
    }

    /** This outcome with {@code fallback} substituted when no explicit incident type was authored. */
    public ReputationOutcome withDefaultIncident(ResourceLocation fallback) {
        return incident.isEmpty()
                ? new ReputationOutcome(delta, Optional.ofNullable(fallback), visibility, tags, recipients)
                : this;
    }

    /** A selector over the tags this outcome declares — used by {@code resolve_incident} rewards. */
    public IncidentSelector asSelector() {
        return new IncidentSelector(incident.map(List::of).orElseGet(List::of), List.of(), tags, false, 0L);
    }
}
