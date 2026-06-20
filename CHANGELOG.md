# Changelog

All notable changes to **MCA: Quests** are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] - 2026-06-19

Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x** and **Architectury API**.
Save-data compatible with 0.1.0–0.3.0 worlds; the new **village projects** are additive and pre-0.4.0
worlds load cleanly. **Datapack breaking change:** `chain.time_limit_ticks` has been replaced by the
new `failure` block (see *Changed*/*Removed*). **Network protocol bumped to v2** — client and server
must run matching versions (the project packets are rejected at handshake otherwise).

### Added

- **Quest failure states & deadlines** — a new optional `failure` block makes a quest *expire* while
  it's active, with author-controlled triggers and outcomes. Failure is server-authoritative,
  persisted (the deadline is anchored to acceptance time, so it survives logout/restart), grants no
  rewards, and records a `FAILED` outcome that follow-up quests branch on via the existing
  `mcaquests:quest_failed` condition.
  - **Triggers** (combine freely; first to fire wins): `deadline_ticks` (relative deadline),
    `deadline_time` (fail at a Minecraft time-of-day, e.g. "before sunrise"), `require_weather`
    (fail when the weather stops matching, e.g. "while it's raining"), and `fail_on_giver_death`
    (per-quest, independent of the global `failQuestIfGiverDies` config).
  - **Outcomes**: `failure_hearts` (relationship penalty, or `0` for non-punitive), `retry_after`
    (cooldown before the quest can be offered again), and `block_retry` (permanent lock). The giver's
    `dialogue.failed` line is shown on failure; recovery quests are expressed with `quest_failed`.
  - A quest already complete ("ready to turn in") is **not** failed by a time/weather trigger — a
    grace window to hand it in. All failure paths route through one idempotent handler, so no quest
    ever double-fails or duplicates a completion.
- **HUD deadline countdown** — the quest tracker shows a live `m:ss` countdown for quests with a time
  deadline, turning red in the final minute. Weather/giver-death failures show no countdown.
- **3 built-in failure examples** — `guard/dawn_defense` (kill zombies before sunrise),
  `cleric/urgent_medicine` (+ `cleric/urgent_medicine_recovery`, a recovery quest gated on
  `quest_failed`), and `fisherman/rain_catch` (fish while it's raining).
- **Failure validation** — `/mcaquests validate` reports a `failure` block with no trigger, a
  `failure_hearts` magnitude past the hearts clamp, and `block_retry` combined with `retry_after`.
- **Village projects** — a new, separate system of **shared, multi-stage community goals** loaded from
  `data/<namespace>/mcaquests/projects/**.json`. A project's progress is **shared** (stored in the
  world save, not per-player), so multiple players contribute toward one common objective. Fully
  additive and backward compatible — existing quests are unchanged and pre-0.4.0 worlds load cleanly.
  - **5 scopes** decide who shares the progress: `player`, `villager`, `family`, `profession`, and
    `village`. The MCA-backed scopes (`family`/`profession`/`village`) resolve via MCA's village/
    relationship data and **fail safe** (the project never appears) when that data is missing.
  - **Phases** run in order — a phase is entered only after every earlier phase completes — each with
    its own dialogue, objectives, rewards, and an optional `unlock` gate.
  - **4 project objective types** that track shared progress: `donate_item` (items consumed and banked
    into the pool, with an optional `per_player_cap`), `project_kill_entity`, `project_place_block`,
    and `project_talk_to_profession`.
  - **Shared rewards** wrap any existing quest reward with a `target` (`contributors`,
    `all_participants`, `sponsor_village`, `top_contributor`) and add new reward types
    (`hearts_with_sponsor`, `hearts_with_participants`, `village_reputation`, `unlock`). Projects also
    carry independent **mod-side village reputation** deltas (`on_phase_complete` /
    `on_project_complete` / `on_fail`), and a new `mcaquests:village_reputation` condition tests a
    giver's reputation in any quest **or** project condition tree.
  - **Sponsors** (by profession) surface a project via a **View Project** button in the MCA villager
    menu; with `oneSponsorPerProjectPerDay` only one deterministically chosen villager per village
    offers a given project per day. Contributions are atomic and server-authoritative (items validated
    and consumed server-side, then banked, then synced); per-phase rewards distribute exactly once,
    with offline players' non-hearts rewards queued for next login and villager hearts queued via MCA.
  - **6 built-in example projects**: `guardhouse_stockpile`, `library_restoration`,
    `festival_preparation`, `well_repair`, `after_raid_recovery`, and `missing_villager_search`.
  - **Project commands** — `/mcaquests project list`, `info <id>`, `validate`, `reset <id>`,
    `advance <id>` (test-only force-advance), and `debug <id>` (explains availability from the nearest
    villager); list/info/debug at op level 2, validate/reset/advance at op level 3.
  - **Project validation** flags unknown scope/objective/reward type ids, missing phases, and
    unknown/disabled or circular `follow_up` chains as errors, with warnings for empty non-final
    phases, MCA-dependent scopes while MCA is absent, disabled command rewards, and mismatched reward
    targets. Hard errors abort the load under `strictJsonValidation`.
  - A **project menu, quest-log section, and HUD tracker** (`showProjectTrackerHud` /
    `projectTrackerMaxEntries`) surface active projects and their shared progress.
  - **Network protocol bumped to v2.** Project sync/contribution packets require a **matching client
    and server**; mismatched versions are rejected at handshake.

### Changed

- The `chain.time_limit_ticks` deadline is now `failure.deadline_ticks` — it works on **any** quest,
  not just chains, and gains the richer triggers/outcomes above. The two built-in chain quests that
  used it (`farmer_family/3_apprentice`, `guard_safety/2_patrol`) were migrated.

### Removed

- `chain.time_limit_ticks` (replaced by the `failure` block). Datapacks still using it will fail
  validation; move the value to `failure.deadline_ticks`.

## [0.3.0] - 2026-06-19

Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x** and **Architectury API**.
Fully backward compatible with 0.1.0–0.2.0 worlds and datapacks.

### Added

- **MCA-aware quest conditions** — 11 new optional, datapack-driven condition types that gate a quest's availability on the giver's MCA Reborn state. Each is usable standalone and composes inside the existing `all_of`/`any_of`/`not` composites and with every existing gate (hearts, profession, biome, time, weather, advancement, level, random chance, quest history):
  - `mcaquests:is_player_spouse` — the giver is married to the player.
  - `mcaquests:relationship_state` — the giver's relationship state (single/promised/engaged/married/widow).
  - `mcaquests:is_family_member` — the giver is the player's parent/child/sibling/grandparent (or any).
  - `mcaquests:age_group` — the giver's MCA age (baby/toddler/child/teen/adult).
  - `mcaquests:personality` — the giver's MCA personality (13 values).
  - `mcaquests:mood` — the giver's mood value range and/or named mood.
  - `mcaquests:village_member` / `mcaquests:has_home` — the giver's village/home status.
  - `mcaquests:health_below` — the giver's health fraction is below a threshold.
  - `mcaquests:infected` — the giver is zombie-infected past a threshold.
  - `mcaquests:related_villager_status` — a relative of the giver is alive/nearby/missing/dead/same-village.
- **6 built-in sample quests** in `relations/` exercising every category: a spouse errand (composed with a hearts gate), a child's request, a sick-villager remedy, a guard village patrol, a missing-child search, and a widower's memorial.
- **Quest templates** — an optional `template` block turns one quest file into many concrete quests. Authors declare variable pools (`item`/`block`/`entity`/`biome`/`dimension` drawn from ids or tags, `int` ranges, and `text` phrase pools) and reference them from objectives, rewards, and dialogue with `{placeholder}` tokens. Values are resolved **server-side at offer time**, deterministic per villager per day, then **frozen onto the accepted quest** and persisted — they never reroll until the quest is completed, failed, or abandoned, surviving logout/death/restart. `int` variables can scale by player level (`per_player_level`) or giver hearts (`per_heart`) with a `limit` clamp. Placeholders fill objective/reward JSON (whole-token `"{var}"`) and dialogue/titles (`{var}` value, `{var_name}` translated display name), preserving translation keys via `translate` + `with`.
- **5 built-in template examples** in `templates/`: farmer crop request, guard mob cull, fisherman catch, librarian knowledge, and cartographer survey.

### Notes

- All MCA access is isolated behind the mod's compatibility layer; conditions **fail safe** to *not met* with debug logging (never crashing the server) when MCA data is missing or the giver is not an MCA villager. Field values are validated on load (lenient skip-with-log, or hard error under `strictJsonValidation`).
- Evaluation stays server-authoritative and runs only at quest-menu time (not per tick); each villager's MCA state is snapshotted once per eligibility pass.
- `age_group` does not support `elder` — MCA Reborn has no elder age state.
- Quest templates resolve server-side only (no client randomization); empty pools fail safe (the offer is skipped with a debug log) and template definitions are validated on load like conditions.
- Fully backward compatible: every new condition and the `template` block are optional and additive; existing quests, datapacks, and save data load unchanged.

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
