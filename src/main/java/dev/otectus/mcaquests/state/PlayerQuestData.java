package dev.otectus.mcaquests.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All of a player's MCA quest state — active quests, history, titles, progression stats and the offers
 * each villager is currently showing them — held in a data attachment and serialised to the player's NBT
 * (spec section 15). Server-authoritative; never trust a client copy.
 */
public final class PlayerQuestData implements INBTSerializable<CompoundTag> {

    private final List<ActiveQuest> active = new ArrayList<>();
    private final QuestHistory history = new QuestHistory();
    private final PlayerTitles titles = new PlayerTitles();
    private final ProgressionStats stats = new ProgressionStats();
    private final OfferSessions offers = new OfferSessions();

    /** The quest the marker, the guidance line and the villager outline are all about. */
    private TrackedQuest tracked;

    /** True once {@link ForgeCapsMigration} has pulled this player's 1.20.1 Forge capability data in. */
    private boolean migratedFromForge;

    public List<ActiveQuest> active() {
        return active;
    }

    public QuestHistory history() {
        return history;
    }

    public PlayerTitles titles() {
        return titles;
    }

    public ProgressionStats stats() {
        return stats;
    }

    /**
     * What each villager is currently offering this player, and what they have already turned down.
     *
     * <p>Lives with the player rather than the villager because an offer set is a fact about a pair: two
     * players talking to the same villager see, and refuse, different things.
     */
    public OfferSessions offers() {
        return offers;
    }

    /**
     * The quest this player is following, if it is still active.
     *
     * <p>Filtered against {@link #active} on every read rather than cleared when a quest ends, so no
     * caller has to remember to tidy up at the moment a quest completes, fails, is abandoned, or
     * disappears in a datapack reload. A dangling reference simply stops being an answer.
     */
    public Optional<TrackedQuest> tracked() {
        if (tracked == null) {
            return Optional.empty();
        }
        return active.stream().anyMatch(tracked::matches) ? Optional.of(tracked) : Optional.empty();
    }

    /** The active quest this player is following, if any. */
    public Optional<ActiveQuest> trackedQuest() {
        return tracked().flatMap(ref -> active.stream().filter(ref::matches).findFirst());
    }

    /** Follows {@code quest}; pass {@code null} to follow nothing. */
    public void setTracked(ActiveQuest quest) {
        tracked = quest == null ? null : new TrackedQuest(quest.questId(), quest.villagerUuid());
    }

    /** Follows {@code quest} only if nothing valid is being followed already. */
    public void trackIfNothingTracked(ActiveQuest quest) {
        if (tracked().isEmpty()) {
            setTracked(quest);
        }
    }

    /** True when {@code quest} is the one being followed. */
    public boolean isTracked(ActiveQuest quest) {
        return tracked().map(ref -> ref.matches(quest)).orElse(false);
    }

    public int activeCount() {
        return active.size();
    }

    public Optional<ActiveQuest> find(ResourceLocation questId, UUID villager) {
        return active.stream()
                .filter(q -> q.questId().equals(questId) && q.villagerUuid().equals(villager))
                .findFirst();
    }

    public List<ActiveQuest> byVillager(UUID villager) {
        return active.stream().filter(q -> q.villagerUuid().equals(villager)).toList();
    }

    public boolean hasActive(ResourceLocation questId, UUID villager) {
        return find(questId, villager).isPresent();
    }

    /**
     * True when this quest is active with <em>any</em> villager. A quest with no chain is one job, and
     * holding it from two givers at once used to pay it twice: the kill/craft/break events credit every
     * active copy, so one set of kills completed both. Arcs stay per-villager (see
     * {@code OfferFilters.alreadyActive}), which is why this is a second question rather than a change
     * to the one above.
     */
    public boolean hasActive(ResourceLocation questId) {
        return active.stream().anyMatch(q -> q.questId().equals(questId));
    }

    public void add(ActiveQuest quest) {
        active.add(quest);
    }

    public void remove(ActiveQuest quest) {
        active.remove(quest);
    }

    public void copyFrom(PlayerQuestData other) {
        active.clear();
        active.addAll(other.active);
        history.copyFrom(other.history);
        titles.copyFrom(other.titles);
        stats.copyFrom(other.stats);
        offers.copyFrom(other.offers);
        tracked = other.tracked;
        migratedFromForge = other.migratedFromForge;
    }

    /**
     * True when nothing has ever been recorded for this player.
     *
     * <p>The attachment is created on first read, so "has no data" is a question about the value, not
     * about whether it exists. {@link ForgeCapsMigration} asks it to decide whether importing a legacy
     * Forge blob could overwrite anything.
     */
    public boolean isEmpty() {
        return active.isEmpty()
                && history.isEmpty()
                && titles.isEmpty()
                && stats.isEmpty()
                && offers.isEmpty()
                && tracked == null;
    }

    /** True when this state came from a 1.20.1 Forge player file rather than being written natively. */
    public boolean migratedFromForge() {
        return migratedFromForge;
    }

    /** Records that the legacy import has run, so it never runs again for this player. */
    public void markMigratedFromForge() {
        this.migratedFromForge = true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (ActiveQuest quest : active) {
            list.add(quest.save());
        }
        tag.put("active", list);
        tag.put("history", history.save());
        tag.put("titles", titles.save());
        tag.put("stats", stats.save());
        tag.put("offers", offers.save());
        // Written only when something is tracked, so a save that never used the feature is byte-for-byte
        // what it was — the same discipline ActiveQuest applies to its own optional fields.
        if (tracked != null) {
            tag.put("tracked", tracked.save());
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        active.clear();
        ListTag list = tag.getList("active", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            active.add(ActiveQuest.load(list.getCompound(i)));
        }
        history.load(tag.getCompound("history"));
        titles.load(tag.getCompound("titles")); // absent on pre-0.7.0 saves -> empty
        stats.load(tag.getCompound("stats")); // absent on pre-1.0.0 saves -> empty
        offers.load(tag.getCompound("offers")); // absent on pre-1.4.3 saves -> empty, so offers redraw
        // Absent on pre-1.5.0 saves -> nothing tracked, and the next quest accepted picks itself up.
        tracked = TrackedQuest.load(tag.getCompound("tracked")).orElse(null);
    }

    /**
     * PORT: the attachment serialiser, wrapping the unchanged {@link #save()} so the 1.20.1 payload is
     * written byte-for-byte and only the migration marker is added around it. {@code provider} is
     * unused — nothing here stores registry-bound data; see {@link NbtComponents} for the one place a
     * 1.21 API wanted a lookup.
     */
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = save();
        tag.putBoolean("migrated_from_forge", migratedFromForge);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        load(tag);
        migratedFromForge = tag.getBoolean("migrated_from_forge"); // absent on Forge-era saves -> false
    }
}
