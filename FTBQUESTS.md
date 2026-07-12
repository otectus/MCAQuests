# MCA: Quests — FTB Quests Integration

An **optional** bridge between MCA: Quests and **[FTB Quests](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge)**: FTB book authors get ten task types and three reward types that read and write real MCA: Quests progress (villager quests, relationship arcs, village reputation, titles, projects, situations, hearts, marriage), and MCA: Quests pack authors get three conditions, an objective, and a reward that read and write FTB Quests' own progress. Neither side requires the other to be installed — everything here degrades gracefully when FTB Quests is absent.

## Install

Drop **FTB Quests** (and its own dependencies, FTB Library and FTB Teams) into `mods/` alongside MCA: Quests and MCA Reborn. Tested against **FTB Quests 2001.4.x** (`ftb-quests-forge`), FTB Library 2001.2.9, FTB Teams 2001.3.0. MCA: Quests declares FTB Quests as an **optional** dependency (`mandatory=false`, version range `[2001.4,)`) — it is never required, and nothing else about MCA: Quests changes if you don't install it.

MCA: Quests **compiles against FTB's publicly published maven artifacts and ships none of them** — the jar contains zero FTB bytes; see the licensing note in the [README](README.md#license--credits).

## How it works

MCA: Quests' own task/reward/condition/objective types stay registered whether or not FTB Quests is present (so nothing about MCA: Quests' own datapack format ever depends on FTB being installed), but the FTB-facing task/reward classes only *do* anything when FTB Quests has actually loaded — every call into FTB code goes through an internal bridge that becomes an inert no-op (with a debug log, never a crash) if FTB Quests is missing, disabled, or throws. Practically: your FTB book edits, quest datapacks, and world save are all safe to author and share regardless of who else has the integration turned on.

