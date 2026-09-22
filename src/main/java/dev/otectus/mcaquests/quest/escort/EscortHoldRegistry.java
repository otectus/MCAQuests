package dev.otectus.mcaquests.quest.escort;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.Optional;
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
 * <h2>Persistence (1.7.0)</h2>
 *
 * <p>Leases are saved with the world ({@link EscortHoldSavedData}), so a restart while a villager is
 * held no longer forgets who holds them. The values of {@code noAi} and {@code invulnerable} the hold
 * overwrote are written onto the villager itself under {@link #MARKER}, and a release restores them
 * rather than forcing both to false, so a villager another mod had frozen stays frozen. A villager
 * frozen by a hold from before 1.7.0 carries no marker; when its quest re-asserts the hold, a villager
 * that is already both motionless and invulnerable is taken to be that old hold rather than another
 * mod's choice, so releasing it unfreezes it. {@code /mcaquests escort release} frees one whose quest is
 * gone.
 */
public final class EscortHoldRegistry {

    /** Stands in for an owner a queued release does not know, so the map stays free of nulls. */
    public static final UUID NO_OWNER = new UUID(0L, 0L);

    /** Entity persistent-data key holding the flags a hold overwrote. */
    public static final String MARKER = "mcaquests_escort_hold";

    private static final String KEY_PRIOR_NO_AI = "prior_no_ai";
    private static final String KEY_PRIOR_INVULNERABLE = "prior_invulnerable";

    /**
     * One villager's hold.
     *
     * @param owner          the player whose escort holds the villager, or {@link #NO_OWNER}
     * @param releasePending the villager could not be reached when the hold ended, and is owed a release
     */
    public record Lease(UUID owner, boolean releasePending) {
    }

    /** Held or owed-a-release villager -> lease. */
    private static final Map<UUID, Lease> LEASES = new ConcurrentHashMap<>();

    /** The saved data backing the leases while a server runs; null in tests and between worlds. */
    private static volatile EscortHoldSavedData attached;

    private EscortHoldRegistry() {
    }

    // ------------------------------------------------------------------ lifecycle

    /** Loads the leases saved with this server's world. Server start. */
    public static void attach(MinecraftServer server) {
        LEASES.clear();
        attached = null;
        attached = EscortHoldSavedData.get(server);
    }

    /** Stops tracking; the leases stay in the save. Server stop. */
    public static void detach() {
        attached = null;
        LEASES.clear();
    }

    static void restore(Map<UUID, Lease> loaded) {
        LEASES.clear();
        LEASES.putAll(loaded);
    }

    static Map<UUID, Lease> snapshot() {
        return Map.copyOf(LEASES);
    }

    private static void changed() {
        EscortHoldSavedData data = attached;
        if (data != null) {
            data.setDirty();
        }
    }

    // ------------------------------------------------------------------ leases

    /** Records that {@code owner}'s escort is holding {@code villager}. Idempotent, and cheap when unchanged. */
    public static void hold(UUID villager, UUID owner) {
        if (villager == null || owner == null) {
            return;
        }
        Lease next = new Lease(owner, false);
        if (!next.equals(LEASES.put(villager, next))) {
            changed();
        }
    }

    /** Forgets a hold, and any release owed for it — the villager has just been released for real. */
    public static void release(UUID villager) {
        if (villager == null) {
            return;
        }
        if (LEASES.remove(villager) != null) {
            changed();
        }
    }

    /** Every villager {@code owner} is currently holding. A copy: callers release while iterating. */
    public static Set<UUID> heldBy(UUID owner) {
        if (owner == null) {
            return Set.of();
        }
        return LEASES.entrySet().stream()
                .filter(entry -> !entry.getValue().releasePending() && owner.equals(entry.getValue().owner()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Queues a release for a villager nothing can reach right now. */
    public static void enqueueRelease(UUID villager, UUID owner) {
        if (villager == null) {
            return;
        }
        LEASES.put(villager, new Lease(owner == null ? NO_OWNER : owner, true));
        changed();
    }

    /** Whether a release is waiting for {@code villager}. Read-only; {@link #claimRelease} consumes it. */
    public static boolean isReleasePending(UUID villager) {
        Lease lease = villager == null ? null : LEASES.get(villager);
        return lease != null && lease.releasePending();
    }

    /** The lease on {@code villager}, held or pending. */
    public static Optional<Lease> leaseOf(UUID villager) {
        return villager == null ? Optional.empty() : Optional.ofNullable(LEASES.get(villager));
    }

    /**
     * Takes the release owed to {@code villager}, if any — non-empty exactly once per queued release.
     *
     * @return the player who owed it, or empty when nothing was owed. {@link #NO_OWNER} when a release
     *         was owed by nobody in particular
     */
    public static Optional<UUID> claimRelease(UUID villager) {
        Lease lease = villager == null ? null : LEASES.get(villager);
        if (lease == null || !lease.releasePending()) {
            return Optional.empty();
        }
        LEASES.remove(villager);
        changed();
        return Optional.of(lease.owner());
    }

    /** Drops every lease. The tests; a running server uses {@link #detach}. */
    public static void clear() {
        LEASES.clear();
        changed();
    }

    // ------------------------------------------------------------------ the entity's own flags

    /**
     * Writes the flags a hold is about to overwrite onto the villager, once: a hold re-asserted every
     * tick keeps the values from before the first. A villager with no marker that is already both
     * motionless and invulnerable is a hold from before 1.7.0 re-asserted by its still-active quest, not
     * a choice another mod made, so its prior values are recorded as false.
     */
    public static void rememberPriorFlags(Mob villager) {
        CompoundTag data = villager.getPersistentData();
        if (data.contains(MARKER)) {
            return;
        }
        boolean legacyHold = villager.isNoAi() && villager.isInvulnerable();
        CompoundTag prior = new CompoundTag();
        prior.putBoolean(KEY_PRIOR_NO_AI, !legacyHold && villager.isNoAi());
        prior.putBoolean(KEY_PRIOR_INVULNERABLE, !legacyHold && villager.isInvulnerable());
        data.put(MARKER, prior);
    }

    /**
     * Restores the flags {@link #rememberPriorFlags} recorded, and removes the marker. With no marker the
     * villager is set moving and vulnerable, which is what every release did before 1.7.0.
     */
    public static void restorePriorFlags(Mob villager) {
        CompoundTag data = villager.getPersistentData();
        CompoundTag prior = data.getCompound(MARKER);
        villager.setInvulnerable(prior.getBoolean(KEY_PRIOR_INVULNERABLE));
        villager.setNoAi(prior.getBoolean(KEY_PRIOR_NO_AI));
        data.remove(MARKER);
    }

    /** Whether this villager carries a hold's marker, whatever the lease records say. */
    public static boolean isMarkedHeld(Mob villager) {
        return villager.getPersistentData().contains(MARKER);
    }
}
