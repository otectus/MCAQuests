# Changelog

All notable changes to **MCA: Quests** are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-06-18

Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x** and **Architectury API**.
Fully backward compatible with 0.1.0 worlds and datapacks.

### Added

- **Relationship quest chains** — an optional `chain` block on any quest turns a set of standalone quests into a multi-stage relationship arc, with no Java required:
  - `prerequisites` gate later stages on completing earlier ones (compiled into the existing condition system, so it composes with hearts/profession/time gates).
  - **Branching** on outcomes via two new conditions, `mcaquests:quest_failed` and `mcaquests:quest_abandoned`, alongside the existing `quest_completed`.
  - **Per-quest time limits** (`time_limit_ticks`) that fail a quest when they expire; the deadline survives logout/restart.
  - Offer selection prioritizes arc continuations and only ever shows the **furthest unlocked** stage of a chain at a villager.
  - UI shows the arc name and "Part 2 of 4" in the conversation menu and quest log; standalone quests are unchanged.
  - `/mcaquests validate` now reports chain problems (unknown/disabled references, bad stages, circular `unlocks`, unreachable stages), each naming the quest and field.
- **4 built-in sample chains** demonstrating the system: a farmer family arc, a guard community-safety arc (with a failure-redemption branch), a librarian chronicle arc, and a jobless friendship arc (with an abandonment branch).

### Notes

- Fully backward compatible: every chain field is optional, the 69 existing quests and any existing datapacks load unchanged, and new player save data is additive.

## [0.1.0] - 2026-06-18

First public release. Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x**
and **Architectury API**. Prod-tested against MCA Reborn 7.6.20.

### Added

- **In-menu Quests button** injected into MCA Reborn's villager interaction screen (client mixin), gated by config.
- **Conversation UI** — multi-offer cards showing each quest's title, dialogue, objectives, and reward summary, with inline Accept / Decline / Complete / Abandon.
- **Quest engine** (server-authoritative, datapack-driven):
  - 10 objective types: item delivery, obtain, craft, fish, kill entity, break block, place block, visit biome, visit dimension, talk to profession.
  - 7 reward types: item, XP, XP levels, status effect, loot table, command (disabled by default), and **MCA hearts**.
  - 12 condition types + `all_of` / `any_of` / `not` composites for gating offers.
  - Profession matching (strict / normalized / loose), 5 turn-in modes, cooldown/once repeat rules, and weighted, per-day-deterministic offer selection.
- **69 built-in quests** across every vanilla profession (4 each), jobless villagers and nitwits (6, shared), the MCA guard (5), and bonus quests for MCA archers, adventurers, and mercenaries.
- **Quest tracking** — keybind-toggled Quest Log, a fully repositionable HUD tracker (anchor + X/Y offset) that names the quest giver, and a toast + sound when a quest is ready to turn in.
- **Persistence** — quest state lives on the player (Forge capability), surviving death, dimension changes, villager unload, and restarts.
- **Java API + Forge events** — `McaQuestsApi` for add-ons to register custom objective/reward/condition types; `QuestAccepted/Ready/Completed/Abandoned/Failed` events on the Forge bus.
- **Commands** — `/mcaquests list`, `validate`, `reload`, `export-schema`, and `debug villager`.
- **Configuration** — common (gameplay) and client (visual) config, including a villager **auto-follow** toggle (off by default), chat confirmations, hearts-reward scaling/clamps, offer/cooldown tuning, and HUD placement.
- **Documentation** — `README.md`, `CONFIG.md` (every option), and `DATAPACK.md` (full quest schema + a datapack-authoring walkthrough).

### Notes

- MCA Reborn exposes no public API, so this release links against MCA's internal classes and is pinned to the **7.6.x** line; all access is isolated behind a single `McaCompat` adapter.
- Turn-in is atomic and idempotent — rewards cannot be duplicated by packet spam.

[0.2.0]: https://github.com/otectus/MCAQuests/releases/tag/v0.2.0
[0.1.0]: https://github.com/otectus/MCAQuests/releases/tag/v0.1.0