Master switch: `enableFtbQuestsIntegration` in `mcaquests-common.toml` (see [CONFIG.md](CONFIG.md#compatftbquests)). Turning it off keeps every type registered (so the book still loads and network stays in sync) but freezes MCA-side progress and no-ops MCA-side rewards.

---

## FTB Quests task types (`mcaquests:` namespace)

Add these from the FTB Quests editor's task-type picker. Every task tracks **real MCA: Quests state**, never client input — the server recomputes the value from your save data on every check. They're per-team like every FTB task (see [Team-credit semantics](#team-credit-semantics) below).

| Type | Base | Fields (SNBT key · type · default) | Value / check |
|---|---|---|---|
| `mcaquests:quest_completed` | Counter | `quest_id·String·""` (exact id, `ns:*` wildcard, or empty = any), `profession·String·""`, `chain_id·String·""`, `category·String·""`, `count·long·1` | Number of matching completed villager quests (repeats count, capped at `count`). Situation offers count too — filter by giver profession/category. |
| `mcaquests:chain_completed` | Boolean | `chain_id·String·""` (required in practice) | True once the player has completed the **final** stage of that relationship arc. |
| `mcaquests:reputation` | Counter* | `reputation·int·100` (threshold), `village_count·int·1` | Reaches `reputation` standing in `village_count` independent villages. *Progress bar shows the best village's reputation when `village_count == 1`, or the qualifying-village count otherwise. |
| `mcaquests:reputation_tier` | Boolean | `ladder·String·"mcaquests:default"`, `tier·String·""`, `village_count·int·1` | `village_count` villages have reached tier `tier` (or higher) on `ladder`. |
| `mcaquests:title` | Counter | `title_id·String·""` (empty = any), `count·long·1` | A specific title held (global or in any village), or the total number of distinct titles held. |
| `mcaquests:project_completed` | Counter | `project_id·String·""` (empty = any), `count·long·1` | Village projects completed (credited to online participants at completion time). |
| `mcaquests:project_contribution` | Counter | `project_id·String·""`, `count·long·64` | Units contributed to a project (items/kills/blocks/talks banked by that project's objectives). |
| `mcaquests:situation_resolved` | Counter | `situation_id·String·""` (source situation id; empty = any), `count·long·1` | Situations **successfully** resolved (failures/clears don't count). |
| `mcaquests:hearts` | Boolean | `hearts·int·100`, `spouse_only·boolean·false` | Hearts with a nearby villager reach the threshold — see [Hearts must be witnessed](#faq) below. |
| `mcaquests:married` | Boolean | *(none)* | The player is married to an MCA villager. |

Every editor field that references an MCA id (`quest_id`, `chain_id`, `ladder`/`tier`, `title_id`, `project_id`, `situation_id`) shows **two rows** in the FTB editor: a free-text field and, once your client has synced ids from a server (on login or `/mcaquests reload`), a matching dropdown underneath it. Both rows edit the same field — use whichever is convenient, and free-text always works (e.g. for ids from a datapack your client hasn't loaded yet).

Notes on a few of the trickier ones:

- **`hearts` / `spouse_only`.** With `spouse_only` on, the check looks at the best-hearts loaded villager near the player and confirms that villager is the spouse and above the threshold, rather than separately locating the spouse — the same one bounded scan either way, and in practice the highest-hearts nearby villager *is* the spouse. See [Hearts must be witnessed](#faq).
- **`reputation_tier` only counts "touched" villages.** A village that has never had a reputation event (no situation, project, or quest has touched it yet) has no reputation *record* at all — it isn't an implicit zero that counts toward `village_count`, it simply isn't examined until the first reputation write happens there. In practice this only matters for a brand-new world before any village has interacted with a player.
- **`reputation`'s completed state latches.** Village reputation can go down (a failed situation, for example), but once this task has completed it stays completed — matching FTB's own behavior for every stat-style task. Use `/ftbquests change_progress ... reset` plus `/mcaquests ftbq recheck` if you need to force a re-check.
- **`'|'` is reserved.** Chain ids and reputation-tier ids may never contain a literal `|` character — it's the separator MCA: Quests uses internally for the ladder/tier sync payload. Datapacks are rejected at load if they use one; author around it.

### FTB Quests reward types (`mcaquests:` namespace)

| Type | Fields | Claim behavior |
|---|---|---|
| `mcaquests:village_reputation` | `amount·int·10` (may be negative, clamped ≥ −1000), `target·enum·NEAREST` (`NEAREST`, `HIGHEST_REPUTATION`) | Awards reputation to the resolved village through the normal `ReputationService` path (tier-ups and toasts fire normally). No village found nearby → **banked** (see below). |
| `mcaquests:hearts` | `amount·int·10`, `target·enum·NEAREST_VILLAGER` (`NEAREST_VILLAGER`, `SPOUSE`, `VILLAGE_RESIDENTS`) | `NEAREST_VILLAGER` finds the nearest loaded **adult** MCA villager within 16 blocks (a nearby child is skipped in favor of a slightly farther adult); `VILLAGE_RESIDENTS` pushes hearts to every resident of the nearest village, loaded or not; `SPOUSE` needs the spouse loaded nearby. The same `heartsRewardMultiplier`/min/max clamp as the native `mcaquests:hearts` reward applies. No target found → banked. |
| `mcaquests:grant_title` | `title_id·String·""` (required), `scope·enum·GLOBAL` (`GLOBAL`, `VILLAGE`) | Grants the title via the normal `TitleService` path. `VILLAGE` scope with no village nearby → banked. Unknown title id → a warning to the player and the server log, no-op (also flagged by `ftbq validate`). |

The `target`/`scope` enum values are persisted in SNBT as the constant names shown above (e.g. `NEAREST_VILLAGER`), but parsing is **case-insensitive** — a hand-edited `nearest_villager` works too; a genuinely unrecognized value falls back to the default (with a debug log line).

**Banked rewards.** When a reward can't find its target (no village or villager nearby to receive it), it is never silently dropped — it's queued in the player's persistent pending-rewards list and automatically re-delivered **on that player's next login, and again once per in-game day while they're online**, until a target resolves. The player is told the reward is banked at claim time.

**FTB per-team/per-player claiming.** Whether a reward pays out once per team or once per player is entirely FTB's own reward-claim setting (untouched by this integration) — our rewards default to whatever FTB's own default is.

---

## MCA: Quests condition, objective, and reward (read/write FTB state)

These live on the **MCA: Quests datapack side** — they're registered whether or not FTB Quests is installed, so a datapack using them loads identically either way; they simply always evaluate to their `when_missing` fallback when FTB Quests isn't there to ask.

### Conditions — gate a quest offer on FTB book state

| `type` | Fields |
|---|---|
| `mcaquests:ftbq_quest_completed` | `quest` (FTB hex id), `when_missing` (`not_met`/`met`, default `not_met`) |
| `mcaquests:ftbq_chapter_completed` | `chapter` (FTB hex id), `when_missing` |
| `mcaquests:ftbq_task_completed` | `task` (FTB hex id), `when_missing` |

The id is FTB's own 16-hex-digit code string, copied from the FTB editor (a leading `#` is tolerated). `when_missing` is the result whenever the real state can't be checked — FTB Quests absent, the integration disabled, the id not resolving to anything in the loaded book, or any internal failure. This one knob covers both "bonus quest that vanishes without the book" (`not_met`) and "catch-up quest hidden once the book chapter is done" (`met`, usually combined with `not`).

### Objective — `mcaquests:ftbq_complete_quest`

Satisfied once the player's FTB team completes the linked FTB quest.

| Field | Default | Meaning |
|---|---|---|
| `quest` | (required) | FTB hex id. |
| `already_complete` | `satisfy` | `satisfy`: an FTB quest already done before this MCA quest was accepted satisfies the objective immediately. `block_offer`: the quest is additionally hidden from the offer pool once the FTB quest is done (desugars into an implicit `not(ftbq_quest_completed)` condition). |
| `display_name` | *(none)* | Optional text naming the FTB quest in the objective line (MCA: Quests can't read FTB's own quest titles cheaply). Falls back to "Complete the linked FTB quest (\<hex id\>)". |

If FTB Quests isn't installed, a quest using this objective is **skipped at load** (under lenient validation, with a log line) rather than entering the offer pool unsatisfiable — under strict validation it's a load error instead.

### Reward — `mcaquests:ftbq_progress`

Pushes progress into the FTB book at turn-in.

| Field | Values |
|---|---|
| `action` | `complete_task`, `complete_quest`, `reset_task` |
| `id` | FTB hex id of the target task/quest |

Gated by `allowFtbqProgressRewards` (default on) — when disabled, the reward card still shows its description to the player, but the grant itself silently no-ops.

### Example: FTB book chapter gated on villager standing (SNBT)

```snbt
{
    title: "A Friend of Oakvale"
    tasks: [{ id: "5F3C0A1B2D4E6F70", type: "mcaquests:reputation_tier", ladder: "mcaquests:default", tier: "friend", village_count: 1 }]
    rewards: [{ id: "70E1D2C3B4A59687", type: "mcaquests:hearts", amount: 15, target: "NEAREST_VILLAGER" }]
}
```

### Example: villager quest gated on the FTB book (datapack JSON)

```json
{
  "id": "mypack:archivist_bonus",
  "giver": { "professions": ["minecraft:librarian"], "min_hearts": 20 },
  "dialogue": { "offer": {"text": "You finished the Ancient Tome chapter? Then you're ready for this."}, "...": "..." },
  "conditions": { "all_of": [
      { "type": "mcaquests:ftbq_chapter_completed", "chapter": "0123456789ABCDEF", "when_missing": "not_met" } ] },
  "objectives": [ { "type": "mcaquests:ftbq_complete_quest", "quest": "1A2B3C4D5E6F7081",
      "display_name": {"text": "the Librarian's Challenge"}, "already_complete": "block_offer" } ],
  "rewards": [ { "type": "mcaquests:hearts", "amount": 30 },
               { "type": "mcaquests:ftbq_progress", "action": "complete_task", "id": "F00DF00DF00DF00D" } ]
}
```

---

## Team-credit semantics

The single most important thing to understand before building a book around this. FTB Quests' whole model is **per-team**, not per-player, and this integration doesn't fight that:

1. **The ten `mcaquests:` FTB tasks complete for the team when any one member meets the bar.** A counter task's progress bar shows the **best individual member's** value, recomputed from scratch every time it's checked (never accumulated from events) — so pre-existing history counts the instant the quest unlocks, exactly like FTB's own stat/advancement tasks. Two consequences worth planning around: (a) `/ftbquests change_progress ... reset` will simply **re-complete on the next check** unless the underlying MCA: Quests state is also reset — use `/mcaquests ftbq recheck` to force an immediate re-evaluation either way; (b) when two teams **merge**, FTB keeps the **higher** of the two teams' progress, never the sum, and leaving a team leaves your progress on the old team behind — this is FTB's own merge/leave behavior, unmodified.
2. **The three `ftbq_*` conditions and the `ftbq_complete_quest` objective read team state**, not just the interacting player's — if any party member has completed the linked FTB quest, the condition is met / the objective is satisfied for everyone in the party. If you don't want that, gate the villager quest with an MCA-native condition instead (or don't party up).
3. **The three FTB-side `mcaquests:` rewards (hearts, village reputation, titles) affect only the claiming player** — these are inherently per-player things in MCA (or village-scoped, for reputation), so claim multiplicity is controlled entirely by FTB's own per-team/per-player reward setting.
4. **The `mcaquests:ftbq_progress` reward affects the turn-in player's team** — there's no other target under FTB's model.

---

## What happens when things aren't installed, or come and go

| Scenario | What happens |
|---|---|
| **FTB Quests not installed** | The bridge is never touched; `ftbq_*` conditions and the `ftbq_complete_quest` objective follow their documented fallback (`when_missing`, or the quest is skipped at load); `ftbq_progress` rewards silently no-op; `/mcaquests ftbq *` commands report "not installed". Nothing else about MCA: Quests changes. |
| **FTB Quests installed, integration disabled** (`enableFtbQuestsIntegration = false`) | Task/reward types stay registered (so the book still opens and client/server stay in network-sync), but tasks never progress and rewards no-op with a **one-time server-log WARN** (never a message shown to the player — worth knowing if you disable this mid-pack and get support questions about "stuck" quests). |
| **FTB Quests removed after a book was built with `mcaquests:` tasks/rewards** | FTB's own fallback keeps them as inert "Unknown type" placeholders — no crash. **If someone opens the editor and hits Save in that state, FTB strips the unknown fields from those tasks/rewards permanently.** Back up `config/ftbquests/quests/` before removing the mod if you might reinstall it later. |
| **MCA: Quests present, FTB Quests removed later** | Any datapack `ftbq_*` conditions/objective fall back as documented above; an active `ftbq_complete_quest` quest simply never satisfies (the player can abandon it); nothing FTB-specific is stored in the MCA: Quests world save, so nothing there is at risk. |
| **`/ftbquests change_progress` used to reset a task** | The task re-completes on its next check unless the underlying MCA state changed too — see [Team-credit semantics](#team-credit-semantics). |
| **Client/server FTB Quests mismatch** | Unaffected by this integration specifically — Forge's own mod-list handshake catches it, same as any other mod. A client/server MCA: Quests **version** mismatch is caught by the network protocol handshake (bumped to `"5"` for this release; see the [changelog](CHANGELOG.md)). |

---

## Commands

All under `/mcaquests ftbq`:

| Command | Permission | What it reports |
|---|---|---|
| `ftbq status` | 2 | Whether FTB Quests is detected (and its version), whether the bridge is active, whether the integration master switch is on, how many `mcaquests:` tasks/rewards are in the current book, and whether editor-id sync is on. |
| `ftbq validate` | 3 | Two sweeps, with different severities. **Book → MCA:** every FTB task/reward referencing an MCA quest/chain/ladder/tier/title/project/situation id names the chapter, quest, and field. **MCA → book:** every loaded quest using an `ftbq_*` condition, `ftbq_complete_quest`, or `ftbq_progress` whose FTB hex id doesn't resolve names the quest and field. |
| `ftbq recheck [player]` | 2 | Forces an immediate re-evaluation of every `mcaquests:` task for the target player (default: yourself). Use this after `/ftbquests change_progress` or any other admin state edit, to get an immediate answer instead of waiting for the next poll. |

**Severity model for `ftbq validate`:** a finding is an **error** only when the id itself is malformed — it could never resolve under any registry state (not a valid 16-hex-digit FTB code, or similar). A **well-formed id that just doesn't currently resolve** — a forward reference to a quest/chapter/task or an MCA id that hasn't been built yet — is a **warning**, in *both* directions. That's deliberate: FTB book authors legitimately reference MCA ids from datapacks they haven't written yet, and datapack authors legitimately reference FTB book content that hasn't been built yet. Only genuinely broken ids fail the command; forward references just get flagged for awareness.

All three commands print a plain "not installed" / "disabled" line instead of erroring when FTB Quests isn't present or the integration is off.

---

## FAQ

**If I reset an FTB task's progress, does it come back?** Yes — the ten FTB tasks recompute from your real MCA: Quests state every time they're checked, they don't remember an old value. `/ftbquests change_progress ... reset` (or any FTB-side reset) is undone on the very next poll unless the *underlying* MCA state changed too. Use `/mcaquests ftbq recheck` to force that check immediately instead of waiting for the poll interval.

**Does completing content before I add a book credit retroactively?** Yes, by design — same as FTB's own stat and advancement tasks. The first time a task is checked (typically the moment the quest unlocks for a player), it reads your existing MCA: Quests history/state, not just things that happen afterward. If you don't want that for a specific task, gate the quest with an FTB condition so it doesn't unlock until some later point.

**Does offline progress count?** Mostly, with one exception. Village reputation, project completions, and title grants are recorded in the world save regardless of who's online, so an FTB task checking those picks them up on your next login (`checkOnLogin` covers this). The one thing that genuinely needs you online and nearby is hearts (see below) — MCA has no "hearts changed" event to push from, so the `mcaquests:hearts` task is entirely poll-driven.

**Why does the hearts task need the villager "witnessed"?** There's no MCA event for a hearts change, so `mcaquests:hearts` can only measure hearts by scanning nearby loaded villagers on its own poll (every 5 seconds by default, radius configurable). In practice this is rarely a problem — you gain hearts by interacting with a villager, which means you're standing right next to them when it happens — but it does mean the task won't complete purely from an offline or far-away change; the qualifying villager has to be loaded near you at some poll tick after you cross the threshold.

**My book quest still shows as an unknown type after I removed MCA: Quests — is my book broken?** No — that's FTB Quests' normal handling of an unrecognized type, and it's harmless as long as nobody **saves** the book in the editor while it's in that state (see the backup warning above).

---

See [DATAPACK.md](DATAPACK.md) for the rest of MCA: Quests' own datapack format, and [CONFIG.md](CONFIG.md#compatftbquests) for every `[compat.ftbquests]` config option and its clamp range.
