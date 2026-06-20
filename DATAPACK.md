# MCA: Quests — Datapack Format

Quests are data-driven. Drop JSON files into a datapack (or this mod's bundled data) at:

```
data/<namespace>/mcaquests/quests/**/*.json
```

One quest per file. The file path is cosmetic — a quest's identity is its `id` field. After editing, run `/reload` (or `/mcaquests reload`); check results with `/mcaquests validate` and `/mcaquests list`. `/mcaquests export-schema` writes a working example to `config/mcaquests/example_quest.json`.

Malformed or unknown quests are skipped with a logged error and listed by `/mcaquests validate`; the rest still load. Set `strictJsonValidation = true` in the config to treat any bad quest as a hard error instead.

> Looking for **shared, multi-player community goals** rather than per-player quests? See
> [Village projects](#village-projects), loaded from `data/<namespace>/mcaquests/projects/**/*.json`.

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
| `objectives` | array | yes¹ | — | One or more objectives (all must complete). See [Objectives](#objectives). |
| `rewards` | array | no | none | Granted on turn-in. See [Rewards](#rewards). |
| `turn_in` | object | no | `original_giver` | Where/how to hand in. See [Turn-in](#turn-in). |
| `conditions` | object | no | none | Extra gate on being offered. See [Conditions](#conditions). |
| `chain` | object | no | none | Relationship-arc metadata: stage, prerequisites, time limit. See [Quest chains](#quest-chains). |
| `template` | object | no | none | Turns the quest into a randomized template: variable pools + the objective/reward JSON that uses them. See [Quest templates](#quest-templates). |

¹ Required for a hand-authored quest. A **template** quest omits top-level `objectives`/`rewards` and supplies them inside its `template` block instead.

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

### MCA-aware conditions

These gate a quest on the giver's **MCA Reborn** state (relationship to the player, family, age, personality, mood, village/home, health, infection). They are evaluated server-side when the quest menu is opened, and all **fail safe**: if MCA data is missing or the giver is not an MCA villager, the condition is treated as **not met** and a debug line is logged — the server never crashes. Enum-like field values are validated when the pack loads; an unknown value skips the quest with a logged error (or is a hard error when `strictJsonValidation = true`). All are **supported** unless noted.

| `type` | Fields | True when |
|---|---|---|
| `mcaquests:is_player_spouse` | *(none)* | The giver is married to the interacting player. |
| `mcaquests:relationship_state` | `states` (list, required) | The giver's relationship state is in the list. Values: `single`, `promised`, `engaged`, `married_to_villager`, `married_to_player`, `widow`. |
| `mcaquests:is_family_member` | `relation` (default `any`) | The giver is that relation **to the player** in the family tree. Values: `any`, `parent`, `child`, `sibling`, `grandparent`. |
| `mcaquests:age_group` | `groups` (list, required) | The giver's age is in the list. Values: `baby`, `toddler`, `child`, `teen`, `adult`. *(MCA has no "elder" age — see Limitations.)* |
| `mcaquests:personality` | `personalities` (list, required) | The giver's personality is in the list. Values: `athletic`, `confident`, `friendly`, `flirty`, `witty`, `shy`, `gloomy`, `sensitive`, `greedy`, `odd`, `lazy`, `grumpy`, `peppy`. |
| `mcaquests:mood` | `min`/`max` (ints) and/or `moods` (list); at least one required | The giver's mood value is within `min`/`max` **and** (if given) its mood name is in `moods`. Mood names are data-driven in MCA, so they are not checked against a fixed list. |
| `mcaquests:village_member` | `value` (bool, default `true`) | Whether the giver belongs to a home village equals `value`. |
| `mcaquests:has_home` | `value` (bool, default `true`) | Whether the giver has an assigned home equals `value`. |
| `mcaquests:health_below` | `threshold` (required, `(0,1]`) | The giver's health fraction (current ÷ max) is below `threshold`. |
| `mcaquests:infected` | `min_progress` (default `0`) | The giver's zombie-infection progress is `> 0` and at least `min_progress` (range `[0,1]`). |
| `mcaquests:related_villager_status` | `relation` + `status` (both required) | The giver has at least one relative of `relation` (`spouse`/`parent`/`child`/`sibling`) whose `status` matches: `alive`, `nearby`, `missing` (in the family tree, not deceased, not currently loaded), `dead`, or `same_village`. |

Examples:

```json
"conditions": { "type": "mcaquests:is_player_spouse" }
```

```json
"conditions": {
  "all_of": [
    { "type": "mcaquests:is_player_spouse" },
    { "type": "mcaquests:hearts", "min": 50 }
  ]
}
```

```json
"conditions": { "type": "mcaquests:related_villager_status", "relation": "child", "status": "missing" }
```

**Failure behavior.** A non-MCA giver, a missing/partly-loaded relationship or family graph, or any internal MCA error all evaluate to *not met* (debug-logged), never an exception. `health_below` and `related_villager_status` read live/persistent state, so a quest can appear or disappear as that state changes — reopen the menu to refresh.

**Limitations.** `age_group` does **not** support `elder`: MCA Reborn has no elder age state (its ages are baby/toddler/child/teen/adult). All MCA access is isolated behind the mod's compatibility layer; if MCA Reborn is absent these conditions simply never match. See the built-in `relations/` quests for working examples of every category.

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

## Failure & deadlines

The optional `failure` block lets a quest **expire** while it's active. A quest with no `failure`
block never fails on its own — it lives until you complete or abandon it. Failure is fully
server-authoritative and the deadline is anchored to the moment you accepted (it survives
logout/restart). A failed quest is removed from your log, grants **no** rewards, and records a
`FAILED` outcome that follow-up quests can branch on with the `mcaquests:quest_failed` condition.

```json
"failure": {
  "deadline_ticks": 12000,
  "deadline_time": 23000,
  "require_weather": "rain",
  "fail_on_giver_death": true,
  "failure_hearts": -10,
  "retry_after": 24000,
  "block_retry": false
}
```

### Triggers — *why* the quest fails

Declare one or more. The first to fire wins. (A `failure` block with no trigger is a validation error.)

| Field | Type | Meaning |
|---|---|---|
| `deadline_ticks` | int (>0) | Fail this many ticks after acceptance. 24000 = 1 MC day. Best for "before it worsens" urgency. |
| `deadline_time` | int (0–24000) | Fail when the world clock next reaches this **time-of-day** after acceptance, e.g. `23000` for "before sunrise". Accepting exactly on the boundary grants a full day. |
| `require_weather` | `clear` / `rain` / `thunder` | The quest demands this weather; it fails the instant the weather stops matching. Pair with a `conditions` weather gate so it's only offered when the weather is right. |
| `fail_on_giver_death` | bool | Fail if the quest giver dies, regardless of the global `failQuestIfGiverDies` config. |

When both `deadline_ticks` and `deadline_time` are set, whichever runs out first wins. Time deadlines
drive the live countdown shown in the HUD tracker; weather/giver-death failures show no countdown.

> **Grace window:** a quest whose objectives are already met (it's "ready to turn in") is **not**
> failed by a deadline or weather trigger — you keep your reward as long as you finish in time. Giver
> death still fails it.

### Outcome — *what happens* on failure

| Field | Type | Default | Meaning |
|---|---|---|---|
| `failure_hearts` | int | `0` | Hearts applied to the giver on failure. Negative = a relationship penalty; `0` = non-punitive. |
| `retry_after` | int (≥0) | — | Cooldown (ticks) before the quest can be offered again. Omit to follow the normal `repeat` rule. |
| `block_retry` | bool | `false` | If true, the quest is locked permanently after a single failure. |

The **failure dialogue** is the giver's `failed` entry in the quest's `dialogue` map (shown as the
failure message). Add it alongside `offer` / `complete`:

```json
"dialogue": {
  "offer":    { "text": "Bring the medicine before the fever turns!" },
  "failed":   { "text": "You came too late..." }
}
```

### Recovery quests

There's no dedicated field — a recovery quest is just a normal quest gated on the failure:

```json
"conditions": { "type": "mcaquests:quest_failed", "quest": "mcaquests:cleric_urgent_medicine" }
```

It only becomes offerable after the player fails that quest. See
`cleric/urgent_medicine.json` + `cleric/urgent_medicine_recovery.json` for a worked pair.

### Validation

`/mcaquests validate` reports a `failure` block with no trigger, a `failure_hearts` magnitude past the
configured hearts clamp, and `block_retry` combined with `retry_after` (contradictory). Numeric ranges
are enforced at load time.

### Built-in examples

- `guard/dawn_defense.json` — kill 6 zombies **before sunrise** (`deadline_time`), small heart penalty.
- `cleric/urgent_medicine.json` — deliver medicine within a **tick deadline**, with a recovery quest.
- `fisherman/rain_catch.json` — fish **while it's raining** (`require_weather`); fails when the rain stops.

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
  "unlocks": ["mcaquests:farmer_family_3_apprentice"]
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

> **Deadlines moved.** Per-quest time limits used to live here as `chain.time_limit_ticks`. They are
> now part of the dedicated [`failure`](#failure--deadlines) block (`deadline_ticks`), which works on
> any quest — not just chains — and supports more triggers and outcomes.

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

## Quest templates

A **template** turns one quest file into many concrete quests: instead of hand-writing 30 near-identical
"bring me X crops" quests, you declare *variable pools* and reference them from the objectives, rewards,
and dialogue with `{placeholders}`. When a villager offers the quest, the server picks one concrete value
per variable and substitutes them in. Add a `template` block and move the objectives/rewards inside it:

```json
{
  "id": "mcaquests:template_farmer_crop_request",
  "giver": { "professions": ["minecraft:farmer"] },
  "title": { "translate": "mcaquests.quest.template_farmer_crop_request.title", "with": ["{crop_name}"] },
  "dialogue": {
    "offer": { "text": "The market's hungry for {crop_name}. Could you bring me {count} of them?" }
  },
  "template": {
    "variables": {
      "crop":  { "kind": "item", "ids": ["minecraft:wheat", "minecraft:carrot", "minecraft:potato"] },
      "count": { "kind": "int", "min": 8, "max": 20, "per_player_level": 0.5, "limit": 48 }
    },
    "objectives": [
      { "type": "mcaquests:item_delivery", "item": "{crop}", "count": "{count}", "consume": true }
    ],
    "rewards": [
      { "type": "mcaquests:xp", "amount": "{count}" }
    ]
  }
}
```

### How resolution works

- Values are chosen **at offer time**, server-side, and are **deterministic per villager per day** — the
  same villager offers the same concrete quest all day, so what you see on the board is what you accept.
- On accept, the chosen values are **frozen onto the active quest** and persisted. They never reroll for
  that copy — through logout, death, dimension change, villager unload, or server restart — until the quest
  is completed, failed, or abandoned. Different players (or the same quest accepted on another day) get
  their own independent roll.
- Resolution is **server-authoritative**: the client only renders the already-resolved card.

### Variable kinds

Every variable has a `kind` discriminator.

| `kind` | Pool fields | Resolves to | Notes |
|---|---|---|---|
| `item` / `block` / `entity` | `ids` (list) and/or `tags` (list) | one registry id | `ids` + all `tags` members are merged, de-duplicated, and sorted; one is picked. |
| `biome` / `dimension` | `ids` (list) and/or `tags`† | one registry id | Dynamic registries — ids are format-checked at load and re-checked against the live world. `dimension` has no tags. |
| `int` | `min`, `max`, optional `per_player_level`, `per_heart`, `limit` | one integer | Base value uniform in `[min, max]`, plus `per_player_level × player level` and `per_heart × giver hearts`, clamped to `limit`. |
| `text` | `options` (list of [text values](#text-values)) | one phrase | For dialogue/title flavor only; never substituted into objective/reward JSON. |

† `biome` tags are expanded from the live world at offer time. `dimension` does not support tags.

### Placeholders

- In **objective/reward JSON**, a string that is *exactly* `"{var}"` is replaced by that variable's value:
  a registry id becomes its id string (`"{crop}"` → `"minecraft:wheat"`), an `int` becomes a JSON number
  (`"count": "{count}"` → `"count": 12`). The existing objective/reward codecs then parse the result, so
  every objective, reward, and target field works unchanged.
- In **dialogue, titles, and objective summaries**, `{var}` inserts the value and `{var_name}` inserts the
  registry object's translated display name (`{crop_name}` → "Wheat"). Translation keys are preserved: a
  `title`/dialogue line may use `"translate"` with a `"with": ["{count}", "{crop_name}"]` list so the
  sentence stays translatable. Use `{{` and `}}` for literal braces.

### Validation

`/mcaquests validate` reports template problems, each naming the quest and field: registry `ids` that don't
exist, `tags` that are empty/unknown, `{placeholders}` that reference no declared variable, and substituted
objective/reward JSON that fails to parse. Empty pools also **fail safe at runtime** — the offer is simply
skipped (with a debug log) rather than crashing. Under `strictJsonValidation` these become hard errors.

The five built-in templates under `data/mcaquests/mcaquests/quests/templates/` (farmer crop request, guard
mob cull, fisherman catch, librarian knowledge, cartographer survey) are complete worked examples — copy one
as a starting point.

---

## Village projects

A **project** is a shared, multi-stage community goal that several players contribute to at once.
Unlike a quest — whose progress lives on the individual player — a project's progress is **shared**:
items donated, mobs killed, and blocks placed are banked into one common pool stored in the world
save, so the whole server (or village, or family) works toward the same objective. Projects are a
separate system layered alongside quests; **existing quests are unchanged** and pre-0.4.0 worlds load
without conversion.

Projects are data-driven, in their own folder:

```
data/<namespace>/mcaquests/projects/**/*.json
```

One project per file; the file path is cosmetic — a project's identity is its `id` field. After
editing, run `/reload` (or `/mcaquests reload`); inspect with `/mcaquests project list`,
`/mcaquests project info <id>`, and `/mcaquests project validate`. Use **your own namespace** (not
`mcaquests`) for custom projects, exactly as with quests.

When a project is available, eligible **sponsor** villagers gain a **View Project** button in their
MCA interaction menu — the sponsor menu surfaces the active phase's `offer`/`in_progress` dialogue and
lets players donate or report progress. Individual quests stay visually simple; only sponsors of an
available project show the extra button.

### Top-level fields

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `id` | resource location | **yes** | — | Unique project id, e.g. `mcaquests:well_repair`. |
| `enabled` | bool | no | `true` | `false` removes the project from selection. |
| `weight` | int (>0) | no | `1` | Relative odds of being surfaced when several projects are eligible. |
| `title` | text | no | — | Inline title or a `translate` key. See [Text values](#text-values). |
| `category` | string | no | — | Free-form tag (e.g. `infrastructure`, `defense`), for your own grouping. |
| `scope` | string **or** object | **yes** | — | Who shares the progress. See [Scope](#scope). |
| `sponsor` | object | no | any adult villager | Which villagers offer the project. See [Sponsor](#sponsor). |
| `conditions` | object | no | none | Gate on whether the project is available at all. Reuses the **existing** quest [condition tree](#conditions). |
| `reputation` | object | no | all `0` | Mod-side village reputation deltas. See [Reputation](#reputation-deltas). |
| `follow_up` | resource location | no | none | Another project id seeded in the **same scope** when this one completes. |
| `failure` | object | no | none | Same [`failure`](#failure--deadlines) block as quests. |
| `phases` | array | **yes** | — | One or more phases, run **in order**. Non-empty. See [Phases](#phases). |

### Scope

`scope` declares **who shares one project's progress**. It is either a bare string or an object:

```json
"scope": "village"
```

```json
"scope": { "scope": "profession", "professions": ["minecraft:librarian"], "fallback_radius": 64 }
```

| Field | Type | Default | Notes |
|---|---|---|---|
| `scope` | enum | — | One of the five values below. |
| `professions` | list of resource locations | — | Only meaningful for the `profession` scope — restricts which professions share the goal. |
| `fallback_radius` | int | config `defaultScopeFallbackRadius` (64) | Used to find/anchor a village when MCA village data is unavailable. |

| Scope | Progress shared by | MCA data needed |
|---|---|---|
| `player` | a single player | no |
| `villager` | one villager (and everyone who helps it) | no |
| `family` | a villager's lineage | **yes** — uses a derived lineage id (see [MCA compatibility](#mca-compatibility)) |
| `profession` | all villagers of the listed `professions` in the village | **yes** |
| `village` | the whole village | **yes** — uses MCA's stable village id (anchor fallback) |

When a scope needs MCA data that isn't present, the project simply **never becomes available** (it
fails safe — it never crashes).

### Sponsor

The `sponsor` block controls **which villagers offer** the project and what happens if the offering
villager dies. Omit it for "any adult villager".

```json
"sponsor": {
  "professions": ["mca:guard"],
  "required_count": 1,
  "adult_only": true,
  "pinned_sponsors": ["<uuid>"],
  "on_death": "transfer"
}
```

| Field | Type | Default | Notes |
|---|---|---|---|
| `professions` | list of resource locations | empty = **any** adult villager | Villager professions that may sponsor. |
| `required_count` | int | `1` | How many eligible sponsors are needed. |
| `adult_only` | bool | `true` | Children never sponsor when `true`. |
| `pinned_sponsors` | list of UUIDs | — | Specific villagers that always sponsor (by UUID). |
| `on_death` | enum | config `defaultSponsorDeathBehavior` (`pause`) | What happens when the sponsor dies: `fail`, `pause`, `transfer` (hand off to another eligible villager), or `turn_in_to_village`. |

### Reputation deltas

Independent **mod-side village reputation** changes, applied as the project progresses. All default `0`.

```json
"reputation": { "on_phase_complete": 2, "on_project_complete": 8, "on_fail": 0 }
```

| Field | Type | Default | When applied |
|---|---|---|---|
| `on_phase_complete` | int | `0` | Each time a phase finishes. |
| `on_project_complete` | int | `0` | When the final phase finishes. |
| `on_fail` | int | `0` | When the project fails. |

This reputation is also testable from any condition tree (quest **or** project) via the new
[`mcaquests:village_reputation`](#village-reputation-condition) condition.

### Phases

`phases` is a non-empty array that runs **in order**: a phase is entered only after **every earlier
phase completes**. Each phase has its own dialogue, shared objectives, shared rewards, and an optional
extra unlock gate.

| Field | Type | Required | Notes |
|---|---|---|---|
| `key` | string | no | Optional label, e.g. `gather_stone`. |
| `dialogue` | object | no | Map of state → [text](#text-values). The sponsor menu shows `offer` and `in_progress`. |
| `objectives` | array | yes¹ | The phase's shared objectives — see [Project objectives](#project-objectives). |
| `rewards` | array | no | Shared rewards granted when the phase completes — see [Shared rewards](#shared-rewards). |
| `unlock` | object | no | An **extra** [condition tree](#conditions) that must pass before this phase is entered (on top of "prior phase done"). |

¹ A non-final phase normally has objectives; the last phase may be a reward-only "payoff" phase.

### Project objectives

Project objectives are **distinct from quest objectives** — they track **shared** progress banked into
the project's pool, not a single player's inventory or kill count. **Quest objective types such as
`mcaquests:item_delivery` are not valid in a project** (different progress model) — use
`mcaquests:donate_item` instead. Targets accept **either** a concrete id **or** a `tag`, as elsewhere.

| `type` | Fields | Meaning |
|---|---|---|
| `mcaquests:donate_item` | `item` or `tag`, `count`, `per_player_cap` (default `0`) | A player donates items to the sponsor; the stack is **consumed immediately** and banked into the shared pool. `per_player_cap` (`0` = unlimited / config default) caps one player's total contribution. |
| `mcaquests:project_kill_entity` | `entity` or `tag`, `count` | Kills inside the project's village/scope, banked into the pool. |
| `mcaquests:project_place_block` | `block` or `tag`, `count` | Blocks placed inside the scope. |
| `mcaquests:project_talk_to_profession` | `profession` (resource location), `count` | Talk to that many **distinct** villagers of a profession inside the scope. |

### Shared rewards

A shared reward **wraps an existing quest reward** and adds a `target` — who receives it when the phase
completes:

```json
{ "reward": { "type": "mcaquests:item", "item": "minecraft:emerald", "count": 3 }, "target": "contributors" }
```

| `target` | Receives |
|---|---|
| `contributors` (default) | Players who helped **this** phase. |
| `all_participants` | Everyone who ever helped the project. |
| `sponsor_village` | The village — used for reputation rewards. |
| `top_contributor` | The single biggest helper of the phase. |

The wrapped `reward` may be **any existing quest reward type** (`item`, `xp`, `xp_levels`, `hearts`,
`effect`, `loot_table`, `command`) — all with the same config safety gates (command rewards are off
unless `allowCommandRewards` **and** `allowProjectCommandRewards` are enabled; loot tables need
`allowLootTableRewards`) — **or** one of these new reward types:

| `type` | Fields | Notes |
|---|---|---|
| `mcaquests:hearts_with_sponsor` | `amount` | MCA hearts with the sponsor villager. |
| `mcaquests:hearts_with_participants` | `amount`, `include_residents` (bool, default `false`) | MCA hearts with the participants (and, optionally, village residents). |
| `mcaquests:village_reputation` | `amount` | Mod-side village reputation. Also valid as a **normal quest reward**. |
| `mcaquests:unlock` | `target` (project resource location) | Seeds another project in the same scope. |

### Village reputation condition

A new condition usable in **either** a quest **or** a project condition tree — it tests the **giver's
village reputation**:

```json
{ "type": "mcaquests:village_reputation", "min": 10, "max": 50 }
```

Both `min` and `max` are optional bounds. It composes inside `all_of`/`any_of`/`not` like any other leaf.

### Shared storage & multiplayer

- **Shared progress** lives in world save data (`<world>/data/mcaquests_projects.dat`), keyed by
  *(project id, scope, scope-identity)*. It survives logout, death, dimension change, villager
  unload/reload, chunk unload, and server restart. Pre-0.4.0 worlds load cleanly — the file is created
  on first need. The format is **save-compatible and additive**.
- **Any eligible player** may contribute. Contributions are **atomic and server-authoritative**: items
  are validated and consumed server-side, then banked, then synced to clients. Duplicate reward claims
  and packet-spam are prevented (one-shot per-phase reward distribution plus a contribution rate
  limit, `projectContributeMinIntervalTicks`). Per-player contribution is tracked for the
  `contributors` and `top_contributor` targets.
- **Reward distribution** happens **once**, when a phase completes. Online recipients are paid
  immediately; offline players' non-hearts rewards are **queued and delivered on next login**; hearts
  destined for unloaded villagers are queued via MCA. Reward order respects the existing config clamps
  (hearts scaling/min/max, etc.).
- **Sponsor surfacing / anti-flood.** A project is offered by eligible sponsor villagers (by
  profession). With `oneSponsorPerProjectPerDay` (default `true`), only **one deterministically chosen
  villager per village** offers a given project per day, so a town doesn't flood you with the same
  request.

### MCA compatibility

- The `family`, `profession`, and `village` scopes require MCA relationship/village data.
- **Village identity** uses MCA's stable village id; if MCA village data is unavailable it falls back
  to an **anchor** (the sponsor villager plus the scope's `fallback_radius`).
- **Family scope** uses a derived **lineage id** — the minimum UUID over the villager's ancestors. This
  is a heuristic that groups a *lineage*, not strictly a single household.
- When required MCA data is missing, the project simply **never becomes available** (fails safe, never
  crashes).

### Project config keys

Projects add a `projects` block to the common config plus two client keys (full reference in
[CONFIG.md](CONFIG.md)):

| Key | Default | Notes |
|---|---|---|
| `enableVillageProjects` | `true` | Master switch for the whole system. |
| `defaultScopeFallbackRadius` | `64` | Default `fallback_radius` for anchoring a village. |
| `defaultSponsorDeathBehavior` | `PAUSE` | Default `sponsor.on_death`. |
| `oneSponsorPerProjectPerDay` | `true` | Only one villager per village offers a given project per day. |
| `projectOffersPerVillager` | `1` | How many projects a single villager surfaces at once. |
| `projectContributeMinIntervalTicks` | `5` | Per-player contribution rate limit. |
| `defaultPerPlayerContributionCap` | `0` | Default `donate_item` `per_player_cap` (`0` = unlimited). |
| `allowProjectCommandRewards` | `false` | Gates `command` rewards inside projects (also needs `allowCommandRewards`). |
| `maxConcurrentProjectsPerScope` | `8` | Cap on simultaneously active projects sharing a scope. |
| `showProjectTrackerHud` *(client)* | `true` | Show the project tracker HUD. |
| `projectTrackerMaxEntries` *(client)* | `3` | Max project entries shown in the HUD. |

### Commands

| Command | Op level | Purpose |
|---|---|---|
| `/mcaquests project list` | 2 | List loaded projects and their state. |
| `/mcaquests project info <id>` | 2 | Show a project's phases, scope, and progress. |
| `/mcaquests project debug <id>` | 2 | Explain why a project is/isn't available from the nearest villager. |
| `/mcaquests project validate` | 3 | Re-run project validation and report problems. |
| `/mcaquests project reset <id>` | 3 | Clear a project's shared progress. |
| `/mcaquests project advance <id>` | 3 | **Test only** — force-advance a phase. |

### Validation

At load, projects are validated; problems are listed by `/mcaquests project validate`, and with
`strictJsonValidation = true` hard errors abort the load. Checks include:

- **Errors:** unknown scope / objective / reward type ids (codec), no `phases`, a `follow_up` pointing
  at an unknown or disabled project, and circular `follow_up` chains.
- **Warnings:** a non-final phase with no objectives, an MCA-dependent scope while MCA isn't loaded, a
  `command` reward while disabled, a `sponsor_village` target with no village, and a
  `top_contributor`/`contributors` target on an objective-less phase.

### Built-in examples

Six worked projects ship under `data/mcaquests/mcaquests/projects/` — copy one as a starting point:

| File | Scope | Sponsor |
|---|---|---|
| `guardhouse_stockpile.json` | `village` | guard |
| `library_restoration.json` | `profession` (librarian) | librarian |
| `festival_preparation.json` | `village` | any |
| `well_repair.json` | `village` | mason |
| `after_raid_recovery.json` | `village` | guard |
| `missing_villager_search.json` | `family` | any |

**Village scope** — the mason rallies the town to fix the well (donate stone, then rebuild), based on
the built-in `well_repair.json`:

```json
{
  "format_version": 1,
  "id": "mypack:well_repair",
  "scope": { "scope": "village", "fallback_radius": 64 },
  "sponsor": { "professions": ["minecraft:mason"], "on_death": "transfer" },
  "title": { "text": "Repair the Village Well" },
  "category": "infrastructure",
  "reputation": { "on_project_complete": 10 },
  "phases": [
    {
      "key": "gather_stone",
      "dialogue": {
        "offer": { "text": "The well's run dry — bring stone and buckets and we'll set it right." },
        "in_progress": { "text": "How are we doing on that stone?" }
      },
      "objectives": [
        { "type": "mcaquests:donate_item", "item": "minecraft:stone", "count": 64 },
        { "type": "mcaquests:donate_item", "item": "minecraft:bucket", "count": 2 }
      ],
      "rewards": [
        { "reward": { "type": "mcaquests:xp", "amount": 30 }, "target": "contributors" }
      ]
    },
    {
      "key": "rebuild",
      "dialogue": {
        "offer": { "text": "Now lay the brickwork around the shaft." },
        "in_progress": { "text": "Mind the masonry — almost there." }
      },
      "objectives": [
        { "type": "mcaquests:project_place_block", "block": "minecraft:stone_bricks", "count": 8 }
      ],
      "rewards": [
        { "reward": { "type": "mcaquests:hearts_with_participants", "amount": 10 }, "target": "all_participants" },
        { "reward": { "type": "mcaquests:item", "item": "minecraft:emerald", "count": 4 }, "target": "top_contributor" },
        { "reward": { "type": "mcaquests:village_reputation", "amount": 5 }, "target": "sponsor_village" }
      ]
    }
  ]
}
```

**Profession scope** — the librarians restore their library (donate texts, then catalogue them),
based on the built-in `library_restoration.json`. The `professions` list scopes the shared progress to
that trade:

```json
{
  "format_version": 1,
  "id": "mypack:library_restoration",
  "scope": { "scope": "profession", "professions": ["minecraft:librarian"] },
  "sponsor": { "professions": ["minecraft:librarian"], "on_death": "pause" },
  "title": { "text": "Restore the Library" },
  "category": "culture",
  "conditions": { "type": "mcaquests:village_reputation", "min": 5 },
  "reputation": { "on_project_complete": 6 },
  "phases": [
    {
      "key": "gather_texts",
      "dialogue": {
        "offer": { "text": "Our shelves are bare — bring paper and books." },
        "in_progress": { "text": "Every page helps. Keep them coming." }
      },
      "objectives": [
        { "type": "mcaquests:donate_item", "item": "minecraft:paper", "count": 64 },
        { "type": "mcaquests:donate_item", "item": "minecraft:book", "count": 16 }
      ],
      "rewards": [
        { "reward": { "type": "mcaquests:xp", "amount": 30 }, "target": "contributors" }
      ]
    },
    {
      "key": "catalogue",
      "dialogue": {
        "offer": { "text": "Now help the librarians catalogue it all." },
        "in_progress": { "text": "So much to sort — thank you." }
      },
      "objectives": [
        { "type": "mcaquests:project_talk_to_profession", "profession": "minecraft:librarian", "count": 3 }
      ],
      "rewards": [
        { "reward": { "type": "mcaquests:loot_table", "loot_table": "minecraft:chests/stronghold_library" }, "target": "all_participants" },
        { "reward": { "type": "mcaquests:hearts_with_participants", "amount": 10 }, "target": "all_participants" }
      ]
    }
  ]
}
```

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
