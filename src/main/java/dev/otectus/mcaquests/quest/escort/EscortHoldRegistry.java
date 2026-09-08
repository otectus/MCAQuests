package dev.otectus.mcaquests.quest.escort;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is currently frozen for an escort, and who owes them a release.
 *
 * <p>A staged escort holds its escortee with {@code setNoAi(true)} and {@code setInvulnerable(true)}
 * until the player arrives. Both flags persist to entity NBT, so a hold that is never undone is a
 * villager standing invulnerable and motionless forever. The release used to hang off the quest
 * definition — abandoning a quest whose definition had been removed from the datapack skipped it
 * entirely — and off the escortee being loaded at that moment, which it need not be.
 *
 * <p>So the hold is recorded here as well as asserted on the entity: the abandon path can release
 * every villager a player is holding without consulting a definition, and one it cannot reach yet is
 * queued instead. {@code EscortHoldEvents} drains that queue as villagers load.
 *
 * <h2>Limitations</h2>
 *
 * <p><b>Session-scoped and unpersisted.</b> A restart while a villager is held still leaves it frozen:
 * the map is gone and only a still-active quest re-asserting the hold will heal it. The prior values
 * of {@code noAi} and {@code invulnerable} are not captured either, so a release always sets both to
 * false rather than restoring whatever another mod had set. A persisted lease that survives a restart
 * and remembers what it overwrote is future work.
 */
public final class EscortHoldRegistry {

    /** Stands in for an owner a queued release does not know, so the map stays free of nulls. */
    public static final UUID NO_OWNER = new UUID(0L, 0L);

    /** Held villager -> the player whose escort is holding them. */
    private static final Map<UUID, UUID> HELD = new ConcurrentHashMap<>();

    /**
     * Villagers owed a release that could not be given because they were not loaded, and the player
     * who owed it. The owner is kept so the release can also take the villager out of follow, which is
     * a state about a specific player and cannot be undone without naming one.
     */
    private static final Map<UUID, UUID> PENDING = new ConcurrentHashMap<>();

    private EscortHoldRegistry() {
    }

    /** Records that {@code owner}'s escort is holding {@code villager}. Idempotent. */
    public static void hold(UUID villager, UUID owner) {
        if (villager == null || owner == null) {
            return;
        }
        HELD.put(villager, owner);
    }

    /** Forgets a hold, and any release owed for it — the villager has just been released for real. */
    public static void release(UUID villager) {
        if (villager == null) {
            return;
        }
        HELD.remove(villager);
        PENDING.remove(villager);
    }

    /** Every villager {@code owner} is currently holding. A copy: callers release while iterating. */
    public static Set<UUID> heldBy(UUID owner) {
        if (owner == null) {
            return Set.of();
        }
        return HELD.entrySet().stream()
                .filter(entry -> owner.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Queues a release for a villager nothing can reach right now, and drops the hold record. */
    public static void enqueueRelease(UUID villager, UUID owner) {
        if (villager == null) {
            return;
        }
        HELD.remove(villager);
        PENDING.put(villager, owner == null ? NO_OWNER : owner);
    }

    /** Whether a release is waiting for {@code villager}. Read-only; {@link #claimRelease} consumes it. */
    public static boolean isReleasePending(UUID villager) {
        return villager != null && PENDING.containsKey(villager);
    }

    /**
     * Takes the release owed to {@code villager}, if any — non-empty exactly once per queued release.
     *
     * @return the player who owed it, or empty when nothing was owed. {@link #NO_OWNER} when a release
     *         was owed by nobody in particular
     */
    public static java.util.Optional<UUID> claimRelease(UUID villager) {
        return villager == null ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(PENDING.remove(villager));
    }

    /** Drops every hold and every queued release. Server shutdown, and the tests. */
    public static void clear() {
        HELD.clear();
        PENDING.clear();
    }
}
