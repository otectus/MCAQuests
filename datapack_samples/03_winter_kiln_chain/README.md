# 03 — The Winter Kiln (chains, deadlines and branches)

A three-stage mason arc that can go wrong, plus two standalone quests that branch on what the player
did last time. This is the sample for **`chain`**, **`failure`**, **`priority`** and **`weight_bonus`**.

## The arc

```
1_clay_from_the_bank ──> 2_fire_the_kiln ──(completed)──> 3_the_glazed_hearth
                              │                              ▲
                              └────(failed)──> 2b_make_amends┘
```

| Stage | File | Point of it |
|---|---|---|
| 1 | `chains/winter_kiln/1_clay_from_the_bank.json` | An ordinary quest with a `chain` block bolted on. |
| 2 | `chains/winter_kiln/2_fire_the_kiln.json` | Has a **deadline**: finish before dawn or it fails. |
| 2b | `chains/winter_kiln/2b_make_amends.json` | Only exists *because* you failed stage 2. |
| 3 | `chains/winter_kiln/3_the_glazed_hearth.json` | The finale both branches converge on. |

## Chains

```json
"chain": {
  "chain": "winterkiln:the_winter_kiln",
  "stage": 2, "stage_total": 3,
  "relationship_arc": { "text": "The Winter Kiln" },
  "chapter": { "text": "Fire the Kiln" },
  "prerequisites": ["winterkiln:kiln_1_clay_from_the_bank"],
  "unlocks": ["winterkiln:kiln_2b_make_amends", "winterkiln:kiln_3_the_glazed_hearth"]
}
```

- **Arcs are per villager.** `prerequisites` compile into `quest_completed` with `scope: "giver"`, so
  finishing stage 1 with *this* mason is what unlocks stage 2 from *this* mason. The same arc can be
  lived out independently with a mason in the next village. Give your own branch conditions the same
  `"scope": "giver"` or the arc leaks across villagers — every condition in this pack does.
- **A villager offers only the furthest unlocked stage** of a chain at a time.
- **`unlocks` does not gate anything.** The gate always lives on the downstream quest
  (`prerequisites`, or a `conditions` block). `unlocks` exists so validation can prove every stage is
  reachable and that there are no cycles. It is why stage 3 is listed in *both* branch stages.
- **Converging branches**: stage 3 has **no** `prerequisites`. It uses
  `any_of [ quest_completed 2 (giver), quest_completed 2b (giver) ]` instead, which is the documented way
  to let two paths meet. Prerequisites are an AND; this needs an OR.
- Chain stages are the one exception to "a quest can only be active with one villager at a time".

## Failure blocks

A quest with no `failure` block never fails on its own. `2_fire_the_kiln` declares one:

```json
"failure": {
  "deadline_time": 23000,
  "fail_on_giver_death": true,
  "failure_hearts": -4,
  "retry_after": 48000
}
```

**Triggers** (declare at least one — a `failure` block with none is a validation error):

| Trigger | Meaning |
|---|---|
| `deadline_ticks` | Elapsed ticks since **accept**. 24000 = one day. Sleeping does not affect it. |
| `deadline_time` | The next time the **world clock** reaches this time of day. `23000` = "before sunrise". Sleeping through the night *does* advance it. |
| `require_weather` | The quest demands `clear`/`rain`/`thunder` and fails the moment the weather stops matching. |
| `fail_on_giver_death` | The giver dying ends it, regardless of the server's global setting. |
| `fail_on_target_lost` | The villager an objective bound is gone for good. **Off by default**, because the standing contract is that a quest you cannot currently play is *suspended*, not taken away. |

**Outcomes**: `failure_hearts` (negative = a relationship cost; `0` = non-punitive), and then exactly one
of `retry_after` (ticks before it can be offered again) or `block_retry` (never again). Setting both is a
validation error — they contradict.

**Grace window:** a quest whose objectives are already met is *not* failed by a deadline or weather
trigger. Once you have the bricks you keep the reward as long as you hand in. Giver death still fails it.

The **failure message** is the quest's `dialogue.failed` line. Write one; the default is a generic line
and this is the moment a player most wants to be told what happened.

## Recovery and preference branches

There is no "recovery quest" field. A recovery quest is an ordinary quest gated on the failure:

```json
"conditions": { "type": "mcaquests:quest_failed", "quest": "winterkiln:kiln_2_fire_the_kiln", "scope": "giver" }
```

The four history conditions are `quest_completed`, `quest_not_completed`, `quest_failed`,
`quest_abandoned` and `quest_declined`, each with `scope` `global` (default, across all villagers) or
`giver` (this villager only).

`the_softer_offer.json` uses **`quest_declined`** — the player turned `dry_the_moulds` down, so the mason
offers the version with no deadline. Declining costs nothing in this mod, so branch on it as a
*preference*, never as a punishment.

`dry_the_moulds.json` shows the **weather** pair that must always be written together: a `conditions`
weather gate (so it is only *offered* when the sky is clear) **and** a `require_weather` failure trigger
(so it ends when the sky turns). Only one of those is not enough — the first without the second offers a
quest that can never fail, the second without the first offers one that fails instantly.

## Which offer wins: `priority` and `weight_bonus`

- **`priority`** groups offers into tiers, and a villager fills its three slots from the highest tier
  down. Unset, a chain continuation (stage > 1) defaults to tier 1 and everything else to tier 0, so an
  arc in progress outranks unrelated errands. `2_fire_the_kiln` sets `2` and the finale `3` so the arc
  finishes rather than being crowded out. Setting `0` on a continuation opts it *out* of the default
  preference.
- **`weight_bonus`** adds to the base `weight` when a condition holds, so an offer grows likelier as the
  relationship deepens:

```json
"weight_bonus": [
  { "when": { "type": "mcaquests:hearts", "min": 40 }, "amount": 6 },
  { "when": { "type": "mcaquests:quest_completed", "quest": "...", "scope": "giver" }, "amount": 4 }
]
```

`amount` may be negative to make something rarer. Grouping (`offer_group`) happens *inside* a tier, never
across tiers, so an emergency can never be crowded out by variety.

## Debugging an arc

```
/mcaquests debug villager      # every stage the nearest villager could give, and why each is or is not offered
/mcaquests debug quest winterkiln:kiln_3_the_glazed_hearth   # the full gate checklist for one quest
/mcaquests validate
```

`validate` reports as **errors**: unknown or disabled prerequisite/unlock/condition targets, a blank chain
id, `stage` below 1 or above `stage_total`, a quest listing itself, an unreachable later stage, circular
prerequisites, and impossible gates. As **warnings**: an inconsistent `stage_total`, two *non-branching*
quests sharing a stage, an `unlocks` pointer the target never references back, and a branch gated on a
quest that can never fail (which is why stage 2 having a `failure` block is what makes 2b legal).
