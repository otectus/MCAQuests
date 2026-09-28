package dev.otectus.mcaquests.api;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Guarded holder for the optional {@link QuestDialogueResolver}s. An add-on registers its resolver
 * during its own setup with {@link #addResolver}; MCA: Quests calls {@link #resolve} at every
 * lifecycle-dialogue build point. With no resolver registered (the default) {@link #resolve} returns
 * the static fallback, so quest text is unchanged when no add-on is present — the degrade-to-static
 * guarantee. A resolver that throws or returns {@code null} also falls back, so a misbehaving add-on
 * can never break the quest UI.
 *
 * <p><b>Several resolvers (1.7.1).</b> Until 1.7.0 this was a single slot and the last writer won, so a
 * second voice provider — MCA: Crime voicing a guard, Ultima Kingdoms voicing a herald — would have
 * silently displaced MCA: Conversations. Resolvers are now an ordered chain keyed by the add-on's id:
 * each is asked in registration order and the first non-null line wins. {@link #setResolver} remains
 * as the legacy single slot, asked after every keyed resolver.
 */
public final class QuestDialogueHooks {

    /** The legacy anonymous slot, kept so an add-on built against 1.7.0 keeps working unchanged. */
    private static final String LEGACY_KEY = "mcaquests:legacy";

    private static final Map<String, QuestDialogueResolver> RESOLVERS = new LinkedHashMap<>();
    private static volatile List<QuestDialogueResolver> chain = List.of();

    private QuestDialogueHooks() {
    }

    /**
     * Registers (or clears, with {@code null}) the legacy add-on dialogue resolver. Last writer wins for
     * this one slot; it is asked after every resolver added with {@link #addResolver}.
     */
    public static void setResolver(@Nullable QuestDialogueResolver newResolver) {
        if (newResolver == null) {
            removeResolver(LEGACY_KEY);
        } else {
            addResolver(LEGACY_KEY, newResolver);
        }
    }

    /**
     * Adds, or replaces, the resolver registered under {@code id} (an add-on's mod id, or
     * {@code modid:purpose} when it has several). Registration order is consultation order; replacing
     * keeps the original position.
     */
    public static synchronized void addResolver(String id, QuestDialogueResolver resolver) {
        if (id == null || id.isBlank() || resolver == null) {
            throw new IllegalArgumentException("a dialogue resolver needs a non-blank id and a resolver");
        }
        RESOLVERS.put(id, resolver);
        rebuildChain();
    }

    /** Withdraws the resolver registered under {@code id}. Idempotent. */
    public static synchronized void removeResolver(String id) {
        if (id != null && RESOLVERS.remove(id) != null) {
            rebuildChain();
        }
    }

    /** The registered resolver ids in consultation order, for diagnostics. */
    public static synchronized List<String> resolverIds() {
        return List.copyOf(RESOLVERS.keySet());
    }

    private static void rebuildChain() {
        // The legacy slot goes last whatever its registration order: a keyed resolver is the newer,
        // deliberate registration and must not lose to an anonymous one.
        List<QuestDialogueResolver> ordered = new ArrayList<>(RESOLVERS.size());
        RESOLVERS.forEach((key, resolver) -> {
            if (!LEGACY_KEY.equals(key)) {
                ordered.add(resolver);
            }
        });
        QuestDialogueResolver legacy = RESOLVERS.get(LEGACY_KEY);
        if (legacy != null) {
            ordered.add(legacy);
        }
        chain = List.copyOf(ordered);
    }

    /**
     * Returns the first resolver's voiced line for this state, or {@code fallback} when no resolver is
     * set, every resolver returns {@code null}, or they all throw.
     */
    public static Component resolve(ServerPlayer player, @Nullable Entity villager, QuestDefinition def,
                                    String lifecycleState, Component fallback) {
        List<QuestDialogueResolver> active = chain;
        for (QuestDialogueResolver resolver : active) {
            try {
                Component voiced = resolver.resolve(player, villager, def, lifecycleState, fallback);
                if (voiced != null) {
                    return voiced;
                }
            } catch (Throwable t) {
                McaQuests.LOGGER.debug("[MCA: Quests] Dialogue resolver threw for state '{}'; asking the next one.",
                        lifecycleState, t);
            }
        }
        return fallback;
    }
}
