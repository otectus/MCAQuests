package dev.otectus.mcaquests.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * All of a player's MCA quest state — active quests, history, titles, progression stats and the offers
 * each villager is currently showing them — held in a Forge capability and serialised to the player's NBT
 * (spec section 16). Server-authoritative; never trust a client copy.
 */
public final class PlayerQuestData {

    private final List<ActiveQuest> active = new ArrayList<>();
    private final QuestHistory history = new QuestHistory();
    private final PlayerTitles titles = new PlayerTitles();
    private final ProgressionStats stats = new ProgressionStats();
    private final OfferSessions offers = new OfferSessions();

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
    }
}
