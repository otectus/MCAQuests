package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import dev.otectus.mcaquests.McaQuests;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers the {@code mcaquests:} FTB Quests task types (spec §15.0 table). Registration is
 * unconditional (never gated by config/side/server state, §2/§4.3) — {@link FtbqBootstrap#init()}
 * only runs at all iff FTB Quests is present, and by the time any client connects the type must
 * already be registered on both sides or {@code readNetDataFull} NPEs on an unrecognised type id
 * (P1 report item 11).
 *
 * <p>Verified against {@code TaskTypes} (FTB-Quests {@code v2001.4.22},
 * {@code quest/task/TaskTypes.java:20-22,24-25}): {@code register(ResourceLocation, TaskType.Provider,
 * Supplier<Icon>)} is backed by {@code Map.computeIfAbsent}, so a duplicate id is a silent no-op
 * (first registrant wins) rather than an overwrite or exception — each id below must stay unique.
 * The icon-supplier idiom ({@code () -> Icon.getIcon("minecraft:item/...")}) mirrors every built-in
 * entry in that same file (e.g. {@code ITEM}, {@code XP}, {@code STAT}).
 */
public final class FtbqTaskTypes {

    public static TaskType MARRIED;
    public static TaskType QUEST_COMPLETED;

    private FtbqTaskTypes() {
    }

    public static void register() {
        MARRIED = TaskTypes.register(id("married"), McaMarriedTask::new,
                () -> Icon.getIcon("minecraft:item/gold_nugget"));
        QUEST_COMPLETED = TaskTypes.register(id("quest_completed"), McaQuestCompletedTask::new,
                () -> Icon.getIcon("minecraft:item/writable_book"));
    }

    /** Number of {@code mcaquests:} task types registered so far — grows with each M2.x task. Used
     * only for the {@code FtbqBootstrap} startup log line so it never needs a manual count bump. */
    public static int count() {
        return 2;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(McaQuests.MOD_ID, path);
    }
}
