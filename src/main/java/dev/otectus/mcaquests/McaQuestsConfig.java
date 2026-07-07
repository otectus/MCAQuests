package dev.otectus.mcaquests;

import dev.otectus.mcaquests.project.SponsorDeathBehavior;
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
        public final ForgeConfigSpec.BooleanValue followGiverAfterAccept;
        public final ForgeConfigSpec.DoubleValue leadVillagerSpeed;
        public final ForgeConfigSpec.BooleanValue highlightQuestTargets;
        public final ForgeConfigSpec.BooleanValue questChatMessages;
        public final ForgeConfigSpec.BooleanValue strictJsonValidation;
        public final ForgeConfigSpec.BooleanValue debugLogging;

        // Village projects (spec 0.4.0).
        public final ForgeConfigSpec.BooleanValue enableVillageProjects;
        public final ForgeConfigSpec.IntValue defaultScopeFallbackRadius;
        public final ForgeConfigSpec.EnumValue<SponsorDeathBehavior> defaultSponsorDeathBehavior;
        public final ForgeConfigSpec.BooleanValue oneSponsorPerProjectPerDay;
        public final ForgeConfigSpec.IntValue projectOffersPerVillager;
        public final ForgeConfigSpec.IntValue projectContributeMinIntervalTicks;
        public final ForgeConfigSpec.IntValue defaultPerPlayerContributionCap;
        public final ForgeConfigSpec.BooleanValue allowProjectCommandRewards;
        public final ForgeConfigSpec.IntValue maxConcurrentProjectsPerScope;

        public final ForgeConfigSpec.BooleanValue enableReputationTiers;

        // Living Village — situations (spec 0.8.0).
        public final ForgeConfigSpec.BooleanValue enableSituations;
        public final ForgeConfigSpec.IntValue maxConcurrentSituationsPerVillage;
        public final ForgeConfigSpec.IntValue situationGlobalCooldownTicks;
        public final ForgeConfigSpec.IntValue situationDetectionIntervalTicks;
        public final ForgeConfigSpec.IntValue maxSituationOffersPerMenu;
        public final ForgeConfigSpec.IntValue situationDefaultPriority;

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

            b.push("behavior");
            followGiverAfterAccept = b.comment(
                    "If true, a quest giver follows the player after they accept a quest (escort-style).",
                    "If false (default), accepting never makes the villager follow you, and an existing auto-follow is cleared.")
                    .define("followGiverAfterAccept", false);
            leadVillagerSpeed = b.comment(
                    "Walk-speed multiplier for a villager LEADING the player in a lead-style escort objective",
                    "(escort_entity with \"lead\": true). Lower keeps the villager near walking pace so the",
                    "player can stay close and guard it.")
                    .defineInRange("leadVillagerSpeed", 0.6, 0.1, 2.0);
            highlightQuestTargets = b.comment(
                    "If true (default), a villager that is the target of one of your active quests (the",
                    "recipient of a delivery, or the villager to heal/cure/escort/protect/defend) glows through",
                    "walls while it is loaded, so you can find it. Applied server-side; syncs to all clients.")
                    .define("highlightQuestTargets", true);
            questChatMessages = b.comment("Send a short chat confirmation when a quest is accepted or completed.")
                    .define("questChatMessages", true);
            b.pop();

            b.push("debug");
            strictJsonValidation = b.comment("Treat any malformed/unknown quest JSON as a hard error.")
                    .define("strictJsonValidation", false);
            debugLogging = b.define("debugLogging", false);
            b.pop();

            b.push("projects");
            enableVillageProjects = b.comment("Master switch for the shared village-projects system (0.4.0).")
                    .define("enableVillageProjects", true);
            defaultScopeFallbackRadius = b.comment(
                    "Block radius used to find/anchor a village when MCA village data is unavailable,",
                    "and the radius around a project anchor counted for in-village contributions.")
                    .defineInRange("defaultScopeFallbackRadius", 64, 8, 512);
            defaultSponsorDeathBehavior = b.comment(
                    "What happens to a project when its last sponsor dies, if the project does not specify:",
                    "FAIL, PAUSE, TRANSFER, or TURN_IN_TO_VILLAGE.")
                    .defineEnum("defaultSponsorDeathBehavior", SponsorDeathBehavior.PAUSE);
            oneSponsorPerProjectPerDay = b.comment(
                    "If true, only one deterministically-chosen villager per village surfaces a given",
                    "project each day, so the same project does not flood every eligible villager.")
                    .define("oneSponsorPerProjectPerDay", true);
            projectOffersPerVillager = b.comment("How many community projects a villager presents at once.")
                    .defineInRange("projectOffersPerVillager", 1, 0, 5);
            projectContributeMinIntervalTicks = b.comment("Minimum ticks between accepted contributions from one player (anti-spam).")
                    .defineInRange("projectContributeMinIntervalTicks", 5, 0, 200);
            defaultPerPlayerContributionCap = b.comment("Default per-player cap on a single project objective (0 = unlimited).")
                    .defineInRange("defaultPerPlayerContributionCap", 0, 0, Integer.MAX_VALUE);
            allowProjectCommandRewards = b.comment("Allow command rewards inside project phases (off by default for safety).")
                    .define("allowProjectCommandRewards", false);
            maxConcurrentProjectsPerScope = b.comment("Cap on simultaneously-active project instances sharing one scope identity.")
                    .defineInRange("maxConcurrentProjectsPerScope", 8, 1, 100);
            b.pop();

            b.push("progression");
            enableReputationTiers = b.comment(
                    "Master switch for reputation tiers, player titles, and the journal screen (0.7.0).",
                    "When off, the reputation_tier condition fails safe, tier-up toasts/titles are not granted,",
                    "and tier ladders are not loaded; existing village reputation still accrues unchanged.")
                    .define("enableReputationTiers", true);
            b.pop();

            b.push("situations");
            enableSituations = b.comment(
                    "Master switch for the Living Village situations system (0.8.0): emergent, world-driven,",
                    "time-limited quest offers opened by gameplay events (raids, deaths, infection, famine, ...).",
                    "When off, no situations are detected, opened, or surfaced; existing quests are unaffected.")
                    .define("enableSituations", true);
            maxConcurrentSituationsPerVillage = b.comment(
                    "Cap on simultaneously-open situations in one village. Excess detections are suppressed",
                    "(and logged). 0 disables the cap.")
                    .defineInRange("maxConcurrentSituationsPerVillage", 2, 0, 100);
            situationGlobalCooldownTicks = b.comment(
                    "Minimum ticks between any two situations opening in the same village (anti-spam).")
                    .defineInRange("situationGlobalCooldownTicks", 6000, 0, Integer.MAX_VALUE);
            situationDetectionIntervalTicks = b.comment(
                    "How often (ticks) the periodic detector scans villages for tick-driven situations",
                    "(famine, missing kin, nightfall). Event-driven triggers (raid/death/infection) are immediate.")
                    .defineInRange("situationDetectionIntervalTicks", 200, 20, Integer.MAX_VALUE);
            maxSituationOffersPerMenu = b.comment(
                    "Cap on how many situation offers a single villager surfaces at once (they compete with",
                    "static offers via the usual priority/weight shaping).")
                    .defineInRange("maxSituationOffersPerMenu", 2, 0, 10);
            situationDefaultPriority = b.comment(
                    "Default offer-priority tier for situation offers that do not set their own. Higher fills",
                    "menu slots first; situations default above standalone quests so the village's needs stand out.")
                    .defineInRange("situationDefaultPriority", 5, 0, 1000);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue showQuestButtonInMcaMenu;
        public final ForgeConfigSpec.BooleanValue showQuestToasts;
        public final ForgeConfigSpec.BooleanValue showQuestTrackerHud;
        public final ForgeConfigSpec.BooleanValue playQuestSounds;
        public final ForgeConfigSpec.IntValue questTrackerMaxEntries;
        public final ForgeConfigSpec.BooleanValue questTrackerBackground;
        public final ForgeConfigSpec.EnumValue<HudAnchor> questTrackerAnchor;
        public final ForgeConfigSpec.IntValue questTrackerX;
        public final ForgeConfigSpec.IntValue questTrackerY;
        public final ForgeConfigSpec.BooleanValue showProjectTrackerHud;
        public final ForgeConfigSpec.IntValue projectTrackerMaxEntries;
        public final ForgeConfigSpec.BooleanValue showSituationToast;

        Client(ForgeConfigSpec.Builder b) {
            b.push("client");
            showQuestButtonInMcaMenu = b.comment("Inject the Quests button into MCA's villager interaction menu.")
                    .define("showQuestButtonInMcaMenu", true);
            showQuestToasts = b.define("showQuestToasts", true);
            showQuestTrackerHud = b.define("showQuestTrackerHud", true);
            playQuestSounds = b.define("playQuestSounds", true);
            questTrackerMaxEntries = b.comment("How many quests the tracker HUD shows at once.")
                    .defineInRange("questTrackerMaxEntries", 5, 1, 15);
            questTrackerBackground = b.comment("Draw a translucent background behind the quest tracker HUD.")
                    .define("questTrackerBackground", true);
            questTrackerAnchor = b.comment("Screen corner the quest tracker HUD anchors to: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT.")
                    .defineEnum("questTrackerAnchor", HudAnchor.TOP_LEFT);
            questTrackerX = b.comment("Quest tracker horizontal offset in pixels from its anchored corner.")
                    .defineInRange("questTrackerX", 4, 0, 10000);
            questTrackerY = b.comment("Quest tracker vertical offset in pixels from its anchored corner.")
                    .defineInRange("questTrackerY", 4, 0, 10000);
            showProjectTrackerHud = b.comment("Show participating community projects in the HUD tracker.")
                    .define("showProjectTrackerHud", true);
            projectTrackerMaxEntries = b.comment("How many community projects the tracker HUD shows at once.")
                    .defineInRange("projectTrackerMaxEntries", 3, 1, 10);
            showSituationToast = b.comment("Show a toast when the village opens a new situation that needs help (0.8.0).")
                    .define("showSituationToast", true);
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
