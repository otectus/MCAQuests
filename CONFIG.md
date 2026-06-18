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
| `offersPerVillager` | `3` | How many quest cards a villager shows at once. |
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
| `questChatMessages` | `true` | Send a short chat confirmation when a quest is accepted or completed. |

### `[debug]`
| Option | Default | What it does |
|---|---|---|
| `strictJsonValidation` | `false` | Treat any malformed/unknown quest JSON as a hard error instead of skipping it. |
| `debugLogging` | `false` | Verbose logging for troubleshooting. |

---

## Client (`mcaquests-client.toml`)

### `[client]`
| Option | Default | What it does |
|---|---|---|
| `showQuestButtonInMcaMenu` | `true` | Inject the **Quests** button into MCA's villager interaction menu. |
| `showQuestToasts` | `true` | Toast popup when a quest becomes ready to turn in. |
| `playQuestSounds` | `true` | Play a sound with the ready toast. |
| `showQuestTrackerHud` | `true` | Show the on-screen active-quest tracker. |
| `questTrackerMaxEntries` | `3` | Max quests listed in the HUD tracker. |
| `questTrackerAnchor` | `TOP_LEFT` | Screen corner the tracker anchors to: `TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`. |
| `questTrackerX` | `4` | Horizontal pixel offset from the anchored corner. |
| `questTrackerY` | `4` | Vertical pixel offset from the anchored corner. |

> The anchor + X/Y offset together make any on-screen tracker position reachable. Example: `TOP_RIGHT` with `questTrackerX = 4`, `questTrackerY = 28` tucks it under the top-right corner.

---

See [DATAPACK.md](DATAPACK.md) for authoring quests.
