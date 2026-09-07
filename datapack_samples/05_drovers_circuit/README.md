# 05 — The Drover's Circuit (anchors, journeys and map guidance)

Six quests about getting somewhere. This is the sample for **location anchors**, the **`source`** hint
that drives the world marker and minimap waypoint, and the objectives that are about places rather than
about items.

## Files

| File | Shows |
|---|---|
| `between_the_two_villages.json` | `reach_location` with `nearest_other_village` and `min_journey`, plus an elapsed-time deadline. |
| `pasture_and_pen.json` | `breed_animals`, `tame_animal`, `build_near_location`; the `home_village`, `workstation` and `giver_pos` anchors. |
| `a_bed_beneath_the_stars.json` | `visit_biome`, `enter_structure`, `sleep_or_rest`. |
| `hold_the_camp.json` | `defend_location` — the place-anchored sibling of `defend_villager`. |
| `things_worth_carrying.json` | Every `source` kind in one (deliberately silly) list. |
| `walk_with_me_to_market.json` | A **follow**-style escort to another village, plus `trade_with_villager`. |
| `a_lantern_on_every_mile.json` | A frozen `nearest_other_village` anchor shared by an objective that has to stay put. |

## Location anchors

Fields named `destination`, `location` and `near` all take the same object:

```json
{ "anchor": "home_village" }                            // the giver's MCA village
{ "anchor": "nearest_village", "radius": 128 }          // nearest village to the giver
{ "anchor": "nearest_other_village", "radius": 2048 }   // the next one along, never the giver's own
{ "anchor": "giver_pos" }
{ "anchor": "bed", "villager": { "mode": "self" } }
{ "anchor": "workstation", "villager": { "mode": "self" } }
{ "anchor": "villager", "villager": { "mode": "family", "relation": "spouse" } }
{ "anchor": "coords", "pos": [100, 64, -200] }
{ "anchor": "townstead_building", "building_type": "dock", "minimum_level": 2 }
```

`nearest_village` falls back to the nearest **vanilla** village (`#minecraft:village`) when MCA knows of
none, so a fresh world is not a dead end.

**Two anchors are frozen; the rest resolve live.** A bed that moves with its owner should keep being the
right destination, so `bed` is re-resolved every poll. But `townstead_building` and
`nearest_other_village` are *choices* among several valid answers, and a choice re-made every second is
not a destination — the marker would jump the moment a closer dock was built. Both are decided once, when
the quest is accepted, and kept. `"selection"` picks how (`nearest_to_giver`, the default, or
`nearest_to_player_at_accept` as in `walk_with_me_to_market.json`); ties break on the lower registered id
so the same world always chooses the same one.

That freezing is also what makes two objectives agree with each other. "Twelve lanterns at the far
village" and "kill six skeletons at the far village" in one quest are one instruction about one place;
they share the binding and cannot drift apart.

**Arrival is border-aware.** For `home_village`/`nearest_village` the objective completes anywhere
**inside the village border**; every other anchor uses a horizontal (Y-ignored) distance within `radius`.
That is why a village arrival does not need a generous `radius` and a `coords` one does.

**`coords` is world-specific.** It is the right answer for an adventure map built on a known seed and the
wrong one for a pack you hand to strangers, which is why nothing in this sample uses it — the numbers
would point at somebody else's ocean.

## `min_journey`: the anti-doorstep rule

`reach_location` and `escort_entity` take `min_journey` — how far the player (or the escortee) must
*start* from the destination for the trip to count. Default is the server's `minEscortJourney` (24).

Below it the quest is **not offered at all**, and if it is granted some other way, arrival is **not
credited** until they have genuinely been that far away. Without it, a quest whose destination you happen
to be standing in completes on the first poll and pays out for nothing.

## `source`: where the marker points

Any of `obtain_item`, `craft_item`, `fish_item`, `kill_entity`, `break_block`, `place_block`,
`interact_block` and `item_delivery` may carry a `source`. It does **not** change what the objective
requires — only what the world marker, the HUD direction line and the minimap waypoint point at.

```json
{ "source": { "structure": "minecraft:fortress" } }
{ "source": { "structure_tag": "mcaquests:ocean_ruins" } }
{ "source": { "biome": "minecraft:warm_ocean" } }
{ "source": { "biome_tag": "minecraft:is_badlands" } }
{ "source": { "block": "minecraft:sweet_berry_bush" } }
{ "source": { "block_tag": "minecraft:iron_ores" } }
{ "source": { "dimension": "minecraft:the_nether" } }
{ "source": { "anchor": { "anchor": "nearest_village" } } }
```

- `structure` / `biome` run a real world search (vanilla's `/locate`), **once**, on the first pass that
  needs it; the answer is written into the objective's progress and survives a restart. Only the quest
  the player is actually following searches at all.
- `block` searches outward from the player, within 48 blocks, **in loaded chunks only**, and is
  re-checked every pass — a berry bush gets picked, and a marker on empty ground is worse than no marker.
- `dimension` points at **the way in** (the nearest lit nether portal, the nearest stronghold for the
  End) and stops pointing once the player is through.
- `anchor` is how you point at a village; there is no separate `village` field because the anchor
  language already says all three.
- When several are set, dimension and anchor win, then `block`, then structure and biome — a berry bush
  twenty blocks away is a better answer than an ocean two thousand blocks away, and far cheaper to find.

**At least one must be set** or the pack fails to load: a `source` naming nothing is a marker that never
appears with no way to find out why.

**Nothing is inferred, and that is deliberate.** There is no index of where eight prismarine crystals are;
a guess would send the player somewhere confidently wrong, which is worse than nowhere, because they
would go. An objective with no `source` draws no marker and its text carries the whole instruction.
`item_delivery` is the single exception: with no `source` it points at the villager the goods are for.

Objectives that already know where they are sending you — `visit_dimension`, `enter_structure`,
`visit_biome`, `reach_location`, `defend_location`, `build_near_location`, `escort_entity`,
`talk_to_profession`, `trade_with_villager`, `sleep_or_rest`, every villager-targeted objective — take no
`source`. (`sleep_or_rest` points at the **player's own** bed, not the giver's: it is the player who has
to sleep.)

## Why is nothing marked?

```
/mcaquests debug guidance    # every active quest, what it would point at, and which marker won
/mcaquests debug waypoints   # which of JourneyMap and Xaero bound, and a round-trip probe
```

The first distinguishes "this objective has no place attached" from "a search found nothing in range"
from "the feature is switched off" — three states that look identical from inside the game. The second
exists because both minimap mods can decline a waypoint without throwing, which is indistinguishable
from having no minimap at all.

## Tags used here

`minecraft:is_forest`, `minecraft:is_badlands`, `minecraft:iron_ores`, `minecraft:fences`,
`minecraft:skeletons` and `minecraft:village` are vanilla. `mcaquests:ocean_ruins` is a structure tag
**this mod ships**, which is why it is safe to name — it collects `ocean_ruin_cold` and `ocean_ruin_warm`
so a quest does not have to guess which one a warm sea generated.

Structure and biome ids live in *dynamic*, world-driven registries, so a name your world does not have
cannot be caught at load. It produces no marker rather than an error, and `/mcaquests validate` against a
running world is what catches it.
