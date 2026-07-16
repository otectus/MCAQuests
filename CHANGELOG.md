# Changelog

All notable changes to **MCA: Quests** are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-07-16

The headline release: an optional, two-way **FTB Quests** integration (see the new
**[FTBQUESTS.md](FTBQUESTS.md)**) — ten FTB-side task types, three FTB-side reward types, and three
MCA-side conditions plus an objective and a reward that read/write FTB Quests book progress. Fully
optional in both directions: nothing here requires FTB Quests to be installed, and MCA: Quests' own
datapack format is unaffected either way. Also folds in the follow-up fixes to 0.9.1's `{player}` /
MCA-name feature (template quests, custom dialogue, username fallback) that had been sitting unreleased,
a conversation-UI fix for villagers offering more than one quest, and a way to abandon a quest from the
Quest Log so a stuck one can always be cleared.

### Added

- **FTB Quests integration** (optional; see [FTBQUESTS.md](FTBQUESTS.md)):
  - Ten `mcaquests:` FTB Quests **task** types reading real MCA: Quests progress: `quest_completed`,
    `chain_completed`, `reputation`, `reputation_tier`, `title`, `project_completed`,
    `project_contribution`, `situation_resolved`, `hearts`, `married`.
  - Three `mcaquests:` FTB Quests **reward** types granting MCA: Quests effects from the book:
    `village_reputation`, `hearts`, `grant_title` — with automatic banking and retry (on login, and once
    per in-game day while online) when no target village/villager can be found at claim time.
  - Three MCA: Quests **conditions** (`mcaquests:ftbq_quest_completed`, `mcaquests:ftbq_chapter_completed`,
    `mcaquests:ftbq_task_completed`), one **objective** (`mcaquests:ftbq_complete_quest`), and one
    **reward** (`mcaquests:ftbq_progress`) that read and write FTB book progress from a datapack quest.
  - A synced editor experience: FTB editor id fields (quest/chain/tier/title/project/situation ids) offer
    a dropdown built from the server's known ids, alongside the usual free-text entry.
  - `/mcaquests ftbq status|validate|recheck` commands.
  - New `[compat.ftbquests]` config block — see [CONFIG.md](CONFIG.md#compatftbquests).
- **Abandon a quest from the Quest Log** — each active quest in the log now has an **Abandon** button,
  with a confirmation prompt naming the quest. Abandoning from the log behaves exactly like abandoning
  from a villager's menu (records a `quest_abandoned` outcome; no cooldown or penalty), but does **not**
  require the giver, so a quest can always be dropped.
- **Core API additions** powering the integration (also usable by other add-ons):
  - Four new Forge events: `SituationResolvedEvent`, `ReputationTierReachedEvent`, `TitleGrantedEvent`,
    and `ProjectEvent.Contributed`.
  - `ProgressionStats` — a per-player snapshot of quest/project/situation completion counts, persisted
    alongside the player's existing quest data.
  - `PollingObjective` — a new objective interface for objectives that need a periodic server-side check
    rather than pure event-driven progress.
  - New `ReputationService` read accessors: `tierIndex`, `villageReputation`, `allVillageReputations`,
    `currentTier`.
  - New `McaCompat` helpers: `isPlayerMarried`, `maxHeartsWithin`, `bestHeartsVillagerWithin`,
    `nearestVillagerWithin`, `nearestAdultVillagerWithin`.

### Changed

- **Network protocol bumped `"4"` → `"6"`**, to carry the new FTB-editor known-ids packet, the
  abandon-from-log packet, and the giver id on quest-log entries. Client and server must run **matching
  versions** — the existing strict handshake already rejects a mismatch, so this is an enforced lockstep
  update, not a soft one. Save data is unaffected.
- **Admin title-command feedback identifies the account** — `/mcaquests title grant|list|clear` feedback
  now shows the MCA character name **and** the Minecraft username (which is unique) when they differ, so a
  mistargeted command is no longer indistinguishable in the output.
- **A template variable named `player` is now rejected at load time** — it would resolve in objective/reward
  JSON but be shadowed by the reserved token in dialogue, silently disagreeing. `/mcaquests validate` now
  reports it (rename the variable).
- Internal cleanup with no behavior change: consolidated the player-name → placeholder-resolver plumbing,
  removed a per-tick MCA save-data lookup on the quest-progress path, and dropped now-unreachable code
  branches.

### Fixed

- **Quest cards overflowed the villager's Quests screen.** With more than one offer (the default
  `offersPerVillager` is 3), cards ran past the bottom of the screen and drew over the "View Project"
  and "Back" buttons — and because card buttons were registered first, an Accept/Decline sitting on top
  of the footer would swallow its clicks. Cards now sit in a clipped, mouse-wheel scrollable area
  between the header and the footer, with a scrollbar when they overflow. The same fix is applied to the
  **village project** screen and the **Quest Log**, which shared the bug.
