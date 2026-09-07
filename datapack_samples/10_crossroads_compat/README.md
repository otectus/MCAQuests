# 10 — Crossroads (optional-mod compatibility)

Seven quests and one situation, each gated on a mod that may or may not be installed: **Townstead**,
**Ice & Fire**, **Bountiful**, **FTB Quests**, **MCA Capitals**. Nothing here is a hard dependency —
the pack loads on a plain MCA: Quests install and simply offers less.

## Files

| File | Needs | Shows |
|---|---|---|
| `quests/compat/a_hot_meal_at_the_dock.json` | Townstead | `townstead_available`, the **query language**, delivery **into a villager's inventory**, `townstead_change`, a reaction reward. |
| `quests/compat/lanterns_along_the_dock.json` | Townstead | The `townstead_building` anchor, `townstead_building_registered`, a **calendar-relative repeat**, a needs reward. |
| `quests/compat/scales_for_the_smith.json` | Ice & Fire | `compat_capability`, and the **optional tag** trick that keeps the file valid when the mod is absent. |
| `quests/compat/three_contracts.json` | Bountiful | `bountiful_bounties` with `min_rarity`, gated on two capabilities. |
| `quests/compat/the_book_and_the_village.json` | FTB Quests | An FTB condition, objective and reward together. |
| `quests/compat/a_writ_from_the_throne.json` | MCA Capitals | Four capital conditions, the `capital_role` villager target, three capital rewards. |
| `situations/the_throne_stands_empty.json` | MCA Capitals | The `capital_interregnum` situation trigger. |
| `quests/compat/when_the_roads_are_closed.json` | *nothing* | The **authored alternative** for a server that has none of this. |
| `tags/entity_types/dragons.json` | — | A vanilla tag with `"required": false` entries. |

## The rule that governs all of it

> **Nothing is offered that names something unresolvable.**

Content that needs another mod gates on that mod's availability. When the mod is absent the content is
**paused, not lost**: a player already holding the quest keeps it in their log with its progress and its
frozen baselines, it stops counting down towards its deadline, it stays abandonable, and it resumes
exactly where it was if the mod comes back. New players are simply never offered it.

## `compat_capability`

```json
{ "type": "mcaquests:compat_capability", "provider": "bountiful", "capability": "bountiful.cash_in_hook" }
```

`present: false` inverts it — that is how `when_the_roads_are_closed.json` authors a deliberate
alternative for servers *without* Townstead, so those players get a plainer version of the same errand
instead of nothing.

| `provider` | Capabilities |
|---|---|
| `mca` | `villagers` |
| `townstead` | data-driven; commonly `read_schedule`, `read_building`, `read_needs`, `read_profession`, `read_spirit`, `mutate_needs`, `dispatch_reaction` |
| `iceandfire` | `iceandfire.core`, `.fire_dragon`, `.ice_dragon`, `.lightning_dragon`, `.myrmex`, `.dread_mobs`, `.dragon_seekers`, `.netherite_dragon_armor`, `.netherite_hippogryph_armor`, `.structures`, `.brush_scales`, `.dragon_forge_blood` |
| `bountiful` | `bountiful.data_pack`, `.board_registry`, `.cash_in_hook`, `.read_rarity`, `.read_objectives` |
| `ftbquests` | `book`, `progress` |

**The string is compared byte for byte.** Some providers use bare ids (`read_needs`) and others prefix
them (`iceandfire.fire_dragon`); the wrong format never matches and is not an error, because an add-on
may legitimately register a provider this installation does not have. `/mcaquests compat status` lists
what actually bound here.

## The trap: which ids fail to load when a mod is absent

This is the single most useful thing in this sample, and it is not symmetrical:

| Field | Codec | Optional-mod id when the mod is missing |
|---|---|---|
| `item` / `tag` on `obtain_item`, `craft_item`, `fish_item`, `heal_entity`, `deliver_to_villager`, `donate_item`, and `item` on `item_delivery` | registry-backed | **The file fails to load** and is skipped with a logged error |
| `block` / `tag` on `break_block`, `place_block`, `build_near_location` | registry-backed | **The file fails to load** |
| `entity` on `kill_entity`, `defend_*`, `breed_animals`, `tame_animal` | plain resource location | Loads fine; the objective just never matches |
| `item` on `use_item`, `block` on `interact_block` | plain resource location | Loads fine; the quest is unofferable/paused |
| Any `tag` | tag key | Loads fine; an empty tag is a `validate` **warning** |

