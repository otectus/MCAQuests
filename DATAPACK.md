# MCA: Quests — Datapack Format

Quests are data-driven. Drop JSON files into a datapack (or this mod's bundled data) at:

```
data/<namespace>/mcaquests/quests/**/*.json
```

One quest per file. The file path is cosmetic — a quest's identity is its `id` field. After editing, run `/reload` (or `/mcaquests reload`); check results with `/mcaquests validate` and `/mcaquests list`. `/mcaquests export-schema` writes a working example to `config/mcaquests/example_quest.json`.

Malformed or unknown quests are skipped with a logged error and listed by `/mcaquests validate`; the rest still load. Set `strictJsonValidation = true` in the config to treat any bad quest as a hard error instead.

---

## Adding quests with a datapack (walkthrough)

You don't need to make a mod — quests load from any datapack. To add your own pack of quests to a single world:

1. **Make the folder structure** inside the world's `datapacks` directory (`saves/<world>/datapacks/` for singleplayer, `<server>/world/datapacks/` for a server):

   ```
   datapacks/
     my_quests/
       pack.mcmeta
       data/
         mypack/                      <- your namespace (pick anything but "mcaquests")
           mcaquests/
             quests/
               errands/wood_run.json
               combat/rat_problem.json
   ```

   Use **your own namespace** for the folder under `data/` (here, `mypack`) and for each quest's `id` (`mypack:wood_run`). Don't reuse `mcaquests` — that's the bundled pack, and a same-id quest would collide. Subfolders under `quests/` (like `errands/`) are organizational only.

2. **Add `pack.mcmeta`** (the `pack_format` for 1.20.1 is `15`):

   ```json
   {
     "pack": {
       "pack_format": 15,
       "description": "My MCA quests"
     }
   }
   ```

3. **Write quest files.** Run `/mcaquests export-schema` to drop a complete, valid example at `config/mcaquests/example_quest.json` — copy it into `quests/`, change its `id` to your namespace, and edit. See the [field reference](#top-level-fields) below.

4. **Load it.** Run `/reload` (or `/mcaquests reload`). Confirm with `/mcaquests list` and `/mcaquests validate`. If a file is wrong, `validate` names it and the reason; the rest still load.

To **disable the built-in quests** entirely and ship only your own, set `enableDefaultQuestPack = false` in the config (see [CONFIG.md](CONFIG.md)). To ship a pack to *every* world, distribute it as a normal datapack zip, or place it in the global pack folder.

---

## Top-level fields

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `format_version` | int | no | `1` | Bump-safe version marker. |
| `id` | resource location | **yes** | — | Unique quest id, e.g. `mcaquests:farmer_wheat_request`. |
| `enabled` | bool | no | `true` | `false` removes the quest from selection. |
| `weight` | int (>0) | no | `1` | Relative odds of being offered (higher = more likely). |
| `category` | string | no | — | Free-form tag (e.g. `delivery`, `combat`), for your own grouping. |
| `title` | text | no | derived | Inline title; if omitted, uses lang key `mcaquests.quest.<path>.title`. |
| `repeat` | object | no | cooldown 24000 | See [Repeat](#repeat). |
| `giver` | object | **yes** | — | Who offers it. See [Giver](#giver). |
| `dialogue` | object | **yes** | — | The conversation lines. See [Dialogue](#dialogue). |
| `objectives` | array | **yes** | — | One or more objectives (all must complete). See [Objectives](#objectives). |
| `rewards` | array | **yes** | — | Granted on turn-in. See [Rewards](#rewards). |
| `turn_in` | object | no | `original_giver` | Where/how to hand in. See [Turn-in](#turn-in). |
| `conditions` | object | no | none | Extra gate on being offered. See [Conditions](#conditions). |
| `chain` | object | no | none | Relationship-arc metadata: stage, prerequisites, time limit. See [Quest chains](#quest-chains). |

### Text values

Any "text" field is either an inline literal or a translation key:

```json
{ "text": "Bring me 10 wheat." }
{ "translate": "mcaquests.quest.my_quest.offer" }
```

---

## Giver

```json
"giver": {
  "professions": ["minecraft:farmer", "minecraft:fisherman"],
  "adult_only": true,
  "min_hearts": 0,
  "max_hearts": 1000
}
```

| Field | Type | Default | Notes |
|---|---|---|---|
| `professions` | list of resource locations | — | Villager professions that may offer this. `minecraft:none` = jobless/nitwit; MCA's guard is `mca:guard`. |
| `adult_only` | bool | `true` | Children never offer quests when `true`. |
| `min_hearts` / `max_hearts` | int | `0` / very high | The player's current MCA hearts with that villager must fall in range. |

Profession matching honors the `professionMatchingMode` config: `STRICT` (exact id), `NORMALIZED` (path-insensitive, default), `LOOSE` (alias-aware).

---

## Dialogue

Six states; each is a [text value](#text-values). All optional — missing lines fall back to the title.

```json
"dialogue": {
  "offer":       { "text": "Could you bring me 10 wheat?" },
  "accept":      { "text": "Thank you kindly!" },
  "decline":     { "text": "Maybe another time." },
  "in_progress": { "text": "Any luck with that wheat?" },
  "ready":       { "text": "You have it all? Wonderful!" },
  "complete":    { "text": "Bless you, friend." }
}
```

---

## Objectives

Every objective shares an optional `count` (default `1`). "Targets" accept **either** a concrete id **or** a tag:

- item target: `"item": "minecraft:wheat"` or `"tag": "minecraft:planks"`
- block target: `"block": "minecraft:stone"` or `"tag": "minecraft:logs"`
- entity target: `"entity": "minecraft:zombie"` or `"tag": "minecraft:skeletons"`
- biome target: `"biome": "minecraft:plains"` or `"tag": "minecraft:is_forest"`

| `type` | Fields | Meaning |
|---|---|---|
| `mcaquests:item_delivery` | item target, `count`, `consume` (bool, default `true`) | Bring items; consumed on turn-in unless `consume:false`. |
| `mcaquests:obtain_item` | item target, `count` | Have the items in inventory. |
| `mcaquests:craft_item` | item target, `count` | Craft that many. |
| `mcaquests:fish_item` | item target, `count` | Fish up that many. |
| `mcaquests:kill_entity` | entity target, `count` | Player-credited kills. |
| `mcaquests:break_block` | block target, `count` | Player-broken blocks. |
| `mcaquests:place_block` | block target, `count` | Player-placed blocks. |
| `mcaquests:visit_biome` | biome target | Enter a matching biome. |
| `mcaquests:visit_dimension` | `dimension` (resource location) | Enter that dimension, e.g. `minecraft:the_nether`. |
| `mcaquests:talk_to_profession` | `profession` (resource location), `count` | Interact with that many villagers of a profession. |

---

## Rewards

Granted atomically on turn-in (items insert-or-drop, then XP, effects, loot, and MCA hearts).

| `type` | Fields | Notes |
|---|---|---|
| `mcaquests:item` | `item`, `count` (default 1) | Item stack. |
| `mcaquests:xp` | `amount` | XP points. |
| `mcaquests:xp_levels` | `levels` | XP levels. |
| `mcaquests:effect` | `effect`, `duration` (ticks), `amplifier` | Status effect. |
| `mcaquests:hearts` | `amount` | MCA hearts with the giver. Clamped by `min/maxHeartsReward` and scaled by `heartsRewardMultiplier`. |
| `mcaquests:loot_table` | `loot_table` (resource location) | Rolls a loot table. Requires `allowLootTableRewards` (on by default). |
| `mcaquests:command` | `command` (string) | Runs a command. **Disabled** unless `allowCommandRewards = true`. |

---

## Conditions

An extra gate on whether the quest is **offered**. A single condition object, which is either a leaf (`type` + fields) or a composite.

### Leaf conditions

| `type` | Fields |
|---|---|
| `mcaquests:hearts` | `min`, `max` — hearts with the prospective giver |
| `mcaquests:profession` | `professions` (list) |
| `mcaquests:biome` | biome target (`biome` or `tag`) |
| `mcaquests:dimension` | `dimension` |
| `mcaquests:time` | `period` (`DAY`/`NIGHT`) **or** `min`/`max` (day-time ticks) |
| `mcaquests:weather` | `weather` (`CLEAR`/`RAIN`/`THUNDER`) |
| `mcaquests:item_held` | item target (`item` or `tag`) |
| `mcaquests:advancement` | `advancement` (resource location) |
| `mcaquests:player_level` | `min`, `max` |
| `mcaquests:random_chance` | `chance` (0.0–1.0) |
| `mcaquests:quest_completed` | `quest` (resource location) |
| `mcaquests:quest_not_completed` | `quest` (resource location) |
| `mcaquests:quest_failed` | `quest` (resource location) — true once that quest has failed (giver died / timed out) |
| `mcaquests:quest_abandoned` | `quest` (resource location) — true once the player abandoned that quest |

### Composites

Composites nest other conditions (any depth):

```json
"conditions": {
  "all_of": [
    { "type": "mcaquests:time", "period": "NIGHT" },
    { "not": { "type": "mcaquests:weather", "weather": "THUNDER" } }
  ]
}
```

- `{ "all_of": [ ... ] }` — every child must pass
- `{ "any_of": [ ... ] }` — at least one child must pass
- `{ "not": { ... } }` — child must fail

---

## Turn-in

```json
"turn_in": { "mode": "same_profession", "professions": ["minecraft:farmer"] }
```

| `mode` (lowercase) | Meaning |
|---|---|
| `original_giver` | Only the villager who gave it (default). |
| `same_profession` | Any villager of the giver's profession. |
| `specified_profession` | Any villager in the `professions` list. |
| `any_villager` | Any MCA villager. |
| `self_complete` | Auto-completes the moment objectives are met; no hand-in. |

---

## Repeat

```json
"repeat": { "type": "cooldown", "cooldown_ticks": 24000 }
```

| `type` | Fields | Meaning |
|---|---|---|
| `cooldown` | `cooldown_ticks` | Repeatable after the cooldown (24000 ticks = 1 MC day). |
| `once` | — | Completable a single time, ever. |

---

## Quest chains

The optional `chain` block turns a set of standalone quests into a **relationship arc**: one quest
unlocks the next, the UI shows "Part 2 of 4", and follow-ups can branch on whether you completed,
failed, or abandoned an earlier step. Quests with no `chain` block are unaffected.

```json
"chain": {
  "chain": "mcaquests:farmer_family",
  "stage": 2,
  "stage_total": 4,
  "relationship_arc": { "text": "The Family Farm" },
  "chapter": { "text": "Breaking New Ground" },
  "prerequisites": ["mcaquests:farmer_family_1_wheat"],
  "unlocks": ["mcaquests:farmer_family_3_apprentice"],
  "time_limit_ticks": 12000
}
```

| Field | Type | Default | Notes |
|---|---|---|---|
| `chain` | string | **yes** | Arc id grouping the stages. The villager only ever offers the **furthest unlocked** stage of a chain at once. |
| `stage` | int (≥1) | `1` | Position in the arc; drives the "Part N" label and offer ordering. Branches may share a stage number. |
| `stage_total` | int | — | Total stages, for the "Part 2 of 4" label. |
| `relationship_arc` | text | — | Arc name shown in the menu / quest log. |
| `chapter` | text | — | This stage's subtitle, shown after the part number. |
| `prerequisites` | list of quest ids | `[]` | The arc gate: every listed quest must be **completed** before this one is offered. Compiles into `quest_completed` conditions and is merged with any `conditions` block. |
| `unlocks` | list of quest ids | `[]` | Forward pointers to the quests this one leads to. Used for validation (reachability, cycle detection); the actual gating lives on the downstream quest's `prerequisites`/`conditions`. |
| `time_limit_ticks` | int | — | If set, the quest **fails** this many ticks after acceptance (the deadline survives logout/restart). A failure is recorded for `quest_failed`. |

### Linear vs. branching

- **Linear** chains use `prerequisites` — "must have completed the previous stage." That's the common case.
- **Branching** uses the explicit outcome conditions in a `conditions` block. A redemption quest gated on
  `{ "type": "mcaquests:quest_failed", "quest": "..." }` only appears after the player fails that quest; a
  "no hard feelings" follow-up can use `mcaquests:quest_abandoned`. To let two branches converge on the same
  finale, gate the finale with `any_of` over the `quest_completed` of each branch (leave `prerequisites` empty
  and list it in each branch's `unlocks` so validation still sees it as reachable).

### Validation

`/mcaquests validate` reports chain problems, each naming the quest and field: unknown or disabled
`prerequisites`/`unlocks` targets, `stage` below 1, circular `unlocks`, and later stages that nothing
can reach. Fix these and `/mcaquests reload`.

The four built-in arcs under `data/mcaquests/mcaquests/quests/chains/` (farmer, guard, librarian, jobless)
are complete worked examples — copy one as a starting point.

---

## Complete example

`/mcaquests export-schema` writes this to `config/mcaquests/example_quest.json`:

```json
{
  "format_version": 1,
  "id": "mcaquests:example_quest",
  "weight": 10,
  "category": "delivery",
  "title": { "text": "An Example Quest" },
  "repeat": { "type": "cooldown", "cooldown_ticks": 24000 },
  "giver": { "professions": ["minecraft:farmer"], "adult_only": true, "min_hearts": 0 },
  "dialogue": {
    "offer": { "text": "Could you bring me 10 wheat?" },
    "accept": { "text": "Thank you kindly!" },
    "decline": { "text": "Maybe another time." },
    "in_progress": { "text": "Any luck with that wheat?" },
    "ready": { "text": "You have it all? Wonderful!" },
    "complete": { "text": "Bless you, friend." }
  },
  "objectives": [
    { "type": "mcaquests:item_delivery", "item": "minecraft:wheat", "count": 10, "consume": true }
  ],
  "rewards": [
    { "type": "mcaquests:item", "item": "minecraft:emerald", "count": 2 },
    { "type": "mcaquests:xp", "amount": 20 },
    { "type": "mcaquests:hearts", "amount": 20 }
  ],
  "turn_in": { "mode": "original_giver" },
  "conditions": { "all_of": [ { "type": "mcaquests:time", "period": "DAY" } ] },
  "chain": {
    "chain": "mcaquests:example_arc",
    "stage": 2,
    "stage_total": 4,
    "relationship_arc": { "text": "An Example Arc" },
    "chapter": { "text": "Chapter Two" },
    "prerequisites": ["mcaquests:example_arc_stage1"],
    "unlocks": ["mcaquests:example_arc_stage3"]
  }
}
```

---

## Extending with code

Add-on mods can register custom objective / reward / condition types under their own namespace via `dev.otectus.mcaquests.api.McaQuestsApi` (during mod setup), and react to quests through the Forge-bus events in `dev.otectus.mcaquests.api.event` (`QuestAccepted/Ready/Completed/Abandoned/Failed`).