- **A quest whose giver was gone could not be abandoned.** Abandon existed only inside the villager
  conversation menu, which silently did nothing when the giver was dead, despawned, in an unloaded
  chunk, or in another dimension — leaving the quest stuck in an active slot forever with no command to
  clear it. It can now be abandoned from the Quest Log.
- **A quest whose definition was removed from a datapack no longer disappears from the Quest Log.** It
  is now listed under its raw id, so it can be abandoned instead of silently occupying an active slot.
- **Template quests using `{player}` no longer fail to load** — the reserved `{player}` token was flagged as
  an undeclared variable, so any template quest using it in `dialogue`/`title` failed validation and, under
  `strictJsonValidation`, refused to load. The reserved token is now exempt from the check.
- **Custom `accept` / `complete` dialogue now shows in chat** — a quest's own `accept`/`complete` dialogue
  line (and any `{player}` in it) was ignored on accept and turn-in unless the MCA: Conversations add-on was
  installed; only the generic "Quest accepted/completed" message appeared. Both now render the datapack's
  line, matching the `failed` state.
- **Players without an MCA character name are named by their username again** — MCA auto-creates a family
  node named "Unnamed Adventurer" for unresolved/offline players, which suppressed the Minecraft-username
  fallback. That placeholder is now treated as "no name set", so cards, messages, toasts, and command
  feedback no longer address players as "Unnamed Adventurer".

### Compatibility

- **Fully save-compatible** — existing worlds and datapacks load unchanged; no migration needed.
- `ProgressionStats` is new per-player save state: on a save from before 1.0.0 it loads **empty**, not
  absent or an error, and simply starts accumulating from first load onward.
- Banked FTB-reward pending entries are new kinds within the existing per-player pending-rewards list;
  they are simply **absent** on saves that predate this release, same as any other save that never
  queued one.
- **Clients running 0.9.x or earlier are rejected by the network handshake** (protocol `"4"` vs `"6"`) —
  this is intentional; update client and server together.

## [0.9.1] - 2026-07-11

Address the player by their **MCA character name** (the name set in MCA's character-creation screen)
instead of their Minecraft username wherever the mod names them. The new `{player}` token is optional, and
every name lookup falls back to the Minecraft username when MCA is absent or no character name was set.
Note that hand-authored (non-template) `text` dialogue now goes through the placeholder pass so `{player}`
works there too: this means `{{`/`}}` escapes now resolve in that text (a literal `{{` renders as `{`), and a
`"with"` list on a hand-authored `"translate"` line is now applied instead of ignored.

### Added

- **`{player}` dialogue token** — quest authors can now write the player's MCA character name into any
  quest's `dialogue`, `title`, and chain arc/chapter text (e.g. `"Well met, {player}!"`), not just
  template quests. It is a reserved token that cannot be shadowed by a template variable named `player`,
  falls back to the Minecraft username when MCA is absent or no name is set, and is dialogue-only (never
  substituted into objective/reward JSON). Resolved server-side per recipient, so situation broadcasts
  name each nearby player correctly. Documented in `DATAPACK.md`.

### Changed

- **Admin command feedback uses the MCA name** — the chat messages from `/mcaquests title grant|list|clear`
  now show the target player's MCA character name instead of their Minecraft username (with the username
  as a safe fallback). Debug logs continue to use the username for account-level troubleshooting.

