# 07 — Ill Omens (situations and shared voice pools)

Five **situations** and two **voice pools**. Situations are the "living village" layer: a transient,
village-shared condition opened by something that actually happens in the world, which surfaces a
time-limited quest offer on nearby villagers and then resolves with an outcome that moves reputation.

```
data/<namespace>/mcaquests/situations/**/*.json
data/<namespace>/mcaquests/dialogue/*.json
```

```
/mcaquests situation list      # loaded definitions and open instances
/mcaquests situation info illomens:the_turning
/mcaquests situation debug     # what is open in the nearest villager's village
/mcaquests situation validate  # op 3
```

## Files

| File | Trigger | Point of it |
|---|---|---|
| `wolves_under_the_full_moon.json` | `night` with **`require_full_moon`** | The simplest complete situation, and all three outcomes. |
| `the_granary_is_bare.json` | `low_food` | A situation offer that is a **template** — the ask varies per village per day. |
| `the_turning.json` | `infection` | **`situation_focus`**: the quest is about the villager who is actually turning. |
| `out_past_the_border.json` | `villager_stranded` | A `villager`-scoped rescue; `weight_bonus` on the weather. |
| `the_wake.json` | `villager_death` with `relation` | A `family`-scoped grief situation with an `hearts` outcome. |

## Anatomy

```json
{
  "id": "illomens:the_turning",
  "scope": "villager",
  "duration_ticks": 9600,
  "cooldown_ticks": 48000,
  "trigger": { "type": "mcaquests:infection", "min_progress": 0.35 },
  "outcomes": { "success": {...}, "failure": {...}, "cleared": {...} },
  "offer": { ... }
}
```

| Field | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Ship a definition without activating it. |
| `scope` | `village` | `village`, `villager` (the focal one) or `family` (an MCA lineage). Decides who surfaces the offer and where the outcome lands. |
| `duration_ticks` | `24000` | How long it stays open. **The accepted quest's deadline is anchored to this**, so the HUD countdown and the whole failure machinery come for free — you do not write a `failure` block for the timeout. |
| `cooldown_ticks` | `24000` | Per-village cooldown before this definition can open there again. |
| `trigger` | required | What opens it. |
| `outcomes` | none | `success` / `failure` / `cleared`, each `{ "reputation": int, "hearts": int }`. Reputation goes to the giver's village; hearts go to the focal villager for `villager`/`family` scopes. |
| `offer` | required | The quest body. |

**Resolution** has three ends:

- **Success** — the *first* player to complete the offer resolves it; the `success` outcome applies and
  it stops being offered. Other players keep the rewards for their own copies.
- **Failure** — the duration expires; `failure` applies and any still-active copies fail.
- **Cleared** — the underlying condition lifts by itself (the raid ends, the food recovers); `cleared`
  applies and is usually neutral. `the_granary_is_bare` pays a token 2 reputation for it, because a
  village that fed itself is still a village that got through.

## Triggers

Detection is player-proximity driven (villages near players are scanned periodically) plus event-driven
on death.

| Type | Fields |
|---|---|
| `mcaquests:raid` | — |
| `mcaquests:villager_death` | `relation` (default `any`) — narrows **who may raise it**, not which deaths open it |
| `mcaquests:infection` | `min_progress` (0–1) |
| `mcaquests:missing_kin` | `relation` — here it *does* narrow which losses fire it |
| `mcaquests:low_food` | `threshold` (banked edible items) |
| `mcaquests:night` | `require_full_moon` |
| `mcaquests:villager_stranded` | `minimum_distance` (96), `hold_ticks` (600), `require_night` (true) |
| `mcaquests:hostiles_near_home` | `count` (3), `radius` (16), `hold_ticks` (200) |
| `mcaquests:capital_interregnum`, `mcaquests:capital_war` | MCA Capitals — sample 10 |
| `mcaquests:townstead_*` | Eight more, from Townstead village state — sample 10 |

