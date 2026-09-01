package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every villager's current offer set for one player, keyed by villager.
 *
 * <p>Held on {@link PlayerQuestData} beside history, titles and stats, and saved with them, so a decline
 * survives a menu close, a relog, a dimension change, a death and a server restart. That durability is
 * the point: a decision the world forgets the moment you walk away is not a decision.
 *
 * <p>Bounded on both axes. Refusals inside a session are dropped once they lapse, and whole sessions for
 * villagers the player has not spoken to in a long time are dropped on save — otherwise a player who has
 * greeted a thousand villagers would carry a thousand remembered menus in their NBT forever.
 */
public final class OfferSessions {

    /**
     * How many refresh windows a session may sit untouched before it is discarded.
     *
     * <p>Generous on purpose. A session that is pruned too eagerly costs the player nothing but a reroll,
     * yet a player who leaves a village for a few days and comes back should find the same offers waiting
     * if the refresh window has not elapsed. Eight windows is well past the point where the session would
     * have been redrawn on sight anyway.
     */
    private static final int STALE_WINDOWS = 8;

    private final Map<UUID, OfferSession> byVillager = new LinkedHashMap<>();

    /** The remembered offers for this villager, if there are any. */
    public Optional<OfferSession> find(UUID villagerUuid) {
        return Optional.ofNullable(byVillager.get(villagerUuid));
    }

    /** The remembered offers for this villager, creating an empty set if there are none. */
    public OfferSession get(UUID villagerUuid) {
        return byVillager.computeIfAbsent(villagerUuid, OfferSession::new);
    }

    public void remove(UUID villagerUuid) {
        byVillager.remove(villagerUuid);
    }

    public int size() {
        return byVillager.size();
    }

    /** Every remembered set, for the debug command. */
    public Map<UUID, OfferSession> view() {
        return Map.copyOf(byVillager);
    }

    /**
     * Drops lapsed refusals everywhere, and whole sessions nobody has looked at in
     * {@value #STALE_WINDOWS} refresh windows.
     *
     * <p>A {@code refreshTicks} of zero or less would make the age test meaningless, so it is treated as
     * "prune nothing" rather than "prune everything".
     */
    public void prune(long now, int refreshTicks) {
        byVillager.values().forEach(session -> session.pruneDeclines(now));
        if (refreshTicks <= 0) {
            return;
        }
        long horizon = (long) refreshTicks * STALE_WINDOWS;
        byVillager.entrySet().removeIf(entry -> now - entry.getValue().refreshedAtGameTime() > horizon);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        byVillager.values().forEach(session -> list.add(session.save()));
        tag.put("sessions", list);
        return tag;
    }

    /** Reads the store back. An absent or empty compound is an empty store — every pre-1.4.3 save. */
    public void load(CompoundTag tag) {
        byVillager.clear();
        ListTag list = tag.getList("sessions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            OfferSession session = OfferSession.load(list.getCompound(i));
            byVillager.put(session.villagerUuid(), session);
        }
    }

    public void copyFrom(OfferSessions other) {
        byVillager.clear();
        other.byVillager.forEach((villager, session) -> {
            OfferSession copy = new OfferSession(villager);
            copy.copyFrom(session);
            byVillager.put(villager, copy);
        });
    }
}
