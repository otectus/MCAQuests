package dev.otectus.mcaquests.state;

import dev.otectus.mcaquests.project.ProjectScope;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.quest.situation.state.SituationSavedData;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.OptionalInt;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CorruptSaveRecoveryTest {
    static { TestBootstrap.ensureBootstrapped(); }

    private static ActiveQuest quest() {
        return ActiveQuest.create(new ResourceLocation("test", "quest"), UUID.randomUUID(),
                Component.literal("Giver"), null, new ResourceLocation("minecraft", "overworld"), 0, 1, null);
    }

    @Test void malformedActiveSiblingIsPreservedWithoutLosingGoodQuest() {
        ActiveQuest good = quest();
        CompoundTag broken = good.save();
        broken.putString("quest", "INVALID ID");
        ListTag entries = new ListTag();
        entries.add(broken);
        entries.add(good.save());
        CompoundTag source = new CompoundTag();
        source.put("active", entries);
        PlayerQuestData loaded = new PlayerQuestData();
        loaded.load(source);
        assertEquals(1, loaded.activeCount());
        assertTrue(loaded.save().getList("active", Tag.TAG_COMPOUND).contains(broken));
    }

    @Test void malformedWorldInstancesDoNotAbortTheWorldStore() {
        CompoundTag broken = new CompoundTag();
        broken.putString("project", "INVALID ID");
        ListTag entries = new ListTag();
        entries.add(broken);
        ProjectState good = new ProjectState(new ResourceLocation("test", "project"), ProjectScope.VILLAGE,
                "v:1", new ResourceLocation("minecraft", "overworld"), BlockPos.ZERO, OptionalInt.of(1), 0, 1);
        entries.add(good.save());
        CompoundTag source = new CompoundTag();
        source.put("instances", entries);
        ProjectSavedData loaded = ProjectSavedData.load(source);
        assertEquals(1, loaded.allInstances().size());
        assertTrue(loaded.save(new CompoundTag()).getList("instances", Tag.TAG_COMPOUND).contains(broken));
        ListTag situationEntries = new ListTag();
        situationEntries.add(broken);
        source.put("instances", situationEntries);
        assertTrue(SituationSavedData.load(source).save(new CompoundTag())
                .getList("instances", Tag.TAG_COMPOUND).contains(broken));
    }

    @Test void copyingForRespawnDoesNotShareMutableProgress() {
        ActiveQuest quest = quest();
        PlayerQuestData old = new PlayerQuestData();
        old.add(quest);
        PlayerQuestData fresh = new PlayerQuestData();
        fresh.copyFrom(old);
        fresh.active().get(0).progress(0).add(1);
        assertEquals(0, old.active().get(0).progress(0).count());
        assertEquals(1, fresh.active().get(0).progress(0).count());
    }

    @Test void unrecognizedPendingRewardsAreRetainedForRecovery() {
        CompoundTag unknown = new CompoundTag();
        unknown.putString("kind", "future_reward");
        ListTag entries = new ListTag();
        entries.add(unknown);
        UUID player = UUID.randomUUID();
        CompoundTag pending = new CompoundTag();
        pending.put(player.toString(), entries);
        CompoundTag source = new CompoundTag();
        source.put("pending", pending);
        ProjectSavedData loaded = ProjectSavedData.load(source);
        assertTrue(loaded.drainPending(player).isEmpty());
        assertEquals(entries, loaded.save(new CompoundTag()).getCompound("pending")
                .getList(player.toString(), Tag.TAG_COMPOUND));
    }
}
