package dev.otectus.mcaquests;

import dev.otectus.mcaquests.project.SponsorDeathBehavior;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Forge common + client configuration (spec section 25). Only the options needed by the early
 * phases are wired today; the remainder are added as their subsystems land.
 */
public final class McaQuestsConfig {

    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;
    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        final Pair<Common, ModConfigSpec> common = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();

        final Pair<Client, ModConfigSpec> client = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private McaQuestsConfig() {
    }

    public static final class Common {
        public final ModConfigSpec.BooleanValue enableDefaultQuestPack;
        public final ModConfigSpec.IntValue maxActiveQuestsPerPlayer;
        public final ModConfigSpec.IntValue maxActiveQuestsPerVillager;
        public final ModConfigSpec.IntValue offersPerVillager;
        public final ModConfigSpec.IntValue offerRefreshTicks;
        public final ModConfigSpec.IntValue defaultQuestCooldownTicks;
        public final ModConfigSpec.IntValue declineCooldownTicks;
        public final ModConfigSpec.BooleanValue declineRefillsSlot;
        public final ModConfigSpec.BooleanValue requireOriginalVillagerForTurnIn;
        public final ModConfigSpec.BooleanValue allowTurnInToSameProfessionIfOriginalMissing;
        public final ModConfigSpec.BooleanValue failQuestIfGiverDies;
        public final ModConfigSpec.BooleanValue allowCommandRewards;
        public final ModConfigSpec.BooleanValue allowLootTableRewards;
        public final ModConfigSpec.DoubleValue heartsRewardMultiplier;
        public final ModConfigSpec.IntValue minHeartsReward;
        public final ModConfigSpec.IntValue maxHeartsReward;
        public final ModConfigSpec.EnumValue<ProfessionMatchingMode> professionMatchingMode;

        // Semantic currency + reward scaling (spec 1.1.0).
        public final ModConfigSpec.DoubleValue currencyRewardMultiplier;
        public final ModConfigSpec.DoubleValue xpRewardMultiplier;
        public final ModConfigSpec.EnumValue<CurrencyProviderMode> currencyProvider;
        public final ModConfigSpec.ConfigValue<String> customCurrencyItem;
        public final ModConfigSpec.ConfigValue<String> numismaticsCurrencyItem;
        public final ModConfigSpec.EnumValue<CurrencyFallback> currencyFallback;
        public final ModConfigSpec.IntValue easyCurrencyMin;
        public final ModConfigSpec.IntValue easyCurrencyMax;
        public final ModConfigSpec.IntValue mediumCurrencyMin;
        public final ModConfigSpec.IntValue mediumCurrencyMax;
        public final ModConfigSpec.IntValue hardCurrencyMin;
        public final ModConfigSpec.IntValue hardCurrencyMax;
        public final ModConfigSpec.IntValue easyQuestReputation;
        public final ModConfigSpec.IntValue mediumQuestReputation;
        public final ModConfigSpec.IntValue hardQuestReputation;
        public final ModConfigSpec.BooleanValue followGiverAfterAccept;
        public final ModConfigSpec.DoubleValue leadVillagerSpeed;
        public final ModConfigSpec.IntValue minEscortJourney;
        public final ModConfigSpec.BooleanValue highlightQuestTargets;
        public final ModConfigSpec.BooleanValue highlightUsesGlowingEffect;
        public final ModConfigSpec.BooleanValue highlightAllActiveQuests;
        public final ModConfigSpec.IntValue guidanceSearchIntervalTicks;
        public final ModConfigSpec.IntValue guidanceSearchesPerPass;
        public final ModConfigSpec.BooleanValue autoTrackNewQuests;
        public final ModConfigSpec.BooleanValue questChatMessages;
        public final ModConfigSpec.BooleanValue strictJsonValidation;
        public final ModConfigSpec.BooleanValue debugLogging;

        // Village projects (spec 0.4.0).
        public final ModConfigSpec.BooleanValue enableVillageProjects;
        public final ModConfigSpec.IntValue defaultScopeFallbackRadius;
        public final ModConfigSpec.EnumValue<SponsorDeathBehavior> defaultSponsorDeathBehavior;
        public final ModConfigSpec.BooleanValue oneSponsorPerProjectPerDay;
        public final ModConfigSpec.IntValue projectOffersPerVillager;
        public final ModConfigSpec.IntValue projectContributeMinIntervalTicks;
        public final ModConfigSpec.IntValue defaultPerPlayerContributionCap;
        public final ModConfigSpec.BooleanValue allowProjectCommandRewards;
        public final ModConfigSpec.IntValue maxConcurrentProjectsPerScope;

        public final ModConfigSpec.BooleanValue enableReputationTiers;

