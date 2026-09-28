package dev.otectus.mcaquests;

import dev.otectus.mcaquests.api.QuestDialogueHooks;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        for (String id : QuestDialogueHooks.resolverIds()) {
            QuestDialogueHooks.removeResolver(id);
        }
    }

    /** Keyed resolvers are asked in registration order and the first line wins (1.7.1). */
    @Test
    void keyedResolversAreAskedInOrderAndFirstLineWins() {
        QuestDialogueHooks.addResolver("examplemod:first", (player, villager, def, state, fb) -> null);
        QuestDialogueHooks.addResolver("mcaconversations", (player, villager, def, state, fb) -> Component.literal("voiced"));
        QuestDialogueHooks.addResolver("mcacrime", (player, villager, def, state, fb) -> Component.literal("guard"));
        assertEquals(Component.literal("voiced"),
                QuestDialogueHooks.resolve(null, null, null, "offer", Component.literal("static")));
        assertEquals(java.util.List.of("examplemod:first", "mcaconversations", "mcacrime"),
                QuestDialogueHooks.resolverIds());
    }

    /** The legacy anonymous slot is asked last, so a deliberate keyed registration is never displaced by it. */
    @Test
    void legacySlotIsAskedAfterKeyedResolvers() {
        QuestDialogueHooks.setResolver((player, villager, def, state, fb) -> Component.literal("legacy"));
        QuestDialogueHooks.addResolver("mcacrime", (player, villager, def, state, fb) -> Component.literal("guard"));
        assertEquals(Component.literal("guard"),
                QuestDialogueHooks.resolve(null, null, null, "offer", Component.literal("static")));
        QuestDialogueHooks.removeResolver("mcacrime");
        assertEquals(Component.literal("legacy"),
                QuestDialogueHooks.resolve(null, null, null, "offer", Component.literal("static")));
    }

    /** A throwing resolver hands over to the next one rather than to the fallback. */
    @Test
    void aThrowingKeyedResolverYieldsToTheNext() {
        QuestDialogueHooks.addResolver("examplemod:broken", (player, villager, def, state, fb) -> {
            throw new IllegalStateException("boom");
        });
        QuestDialogueHooks.addResolver("mcaconversations", (player, villager, def, state, fb) -> Component.literal("voiced"));
        assertEquals(Component.literal("voiced"),
                QuestDialogueHooks.resolve(null, null, null, "offer", Component.literal("static")));
    }

    /** Re-registering an id replaces the resolver in place; removing it is idempotent. */
    @Test
    void reRegistrationReplacesInPlace() {
        QuestDialogueHooks.addResolver("mcaconversations", (player, villager, def, state, fb) -> Component.literal("one"));
        QuestDialogueHooks.addResolver("mcacrime", (player, villager, def, state, fb) -> Component.literal("two"));
        QuestDialogueHooks.addResolver("mcaconversations", (player, villager, def, state, fb) -> Component.literal("three"));
        assertEquals(Component.literal("three"),
                QuestDialogueHooks.resolve(null, null, null, "offer", Component.literal("static")));
        QuestDialogueHooks.removeResolver("mcaconversations");
        QuestDialogueHooks.removeResolver("mcaconversations");
        assertEquals(Component.literal("two"),
                QuestDialogueHooks.resolve(null, null, null, "offer", Component.literal("static")));
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

    /**
     * The lifecycle state reaches the resolver, so an add-on can voice a refusal differently from an
     * acceptance. Worth pinning because {@code decline} is the newest state to be routed through here and
     * the only one whose line the mod parsed and then never showed for several releases.
     */
    @Test
    void passesTheLifecycleStateThroughToTheResolver() {
        Component[] seen = new Component[1];
        String[] state = new String[1];
        QuestDialogueHooks.setResolver((player, villager, def, lifecycleState, fallback) -> {
            state[0] = lifecycleState;
            seen[0] = fallback;
            return Component.literal("Maybe another time.");
        });
        Component fallback = Component.literal("static decline line");

        Component resolved = QuestDialogueHooks.resolve(null, null, null, "decline", fallback);

        assertEquals("decline", state[0]);
        assertSame(fallback, seen[0], "the pack's own line must be offered as the fallback");
        assertEquals(Component.literal("Maybe another time."), resolved);
    }

    /** A resolver that throws while voicing a refusal must not break the refusal. */
    @Test
    void aThrowingResolverDoesNotBreakDeclining() {
        Component fallback = Component.literal("Maybe another time.");
        QuestDialogueHooks.setResolver((player, villager, def, state, fb) -> {
            throw new IllegalStateException("conversations add-on blew up");
        });
        assertSame(fallback, QuestDialogueHooks.resolve(null, null, null, "decline", fallback));
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
