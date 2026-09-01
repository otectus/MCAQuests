package dev.otectus.mcaquests.data;

import dev.otectus.mcaquests.project.ProjectPhase;
import dev.otectus.mcaquests.project.SponsorSpec;
import dev.otectus.mcaquests.quest.FailureSpec;
import dev.otectus.mcaquests.quest.GiverSpec;
import dev.otectus.mcaquests.quest.OfferShaping;
import dev.otectus.mcaquests.quest.RepeatRule;
import dev.otectus.mcaquests.quest.TurnInSpec;
import dev.otectus.mcaquests.quest.situation.SituationDefinition;
import dev.otectus.mcaquests.quest.situation.SituationOffer;
import dev.otectus.mcaquests.quest.situation.trigger.MissingKinTrigger;
import dev.otectus.mcaquests.quest.situation.trigger.VillagerDeathTrigger;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.support.TestBootstrap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A datapack field that is parsed and then ignored is a bug with a documentation page.
 *
 * <p>Five of them shipped. {@code SponsorSpec.required_count} described itself as "informational/UX" and
 * was shown to nobody. {@code VillagerDeathTrigger.relation} and {@code MissingKinTrigger.relation} were
 * both read out of the JSON, both documented as narrowing the trigger, and both discarded —
 * {@code find_missing_child}, whose trigger says {@code "relation": "child"}, opened just as readily when
 * a villager's spouse went missing. A pack author writing any of these got no error, no warning, and no
 * effect.
 *
 * <p>The check is a source scan for each record component's accessor name. Coarse — a mention in a comment
 * satisfies it — but coarse in the safe direction: it cannot pass a field that nothing anywhere refers to,
 * which is the failure that actually happened.
 *
 * <p>{@link #WRITE_ONLY} is the deliberate exceptions list. It is meant to stay short, and an addition to
 * it should be an argued edit rather than a way to quiet the test.
 */
class DatapackFieldCoverageTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final Path MAIN = Path.of("src/main/java");

    /** The datapack-facing records whose every field must do something. */
    private static final List<Class<?>> TYPES = List.of(
            SponsorSpec.class, ProjectPhase.class,
            FailureSpec.class, GiverSpec.class, OfferShaping.class, RepeatRule.class, TurnInSpec.class,
            VillagerTarget.class,
            SituationDefinition.class, SituationOffer.class,
            VillagerDeathTrigger.class, MissingKinTrigger.class);

    /**
     * Components that legitimately have no reader outside their own class.
     *
     * <p>Each entry needs a reason, and "we could not find the reader" is not one.
     */
    private static final Set<String> WRITE_ONLY = Set.of(
            // All of these are read by a derived accessor on their own record, which is the only place
            // that should be reading them: the raw field answers a question ("what did the author write?")
            // that no caller outside the record has any business asking.
            //
            // Read through cooldownTicks() / mode(), which fall back to config when the author said
            // nothing — the Optional exists purely to tell "unstated" from "stated as the default".
            "RepeatRule.declaredCooldownTicks",
            "TurnInSpec.declaredMode",
            // Read through onDeathOr(fallback), which supplies the configured default.
            "SponsorSpec.onDeath",
            // Read through deadlineGameTime(start), which takes whichever of the two deadlines lands
            // first — a caller comparing them itself would get that precedence wrong.
            "FailureSpec.deadlineTicks",
            "FailureSpec.deadlineTimeOfDay",
            // Read through acceptsHearts(hearts). The bounds are a band, and a caller that read one
            // without the other would be asking half a question.
            "GiverSpec.minHearts",
            "GiverSpec.maxHearts");

    @Test
    @DisplayName("every datapack field on these records is read somewhere outside its own class")
    void noDatapackFieldIsParsedAndIgnored() {
        String sources = allSources();
        List<String> dead = new ArrayList<>();
        for (Class<?> type : TYPES) {
            RecordComponent[] components = type.getRecordComponents();
            if (components == null) {
                throw new AssertionError(type.getSimpleName() + " is no longer a record; update TYPES");
            }
            for (RecordComponent component : components) {
                String qualified = type.getSimpleName() + "." + component.getName();
                if (WRITE_ONLY.contains(qualified)) {
                    continue;
                }
                // "name()" catches a direct call; "::name" catches a method reference, which is how most
                // of these are read (every codec field uses one).
                if (!sources.contains(component.getName() + "()")
                        && !sources.contains("::" + component.getName())) {
                    dead.add(qualified);
                }
            }
        }
        assertEquals(List.of(), dead, "these datapack fields are parsed out of the JSON and read by "
                + "nothing: either use them or remove them, along with their DATAPACK.md rows");
    }

    /** Every main source file except the ones declaring the records under test. */
    private static String allSources() {
        Set<String> declaring = Set.of(TYPES.stream()
                .map(type -> type.getSimpleName() + ".java")
                .toArray(String[]::new));
        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (declaring.contains(path.getFileName().toString())) {
                    continue;
                }
                all.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return all.toString();
    }
}
