# Changelog

All notable changes to **MCA: Quests** are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[0.1.0]: https://github.com/otectus/MCAQuests/releases/tag/v0.1.0