So a quest that asks you to **kill** an Ice & Fire dragon is safe to ship anywhere, and a quest that asks
you to **obtain** an Ice & Fire item is not. The mod's own Ice & Fire content sidesteps this by living in
a conditional compat pack that is only mounted when the mod is installed.

For a datapack you hand to strangers, the portable answer is a **tag with optional entries**:

```json
{ "replace": false, "values": [ { "id": "iceandfire:fire_dragon", "required": false } ] }
```

`scales_for_the_smith.json` kills `#crossroads:dragons` rather than `iceandfire:fire_dragon` for exactly
that reason: with Ice & Fire absent the tag is empty (a warning), and the `compat_capability` gate stops
the quest being offered anyway. With Ice & Fire present it names three dragons instead of one.

## Townstead

Open **every** Townstead quest with `townstead_available`. It is the gate that makes "paused, not lost"
true; without it the content is offered to players who cannot complete it.

```json
{ "type": "mcaquests:townstead_available", "capabilities": ["READ_VILLAGER", "READ_NEEDS", "MUTATE_NEEDS"] }
```

Capability names are case-insensitive, and one that is not a real capability **fails the reload** rather
than quietly gating on nothing.

Four condition/objective types share one **query language** — a `source`, a dot `path`, an `operator` and
a `value`:

```json
{ "type": "mcaquests:townstead_value", "source": "villager", "target": "giver",
  "path": "needs.hunger", "operator": "lte", "value": 30, "missing": false }
```

`source` is `villager`, `calendar`, `building`, `spirit`, `root` or `gene`; `target` is `giver` (default),
`bound`, `related`, `nearest` or `village_any`; operators are `eq`, `ne`, `lt`, `lte`, `gt`, `gte`,
`contains`, `in`, `matches`, `exists`. Useful paths: `needs.hunger` (0–100), `needs.thirst` /
`needs.quenched` / `needs.energy` (0–20), `needs.fatigue` (0–20, **lower is more rested**),
`needs.collapsed`, `schedule.currentActivity`, `schedule.onSchedule`, `professionId`, `professionLevel`,
`professionXp`, `lifeStage`, `senior`, `heritage.<root id>`.

**`missing` defaults to `false` on purpose**: an unreadable hunger value must make content *ineligible*,
not read as "starving" and start handing out famine quests. `/mcaquests compat townstead snapshot`
prints a nearby villager using these exact paths, so its output pastes straight into a condition.

Two Townstead details this pack leans on:

- **Delivery that actually arrives.** `"destination": { "type": "townstead_villager_inventory", "target":
  "giver" }` puts the bread in the villager's real inventory, where Townstead lets them eat it, instead
  of destroying it on hand-over. The transfer is all-or-nothing: if it will not fit, the turn-in is
  refused rather than half-completing. **No Townstead capability is required for the transfer itself** —
  the inventory belongs to MCA — so a delivery quest keeps working when Townstead is absent; Townstead
  only supplies the reason to care. (`townstead_village_storage` is *not* implemented and never parses.)
- **Baselines freeze on accept.** `townstead_change` means "raise their hunger by 30 *from where it was
  when you took the job*", which is why it survives a Townstead absence without silently re-basing itself
  on resume.

`lanterns_along_the_dock.json` also shows a **calendar-relative repeat**:

```json
"repeat": { "type": "period", "period": "season", "scope": "giver", "fallback_cooldown_ticks": 96000 }
```

A tick cooldown cannot express "once a season", because a season may be three days on one server and
thirty on another. A completion records a **token** naming the period it happened in, and the quest is
eligible again exactly when the live token differs. If the calendar cannot be read it falls back to
`fallback_cooldown_ticks`, which is armed at completion for precisely that case — a missing calendar
never grants a second reward.

