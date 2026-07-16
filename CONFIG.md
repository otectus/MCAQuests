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
| `enableDefaultQuestPack` | `true` | Load the 38 built-in quests. Set `false` to ship only your own datapack quests. |
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
| `heartsRewardMultiplier` | `1.0` | Scales every hearts reward (e.g. `0.5` halves all relationship gains). |
| `minHeartsReward` | `0` | Lower clamp on a single hearts reward after scaling. |
| `maxHeartsReward` | `100` | Upper clamp on a single hearts reward after scaling. |

### `[matching]`
| Option | Default | What it does |
|---|---|---|
| `professionMatchingMode` | `NORMALIZED` | How a quest's `giver.professions` match a villager: `STRICT` (exact id), `NORMALIZED` (ignores namespace/case), `LOOSE` (alias-aware). |

### `[behavior]`
| Option | Default | What it does |
|---|---|---|
| `followGiverAfterAccept` | `false` | If `true`, the giver follows the player after a quest is accepted (escort-style). **Default `false`: accepting never makes a villager follow you, and an existing auto-follow is cleared.** |
| `leadVillagerSpeed` | `0.6` | Walk-speed multiplier for a villager **leading** the player in a lead-style escort (`escort_entity` with `lead:true`). Lower keeps it near walking pace so the player can stay close and guard it. Range `0.1`–`2.0`. |
| `highlightQuestTargets` | `true` | A villager that is the target of one of your active quests (the delivery recipient, or the villager to heal/cure/escort/protect/defend) **glows** through walls while it is loaded, so you can find it. Applied server-side; syncs to all clients. |
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

---

## Client (`mcaquests-client.toml`)

### `[client]`
| Option | Default | What it does |
|---|---|---|
| `showQuestButtonInMcaMenu` | `true` | Inject the **Quests** button into MCA's villager interaction menu. |
| `showQuestToasts` | `true` | Toast popup when a quest becomes ready to turn in. |
| `showSituationToast` | `true` | Toast popup when a nearby village opens a situation that needs help (0.8.0). |
| `playQuestSounds` | `true` | Play a sound with the ready toast. |
| `showQuestTrackerHud` | `true` | Show the on-screen active-quest tracker. |
| `questTrackerMaxEntries` | `3` | Max quests listed in the HUD tracker. |
| `questTrackerAnchor` | `TOP_LEFT` | Screen corner the tracker anchors to: `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`. |
| `questTrackerX` | `4` | Horizontal pixel offset from the anchored corner. |
| `questTrackerY` | `4` | Vertical pixel offset from the anchored corner. |

> The anchor + X/Y offset together make any on-screen tracker position reachable. Example: `TOP_RIGHT` with `questTrackerX = 4`, `questTrackerY = 28` tucks it under the top-right corner.

---

See [DATAPACK.md](DATAPACK.md) for authoring quests.
