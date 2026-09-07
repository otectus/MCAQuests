# 04 — Kinbound (MCA family conditions and villager targets)

Seven quests about somebody's relatives. This is the sample for the two halves of MCA-aware content
that must always agree with each other:

- **conditions** that ask *does this villager have the relative my quest is about?*
- **villager targets** that later say *and this is the one I mean*.

## Files

| File | The capability it exists to show |
|---|---|
| `a_name_on_the_stone.json` | `related_villager_status` with `dead`; `grandparent`, which is deliberately **not** part of `any`; a `bed` anchor that names whose bed. |
| `fever_in_the_family.json` | `cure_villager` and `heal_entity` on a **bound** relative, with the infection gate the loader insists on. |
| `the_long_way_back.json` | `find_missing_relative` — the objective that materialises a villager MCA says is missing. |
| `a_word_to_your_sister.json` | `deliver_to_villager` to one specific sibling, gated on `same_village`. |
| `mind_the_little_one.json` | `protect_entity` + `defend_villager` on the same bound child, `personality`, `fail_on_target_lost`. |
| `the_quiet_evening.json` | `is_player_spouse`, `relationship_state`, `mood`, and `sleep_or_rest`. |
| `walk_me_home.json` | A **lead**-style escort, gated on `giver_distance_from_village` the way the docs prescribe. |
| `the_trade_she_left_you.json` | A **teen** giver: `adult_only: false` paired with an `age_group` condition. |

> The bundled pack has its own `relations/` folder (37 quests) covering similar ground — `widow_memorial`,
> `cure_infected_kin`, `missing_child_search`, `protect_my_child`, `letter_to_brother`, `walk_me_to_bed`.
> These samples do not replace them; they are written to show every family-facing *field* in one place
> with the reasoning attached. Read them alongside the bundled ones.

## Villager targets

Every field named `villager` / `recipient` / `relative` selects a villager **relative to the giver**:

```json
{ "mode": "self" }
{ "mode": "profession", "profession": "minecraft:weaponsmith" }
{ "mode": "family", "relation": "sibling", "require": "same_village" }
{ "mode": "situation_focus" }
{ "mode": "capital_role", "role": "sovereign" }
{ "mode": "uuid", "uuid": "<uuid>" }
```

`relation` is `any`, `spouse`, `parent`, `child`, `sibling` or `grandparent`. **`any` means immediate
family only** — spouse, parents, children, siblings. `grandparent` is a two-hop walk and must be asked
for by name, which is why `a_name_on_the_stone` says so explicitly. (`situation_focus` only means
anything inside a situation — sample 07; `capital_role` needs MCA Capitals — sample 10.)

**One villager, for the whole quest.** A `family` target is bound to one concrete villager the moment the
quest is accepted, and never re-resolves. Without that, a giver with two children could have the log
naming one, the highlight glowing another, and the hand-off crediting either. It also means delivering
to *a* sibling is not enough — it must be **the** sibling the quest named. If that villager dies or
leaves for good the objective **pauses**; it does not fail unless you asked for
`failure.fail_on_target_lost`.

The active quest line resolves the target's real name and home village ("Deliver 1x Paper to Hans (the
quest giver's sibling) — Oakvale"), outlines them through walls while they are loaded, and gives the HUD
tracker a live distance and bearing. That outline is sent to the quest owner alone.

## `require`, and the gate the loader demands

A target states which villagers it is willing to be about, with `require` (default `reachable`):

| `require` / `status` | Means |
|---|---|
| `reachable` | Not dead, not one of MCA's invented filler ancestors, not a player, and either loaded or on some village's resident roll. |
| `alive` | Not dead. Includes the missing. |
| `nearby` | Alive and within interaction range of the giver. |
| `same_village` | Alive **and** on the giver's own roll. The "alive" half matters: MCA never prunes the dead from a roll. |
| `missing` | Alive, no body anywhere in the world, on no village's roll. |
| `dead` | Flagged deceased, excluding MCA's invented ancestors. |
| `infected` | Alive and part-way through MCA's zombie infection **right now**, read off a loaded body. |
| `any_known` | Anyone with a family-tree node at all. The old unfiltered behaviour. |

**A quest whose target must be findable needs a matching `related_villager_status` gate**, on the same
relation, as a plain leaf (top level or inside `all_of`). Every quest here has one. Two things that do
*not* count:

