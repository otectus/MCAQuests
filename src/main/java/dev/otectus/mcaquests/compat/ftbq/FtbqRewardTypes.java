package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.otectus.mcaquests.McaQuests;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers the {@code mcaquests:} FTB Quests reward types (spec §16 table). Registration is
 * unconditional (never gated by config/side/server state, §2/§4.3) — {@link FtbqBootstrap#init()} only
 * runs at all iff FTB Quests is present, and by the time any client connects the type must already be
 * registered on both sides or {@code readNetDataFull} NPEs on an unrecognised type id (mirrors
 * {@link FtbqTaskTypes}'s reasoning, P1 report item 11).
 *
 * <p>Verified against {@code RewardTypes} (FTB-Quests {@code v2001.4.22},
 * {@code quest/reward/RewardTypes.java:15-16}): {@code register(ResourceLocation, RewardType.Provider,
 * Supplier<Icon>)} is backed by {@code Map.computeIfAbsent}, so a duplicate id is a silent no-op (first
 * registrant wins) rather than an overwrite or exception — each id below must stay unique. Icon choices
 * mirror the §15 task-type table precedent: {@code village_reputation} reuses the reputation task's
 * emerald, {@code hearts} reuses the hearts task's heart-of-the-sea, {@code grant_title} reuses the
 * title task's name tag — same underlying concept, task vs. reward, same icon.
 */
public final class FtbqRewardTypes {

    public static RewardType VILLAGE_REPUTATION;
    public static RewardType HEARTS;
    public static RewardType GRANT_TITLE;

    private FtbqRewardTypes() {
    }

    public static void register() {
        VILLAGE_REPUTATION = RewardTypes.register(id("village_reputation"), McaVillageReputationReward::new,
                () -> Icon.getIcon("minecraft:item/emerald"));
        HEARTS = RewardTypes.register(id("hearts"), McaHeartsReward::new,
                () -> Icon.getIcon("minecraft:item/heart_of_the_sea"));
        GRANT_TITLE = RewardTypes.register(id("grant_title"), McaGrantTitleReward::new,
                () -> Icon.getIcon("minecraft:item/name_tag"));
    }

    /** Number of {@code mcaquests:} reward types registered so far. Used only for the
     * {@code FtbqBootstrap} startup log line so it never needs a manual count bump. */
    public static int count() {
        return 3;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(McaQuests.MOD_ID, path);
    }
}