## [0.9.0] - 2026-07-07

An **MCA: Conversations** add-on bridge, lead-style escorts, and a substantial quest-pack expansion.
Existing saves and datapacks are unaffected — the new `escort_entity` fields are optional and default
to the previous behavior, and the conversation hooks do nothing unless the add-on registers them.

### Added

- **MCA: Conversations — voiced quest dialogue** — a new add-on API (`QuestDialogueHooks` /
  `QuestDialogueResolver`) lets **MCA: Conversations** speak a quest's lifecycle line (offer /
  in-progress / ready / complete / failed) in the villager's own personality instead of the static
  datapack `dialogue` text. Resolved server-side at Component-build time; **degrades safely to the
  static line** when no resolver is registered, the resolver returns `null`, or it throws — so the
  base mod is unchanged without the add-on.
- **MCA: Conversations — conversation-driven objectives** — objectives implementing
  `ExternalSignalObjective` advance when the add-on pushes a signal via
  `QuestManager.notifyExternalObjective(player, signalId, villagerUuid)` — e.g. "the player talked to
  this villager about topic Y" — letting talk-based quests progress from an actual conversation
  rather than a built-in detector.
- **NPC-led escorts** — `escort_entity` gains a `lead` flag (default `false`). With `lead: true` the
  villager walks to the destination **itself** and **pauses whenever the player is farther than
  `wait_distance` blocks** (default 6), so the player must stay close to keep it safe — the inverse of
  the old player-leads / villager-follows behavior. Driven server-side through MCA's brain
  (`MoveState.MOVE` + the vanilla `WALK_TARGET` memory), isolated behind `McaCompat`; the lead pace is
  configurable via the new `leadVillagerSpeed` option. Lead/follow movement is now also released cleanly
  on quest complete/abandon/fail. Pairs with `failure.fail_on_giver_death`.
- **Staged relative-escorts** — when a `lead` escort's target is someone *other than the giver* (a
  relative or other villager), the escortee now **waits invulnerable and motionless at its spot** until the
  player comes within `wait_distance`; the escort then "truly begins" — the escortee becomes mortal, starts
  being led, and from that point its **death fails the quest** (new `ESCORT_TARGET_DIED` reason, heart
  penalty applied to the giver). Auto-detected for `lead` + non-`self` villager, overridable with the new
  `stage_until_near` field. The escortee is locked by UUID (so a re-resolving `family` target can't swap
  relatives) and the hold is released on engage/cleanup so a held villager is never left frozen.
- **Findable quest targets** — for an active quest, objectives that target a specific villager
  (`deliver_to_villager`, `heal_entity`, `cure_villager`, `escort_entity`, `protect_entity`,
  `defend_villager`) now resolve the target's **real name** and home village in the objective line — e.g.
  "Deliver 1× Paper to **Hans (your brother) — Oakvale**" (the name comes from MCA's persistent family tree,
  so it shows even when the relative is unloaded) — and the target villager **glows** through walls while it
  is loaded, so it can be found. New `highlightQuestTargets` config (default on) toggles the glow. Objective
  lines are resolved server-side, so no protocol change. Family-relative quests are also gated on
  `related_villager_status <relation> same_village` so they are only offered when a findable relative exists
  — fixing `relations/letter_to_brother`, which could previously be offered to a villager with no sibling at
  all (an impossible quest).
- **`mcaquests:giver_distance_from_village` condition** — gates a quest on the giver being at least
  `min_distance` blocks from its home-village center (optionally also outside the village border). Fails
  safe to *not met* when the giver has no village. Combine with `mcaquests:time` `NIGHT` via `any_of` to
  reserve escort / "out after dark" quests for villagers genuinely far from home or caught out at night.