        // Living Village — situations (spec 0.8.0).
        public final ModConfigSpec.BooleanValue enableSituations;
        public final ModConfigSpec.IntValue maxConcurrentSituationsPerVillage;
        public final ModConfigSpec.IntValue situationGlobalCooldownTicks;
        public final ModConfigSpec.IntValue situationDetectionIntervalTicks;
        public final ModConfigSpec.IntValue maxSituationOffersPerMenu;
        public final ModConfigSpec.IntValue situationDefaultPriority;

        // FTB Quests optional integration (spec section 10/13, v1.0.0).
        public final ModConfigSpec.BooleanValue enableFtbQuestsIntegration;
        public final ModConfigSpec.IntValue ftbqStatePollIntervalTicks;
        public final ModConfigSpec.DoubleValue ftbqHeartsScanRadius;
        public final ModConfigSpec.BooleanValue allowFtbqProgressRewards;
        public final ModConfigSpec.BooleanValue syncFtbqEditorIds;

        // Townstead optional integration (Townstead spec section 11, v1.4.0).
        public final ModConfigSpec.BooleanValue townsteadEnabled;
        public final ModConfigSpec.BooleanValue townsteadContentEnabled;
        public final ModConfigSpec.BooleanValue townsteadReactionsEnabled;
        public final ModConfigSpec.BooleanValue townsteadNeedRewardsEnabled;
        public final ModConfigSpec.BooleanValue townsteadProfessionXpRewardsEnabled;
        public final ModConfigSpec.BooleanValue townsteadSkillRewardsEnabled;
        public final ModConfigSpec.BooleanValue townsteadAllowUncappedProfessionXp;
        public final ModConfigSpec.BooleanValue townsteadRewardFailureBlocksCompletion;
        public final ModConfigSpec.IntValue townsteadPollIntervalTicks;
        public final ModConfigSpec.IntValue townsteadProjectPollIntervalTicks;
        public final ModConfigSpec.IntValue townsteadMaxVillagersPerPass;
        public final ModConfigSpec.IntValue townsteadMaxVillagesPerPass;
        public final ModConfigSpec.IntValue townsteadNeedCrisisHysteresis;
        public final ModConfigSpec.BooleanValue townsteadDebugBindingLogs;

        /**
         * Per-theme switches for the bundled Townstead content (spec 5.10). All default true and all
         * are subordinate to {@link #townsteadContentEnabled}: turning the master off hides everything
         * regardless of these, so a server owner who wants none of it still only has one switch to find.
         *
         * <p>They exist because "Townstead content" is not one thing. A server that loves the needs and
         * schedule quests may have no interest in the spirit and building ones, and before this the only
         * way to express that was to disable the lot.
         */
        public final ModConfigSpec.BooleanValue townsteadContentNeedsAndSchedules;
        public final ModConfigSpec.BooleanValue townsteadContentProfessions;
        public final ModConfigSpec.BooleanValue townsteadContentCalendarAndLife;
        public final ModConfigSpec.BooleanValue townsteadContentSpiritAndBuildings;
        public final ModConfigSpec.BooleanValue townsteadContentProjects;
        public final ModConfigSpec.BooleanValue townsteadContentSituations;

