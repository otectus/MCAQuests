package dev.otectus.mcaquests;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Forge common + client configuration (spec section 25). Only the options needed by the early
 * phases are wired today; the remainder are added as their subsystems land.
 */
public final class McaQuestsConfig {

    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    static {
        final Pair<Common, ForgeConfigSpec> common = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();

        final Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private McaQuestsConfig() {
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue enableDefaultQuestPack;
        public final ForgeConfigSpec.IntValue maxActiveQuestsPerPlayer;
        public final ForgeConfigSpec.IntValue maxActiveQuestsPerVillager;
        public final ForgeConfigSpec.IntValue offersPerVillager;
        public final ForgeConfigSpec.IntValue offerRefreshTicks;
        public final ForgeConfigSpec.IntValue defaultQuestCooldownTicks;
        public final ForgeConfigSpec.BooleanValue requireOriginalVillagerForTurnIn;
        public final ForgeConfigSpec.BooleanValue allowTurnInToSameProfessionIfOriginalMissing;
        public final ForgeConfigSpec.BooleanValue failQuestIfGiverDies;
        public final ForgeConfigSpec.BooleanValue allowCommandRewards;
        public final ForgeConfigSpec.BooleanValue allowLootTableRewards;
        public final ForgeConfigSpec.DoubleValue heartsRewardMultiplier;
        public final ForgeConfigSpec.IntValue minHeartsReward;
        public final ForgeConfigSpec.IntValue maxHeartsReward;
        public final ForgeConfigSpec.EnumValue<ProfessionMatchingMode> professionMatchingMode;
        public final ForgeConfigSpec.BooleanValue strictJsonValidation;
        public final ForgeConfigSpec.BooleanValue debugLogging;

        Common(ForgeConfigSpec.Builder b) {
            b.push("quests");
            enableDefaultQuestPack = b.comment("Ship and enable the built-in default quest pack.")
                    .define("enableDefaultQuestPack", true);
            maxActiveQuestsPerPlayer = b.comment("Maximum simultaneously-active MCA quests per player.")
                    .defineInRange("maxActiveQuestsPerPlayer", 10, 1, 1000);
            maxActiveQuestsPerVillager = b.comment("Maximum simultaneously-active quests per villager per player.")
                    .defineInRange("maxActiveQuestsPerVillager", 1, 1, 100);
            offersPerVillager = b.comment("How many quest offers a villager presents at once.")
                    .defineInRange("offersPerVillager", 3, 1, 10);
            offerRefreshTicks = b.comment("Ticks before a villager's cached offers reroll (24000 = 1 MC day).")
                    .defineInRange("offerRefreshTicks", 24000, 1, Integer.MAX_VALUE);
            defaultQuestCooldownTicks = b.comment("Default cooldown applied to quests that do not specify one.")
                    .defineInRange("defaultQuestCooldownTicks", 24000, 0, Integer.MAX_VALUE);
            b.pop();

            b.push("turn_in");
            requireOriginalVillagerForTurnIn = b.define("requireOriginalVillagerForTurnIn", true);
            allowTurnInToSameProfessionIfOriginalMissing = b.define("allowTurnInToSameProfessionIfOriginalMissing", false);
            failQuestIfGiverDies = b.define("failQuestIfGiverDies", false);
            b.pop();

            b.push("rewards");
            allowCommandRewards = b.comment("Command rewards are disabled by default for safety (spec section 26).")
                    .define("allowCommandRewards", false);
            allowLootTableRewards = b.define("allowLootTableRewards", true);
            heartsRewardMultiplier = b.defineInRange("heartsRewardMultiplier", 1.0, 0.0, 100.0);
            minHeartsReward = b.defineInRange("minHeartsReward", 0, -1000, 1000);
            maxHeartsReward = b.defineInRange("maxHeartsReward", 100, 0, 10000);
            b.pop();

            b.push("matching");
            professionMatchingMode = b.comment("How giver professions are matched: STRICT, NORMALIZED, or LOOSE.")
                    .defineEnum("professionMatchingMode", ProfessionMatchingMode.NORMALIZED);
            b.pop();

            b.push("debug");
            strictJsonValidation = b.comment("Treat any malformed/unknown quest JSON as a hard error.")
                    .define("strictJsonValidation", false);
            debugLogging = b.define("debugLogging", false);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue showQuestButtonInMcaMenu;
        public final ForgeConfigSpec.BooleanValue showQuestToasts;
        public final ForgeConfigSpec.BooleanValue showQuestTrackerHud;
        public final ForgeConfigSpec.BooleanValue playQuestSounds;
        public final ForgeConfigSpec.IntValue questTrackerMaxEntries;
        public final ForgeConfigSpec.EnumValue<HudAnchor> questTrackerAnchor;
        public final ForgeConfigSpec.IntValue questTrackerX;
        public final ForgeConfigSpec.IntValue questTrackerY;

        Client(ForgeConfigSpec.Builder b) {
            b.push("client");
            showQuestButtonInMcaMenu = b.comment("Inject the Quests button into MCA's villager interaction menu.")
                    .define("showQuestButtonInMcaMenu", true);
            showQuestToasts = b.define("showQuestToasts", true);
            showQuestTrackerHud = b.define("showQuestTrackerHud", true);
            playQuestSounds = b.define("playQuestSounds", true);
            questTrackerMaxEntries = b.defineInRange("questTrackerMaxEntries", 3, 1, 10);
            questTrackerAnchor = b.comment("Screen corner the quest tracker HUD anchors to: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT.")
                    .defineEnum("questTrackerAnchor", HudAnchor.TOP_LEFT);
            questTrackerX = b.comment("Quest tracker horizontal offset in pixels from its anchored corner.")
                    .defineInRange("questTrackerX", 4, 0, 10000);
            questTrackerY = b.comment("Quest tracker vertical offset in pixels from its anchored corner.")
                    .defineInRange("questTrackerY", 4, 0, 10000);
            b.pop();
        }
    }

    /** Profession matching strategy (spec section 12). */
    public enum ProfessionMatchingMode {
        STRICT,
        NORMALIZED,
        LOOSE
    }

    /** Screen corner the quest-tracker HUD anchors to (spec section 21). */
    public enum HudAnchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }
}
