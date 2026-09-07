# 02 — Market Day (templates + localization)

Four **template** quests: one file each, but every villager offers a different concrete version of it.
This is how you get a hundred errands out of four files without writing a hundred files — and it is the
sample to copy if your pack is meant to be translated.

## What a template is

A normal quest hard-codes its objectives. A template declares **variable pools**, then writes the
objectives and rewards in terms of `{placeholders}`:

```json
"template": {
  "variables": { "cut": { "kind": "item", "tags": ["marketday:market_cuts"] }, ... },
  "objectives": [ { "type": "mcaquests:item_delivery", "item": "{cut}", "count": "{count}" } ],
  "rewards":    [ { "type": "mcaquests:xp", "amount": "{count}" } ]
}
```

A template quest has **no top-level `objectives`/`rewards`** — they move inside the `template` block.
Everything else (giver, dialogue, repeat, conditions, difficulty, offer groups) works exactly as in a
hand-written quest.

## Files

| File | Variable kinds shown |
|---|---|
| `butcher_order.json` | `item` from a **custom tag**, two `int` pools scaled by player level and by hearts |
| `mason_haul.json` | `block` from an id list, `int` with `per_player_level`, an objective `source` |
| `ranger_cull.json` | `entity` from ids **and** a tag shipped by the mod itself (`mcaquests:common_undead`) |
| `wanderer_route.json` | `biome` (ids + tag), `dimension`, and a `text` pool used for flavour only |
| `data/marketday/tags/items/market_cuts.json` | An ordinary vanilla item tag, defined by this pack and used as a pool |
| `assets/marketday/lang/en_us.json` | The translations behind every `translate` key here |

## How resolution works

- Values are picked **at offer time, server-side**, and are **deterministic per villager per day**. The
  same butcher offers the same order all day; what you see on the card is what you accept.
- On accept the values are **frozen onto that copy** of the quest and persisted. They never reroll —
  through logout, death, dimension change, villager unload or a server restart.
- The client only renders an already-resolved card. Nothing about the roll is client-side.

## Placeholder rules worth memorising

1. **In objective/reward JSON**, a string that is *exactly* `"{var}"` is replaced by the value: an id
   becomes `"minecraft:cooked_beef"`, an `int` becomes the JSON number `12`. That is why
   `"count": "{count}"` is written as a string and still parses as a number afterwards.
2. **In dialogue and titles**, `{var}` inserts the value and `{var_name}` inserts the *translated display
   name* — `{cut_name}` renders "Cooked Beef", not `minecraft:cooked_beef`. Never show a raw id to a player.
3. **`text` pools are flavour only.** `{rumour}` in `wanderer_route.json` can go in a sentence and never
   into an objective.
4. **`{player}`** is reserved (the player's MCA character name). A variable named `player` is a validation
   error — rename it.
5. **Literal braces** are `{{` and `}}`.

## Localization: why the keys, and where the lang file goes

Player-facing text is either `{"text": "..."}` (inline, never translatable) or
`{"translate": "key"}` (translatable). Both are supported permanently; a pack meant for other people
should use keys.

A template's `{token}` cannot live inside a translation key — a translator cannot be expected to preserve
it — so tokens are passed as **ordered arguments**:

```json
{ "translate": "marketday.quest.butcher_order.offer", "with": ["{count}", "{cut_name}", "{player}"] }
```

and the English string uses **positional** specifiers:

```json
"marketday.quest.butcher_order.offer": "Bring me %1$s of the %2$s, %3$s, and I will pay properly."
```

Use `%1$s`, not bare `%s`: many languages need to reorder the arguments, and only the numbered form lets
them.

> **The `assets/` half is a resource pack.** A folder in `datapacks/` only loads `data/`. For the
> `marketday.*` keys to render as English rather than as raw keys, the same folder must *also* be loaded
> as a resource pack (drop a copy in `resourcepacks/` and enable it, or ship the whole thing inside a mod
> jar, or use a mod that serves both halves). This is a Minecraft rule, not an MCA: Quests one. If you do
> not want to deal with it, use inline `"text"` like sample 01 — nothing is lost except translatability.
>
> **That is also why this pack's `pack.mcmeta` declares two formats.** On 1.21.1 the *data* pack
> format is `48` and the *resource* pack format is `34`; a folder that has to be accepted as both
> declares `"pack_format": 48` with `"supported_formats": [34, 48]`. The other nine samples are
> data-only and just say `48`.
>
> `/mcaquests validate` only warns about missing translations for `mcaquests.*` keys — your own namespace
> is assumed to belong to a language file the server may legitimately not have loaded.

## `int` pools scale with the player

```json
"count": { "kind": "int", "min": 6, "max": 14, "per_player_level": 0.4, "limit": 32 }
```

Base value uniform in `[min, max]`, plus `per_player_level × level`, plus `per_heart × hearts with the
giver`, clamped to `limit`. That is how one file stays reasonable for a level-3 player and a level-60 one.
`per_heart` on the `pay` pool in `butcher_order.json` is the other half of the same idea: the better the
butcher knows you, the better the price.

## Validation

`/mcaquests validate` reports, per quest and field: registry ids that do not exist, tags that are empty or
unknown, `{placeholders}` naming no declared variable, a variable named `player`, and substituted
objective/reward JSON that fails to parse. An empty pool also fails safe at runtime — the offer is skipped
rather than crashing.

Two failure modes that are *not* errors and are worth knowing:

- A **biome or dimension id this world does not have** cannot be caught at load (those registries are
  world-driven). `validate` catches it against a running world; otherwise the offer is simply skipped.
- A **tag that resolves to nothing** parses fine and can never be picked. `mcaquests:common_undead` is
  shipped by the mod, so it always resolves; `marketday:market_cuts` resolves because this pack defines it.