        Common(ModConfigSpec.Builder b) {
            b.push("quests");
            enableDefaultQuestPack = b.comment("Ship and enable the built-in default quest pack.")
                    .define("enableDefaultQuestPack", true);
            maxActiveQuestsPerPlayer = b.comment("Maximum simultaneously-active MCA quests per player.")
                    .defineInRange("maxActiveQuestsPerPlayer", 10, 1, 1000);
            maxActiveQuestsPerVillager = b.comment("Maximum simultaneously-active quests per villager per player.")
                    .defineInRange("maxActiveQuestsPerVillager", 1, 1, 100);
            offersPerVillager = b.comment("How many quest offers a villager presents at once.")
                    .defineInRange("offersPerVillager", 3, 1, 10);
            offerRefreshTicks = b.comment(
                    "Ticks a villager keeps the same set of offers before drawing a fresh one",
                    "(24000 = 1 MC day). Counted on the monotonic game clock, so sleeping through a night",
                    "does not reroll a villager's offers -- time has to actually pass.",
                    "Reopening the menu inside this window shows exactly what it showed before: the same",
                    "quests, the same numbers, the same dialogue.")
                    .defineInRange("offerRefreshTicks", 24000, 1, Integer.MAX_VALUE);
            defaultQuestCooldownTicks = b.comment("Default cooldown applied to quests that do not specify one.")
                    .defineInRange("defaultQuestCooldownTicks", 24000, 0, Integer.MAX_VALUE);
            declineCooldownTicks = b.comment(
                    "Ticks a quest stays out of a villager's offers after you decline it.",
                    "0 (default) means the refusal lasts until that villager's offers next refresh, which",
                    "is the gentler behaviour: turning something down should not lock it away for a week.",
                    "Declining is never punitive -- no hearts, no reputation, no failure is recorded.")
                    .defineInRange("declineCooldownTicks", 0, 0, Integer.MAX_VALUE);
            declineRefillsSlot = b.comment(
                    "Whether declining an offer immediately draws a replacement into that slot.",
                    "The other offers never change either way; only the card you turned down is replaced.")
                    .define("declineRefillsSlot", true);
            b.pop();

            b.push("turn_in");
            requireOriginalVillagerForTurnIn = b.comment(
                    "What a quest that does not state a \"turn_in\": {\"mode\"} means.",
                    "  true  (default) - hand it back to the villager who gave it",
                    "  false           - hand it to any MCA villager",
                    "A quest that DOES state a mode always wins; this only fills in the blank.")
                    .define("requireOriginalVillagerForTurnIn", true);
            allowTurnInToSameProfessionIfOriginalMissing = b.define("allowTurnInToSameProfessionIfOriginalMissing", false);
            failQuestIfGiverDies = b.define("failQuestIfGiverDies", false);
            b.pop();

            b.push("rewards");
            allowCommandRewards = b.comment("Command rewards are disabled by default for safety (spec section 26).")
                    .define("allowCommandRewards", false);
            allowLootTableRewards = b.define("allowLootTableRewards", true);
            heartsRewardMultiplier = b.comment(
                    "Scales every hearts reward before the min/max clamp below. THE key relationship-pacing",
                    "lever: MCA needs 100 hearts to marry (50 to engage, 40 to be a friend), so halving this",
                    "roughly doubles how many quests a player must complete with one villager to court them.")
                    .defineInRange("heartsRewardMultiplier", 1.0, 0.0, 100.0);
            minHeartsReward = b.defineInRange("minHeartsReward", 0, -1000, 1000);
            maxHeartsReward = b.defineInRange("maxHeartsReward", 100, 0, 10000);
            currencyRewardMultiplier = b.comment(
                    "Scales every mcaquests:currency reward. Applied to the rolled amount, then clamped to at",
                    "least 0. Explicit mcaquests:item rewards are NOT scaled - only semantic currency is.")
                    .defineInRange("currencyRewardMultiplier", 1.0, 0.0, 100.0);
            xpRewardMultiplier = b.comment(
                    "Scales every mcaquests:xp and mcaquests:xp_levels reward.")
                    .defineInRange("xpRewardMultiplier", 1.0, 0.0, 100.0);
            b.pop();

            b.push("rewards.currency");
            currencyProvider = b.comment(
                    "Which item the semantic mcaquests:currency reward pays out in.",
                    "  VANILLA      - emeralds (default; always available)",
                    "  NUMISMATICS  - Create: Numismatics coins, resolved by registry id at runtime",
                    "  CUSTOM       - the item named by customCurrencyItem below",
                    "MCA: Quests never links against Numismatics classes; the item is looked up by id, so an",
                    "absent mod is simply an unresolvable id, handled by currencyFallback.")
                    .defineEnum("currencyProvider", CurrencyProviderMode.VANILLA);
            customCurrencyItem = b.comment(
                    "Item id used when currencyProvider = CUSTOM.")
                    .define("customCurrencyItem", "minecraft:emerald");
            numismaticsCurrencyItem = b.comment(
                    "Item id used when currencyProvider = NUMISMATICS. Defaults to the spur, Numismatics'",
                    "smallest denomination, so datapack amounts stay meaningful at small numbers.")
                    .define("numismaticsCurrencyItem", "numismatics:spur");
            currencyFallback = b.comment(
                    "What to do when the configured provider's item cannot be resolved (mod absent, id typo):",
                    "  EMERALDS - pay out vanilla emeralds instead (default; the reward still happens)",
                    "  DISABLE  - grant nothing for that reward",
                    "Either way the problem is logged once per provider, never once per quest turn-in.")
                    .defineEnum("currencyFallback", CurrencyFallback.EMERALDS);
            // Defaults chosen to reproduce the built-in pack's pre-1.1.0 emerald economy (which ranged 1-8,
            // clustered on 2-3), so switching those payouts to semantic currency changed nothing in practice.
            easyCurrencyMin = b.comment("Currency range for a quest with \"difficulty\": \"easy\".")
                    .defineInRange("easyCurrencyMin", 1, 0, 10000);
            easyCurrencyMax = b.defineInRange("easyCurrencyMax", 2, 0, 10000);
            mediumCurrencyMin = b.comment("Currency range for a quest with \"difficulty\": \"medium\".")
                    .defineInRange("mediumCurrencyMin", 2, 0, 10000);
            mediumCurrencyMax = b.defineInRange("mediumCurrencyMax", 4, 0, 10000);
            hardCurrencyMin = b.comment("Currency range for a quest with \"difficulty\": \"hard\".")
                    .defineInRange("hardCurrencyMin", 4, 0, 10000);
            hardCurrencyMax = b.defineInRange("hardCurrencyMax", 8, 0, 10000);
            b.pop();

            b.push("rewards.reputation");
            // What finishing a quest is worth to the village when the quest does not say.
            //
            // It had to say, and 252 of the 262 bundled quests did not: only ten carried a
            // village_reputation reward, none used the documented "reputation" block at all, and six of
            // the seven quests GATED on standing were themselves among those ten. So an ordinary village
            // playthrough earned nothing from 96% of the pack, against a ladder with Acquaintance at 25,
            // Friend at 75, Honored at 150 and Revered at 300. A player reported it as "no matter what I
            // do I have 25 more to acquaintance and my rank is stranger", which is precisely correct
            // arithmetic over a score almost nothing was adding to.
            //
            // These bands mirror the currency bands beside them and are applied ONLY when a quest
            // authors nothing of its own; anything it does author still wins outright. Set all three to
            // 0 for the pre-1.5.0 behaviour, where quests were worth no standing at all.
            easyQuestReputation = b.comment(
                    "Village standing granted for completing a quest with \"difficulty\": \"easy\" that",
                    "declares no \"reputation\" block and no mcaquests:village_reputation reward.")
                    .defineInRange("easyQuestReputation", 2, 0, 1000);
            mediumQuestReputation = b.comment(
                    "As easyQuestReputation, for \"difficulty\": \"medium\". Also the amount used by a",
                    "quest that declares no difficulty at all, which most do not.")
                    .defineInRange("mediumQuestReputation", 4, 0, 1000);
            hardQuestReputation = b.comment(
                    "As easyQuestReputation, for \"difficulty\": \"hard\".")
                    .defineInRange("hardQuestReputation", 7, 0, 1000);
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
            minEscortJourney = b.comment(
                    "How far, in blocks, the subject of an escort_entity or reach_location objective must",
                    "START from the destination for the quest to be worth doing. A quest whose subject is",
                    "already inside this distance is not offered, and one granted some other way (a quest",
                    "chain, a command) will not credit arrival until the subject has genuinely travelled.",
                    "This is what stops 'walk me to my bed' being offered by a villager standing at their",
                    "bed and completed instantly for the reward. A datapack can override it per objective",
                    "with \"min_journey\"; set 0 here to fall back to the objective's own arrival radius.")
                    .defineInRange("minEscortJourney", 24, 0, 512);
            highlightQuestTargets = b.comment(
                    "If true (default), a villager that is the target of one of your active quests (the",
                    "recipient of a delivery, a missing relative to find, or the villager to",
                    "heal/cure/escort/protect/defend) is outlined through walls while it is loaded, so you can",
                    "find it. Sent to the quest owner only — other players do not see your quest markers.")
                    .define("highlightQuestTargets", true);
            highlightUsesGlowingEffect = b.comment(
                    "Legacy highlighting mode. If true, quest targets are highlighted with the vanilla Glowing",
                    "status effect applied to the villager itself instead of a per-player outline. That effect",
                    "is part of world state, so EVERY player on the server sees it and it can show up in",
                    "minimaps and shader outlines. Only enable it if you want that behaviour back.")
                    .define("highlightUsesGlowingEffect", false);
            highlightAllActiveQuests = b.comment(
                    "If true, every active quest highlights its target at once, as versions before 1.5.0 did.",
                    "The default (false) highlights only the quest you are tracking, and within it only the",
                    "villager the objective you are actually on is about — plus the villager you hand the",
                    "quest back to, once it is ready. Highlighting everything meant a player holding five",
                    "quests had five glowing villagers and no way to tell which one mattered.")
                    .define("highlightAllActiveQuests", false);
            guidanceSearchIntervalTicks = b.comment(
                    "How long, in ticks, before the quest marker retries a world search that found nothing.",
                    "Locating a structure or a biome is the same work as /locate and runs on the server",
                    "thread; a search that SUCCEEDS is remembered permanently on the objective, so this only",
                    "governs how often a failed one is tried again as the player travels. Lower it if you",
                    "want markers to appear sooner after a long journey; raise it on a busy server.")
                    .defineInRange("guidanceSearchIntervalTicks", 200, 20, 24000);
            guidanceSearchesPerPass = b.comment(
                    "How many world searches one player's guidance pass may run, per second.",
                    "Since 1.5.0 every active quest gets its own destination rather than only the one",
                    "carrying the marker, so a player holding five quests whose structures are all out of",
                    "range could otherwise fire five /locate calls at once. Quests that do not get a turn",
                    "are asked again on the next pass -- nothing is skipped, it is only spread out. Raise it",
                    "if destinations take too long to appear after a long journey.")
                    .defineInRange("guidanceSearchesPerPass", 1, 1, 8);
            autoTrackNewQuests = b.comment(
                    "If true (default), accepting a quest starts following it when you are not already",
                    "following one, so the marker and the tracker point at it without you having to ask.",
                    "Set false if you would rather pick what to follow yourself, with the pin in the quest",
                    "log. This is a server setting because the server decides what to point you at.")
                    .define("autoTrackNewQuests", true);
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

            b.push("compat.ftbquests");
            enableFtbQuestsIntegration = b.comment(
                    "Master behavior switch for the optional FTB Quests integration. When false (or FTB",
                    "Quests is absent): mcaquests tasks never progress (poll returns early, pushes skip),",
                    "mcaquests rewards no-op with a one-time WARN, ftbq_* conditions follow their",
                    "when_missing policy, ftbq_progress rewards no-op with log, and commands report",
                    "\"disabled\". Type registration is unaffected by this switch either way.")
                    .define("enableFtbQuestsIntegration", true);
            ftbqStatePollIntervalTicks = b.comment(
                    "autoSubmitOnPlayerTick() interval (ticks) for the stateful FTB Quests tasks. Counter",
                    "tasks are event-pushed and additionally poll at 4x this interval as a safety net.")
                    .defineInRange("ftbqStatePollIntervalTicks", 100, 20, 1200);
            ftbqHeartsScanRadius = b.comment(
                    "Radius (blocks) for McaCompat.maxHeartsWithin, used by the FTB Quests hearts task.")
                    .defineInRange("ftbqHeartsScanRadius", 16.0, 4.0, 64.0);
            allowFtbqProgressRewards = b.comment(
                    "Gates the mcaquests:ftbq_progress reward. Scope is limited to quest-book progress",
                    "(not commands), hence default-on; still a server-owner lever.")
                    .define("allowFtbqProgressRewards", true);
            syncFtbqEditorIds = b.comment(
                    "Send the known-ids packet to clients on login/reload when FTB Quests is present,",
                    "so the mcaquests condition/reward editor dropdowns can offer real quest/task ids.")
                    .define("syncFtbqEditorIds", true);
            b.pop();

            b.push("compat.townstead");
            townsteadEnabled = b.comment(
                    "Master switch for the optional Townstead integration. When false (or Townstead is",
                    "absent) no Townstead class is loaded, no Townstead state is queried, and Townstead",
                    "content stays ineligible. Datapack type registration is unaffected either way, so",
                    "packs still parse. Takes effect on restart.")
                    .define("enabled", true);
            townsteadContentEnabled = b.comment(
                    "Offer the quests, projects and situations that MCA: Quests ships for Townstead. Turn",
                    "this off to keep the mechanics available to your own datapacks without the built-in",
                    "content competing for menu slots.")
                    .define("contentEnabled", true);
            townsteadReactionsEnabled = b.comment(
                    "Let quest, project and situation transitions play Townstead reactions (villagers",
                    "applaud, wave, and so on). Purely cosmetic: a reaction never affects quest state, and",
                    "a failed one never blocks a completion.")
                    .define("reactionsEnabled", true);
            townsteadNeedRewardsEnabled = b.comment(
                    "Allow the mcaquests:townstead_needs reward to change a villager's hunger, thirst or",
                    "energy. Values are always clamped to Townstead's own ranges.")
                    .define("needRewardsEnabled", true);
            townsteadProfessionXpRewardsEnabled = b.comment(
                    "Allow the mcaquests:townstead_profession_xp reward to award Townstead profession XP.")
                    .define("professionXpRewardsEnabled", true);
            townsteadSkillRewardsEnabled = b.comment(
                    "Allow the mcaquests:townstead_skill reward to teach or remove Townstead skills.")
                    .define("skillRewardsEnabled", true);
            townsteadAllowUncappedProfessionXp = b.comment(
                    "Permit profession XP rewards that ask to bypass Townstead's daily cap. Bypassing needs",
                    "BOTH this switch and \"respect_daily_cap\": false in the reward JSON, because uncapped",
                    "XP lets a quest loop outrun Townstead's intended progression pacing.")
                    .define("allowUncappedProfessionXp", false);
            townsteadRewardFailureBlocksCompletion = b.comment(
                    "When a Townstead reward cannot be applied (mod removed mid-quest, villager gone,",
                    "capability missing), refuse the turn-in instead of completing without it. Default off:",
                    "removing Townstead should never trap a player in a quest they have finished.")
                    .define("rewardFailureBlocksCompletion", false);
            townsteadPollIntervalTicks = b.comment(
                    "How often (ticks) Townstead-backed quest objectives re-read villager state. Shares the",
                    "existing once-per-second objective pass; raising this trades responsiveness for tick time.")
                    .defineInRange("pollIntervalTicks", 20, 10, 1200);
            townsteadProjectPollIntervalTicks = b.comment(
                    "How often (ticks) Townstead-backed project objectives re-read village state.")
                    .defineInRange("projectPollIntervalTicks", 20, 10, 1200);
            townsteadMaxVillagersPerPass = b.comment(
                    "Cap on how many village residents one player's pass inspects. Larger villages are",
                    "visited round-robin across passes, so nobody is ignored and no pass is unbounded.")
                    .defineInRange("maxVillagersPerPass", 64, 1, 256);
            townsteadMaxVillagesPerPass = b.comment(
                    "Cap on how many villages one situation scan inspects, also round-robin.")
                    .defineInRange("maxVillagesPerPass", 8, 1, 64);
            townsteadNeedCrisisHysteresis = b.comment(
                    "Gap between the value that opens a need crisis and the value that closes it. With a",
                    "hunger crisis at 20 and a hysteresis of 10, the village leaves the crisis at 30 -- so a",
                    "villager hovering on the threshold cannot flap the situation on and off.")
                    .defineInRange("needCrisisHysteresis", 10, 0, 100);
            townsteadDebugBindingLogs = b.comment(
                    "Log every Townstead binding decision at startup and each capability miss at runtime.",
                    "Verbose; for diagnosing an integration problem, not for normal play.")
                    .define("debugBindingLogs", false);

            b.push("content");
            b.comment(
                    "Which themes of the built-in Townstead content are offered. All of these are",
                    "subordinate to contentEnabled above: turning that off hides everything regardless.",
                    "Changing any of them affects future offers only -- a quest already accepted is never",
                    "taken away from a player because a server owner changed their mind about a theme.");
            townsteadContentNeedsAndSchedules = b.comment(
                    "Hunger, thirst, energy, collapse, shifts and work routine.")
                    .define("needsAndSchedules", true);
            townsteadContentProfessions = b.comment(
                    "Profession progression, workplaces and apprenticeships.")
                    .define("professions", true);
            townsteadContentCalendarAndLife = b.comment(
                    "Seasons, the Townstead calendar, coming of age and later life.")
                    .define("calendarAndLife", true);
            townsteadContentSpiritAndBuildings = b.comment(
                    "Village character, registered buildings and the identity commissions.")
                    .define("spiritAndBuildings", true);
            townsteadContentProjects = b.comment(
                    "The village projects that read Townstead state.")
                    .define("projects", true);
            townsteadContentSituations = b.comment(
                    "The situations that Townstead state can trigger.")
                    .define("situations", true);
            b.pop();

            b.pop();
        }
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue showQuestButtonInMcaMenu;
        public final ModConfigSpec.BooleanValue showTownsteadQuestContext;
        public final ModConfigSpec.BooleanValue showQuestToasts;
        public final ModConfigSpec.BooleanValue showQuestTrackerHud;
        public final ModConfigSpec.BooleanValue showQuestTargetDirection;
        public final ModConfigSpec.BooleanValue showQuestTargetCoordinates;
        public final ModConfigSpec.BooleanValue showQuestLogDestination;
        public final ModConfigSpec.BooleanValue playQuestSounds;
        public final ModConfigSpec.IntValue questTrackerMaxEntries;
        public final ModConfigSpec.BooleanValue questTrackerBackground;
        public final ModConfigSpec.EnumValue<HudBackground> questTrackerStyle;
        public final ModConfigSpec.EnumValue<HudAnchor> questTrackerAnchor;
        public final ModConfigSpec.IntValue questTrackerX;
        public final ModConfigSpec.IntValue questTrackerY;
        public final ModConfigSpec.BooleanValue showQuestMarker;
        public final ModConfigSpec.IntValue questMarkerMaxDistance;
        public final ModConfigSpec.EnumValue<MarkerStyle> questMarkerStyle;
        public final ModConfigSpec.EnumValue<MarkerOcclusion> questMarkerOcclusion;
        public final ModConfigSpec.BooleanValue questMarkerEdgeIndicator;
        public final ModConfigSpec.EnumValue<MarkerLabels> questMarkerLabels;
        public final ModConfigSpec.BooleanValue questMarkerHighContrast;
        public final ModConfigSpec.BooleanValue questMarkerReducedMotion;
        public final ModConfigSpec.BooleanValue mapWaypoints;
        public final ModConfigSpec.BooleanValue mapWaypointsFollowedOnly;
        public final ModConfigSpec.BooleanValue journeyMapWaypoints;
        public final ModConfigSpec.BooleanValue xaeroWaypoints;
        public final ModConfigSpec.BooleanValue showProjectTrackerHud;
        public final ModConfigSpec.IntValue projectTrackerMaxEntries;
        public final ModConfigSpec.BooleanValue showSituationToast;

