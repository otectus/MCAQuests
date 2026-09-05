package dev.otectus.mcaquests.compat.bountiful;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rarity ladder, and the one value on it that is not a rank.
 *
 * <p>{@link BountyRarity#UNKNOWN} carries the whole design of this enum, and it is the case that
 * would silently ship wrong: if an unreadable rarity sorted as "lowest", a quest asking for a rare
 * contract would be satisfied by any contract at all the moment the rarity reader failed to bind —
 * and it would look like the quest working, not like the quest broken.
 */
class BountyRarityTest {

    @Test
    @DisplayName("Bountiful's own names parse, case-insensitively")
    void namesParse() {
        assertEquals(BountyRarity.COMMON, BountyRarity.fromName("COMMON"));
        assertEquals(BountyRarity.UNCOMMON, BountyRarity.fromName("uncommon"));
        assertEquals(BountyRarity.RARE, BountyRarity.fromName("  Rare "));
        assertEquals(BountyRarity.EPIC, BountyRarity.fromName("EPIC"));
        assertEquals(BountyRarity.LEGENDARY, BountyRarity.fromName("Legendary"));
    }

    @Test
    @DisplayName("anything else is UNKNOWN, including null and Bountiful's own UNKNOWN spelling")
    void unrecognisedNamesAreUnknown() {
        assertEquals(BountyRarity.UNKNOWN, BountyRarity.fromName(null));
        assertEquals(BountyRarity.UNKNOWN, BountyRarity.fromName(""));
        assertEquals(BountyRarity.UNKNOWN, BountyRarity.fromName("MYTHIC"),
                "a rank Bountiful adds later must read as unknown rather than throw");
        assertEquals(BountyRarity.UNKNOWN, BountyRarity.fromName("UNKNOWN"),
                "UNKNOWN is ours, not a rank Bountiful can send; parsing it back is still unknown");
    }

    @Test
    @DisplayName("atLeast orders the five ranks")
    void ranksAreOrdered() {
        assertTrue(BountyRarity.LEGENDARY.atLeast(BountyRarity.COMMON));
        assertTrue(BountyRarity.RARE.atLeast(BountyRarity.RARE));
        assertTrue(BountyRarity.EPIC.atLeast(BountyRarity.RARE));
        assertFalse(BountyRarity.UNCOMMON.atLeast(BountyRarity.RARE));
        assertFalse(BountyRarity.COMMON.atLeast(BountyRarity.LEGENDARY));
    }

    @Test
    @DisplayName("UNKNOWN never meets a minimum, and is never a minimum that can be met")
    void unknownSatisfiesNothing() {
        for (BountyRarity rank : BountyRarity.values()) {
            assertFalse(BountyRarity.UNKNOWN.atLeast(rank),
                    "an unreadable rarity must not satisfy " + rank + "; it is not a rank");
            assertFalse(rank.atLeast(BountyRarity.UNKNOWN),
                    "\"at least unknown\" is not a question with an answer, so " + rank
                            + " must not satisfy it vacuously");
        }
    }

    @Test
    @DisplayName("every rank has its own lang key")
    void keysAreDistinct() {
        assertEquals("mcaquests.bountiful.rarity.rare", BountyRarity.RARE.translationKey());
        assertEquals("mcaquests.bountiful.rarity.unknown", BountyRarity.UNKNOWN.translationKey());
    }
}
