package dev.otectus.mcaquests.compat.townstead;

import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Townstead overloads {@code ProfessionProgressions.spec} (String or ProfessionXpType) and the
 * {@code LearnedSkills} methods (LivingEntity or UUID) under one name and arity. Before 1.7.0 the binder
 * took whichever {@code getMethods()} listed first, and on a production server that was the enum
 * overload: every profession track read as "no progression", so workforce objectives could never count
 * a resident. These pin the selection rule in both orders the JVM might list the methods.
 */
class TownsteadOverloadSelectionTest {

    /** Stands in for Townstead's owners: the second overload of each is the one we must not bind. */
    @SuppressWarnings("unused")
    public static final class Owner {
        public static Object spec(String professionId) {
            return professionId;
        }

        public static Object spec(Thread.State notAString) {
            return notAString;
        }

        public static boolean has(LivingEntity villager, Object skill) {
            return true;
        }

        public static boolean has(UUID villager, Object skill) {
            return false;
        }

        public static Object unique(String id) {
            return id;
        }
    }

    private static Method[] methods(boolean reversed) {
        List<Method> list = Arrays.asList(Owner.class.getMethods());
        if (reversed) {
            Collections.reverse(list);
        }
        return list.toArray(Method[]::new);
    }

    @Test
    @DisplayName("an overload named by its first parameter binds the same method in either listing order")
    void hintPicksTheOverload() {
        for (boolean reversed : new boolean[] {false, true}) {
            Method spec = TownsteadBinding.select(methods(reversed), "spec", 1, true, "java.lang.String");
            assertNotNull(spec);
            assertEquals(String.class, spec.getParameterTypes()[0]);
            Method has = TownsteadBinding.select(methods(reversed), "has", 2, true,
                    "net.minecraft.world.entity.LivingEntity");
            assertNotNull(has);
            assertEquals(LivingEntity.class, has.getParameterTypes()[0]);
        }
    }

    @Test
    @DisplayName("an overloaded member without a hint is left unbound rather than guessed")
    void ambiguityIsNotGuessed() {
        assertNull(TownsteadBinding.select(methods(false), "spec", 1, true, null));
        assertNull(TownsteadBinding.select(methods(true), "has", 2, true, null));
    }

    @Test
    @DisplayName("a unique member binds without a hint, and a hint that matches nothing binds nothing")
    void uniqueAndMismatchedHints() {
        assertNotNull(TownsteadBinding.select(methods(false), "unique", 1, true, null));
        assertNull(TownsteadBinding.select(methods(false), "unique", 1, true, "java.util.UUID"));
        assertNull(TownsteadBinding.select(methods(false), "unique", 1, false, null), "staticness still counts");
    }
}
