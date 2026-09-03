package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the data a player must not lose across a save, a death or a dimension change, now that the
 * state travels as a NeoForge data attachment.
 *
 * <p>PORT: replaces {@code PlayerQuestDataProviderTest}. That test guarded a Forge-only invariant —
 * that {@code PlayerQuestDataProvider} hands out a <em>live</em> {@code LazyOptional} over the
 * <em>same</em> {@code PlayerQuestData} after {@code invalidate()}, which is how every player lost
 * their quests on death between 1.0.0 and 1.4.3.
 *
 * <ol>
 *   <li><b>What it guarded:</b> capability revival across {@code PlayerEvent.Clone}.</li>
 *   <li><b>Why it cannot apply:</b> capabilities and {@code LazyOptional} do not exist in NeoForge
 *       21.1, and the provider class was deleted in WP5. An attachment is a plain value on the entity,
 *       never invalidated and never handed out through an optional, and the death case is covered
 *       declaratively by {@code AttachmentType.Builder#copyOnDeath()} rather than by a clone handler
 *       this suite could exercise.</li>
 *   <li><b>What replaces it:</b> the invariant the old test was ultimately protecting — that no field
 *       is dropped when the data is written out and read back — asserted here field by field over
 *       {@code serializeNBT}/{@code deserializeNBT}, plus {@code copyFrom} (which {@code copyOnDeath}
 *       and the dimension-change handler both reduce to) and {@code isEmpty}.</li>
 * </ol>
 *
 * <p>Every field on {@link PlayerQuestData} is populated before the round trip, and every one is read
 * back: a field added to {@code save()} but forgotten in {@code load()} is exactly the silent data
 * loss this file exists to catch.
 */
class PlayerQuestDataAttachmentTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation QUEST = ResourceLocation.fromNamespaceAndPath("mcaquests", "test_quest");
    private static final ResourceLocation OTHER_QUEST = ResourceLocation.fromNamespaceAndPath("mcaquests", "other_quest");
    private static final ResourceLocation PROJECT = ResourceLocation.fromNamespaceAndPath("mcaquests", "test_project");
    private static final ResourceLocation TITLE = ResourceLocation.fromNamespaceAndPath("mcaquests", "village_friend");
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static final UUID VILLAGER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    /** A data blob with something in every one of the seven persisted fields. */
    private static PlayerQuestData populated() {
        PlayerQuestData data = new PlayerQuestData();

        ActiveQuest quest = ActiveQuest.create(QUEST, VILLAGER, Component.literal("Anna"),
                ResourceLocation.withDefaultNamespace("farmer"), OVERWORLD, 1234L, 2, null);
        quest.progress(0).add(3);
        data.add(quest);

        data.history().recordCompletion(OTHER_QUEST, VILLAGER);
        data.titles().grantGlobal(TITLE);
        data.titles().grantVillage(OVERWORLD, 7, TITLE);
        ProgressionStats.increment(data.stats().projectCompletions(), PROJECT, 3);
        data.offers().get(VILLAGER).redraw(
                List.of(new OfferSession.Slot(OTHER_QUEST, null, Component.literal("A word with you?"))),
                1000L, 1, 4242L);
        data.setTracked(quest);
        data.markMigratedFromForge();
        return data;
    }

    private static PlayerQuestData roundTrip(PlayerQuestData data) {
        // RegistryAccess.EMPTY: nothing under PlayerQuestData stores a registry-bound value, and the
        // attachment is serialised with whatever provider the level happens to hand it.
        CompoundTag tag = data.serializeNBT(RegistryAccess.EMPTY);
        PlayerQuestData read = new PlayerQuestData();
        read.deserializeNBT(RegistryAccess.EMPTY, tag);
        return read;
    }

    private static void assertFullyPopulated(PlayerQuestData read, String what) {
        assertEquals(1, read.activeCount(), what + ": active");
        assertTrue(read.hasActive(QUEST, VILLAGER), what + ": the active quest keeps its quest id and giver");
        assertEquals(3, read.find(QUEST, VILLAGER).orElseThrow().progress(0).count(),
                what + ": objective progress");
        assertEquals(1, read.history().completionCountByGiver(OTHER_QUEST, VILLAGER), what + ": history");
        assertTrue(read.titles().hasGlobal(TITLE), what + ": global titles");
        assertTrue(read.titles().hasVillage(OVERWORLD, 7, TITLE), what + ": per-village titles");
        assertEquals(3, ProgressionStats.count(read.stats().projectCompletions(), PROJECT), what + ": stats");
        assertEquals(1, read.offers().find(VILLAGER).orElseThrow().slots().size(), what + ": offers");
        assertEquals(QUEST, read.tracked().orElseThrow().questId(), what + ": tracked");
        assertTrue(read.migratedFromForge(), what + ": migrated_from_forge");
    }

    @Test
    @DisplayName("every persisted field survives serializeNBT/deserializeNBT")
    void fullRoundTrip() {
        assertFullyPopulated(roundTrip(populated()), "round trip");
    }

    @Test
    @DisplayName("a blank blob round-trips as blank, and does not invent a tracked quest")
    void emptyRoundTrip() {
        PlayerQuestData read = roundTrip(new PlayerQuestData());

        assertTrue(read.isEmpty(), "nothing was ever recorded, so nothing may come back");
        assertTrue(read.tracked().isEmpty(),
                "'tracked' is only written when something is tracked, so its absence must read as nothing");
        assertFalse(read.migratedFromForge(),
                "a natively written blob carries no migration marker; absent must read as false");
    }

    @Test
    @DisplayName("copyFrom carries every field, which is what copyOnDeath and a dimension change reduce to")
    void copyFromCarriesEveryField() {
        PlayerQuestData source = populated();
        PlayerQuestData target = new PlayerQuestData();

        target.copyFrom(source);

        assertFullyPopulated(target, "copyFrom");
        assertNotSame(source.history(), target.history(),
                "the sub-states are copied into, not aliased -- the dying player's data is discarded");
    }

    @Test
    @DisplayName("isEmpty answers about the value, since the attachment always exists")
    void isEmptyTracksEveryField() {
        assertTrue(new PlayerQuestData().isEmpty(), "a freshly created attachment has no data in it");
        assertFalse(populated().isEmpty(), "anything recorded at all makes it non-empty");

        // The marker alone is not player data: a player whose empty Forge file was imported must still
        // look empty, since isEmpty is what ForgeCapsMigration asks before it overwrites anything.
        PlayerQuestData markerOnly = new PlayerQuestData();
        markerOnly.markMigratedFromForge();
        assertTrue(markerOnly.isEmpty(), "the migration marker is bookkeeping, not player data");
    }
}
