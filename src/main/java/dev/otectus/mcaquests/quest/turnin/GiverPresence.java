package dev.otectus.mcaquests.quest.turnin;

import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Whether the villager who gave a quest is here to take it back.
 *
 * <p>The same-profession hand-in fallback is documented as "if the original giver is gone", and the
 * code that read it asked only whether the config flag was set — so a player standing next to a
 * perfectly healthy giver could hand the quest to the farmer beside them instead. This is the missing
 * question, asked before that fallback is allowed.
 *
 * <p><b>Absence from the loaded entities is not proof of death.</b> A villager in an unloaded chunk,
 * in a dimension nobody is standing in, or one the server has not restored yet, is indistinguishable
 * here from one that no longer exists: {@code ActiveQuest} records no last-known position for the
 * giver, so there is nothing left to search. {@link #UNLOADED_OR_UNKNOWN} names that honestly rather
 * than pretending to know, and the fallback treats it as absence — the alternative is a quest the
 * player cannot hand in until they happen to walk back into the right chunk.
 */
public enum GiverPresence {

    /** The giver is loaded and alive. The fallback is not needed and is not allowed. */
    PRESENT,
    /** The giver is loaded but dead or removed. The fallback applies. */
    KNOWN_DEAD,
    /** The giver is in no loaded level. May be unloaded, may be gone; we cannot tell. */
    UNLOADED_OR_UNKNOWN;

    /** Where {@code active}'s giver is, searching {@code level} first and then the server's others. */
    public static GiverPresence of(ServerLevel level, ActiveQuest active) {
        if (level == null) {
            return UNLOADED_OR_UNKNOWN;
        }
        GiverPresence here = inLevel(level, active == null ? null : active.villagerUuid());
        return here != UNLOADED_OR_UNKNOWN ? here : of(level.getServer(), active);
    }

    /** Where {@code active}'s giver is, searching every level the server has loaded. */
    public static GiverPresence of(MinecraftServer server, ActiveQuest active) {
        UUID uuid = active == null ? null : active.villagerUuid();
        if (server == null || uuid == null) {
            return UNLOADED_OR_UNKNOWN;
        }
        for (ServerLevel level : server.getAllLevels()) {
            GiverPresence found = inLevel(level, uuid);
            if (found != UNLOADED_OR_UNKNOWN) {
                return found;
            }
        }
        return UNLOADED_OR_UNKNOWN;
    }

    private static GiverPresence inLevel(ServerLevel level, UUID uuid) {
        if (level == null || uuid == null) {
            return UNLOADED_OR_UNKNOWN;
        }
        Entity giver = level.getEntity(uuid);
        if (giver == null) {
            return UNLOADED_OR_UNKNOWN;
        }
        return giver.isAlive() && !giver.isRemoved() ? PRESENT : KNOWN_DEAD;
    }

    /**
     * The whole policy, as a pure function: the same-profession fallback needs the config flag
     * <em>and</em> a giver who is not standing right there.
     *
     * @param allowedByConfig {@code allowTurnInToSameProfessionIfOriginalMissing}
     */
    public boolean permitsSameProfessionFallback(boolean allowedByConfig) {
        return allowedByConfig && this != PRESENT;
    }
}
