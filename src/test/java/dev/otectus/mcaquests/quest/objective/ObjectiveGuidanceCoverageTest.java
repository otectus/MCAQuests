package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An objective whose text names a place must be able to say where that place is.
 *
 * <p>{@code QuestObjective.guidance} is a {@code default} returning empty, which is the right default
 * for an add-on and a silent one for a built-in. Seven shipped types took it: every Townstead
 * objective and {@code sleep_or_rest}. Between them they account for sixty-one bundled objectives —
 * "keep them rested", "a week kept well", "the whole flock", "build us a dock" — and the tracker's
 * answer to <em>where</em> for all of them was nothing at all, in the one family of quests where the
 * village <em>is</em> the subject and the mod already knows exactly where it is.
 *
 * <p>The check is by reflection on the resolved method's declaring class: a type that still inherits
 * {@link QuestObjective}'s default has no answer, whatever it inherits it through. That is precise
 * where a source scan would be coarse, and it cannot be satisfied by a comment.
 *
 * <p>{@link #PLACELESS} is the deliberate exceptions list, and it is meant to stay short. Each entry
 * is a type for which the honest answer really is nothing: there is no index of where eight
 * prismarine crystals are, and a guess would send the player somewhere confidently wrong, which is
 * worse than sending them nowhere because they would go.
 */
class ObjectiveGuidanceCoverageTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    /**
     * Types that legitimately point nowhere.
     *
     * <p>{@code ftbq_complete_quest} defers entirely to another mod's quest book, which has its own
     * navigation and whose tasks this mod cannot locate. Everything else that gathers an item takes
     * the optional {@code source} block instead, which <em>is</em> a {@code guidance} override — so
     * they are not on this list.
     */
    private static final Set<Class<?>> PLACELESS = Set.of(FtbqCompleteQuestObjective.class);

    @Test
    @DisplayName("every registered objective type can say where it is sending the player")
    void everyTypeAnswersGuidance() {
        List<String> silent = new ArrayList<>();
        for (Class<?> type : registeredTypes()) {
            if (PLACELESS.contains(type)) {
                continue;
            }
            if (declaringClassOfGuidance(type) == QuestObjective.class) {
                silent.add(type.getSimpleName());
            }
        }

        assertEquals(List.of(), silent, "these objective types inherit QuestObjective's empty default, "
                + "so a quest built on one draws no marker, prints no destination on the tracker and "
                + "puts no waypoint on the map. Override guidance(), or add the type to PLACELESS with "
                + "a reason");
    }

    @Test
    @DisplayName("the exceptions list only names types that really are registered")
    void exceptionsListIsNotStale() {
        // A renamed or deleted type left behind here would silently excuse whatever took its place.
        Set<Class<?>> registered = registeredTypes();
        List<String> stale = PLACELESS.stream()
                .filter(type -> !registered.contains(type))
                .map(Class::getSimpleName)
                .toList();

        assertEquals(List.of(), stale, "PLACELESS names types that no longer register");
    }

    /** Every objective class reachable from the dispatch registry, in registration order. */
    private static Set<Class<?>> registeredTypes() {
        Set<Class<?>> types = new LinkedHashSet<>();
        for (java.lang.reflect.Field field : ObjectiveTypes.class.getDeclaredFields()) {
            if (!QuestObjectiveType.class.isAssignableFrom(field.getType())) {
                continue;
            }
            // The concrete objective class is the type argument on the field's declared generic type.
            String generic = field.getGenericType().getTypeName();
            int open = generic.indexOf('<');
            if (open < 0) {
                continue;
            }
            String name = generic.substring(open + 1, generic.length() - 1);
            try {
                types.add(Class.forName(name));
            } catch (ClassNotFoundException e) {
                throw new AssertionError("ObjectiveTypes." + field.getName()
                        + " names a class that does not exist: " + name, e);
            }
        }
        assertTrue(types.size() > 25,
                "the reflection over ObjectiveTypes found almost nothing; has the class been reshaped?");
        return types;
    }

    private static Class<?> declaringClassOfGuidance(Class<?> type) {
        try {
            Method guidance = type.getMethod("guidance", ServerPlayer.class, ActiveQuest.class,
                    ObjectiveProgress.class, ServerLevel.class);
            return guidance.getDeclaringClass();
        } catch (NoSuchMethodException e) {
            throw new AssertionError("QuestObjective.guidance has been renamed or its signature "
                    + "changed; this test needs updating with it", e);
        }
    }
}
