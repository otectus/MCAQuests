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
| `priority` | int | no | — | Offer priority tier; higher tiers fill a villager's slots first. Defaults so chain continuations (stage > 1) outrank standalone offers. See [Offer priority & weight bonuses](#offer-priority--weight-bonuses). |
| `weight_bonus` | array | no | none | Conditional additions to `weight` (e.g. likelier as hearts rise). See [Offer priority & weight bonuses](#offer-priority--weight-bonuses). |
| `chain` | object | no | none | Relationship-arc metadata: stage, prerequisites, unlocks. See [Quest chains](#quest-chains). |
| `template` | object | no | none | Turns the quest into a randomized template: variable pools + the objective/reward JSON that uses them. See [Quest templates](#quest-templates). |
| `difficulty` | string | no | — | `easy`, `medium`, or `hard`. Optional metadata that supplies default reward amounts. See [Difficulty](#difficulty). |
| `offer_group` | string | no | — | Names the *kind* of quest this is, so the offer menu does not fill every slot with the same one. See [Offer groups](#offer-groups). |

¹ Required for a hand-authored quest. A **template** quest omits top-level `objectives`/`rewards` and supplies them inside its `template` block instead.

### Difficulty

*(1.1.0)*

`"difficulty"` describes how demanding a quest is, so rewards can be tuned server-side instead of hard-coded in every file:

```json
"difficulty": "hard"
```

- `easy` — a fetch-or-errand finishable without leaving the village.
- `medium` — needs a trip or a fight.
- `hard` — real risk, or a long expedition.

It is **purely optional and purely additive**. A quest that omits it keeps every explicit reward amount exactly as written, so datapacks authored before 1.1.0 behave identically. Difficulty only supplies a default to rewards that ask for one — today that means [`mcaquests:currency`](#rewards). A currency reward with no `difficulty` of its own inherits the quest's; if neither declares one, `medium` is assumed.

### Text values

Any "text" field is either an inline literal or a translation key:

```json
{ "text": "Bring me 10 wheat." }
{ "translate": "mcaquests.quest.my_quest.offer" }
```

**Both are fully supported and always will be** — a small pack can stay inline. But only `translate` can be localized, so if you want your pack to be translatable, use keys and ship a `lang/` folder alongside it.

As of 1.1.0 the entire built-in pack uses `translate`; see [Localization](#localization).

#### Placeholders in translated text

A template quest's `{token}` placeholders can't live inside a translation key, because a translator can't be expected to preserve them. Pass them as ordered arguments instead:

```json
{
  "translate": "mcaquests.quest.my_quest.offer",
  "with": ["{count}", "{crop_name}"]
}
```

with the English string using positional format specifiers:

```json
"mcaquests.quest.my_quest.offer": "The market wants %2$s — could you bring me %1$s of them?"
```

Using `%1$s` / `%2$s` rather than bare `%s` lets a translation reorder the arguments, which many languages need. A translation whose placeholders don't match the source is a build failure, not a crash at runtime.

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

Nine states; each is a [text value](#text-values). All optional — missing lines fall back to the title.

```json
"dialogue": {
  "offer":       { "text": "Could you bring me 10 wheat?" },
  "accept":      { "text": "Thank you kindly!" },
  "decline":     { "text": "Maybe another time." },
  "in_progress": { "text": "Any luck with that wheat?" },
  "ready":       { "text": "You have it all? Wonderful!" },
  "complete":    { "text": "Bless you, friend." },
  "failed":      { "text": "Too late for that now." },
  "cooldown":    { "text": "You have done enough for me this week." },
  "locked":      { "text": "Perhaps when I know you better." }
}
```

| State | When the villager says it |
|---|---|
| `offer` | On the card, while the quest is being offered. |
| `accept` | You took it on. |
| `decline` | You turned it down. |
| `in_progress` | You come back with work still to do. |
| `ready` | You come back with every objective satisfied. |
| `complete` | You hand it in. |
| `failed` | It failed — a deadline ran out, the giver or the escortee died, the weather turned. |
| `cooldown` | They would offer it again, but you did it too recently. |
| `locked` | They have it, and you have not met its conditions yet. |

The last three are about the *villager* having nothing to give you, so a quest that authors none of them
falls back to the shared [voice pools](#shared-voice-pools--datansmcaquestsdialoguejson) below.

### Shared voice pools — `data/<ns>/mcaquests/dialogue/*.json`

A quest's `dialogue` covers the six states a quest actually reaches. The two states that explain a
villager having **nothing** to offer — `cooldown` and `locked` — are reached by the *villager*, not by
any one quest, and no quest in the bundled pack authored them, so every busy villager in the game said
the same flat refusal. Voice pools are lines any villager can fall back on.

```json
{
  "format_version": 1,
  "state": "cooldown",
  "priority": 0,
  "lines": [
    {
      "when": { "type": "mcaquests:personality", "personalities": ["grumpy", "greedy"] },
      "translate": "mcaquests.voice.cooldown.brusque"
    },
    { "text": "You have done enough for me lately. Come back tomorrow.", "weight": 2 }
  ]
}
```

| Field | Default | Meaning |
|---|---|---|
| `state` | **required** | One of `greeting`, `cooldown`, `locked`, `no_quests`. Any other value fails to load — a pool naming a state nothing reads is a file that silently does nothing. |
| `priority` | `0` | Higher pools are consulted first, so a pack can shadow the built-in voice without deleting it. |
| `lines` | **required**, non-empty | The things that might be said. |
| `lines[].when` | none | The **same** [condition language](#conditions) quests are gated with. A line with no `when` is the pool's fallback: always eligible, never preferred. |
| `lines[].text` / `.translate` | **required** | A [text value](#text-values). |
| `lines[].weight` | `1` | Relative likelihood among the lines that match. |

The four states:

| State | When it is said |
|---|---|
| `greeting` | In the menu header whenever the villager *does* have offers. |
| `cooldown` | They have nothing because you did their quest recently. |
| `locked` | They have something, but you have not earned it yet. |
| `no_quests` | They have nothing, and there is no more specific reason. |

**A quest's own line always wins where it has one.** This is the floor, not the ceiling.

Selection is deterministic per player, villager, day and state, so reopening a menu does not re-voice a
villager — the guarantee offers got in 1.4.3, applied one layer down.

Reusing the condition language rather than inventing a dialogue mini-language is the whole design:
personality, mood, time of day, weather, hearts, reputation tier, relationship state, age group and
every condition added later are available to dialogue for free, and there is no second grammar to
document, validate or teach.

---

## Objectives

Every objective shares an optional `count` (default `1`). Progress is stored **positionally**, so append
new objectives to the end of a quest or stage players may already be holding — never insert one in the
middle, or their progress shifts onto the wrong objective. "Targets" accept **either** a concrete id **or** a tag:

- item target: `"item": "minecraft:wheat"` or `"tag": "minecraft:planks"`
- block target: `"block": "minecraft:stone"` or `"tag": "minecraft:logs"`
- entity target: `"entity": "minecraft:zombie"` or `"tag": "minecraft:skeletons"`
- biome target: `"biome": "minecraft:plains"` or `"tag": "minecraft:is_forest"`

| `type` | Fields | Meaning |
|---|---|---|
| `mcaquests:item_delivery` | item target, `count`, `consume` (bool, default `true`), `destination` (optional) | Bring items; consumed on turn-in unless `consume:false`. **`destination`** sends the goods somewhere instead of destroying them: `{"type": "townstead_villager_inventory", "target": "giver"}` puts them in the villager's own inventory, where [Townstead](TOWNSTEAD.md) lets them be eaten or used. A destination overrides `consume`, and the turn-in is **refused** rather than half-done when the goods will not fit. The default, `consume`, is the behaviour every existing pack already has. |
| `mcaquests:obtain_item` | item target, `count` | Have the items in inventory. |
| `mcaquests:craft_item` | item target, `count` | Craft that many. |
| `mcaquests:fish_item` | item target, `count` | Fish up that many. |
| `mcaquests:kill_entity` | entity target, `count` | Player-credited kills. Credit follows the blow, the player's **tamed animal**, or — when something else lands the last hit (TNT, lava, a fall after the player struck) — vanilla's own kill credit, the same rule the death message uses. `defend_villager`, `defend_location` and the project kill objectives all read the same answer. |
| `mcaquests:break_block` | block target, `count` | Player-broken blocks. |
| `mcaquests:place_block` | block target, `count` | Player-placed blocks. |
| `mcaquests:visit_biome` | biome target | Enter a matching biome. |
| `mcaquests:visit_dimension` | `dimension` (resource location) | Enter that dimension, e.g. `minecraft:the_nether`. |
| `mcaquests:talk_to_profession` | `profession` (resource location), `count` | Interact with that many villagers of a profession. |

The objectives below center on living villagers, homes, families, and places. They all track
progress **server-side** and persist through the existing quest state, so they survive logout, death,
dimension change, villager/chunk unload, and dedicated-server restart. They never trust client packets.

| `type` | Fields | Meaning |
|---|---|---|
| `mcaquests:escort_entity` | `villager` (default self), `destination` (anchor, req.), `radius` (1–64, def 6), `follow` (bool, def true), `lead` (bool, def false), `wait_distance` (1–64, def 6), `stage_until_near` (bool, optional), `min_journey` (0–512, optional) | Get a villager to a location; arrival sticks complete. With `follow` (default) the **player** leads and the villager trails. With `lead:true` the **villager** walks to the destination itself (re-pathed every tick), pausing whenever the player is farther than `wait_distance` blocks (so the player must stay close to guard it); `follow` is ignored. The destination is **resolved and frozen when the quest is accepted** (so a `nearest_village`/relative target never drifts). **Arrival** is border-aware: for a `home_village`/`nearest_village` anchor the villager need only be **inside the village border**; for any other anchor it is a horizontal (Y-ignored) distance within `radius`. Pair with `failure.fail_on_giver_death` so it fails if the escorted villager dies.<br>**`min_journey`** is how far the escortee must **start** from the destination for the trip to count, defaulting to the `minEscortJourney` config (24). Below it the quest is **not offered at all**, and if it is granted some other way (a chain stage, a command) arrival is **not credited** until the escortee has genuinely been that far away — otherwise a villager standing at the destination completes the quest on the first poll and the reward is free. A quest already in flight when this was introduced is treated as having travelled, so it completes exactly as it always would have.<br>**Staged escort:** when `lead` is on and the escortee is **not the giver** (a relative/other villager), the escort is *staged* — the escortee waits **invulnerable and motionless** at its spot until the player comes within `wait_distance`, then the escort begins and from that point the escortee's **death fails the quest**. Auto-detected for `lead` + non-`self` villager; `stage_until_near` forces it on (`true`) or off (`false`). |
| `mcaquests:protect_entity` | `villager` (default self), `duration_ticks` (≥20, def 2400), `require_near_player` (bool), `near_radius` (1–64, def 16), `fail_on_death` (bool, def true) | Keep a villager alive for a duration (shown in seconds). |
| `mcaquests:defend_villager` | `villager` (default self), `threat` (entity target, req.), `radius` (1–64, def 16), `count` (def 5) | Kill hostile threats near a villager. |
| `mcaquests:defend_location` | `location` (anchor, req.), `threat` (entity target, req.), `radius` (1–64, def 16), `count` (def 5) | Kill hostile threats near a fixed place (a gate, well, village center) — the place-anchored sibling of `defend_villager`. |
| `mcaquests:trade_with_villager` | `villager` **or** `profession` (both optional), `count` (def 1) | Complete trades with a villager / profession. |
| `mcaquests:heal_entity` | `villager` (default self), item target (req.), `below_health_fraction` (0–1, def 1.0), `count` (def 1), `consume` (bool, def false) | Use a remedy item on a hurt villager. |
| `mcaquests:cure_villager` | `villager` (default self), `cure_item` (anchored item, def golden apple) | Cure an infected (zombifying) MCA villager. |
| `mcaquests:breed_animals` | `animal` (entity target, req.), `near` (anchor, optional), `radius` (def 32), `count` (def 1) | Breed animals, optionally near a place. |
| `mcaquests:tame_animal` | `animal` (entity target, req.), `near` (anchor, optional), `radius` (def 48), `count` (def 1) | Tame animals, optionally near a place. |
| `mcaquests:sleep_or_rest` | `require_morning` (bool, def true) | Sleep through to morning. |
| `mcaquests:build_near_location` | block target (req.), `location` (anchor, req.), `radius` (1–64, def 8), `count` (def 8) | Place blocks near a place (each position counts once). |
| `mcaquests:enter_structure` | `structure` (id) **or** `structure_tag` (tag) | Enter a generated structure. |
| `mcaquests:deliver_to_villager` | `recipient` (villager, req.), item target (req.), `count` (def 1), `consume` (bool, def true), `destination` (object, optional) | Hand an item to a specific villager. Consumed at hand-off by default; with a `destination` of `townstead_villager_inventory` the goods go **into the recipient's own inventory** instead. That transfer is all-or-nothing — capacity is simulated before a single item leaves the player, a recipient with no room refuses rather than swallowing half a stack, and a replayed interact packet cannot pay twice. Set `"target": "recipient"` on the destination; it is the only value that makes sense here and the validator rejects the others. |
| `mcaquests:find_missing_relative` | `relative` (villager, req. — `family` mode), `biome` (biome target, optional), `structure` (structure target, optional), `min_distance` (0–4096, def 96), `discover_radius` (1–64, def 24), `spawn_distance` (1–64, def 12) | Search the wilds for a relative of the giver who has gone **missing**, and find them. MCA's `missing` means *in the family tree, not deceased, and no entity anywhere in the world* — so there is nobody to walk up to. This objective **materialises** them: once the player is inside the named `biome`/`structure` and at least `min_distance` from the giver, the relative appears `spawn_distance` blocks away with their real identity (same UUID, name, gender, profession — MCA's family tree keeps every link), is highlighted, and completes the objective within `discover_radius`. Never spawns twice, and never spawns a relative who is merely unloaded: an alive villager on any village's resident roll is skipped. Gate the quest on `related_villager_status <relation> missing`. Once found they are an ordinary villager, so later chain stages can `escort_entity` or `deliver_to_villager` them through the usual `"mode": "family"` path — and `missing` flips to false, so a `once` search quest stops being re-offered. **Finding someone is permanent:** abandoning or failing the quest drops the quest, never the villager. |
| `mcaquests:reach_location` | `location` (anchor, req.), `radius` (1–64, def 6), `min_journey` (0–512, optional) | The **player** travels to a location anchor; arrival sticks complete. Border-aware like `escort_entity`: a `home_village`/`nearest_village` anchor completes anywhere **inside the village border**; other anchors use a horizontal (Y-ignored) distance within `radius`. (Distinct from `enter_structure`, which keys off a named structure.) `min_journey` works exactly as it does for `escort_entity`, measured on the **player**: a quest whose destination the player is already standing in is not offered, and arrival is not credited until they have genuinely left and come back. |

### Villager targets

Fields named `villager` / `recipient` select an MCA villager **relative to the quest giver**. The
object always carries a `mode`:

```json
{ "mode": "self" }                                       // the giver
{ "mode": "profession", "profession": "minecraft:weaponsmith" }
{ "mode": "family", "relation": "sibling" }              // any | spouse | parent | child | sibling | grandparent
{ "mode": "family", "relation": "child", "require": "missing" }
{ "mode": "situation_focus" }                            // the villager an open situation is about
{ "mode": "uuid", "uuid": "<uuid>" }
```

Where `villager` is optional it defaults to `self`. Resolution is by UUID, so an **unloaded** target
just pauses the objective (no false completion or failure).

`any` means immediate family — spouse, parents, children, siblings. `grandparent` is a two-hop walk and must
be asked for explicitly; it is deliberately *not* part of `any`.

**One villager, for the whole quest.** A `family` target is **bound to one concrete villager when the quest is
accepted** and never re-resolves. Without that, MCA's family lookup prefers whichever relative happens to be
loaded, so a giver with two children could have the quest log naming one, the highlight glowing another, and
the hand-off crediting either. A quest accepted before this existed binds on its next tick.

Because of that binding, delivering to *a* relative of the right relation is no longer enough — it must be **the
villager the quest named**. If the bound villager dies or is gone for good the objective simply pauses (it never
fails on its own); abandon the quest to drop it.

**Finding the target.** For an *active* quest the objective line resolves the target's **real name** and home
village — e.g. "Deliver 1× Paper to **Hans (the quest giver's sibling) — Oakvale**" (the name comes from MCA and
works even when the relative is unloaded); the target villager is **outlined** through walls while it is loaded,
and the HUD tracker names them with a live distance and compass bearing. The outline is sent to the quest owner
alone — other players never see your markers. Toggle with `highlightQuestTargets` (and
`showQuestTargetDirection` client-side); a quest whose objectives target nobody falls back to highlighting the
**giver**, which is the right answer for the many quests written in the first person ("bring *me* six loaves").

#### `require` — who a family target is allowed to name

A `family` target states which villagers it is willing to be about. `require` takes any of the seven
relative statuses below, and **defaults to `reachable`**: a real person, not dead, not one of the two
ancestors MCA invents for every villager it spawns, and either loaded or on a village roll that says where
to find them.

```json
{ "mode": "family", "relation": "sibling", "require": "same_village" }
```

The default is the safe one, so a pack that has never heard of `require` gets the right behaviour. A quest
that *deliberately* names someone dead or missing has to say so — `"require": "dead"`, `"require":
"missing"` — and `"require": "any_known"` is the old loose behaviour for a pack that genuinely wants
"anybody in the family tree at all".

**A family target needs a gate, and the loader now says so.** A quest whose target requires the villager to
be findable (`reachable`, `alive`, `nearby`, `same_village`, `infected`) must establish that one exists, with
a `related_villager_status` leaf on the same relation:

```json
"conditions": { "type": "mcaquests:related_villager_status", "relation": "sibling", "status": "same_village" }
```

Without it the reload reports the file, the objective index, the relation and the block to add. It is a
**warning** by default and a hard error under `strictJsonValidation`; a future release will promote it.

Two things that do *not* count as a gate:

- **A leaf inside `any_of`.** An alternative is not a guarantee.
- **`is_family_member`.** That condition asks how the *player* is related to the giver, in the opposite
  direction. It says nothing about whether the giver has a findable relative.

A gate on a narrower relation does count: proving a sibling exists proves a member of `any` exists. The
reverse does not, and `grandparent` covers neither — `any` is the union of spouse, parents, children and
siblings, deliberately excluding grandparents.

#### `situation_focus`

`{ "mode": "situation_focus" }` names the villager an open situation is **about** — the one who collapsed,
caught the infection, or went missing — rather than a relative of whoever happens to be telling you about
it. Only meaningful inside a situation's `offer`; anywhere else it resolves to nobody, which makes the
objective unofferable rather than silently pointing somewhere else.

### Location anchors

Fields named `destination` / `location` / `near` resolve to a position via an `anchor`:

```json
{ "anchor": "home_village" }                             // the giver's MCA home village (arrival = inside its border)
{ "anchor": "nearest_village", "radius": 128 }           // nearest village to the giver, within radius (arrival = inside its border)
                                                         // falls back to the nearest VANILLA village (#minecraft:village) when MCA knows none
{ "anchor": "giver_pos" }                                 // the giver's current position
{ "anchor": "villager", "villager": { "mode": "family", "relation": "spouse" } }
{ "anchor": "workstation" }                               // the giver's job site
{ "anchor": "bed" }                                       // the giver's home/bed
{ "anchor": "bed", "villager": { "mode": "family", "relation": "parent" } }   // that relative's bed
{ "anchor": "coords", "pos": [100, 64, -200] }
{ "anchor": "nearest_other_village", "radius": 2048 }     // the next MCA village along, never the giver's own
{ "anchor": "townstead_building", "building_type": "dock", "minimum_level": 2 }
```

**Two of these are frozen; the rest resolve live.** A bed that moves with its owner should keep being the
right destination, so `bed` is re-resolved every poll. But `townstead_building` and
`nearest_other_village` are *choices* among several valid answers, and a choice re-made every second is
not a destination — the map marker would jump the moment a closer dock was built. Both are decided once
when the quest is accepted and kept.

That freezing is also what makes two objectives agree. "Place six lanterns at the dock" and "place twelve
chains at the dock" in the same quest are one instruction about one dock; they share a binding, so they
cannot drift to different docks the moment a second one qualifies.

| Field | Anchor | Default | Meaning |
|---|---|---|---|
| `building_type` | `townstead_building` | **required** | The building family. `dock` covers `dock_l1` through `dock_l3`; `butcher` and `butcher_shop` are the same family. |
| `minimum_level` | `townstead_building` | `1` | Minimum registered tier. |
| `selection` | both frozen anchors | `nearest_to_giver` | Or `nearest_to_player_at_accept`. Ties break on the lower registered id, so the same world always chooses the same one. |
| `radius` | `nearest_other_village` | `2048` | How far to look. |
| `villager` | `villager`, `bed`, `workstation` | the giver | Whose home or workplace is meant. Stating it anywhere else is a load error, because it would be read by nothing. |

**Whose bed?** `bed` and `workstation` mean *the giver's* unless the anchor names somebody. That is right
for the common "walk me home", where the giver is the person being walked, and silently wrong the moment
the escortee is anyone else: through 1.5.0 the bundled "one last walk" told the player to see an ageing
parent "safely back to their bed" and sent them to the parent's child's house instead — the escort
completed, at the wrong building. So an `escort_entity` whose escortee is not the giver and whose
destination is an unowned `bed` or `workstation` is now a **load error**. Say whose it is.

If the building is demolished later the frozen position stays a perfectly good place to walk to, build at
or defend. An objective that needs the building to still be *registered* reads that live and separately.

### `source` — where a thing can be got

Any of `obtain_item`, `craft_item`, `fish_item`, `kill_entity`, `break_block`, `place_block` and
`item_delivery` may carry an optional `source`, which is what the world marker and the tracker's
direction line point at while that objective is the one the player is on.

```json
{ "type": "mcaquests:kill_entity", "entity": "minecraft:blaze", "count": 8,
  "source": { "structure": "minecraft:fortress" } }

{ "source": { "structure_tag": "mcaquests:ocean_ruins" } }
{ "source": { "biome": "minecraft:warm_ocean" } }
{ "source": { "biome_tag": "minecraft:is_ocean" } }
{ "source": { "block": "minecraft:sweet_berry_bush" } }
{ "source": { "block_tag": "minecraft:iron_ores" } }
{ "source": { "dimension": "minecraft:the_nether" } }
{ "source": { "anchor": { "anchor": "workstation" } } }
```

| Field | Meaning |
|---|---|
| `structure` / `structure_tag` | Locate the nearest generated instance. Vanilla's `/locate`. |
| `biome` / `biome_tag` | Locate the nearest matching biome. Vanilla's `/locatebiome`. |
| `block` / `block_tag` | The nearest matching block within 48 blocks, searched outward from the player and **only in loaded chunks**. For the things a village errand is actually about: wheat comes from `minecraft:wheat`, iron from `minecraft:iron_ores`. Re-checked against the world each pass and dropped the moment it stops matching, because a berry bush gets picked and a marker on empty ground is worse than none. |
| `dimension` | Point at **the way in** — the nearest lit nether portal, or the nearest stronghold for the End — and stop pointing once the player is through. Not the dimension itself, which is not a place you can walk to. |
| `anchor` | Any [location anchor](#location-anchors). Use it to point at a **village**: `{"anchor": {"anchor": "nearest_village"}}` marks the nearest one, `home_village` the giver's own, `nearest_other_village` the next one along. There is no separate `village` field — the anchor language already says all three, and a second spelling would be a second thing to keep in step. |

At least one must be set, or the pack fails to load: a `source` that names nothing is a marker that
never appears with no way to find out why. When several are set, the dimension and the anchor win first,
then `block` — a berry bush twenty blocks away is a better answer than an ocean two thousand blocks
away, and far cheaper to have found — then the structure and biome searches.

**Nothing is inferred, and that is deliberate.** There is no index of where eight prismarine crystals
are, and a guess would send the player somewhere confidently wrong — worse than sending them nowhere,
because they would go. An objective with no `source` draws no marker at all and its text carries the
whole instruction, exactly as before this field existed.

**Cost.** A structure or biome `source` runs a real world search, once, on the first pass that needs it;
the answer is written into the objective's own progress and survives a restart. A search that finds
nothing is retried no more often than `guidanceSearchIntervalTicks`. Only the quest the player is
following searches at all.

**`item_delivery` has a fallback.** With no `source`, it points at the villager the goods are for.
That does not claim to say where the wheat is — nothing can — but where the quest wants you to end up is
a true and useful answer to "where next", and for a village errand the fields are usually within sight of
the villager anyway. Give it a `source` when you know better and the source wins.

`visit_dimension`, `enter_structure`, `visit_biome`, `reach_location`, `defend_location`,
`build_near_location`, `escort_entity`, `talk_to_profession`, `trade_with_villager`,
`sleep_or_rest`, every `townstead_*` objective and every villager-targeted objective already
know where they are sending the player and need no `source`.

A Townstead objective points at the resident its query names, or — for the ones about the
settlement as a whole — at the giver's home village; `townstead_building_registered` points at
the nearest building of the family it asks for, and at the village when there is not one yet to
point at. `sleep_or_rest` points at the **player's own** bed, not the giver's: it is the player
who has to sleep, and sending them to somebody else's house would be a marker on a place the
quest is not about.

### Why is nothing marked?

Run **`/mcaquests debug guidance`**. It lists every active quest, what each one would point at, and
which one the marker actually chose — so "the objective has no place attached" is distinguishable from
"a search found nothing in range" and from "the feature is off", which from inside the game look
identical.

And **`/mcaquests debug waypoints`** for the map half: which of JourneyMap and Xaero bound, which
members did not, and the result of a round-trip probe. Both mods can decline a waypoint without
throwing, and neither says so, which from inside the game is indistinguishable from having no
minimap installed at all.

### Validation & MCA limitations

- Unknown item/block/entity ids fail at load (registry-backed codecs). Radius/duration/count ranges are
  enforced by the codec. The objective validator additionally reports a `villager`/`recipient` missing
  the field its `mode` needs, a `coords` anchor without `pos`, an unknown family `relation`, a
  `trade_with_villager` setting both `villager` and `profession`, and an `enter_structure` with neither
  selector — each message names the quest and objective index.
- **`enter_structure`** ids belong to a *dynamic* registry, so they can only be checked for syntax at
  load (a warning, never a hard error); an unknown/unloaded structure simply never matches at runtime.
- **`cure_villager`** observes MCA's infection state (it latches "seen infected", then completes on the
  return to not-infected). MCA's infection/cure model is the authority; if that state is unavailable the
  objective never completes (it never crashes). Targets that are never infected can't be completed.
- **`sleep_or_rest`** implements player sleep only; "make a villager use their bed" is not implemented
  (MCA does not expose villager sleep reliably).
- **`find_missing_relative`** will not act on a relative who is deceased, is a player, is a filler ancestor
  MCA generated to pad a family tree, is already in the world, or is on any village's resident roll. Any of
  those simply pauses the objective — it never errors. In multiplayer two players searching for the same
  relative produce exactly one villager: the second spawn is refused and both complete on proximity.
- **Workstation/bed anchors** depend on the villager they name having an assigned job site / home in
  MCA; otherwise they resolve empty and the objective pauses.
- **`source`** ids for structures and biomes belong to *dynamic* registries, so a name this world has
  never heard of cannot be caught at load. It produces no marker rather than an error, and the quest
  still says in words where to go.

---

## Rewards

Granted atomically on turn-in (items insert-or-drop, then XP, effects, loot, and MCA hearts).

| `type` | Fields | Notes |
|---|---|---|
| `mcaquests:item` | `item`, `count` (default 1) | Item stack. Never scaled by config — an explicit item reward means exactly what it says. |
| `mcaquests:currency` | `min`, `max`, `difficulty` — all optional | Semantic money. See [Currency rewards](#currency-rewards). |
| `mcaquests:xp` | `amount` | XP points. Scaled by `xpRewardMultiplier`. |
| `mcaquests:xp_levels` | `levels` | XP levels. Scaled by `xpRewardMultiplier`. |
| `mcaquests:effect` | `effect`, `duration` (ticks), `amplifier` | Status effect. |
| `mcaquests:hearts` | `amount` | MCA hearts with the giver. Clamped by `min/maxHeartsReward` and scaled by `heartsRewardMultiplier`. |
| `mcaquests:loot_table` | `loot_table` (resource location) | Rolls a loot table. Requires `allowLootTableRewards` (on by default). |
| `mcaquests:command` | `command` (string) | Runs a command. **Disabled** unless `allowCommandRewards = true`. |
| `mcaquests:village_reputation` | `amount` | Adds independent mod-side reputation to the giver's village (see Progression). |
| `mcaquests:grant_title` | `title` (resource location), `scope` (`village`/`global`, default `village`) | Awards a player title (see Progression). |

### Currency rewards

*(1.1.0)*

`mcaquests:currency` asks for *money* without naming an item. The server decides what money is (emeralds by default, or Create: Numismatics coins, or any item) via `currencyProvider` — so your pack works on an economy modpack and a vanilla-ish server without being rewritten, and without depending on Numismatics.

```json
{ "type": "mcaquests:currency" }                        // range from the quest's difficulty
{ "type": "mcaquests:currency", "difficulty": "hard" }  // range from an explicit band
{ "type": "mcaquests:currency", "min": 4, "max": 9 }    // explicit range, ignores the bands
```

- With no `min`/`max`, the range comes from the difficulty band's `[rewards.currency]` config (see [CONFIG.md](CONFIG.md#rewardscurrency)).
- The band is the reward's own `difficulty`, else the quest's, else `medium`.
- `min`/`max` are validated at load: negative values or `min > max` are reported by `/mcaquests validate`.

**The amount is rolled once, when the quest is accepted**, and stored with it. Reopening the menu, reconnecting, reloading the world, or a retried turn-in packet all read that same number back — a player cannot reroll a payout by reopening the UI. An offer card shows the honest *range*; an accepted quest shows the exact frozen amount, which is what turn-in pays. Project phase rewards freeze the same way, per phase, so everyone who helped — including someone who was offline and collects later — is paid the same.

If the configured currency item can't be resolved (the mod isn't installed, or the id is a typo), the reward falls back to emeralds or is skipped per `currencyFallback`, and logs once.

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
| `mcaquests:quest_completed` | `quest` (resource location), `scope` (`global`/`giver`, default `global`) |
| `mcaquests:quest_not_completed` | `quest` (resource location), `scope` (`global`/`giver`, default `global`) |
| `mcaquests:quest_failed` | `quest` (resource location), `scope` — true once that quest has failed (giver died / timed out) |
| `mcaquests:quest_abandoned` | `quest` (resource location), `scope` — true once the player abandoned that quest |
| `mcaquests:quest_declined` | `quest` (resource location), `scope` — true once the player turned that offer down. Declining costs nothing, so branch on it as a preference ("offer the softer version instead"), not as a punishment |
| `mcaquests:village_reputation` | `min`, `max` — raw reputation with the giver's village |
| `mcaquests:reputation_tier` | `min_tier` (required), `max_tier`, `ladder` (default `mcaquests:default`) — tier with the giver's village (see Progression) |

> **`scope`** on the four quest-state conditions chooses whose history they read. `global` (the default)
> counts the quest across all villagers — the historical behaviour. `giver` counts only what the player did
> with the villager currently being talked to, which is how **per-villager relationship arcs** work (see
> [Quest chains](#quest-chains)).

### MCA-aware conditions

These gate a quest on the giver's **MCA Reborn** state (relationship to the player, family, age, personality, mood, village/home, health, infection). They are evaluated server-side when the quest menu is opened, and all **fail safe**: if MCA data is missing or the giver is not an MCA villager, the condition is treated as **not met** and a debug line is logged — the server never crashes. Enum-like field values are validated when the pack loads; an unknown value skips the quest with a logged error (or is a hard error when `strictJsonValidation = true`). All are **supported** unless noted.

| `type` | Fields | True when |
|---|---|---|
| `mcaquests:is_player_spouse` | *(none)* | The giver is married to the interacting player. |
| `mcaquests:relationship_state` | `states` (list, required) | The giver's relationship state is in the list. Values: `single`, `promised`, `engaged`, `married_to_villager`, `married_to_player`, `widow`. |
| `mcaquests:is_family_member` | `relation` (default `any`) | The giver is that relation **to the player** in the family tree. Values: `any`, `spouse`, `parent`, `child`, `sibling`, `grandparent`. Player-relative, and so the *opposite direction* to `related_villager_status`: the giver being the player's `child` means the player is one of the giver's parents. **This is not a gate for a `family` target** — it says nothing about whether the giver has a findable relative. |
| `mcaquests:age_group` | `groups` (list, required) | The giver's age is in the list. Values: `baby`, `toddler`, `child`, `teen`, `adult`. *(MCA has no "elder" age — see Limitations.)* |
| `mcaquests:personality` | `personalities` (list, required) | The giver's personality is in the list. Values: `athletic`, `confident`, `friendly`, `flirty`, `witty`, `shy`, `gloomy`, `sensitive`, `greedy`, `odd`, `lazy`, `grumpy`, `peppy`. |
| `mcaquests:mood` | `min`/`max` (ints) and/or `moods` (list); at least one required | The giver's mood value is within `min`/`max` **and** (if given) its mood name is in `moods`. Mood names are data-driven in MCA, so they are not checked against a fixed list. |
| `mcaquests:village_member` | `value` (bool, default `true`) | Whether the giver belongs to a home village equals `value`. |
| `mcaquests:has_home` | `value` (bool, default `true`) | Whether the giver has an assigned home equals `value`. |
| `mcaquests:health_below` | `threshold` (required, `(0,1]`) | The giver's health fraction (current ÷ max) is below `threshold`. |
| `mcaquests:infected` | `min_progress` (default `0`) | The giver's zombie-infection progress is `> 0` and at least `min_progress` (range `[0,1]`). |
| `mcaquests:related_villager_status` | `relation` + `status` (both required) | The giver has at least one relative of `relation` (`any`/`spouse`/`parent`/`child`/`sibling`/`grandparent` — the same set `"mode": "family"` targets accept) whose `status` matches. The seven statuses are listed below, and are **exactly** what a target's `require` accepts, so a quest can gate on precisely the question its objective will later ask. |
| `mcaquests:giver_distance_from_village` | `min_distance` (default `0`), `require_outside_border` (bool, default `false`) | The giver is at least `min_distance` blocks from its **home-village center** (and, when `require_outside_border`, also outside the village border). Fails safe to *not met* when the giver has no home village — so a villager standing in its own square is never offered an "escort me home" quest. The gate for lead-style escorts and "out after dark" content; pair with `time:NIGHT` via `any_of`. |

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

A lead-style escort is gated to "far from home, or a little out after dark" like so:

```json
"conditions": {
  "any_of": [
    { "type": "mcaquests:giver_distance_from_village", "min_distance": 80 },
    { "all_of": [
        { "type": "mcaquests:time", "period": "NIGHT" },
        { "type": "mcaquests:giver_distance_from_village", "min_distance": 24 }
    ] }
  ]
}
```

#### Relative statuses

Used by `related_villager_status`'s `status` and by a villager target's `require`. One vocabulary, so the
gate and the target cannot disagree about who is in scope.

| `status` | Means |
|---|---|
| `reachable` | **The default for a `family` target.** A real person a quest can send you to: not dead, not one of MCA's invented ancestors, not a player, and either loaded or on some village's resident roll. |
| `alive` | A real person who is not dead. Includes the missing — being missing is not being dead. |
| `nearby` | Alive and standing within interaction range of the giver. |
| `same_village` | Alive **and** on the giver's own village roll. The "alive" half matters: MCA never removes the dead from a village's resident roll, so this used to be satisfied by relatives who had died. |
| `missing` | Alive, with no body anywhere in the world and on no village's resident roll. That last part separates "genuinely vanished" from "merely outside render distance", and is what stops `find_missing_relative` spawning a duplicate of someone alive and well. |
| `dead` | Flagged deceased in the family tree, and not one of MCA's invented ancestors — a villager the game made up to pad a family tree was never alive, so mourning them is not a thing. |
| `infected` | Alive and part-way through MCA's zombie infection **right now**. Read off a loaded body, so an unloaded relative never satisfies it — which is the point: this is the gate a `cure_villager` objective about a relative needs, and a cure quest about kin nobody can see turning is a quest that cannot advance. |
| `any_known` | Anyone with a family-tree node at all, dead or invented. The old unfiltered behaviour, available to a pack that deliberately wants it. |

**Failure behavior.** A non-MCA giver, a missing/partly-loaded relationship or family graph, or any internal MCA error all evaluate to *not met* (debug-logged), never an exception. `health_below` and `related_villager_status` read live/persistent state, so a quest can appear or disappear as that state changes — reopen the menu to refresh.

**Limitations.** `age_group` does **not** support `elder`: MCA Reborn has no elder age state (its ages are baby/toddler/child/teen/adult). All MCA access is isolated behind the mod's compatibility layer; if MCA Reborn is absent these conditions simply never match. See the built-in `relations/` quests for working examples of every category.

### Offer groups

A villager offers three quests at a time. With a catalogue this size, plain weighted selection regularly
fills all three with variations of the same errand, and the menu stops looking like a village with things
going on.

```json
"offer_group": "townstead_need"
```

During a reroll the menu takes **at most one quest from each group** before allowing seconds. Grouping
happens *inside* a priority tier, never across tiers, so an emergency can never be crowded out by
diversity — and a quest with no `offer_group` is never excluded by this pass, so every pre-1.4.1 datapack
selects exactly as it always did.

The built-in groups are `townstead_need`, `townstead_schedule`, `townstead_work`, `townstead_life`,
`townstead_spirit`, `townstead_season`, `core_adventure` and `core_relationship`. Your own names work
just as well — the group is only ever compared against other groups, so any string that means "this kind
of quest" will do.

`offer_group` is server-only and never crosses the wire.

---

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

### Delivering into a villager's inventory

Both `mcaquests:item_delivery` and `mcaquests:deliver_to_villager` take an optional `destination`:

```json
"destination": { "type": "townstead_villager_inventory", "target": "giver" }
```

Without it the goods are consumed on hand-over, which is what every quest did before 1.4.0 and still the
default. With it they land in the villager's real inventory, where **Townstead lets them actually be
eaten and used** — which is what turns "bring me bread" from a token gesture into the thing that keeps
somebody alive.

**No Townstead capability is required.** The inventory belongs to MCA and the whole transfer is vanilla
container work; Townstead only supplies the reason to care. A delivery quest therefore keeps working when
Townstead is absent rather than suspending, because nothing about it has stopped being possible.

`townstead_village_storage` is **not** implemented and never parses: Townstead exposes no registered
storage API that could be written to safely, and a destination that silently ate a donation would be
worse than none.

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
| `once` | — | Completable a single time, ever (per villager for a chain stage, per player otherwise). |
| `repeatable` | — | Available again immediately. |
| `period` | `period`, `scope`, `fallback_cooldown_ticks` | Once per Townstead calendar period. |

### Calendar-relative repeats

```json
"repeat": { "type": "period", "period": "season", "scope": "giver", "fallback_cooldown_ticks": 96000 }
```

`period` is `townstead_week`, `season` or `year`. A tick cooldown cannot express "once a season", because
a Townstead season may be three days on one server and thirty on another — so a completion records a
**token** naming the period it happened in, and the quest is eligible again exactly when the live token
differs. The token includes the calendar profile id, so switching profiles mid-world does not collide two
different definitions of spring.

Nothing here assumes four seasons, seven-day weeks or a fixed year length; every value comes from the
loaded profile. `scope` is `giver` (default) or `global`, matching cooldown scoping.

**A missing calendar never grants a second reward.** If `READ_CALENDAR` is unavailable the quest falls
back to `fallback_cooldown_ticks`, which is armed at completion alongside the token for exactly this case,
and an already-accepted calendar-bound objective suspends rather than completing on an unreadable clock.

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
  "fail_on_target_lost": false,
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
| `deadline_time` | int (0–24000) | Fail when the world clock next reaches this **time-of-day** after acceptance, e.g. `23000` for "before sunrise". Accepting exactly on the boundary grants a full day. Measured on the world clock, so sleeping through a night and `/time set` both advance it; `deadline_ticks` is elapsed time and neither affects it. |
| `require_weather` | `clear` / `rain` / `thunder` | The quest demands this weather; it fails the instant the weather stops matching. Pair with a `conditions` weather gate so it's only offered when the weather is right. |
| `fail_on_giver_death` | bool | Fail if the quest giver dies, regardless of the global `failQuestIfGiverDies` config. |
| `fail_on_target_lost` | bool (default `false`) | Fail if the villager an objective **bound** dies or leaves the world for good. Off by default: the standing contract is that a quest you cannot currently play is *suspended*, not taken away, so by default the objective shows a reason line instead of a counter, the deadline stops running down, the quest stays abandonable, and it resumes if they come back. Turn it on for a story that should close rather than wait. |

When both `deadline_ticks` and `deadline_time` are set, whichever runs out first wins. Time deadlines
drive the live countdown shown in the HUD tracker; weather/giver-death failures show no countdown.
Quests accepted before 1.5.1 keep the game-time `deadline_time` they were given until they are
re-accepted; only quests accepted from 1.5.1 on follow the world clock.

> **Grace window:** a quest whose objectives are already met (it's "ready to turn in") is **not**
> failed by a deadline or weather trigger — you keep your reward as long as you finish in time. Giver
> death still fails it.

### Outcome — *what happens* on failure

| Field | Type | Default | Meaning |
|---|---|---|---|
| `failure_hearts` | int | `0` | Hearts applied to the giver on failure. Negative = a relationship penalty; `0` = non-punitive. |
| `retry_after` | int (≥0) | — | Cooldown (ticks) before the quest can be offered again after a **failure**. Omit to follow the normal `repeat` rule. Pairs well with `fail_on_giver_death` — without it, losing the giver of a chain finale means restarting the arc with somebody else. Contradicts `block_retry`. |
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

It also reports, as **warnings** (they never stop a server starting):

- **Empty or unknown item/block/entity tags** named by an objective. The id parses, but nothing is in the
  tag, so the objective can never advance. Template pools have been checked for this since 1.2.0; plain
  objectives now are too.
- **Biome, dimension and structure ids this world does not have** (including `#tags` that resolve to
  nothing). These live in datapack-driven dynamic registries, so they cannot be checked at load — only
  against a running world, which is what `validate` has. A quest naming one is silently unfinishable.

And as an **error** (a warning outside `strictJsonValidation`):

- A **`cure_villager` objective about a relative with no infection gate**. Conditions are evaluated at offer
  time only, so the gate is the one moment anything asks whether the kin is actually turning. Say either
  `"require": "infected"` on the villager target or
  `{"type": "mcaquests:related_villager_status", "relation": "...", "status": "infected"}`.

### Built-in examples

- `guard/dawn_defense.json` — kill 6 zombies **before sunrise** (`deadline_time`), small heart penalty.
- `cleric/urgent_medicine.json` — deliver medicine within a **tick deadline**, with a recovery quest.
- `fisherman/rain_catch.json` — fish **while it's raining** (`require_weather`); fails when the rain stops.

---

## Quest chains

The optional `chain` block turns a set of standalone quests into a **relationship arc**: one quest
unlocks the next, the UI shows "Part 2 of 4", and follow-ups can branch on whether you completed,
failed, or abandoned an earlier step. Quests with no `chain` block are unaffected.

**A quest with no `chain` can be active with only one villager at a time.** It is one job, and the progress
events credit every active copy of it, so holding "bring me ten wheat" from two farmers would have completed
and paid both from one harvest. Once the player accepts it anywhere, no other villager offers it until it is
turned in, failed or abandoned (`/mcaquests debug quest` says `ALREADY_ACTIVE (with another villager)`).
Chain stages are the deliberate exception, for the reason below.

**Arcs are per-villager.** Chain progress is tracked against the individual villager you are dealing with: a
prerequisite is satisfied only when you completed the earlier stage **with that same villager**, so the same
arc can be lived out independently with different villagers. Standalone (non-chain) quests are unaffected and
stay global. (Under the hood, `prerequisites` compile to `quest_completed` with `scope: giver`; give branch
conditions the same `scope: giver` to keep the whole arc per-villager.)

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
| `prerequisites` | list of quest ids | `[]` | The arc gate: every listed quest must be **completed with this villager** before this one is offered. Compiles into `quest_completed` (`scope: giver`) conditions, merged with any `conditions` block. |
| `unlocks` | list of quest ids | `[]` | Forward pointers to the quests this one leads to. Used for validation (reachability, cycle detection); the actual gating lives on the downstream quest's `prerequisites`/`conditions`. |

> **Deadlines moved.** Per-quest time limits used to live here as `chain.time_limit_ticks`. They are
> now part of the dedicated [`failure`](#failure--deadlines) block (`deadline_ticks`), which works on
> any quest — not just chains — and supports more triggers and outcomes.

### Linear vs. branching

- **Linear** chains use `prerequisites` — "must have completed the previous stage." That's the common case.
- **Branching** uses the explicit outcome conditions in a `conditions` block. A redemption quest gated on
  `{ "type": "mcaquests:quest_failed", "quest": "...", "scope": "giver" }` only appears after the player fails
  that quest **with this villager**; a "no hard feelings" follow-up can use `mcaquests:quest_abandoned`. Add
  `"scope": "giver"` so the branch stays per-villager like the prerequisites (omit it for the old global
  behaviour). To let two branches converge on the same finale, gate the finale with `any_of` over the
  `quest_completed` (`scope: giver`) of each branch (leave `prerequisites` empty and list it in each branch's
  `unlocks` so validation still sees it as reachable).

### Offer priority & weight bonuses

Two optional top-level fields shape *which* eligible quest a villager offers (both work on any quest, not just
chains):

- **`priority`** (int) groups offers into tiers. A villager fills its offer slots from the highest tier down,
  so a higher-priority quest is shown ahead of lower ones. Unset, a chain continuation (stage > 1) defaults to
  tier 1 and everything else to tier 0 — an in-progress arc is preferred over unrelated standalone offers. Set
  `priority` to override (including `0` to opt a continuation out of the default preference).
- **`weight_bonus`** is a list of `{ "when": <condition>, "amount": <int> }`. Each entry adds `amount` to the
  quest's `weight` when its condition holds, so an offer grows likelier as a relationship deepens, as MCA
  hearts rise, or as earlier stages are completed (`quest_completed` with `scope: giver`). `amount` may be
  negative to make a quest rarer.

```json
"priority": 2,
"weight_bonus": [
  { "when": { "type": "mcaquests:hearts", "min": 50 }, "amount": 10 },
  { "when": { "type": "mcaquests:quest_completed",
              "quest": "mcaquests:mapmaker_expedition_1_survey", "scope": "giver" }, "amount": 5 }
]
```

Selection stays deterministic and server-authoritative — the same player/villager/day always yields the same
offers. Within a tier the weighted draw uses each quest's effective weight (base `weight` plus matching
bonuses, floored at 1).

### Validation

`/mcaquests validate` reports chain problems in two tiers, each naming the quest, chain, field, and referenced
id. **Errors** (these also block load when `strictJsonValidation` is on): unknown or disabled
`prerequisites`/`unlocks`/condition targets, a blank chain id, `stage` below 1, `stage` above `stage_total`,
a quest listing itself, a later stage nothing can reach, circular `prerequisites` or `unlocks`, and impossible
gates (requiring a quest to be both completed and not-completed). **Warnings** (reported but never fatal):
inconsistent `stage_total` across a chain, two non-branching quests sharing a stage (they'd be offered
together), an `unlocks` pointer the target never references back, and a branch gated on a quest that can never
fail. Fix and `/mcaquests reload`.

`/mcaquests debug villager` lists every chain stage the nearest villager could give and why each is offered /
eligible / locked / hidden / completed / on cooldown; `/mcaquests debug quest <id>` prints the full gate
checklist and per-villager progress for one quest — use these to trace a stuck arc.

The five built-in arcs under `data/mcaquests/mcaquests/quests/chains/` — `farmer_family` (linear),
`guard_safety` and `jobless_friendship` (branching), `librarian_knowledge`, and `mapmaker_expedition` (a
branching arc that also shows `priority`, `weight_bonus`, and a `failure` deadline with `retry_after`) — are
complete worked examples; copy one as a starting point.

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
- The reserved token **`{player}`** inserts the player's MCA character name (the name set in MCA's
  character-creation screen), falling back to their Minecraft username when MCA is absent or no name was set.
  It works in the dialogue, title, and chain arc/chapter text of **any** quest — not just templates — and
  cannot be shadowed by a template variable named `player`. Being dialogue-only, `{player}` is never
  substituted into objective/reward JSON. Example: `"offer": { "text": "Well met, {player}! Could you help me?" }`.
  Because hand-authored (non-template) `text` dialogue now runs through this same placeholder pass, the
  `{{`/`}}` escapes apply to it too (a literal `{{` renders as `{`), and a `"with"` list on a hand-authored
  `"translate"` line is now filled in rather than ignored.

### Validation

`/mcaquests validate` reports template problems, each naming the quest and field: registry `ids` that don't
exist, `tags` that are empty/unknown, `{placeholders}` that reference no declared variable, a template
variable named `player` (which would shadow the reserved `{player}` token — rename it), and substituted
objective/reward JSON that fails to parse. The reserved `{player}` token is exempt from the
declared-variable check. Empty pools also **fail safe at runtime** — the offer is simply skipped (with a
debug log) rather than crashing. Under `strictJsonValidation` these become hard errors.

The five built-in templates under `data/mcaquests/mcaquests/quests/templates/` (farmer crop request, guard
mob cull, fisherman catch, librarian knowledge, cartographer survey) are complete worked examples — copy one
as a starting point.

---

## Progression: reputation tiers & titles

*(spec 0.7.0; gated by `enableReputationTiers`, on by default.)*

Every village carries an independent mod-side **reputation** value, raised by the `village_reputation`
reward (on quests and projects). **Reputation tiers** put named, ordered thresholds on top of that value,
and players can earn **titles** as they climb.

### Reputation tier ladders

Define a ladder in `data/<ns>/mcaquests/reputation_tiers/<name>.json`. The id `mcaquests:default` is the
ladder used by the UI and by any `reputation_tier` condition that omits `ladder`; ship your own
`default.json` to override the built-in one.

```json
{
  "tiers": [
    { "id": "stranger",     "threshold": 0,   "name": "Stranger" },
    { "id": "acquaintance", "threshold": 25,  "name": "Acquaintance" },
    { "id": "friend",       "threshold": 75,  "name": "Friend" },
    { "id": "honored",      "threshold": 150, "name": "Honored",  "grants_title": "mcaquests:honored_of_village" },
    { "id": "revered",      "threshold": 300, "name": "Revered",  "grants_title": "mcaquests:revered_of_village" }
  ]
}
```

- `threshold` is the **inclusive minimum** reputation for the tier. Tiers must **strictly ascend**, and the
  lowest threshold must be `<= 0` so every value maps to a tier (validated; invalid ladders are skipped).
- `grants_title` (optional) auto-awards a village-scoped title the first time a village reaches this tier.

Gate a quest on a tier with the `reputation_tier` condition:

```json
{ "type": "mcaquests:reputation_tier", "min_tier": "friend" }
```

### Titles

A title is just a resource location. Award one with the `grant_title` reward:

```json
{ "type": "mcaquests:grant_title", "title": "mcaquests:village_friend", "scope": "village" }
```

`scope` is `village` (attached to the giver's village; no-op if no village resolves) or `global`. Titles
work even without a definition (the id is displayed), but you can supply a display name and scope in
`data/<ns>/mcaquests/titles/<name>.json`:

```json
{ "name": "Friend of the Village", "scope": "village" }
```

Players see their reputation, tiers, and titles in the **Journal** (Open Journal keybind, or the button in
the Quest Log). `/mcaquests reputation get|set|add|tiers` and `/mcaquests title grant|list|clear` help with
testing; `/mcaquests validate` warns about `grant_title` rewards with undefined titles and `reputation_tier`
conditions naming unknown tiers.

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
| `mcaquests:townstead_building_project` | `building_type` (required), `minimum_level` (def `1`), `count` (def `1`) | *(optional [Townstead](TOWNSTEAD.md))* The village has that many buildings of the family at the tier. **Polled**, not banked: it reads the village's real building registry, so it is satisfied by whoever raises the dock, and it un-satisfies if the dock is lost. |
| `mcaquests:townstead_spirit_project` | `spirit` (optional), `points_delta` **or** `target_tier` | *(optional Townstead)* The village's character has grown — either by so many points from where the project started, or up to a tier outright. `points_delta` freezes its baseline when the project opens, so pre-existing progress does not count. |
| `mcaquests:townstead_workforce_project` | `professions` (list, required), `minimum_tier` (def `1`), `count` (def `1`) | *(optional Townstead)* That many residents practise one of those trades at the tier — "three journeyman farmers before the granary is worth building". |
| `mcaquests:townstead_resident_wellbeing_project` | `minimum_observed` (def `1`), `minimum_fraction` (0–1), `hunger_min`, `energy_min`, `hold_ticks` | *(optional Townstead)* Enough of the village has been fed and rested for long enough. `minimum_observed` stops a one-resident village trivially satisfying a fraction. |

The four `townstead_*` project objectives are **polled** rather than contributed to: they read village state on the project sweep instead of banking a player's donation, so they progress and regress with the village itself. Without Townstead installed they simply sit at zero and the project stalls rather than breaking. See **[TOWNSTEAD.md](TOWNSTEAD.md)**.

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
| `maxConcurrentProjectsPerScope` | `8` | Cap on simultaneously active projects sharing a scope. New projects only — one already under way is never hidden by a lowered cap. |
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

## Situations (the Living Village)

*(spec 0.8.0; gated by `enableSituations`, on by default.)*

A **situation** is a transient, village-shared condition opened by something that happens in the world
(a raid, a death, an infection, missing kin, famine, nightfall). While it is open it surfaces a
**dynamic, time-limited quest offer** on eligible nearby villagers, then **resolves** with a
success/failure/cleared outcome that moves village reputation. Situations are server-authoritative,
persisted in the world save, and survive restart.

Define one in `data/<ns>/mcaquests/situations/<name>.json`:

```json
{
  "id": "mcaquests:after_raid_recovery",
  "scope": "village",
  "duration_ticks": 9600,
  "cooldown_ticks": 48000,
  "trigger": { "type": "mcaquests:raid" },
  "outcomes": {
    "success": { "reputation": 18 },
    "failure": { "reputation": -12 },
    "cleared": { "reputation": 0 }
  },
  "offer": {
    "weight": 12,
    "giver": { "professions": ["mca:guard"], "adult_only": true },
    "title": { "text": "Drive Them Back" },
    "dialogue": {
      "offer":   { "text": "The raid is upon us! Cut down the attackers — six of them, quickly!" },
      "ready":   { "text": "They're breaking! You've saved us." },
      "complete":{ "text": "The village still stands because of you." },
      "failed":  { "text": "We lost too much before help came." }
    },
    "objectives": [ { "type": "mcaquests:kill_entity", "entity": "minecraft:zombie", "count": 6 } ],
    "rewards":    [ { "type": "mcaquests:hearts", "amount": 25 } ]
  }
}
```

### Top-level fields

| Field | Default | Meaning |
| --- | --- | --- |
| `id` | (required) | Unique id. The offer reuses the quest lifecycle under a synthetic id `mcaquests:situation/<ns>/<path>`. |
| `enabled` | `true` | Set `false` to ship a definition without activating it. |
| `scope` | `village` | Who surfaces the offer / where the outcome lands: `village`, `villager` (the focal one), or `family` (an MCA lineage). |
| `duration_ticks` | `24000` | How long the situation stays open. The accepted quest's deadline is anchored to this, so the HUD countdown and failure machinery apply automatically. |
| `cooldown_ticks` | `24000` | Per-village cooldown before this definition can open again there. |
| `trigger` | (required) | What opens it (see below). |
| `outcomes` | none | `success` / `failure` / `cleared`, each `{ "reputation": <int>, "hearts": <int> }` (both default 0). Reputation goes to the giver's village; hearts go to the focal villager (scopes `villager`/`family`). |
| `offer` | (required) | The dynamic quest body (see below). |

### Triggers

The `"type"` field selects the trigger, exactly like objectives/conditions/rewards. Detection is
player-proximity-driven (villages near players are scanned periodically) plus event-driven on death.

| Type | Fields | Fires when |
| --- | --- | --- |
| `mcaquests:raid` | — | A raid is active at the village. Closes as **cleared** if the raid ends first. |
| `mcaquests:villager_death` | `relation` (default `any`) | A village resident dies. `relation` narrows **who may raise it**: with `child`, only a villager who lost a child offers the situation. Every death still opens it — the village has lost someone either way. |
| `mcaquests:infection` | `min_progress` (0–1, default 0) | A resident's zombie-infection reaches the threshold. |
| `mcaquests:missing_kin` | `relation` (default `any`) | A resident has a missing relative of that relation. Narrowing it means the situation fires **only** for that kind of loss — `child` will not open on a missing spouse. |
| `mcaquests:low_food` | `threshold` (default 16) | The village's banked edible items drop to the threshold. Closes as **cleared** if food recovers. |
| `mcaquests:night` | `require_full_moon` (default false) | Nightfall in the village. |
| `mcaquests:townstead_need` | `need` (required), `minimum_fraction` (0–1) | *(optional [Townstead](TOWNSTEAD.md))* That share of the village has crossed into a need crisis. Banded: it closes only once the village recovers past a margin (`needCrisisHysteresis`), so a village sitting on the line does not flap the same famine on and off. |
| `mcaquests:townstead_collapse` | *(none)* | *(optional Townstead)* A resident has collapsed. |
| `mcaquests:townstead_profession_tier` | `profession` (optional), `minimum_tier` | *(optional Townstead)* A resident **rises** to that tier. Edge-triggered from a persisted baseline, so an already-master blacksmith never fires it. |
| `mcaquests:townstead_spirit` | `spirit` (optional), `minimum_tier` | *(optional Townstead)* The village gains a spirit tier, or its dominant character changes. |
| `mcaquests:townstead_building` | `building_type` (optional), `minimum_level` | *(optional Townstead)* A matching building is newly registered or upgraded. |

### The `offer` block

The offer is the body of a quest: it accepts the same `weight`, `title`, `giver`, `dialogue`,
`objectives`, `rewards`, `turn_in`, `template`, `conditions`, and offer-shaping (`priority`,
`weight_bonus`, `difficulty`, `offer_group`) fields a normal quest does. It has no `id` and no `chain`.
A situation always *opens* on its signal; its scope, its giver gate and its `conditions` decide which
villager is allowed to be the one who asks. A `dialogue.failed` line is shown if the situation fails.

Objectives resolve relative to the giver, just like ordinary NPC/village objectives — **or** relative to
the villager the situation is about, with `{ "mode": "situation_focus" }`. Prefer the latter whenever the
situation names someone: "cure your relative" asked of any adult in the village is a different quest from
"cure the villager who is actually infected".

**Situation offers pass the same gates static quests do**, as of 1.4.3: conditions, cooldowns, repeat
rules, the already-satisfied check, and the check that the villager an objective names actually exists.
Before that they were appended to the offer pool *after* the static filter chain and skipped all of it.

### Resolution

- **Success** — the first player to complete the offer resolves the situation: its `success` outcome is
  applied and it stops being offered. (Other players' rewards for their own copies are unaffected.)
- **Failure** — the deadline expires: the `failure` outcome is applied and any still-active copies fail.
- **Cleared** — the underlying condition lifts on its own (raid ends, food recovers): the `cleared`
  outcome is applied, usually neutral.

### Throttling

`maxConcurrentSituationsPerVillage` caps how many are open in one village at once; `cooldown_ticks`
(per definition) and `situationGlobalCooldownTicks` (per village) space them out. Suppressed openings
are logged. `maxSituationOffersPerMenu` caps how many a single villager surfaces; situation offers
otherwise compete with static quests, defaulting above them via `situationDefaultPriority`.

### Commands

`/mcaquests situation list` (loaded definitions + open instances), `info <id>`, `debug` (open
situations for the nearest villager's village), and `validate` (op 3) report problems from the last load.

---

## FTB Quests integration (optional)

*(1.0.0; requires the optional [FTB Quests](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge) mod — see [FTBQUESTS.md](FTBQUESTS.md) for the full guide, including the ten FTB-side task types and three FTB-side reward types you can use inside an FTB Quests book.)*

Three conditions, one objective, and one reward let your **datapack** quests read and write **FTB Quests book** progress. They're registered whether or not FTB Quests is installed — a datapack using them loads and validates identically either way, they just always fall back to their documented default when FTB Quests can't be consulted.

### Conditions

| `type` | Fields |
|---|---|
| `mcaquests:ftbq_quest_completed` | `quest` (FTB hex id), `when_missing` (`not_met`/`met`, default `not_met`) |
| `mcaquests:ftbq_chapter_completed` | `chapter` (FTB hex id), `when_missing` |
| `mcaquests:ftbq_task_completed` | `task` (FTB hex id), `when_missing` |

The id is FTB Quests' own 16-hex-digit code string (regex `#?[0-9a-fA-F]{1,16}`, leading `#` tolerated) — copy it straight out of the FTB editor. `when_missing` is the result used whenever the real answer can't be checked (FTB Quests absent, the integration disabled in config, the id not resolving to anything in the loaded book, or any internal failure) — `not_met` for "bonus content gated behind the book", `met` (usually paired with `not`) for "catch-up content hidden once the book is done".

```json
{ "type": "mcaquests:ftbq_quest_completed", "quest": "1A2B3C4D5E6F7081", "when_missing": "not_met" }
```

### Objective — `mcaquests:ftbq_complete_quest`

| Field | Default | Meaning |
|---|---|---|
| `quest` | (required) | FTB hex id. |
| `already_complete` | `satisfy` | `satisfy` — an FTB quest already done before accept satisfies this objective on the first check. `block_offer` — additionally hides the offer once the linked FTB quest is done (desugars into an implicit `not(ftbq_quest_completed)` condition). |
| `display_name` | *(none)* | Optional `QuestText` naming the FTB quest in the objective line; falls back to a generic line naming the raw hex id. |

If FTB Quests isn't installed, any quest using this objective is skipped at load (lenient mode, logged) rather than being offered unsatisfiable; strict mode treats it as a load error.

### Reward — `mcaquests:ftbq_progress`

| Field | Values |
|---|---|
| `action` | `complete_task`, `complete_quest`, `reset_task` |
| `id` | FTB hex id of the target task/quest |

Gated by the `allowFtbqProgressRewards` config option (on by default) — when disabled, the reward's description still renders on the card, but claiming it silently does nothing.

### Example

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

`/mcaquests ftbq validate` flags an `id` that doesn't currently resolve as a **warning** (not an error) in this direction too — datapacks legitimately reference FTB book content that hasn't been built yet. See [FTBQUESTS.md](FTBQUESTS.md#commands) for the full command reference.

---

## Townstead integration (optional)

*(1.4.0; requires the optional [Townstead](https://www.curseforge.com/minecraft/mc-mods/townstead) mod,
version range `[0.7.5,0.8)` and verified against **0.7.6** — see [TOWNSTEAD.md](TOWNSTEAD.md) for the
full guide, including the bundled quests, projects and situations, the capability model, and what
happens to a save when Townstead is removed.)*

Townstead gives MCA villagers **needs** (hunger, thirst, energy), a **shift schedule**, **profession
tiers**, **learned skills** and **ancestry**, and gives villages a **spirit** — a character built from
what they have built and who lives there. This integration turns all of that into things a quest can
read, wait for, and change: five conditions, six objectives, four project objectives, four rewards, five
situation triggers, and a delivery destination.

**They are registered whether or not Townstead is installed**, so a datapack using them parses and
validates identically either way. What changes is the answer: with Townstead absent every
`townstead_*` condition is **not met** (so the content is never offered), every `townstead_*` reward
no-ops, and an already-accepted quest **suspends** — it keeps its progress and its frozen baselines,
stays abandonable, stops counting down towards its deadline, and resumes exactly where it was if
Townstead comes back. Nothing fails and nothing is lost.

**Open every Townstead quest with `townstead_available`.** It is the gate that makes all of the above
true, and without it your content will be offered to players who cannot complete it.

```json
{ "type": "mcaquests:townstead_available", "capabilities": ["READ_VILLAGER", "READ_NEEDS"] }
```

Capability names are case-insensitive; one that is not a real capability **fails the reload** rather
than quietly gating on nothing. `/mcaquests compat townstead status` lists what bound on this server.

### The query

Four of the types below share one query language — a `source`, a dot `path` into it, an `operator`, and
a `value`:

| Field | Default | Meaning |
|---|---|---|
| `source` | (required) | `villager`, `calendar`, `building`, `spirit`, `root`, `gene` |
| `target` | `giver` | `giver`, `bound`, `related`, `nearest`, `village_any` |
| `path` | (required) | Dot path into the source; max 128 characters and 8 segments |
| `operator` | (required) | `eq`, `ne`, `lt`, `lte`, `gt`, `gte`, `contains`, `in`, `matches`, `exists` |
| `value` | (required except `exists`) | A JSON primitive, or an array for `in` |
| `missing` | `false` | The answer when the source, target, path or capability cannot be read |

Numbers compare by value; strings compare case-insensitively; `matches` is a full-match regex compiled
at load, so a broken expression fails the reload rather than a quest. **`missing` defaults to `false`**
on purpose: an unreadable hunger value must make content *ineligible*, not read as "starving" and start
handing out famine quests.

Useful paths: `needs.hunger` (0–100), `needs.thirst` / `needs.quenched` / `needs.energy` (0–20),
`needs.fatigue` (0–20, **lower is more rested**), `needs.collapsed`, `schedule.currentActivity`
(`work`/`meet`/`rest`/`idle`), `schedule.onSchedule`, `professionId`, `professionLevel`,
`professionXp`, `lifeStage`, `senior`, `heritage.<root id>`.
`/mcaquests compat townstead snapshot` prints a nearby villager using these exact paths, so its output
pastes straight into a condition.

### Conditions

| `type` | Fields |
|---|---|
| `mcaquests:townstead_available` | `capability` **or** `capabilities` (list) |
| `mcaquests:townstead_value` | the query fields above |
| `mcaquests:townstead_building` | `building_type` (required), `minimum_level`, `count`, `minimum_size` |
| `mcaquests:townstead_spirit` | `spirit`, `minimum_points`, `minimum_tier`, `classification`, `primary`, `minimum_share` |
| `mcaquests:townstead_skill` | `target`, `skill` (required), `has` (bool, default `true`) |

`building_type` matches a **family**, so `dock` covers `dock_l1` through `dock_l3` and `minimum_level`
picks the tier. On `townstead_spirit`, points and share are per-spirit when `spirit` names one and
village-wide when it does not.

### Objectives

| `type` | Fields | Meaning |
|---|---|---|
| `mcaquests:townstead_state` | query fields, `hold_ticks` (def `0`), `reset_on_false` (bool, def `true`) | Hold a value true for a stretch — "keep them rested until morning". |
| `mcaquests:townstead_change` | query fields, `direction` (required, `increase`/`decrease`), `amount` (required), `minimum_final`, `maximum_final`, `baseline_on_accept` (bool, def `true`) | Move a value from where it started. |
| `mcaquests:townstead_profession_progress` | `target`, `profession` (optional), `xp_delta` **or** `target_xp` **or** `target_tier`, `require_current_profession` (bool, def `true`) | Advance a trade. Leave `profession` out to mean *whatever they practise*, frozen at accept. |
| `mcaquests:townstead_building_registered` | `building_type` (required), `minimum_level`, `count`, `minimum_size`, `require_new_or_upgraded` (bool, def `true`) | Get something built. |
| `mcaquests:townstead_spirit_progress` | `spirit` (optional), `points_delta` **or** `target_tier` | Grow a village's character. |
| `mcaquests:townstead_healthy_residents` | `minimum_observed`, `minimum_fraction`, `hunger_min`, `energy_min`, `require_not_collapsed`, `hold_ticks` | Keep a whole village well. |

**Baselines are frozen once, when the quest is accepted**, and stored with the quest. That is what makes
"raise their hunger by 40" mean *forty from where they were when you took the job* rather than forty
from wherever they happen to be at the moment of the check — and it is why a quest survives a Townstead
absence without silently re-basing itself on resume.

### Rewards

| `type` | Fields | Notes |
|---|---|---|
| `mcaquests:townstead_needs` | `target`, `need` (required), `mode` (`delta`/`target`, def `delta`), `amount` (required) | Always clamped to that need's own range, and they differ (hunger `100`, thirst/quenched/energy `20`). Gated by `needRewardsEnabled`. |
| `mcaquests:townstead_profession_xp` | `target`, `profession` (required), `amount` (required), `respect_daily_cap` (bool, def `true`) | Bypassing the cap needs **both** `respect_daily_cap: false` here **and** `allowUncappedProfessionXp` in config — a datapack alone should not undo a server's progression pacing. Gated by `professionXpRewardsEnabled`. |
| `mcaquests:townstead_skill` | `target`, `skill` (required), `forget` (bool), `force` (bool) | Teaching an already-known skill is a **success that changes nothing**, not a failure. `force` skips prerequisites and also needs `allowUncappedProfessionXp`. Gated by `skillRewardsEnabled`. |
| `mcaquests:townstead_reaction` | `target`, `task` (required), `phase` | Purely cosmetic. Quest, project and situation lifecycle reactions already play automatically; this is an *extra* flourish. Gated by `reactionsEnabled`. |

By default a Townstead reward that cannot be applied is **skipped and the quest still completes** — the
player has already done the work, and trapping them with a finished quest they can never hand in is
worse. Set `rewardFailureBlocksCompletion` to reverse that.

### Delivering goods that arrive

`mcaquests:item_delivery` takes an optional `destination`:

```json
{ "type": "mcaquests:item_delivery", "item": "minecraft:bread", "count": 8,
  "destination": { "type": "townstead_villager_inventory", "target": "giver" } }
```

The bread goes **into that villager's inventory**, where Townstead lets them actually eat it, instead of
being destroyed on hand-over. The transfer is all-or-nothing: if it will not fit, the turn-in is refused
with a message rather than half-completing. `target` accepts the same values as a query target.

*(`townstead_village_storage` is **not** implemented — Townstead exposes no registered storage API that
can be written to safely — and the parse error names it explicitly so nobody has to find out the hard
way.)*

### Example

```json
{
  "id": "mypack:a_proper_supper",
  "difficulty": "easy",
  "giver": { "professions": ["minecraft:farmer"] },
  "dialogue": { "offer": {"text": "I have not eaten since the fields flooded."}, "...": "..." },
  "conditions": { "all_of": [
      { "type": "mcaquests:townstead_available", "capabilities": ["READ_NEEDS", "MUTATE_NEEDS"] },
      { "type": "mcaquests:townstead_value", "source": "villager", "target": "giver",
        "path": "needs.hunger", "operator": "lte", "value": 30 } ] },
  "objectives": [
      { "type": "mcaquests:item_delivery", "item": "minecraft:bread", "count": 6,
        "destination": { "type": "townstead_villager_inventory", "target": "giver" } },
      { "type": "mcaquests:townstead_change", "source": "villager", "target": "giver",
        "path": "needs.hunger", "direction": "increase", "amount": 30, "minimum_final": 60 } ],
  "rewards": [ { "type": "mcaquests:hearts", "amount": 10 },
               { "type": "mcaquests:townstead_reaction", "target": "giver", "task": "eat" } ]
}
```

Both halves of that are real: the bread physically arrives, and the objective waits for Townstead's own
simulation to register that they ate it. Neither is a stand-in for the other.

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

## Two invariants

Everything the loader refuses, and much of what the runtime withholds, comes back to these two. They are
worth knowing before writing content, because content that breaks one of them tends to fail in the worst
possible way: silently, in front of a player, with nothing anywhere saying why.

> **No inert surface.** Every config key, datapack field, dialogue state and UI button either changes
> observable state or does not exist. A field that is parsed and ignored is a bug with a documentation
> page — and there were fourteen of them, including a Decline button that did nothing at all.

> **Nothing is offered that names something unresolvable.** Any content that references a villager, a
> structure, a biome, a location or a village must prove that reference resolves *before* it is offered,
> using the same predicate that will later resolve it for real. A quest gated on one question and
> targeted on another is how a player was asked to deliver a letter to a brother who had died.

---

## Localization

*(1.1.0)*

### Making your pack translatable

Everything a player reads — quest titles, dialogue lines, chain arc/chapter names, project phase text, situation offers — accepts either an inline literal or a translation key:

```json
"title":    { "text": "Sweet Tooth" },                              // not translatable
"title":    { "translate": "mypack.quest.sweet_tooth.title" }       // translatable
```

Inline `text` is **permanently supported** — small or personal packs need not bother with keys. But only `translate` can be overridden by a lang file, so a pack meant for others should use keys and ship `assets/<yournamespace>/lang/en_us.json` alongside its data.

If your quest is a template, pass `{token}` placeholders as ordered `with` arguments rather than embedding them in the key — see [Placeholders in translated text](#placeholders-in-translated-text).

### Key naming used by the built-in pack

Follow this scheme and your keys will read the same way as the shipped ones:

| Content | Key |
|---|---|
| Quest title | `mcaquests.quest.<quest id path>.title` |
| Quest dialogue | `mcaquests.quest.<quest id path>.dialogue.<state>` |
| Chain arc / chapter | `mcaquests.quest.<quest id path>.chain.arc` / `.chapter` |
| Project title | `mcaquests.project.<project id path>.title` |
| Project phase line | `mcaquests.project.<project id path>.<phase key>.<state>` |
| Situation offer | `mcaquests.situation.<situation id path>.offer.title` / `.offer.dialogue.<state>` |

### Shipped locales

| Locale | Coverage |
|---|---|
| `en_us` | Source of truth — 2,891 keys |
| `pt_br` (Português do Brasil) | Complete — all 2,891 keys |

`LocaleParityTest` enforces the rules that keep this honest: every locale must cover all of `en_us` and define nothing `en_us` lacks, placeholders must agree between a translation and its source, no value may be blank or left as a `TODO` marker, no value may mix in a non-Latin writing system, and no built-in data file may go back to hard-coding English via `text`.

### Contributing a translation

1. Copy `assets/mcaquests/lang/en_us.json`.
2. Translate the values; leave every key exactly as it is.
3. Keep the placeholders. `%s` counts must match the English, and you may reorder arguments with `%1$s` / `%2$s` where your language needs a different word order.
4. Run `./gradlew test` — the parity checks will point at anything that drifted.

---

## Extending with code

Add-on mods can register custom objective / reward / condition types under their own namespace via `dev.otectus.mcaquests.api.McaQuestsApi` (during mod setup), and react to quests through the Forge-bus events in `dev.otectus.mcaquests.api.event` (`QuestAccepted/Ready/Completed/Abandoned/Failed`).

### MCA: Conversations hooks

Two optional hooks let a conversation add-on such as **MCA: Conversations** drive quest text and progress. Both are code-facing (not datapack fields), and both are inert unless the add-on registers itself, so the base mod behaves identically without it:

- **Voiced dialogue** — register a `dev.otectus.mcaquests.api.QuestDialogueResolver` via `QuestDialogueHooks.setResolver(...)` to render a quest's lifecycle line (offer / in-progress / ready / complete / failed) in the villager's own voice instead of the static `dialogue` text. It runs server-side when the quest card is built and **falls back to the datapack text** whenever the resolver is absent, returns `null`, or throws.
- **Conversation-driven objectives** — an objective that implements `dev.otectus.mcaquests.api.ExternalSignalObjective` advances when the add-on calls `QuestManager.notifyExternalObjective(player, signalId, villagerUuid)` — e.g. signalling that the player discussed a given topic with a villager — rather than relying on a built-in detector.
