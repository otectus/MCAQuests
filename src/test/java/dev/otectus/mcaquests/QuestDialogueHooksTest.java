package dev.otectus.mcaquests;

import dev.otectus.mcaquests.api.QuestDialogueHooks;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The add-on dialogue resolver hook must never change quest text unless a resolver opts in, and must never
 * let a misbehaving resolver break the quest UI — a null return or a thrown exception both fall back to the
 * quest's own static line. (No {@code ServerPlayer}/villager is needed: {@code resolve} never dereferences
 * them, and these tests pass {@code null} for all three context args deliberately.)
 */
class QuestDialogueHooksTest {

    @AfterEach
    void clearResolver() {
        QuestDialogueHooks.setResolver(null);
    }

    @Test
    void returnsFallbackWhenNoResolverRegistered() {
        Component fallback = Component.literal("static");
        assertSame(fallback, QuestDialogueHooks.resolve(null, null, null, "offer", fallback));
    }

    @Test
    void usesResolverResultWhenItReturnsALine() {
        Component voiced = Component.literal("voiced");
        QuestDialogueHooks.setResolver((player, villager, def, state, fallback) -> voiced);
        assertSame(voiced, QuestDialogueHooks.resolve(null, null, null, "offer", Component.literal("static")));
    }

    @Test
    void fallsBackWhenResolverReturnsNull() {
        Component fallback = Component.literal("static");
        QuestDialogueHooks.setResolver((player, villager, def, state, fb) -> null);
        assertSame(fallback, QuestDialogueHooks.resolve(null, null, null, "offer", fallback));
    }

    @Test
    void fallsBackWhenResolverThrows() {
        Component fallback = Component.literal("static");
        QuestDialogueHooks.setResolver((player, villager, def, state, fb) -> {
            throw new RuntimeException("boom");
        });
        assertSame(fallback, QuestDialogueHooks.resolve(null, null, null, "offer", fallback));
    }
}
