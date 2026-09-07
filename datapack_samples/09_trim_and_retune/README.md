# 09 — Trim and Retune (removing and overriding bundled content)

Every other sample in this folder **adds** content in its own namespace. This one is the opposite: it
takes the content MCA: Quests already ships and switches parts of it off, rewrites others, and re-tunes
the progression ladder — without editing the mod jar and without touching a config file.

**This is the only sample that deliberately uses the `mcaquests` namespace.** That is the whole
mechanism, and it is why every other sample tells you not to.

## How overriding works

A datapack file at the **same resource path** as a bundled file wins the merge. The mod's own content
lives at:

```
data/mcaquests/mcaquests/quests/<path>.json
data/mcaquests/mcaquests/projects/<name>.json
data/mcaquests/mcaquests/situations/<name>.json
data/mcaquests/mcaquests/reputation_tiers/<name>.json
data/mcaquests/mcaquests/titles/<name>.json
data/mcaquests/mcaquests/dialogue/<state>.json
```

Put a file at exactly that path in your datapack and yours is the one that loads. Note the doubled
`mcaquests`: the first is the **namespace**, the second is the mod's **directory** inside it.

The match is on **path**, not on `id`. The bundled paths conventionally match their quest ids
(`quests/farmer/wheat_request.json` holds `mcaquests:farmer_wheat_request`), which makes them easy to
find but is not what does the work. Keep the `id` the same anyway when you are replacing a quest —
anything else in the pack that references it by id (a chain prerequisite, a `quest_completed`
condition) is looking for the id, not the file.

## Files in this pack

| File | What it does |
|---|---|
| `quests/cleric/grim_harvest.json` | Disables `mcaquests:cleric_grim_harvest` (fetch 10 rotten flesh). |
| `quests/mercenary/contract_killing.json` | Disables `mcaquests:mercenary_contract_killing` (a generic zombie cull). |
| `quests/compat/bountiful/board_discovery.json` | Disables a quest from a **conditional compat pack**. |
| `quests/farmer/wheat_request.json` | **Replaces** the bundled wheat errand with a cheaper, rarer, longer-cooldown version. |
| `projects/festival_preparation.json` | Disables a bundled project. |
| `situations/night_watch.json` | Disables a bundled situation. |
| `reputation_tiers/default.json` | Replaces the **default ladder** with much harsher thresholds. |
| `titles/honored_of_village.json` | Renames a bundled title's display text. |
| `dialogue/cooldown.json` | Replaces the bundled "come back later" voice pool. |

## Disabling: the minimal stub

`"enabled": false` is what removes something from selection. The stub still has to **parse**, so it
needs whatever its codec marks as required — and no more:

```json
{ "id": "mcaquests:cleric_grim_harvest", "enabled": false, "giver": {}, "dialogue": {} }
```

| Type | Required in a stub |
|---|---|
| Quest | `id`, `giver` (an empty object is fine), `dialogue` (an empty object is fine) |
| Project | `id`, `scope`, and **at least one phase** — a project with no phases is a validation *error* even when disabled, so the stub carries one empty final phase |
| Situation | `id`, `trigger`, `offer` (an empty object is fine) |

What `enabled: false` actually does: the definition still **loads** — `/mcaquests debug quest
mcaquests:cleric_grim_harvest` will still find it and report `enabled: false` — but it is never offered
to anybody. A player who already holds it keeps it; the flag is read on the offer path.

**Check what references it before you disable it.** Unknown *or disabled* prerequisites, `unlocks`
targets, and condition targets are validation **errors** (and refuse the whole reload under
`strictJsonValidation`). Disabling a chain stage, or a quest another quest's `quest_completed` condition
names, breaks the pack that references it. The three quests disabled here are standalone — nothing in
the bundled pack points at them, which is exactly why they were chosen. Grep first:

```
grep -rl "mcaquests:cleric_grim_harvest" .
```

## Replacing: retune in place

`quests/farmer/wheat_request.json` keeps the id, the profession, the translation keys and the objective
type, and changes only the numbers: 24 wheat becomes 12, the currency reward is pinned to a flat 1–2
instead of the difficulty band, `weight` drops from 20 to 8, and the cooldown triples. That is what
"this quest appears too often and pays too well for my server" looks like as a diff.