        Client(ModConfigSpec.Builder b) {
            b.push("client");
            showQuestButtonInMcaMenu = b.comment("Inject the Quests button into MCA's villager interaction menu.")
                    .define("showQuestButtonInMcaMenu", true);
            showTownsteadQuestContext = b.comment(
                    "Show a short read-only summary of Townstead state under each Townstead quest in the",
                    "log -- the villager's trade and tier, the need or schedule the quest is about, the",
                    "village's spirit. Server-rendered, so this only hides it for you; it has no effect on",
                    "quests, and nothing is shown for quests that are not about Townstead state.")
                    .define("showTownsteadQuestContext", true);
            showQuestToasts = b.define("showQuestToasts", true);
            showQuestTrackerHud = b.define("showQuestTrackerHud", true);
            showQuestTargetDirection = b.comment(
                    "Add a line to the quest tracker naming the villager the quest wants you to find, with",
                    "the distance to it and which way to turn -- \"Nether Fortress -- 412 blocks ahead-left\".",
                    "Every active quest that can name a place gets its own line; the world marker still",
                    "stands on only one of them. Needs showQuestTrackerHud.")
                    .define("showQuestTargetDirection", true);
            showQuestTargetCoordinates = b.comment(
                    "Add the destination's coordinates to that line, and to the quest log's. The mod could",
                    "say how far away somewhere was and never where it was, so there was nothing to write",
                    "down, type into a minimap or send to somebody else. A destination in another dimension",
                    "shows its coordinates too -- a bearing across dimensions would be a lie, but a",
                    "coordinate is exactly what you want written down before you go looking for a portal.")
                    .define("showQuestTargetCoordinates", true);
            showQuestLogDestination = b.comment(
                    "Show each quest's destination in the quest log as well as on the HUD tracker. The log",
                    "listed objectives and never said where any of them were. Click the line to copy the",
                    "coordinates.")
                    .define("showQuestLogDestination", true);
            playQuestSounds = b.define("playQuestSounds", true);
            questTrackerMaxEntries = b.comment("How many quests the tracker HUD shows at once.")
                    .defineInRange("questTrackerMaxEntries", 5, 1, 15);
            questTrackerBackground = b.comment("Draw a background behind the quest tracker HUD.")
                    .define("questTrackerBackground", true);
            questTrackerStyle = b.comment(
                    "Which background the quest tracker draws, when questTrackerBackground is on:",
                    "PANEL for the mod's textured panel, SHADED for the plain translucent box used",
                    "before 1.5.0. Ignored entirely when questTrackerBackground is false.")
                    .defineEnum("questTrackerStyle", HudBackground.PANEL);
            questTrackerAnchor = b.comment("Screen corner the quest tracker HUD anchors to: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT.")
                    .defineEnum("questTrackerAnchor", HudAnchor.TOP_LEFT);
            questTrackerX = b.comment("Quest tracker horizontal offset in pixels from its anchored corner.")
                    .defineInRange("questTrackerX", 4, 0, 10000);
            questTrackerY = b.comment("Quest tracker vertical offset in pixels from its anchored corner.")
                    .defineInRange("questTrackerY", 4, 0, 10000);
            showQuestMarker = b.comment(
                    "Draw a marker in the world at the place your tracked quest is currently sending you --",
                    "a small glyph on the target itself, with a ring on the ground under it, which fades out",
                    "as you arrive. Only ever one at a time, for the objective you are actually on. Turn it",
                    "off for the tracker text alone.")
                    .define("showQuestMarker", true);
            questMarkerMaxDistance = b.comment(
                    "How far away, in blocks, the world marker is still drawn. Past this only the tracker",
                    "line names the target, which keeps a marker from standing on the horizon for a",
                    "destination two thousand blocks away.")
                    .defineInRange("questMarkerMaxDistance", 256, 16, 4096);
            questMarkerStyle = b.comment(
                    "How much of the marker is drawn: COMPACT for the glyph, its ground ring and its label,",
                    "ICON_ONLY for the glyph alone -- the quietest thing that still says where to go --",
                    "and HIGH_VISIBILITY to add a short translucent column over the target for finding it",
                    "across a crowded village.")
                    .defineEnum("questMarkerStyle", MarkerStyle.COMPACT);
            questMarkerOcclusion = b.comment(
                    "What the marker looks like when the target is behind terrain: DIM_OUTLINE leaves a",
                    "faint hollow diamond, so a wall reads as a wall; HIDDEN shows nothing at all; FULL",
                    "draws the whole marker through the wall the way versions before 1.5.3 did.")
                    .defineEnum("questMarkerOcclusion", MarkerOcclusion.DIM_OUTLINE);
            questMarkerEdgeIndicator = b.comment(
                    "When the target is off the edge of the screen or behind you, show a small arrow at the",
                    "edge pointing at it, with the distance. Without it a target you are not facing is",
                    "simply not drawn, and there is nothing to turn towards.")
                    .define("questMarkerEdgeIndicator", true);
            questMarkerLabels = b.comment(
                    "When the marker carries its target's name and distance: NEARBY within 48 blocks,",
                    "ALWAYS at any distance it is drawn at, NEVER for the glyph alone. The tracker line",
                    "names the target either way.")
                    .defineEnum("questMarkerLabels", MarkerLabels.NEARBY);
            questMarkerHighContrast = b.comment(
                    "Draw the marker in white on a thick black outline instead of its kind's colour, and",
                    "darken the label background. The glyph still says what the target is, so nothing is",
                    "lost by not being able to tell the colours apart.")
                    .define("questMarkerHighContrast", false);
            questMarkerReducedMotion = b.comment(
                    "Skip the marker's fade in and its cross-fade between targets, so it simply appears.",
                    "Only ever affects opacity over time -- the marker never moves, scales or spins either",
                    "way.")
                    .define("questMarkerReducedMotion", false);
            mapWaypoints = b.comment(
                    "Put your quest destinations on JourneyMap and Xaero's Minimap, where either is",
                    "installed. One waypoint per quest that has somewhere to send you, created when it",
                    "resolves, moved as the quest advances, and taken away when it is done. They are not",
                    "saved to your own waypoint list -- they belong to the quest, not to you.")
                    .define("mapWaypoints", true);
            mapWaypointsFollowedOnly = b.comment(
                    "Restrict those waypoints to the quest you are following, so the map carries one at a",
                    "time rather than one per quest. The in-world marker beam has always worked this way;",
                    "this makes the map match it.")
                    .define("mapWaypointsFollowedOnly", false);
            journeyMapWaypoints = b.comment(
                    "Put quest destinations on JourneyMap, when it is installed. With both minimaps",
                    "installed the same destination is on both maps, and this is how you keep it on only",
                    "the one you actually read. mapWaypoints=false turns both off regardless.")
                    .define("journeyMapWaypoints", true);
            xaeroWaypoints = b.comment(
                    "Put quest destinations on Xaero's Minimap, when it is installed. Xaero's third-party",
                    "waypoints carry no dimension of their own, so only destinations in the dimension you",
                    "are standing in appear there. mapWaypoints=false turns both off regardless.")
                    .define("xaeroWaypoints", true);
            showProjectTrackerHud = b.comment("Show participating community projects in the HUD tracker.")
                    .define("showProjectTrackerHud", true);
            projectTrackerMaxEntries = b.comment("How many community projects the tracker HUD shows at once.")
                    .defineInRange("projectTrackerMaxEntries", 3, 1, 10);
            showSituationToast = b.comment("Show a toast when the village opens a new situation that needs help (0.8.0).")
                    .define("showSituationToast", true);
            b.pop();
        }