The last two rows need an optional mod. The rest, including `villager_stranded` and
`hostiles_near_home`, are **plain MCA**: home villages, borders and residency are MCA concepts, so they
behave identically whether Townstead is installed, absent, or removed mid-world.

Two design details worth copying:

- **`hold_ticks` is what makes a rescue instead of a nuisance.** A villager who stepped past the border
  for a moment is not stranded; a zombie passing a door is not a siege. The state has to still be true
  when the detector looks again.
- **`hostiles_near_home` searches a small box** around a known bed or village centre, never the world. A
  detector that swept a dimension for hostiles would be the most expensive thing the mod does, forever.

## The `offer` block

The offer is a quest body without an `id` and without a `chain`: `weight`, `title`, `giver`, `dialogue`,
`objectives`, `rewards`, `turn_in`, `template`, `conditions`, `priority`, `weight_bonus`, `difficulty`,
`offer_group` all work exactly as in a normal quest. `the_granary_is_bare` uses a full `template` block,
so one situation file produces a different ask in every village on every day.

**Situation offers pass the same gates static quests do** — conditions, cooldowns, repeat rules, the
already-satisfied check, and the check that a villager an objective names actually exists.

**Use `situation_focus` whenever the situation names somebody.**
`{ "mode": "situation_focus" }` is the villager the situation is *about* — the one who collapsed, caught
the infection, or is stranded — rather than a relative of whoever happens to be telling you. "Cure your
relative" asked of any adult in the village is a different quest from "cure the villager who is actually
infected", and only the second one is true. Outside a situation the mode resolves to nobody, which makes
the objective unofferable rather than silently pointing somewhere else.

A situation always *opens* on its signal; its scope, its `giver` gate and its `conditions` decide which
villager gets to be the one who asks.

## Throttling

`maxConcurrentSituationsPerVillage` caps how many are open at once in one village;
`situationGlobalCooldownTicks` spaces them out per village on top of each definition's own
`cooldown_ticks`; `maxSituationOffersPerMenu` caps how many one villager surfaces. Situation offers
compete with static quests and default above them via `situationDefaultPriority` — which is why the
`priority` values here (4 and 5 on the two emergencies) are worth setting deliberately rather than
letting an ordinary errand share the menu with somebody's house burning down.

Suppressed openings are logged, so if a situation "never fires" check the log before the definition.

## Voice pools

A quest's `dialogue` covers the six states a quest reaches. The two states that explain a villager having
**nothing** to offer — `cooldown` and `locked` — are reached by the *villager*, not by any one quest.
Before voice pools existed, every busy villager in the game said the same flat refusal.

```json
{
  "format_version": 1,
  "state": "greeting",
  "priority": 5,
  "lines": [
    { "when": { "type": "mcaquests:weather", "weather": "RAIN" }, "text": "Filthy weather..." },
    { "text": "Good day to you.", "weight": 2 }
  ]
}
```

| Field | Meaning |
|---|---|
| `state` | One of `greeting`, `cooldown`, `locked`, `no_quests`. **Any other value fails to load** — a pool naming a state nothing reads is a file that silently does nothing. |
| `priority` | Higher pools are consulted first, so a pack can shadow the built-in voice without deleting it. These use `5`; the bundled pools are `0`. |
| `lines[].when` | The **same condition language** quests are gated with. A line with no `when` is the fallback: always eligible, never preferred. |
| `lines[].weight` | Relative likelihood among the lines that match. |

The four states: `greeting` (the menu header when the villager *does* have offers), `cooldown` (you did
their quest too recently), `locked` (they have something you have not earned), `no_quests` (nothing, and
no more specific reason).

**A quest's own line always wins where it has one.** This is the floor, not the ceiling.

Selection is deterministic per player, villager, day and state, so reopening the menu does not re-voice a
villager. Reusing the condition language rather than inventing a dialogue mini-language is the whole
design: personality, mood, time, weather, hearts, reputation tier, relationship state, age group and
everything added later are available to dialogue for free.
