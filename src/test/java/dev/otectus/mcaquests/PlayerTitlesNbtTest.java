package dev.otectus.mcaquests;

import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.PlayerTitles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registry-free NBT round-trips for player titles (0.7.0), including the legacy/empty case. */
class PlayerTitlesNbtTest {

    private static final ResourceLocation GLOBAL = new ResourceLocation("mcaquests", "wandering_helper");
    private static final ResourceLocation VILLAGE_TITLE = new ResourceLocation("mcaquests", "honored_of_village");
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");

    @Test
    void titlesRoundTrip() {
        PlayerTitles titles = new PlayerTitles();
        assertTrue(titles.grantGlobal(GLOBAL));
        assertFalse(titles.grantGlobal(GLOBAL), "second grant is a no-op");
        assertTrue(titles.grantVillage(OVERWORLD, 7, VILLAGE_TITLE));

        PlayerTitles loaded = new PlayerTitles();
        loaded.load(titles.save());

        assertTrue(loaded.hasGlobal(GLOBAL));
        assertTrue(loaded.hasVillage(OVERWORLD, 7, VILLAGE_TITLE));
        assertFalse(loaded.hasVillage(OVERWORLD, 8, VILLAGE_TITLE));
    }

    /** MCA allocates village ids per level, so id 3 in two dimensions is two different villages. */
    @Test
    void villagesWithTheSameIdInDifferentDimensionsDoNotCollide() {
        PlayerTitles titles = new PlayerTitles();
        assertTrue(titles.grantVillage(OVERWORLD, 3, VILLAGE_TITLE));
        assertFalse(titles.hasVillage(NETHER, 3, VILLAGE_TITLE),
                "a nether village sharing the numeric id holds no overworld titles");

        PlayerTitles loaded = new PlayerTitles();
        loaded.load(titles.save());
        assertTrue(loaded.hasVillage(OVERWORLD, 3, VILLAGE_TITLE));
        assertFalse(loaded.hasVillage(NETHER, 3, VILLAGE_TITLE));
        assertEquals(java.util.Set.of(3), loaded.villageIdsIn(OVERWORLD));
        assertTrue(loaded.villageIdsIn(NETHER).isEmpty());
    }

    /** §32.2's assumption, applied to titles: a bare-integer key from an old save means the overworld. */
    @Test
    void aLegacyBareIntegerVillageKeyLoadsAsTheOverworld() {
        CompoundTag tag = new CompoundTag();
        tag.put("global", new ListTag());
        CompoundTag villages = new CompoundTag();
        ListTag list = new ListTag();
        list.add(StringTag.valueOf(VILLAGE_TITLE.toString()));
        villages.put("3", list); // pre-dimension-aware key shape
        tag.put("villages", villages);

        PlayerTitles titles = new PlayerTitles();
        titles.load(tag);
        assertTrue(titles.hasVillage(OVERWORLD, 3, VILLAGE_TITLE));
        assertFalse(titles.hasVillage(NETHER, 3, VILLAGE_TITLE));
    }

    @Test
    void emptyCompoundLoadsAsEmpty() {
        PlayerTitles titles = new PlayerTitles();
        titles.load(new CompoundTag());
        assertTrue(titles.isEmpty());
    }

    @Test
    void playerQuestDataRoundTripWithAndWithoutTitles() {
        PlayerQuestData withTitles = new PlayerQuestData();
        withTitles.titles().grantGlobal(GLOBAL);
        withTitles.titles().grantVillage(OVERWORLD, 3, VILLAGE_TITLE);

        PlayerQuestData reloaded = new PlayerQuestData();
        reloaded.load(withTitles.save());
        assertTrue(reloaded.titles().hasGlobal(GLOBAL));
        assertTrue(reloaded.titles().hasVillage(OVERWORLD, 3, VILLAGE_TITLE));

        // A pre-0.7.0 save with no "titles" tag must load cleanly as empty.
        PlayerQuestData legacy = new PlayerQuestData();
        CompoundTag legacyTag = new CompoundTag();
        legacy.load(legacyTag);
        assertTrue(legacy.titles().isEmpty());
    }

    @Test
    void copyFromCopiesTitles() {
        PlayerQuestData source = new PlayerQuestData();
        source.titles().grantGlobal(GLOBAL);
        source.titles().grantVillage(OVERWORLD, 5, VILLAGE_TITLE);

        PlayerQuestData dest = new PlayerQuestData();
        dest.copyFrom(source);
        assertTrue(dest.titles().hasGlobal(GLOBAL));
        assertTrue(dest.titles().hasVillage(OVERWORLD, 5, VILLAGE_TITLE));

        // Mutating the copy must not affect the source (deep copy of village sets).
        dest.titles().grantVillage(OVERWORLD, 5, new ResourceLocation("mcaquests", "other"));
        assertEquals(1, source.titles().forVillage(OVERWORLD, 5).size());
    }
}