- **`mcaquests:reach_location` objective** — the player travels to a location anchor; arrival sticks
  complete (distinct from `enter_structure`, which keys off a named structure).
- **`mcaquests:defend_location` objective** — defeat hostile threats near a fixed place anchor (the
  place-anchored sibling of `defend_villager`).
- **~54 new built-in quests plus 3 chains, 2 projects, 2 situations, and 2 templates**, emphasizing
  combat/defense, relationship & family arcs, and village/emergent events: lead-escort and
  night/distance-gated quests, gate defenses and night watches (showcasing `defend_location`), spouse /
  child / parent storylines, the multi-stage **courting**, **lost_child** (branching), and
  **aging_parent** relationship arcs, the **muster_the_militia** and **rebuild_the_walls** community
  projects, and the **defend_the_gate** / **raiders_at_the_gate** situations.

### Changed

- The built-in `relations/escort_me_home` quest now uses `lead: true` and is gated to a villager a short
  way from home during the day (keeping its "before nightfall" deadline). A new night/far variant,
  `relations/lead_me_home`, covers being caught out after dark with no time limit.

### Fixed

- **Escort/lead quests now actually work.** Three compounding bugs are resolved:
  - **Erratic, stuttering movement.** A led villager's walk target was only re-issued once per second, so
    MCA's per-tick brain behaviors overwrote it for the other 19 ticks — the villager drifted/stuttered
    instead of walking to its destination. Lead actuation now runs **every tick** so the walk target sticks,
    and the `wait_distance` leash gained hysteresis so it no longer thrashes start/stop at the boundary.
  - **"Escort to the village" while already in it / never completing.** Arrival at a village anchor
    (`home_village`/`nearest_village`) now triggers when the villager is **inside the village border**, not
    within a small radius of the single center point, and the check is horizontal (the village center's `Y`
    no longer blocks completion). Home-village lead quests are also gated with `require_outside_border` so a
    villager already inside the village isn't offered an "escort me home".
  - **Drifting destination.** The escort destination is now **resolved once and frozen when the quest is
    accepted**, so a `nearest_village` (previously recomputed against the *player's* position every tick) or a
    moving relative target no longer snaps around. `nearest_village` also resolves relative to the
    escortee/giver rather than the player.
- `reach_location` uses the same border-aware, horizontal arrival as escorts.

### Compatibility

- The **MCA: Conversations** integration is **optional**. MCA: Quests only ships the hooks and their
  safe fallbacks; the consumer lives in the separate MCA: Conversations add-on. With the add-on
  absent, dialogue stays the static datapack text and `ExternalSignalObjective` quests progress
  through their normal detectors — nothing else changes.

## [0.8.0] - 2026-06-22

Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x** and **Architectury API**. The
**Living Village** phase: villages now react to what actually happens in the world and ask for help.
Instead of only standing, author-authored offers, gameplay events open transient **Situations** that
surface dynamic, time-limited quest offers on nearby villagers and resolve into reputation outcomes.
Saves remain forward/backward compatible (new data loads as empty when absent). **Network protocol
bumped to v4** — client and server must run matching versions.

### Added

- **Situations** — a new, fully datapack-driven system of emergent, world-driven quests loaded from
  `data/<ns>/mcaquests/situations/**.json`. A situation pairs a **trigger** (what world event opens it)
  with lifetime/throttle metadata, a **scope**, resolution **outcomes**, and the dynamic **offer**
  surfaced while it is open. Open situations are stored in the world save (`mcaquests_situations.dat`),
  so they survive logout, chunk/villager unload, and restart; everything is server-authoritative.
  - **Six trigger types**, registry-driven like objectives/conditions/rewards: `mcaquests:raid`,
    `villager_death`, `infection` (`min_progress`), `missing_kin` (`relation`), `low_food`
    (`threshold`, read from MCA village storage), and `night` (`require_full_moon`). Detection is
    player-proximity-driven (a periodic sweep of villages near players) plus event-driven on villager
    death; all MCA access stays behind `McaCompat`.
  - **Three scopes** decide who surfaces the offer and where the outcome lands: `village`, `villager`
    (the focal one), and `family` (an MCA lineage).
  - **Dynamic offers** reuse the entire existing quest lifecycle. A situation's `offer` block is the
    body of a quest (objectives, rewards, dialogue, turn-in, templates, offer shaping) and is surfaced
    through the same selection/shaping pipeline as static quests, defaulting to a higher priority so the
    village's needs stand out (`situationDefaultPriority`). The offer is time-limited: its deadline is
    anchored to the situation's open time, so the existing HUD countdown and failure machinery apply.
  - **Resolution & outcomes** — the first participant to complete the offer resolves the situation as a
    **success** (village reputation, routed through the single `ReputationService`, plus optional hearts
    to the focal villager); its deadline expiring resolves a **failure** (a reputation penalty, and any
    still-active copies fail with the new `SITUATION_CLOSED` reason); a condition lifting on its own
    (the raid ends, food recovers) closes it **cleared**, usually neutral.
  - **Throttling** — a per-village concurrency cap, a per-definition cooldown, and a global anti-spam
    cooldown gate how often situations open; every suppression is logged (caps are never silent).
  - **"Village needs help" toast** to nearby players when a situation opens (client `showSituationToast`)
    and a card tag marking situation offers in the menu.
  - **Six built-in example situations**: `after_raid_recovery`, `cure_the_infected`, `find_missing_child`,
    `avenge_the_fallen`, `famine_relief`, and `night_watch`.
  - **New commands** — `/mcaquests situation list|info <id>|validate|debug` (list/info/debug at op level
    2, validate at op level 3).
  - **Config** — a new `situations` block: `enableSituations` (master switch),
    `maxConcurrentSituationsPerVillage`, `situationGlobalCooldownTicks`, `situationDetectionIntervalTicks`,
    `maxSituationOffersPerMenu`, `situationDefaultPriority`, and client `showSituationToast`.

### Changed

- **Network protocol bumped v3 → v4** for the situation toast packet. The handshake rejects mismatched
  client/server; save data is unaffected.
- All active-quest definition lookups now route through a single resolver so dynamic situation offers
  reuse the quest lifecycle (accept, track, turn in, fail) unchanged alongside static quests.

### Compatibility

- Fully save backward/forward compatible: open situations and the new optional `ActiveQuest` situation
  link load as empty/absent on pre-0.8.0 saves, and existing quests, projects, conditions, and rewards
  are unchanged. When `enableSituations` is off, nothing is detected, opened, or surfaced.

## [0.7.0] - 2026-06-21

Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x** and **Architectury API**. Turns the
quest loop into a long-term **progression** system: village reputation now has named tiers, players earn
titles, and a new journal screen makes it all visible. Saves are forward/backward compatible (new data
loads as empty when absent). **Network protocol bumped to v3** — client and server must run matching
versions.

### Added

- **Reputation tiers** — a datapack-defined, ordered ladder of named thresholds over the existing
  per-village reputation (default: Stranger → Acquaintance → Friend → Honored → Revered). Loaded from
  `data/<ns>/mcaquests/reputation_tiers/**.json`; the shipped `mcaquests:default` ladder can be overridden.
  A built-in fallback ladder is used if none is defined.
- **`mcaquests:reputation_tier` condition** — gate a quest's eligibility on a minimum (and optional maximum)
  tier with the giver's village; supports an optional named `ladder`. Fails safe when no village resolves
  or tiers are disabled.
- **Player titles** — earned per-village or globally and persisted on the player. New
  **`mcaquests:grant_title` reward** (scope `village` or `global`), and tiers may auto-grant a title via
  `grants_title` when first reached. Optional title definitions load from `data/<ns>/mcaquests/titles/**.json`.
- **Tier-up toast** — shown when the player pushes a village into a new reputation tier.
- **Journal screen** — opened via the new (unbound by default) **Open Journal** keybind or a button in the
  Quest Log. Shows each village's reputation and tier, earned titles, and a completed-quest archive.
- **New admin/test commands** — `/mcaquests reputation get|set|add|tiers` and
  `/mcaquests title grant|list|clear`. `/mcaquests validate` now also reports progression cross-references
  (undefined granted titles, unknown tier ids).
- **Config** — `enableReputationTiers` (default on) gates the loaders, condition, UI, toasts, and titles.
- Example quest `mcaquests:honored_envoy` exercising the new condition and reward.

### Changed

- **Network protocol bumped v2 → v3** for the tier-up toast and journal request/sync packets. The
  handshake rejects mismatched client/server; save data is unaffected.
- All village-reputation writes (quests and projects) now route through a single `ReputationService` so
  tier crossings and title grants fire consistently.

### Compatibility

- Fully save backward/forward compatible: new player NBT (`titles`) and the per-village tier high-water
  mark load as empty when absent. Existing `village_reputation` rewards keep working and now additionally
  drive tier-ups. A 0.7.0 save opened by 0.6.0 ignores the new keys (losing titles/high-water on a
  round-trip through the older version).

## [0.6.0] - 2026-06-21

Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x** and **Architectury API**. Turns
MCA: Quests into a living-village RPG system: quests can now involve people, homes, families, and places
rather than only inventory checks. No network protocol change; fully backward compatible — every existing
quest, condition, and objective works unchanged.

### Added

- **Twelve NPC- and village-centered objective types**, all registry-driven (no hardcoded quest cases),
  server-authoritative, and persisted through the existing quest state (they survive logout, death,
  dimension change, villager/chunk unload, and dedicated-server restart):
  - `mcaquests:escort_entity` — lead a villager to a home, village, workstation, another villager, or coords.
  - `mcaquests:protect_entity` — keep a villager alive for a duration (optionally only while near you).
  - `mcaquests:defend_villager` — kill hostile threats near a villager.
  - `mcaquests:trade_with_villager` — complete trades with a villager or profession.
  - `mcaquests:heal_entity` — use a remedy item on a hurt villager.
  - `mcaquests:cure_villager` — cure an infected (zombifying) MCA villager.
  - `mcaquests:breed_animals` / `mcaquests:tame_animal` — breed/tame animals, optionally near a place.
  - `mcaquests:sleep_or_rest` — sleep through to morning.
  - `mcaquests:build_near_location` — place blocks near a place (each position counts once — no farming).
  - `mcaquests:enter_structure` — enter a configured structure (id or tag).
  - `mcaquests:deliver_to_villager` — hand an item to a specific villager (family member, profession, UUID).
- **Villager targets** (`self` / `profession` / `family` relation / `uuid`) and **location anchors**
  (`home_village` / `nearest_village` / `giver_pos` / `villager` / `workstation` / `bed` / `coords`),
  resolved relative to the quest giver — unloaded targets pause the objective rather than failing it.
- **Per-objective datapack validation** (`ObjectiveValidator`) reporting bad targets/anchors/structures by
  quest id, objective index, and field, honouring `strictJsonValidation`.
- **Seven example quests** demonstrating the new system (escort home, protect a child, deliver a letter,
  cure infected kin, repair the village well, trade with the blacksmith, defend the guard captain).
- **`McaCompat`** gains `getHomePos`, `getWorkstationPos`, `isInfected`, `findGiverRelative`, and
  `giverRelativeUuids` — all safe-fail, with MCA internals kept inside that one class.
- **`ObjectiveProgress`** extended (elapsed ticks, locked target UUID, deduped placed positions, scratch
  tag) — backward compatible: old count-only progress loads unchanged.

### Known limitations

- `cure_villager` and `enter_structure` depend on MCA/dynamic-registry state and ship with documented
  fallbacks (see DATAPACK.md). The "villager uses their bed" variant of `sleep_or_rest` is not implemented.

## [0.5.0] - 2026-06-19

Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x** and **Architectury API**. Hardens the
relationship-quest-chain system (0.2.0) into a production-grade tool for long-term, author-built villager
stories. No network protocol change. **Behaviour change:** relationship arcs are now **per-villager** — see
*Changed* and the migration note below.

### Added

- **Per-villager relationship arcs** — chain progress is tracked against the individual villager you deal
  with, so the same arc can be lived out independently with different villagers. Chain `prerequisites` compile
  to `quest_completed` with `scope: giver`, and the four quest-state conditions (`quest_completed`,
  `quest_not_completed`, `quest_failed`, `quest_abandoned`) gain an optional `scope` field (`global` default,
  or `giver`). `QuestHistory` persists per-giver completion/outcome counts alongside the global ones, so arc
  progress survives logout, death, dimension change, villager unload/reload, and dedicated-server restart. No
  client-side state is trusted.
- **Offer priority & weighted bonuses** — two optional top-level fields shape *which* eligible quest a villager
  offers. `priority` (int) tiers offers (higher fills slots first; a chain continuation defaults above
  standalone). `weight_bonus` is a list of `{ "when": <condition>, "amount": <int> }` that adds to a quest's
  selection weight when its condition holds — e.g. likelier as MCA hearts rise or as earlier stages are
  completed (`quest_completed` `scope: giver`). Selection stays deterministic and server-authoritative.
- **Chain-aware debug commands** — `/mcaquests debug villager` now lists every chain stage the nearest villager
  could give and why each is offered / eligible / locked / hidden-superseded / completed / on cooldown;
  `/mcaquests debug quest <id>` prints the full per-quest gate checklist, per-villager chain progress, history
  counts, and effective weight/priority — built for datapack authors diagnosing stuck arcs.
- **Expanded chain validation** — `/mcaquests validate` splits **errors** (blank chain id, `stage` above
  `stage_total`, self-references, circular `prerequisites` or `unlocks`, unknown/disabled condition targets,
  impossible "completed and not-completed" gates) from non-fatal **warnings** (inconsistent `stage_total`, two
  non-branching quests sharing a stage, dangling `unlocks`, a branch gated on a quest that can never fail).
  Only errors honour `strictJsonValidation`. Every message names the quest, chain, field, and referenced id.
- **New worked arc** — `chains/mapmaker_expedition` (cartographer), a 3-stage branching arc demonstrating
  prerequisites, a `failure` deadline with `retry_after`, a `quest_failed` `scope: giver` recovery branch,
  `priority`, and hearts-based `weight_bonus`.

### Changed

- Relationship arcs are **per-villager** rather than global (see *Added*). Standalone (non-chain) quests are
  unchanged and keep global completion/cooldown semantics. The bundled `guard_safety` and `jobless_friendship`
  branch conditions now carry `scope: giver` so each arc is coherently per-villager.
- Offer selection is organised into datapack-controllable priority tiers with context-sensitive effective
  weights, generalising the previous hardcoded "chain continuations first" rule (which remains the default
  when no `priority` is set).

### Migration

- Save-data compatible with 0.1.0–0.4.0 worlds; the per-villager history maps are additive and load empty on
  older saves. **One caveat:** an arc that was *in progress* before this update restarts under per-villager
  tracking (an earlier stage completed globally no longer counts toward the new per-villager gate). Standalone
  quests, cooldowns, and already-finished arcs are unaffected.

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

[0.9.0]: https://github.com/otectus/MCAQuests/releases/tag/v0.9.0
[0.2.0]: https://github.com/otectus/MCAQuests/releases/tag/v0.2.0
[0.1.0]: https://github.com/otectus/MCAQuests/releases/tag/v0.1.0
