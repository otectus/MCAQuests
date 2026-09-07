# 06 — Civic Works (village projects)

Six projects that between them use all five scopes, all four reward targets, every non-Townstead project
objective, phase unlock gates, shared deadlines, and both ways one project can lead to another.

A **project** is not a quest. A quest's progress lives on one player; a project's progress is **shared** —
items donated, mobs killed and blocks placed are banked into one pool in the world save, so a whole
server (or village, or family) works towards the same thing. Projects live in their own folder:

```
data/<namespace>/mcaquests/projects/**/*.json
```

and are inspected with their own commands:

```
/mcaquests project list
/mcaquests project info civicworks:the_bathhouse
/mcaquests project debug civicworks:the_bathhouse     # why it is or is not available from the nearest villager
/mcaquests project validate
/mcaquests project reset civicworks:the_bathhouse     # clear shared progress (op 3)
/mcaquests project advance civicworks:the_bathhouse   # force a phase, testing only (op 3)
```

When a project is available, eligible **sponsor** villagers get a **View Project** button in their MCA
interaction menu. Ordinary quests stay visually simple; only sponsors show the extra button.

## Files

| File | Scope | What it is for |
|---|---|---|
| `the_bathhouse.json` | `village` | Three phases, a phase `unlock` gate, a final reward-only phase, `follow_up`. |
| `the_aqueduct.json` | `village` | A shared **deadline**, `required_count: 2`, and an `mcaquests:unlock` reward. |
| `the_lantern_rounds.json` | `village` | Seeded by the aqueduct's unlock reward. `on_death: turn_in_to_village`. |
| `the_apothecary_round.json` | `profession` | Progress shared by one trade, not the whole town. |
| `a_roof_for_the_orphan.json` | `family` | An MCA lineage shares the goal; a deadline with a retry. |
| `the_ratters.json` | `villager` | One villager's problem that anyone may help with. |
| `a_private_commission.json` | `player` | A private, one-player project — no MCA data needed at all. |

## Scope: who shares the progress

```json
"scope": "player"
"scope": { "scope": "village", "fallback_radius": 64 }
"scope": { "scope": "profession", "professions": ["minecraft:cleric"] }
```