- a leaf inside `any_of` — an alternative is not a guarantee;
- `is_family_member` — that asks how the **player** is related to the **giver**, the opposite direction.
  `the_trade_she_left_you.json` uses it correctly (the giver *is* the player's child), and it says
  nothing about whether the giver has a findable relative.

A gate on a *narrower* relation does count: proving a sibling exists proves a member of `any` exists.
The reverse does not, and `grandparent` covers neither.

Without the gate the reload reports the file, the objective index, the relation, and the block to add.
It is a warning today and a hard error under `strictJsonValidation`.

`cure_villager` about a relative has a **stricter** rule: it must have an infection gate — either
`"require": "infected"` on the target or a `related_villager_status ... infected` condition.
`fever_in_the_family.json` uses both. Conditions are evaluated at offer time only, so the gate is the one
moment anything asks whether the kin is actually turning.

## `find_missing_relative`

MCA's `missing` means *in the family tree, not deceased, and no entity anywhere in the world* — there is
nobody to walk up to. The objective **materialises** them: once the player is inside the named
`biome`/`structure` and at least `min_distance` from the giver, the relative appears `spawn_distance`
blocks away with their real identity (same UUID, name, gender, profession), is highlighted, and completes
within `discover_radius`.

- It never spawns twice, and never spawns somebody who is merely unloaded — an alive villager on any
  village's roll is skipped.
- Two players searching for the same relative produce exactly one villager; both complete on proximity.
- **Finding somebody is permanent.** Abandoning or failing the quest drops the quest, never the villager.
  Once found, `missing` flips to false, so a `once` search quest stops being re-offered and later quests
  can `escort_entity` or `deliver_to_villager` them through the ordinary `family` path.

Note the nesting: `relative`, `biome` and `structure` are **objects** here
(`"biome": { "tag": "minecraft:is_forest" }`), not bare strings.

## Escorts

`walk_me_home.json` is a **lead** escort: `"lead": true` makes the *villager* walk to the destination,
re-pathing every tick and pausing whenever the player is more than `wait_distance` blocks away — so the
player has to stay close and guard it. The default (`follow`) is the opposite: the player leads and the
villager trails.

Three things that bite:

1. **The destination is frozen on accept**, so a `nearest_village` or relative anchor cannot drift.
2. **Arrival is border-aware.** For a `home_village`/`nearest_village` anchor the villager only has to be
   *inside the village border*; every other anchor is a horizontal (Y-ignored) distance within `radius`.
3. **`min_journey`** (default: the server's `minEscortJourney`, 24) is how far the escortee must *start*
   from the destination. Below it the quest is not offered at all, and arrival is not credited until they
   have genuinely been that far away — otherwise a villager standing on their own doorstep completes it
   on the first poll and the reward is free.

**Say whose bed you mean.** `bed` and `workstation` default to *the giver's*. That is right for "walk me
home" and silently wrong the moment the escortee is somebody else — through 1.5.0 a bundled quest sent
the player to an ageing parent's *child's* house. An escort whose escortee is not the giver and whose
destination is an unowned `bed`/`workstation` is now a **load error**. Both anchors in this pack name
`{ "mode": "self" }` explicitly even where it is the default, because it reads better and survives being
copied into a quest where it is not.

There is also a **staged** escort, which you get automatically when `lead` is on and the escortee is not
the giver: the escortee waits invulnerable and motionless until the player comes within `wait_distance`,
and only from that point does its death fail the quest. `stage_until_near` forces that on or off.

## Ages

`adult_only` defaults to `true` and is the only age restriction the mod applies. Turning it off makes
**every** MCA age eligible, `baby` and `toddler` included — a quest written in a teenager's voice offered
by an infant. `the_trade_she_left_you.json` therefore pairs `"adult_only": false` with an `age_group`
condition, which is exactly what the loader warns you to do. (MCA has no `elder` age; its ages are baby,
toddler, child, teen, adult.)

## Everything fails safe

A non-MCA giver, a half-loaded family graph, or any internal MCA error evaluates to **not met** plus a
debug log line — never an exception. `health_below`, `related_villager_status` and friends read live
state, so a quest can appear and disappear as that state changes; reopen the menu to refresh, and use
`/mcaquests debug quest kinbound:the_long_way_back` to see the whole gate checklist rather than guessing.
