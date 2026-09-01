# MCA: Quests — Configuration

Two TOML files are generated in your instance's `config/` folder on first run:

- **`mcaquests-common.toml`** — gameplay rules (server-authoritative). On a dedicated server this is the server's copy that matters.
- **`mcaquests-client.toml`** — purely visual / local options.

Edit with the game closed (or close the world), then relaunch / rejoin — these are loaded at startup, not via `/reload` (that command only reloads the quest **datapack**, not the config).

---

## Common (`mcaquests-common.toml`)

### `[quests]`
| Option | Default | What it does |
|---|---|---|
| `enableDefaultQuestPack` | `true` | Load the 165 built-in quests. Set `false` to ship only your own datapack quests. |
| `maxActiveQuestsPerPlayer` | `10` | Hard cap on a player's simultaneously-active quests. |
| `maxActiveQuestsPerVillager` | `1` | Cap on active quests per individual villager, per player. |
| `offersPerVillager` | `3` | How many quest cards a villager shows at once. The cards scroll, so this is not limited by screen height. |
| `offerRefreshTicks` | `24000` | Ticks before a villager's offered set rerolls (24000 = 1 MC day). |
| `defaultQuestCooldownTicks` | `24000` | Cooldown applied to quests that don't define their own. |

### `[turn_in]`
| Option | Default | What it does |
|---|---|---|
| `requireOriginalVillagerForTurnIn` | `true` | Quests must be handed in to the villager who gave them (unless the quest overrides via `turn_in.mode`). |
| `allowTurnInToSameProfessionIfOriginalMissing` | `false` | If the original giver is gone, allow any same-profession villager. |
| `failQuestIfGiverDies` | `false` | If `true`, a quest fails when its giver dies; otherwise it waits / can be turned in elsewhere per its mode. |

