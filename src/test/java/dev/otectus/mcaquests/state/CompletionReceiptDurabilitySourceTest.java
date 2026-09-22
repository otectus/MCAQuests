package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.support.TestPaths;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletionReceiptDurabilitySourceTest {
    @Test
    void durabilityFenceSavesOnlyTheReceiptOwner() throws Exception {
        String source = Files.readString(TestPaths.of(
                "src/main/java/dev/otectus/mcaquests/state/CompletionReceiptDurability.java"));
        assertTrue(source.contains("mcaquests$savePlayer(player)"));
        assertTrue(source.contains("AttachmentHolder.ATTACHMENTS_NBT_KEY"),
                "the fence rereads the attachment NeoForge wrote, not the 1.20.1 ForgeCaps key");
        assertFalse(source.contains("saveAll()"),
                "one receipt must not force a save of every online player's attachment graph");

        String mixins = Files.readString(TestPaths.of("src/main/resources/mcaquests.mixins.json"));
        assertTrue(mixins.contains("PlayerListAccessor"));
    }
}