By default a Townstead reward that cannot be applied is **skipped and the quest still completes**: the
player has already done the work, and trapping them with a finished quest they can never hand in is
worse. `rewardFailureBlocksCompletion` reverses that.

## Bountiful

`bountiful_bounties` counts bounties **cashed in** at a board, not accepted. It needs
`bountiful.cash_in_hook`; adding `min_rarity` additionally needs `bountiful.read_rarity`, because an
unreadable rarity is treated as unknown and refused rather than passed. Both are gated explicitly here
rather than left to chance. `compat.bountiful.mode` (AUTO / DATA_ONLY / OFF) is the server-side switch
that decides how far the integration goes at all.

## FTB Quests

Ids are FTB's own 16-hex-digit codes, copied out of the FTB editor (`#` prefix tolerated). **The ones in
this file are placeholders — replace them with your book's real ids.**

- `when_missing` is the answer used whenever the real answer cannot be checked (FTB absent, integration
  disabled, id not in the loaded book): `not_met` for "bonus content behind the book", `met` (usually
  paired with `not`) for "catch-up content hidden once the book is done".
- `already_complete: "block_offer"` additionally hides the offer once the linked FTB quest is done; the
  default, `satisfy`, lets prior completion satisfy the objective immediately.
- **A quest using `ftbq_complete_quest` is skipped at load when FTB Quests is absent** (logged, lenient
  mode; a load error under `strictJsonValidation`). That is by design — the objective could never be
  satisfied — and it is the one file here that does not merely go unoffered. If that matters for your
  distribution, ship the FTB-facing quests as a separate datapack.
- `/mcaquests ftbq validate` reports an id that does not currently resolve as a warning, not an error,
  because packs legitimately reference book content that has not been built yet.

## MCA Capitals

Open every Capitals quest with `capital_present`, the most basic gate — it ensures the giver has an
active capital at all. The other four conditions are `capital_role`, `capital_allegiance`,
`capital_relation` and `capital_interregnum`; `a_writ_from_the_throne.json` uses all four, including
`capital_role` with `present: false` to mean "you are not a knight *yet*", which is what makes the
knighthood reward land once and stop being offered.

`{ "mode": "capital_role", "role": "herald" }` targets an officeholder. Roles: `sovereign`, `consort`,
`dowager`, `heir`, `royal_child`, `hand`, `commander`, `herald`, `grand_maester`, `master_of_laws`,
`ambassador`, `duke`, `lord`, `knight`, `royal_guard`, `member`. `archduke` is **player-only** and is
rejected on a villager; invalid subject/role combinations fail validation at load. The holder is bound to
one concrete villager on accept and later succession never changes that UUID. If the office has no
villager holder — vacant, or held by a player — the objective is unofferable with a reason line rather
than pointing at nobody.

Three rewards: `capital_title` (`knight`/`lord`/`duke`/`archduke`, with `female_title` to override the
gendered constant), `capital_villager_title` (`knight`/`lord`/`duke`, raising a villager), and
`capital_chronicle`, which writes a line into the capital's chronicle. **Supply a `fallback` for a custom
chronicle key**: the line is rendered once on the server, and a dedicated server has no client
translations, so a key with nothing behind it prints as a key. The two `%s` arguments are the player and
capital names.

With Capitals absent, its leaf conditions are **not met — including their `present: false` forms**.
Unavailable data does not prove absence, so "there is no capital here" cannot be expressed by inverting a
capital condition; use `compat_capability` with `present: false` for that, exactly as
`when_the_roads_are_closed.json` does for Townstead.

## Server switches worth knowing

- `compat.validation.logMissingOptionalContent` (default on) — warns when a quest names content that is
  not installed. Useful while developing; turn it off on a server that deliberately ships packs for mods
  it does not have.
- `compat.iceandfire.enabled`, `compat.bountiful.mode`, `compat.capitals.enableBuiltinContent`, etc. —
  master switches per integration.
- `/mcaquests compat status` and `/mcaquests compat townstead status` say what actually bound, which is
  the first thing to check when gated content never appears.
