package dev.otectus.mcaquests.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** format_version is checked before a file is parsed (1.7.0; F19). */
class FormatVersionTest {

    private static boolean refused(String json) {
        return FormatVersion.refusal(JsonParser.parseString(json)).isPresent();
    }

    @Test
    @DisplayName("absent or 1 reads as always")
    void currentFormatReads() {
        assertTrue(!refused("{\"id\":\"a:b\"}"));
        assertTrue(!refused("{\"format_version\":1}"));
    }

    @Test
    @DisplayName("a newer format, zero, a fraction or a string is refused")
    void otherFormatsAreRefused() {
        assertTrue(refused("{\"format_version\":2}"));
        assertTrue(refused("{\"format_version\":0}"));
        assertTrue(refused("{\"format_version\":1.5}"));
        assertTrue(refused("{\"format_version\":\"1\"}"));
        assertTrue(FormatVersion.refusal(JsonParser.parseString("{\"format_version\":3}")).orElseThrow()
                .contains("newer MCA: Quests"));
    }
}