**Keep the `translate` keys** when you rewrite a bundled quest. They already have English and Brazilian
Portuguese behind them in the mod's own language files; inventing new `mcaquests.*` keys means keys with
nothing behind them, which render as raw text and which `/mcaquests validate` will warn about (it checks
missing translations for the `mcaquests` namespace specifically, because those are the ones it can be
confident about). If you want new wording, either write inline `"text"` or use keys in **your own**
namespace and ship a resource pack for them (sample 02 explains that split).

## Replacing the default ladder

`mcaquests:default` is the ladder the Journal shows and the one every `reputation_tier` condition uses
when it omits `ladder`. Shipping your own `reputation_tiers/default.json` replaces it wholesale.

**Keep the tier ids; change the thresholds and names.** A `reputation_tier` condition names a tier by
**id**, and the bundled quest `relations/honored_envoy.json` gates itself on `min_tier: "friend"`. Drop
or rename that id and the condition names a tier that does not exist — `/mcaquests validate` warns, and
the quest is never offered again. This override keeps all five ids (`stranger`, `acquaintance`, `friend`,
`honored`, `revered`) and the two `grants_title` hooks, and only moves the thresholds (25/75/150/300
becomes 60/180/400/800) and two display names. Nothing else in the pack notices, and reputation takes
about three times as long to climb.

Remember the ladder's own rules: thresholds must strictly ascend and the lowest must be `<= 0`. An
invalid ladder is **skipped entirely**, which silently restores the built-in one — so always reload and
check `/mcaquests reputation tiers`.

## Titles and voice pools

A title override is a two-line file. `titles/honored_of_village.json` renames "Honored" to "Freeman of
the Village"; every quest and ladder that grants `mcaquests:honored_of_village` keeps working, because
the id has not moved.

The **voice pool** override is subtler and better: pools have a `priority`, and higher pools are
consulted first. So you can shadow the bundled voice **without deleting it** by shipping a pool at a
different path in your own namespace with a higher priority (sample 07 does that). This pack instead
replaces the bundled file at its own path — use that when you want the bundled lines *gone*, and the
priority route when you want yours preferred but theirs kept as a fallback.

## Compat-pack content

`iafce_quests`, `bountiful_core`, `bountiful_iafce` and `capitals_court` are datapacks the mod mounts
only when the mod they need is installed *and* the matching `compat.<mod>.enableBuiltinContent` config is
on. They live in the `mcaquests` namespace at ordinary paths, so an owner's datapack **wins over the
mounted compat pack at the same path** — `quests/compat/bountiful/board_discovery.json` here is the same
trick, aimed one layer deeper.

Two other levers exist for the same job, and they are config rather than datapack:
`compat.bountiful.enableBuiltinContent` turns that whole pack off, and `compat.bountiful.mode` controls
how far the integration goes at all.

## The nuclear option: `enableDefaultQuestPack`

Setting `enableDefaultQuestPack = false` in the config drops **every** bundled definition — quests,
projects, situations, ladders, titles and voice pools — leaving only what datapacks provide. If you are
shipping a total-conversion quest pack, that is the switch, and you do not need this sample's stubs at
all.

The one thing worth knowing: the filter drops a definition by asking **which pack actually won the
merge**, not by looking at the namespace. So a file you override stays yours even with the switch off —
your version was never "built-in", and turning off the bundled content does not take your override with
it. (A file whose source cannot be determined is kept, on the principle that silently dropping content
because a resource-pack implementation named itself oddly would be a much worse failure.)

## Verifying an override

```
/reload
/datapack list                          # confirm your pack is enabled and loaded after the mod
/mcaquests list                         # the retuned wheat quest, the disabled ones absent from offers
/mcaquests validate                     # anything that now references something disabled
/mcaquests reputation tiers             # the new thresholds
/mcaquests debug quest mcaquests:cleric_grim_harvest    # says enabled: false
/mcaquests debug villager               # stand next to a farmer: what they will and will not offer now
```

If an override seems not to apply, it is almost always one of three things: the path is wrong (count the
`mcaquests`es), the file failed to parse and was skipped with a logged error (`/mcaquests validate`), or
another datapack later in the load order overrides you in turn.

**Do not edit the jar.** An override is a file you can version, hand to somebody, and remove; a modified
jar is a support problem that reappears at every update, and `verifyReobfJar` exists precisely because
edited jars are a real failure mode for this project.
