package dev.otectus.mcaquests.compat.map;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MapBindingEnumTest {
    public enum ReorderedPalette {
        YELLOW, LIGHT_BLUE, BLACK;

        @Override
        public String toString() {
            return "localized label";
        }
    }

    public enum UninitializablePalette {
        LIGHT_BLUE;

        static {
            if (unavailableRuntime()) throw new NoSuchMethodError("Minecraft runtime is not mapped");
        }

        private static boolean unavailableRuntime() {
            return true;
        }
    }

    @Test
    void liveResolutionUsesTheConstantNameAndItsActualOrdinal() {
        MapBinding.Member palette = MapBinding.Member.cls("MapBindingEnumTest$ReorderedPalette");
        MapBinding.Resolution binding = resolve(palette);
        assertTrue(binding.isBound());
        Object value = binding.enumConstant(palette, "LIGHT_BLUE");
        assertSame(ReorderedPalette.LIGHT_BLUE, value);
        assertEquals(1, ((Enum<?>) value).ordinal());
        assertNull(binding.enumConstant(palette, "localized label"));
        assertNull(binding.enumConstant(palette, "NONEXISTENT"));
    }

    @Test
    void shippedDeclarationProbeDoesNotExecuteAnIncompatibleInitializer() {
        MapBinding.Member palette = MapBinding.Member.cls("MapBindingEnumTest$UninitializablePalette");
        MapBinding.Resolution binding = resolve(palette);
        assertTrue(binding.isBound());
        assertEquals(Set.of("LIGHT_BLUE"), MapBindingProbeTest.declaredEnumConstants(binding.cls(palette)));
        // Runtime binding must still fail safely if a genuinely incompatible game cannot initialize it.
        assertNull(binding.enumConstant(palette, "LIGHT_BLUE"));
        assertNull(binding.enumConstant(palette, "LIGHT_BLUE"));
    }

    private static MapBinding.Resolution resolve(MapBinding.Member palette) {
        return MapBinding.resolve("test", "dev.otectus.mcaquests.compat.map.", "MapBindingEnumTest",
                List.of(palette), MapBindingEnumTest.class.getClassLoader());
    }
}