        /**
         * How much of the world marker is drawn.
         *
         * <p>Nested here, and not beside {@code HudBackground}, because these three are read only by
         * the marker renderer and the HUD indicator. The enums themselves stay in the common config
         * class for the usual reason: common code must never import anything under {@code client/},
         * and a config value's type is part of the common config.
         */
        public enum MarkerStyle {
            /** Glyph, ground ring, stem and label: the default. */
            COMPACT,
            /** The glyph alone, with nothing drawn around it. */
            ICON_ONLY,
            /** COMPACT plus a short translucent column standing on the target. */
            HIGH_VISIBILITY
        }

        /** What remains of the marker when the target is behind terrain. */
        public enum MarkerOcclusion {
            /** A faint hollow diamond, visibly different from the marker in the open. */
            DIM_OUTLINE,
            /** Nothing. */
            HIDDEN,
            /** The whole marker, through the wall, as it was drawn before 1.5.3. */
            FULL
        }

        /** When the marker carries its target's name and distance. */
        public enum MarkerLabels {
            /** Within 48 blocks, where the text is legible and the target is close enough to matter. */
            NEARBY,
            /** At every distance the marker is drawn at. */
            ALWAYS,
            /** Never; the glyph alone. */
            NEVER
        }
    }

    /** Profession matching strategy (spec section 12). */
    public enum ProfessionMatchingMode {
        STRICT,
        NORMALIZED,
        LOOSE
    }

