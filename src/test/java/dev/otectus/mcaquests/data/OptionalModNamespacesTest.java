package dev.otectus.mcaquests.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A file that fails to parse only because it names an uninstalled optional mod's content is excluded,
 * and every other parse failure stays an error (1.7.0). No mod is loaded in this JVM, so every known
 * optional namespace reads as absent.
 */
class OptionalModNamespacesTest {

    @Test
    @DisplayName("a type registered by an absent optional mod excludes the file instead of failing it")
    void absentOptionalModIsExcluded() {
        Map<String, Integer> absent = new TreeMap<>();
        assertTrue(OptionalModNamespaces.excludedForAbsentMod(
                "Unknown condition type 'ultima_kingdoms:kingdom'", absent));
        assertTrue(OptionalModNamespaces.excludedForAbsentMod(
                "Unknown objective type 'mcaconversations:talk_about'", absent));
        assertTrue(OptionalModNamespaces.excludedForAbsentMod(
                "Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: iceandfire:dragonbone", absent),
                "the first id in the message is the registry's own; the absent mod's id follows it");
        assertEquals(Map.of("iceandfire", 1, "mcaconversations", 1, "ultima_kingdoms", 1), Map.copyOf(absent));
    }

    @Test
    @DisplayName("an unknown namespace is still an error, so a typo is never silently excluded")
    void typoStaysAnError() {
        Map<String, Integer> absent = new TreeMap<>();
        assertFalse(OptionalModNamespaces.excludedForAbsentMod("Unknown item 'minecarft:stone'", absent));
        assertFalse(OptionalModNamespaces.excludedForAbsentMod("Missing field 'count'", absent));
        assertFalse(OptionalModNamespaces.excludedForAbsentMod(null, absent));
        assertTrue(absent.isEmpty());
    }

    @Test
    @DisplayName("the namespace is the first id in the message, or nothing")
    void namespaceIn() {
        assertEquals("townstead", OptionalModNamespaces.namespaceIn("bad value townstead:inn here"));
        assertEquals("", OptionalModNamespaces.namespaceIn("no ids at all"));
        assertEquals("", OptionalModNamespaces.namespaceIn(null));
    }
}