| Scope | Progress shared by | Needs MCA data |
|---|---|---|
| `player` | one player | no |
| `villager` | one villager, and everyone who helps it | no |
| `family` | a villager's lineage | **yes** |
| `profession` | all villagers of the listed professions in the village | **yes** |
| `village` | the whole village | **yes** (MCA's stable village id, with an anchor fallback) |

When a scope needs MCA data that is not there, the project simply **never becomes available**. It fails
safe; it never crashes. `fallback_radius` is how a village is anchored when MCA village data cannot be
read (default: the server's `defaultScopeFallbackRadius`, 64).

The `family` scope uses a derived **lineage id** — the minimum UUID over the villager's ancestors. That
is a heuristic that groups a *lineage*, not strictly one household, and `a_roof_for_the_orphan.json` is
written to suit that: it is about a family, loosely, rather than about two named parents.

## Sponsors

```json
"sponsor": {
  "professions": ["minecraft:mason"],
  "required_count": 1,
  "adult_only": true,
  "pinned_sponsors": ["<uuid>"],
  "on_death": "transfer"
}
```

Omit the block entirely for "any adult villager". `required_count` is the sponsor count shown on the
card — contributions are allowed before it is reached, so it is a display target, not a gate.
`on_death` is `fail`, `pause` (the server default), `transfer` (hand off to another eligible villager) or
`turn_in_to_village`. All four appear across these files.

**Anti-flood:** with `oneSponsorPerProjectPerDay` (default on) only one deterministically chosen villager
per village offers a given project per day, so a town does not repeat the same request at you from every
doorway.

## Phases

`phases` runs **in order**; a phase is entered only when every earlier phase is complete. Each has its
own `key`, `dialogue` (the sponsor menu shows `offer` and `in_progress`), `objectives`, `rewards`, and an
optional `unlock` — an **extra** condition tree on top of "the previous phase finished".
`the_bathhouse.json` gates its second phase on `village_reputation >= 5`: the town has to care before the
walls go up.

The last phase may be a **payoff phase** with no objectives at all — `opening_day` in the bathhouse pays
everyone who ever helped and ends the project. A *non-final* phase with no objectives is a validation
warning, because nothing would ever advance it.

## Project objectives are their own set

Quest objective types are **not** valid in a project — the progress model is different. Use:

| Type | Meaning |
|---|---|
| `mcaquests:donate_item` | A player hands items to the sponsor; the stack is consumed immediately and banked. `per_player_cap` (0 = unlimited) caps one player's share. |
| `mcaquests:project_kill_entity` | Kills inside the scope, banked. |
| `mcaquests:project_place_block` | Blocks placed inside the scope. |
| `mcaquests:project_talk_to_profession` | Talk to that many **distinct** villagers of a profession. |
| `mcaquests:townstead_*_project` | Four more, polled from Townstead village state — see sample 10. |

Targets take an id or a tag exactly as elsewhere (`"item"`/`"tag"`, `"block"`/`"tag"`, `"entity"`/`"tag"`).
`per_player_cap` is the difference between a community goal and a rich player finishing it alone in one
trip; the bathhouse caps stone at 64 per person out of 128.

## Shared rewards and who gets them

A shared reward **wraps a quest reward** and adds a `target`:

```json
{ "reward": { "type": "mcaquests:item", "item": "minecraft:emerald", "count": 6 }, "target": "top_contributor" }
```

| `target` | Receives |
|---|---|
| `contributors` (default) | Players who helped **this phase**. |
| `all_participants` | Everyone who ever helped the project. |
| `sponsor_village` | The village — used for reputation. |
| `top_contributor` | The single biggest helper of that phase. |

The wrapped reward may be any quest reward (`item`, `xp`, `xp_levels`, `hearts`, `effect`, `loot_table`,
`command`) or one of the project-only ones: `hearts_with_sponsor`, `hearts_with_participants`
(with `include_residents`), `village_reputation`, and `mcaquests:unlock`.

`mcaquests:unlock` seeds another project **in the same scope** — the project reward distributor does the
work, so the wrapper's `target` is only about who is credited; `sponsor_village` is the honest label for
something that happens to the town rather than to a player. `follow_up` at the top level does the same
job unconditionally when the project completes. Both must name a project with the **same scope**, or
validation rejects them.

**Distribution happens once, when a phase completes.** Online players are paid immediately; offline
players' non-hearts rewards are queued and delivered on next login; hearts for unloaded villagers are
queued through MCA. Currency amounts are frozen per phase, so somebody who was offline and collects later
is paid the same as everybody else.

## Failure on a shared project

Projects accept the same `failure` fields as quests, with shared semantics: the clock starts when the
instance is created, `deadline_ticks`/`deadline_time`/`require_weather` are checked before accepting
contributions and on the project sweep, and a completed final phase is protected from a late deadline
check. `fail_on_giver_death` and `fail_on_target_lost` refer to the project's **bound sponsors**;
otherwise `sponsor.on_death` is what handles losing one.

Failure applies `failure_hearts` between participants and sponsors (banking hearts for offline players),
applies `reputation.on_fail`, fires the failure event, and notifies online participants. `retry_after`
permits a fresh instance later — and a new attempt **does not inherit contributions** or the rewards owed
by the old one. Omitting `retry_after` on a project permits an immediate fresh attempt (this is the
opposite of a quest, where omitting it means "follow the repeat rule").

A paused instance — missing definition, missing dimension, changed scope, a disabled project, an
unavailable required integration — **freezes its deadline** rather than running it down.

## Reputation deltas

```json
"reputation": { "on_phase_complete": 3, "on_project_complete": 12, "on_fail": -4 }
```

This is the mod's own village reputation (sample 08), not MCA hearts. It is readable from any condition
tree with `mcaquests:village_reputation`, which is exactly how `the_aqueduct` gates itself behind the
bathhouse having raised the town's standing.

## Storage and multiplayer

Shared progress lives in `<world>/data/mcaquests_projects.dat`, keyed by *(project id, scope,
scope-identity)*. It survives logout, death, dimension change, villager or chunk unload, and restarts.
Contributions are atomic and server-authoritative — items are validated and consumed server-side, then
banked, then synced; duplicate reward claims and packet spam are stopped by a one-shot per-phase
distribution plus the `projectContributeMinIntervalTicks` rate limit.

`maxConcurrentProjectsPerScope` (default 8) caps how many can run in one scope at once. Lowering it never
hides a project already under way.