    /** Which item the semantic {@code mcaquests:currency} reward pays out in (spec 1.1.0). */
    public enum CurrencyProviderMode {
        /** Vanilla emeralds. Always resolvable, so this is the default and the universal fallback. */
        VANILLA,
        /** Create: Numismatics coins, resolved by registry id — never by linking against its classes. */
        NUMISMATICS,
        /** Whatever item {@code customCurrencyItem} names. */
        CUSTOM
    }

    /** What a currency reward does when its configured provider item cannot be resolved (spec 1.1.0). */
    public enum CurrencyFallback {
        /** Pay vanilla emeralds instead, so the player is still rewarded. */
        EMERALDS,
        /** Grant nothing for that reward. */
        DISABLE
    }

    /**
     * Which background the quest tracker draws, when it draws one at all.
     *
     * <p>Whether there is a background stays {@code questTrackerBackground}'s decision, so every
     * existing config keeps the meaning it had; this only picks between the textured panel and the
     * flat shading the tracker used before it had a texture to draw.
     */
    public enum HudBackground {
        /** The mod's nine-sliced panel. */
        PANEL,
        /** The plain translucent-black box the tracker used through 1.4.3. */
        SHADED
    }

    /** Screen corner the quest-tracker HUD anchors to (spec section 21). */
    public enum HudAnchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }
}