### `[rewards]`
| Option | Default | What it does |
|---|---|---|
| `allowCommandRewards` | `false` | Master switch for `mcaquests:command` rewards. **Off by default for safety** — only enable for trusted packs. |
| `allowLootTableRewards` | `true` | Allow `mcaquests:loot_table` rewards. |
| `heartsRewardMultiplier` | `1.0` | **The relationship-pacing lever.** Scales every hearts reward before the clamps below. See [Relationship pacing](#relationship-pacing) for what to set it to. |
| `minHeartsReward` | `0` | Lower clamp on a single hearts reward after scaling. |
| `maxHeartsReward` | `100` | Upper clamp on a single hearts reward after scaling. |
| `currencyRewardMultiplier` | `1.0` | Scales every `mcaquests:currency` reward. Explicit `mcaquests:item` rewards are **not** scaled — only semantic currency is. |
| `xpRewardMultiplier` | `1.0` | Scales every `mcaquests:xp` and `mcaquests:xp_levels` reward. |

Scaling is applied **before** the amount is displayed, and a randomized currency amount is frozen when the quest is accepted — so the number on the card is always exactly the number you are paid.

### `[rewards.currency]`

*(1.1.0)*

The `mcaquests:currency` reward says *"pay the player some money"* and lets the server decide what money is. Switching every currency reward in every installed pack to another mod's coin is one config line; no datapack is rewritten.

| Option | Default | What it does |
|---|---|---|
| `currencyProvider` | `VANILLA` | What currency is paid in: `VANILLA` (emeralds), `NUMISMATICS` (Create: Numismatics coins), or `CUSTOM`. |
| `numismaticsCurrencyItem` | `numismatics:spur` | Item id used when the provider is `NUMISMATICS`. The spur is the smallest denomination, so small datapack amounts stay meaningful. |
| `customCurrencyItem` | `minecraft:emerald` | Item id used when the provider is `CUSTOM`. |
| `currencyFallback` | `EMERALDS` | What happens when the configured item cannot be resolved (mod not installed, id typo): `EMERALDS` pays emeralds instead, `DISABLE` grants nothing. Either way the problem is logged **once**, not once per turn-in. |
| `easyCurrencyMin` / `easyCurrencyMax` | `1` / `2` | Payout range for a quest with `"difficulty": "easy"`. |
| `mediumCurrencyMin` / `mediumCurrencyMax` | `2` / `4` | Payout range for `"difficulty": "medium"`. |
| `hardCurrencyMin` / `hardCurrencyMax` | `4` / `8` | Payout range for `"difficulty": "hard"`. |

**Numismatics is never a hard dependency.** The coin is looked up by registry id at runtime; MCA: Quests contains no reference to any Numismatics class and cannot classload one. An uninstalled Numismatics is simply an id that does not resolve, and takes the `currencyFallback` path.

The band defaults reproduce the built-in pack's pre-1.1.0 emerald payouts, so enabling nothing changes nothing.

### Relationship pacing

MCA Reborn's own thresholds (7.6.x defaults) are what quest rewards are pacing you towards:

| Milestone | Hearts |
|---|---|
| Gift a bouquet / begin courting | 10 |
| Considered a friend | 40 |
| Engagement | 50 |
| Greeting threshold | 75 |
| **Marriage** | **100** |

Before 1.1.0 the built-in pack granted up to 35 hearts for a single repeatable quest on a one-day cooldown, so a player could reach marriage with a villager in about **three in-game days** of repeating one trivial errand. Built-in hearts rewards are now banded by difficulty — **4 / 8 / 14** for easy / medium / hard — and hard repeatables carry at least a two-day cooldown, putting marriage at roughly 12–25 in-game days of sustained attention to *one* villager.

To pace it differently, use `heartsRewardMultiplier`: `0.5` roughly doubles the number of quests needed, `2.0` restores something close to the old speed.

### `[matching]`
| Option | Default | What it does |
|---|---|---|
| `professionMatchingMode` | `NORMALIZED` | How a quest's `giver.professions` match a villager: `STRICT` (exact id), `NORMALIZED` (ignores namespace/case), `LOOSE` (alias-aware). |

### `[behavior]`
| Option | Default | What it does |
|---|---|---|
| `followGiverAfterAccept` | `false` | If `true`, the giver follows the player after a quest is accepted (escort-style). **Default `false`: accepting never makes a villager follow you, and an existing auto-follow is cleared.** |
| `leadVillagerSpeed` | `0.6` | Walk-speed multiplier for a villager **leading** the player in a lead-style escort (`escort_entity` with `lead:true`). Lower keeps it near walking pace so the player can stay close and guard it. Range `0.1`–`2.0`. |
| `minEscortJourney` | `24` | How far, in blocks, the subject of an `escort_entity` or `reach_location` objective must **start** from the destination for the quest to be worth doing. A quest whose subject is already inside this distance is **not offered**, and one granted some other way (a quest chain, a command) will not credit arrival until the subject has genuinely travelled. This is what stops *"walk me to my bed"* being offered by a villager standing at their bed and completed instantly for the reward. A datapack can override it per objective with `min_journey`; set `0` here to fall back to the objective's own arrival radius. Range `0`–`512`. |
| `highlightQuestTargets` | `true` | A villager that is the target of one of your active quests (the delivery recipient, a missing relative you have found, or the villager to heal/cure/escort/protect/defend) is **outlined** through walls while it is loaded, so you can find it. Sent to the quest owner only — other players never see your quest markers. A quest whose objectives target nobody in particular outlines the **giver** instead. |
| `highlightUsesGlowingEffect` | `false` | Legacy highlighting mode. Applies the vanilla **Glowing status effect** to the villager itself instead of drawing a per-player outline. That effect is world state, so **every player on the server sees it** and it can appear in minimaps and shader outlines — which is why it is no longer the default. Only enable it if you want that behaviour back. |
| `questChatMessages` | `true` | Send a short chat confirmation when a quest is accepted or completed. |

### `[debug]`
| Option | Default | What it does |
|---|---|---|
| `strictJsonValidation` | `false` | Treat any malformed/unknown quest JSON as a hard error instead of skipping it. |
| `debugLogging` | `false` | Verbose logging for troubleshooting. |

### `[progression]`
| Option | Default | What it does |
|---|---|---|
| `enableReputationTiers` | `true` | Master switch for reputation tiers, player titles, and the journal screen (0.7.0). When off, the `reputation_tier` condition fails safe, tier-up toasts/titles are not granted, and tier/title ladders are not loaded; raw village reputation still accrues. |

### `[situations]`
| Option | Default | What it does |
|---|---|---|
| `enableSituations` | `true` | Master switch for the Living Village situations system (0.8.0). When off, no situations are detected, opened, or surfaced; existing quests are unaffected. |
| `maxConcurrentSituationsPerVillage` | `2` | Cap on simultaneously-open situations in one village. Excess detections are suppressed (and logged). `0` disables the cap. |
| `situationGlobalCooldownTicks` | `6000` | Minimum ticks between any two situations opening in the same village (anti-spam). |
| `situationDetectionIntervalTicks` | `200` | How often (ticks) villages near players are scanned for tick-driven situations (famine, missing kin, nightfall) and open situations are maintained. Death is detected immediately. |
| `maxSituationOffersPerMenu` | `2` | Cap on how many situation offers a single villager surfaces at once (they compete with static offers via the usual priority/weight shaping). |
| `situationDefaultPriority` | `5` | Default offer-priority tier for situation offers that don't set their own, so the village's needs fill menu slots first. |

### `[compat.ftbquests]`
| Option | Default | What it does |
|---|---|---|
| `enableFtbQuestsIntegration` | `true` | Master switch for the [FTB Quests integration](FTBQUESTS.md) (1.0.0). When off (or FTB Quests isn't installed): `mcaquests:` FTB tasks never progress, `mcaquests:` FTB rewards no-op with a one-time server-log WARN, `ftbq_*` datapack conditions follow their `when_missing` policy, the `mcaquests:ftbq_progress` reward no-ops, and `/mcaquests ftbq *` commands report "disabled". The task/reward **types stay registered** either way — only the behavior is gated. |
| `ftbqStatePollIntervalTicks` | `100` | How often (ticks) the poll-driven FTB tasks re-check MCA: Quests state as a safety net (event pushes are primary). Clamp `20`–`1200`. |
| `ftbqHeartsScanRadius` | `16.0` | Block radius the `mcaquests:hearts` FTB task scans for nearby villagers. Clamp `4.0`–`64.0`. |
| `allowFtbqProgressRewards` | `true` | Gates the MCA-side `mcaquests:ftbq_progress` reward (pushes FTB book progress at turn-in). Scope is limited to quest-book progress, hence default-on; still a server-owner lever. |
| `syncFtbqEditorIds` | `true` | Send MCA: Quests' known ids (quests, chains, tiers, titles, projects, situations) to the client on login/reload, so the FTB editor's id fields can offer a dropdown. Purely an editor convenience — free-text entry always works regardless. |

See [FTBQUESTS.md](FTBQUESTS.md) for the full task/reward/condition reference.

### `[compat.townstead]`
| Option | Default | What it does |
|---|---|---|
| `enabled` | `true` | Master switch for the [Townstead integration](TOWNSTEAD.md) (1.4.0). When off (or Townstead isn't installed): no Townstead class is loaded and no Townstead state is read, `townstead_*` conditions answer false so the bundled content never becomes eligible, `townstead_*` rewards no-op, and an already-accepted Townstead quest **suspends** rather than failing — it keeps its progress and frozen baselines and resumes if Townstead comes back. Datapack **types stay registered** either way, so a pack always parses. Takes effect on restart. |
| `contentEnabled` | `true` | Offer the quests, projects and situations MCA: Quests ships for Townstead. Turn off to keep the mechanics available to your own datapacks without the built-in content competing for menu slots. **Before 1.4.1 this only hid the situations**, despite the description; it now does what it says. Only affects *bundled* content — a third-party pack's Townstead quests are its author's to switch off, not this option's. |
| `reactionsEnabled` | `true` | Let quest, project and situation transitions play Townstead reactions, and gate the `mcaquests:townstead_reaction` reward. Purely cosmetic: a reaction never affects quest state, and one that fails never blocks a completion. |
| `needRewardsEnabled` | `true` | Gates `mcaquests:townstead_needs`. Values are always clamped to Townstead's own range for that need, which differ (hunger `100`, thirst/quenched/energy `20`). |
| `professionXpRewardsEnabled` | `true` | Gates `mcaquests:townstead_profession_xp`. |
| `skillRewardsEnabled` | `true` | Gates `mcaquests:townstead_skill`. |
| `allowUncappedProfessionXp` | `false` | Permit XP rewards that ask to bypass Townstead's daily cap, and skill rewards that ask to skip its prerequisites. Bypassing needs **both** this and the request in the reward JSON, because uncapped XP lets a repeatable quest outrun the progression pacing Townstead deliberately sets — and a datapack alone should not decide that for someone else's server. |
| `rewardFailureBlocksCompletion` | `false` | Refuse a turn-in when a Townstead reward cannot be applied, instead of completing without it. Default off: the player has already done the work, and trapping them with a finished quest they can never hand in is worse than quietly skipping the villager-facing half of the reward. |
| `pollIntervalTicks` | `20` | How often (ticks) Townstead-backed quest objectives re-read villager state. Shares the existing once-per-second objective pass. Clamp `10`–`1200`. |
| `projectPollIntervalTicks` | `20` | How often (ticks) Townstead-backed project objectives re-read village state. Clamp `10`–`1200`. |
| `maxVillagersPerPass` | `64` | Cap on residents inspected per pass. Larger villages are visited round-robin across passes, so nobody is skipped and no pass is unbounded. Clamp `1`–`256`. |
| `maxVillagesPerPass` | `8` | Cap on villages inspected per situation scan, also round-robin. Clamp `1`–`64`. |
| `needCrisisHysteresis` | `10` | Gap in percentage points between the share of a village that opens a need crisis and the share that closes it. A village sitting exactly on one threshold would otherwise flap the same emergency on and off every scan. Clamp `0`–`100`. |
| `debugBindingLogs` | `false` | Log every binding decision at startup, and add a counters line to `/mcaquests compat townstead status` — reads, cache hits, villages and residents observed, signals fired, capability misses, mutation failures, and average/max scan time. For diagnosing a problem or checking the performance budget, not for normal play. |

### `[compat.townstead.content]`

Per-theme switches for the bundled Townstead content (1.4.1). "Townstead content" is not one thing, and a
server that wants the needs and schedule quests but not the civic-identity ones previously had to take all
of it or none.

All default `true`, and all are **subordinate to `contentEnabled`** above: turning the master off hides
everything regardless, so an owner who wants none of it still only has one switch to find. Changes affect
future offers only — a quest already accepted is never taken away from a player because a server owner
changed their mind about a theme.

| Option | Default | Covers |
|---|---|---|
| `needsAndSchedules` | `true` | Hunger, thirst, energy, collapse, shifts and work routine. Offer groups `townstead_need` and `townstead_schedule`. |
| `professions` | `true` | Profession progression, workplaces and apprenticeships. Offer group `townstead_work`. |
| `calendarAndLife` | `true` | Seasons, the Townstead calendar, coming of age and later life. Offer groups `townstead_life` and `townstead_season`. |
| `spiritAndBuildings` | `true` | Village character, registered buildings and the identity commissions. Offer group `townstead_spirit`. |
| `projects` | `true` | The eleven village projects that read Townstead state. |
| `situations` | `true` | The fourteen situations Townstead state can trigger. |

See [TOWNSTEAD.md](TOWNSTEAD.md) for the full condition/objective/reward reference.

---

## Client (`mcaquests-client.toml`)

### `[client]`
| Option | Default | What it does |
|---|---|---|
| `showQuestButtonInMcaMenu` | `true` | Inject the **Quests** button into MCA's villager interaction menu. |
| `showTownsteadQuestContext` | `true` | Show a short read-only summary under each [Townstead](TOWNSTEAD.md) quest in the log — the villager's trade and tier, the need or schedule the quest is about, the village's spirit (1.4.0). Only what that quest actually reads is shown, and quests that are not about Townstead state show nothing. Server-rendered, so this hides it for you alone and has no effect on quests. |
| `showQuestToasts` | `true` | Toast popup when a quest becomes ready to turn in. |
| `showSituationToast` | `true` | Toast popup when a nearby village opens a situation that needs help (0.8.0). |
| `playQuestSounds` | `true` | Play a sound with the ready toast. |
| `showQuestTrackerHud` | `true` | Show the on-screen active-quest tracker. |
| `showQuestTargetDirection` | `true` | Add a line to the tracker naming the villager the quest wants you to find, with the distance to them and which way to turn ("Hans — 84 blocks to your right"). Falls back to their last known home while they are outside render distance. Hidden once the quest is ready to hand in. Needs `showQuestTrackerHud`. |
| `questTrackerMaxEntries` | `3` | Max quests listed in the HUD tracker. |
| `questTrackerAnchor` | `TOP_LEFT` | Screen corner the tracker anchors to: `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`. |
| `questTrackerX` | `4` | Horizontal pixel offset from the anchored corner. |
| `questTrackerY` | `4` | Vertical pixel offset from the anchored corner. |

> The anchor + X/Y offset together make any on-screen tracker position reachable. Example: `TOP_RIGHT` with `questTrackerX = 4`, `questTrackerY = 28` tucks it under the top-right corner.

---

See [DATAPACK.md](DATAPACK.md) for authoring quests.
