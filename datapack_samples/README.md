# MCA: Quests — sample datapacks

Ten complete, installable datapacks for **MCA: Quests** (Minecraft 1.21.1 / NeoForge). Between them they
use every datapack-facing capability the mod has: quests, quest chains, templates, deadlines and
failure branches, village projects, situations, voice pools, reputation ladders, titles, incidents,
optional-mod integrations — and one pack that exists purely to switch bundled content **off**.

Every pack is written for a subject the bundled quest pack does not already cover (beekeeping, a mason's
kiln, a drove road, a bathhouse, a merchants' ledger), so you can install several at once without them
reading as duplicates of the mod's own content.

Each folder has its own `README.md` with the reasoning behind every field it uses. Start with **01**.

## The ten packs

| # | Pack | Subject | Datapack capabilities it demonstrates |
|---|---|---|---|
| 01 | `01_starter_beekeepers_bargain` | Bees, honey, beeswax | The core quest format end to end: giver, all nine dialogue states, objectives, item/xp/effect/loot/currency rewards, turn-in modes, repeat rules, condition composites, offer groups |
| 02 | `02_market_day_templates` | Market-day orders | Quest **templates** (item/block/entity/biome/dimension/int/text pools), scaling by level and hearts, `translate` keys with `with` arguments, a custom item tag, a `lang` file |
| 03 | `03_winter_kiln_chain` | A mason's kiln | **Chains**: stages, prerequisites, unlocks, a branch after failure, converging finales; **failure**: deadlines, weather, retry and block-retry; `priority` and `weight_bonus` |
| 04 | `04_kinbound_family` | Somebody's relatives | MCA-aware conditions, **villager targets** and their `require` gates, `find_missing_relative`, escorts, protect/defend, spouse and teen content, a title |
| 05 | `05_drovers_circuit` | Moving stock between villages | **Location anchors**, `min_journey`, every kind of `source` map hint, breed/tame/sleep/structure/biome objectives |
| 06 | `06_civic_works_projects` | Bathhouse, aqueduct, orphan's roof | **Village projects**: all five scopes, sponsors and their death behaviour, phases and unlock gates, shared rewards and their four targets, `follow_up` and `unlock`, shared deadlines |
| 07 | `07_ill_omens_situations` | Full moons, fever, a wake | **Situations**: six triggers, three outcomes, `situation_focus` targets, a templated offer; plus shared **voice pools** |
| 08 | `08_ledger_of_standing` | A merchants' guild | Village **reputation**, custom tier **ladders**, **titles**, the quest-level `reputation` block, and the optional **MCA: Reputation** conditions and rewards |
| 09 | `09_trim_and_retune` | *The mod's own content* | **Disabling and overriding bundled quests, projects, situations, ladders, titles and voice pools** by shadowing their resource paths |
| 10 | `10_crossroads_compat` | Docks, dragons, contracts, a court | Optional-mod gating for **Townstead**, **Ice & Fire**, **Bountiful**, **FTB Quests** and **MCA Capitals**, plus an authored alternative for servers with none of them |

## Installing

Copy any pack folder — the one containing `pack.mcmeta` — into:

- **single player**: `saves/<world>/datapacks/`
- **dedicated server**: `<server>/world/datapacks/`

then, in game:

```
/reload
/mcaquests list
/mcaquests validate
```

They are independent; install one, several, or all ten. The only one that changes existing behaviour is
**09**, which is designed to.

Two packs have a second half that is *not* a datapack:

- **02** ships `assets/marketday/lang/en_us.json`. A folder in `datapacks/` only loads `data/`, so for its
  `translate` keys to render as English the same folder must also be enabled as a **resource pack**
  (copy it into `resourcepacks/` as well). Its README explains the split.
- **10** ships a `tags/` folder, which is an ordinary vanilla datapack tag and needs nothing special.

The nine data-only packs declare `"pack_format": 48`, the 1.21.1 **data** pack format — the same
number this mod's own bundled compat packs use. Pack 02 additionally declares
`"supported_formats": [34, 48]` because it doubles as a resource pack, and 34 is the 1.21.1
*resource* pack format.

## Reading order

**If you have never written one of these:** 01 → 03 → 02.
**If you are building a story:** 03 (chains and failure) → 04 (family) → 07 (situations).
**If you are running a server and want to change what ships:** 09, then 08 for progression tuning.
**If you are shipping to a modpack:** 10 first, then 02 for the localization rules.

## Where things live

```
data/<namespace>/mcaquests/quests/**/*.json            one quest per file
data/<namespace>/mcaquests/projects/**/*.json          shared, multi-player community goals
data/<namespace>/mcaquests/situations/**/*.json        transient, village-shared events
data/<namespace>/mcaquests/dialogue/<state>.json       shared villager voice pools
data/<namespace>/mcaquests/reputation_tiers/<id>.json  named reputation ladders
data/<namespace>/mcaquests/titles/<id>.json            title display names
```

**Use your own namespace** for everything except a deliberate override. The file path is cosmetic — a
definition's identity is its `id` field — but a file in the `mcaquests` namespace at a bundled path
*replaces* that bundled file, which is sample 09's entire subject and nobody else's intent.

## Commands worth knowing

```
/mcaquests list                         # what loaded
/mcaquests validate                     # errors and warnings from the last load
/mcaquests reload                       # same as /reload for this mod's data
/mcaquests export-schema                # writes a complete valid example quest to config/mcaquests/
/mcaquests debug villager               # every quest the nearest villager could give, and why
/mcaquests debug quest <id>             # the full gate checklist for one quest
/mcaquests debug guidance               # what each active quest would point a marker at
/mcaquests project list|info|debug|validate
/mcaquests situation list|info|debug|validate
/mcaquests compat status                # which optional-mod providers actually bound
/mcaquests reputation get|set|add|tiers
/mcaquests title grant|list|clear
```

A malformed file is **skipped with a logged error and named by `validate`**, and the rest of the pack
still loads — unless the server sets `strictJsonValidation = true`, which turns every one of those
reports into a refused reload. Turn it on while developing; leave it off in production.

## Two invariants behind almost every rule

> **No inert surface.** Every config key, datapack field, dialogue state and UI button either changes
> observable state or does not exist. A field that is parsed and ignored is a bug with a documentation
> page.

> **Nothing is offered that names something unresolvable.** Any content referencing a villager, a
> structure, a biome, a location or a village must prove that reference resolves *before* it is offered,
> using the same predicate that will later resolve it for real. A quest gated on one question and
> targeted on another is how a player was once asked to deliver a letter to a brother who had died.

Most of what the loader refuses — and most of the "why is my quest never offered?" answers — comes back
to those two.

## Verified

Every JSON file in these ten packs was parsed through the mod's own codecs and run through its
reload-time validators (`QuestChainValidator`, `TemplateValidator`, `FailureValidator`,
`ObjectiveValidator`, `TargetGateValidator`, `AgeEligibilityValidator`, `ProjectValidator`) with no
errors and no warnings other than the two that are true of any environment without the optional mods
installed:

- the FTB Quests quest in pack 10 is skipped at load when FTB Quests is absent (documented behaviour of
  `ftbq_complete_quest`, explained in that pack's README);
- projects with MCA-dependent scopes report "MCA is not loaded" when MCA Reborn is not present.

Reference documentation: [`DATAPACK.md`](../DATAPACK.md), [`CONFIG.md`](../CONFIG.md),
[`TOWNSTEAD.md`](../TOWNSTEAD.md), [`CAPITALS.md`](../CAPITALS.md), [`FTBQUESTS.md`](../FTBQUESTS.md),
[`BOUNTIFUL.md`](../BOUNTIFUL.md), [`ICEANDFIRE.md`](../ICEANDFIRE.md).
