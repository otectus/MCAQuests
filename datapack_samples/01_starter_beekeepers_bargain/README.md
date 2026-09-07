# 01 — Beekeeper's Bargain (starter pack)

Four hand-written quests about bees, honey and beeswax — a subject the bundled pack never touches.
This is the pack to read first: everything in it is the *core* quest format, with no optional mods,
no templates, no chains and no shared state.

## Install

Copy the whole folder (the one containing `pack.mcmeta`) into:

- single player: `saves/<world>/datapacks/`
- server: `<server>/world/datapacks/`

Then in game:

```
/reload
/mcaquests list
/mcaquests validate
```

`list` should show four `beekeeper:` quests. `validate` should say nothing about them. If a file is
wrong, `validate` names the file and the reason and **the other quests still load** — a bad file never
takes the pack down with it (unless the server sets `strictJsonValidation = true`, which turns every
one of those reports into a refused reload).

## Files

| File | What it is there to show |
|---|---|
| `honey_for_the_hearth.json` | The complete shape of a quest: every dialogue state, a delivery objective, three rewards, an offer gate. |
| `a_place_for_the_swarm.json` | Two objectives in one quest, a **location anchor**, a composite condition, `same_profession` turn-in. |
| `smoke_and_patience.json` | A one-time tutorial quest that hands itself in (`self_complete`), with three different "do a thing" objectives. |
| `candles_for_the_chapel.json` | A repeatable supply run that stops being offered once you are close to the giver, plus a loot-table reward. |

## Notes on what each field is doing

**`id` is identity, the path is decoration.** `data/beekeeper/mcaquests/quests/hive/honey_for_the_hearth.json`
could sit in any subfolder; what makes the quest unique is `"id": "beekeeper:honey_for_the_hearth"`.
Use your own namespace (`beekeeper` here). Anything you put in the `mcaquests` namespace **overrides a
bundled file** at the same path — that is sample 09's whole subject, and it is not what you want by accident.

**Dialogue.** Nine states exist; `honey_for_the_hearth` writes all of them so you can see the list in one
place. The last three (`failed`, `cooldown`, `locked`) are the villager explaining why they have nothing
for you; a quest that omits them falls back to the shared voice pools (sample 07 builds some). Everything
here is inline `"text"`, which is supported forever but cannot be translated — sample 02 shows the
`"translate"` form.

**`{player}`** in the first quest's offer line is a reserved token available in *any* quest's dialogue,
title and chain text. It renders the player's MCA character name, falling back to their username. It is
dialogue-only: it is never substituted into objective or reward JSON.

**Objective targets take an id or a tag.** `"item": "minecraft:honey_bottle"` and
`"tag": "minecraft:candles"` are both valid; so are `block`/`tag`, `entity`/`tag`, `biome`/`tag`.
A tag that resolves to nothing is a `validate` **warning**, because the objective can then never advance.

**`source` is the map marker, not the objective.** `honey_for_the_hearth` carries
`"source": { "block_tag": "minecraft:beehives" }`, which points the tracker and world marker at the
nearest hive within 48 blocks *in loaded chunks*. Nothing is inferred: an objective with no `source`
draws no marker and its text carries the whole instruction. `item_delivery` is the one exception — with
no `source` it points at the villager the goods are for.

**Objective order is load-bearing.** Progress is stored positionally. Append new objectives to the end of
a quest players may already be holding; inserting one in the middle shifts their progress onto the wrong
row.

**Rewards.** `mcaquests:currency` asks for *money* without naming an item — the server decides what money
is (emeralds by default), so the pack works on an economy modpack unchanged. With no `min`/`max` it reads
the range from the quest's `difficulty` band; `candles_for_the_chapel` names an explicit range instead.
The amount is rolled **once, on accept**, and frozen, so reopening the menu cannot reroll a payout.
`mcaquests:hearts` is clamped and scaled by server config; `mcaquests:item` never is.

**`difficulty`** is optional metadata (`easy` / `medium` / `hard`). Its only mechanical effect today is
supplying the default currency band, so a server can retune payouts without editing your files.

**`offer_group`** stops a villager filling all three of its offer slots with variations of the same thing:
during a reroll the menu takes at most one quest per group before allowing seconds. The two hive quests
share `hive_work` deliberately.

**`repeat`** is `cooldown` (default, 24000 ticks = one Minecraft day), `once`, `repeatable`, or `period`
(a Townstead calendar season — sample 10). `once` means once ever.

**`turn_in`** decides who may take the finished work: `original_giver` (default), `same_profession`,
`specified_profession`, `any_villager`, or `self_complete` (no hand-in at all — the quest completes the
moment its objectives are met, which is why `smoke_and_patience` reads as a tutorial rather than an errand).

**Conditions gate the *offer*, not the quest.** They are evaluated when the villager's menu is opened. A
quest whose conditions stop matching after you accepted it does not fail; that is what the `failure` block
is for (sample 03). Leaves compose with `all_of`, `any_of` and `not` to any depth.

**`min_hearts` / `max_hearts` on the giver** are the MCA hearts range the *player* must be in with that
villager. `candles_for_the_chapel` uses `max_hearts: 80` so it fades out as you become close friends —
an upper bound is how you retire early-game content without deleting it.

## Try changing something

1. Set `"weight": 200` on `smoke_and_patience` and reload — it will crowd out the others.
2. Set `"enabled": false` on it and reload — it disappears from `/mcaquests list` entirely.
3. Break a field on purpose (`"count": "six"`) and run `/mcaquests validate` to see exactly how the loader
   reports it, and that the other three quests still work.
4. `/mcaquests debug villager` while standing next to a farmer explains, quest by quest, why each one is
   offered, locked, hidden or on cooldown.
