package dev.otectus.mcaquests.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The format regex accept/reject table for FTB hex ids (spec §29.1 #1). This tests <em>our</em>
 * validator ({@link FtbqIds#isValidFormat}) — FTB's own {@code parseCodeString} is bridge-side and out
 * of scope here (and unavailable: no FTB jars on the unit test classpath).
 */
class FtbqIdParsingTest {

    @Test
    void acceptsSixteenHexCharsWithoutHash() {
        assertTrue(FtbqIds.isValidFormat("1A2B3C4D5E6F7081"));
    }

    @Test
    void acceptsSixteenHexCharsWithLeadingHash() {
        assertTrue(FtbqIds.isValidFormat("#1A2B3C4D5E6F7081"));
    }

    @Test
    void acceptsSingleHexChar() {
        assertTrue(FtbqIds.isValidFormat("a"));
        assertTrue(FtbqIds.isValidFormat("#a"));
    }

    @Test
    void acceptsMixedCaseHexDigits() {
        assertTrue(FtbqIds.isValidFormat("F00dF00dF00dF00D"));
    }

    @Test
    void rejectsSeventeenHexChars() {
        assertFalse(FtbqIds.isValidFormat("1A2B3C4D5E6F70812"));
        assertFalse(FtbqIds.isValidFormat("#1A2B3C4D5E6F70812"));
    }

    @Test
    void rejectsInvalidCharacters() {
        assertFalse(FtbqIds.isValidFormat("1A2B3C4D5E6F708G")); // 'G' is not hex
        assertFalse(FtbqIds.isValidFormat("hello world"));
        assertFalse(FtbqIds.isValidFormat("1A2B-3C4D"));
    }

    @Test
    void rejectsEmptyString() {
        assertFalse(FtbqIds.isValidFormat(""));
    }

    @Test
    void rejectsBareHash() {
        assertFalse(FtbqIds.isValidFormat("#"));
    }

    @Test
    void rejectsNull() {
        assertFalse(FtbqIds.isValidFormat(null));
    }

    @Test
    void rejectsDoubleLeadingHash() {
        assertFalse(FtbqIds.isValidFormat("##1A2B3C4D5E6F70"));
    }

    @Test
    void rejectsTrailingGarbage() {
        assertFalse(FtbqIds.isValidFormat("1A2B3C4D5E6F7081 "));
        assertFalse(FtbqIds.isValidFormat(" 1A2B3C4D5E6F7081"));
    }
}
