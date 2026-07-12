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
    public static TaskType CHAIN_COMPLETED;
    public static TaskType REPUTATION;
    public static TaskType REPUTATION_TIER;
    public static TaskType TITLE;
    public static TaskType PROJECT_COMPLETED;
    public static TaskType PROJECT_CONTRIBUTION;
    public static TaskType SITUATION_RESOLVED;
    public static TaskType HEARTS;

    private FtbqTaskTypes() {
    }

    public static void register() {
        MARRIED = TaskTypes.register(id("married"), McaMarriedTask::new,
                () -> Icon.getIcon("minecraft:item/gold_nugget"));
        QUEST_COMPLETED = TaskTypes.register(id("quest_completed"), McaQuestCompletedTask::new,
                () -> Icon.getIcon("minecraft:item/writable_book"));
        CHAIN_COMPLETED = TaskTypes.register(id("chain_completed"), McaChainCompletedTask::new,
                () -> Icon.getIcon("minecraft:item/lead"));
        REPUTATION = TaskTypes.register(id("reputation"), McaReputationTask::new,
                () -> Icon.getIcon("minecraft:item/emerald"));
        REPUTATION_TIER = TaskTypes.register(id("reputation_tier"), McaReputationTierTask::new,
                () -> Icon.getIcon("minecraft:item/golden_apple"));
        TITLE = TaskTypes.register(id("title"), McaTitleTask::new,
                () -> Icon.getIcon("minecraft:item/name_tag"));
        PROJECT_COMPLETED = TaskTypes.register(id("project_completed"), McaProjectCompletedTask::new,
                () -> Icon.getIcon("minecraft:item/bell"));
        PROJECT_CONTRIBUTION = TaskTypes.register(id("project_contribution"), McaProjectContributionTask::new,
                () -> Icon.getIcon("minecraft:item/bundle"));
        // "clock_00" is a real vanilla texture (assets/minecraft/textures/item/clock_00.png, one of the
        // clock's 64 baked animation frames), confirmed present in the 1.20.1 client-extra jar — so
        // Icon.getIcon's AtlasSpriteIcon path resolves it fine, just as a single static frame rather than
        // the ticking clock animation. Kept as the spec suggests rather than falling back to plain
        // "minecraft:item/clock" (which is not a stitched atlas sprite at all — clock/compass render via
        // vanilla's dynamic model overrides, not a plain item texture — so it would be the wrong swap).
        SITUATION_RESOLVED = TaskTypes.register(id("situation_resolved"), McaSituationResolvedTask::new,
                () -> Icon.getIcon("minecraft:item/clock_00"));
        HEARTS = TaskTypes.register(id("hearts"), McaHeartsTask::new,
                () -> Icon.getIcon("minecraft:item/heart_of_the_sea"));
    }

    /** Number of {@code mcaquests:} task types registered so far — grows with each M2.x task. Used
     * only for the {@code FtbqBootstrap} startup log line so it never needs a manual count bump. */
    public static int count() {
        return 10;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(McaQuests.MOD_ID, path);
    }
}
