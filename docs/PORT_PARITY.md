# Port parity ledger — MCA: Quests, NeoForge 1.21.1

Every difference between this port and the Forge 1.20.1 tree at `../../MCAQuests`, and its status. The
family rule is that this tree mirrors every change made there; anything that does not is listed here as
**pending** (owed, not yet ported), **not ported** (a recorded decision), or a **loader adaptation** (the
same behaviour, built the way 1.21.1 and NeoForge require). Compiled from a file-level comparison of
both working trees on 2026-09-28.

## Numbers

| | Forge 1.20.1 | This port |
|---|---|---|
| `mod_version` | 1.7.1 | 1.7.1 |
| Network protocol (`QuestNetwork.PROTOCOL_VERSION`) | `"18"` | `"18"` |
| MCA range | `[7.6,8)` | `[7.7,8)` |

## Features the port does not have

| Feature | Forge sources | Status |
|---|---|---|
| Ultima Kingdoms integration: kingdom quest lifecycles and gates, faction-standing rewards, institutional commissions, civic-building bindings and the kingdom standing condition | `compat/KingdomIntegration`, `quest/kingdom/*`, `quest/InstitutionalCommissionBridge`, `quest/condition/InstitutionalServiceAvailableCondition`, `quest/condition/KingdomStandingCondition`, `quest/reward/FactionStandingReward`, `state/CivicBuildingBinding`, `state/KingdomBindingSnapshot`, `assets/.../kingdom_lifecycle.schema.json` | **Not ported.** Ultima Kingdoms is Forge-only. |
| External map points published by another mod (Ultima's) | `api/ExternalMapPoint`, `network/ExternalMapPointsS2CPacket` | **Not ported**, for the same reason. |

## Loader adaptations (same behaviour, different mechanism)

| Area | Forge | This port |
|---|---|---|
| Player quest data | `state/PlayerQuestDataProvider`, `state/QuestCapabilityEvents` (capability) | `state/QuestAttachments`, `state/NbtComponents`, `state/ServerRegistries`, and `state/ForgeCapsMigration`, a one-shot import of the Forge build's `ForgeCaps` player data on world upgrade |
| Networking | `SimpleChannel` | `CustomPacketPayload` with `network/NetComponents` and `network/ClientPayloadHandlers` |
| Bountiful cash-in hook | `mixin/compat/BountyDataCashInMixin` on `io.ejekta.bountiful.bounty.BountyData` | `mixin/compat/BountyStackCashInMixin` on `io.ejekta.bountiful.components.BountyStack`, where Bountiful keeps a bounty on 1.21.1 |
| Data directories | 1.20.1 names (`tags/items`, `tags/entity_types`) | 1.21 names (`tags/item`, `tags/entity_type`); `DataDirectoryLayoutTest` rejects the old ones |

## Corrected on 2026-09-28

Four tags (`mcaquests:harbor_catch`, `mcaquests:pottery_sherds`, `mcaquests:common_undead` and the
Ice and Fire pack's `mcaquests:iceandfire_dread`) were still under the 1.20.1 directory names, which
1.21 never reads. The tags were empty on this port, so the quests "Deep Water Days" (`townstead/deep_water_days`), "The
Relic beneath the Well" (`adventurer/relic_beneath_the_well`) and the Ice and Fire pack's "The Dread
Tide" (`compat/iceandfire/dread_purge`), and the situation "Something below the Floor"
(`monster_in_the_cellar`), named a tag nothing was in: their objectives could never advance. The four
files now sit under `tags/item` and `tags/entity_type`.

## Changes from the 2026-09-28 remediation pass

| Change | Here |
|---|---|
| Stale `mcarealtalk` objective and reward keys removed from `en_us` and `pt_br` | Mirrored |
| Institutional commission rule documented as "exactly one reward", with a test | Not applicable (no commissions on this port) |
| Tags moved to the 1.21 directories, plus `DataDirectoryLayoutTest` | This port only |

## Changes from the 2026-09-30 quest audit

| Change | Here |
|---|---|
| A quest's standing falls back to the village frozen at accept when the giver resolves to none | Mirrored |
| `record_incident` and `resolve_incident` fall back to the frozen village | Mirrored |
| Held rewards keep the giver's context, and `/mcaquests rewards retry` grants through it | Mirrored (giver name saved through `NbtComponents`) |
| A copy whose definition drifted is not complete, takes no goods and takes no add-on signals | Mirrored |
| `failure_hearts` banked in the pending-hearts ledger when the giver is unloaded | Mirrored |
| The journal is pushed when standing, a title or the completion archive changes | Mirrored (flushed from `ServerTickEvent.Post`) |
| Load warning for a project-only reward in a quest; `/mcaquests validate` checks an untitled quest's fallback key | Mirrored |
| Deliver & complete keeps a commission board's restriction | Not applicable (no commissions on this port) |
| Reliability fixture `delivery` and `client` phases | Translated and compiled here; run on Forge only |

## Housekeeping

`src/main/java/dev/otectus/mcaquests/event/QuestProgressEvents.java.rej` is an untracked leftover of a
patch hunk (dated 2026-09-27) whose change, the jailed-player quest pause, is already in
`QuestProgressEvents.java`. It is not compiled and can be deleted.
