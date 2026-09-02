# MCA: Quests 1.5.0 — “Life of the Town”

## Implementation specification for a major Townstead-centered content update

**Status:** proposed implementation contract  
**Recommended release:** `1.5.0` (a large, additive feature release; no save or datapack break)  
**Minecraft / loader:** Minecraft 1.20.1, Forge 47.4.10+, Java 17  
**Required mod:** MCA Reborn `[7.6,8)`  
**Optional integration:** Townstead `[0.7.5,0.8)`, with 0.7.6 as the primary compatibility target  
**Working title:** *Life of the Town*

---

## 1. Executive decision

Build this update as a content-and-systems expansion in which Townstead changes what quests are *about*, not merely which condition hides an ordinary fetch quest. A Townstead quest should observe or affect a real need, shift, season, workplace, life stage, profession track, or village spirit. Vanilla/MCA quests should emphasize journeys, rescues, defenses, construction, family, and multi-step adventures rather than larger item counts.

The release should add exactly:

| Content | Current | New | Post-update | Townstead share of new |
|---|---:|---:|---:|---:|
| Personal quest JSONs | 190 | 72 | 262 | 48 (66.7%) |
| Village project JSONs | 13 | 8 | 21 | 6 (75%) |
| Situation JSONs | 15 | 10 | 25 | 7 (70%) |
| **Playable definitions** | **218** | **90** | **308** | **61 (67.8%)** |

The 72 personal quests comprise nine four-stage arcs (36 quests) and 36 standalones. Six arcs and 24 standalones use Townstead; three arcs and 12 standalones work without Townstead.

This release also needs a small set of additive engine features. They are not optional polish: without them, the proposed content either becomes repetitive, cannot point players at Townstead buildings, or risks being impossible with particular Townstead profession registries.

### Release-blocking outcomes

1. No built-in quest can be offered if its Townstead progression target is not achievable in the loaded Townstead registry.
2. All Townstead content disappears cleanly when Townstead is absent; already-active content suspends and resumes under the existing rules.
3. Seasonal quests repeat by the loaded Townstead calendar, not by hard-coded Minecraft day counts.
4. Quests can escort, defend, reach, or build at a registered Townstead/MCA building.
5. A villager’s completed work shifts can be counted across days without requiring the player to stare at the villager continuously.
6. The three-offer menu remains diverse even after adding 72 quests.
7. Every new definition has English and Brazilian Portuguese locale parity and passes `/mcaquests validate`.

---

## 2. Source baselines and verified constraints

This plan is based on the following repository states. Pin implementation decisions and compatibility tests to these revisions so later upstream changes are visible rather than silently changing assumptions.

| Repository | Revision inspected | Role |
|---|---|---|
| [MCAQuests](https://github.com/otectus/MCAQuests/tree/621b7d72a11dc55595c9280ad24d7029f67ceda8) | `621b7d72a11dc55595c9280ad24d7029f67ceda8` (`main`, version 1.4.0) | Existing quest engine, data format, Townstead bridge, and built-in content |
| [Townstead 0.7.6](https://github.com/AetherianArtificer/Townstead/tree/0.7.6) | tag `0.7.6`, tree `01af409f2b67e1f0e35dbd32d8df7001264ef700` | Supported 1.20.1 feature and registry behavior |
| [MCA Reborn 1.20.1](https://github.com/Luke100000/minecraft-comes-alive/tree/4d824551b30654e5792e19e84f3933e3e3d90ea2) | `4d824551b30654e5792e19e84f3933e3e3d90ea2` | Villager identity, family, village, building, residency, and profession state |

### 2.1 What MCA: Quests already supports

The current mod is datapack-driven and already has the foundations this update should preserve:

- 25 Townstead quests, five Townstead projects, and seven Townstead situations.
- Townstead conditions for availability, arbitrary queried values, buildings, spirit, and skills.
- Townstead objectives for state, baseline change, profession progress, new/updated buildings, spirit progress, and healthy residents.
- Townstead rewards for needs, profession XP, skills, and reactions.
- A reflection-only Townstead bridge with 13 granular capabilities.
- Frozen acceptance baselines; optional-mod loss suspends an objective and pauses its deadline.
- Atomic, exact-once item movement into a villager inventory.
- Relationship chains, dynamic situations, shared projects, MCA family targets, stable target UUIDs, location anchors, village reputation, titles, templates, and FTB Quests interoperability.

The current Townstead query surface is much broader than the built-in content uses. Existing built-ins query only the `villager` source and mostly `professionId`, `professionLevel`, `schedule.currentActivity`, `schedule.onSchedule`, hunger, thirst, energy, and gating state. The bridge also exposes calendar, building, spirit, root, gene, life-stage, heritage, and age data. The proposed content deliberately uses that currently dormant surface.

### 2.2 Townstead 0.7.6 facts that content must respect

Townstead exposes:

- needs: hunger `0–100`, thirst `0–20`, fatigue `0–20`, inverse energy, collapse, and needs gating;
- schedules: current and planned activity, on-schedule state, templates, shifts, and week plans;
- calendar profiles: world day, year, month, day, day-of-year, day-of-week, and season;
- professions: profession ID, XP, tier, progression definitions, daily caps, and optional skills;
- registered buildings and tiers;
- village spirit totals, per-spirit points, contributing buildings, tier, classification, primary ID, and secondary ID;
- root/life/gene views including root ID, life stage, biological age, apparent age, senior/ageless state, personality, fertility, heritage, carried variants, and expressed alleles.

Townstead’s spirit thresholds are 25, 60, 140, 300, and 600 points for tiers 1–5. Its classification logic is:

- `settlement` when no spirit reaches tier 1;
- a single identity when the leading spirit is at least 40% of the total;
- `blend` when the leading two are each at least 25%;
- otherwise `mixed`.

The registered spirit IDs are `nautical`, `pastoral`, `martial`, `scholar`, `industrious`, `commercial`, `tourism`, `magical`, `spiritual`, `haunted`, `mining`, and `natural`. In the inspected 0.7.6 content, however, bundled building contributors are only demonstrably available for:

`nautical`, `pastoral`, `martial`, `scholar`, `industrious`, `commercial`, `tourism`, and `haunted`.

Do not ship built-in goals for `magical`, `spiritual`, `mining`, or `natural` until the loaded registry proves that at least one building contributes to that spirit. Datapacks may still use them when another pack supplies contributors.

### 2.3 Reachable building/spirit vocabulary

Use these 0.7.6 contributions as the balancing baseline. The compatibility layer must normalize the actual registered building ID/family; content should not infer contributions from display names.

| Building family | Spirit contribution |
|---|---|
| `armorer` | martial 5, industrious 2 |
| `armory` | martial 5 |
| `bakery` | pastoral 5, commercial 2 |
| `blacksmith` | industrious 5, martial 2 |
| `bookkeeper` | scholar 5 |
| `butcher` / existing MCAQ alias `butcher_shop` | pastoral 5, industrious 2 |
| `cartographer` | scholar 5 |
| `dock` level 1 / 2 / 3 | nautical 5 / 10 / 20; industrious 0 / 5 / 10 |
| `fishermans_hut` | nautical 5, pastoral 2 |
| `fletcher` | martial 5, industrious 2 |
| `graveyard` | haunted 5, scholar 2 |
| `infirmary` | scholar 5, commercial 2 |
| `inn` | commercial 5, tourism 2 |
| `leatherworker` | industrious 5, martial 2 |
| `library` | scholar 5 |
| `mason` | industrious 5 |
| `music_store` | commercial 5, scholar 2 |
| `pen` | pastoral 6 |
| `prison` | martial 5 |
| `toolsmith` | industrious 5 |
| `weaponsmith` | martial 5, industrious 2 |
| `weaving_mill` | industrious 5, pastoral 2 |
| `wool_shed` | pastoral 5, industrious 1 |

### 2.4 Critical compatibility defect in the current built-ins

Townstead 0.7.6’s built-in `ProfessionXpType` supplies progression for farmer, butcher, cook, and shepherd. Its data-driven profession and skill registries exist, but the repository does not bundle profession or skill JSON definitions. Fisherman and leatherworker work tasks do not award progression XP in the inspected source.

Consequences in MCA: Quests 1.4.0:

- `townstead_deep_water_days` can wait forever for fisherman XP.
- `townstead_the_master_tanner` can wait forever for leatherworker XP.
- `townstead_master_of_the_trade` can be offered to fisherman or leatherworker and wait forever.
- profession-XP rewards on `dockside_catch`, `mend_the_nets`, `leatherworkers_order`, and `tanned_and_ready` can silently do nothing.
- `townstead_master_artisan` can tell a story about an unsupported “master” track.

This must be repaired before new content is enabled. It is not acceptable to solve this with a hard-coded profession whitelist: Townstead explicitly supports datapack-provided profession definitions, so the loaded progression registry is the authority.

---

## 3. Product goals and non-goals

### 3.1 Design pillars

1. **The town is the protagonist.** Personal errands should feed into a visible workplace, season, household, or civic identity.
2. **Observe real simulation state.** “Work a shift” means Townstead reports a completed shift; “prepare for winter” uses the loaded calendar; “healthy village” checks the resident population.
3. **Build, travel, protect, and care.** At least half of medium/hard content must include a world-state, movement, combat, building, relationship, or sustained-state objective—not only possession or delivery.
4. **Long arcs have a changing texture.** No four-stage chain may use the same objective shape in more than two stages.
5. **Failure is legible, never arbitrary.** Missing mods, unloaded targets, missing progression tracks, or absent building families suspend or hide content. They do not produce silent impossible quests.
6. **Townstead is optional.** The non-Townstead set remains a substantial update on its own.
7. **Data remains pack-friendly.** New behavior is exposed through additive codecs and documented, not locked into hard-coded built-ins.

### 3.2 Non-goals

- Do not add a hard compile-time dependency on Townstead or MCA classes.
- Do not change Minecraft, MCA, or Townstead save data.
- Do not make Townstead mandatory for the quest menu, projects, situations, journal, or vanilla/MCA quests.
- Do not add new GUIs unless a compact context line is insufficient.
- Do not invent bundled skill IDs. Townstead 0.7.6 has a skill registry but no bundled definitions.
- Do not grant or remove genes, roots, heritage, fertility, age, or life stages as rewards.
- Do not frame ancestry or genetics as “good,” “bad,” “pure,” superior, or inferior. Root/gene data is for respectful context and story gating only.
- Do not require a fresh world.
- Do not bump `format_version` solely for additive optional fields.

---

## 4. Phase zero: repair existing Townstead content

Ship these changes in the same release and test them before adding the new catalog.

| Existing definition | Required repair |
|---|---|
| `townstead_deep_water_days` | Replace fisherman `townstead_profession_progress` with: fish 12 cod or salmon, complete two observed work shifts, and require a level-2 dock. Retain the title and hard reward band. |
| `townstead_the_master_tanner` | Replace leatherworker XP delta with: complete three work shifts, deliver 32 leather to the giver’s inventory, and require a registered `leatherworker` workplace. Keep it a once-only capstone but stop calling a numerical Townstead tier “master.” |
| `townstead_master_of_the_trade` | Gate with the new progression-track condition and bind the giver’s current profession at acceptance. Restrict the bundled giver list to farmer, shepherd, and butcher; allow datapacks to add any profession whose loaded track proves the requested delta/tier is reachable. |
| `dockside_catch`, `mend_the_nets` | Remove the unsupported fisherman XP reward. Replace it with +6 ordinary XP and a `townstead_reaction` reward. |
| `leatherworkers_order`, `tanned_and_ready` | Remove the unsupported leatherworker XP reward. Replace it with +6 ordinary XP and a `townstead_reaction` reward. |
| `townstead_master_artisan` situation | Require a provably reachable tier-3 progression track and bind the signal’s actual artisan. Default giver professions: farmer, shepherd, butcher. |
| All Townstead XP/skill definitions | Validate IDs against the live registries. Hide impossible offers, suspend active objectives if a registry disappears after reload, and log one actionable warning per definition/reload. |

Existing saves need no migration: active instances retain their frozen objective list. To avoid changing a quest under a player after reload, continue resolving an active instance against its accepted serialized objective data. The repaired definition applies to new acceptances. If current state stores only the definition ID rather than a concrete objective snapshot, add a one-time compatibility rule: legacy instances of the three impossible quests are marked suspendable and can be abandoned without penalty; do not silently complete them.

---

## 5. Required additive engine work

This section is normative. Names may be adjusted to match repository conventions, but JSON behavior, persistence, failure behavior, and validation must remain equivalent.

### 5.1 Live profession-track introspection

Add a value object and bridge method:

```java
public record TownsteadProfessionTrackView(
    String professionId,
    List<Integer> tierThresholds,
    int maxTier,
    int maxXp,
    int dailyCap,
    boolean dataDriven
) {
    boolean supportsTier(int tier) { ... }
    boolean supportsXpDelta(int currentXp, int delta) { ... }
}
```

Add `READ_PROFESSION_SPEC` to `TownsteadCapability`. In the 0.7.6 binding, reflect Townstead’s `ProfessionProgressions.spec(String)`, then read the returned progression spec. Townstead’s built-in enum IDs are bare paths (`farmer`, `butcher`, `cook`, `shepherd`) while MCA profession IDs are commonly namespaced. For a namespaced ID, try the canonical full ID and, if it returns the zero/default spec, retry the path; cache the successful result under the canonical MCA ID. Do not stop at the first default result. Data-driven definitions already compare both full ID and path.

A track is **not progressive** when it exposes only the zero/default tier and both cap/max XP are zero. Compute `maxTier` through the progression spec’s own tier calculation at `maxXp` when available; do not assume it equals the threshold-array length, because the 0.7.6 built-ins report tier 5 from five thresholds. Data-driven definitions should be discoverable through `ProfessionDefs`; built-ins remain valid through `ProfessionProgressions` even if that map is empty.

Add condition:

```json
{
  "type": "mcaquests:townstead_profession_track",
  "target": "giver",
  "profession": "minecraft:farmer",
  "minimum_max_tier": 3,
  "minimum_remaining_xp": 150,
  "missing": false
}
```

Fields:

| Field | Default | Contract |
|---|---|---|
| `target` | `giver` | Same Townstead target vocabulary as other Townstead conditions. |
| `profession` | target’s current profession | Optional explicit ID. If omitted, bind the target’s current profession when accepted. |
| `minimum_max_tier` | omitted | Track must expose at least this tier. |
| `minimum_remaining_xp` | omitted | `maxXp - currentXp` must be at least this amount when offered. |
| `missing` | `false` | If the registry/spec cannot be read, false hides the offer. |

Retain the existing `townstead_profession_progress` behavior in which omitted `profession` means “the target’s current profession at acceptance,” but make the frozen ID participate in the new track validation. Reprofessioning after acceptance suspends when `require_current_profession:true`; it must not switch the objective to a different track.

Validation rules:

- A bundled profession-progress objective must have the new track condition in its effective `all_of` gate.
- `target_tier` must not exceed the loaded `maxTier`.
- `xp_delta` must be positive and no larger than reachable remaining XP at offer time.
- A profession-XP reward whose explicit ID has no progressive track is rejected for built-ins and warned for external datapacks.
- If a datapack reload removes a track used by an active objective, return `unavailableReason`, preserve progress, and pause deadlines.

### 5.2 Skill registry validation

Add `READ_SKILL_REGISTRY` when Townstead’s `SkillDefs.byId/all` can be bound. `townstead_skill` conditions and `townstead_skill` rewards must verify the ID exists. Bundled content in this release must contain **zero** skill IDs; this work prevents existing and third-party packs from silently mutating nothing.

### 5.3 Completed-shift streak objective

Add `mcaquests:townstead_schedule_streak`:

```json
{
  "type": "mcaquests:townstead_schedule_streak",
  "target": "giver",
  "activity": "work",
  "required_shifts": 3,
  "minimum_coverage": 0.65,
  "require_on_schedule": true,
  "reset_on_miss": false
}
```

| Field | Default | Range / meaning |
|---|---|---|
| `target` | `giver` | Bound villager UUID. |
| `activity` | `work` | Planned/current activity to observe. |
| `required_shifts` | required | `1–28`. |
| `minimum_coverage` | `0.60` | `0.10–1.00`; fraction of sampled, loaded shift ticks that match. |
| `require_on_schedule` | `true` | Matching samples must also report `onSchedule`. |
| `reset_on_miss` | `false` | If true, a failed completed shift clears the current streak; otherwise it simply adds no progress. |

Persistence in `ObjectiveProgress.extra`:

- bound target UUID;
- current calendar profile and shift key (`profile/year/day/template/segment` or the closest stable upstream identity);
- sampled ticks and matching ticks for the active shift;
- last sample game time;
- ordered set of credited shift keys, capped at `required_shifts`;
- current streak when `reset_on_miss` is true.

Rules:

1. The first observation arms a shift; it never grants a free completed shift.
2. Credit only when the observed planned shift transitions out or the calendar advances beyond it.
3. Unloaded villagers and missing capabilities pause sampling. They neither pass nor fail a shift.
4. Prevent duplicate credit across logout, server restart, dimension change, `/reload`, or clock rollback by persisting credited keys.
5. If less than 20% of the planned shift was observable, mark it “unknown,” not failed.
6. Poll only active objectives. Share the existing per-tick Townstead evaluation cache.
7. Requires `READ_VILLAGER`, `READ_SCHEDULE`, and `READ_CALENDAR`.

This objective replaces long `hold_ticks` for multi-day work stories. Keep `townstead_state` for short, immediate observations.

### 5.4 Registered-building location anchor

Extend `LocationAnchor.Type` with `TOWNSTEAD_BUILDING`:

```json
{
  "anchor": "townstead_building",
  "building_type": "dock",
  "minimum_level": 2,
  "selection": "nearest_to_giver"
}
```

| Field | Default | Contract |
|---|---|---|
| `building_type` | required | Normalized Townstead/MCA building family. |
| `minimum_level` | `1` | Minimum registered tier. |
| `selection` | `nearest_to_giver` | Initially support `nearest_to_giver` and `nearest_to_player_at_accept`. |

Resolve within the giver’s MCA home village using the existing village ID and Townstead building snapshot. Select by squared distance, then building ID as the deterministic tie-breaker. Freeze dimension, center, building ID, family, tier, and village ID at acceptance exactly as other dynamic destinations are frozen. If the building is removed after acceptance, the frozen position remains a valid travel/build/defense location; objectives that explicitly require the live registered building still read live state separately.

The anchor works with `reach_location`, `escort_entity`, `defend_location`, `build_near_location`, `breed_animals`, and `tame_animal`. It requires `READ_BUILDING` plus the existing MCA village binding. It must parse while Townstead is absent and resolve unavailable without crashing.

Also add a core anchor:

```json
{ "anchor": "nearest_other_village", "radius": 2048 }
```

It excludes the giver’s home village ID, chooses the nearest other registered MCA village, ties by stable village ID, and freezes the target border/center. This powers routes and caravan quests without Townstead.

### 5.5 Delivery to the recipient’s real inventory

`ItemDeliveryObjective` already has an atomic `DeliveryDestination`. Add the same optional field to `DeliverToVillagerObjective`:

```json
{
  "type": "mcaquests:deliver_to_villager",
  "recipient": { "mode": "family", "relation": "child" },
  "item": "minecraft:bread",
  "count": 6,
  "destination": {
    "type": "townstead_villager_inventory",
    "target": "recipient"
  }
}
```

If `destination` is absent, preserve today’s right-click-and-consume behavior exactly. If present:

- freeze the same bound recipient UUID used by the objective;
- simulate full insertion before removing anything from the player;
- refuse the handoff when the full stack cannot fit;
- remove from the player and insert into the recipient in one server-side transaction;
- return/drop any unexpected remainder at the player and log an invariant violation;
- persist a completion marker before emitting downstream events so packet replay cannot duplicate items;
- never trust a client-supplied UUID, count, or stack.

### 5.6 Calendar-relative repeat rules

Extend `RepeatRule` additively:

```json
{
  "type": "period",
  "period": "season",
  "scope": "giver",
  "fallback_cooldown_ticks": 96000
}
```

Supported periods: `townstead_week`, `season`, and `year`. At completion, store a token in `QuestHistory`:

`calendarProfileId / period / year / periodIndex`

A quest is eligible again only when the live token differs. Custom calendar profiles are authoritative; never assume four seasons, seven days, or a fixed year length. `scope` follows existing repeat history semantics (`global` or `giver`). If `READ_CALENDAR` is temporarily unavailable, use `fallback_cooldown_ticks` for an unaccepted offer and suspend an already-active calendar-bound objective. Never treat missing calendar data as a new period.

Keep `once`, `cooldown`, and `repeatable` unchanged.

### 5.7 Resident wellbeing: thirst and collapse

Extend both `townstead_healthy_residents` and `townstead_resident_wellbeing_project` with `thirst_min` and `minimum_loaded_fraction`. Retain the personal objective’s existing `require_not_collapsed` field and add/standardize it on the project objective:

| Field | Default | Meaning |
|---|---|---|
| `thirst_min` | omitted | Resident must meet this thirst value. Valid `0–20`. |
| `require_not_collapsed` | `true` when any need threshold is present | Exclude collapsed residents; existing explicitly authored values retain their meaning. |
| `minimum_loaded_fraction` | `0.50` | At least this fraction of MCA resident-roll UUIDs must be observable before the hold timer advances. |

Do not count an unloaded population as healthy. When observation coverage is below the floor, show “Waiting for residents to be observable” and pause the hold.

### 5.8 Transition-driven situation signals

Add two generic Townstead triggers:

```json
{
  "type": "mcaquests:townstead_calendar_transition",
  "transition": "season",
  "to": "winter"
}
```

```json
{
  "type": "mcaquests:townstead_life_transition",
  "transition": "senior",
  "from": false,
  "to": true
}
```

Calendar transition supports `week`, `season`, and `year`. Life transition supports `canonical_stage`, raw `life_stage`, and `senior`. `canonical_stage` resolves the current stage definition’s `presentsAs` value through the root view, so a custom root whose adult stage is named “butterfly” still produces the semantic `child → adult` transition. The detector stores last-seen values per village or villager in `TownsteadSignalStateSavedData`.

Signal rules:

- First observation seeds state and never fires.
- Restart, dimension unload/reload, and datapack reload never replay a transition.
- Calendar profile changes seed a fresh baseline rather than synthesizing a season change.
- Life signals carry the subject UUID so the offer can prefer and bind that villager.
- Stale subject data expires only after the MCA family tree confirms death/removal; mere unload is not removal.

Add `townstead_schedule_disruption` for the “Broken Routine” situation. It fires only when at least `minimum_observed` residents are visible and `minimum_fraction` remain off schedule for `hold_ticks`. Require a recovery threshold at least 0.15 lower than the trigger threshold before it can arm again (hysteresis).

For the three new core situations, add two small MCA-backed signals:

- `mcaquests:villager_stranded`: a living resident is outside their home-village border by `minimum_distance`, at night, and has remained there for `hold_ticks`;
- `mcaquests:hostiles_near_home`: at least `count` hostiles are within `radius` of a resident’s bed or the village center for `hold_ticks`.

Both must be throttled per village and use existing MCA binding abstractions only.

### 5.9 Offer-group diversification

Adding 72 quests to a default three-slot offer menu can otherwise yield three nearly identical need errands. Add optional `offer_group` to `OfferShaping`:

```json
{ "offer_group": "townstead_need" }
```

During a reroll, fill priority tiers as today but take at most one definition from each non-empty group until every eligible group has had one chance. If slots remain, permit second picks by existing weight. Ungrouped third-party quests behave exactly as today.

Built-in groups:

| Group | Content |
|---|---|
| `townstead_need` | hunger, thirst, fatigue, collapse |
| `townstead_schedule` | shifts, work/rest routine |
| `townstead_work` | profession and workplace |
| `townstead_life` | age, family, calendar milestones |
| `townstead_spirit` | buildings, identity, civic character |
| `townstead_season` | seasonal preparation and transitions |
| `core_adventure` | travel, structures, combat, exploration |
| `core_relationship` | family, escort, rescue, ceremony |

Recommended priorities: emergencies `8–10`; unlocked chain continuation `6`; seasonal/life transition `5`; ordinary work `1–3`. Priority is not difficulty.

### 5.10 Context lines and configuration

Extend `TownsteadContextLines` so a card shows only state actually read by that definition:

- current season/day for calendar-gated content;
- current/required shifts for streaks;
- life stage or senior state for life content;
- target building family, tier, and direction/distance;
- current spirit identity, points, and requested tier;
- current/max profession tier for progression content.

Retain `compat.townstead.contentEnabled` as the master toggle. Add optional sub-toggles, all default `true`:

```toml
[compat.townstead.content]
needsAndSchedules = true
professions = true
calendarAndLife = true
spiritAndBuildings = true
projects = true
situations = true
```

The master false overrides all children. Config changes affect future offers, not accepted quests.

### 5.11 Achievability validator

Run a semantic preflight after MCAQ definitions and Townstead registries have reloaded. `/mcaquests validate` must report:

- missing capability gates for every Townstead condition/objective/reward;
- progression tiers/XP that the loaded track cannot reach;
- skill IDs absent from the loaded registry;
- spirit IDs absent from the spirit registry;
- spirit targets with no loaded building contributor;
- building anchors/types absent from the normalized building vocabulary;
- period repeats without a calendar capability gate;
- multiple objectives that accidentally share the same fingerprint when the design calls for distinct work;
- a `deliver_to_villager.destination` target that is not exactly the bound recipient;
- seasonal situation transitions whose `to` value is not present in the loaded calendar profile;
- hard quests containing only possession/delivery objectives;
- missing dialogue states or locale-key parity.

External datapacks should receive actionable warnings where a registry can change at runtime; invalid built-in definitions fail the build.

---

## 6. Content authoring contract

The catalogs below are not brainstorming lists. Treat each row as a required definition with a stable ID, giver, gate, objective contract, repeat rule, and reward band. Dialogue wording may improve during implementation, but changing mechanics or counts requires an explicit design review because the project and test totals depend on them.

### 6.1 Shorthand used in the catalog

Townstead capability abbreviations:

| Code | Capability |
|---|---|
| `V` | `READ_VILLAGER` |
| `N` | `READ_NEEDS` |
| `S` | `READ_SCHEDULE` |
| `P` | `READ_PROFESSION` |
| `PX` | `READ_PROFESSION_SPEC` |
| `C` | `READ_CALENDAR` |
| `B` | `READ_BUILDING` |
| `SP` | `READ_SPIRIT` |
| `R` | `READ_ROOT` |
| `RX` | `DISPATCH_REACTION` |

Every Townstead row must translate its capability codes into an explicit `mcaquests:townstead_available.capabilities` array. Never infer capabilities from objective types at runtime.

Reward bands:

| Band | JSON rewards |
|---|---|
| `E` | `difficulty:easy`; semantic currency; 20 XP; 4 MCA hearts |
| `M` | `difficulty:medium`; semantic currency; 34 XP; 8 MCA hearts |
| `H` | `difficulty:hard`; semantic currency; 55 XP; 14 MCA hearts |

Chain finals additionally grant 8 village reputation unless a row says otherwise. Major arc finals grant a title listed in §11. Do not add Townstead profession XP as a routine payout; profession progress should come from Townstead’s own successful-work rules.

Objective shorthand:

- `inventory(item × n)` means `mcaquests:item_delivery` with `townstead_villager_inventory` targeting the giver.
- `shift(n, coverage)` means the new `mcaquests:townstead_schedule_streak` with `activity:work`, `require_on_schedule:true`, and the named count/coverage.
- `building(family, level, new)` means `townstead_building_registered`, minimum level, and `require_new_or_upgraded:true`.
- `at building(...)` means the new frozen `townstead_building` location anchor.
- `healthy(...)` means `townstead_healthy_residents`, `require_not_collapsed:true`, and `minimum_loaded_fraction:0.50`.
- `spirit(id, tier)` means `townstead_spirit_progress` for that spirit and target tier; `spirit(any, tier)` omits the spirit ID.
- All item/block/entity IDs in implementation are fully namespaced even when a table uses a short display name.

### 6.2 Global quest rules

- All new JSON uses `format_version: 1`, `enabled: true`, translation-backed title/dialogue, and one of the offer groups in §5.9.
- All chain stages use `repeat:{"type":"once"}` and giver-scoped prerequisites. Stage `n` names stage `n-1` in `prerequisites`; stage `n-1` names stage `n` in `unlocks`.
- A stage-2+ chain offer has priority 6. Stage 1 uses priority 3. Emergency standalones use 8; seasonal/life milestones use 5; ordinary standalones use 2.
- If a chain stage is gated to a later season, the preceding completion dialogue must say when it will continue.
- Use `failure.fail_on_giver_death:true` only when the giver is being escorted/protected or their survival is the story. Avoid deadlines on season-long arcs.
- Hard quests must contain at least one non-possession objective.
- An objective that reads a bound villager must show their real MCA name and a direction/distance hint using the existing target system.

---

## 7. Forty-eight new Townstead personal quests

### 7.1 Arc A — Seasons of the Soil (4)

**Arc key:** `seasons_of_the_soil`  
**Relationship arc text:** “Seasons of the Soil”  
**Giver:** adult `minecraft:farmer`  
**Intent:** a deliberately year-long relationship with one farmer. The player helps with planting, heat, harvest, and winter welfare. Custom Townstead calendars determine all season gates.

| Stage / ID / title | Gate and capabilities | Required objectives | Band / outcome |
|---|---|---|---|
| 1 — `mcaquests:townstead_spring_in_the_furrows` — **Spring in the Furrows** | season `spring`; `V,S,C` | `inventory(wheat_seeds ×32)`; `shift(1, 0.60)` | `M`; establishes the arc |
| 2 — `mcaquests:townstead_heat_over_the_fields` — **Heat over the Fields** | season `summer`; `V,N,S,C` | `inventory(potion ×3)`; giver thirst increases by 6 and finishes at 12+; `shift(2, 0.60)` | `M`; dialogue treats water as care, not payment |
| 3 — `mcaquests:townstead_harvest_under_gold` — **Harvest under Gold** | season `autumn`; `V,S,C` | `inventory(wheat ×64)`; `inventory(carrot ×32)`; `inventory(potato ×32)`; `shift(2, 0.65)` | `H`; all goods remain usable by the villager |
| 4 — `mcaquests:townstead_winter_at_the_table` — **Winter at the Table** | season `winter`; `V,N,C,SP` | `healthy(minimum_observed:6, fraction:0.75, hunger:65, thirst:12, energy:10, hold:2400)`; `spirit(pastoral, 1)` | `H`; +8 reputation; title `keeper_of_seasons` |

Do not offer stage 1 unless the loaded calendar actually contains a `spring` season. The preflight must likewise validate the other three season IDs.

### 7.2 Arc B — Harbor of Hands (4)

**Arc key:** `harbor_of_hands`  
**Giver:** adult fisherman for stages 1–3; fisherman or cartographer for stage 4  
**Intent:** make the dock’s physical growth, work routine, safety, and nautical character visible without relying on the unsupported fisherman progression track.

| Stage / ID / title | Gate and capabilities | Required objectives | Band / outcome |
|---|---|---|---|
| 1 — `mcaquests:townstead_harbor_first_piling` — **The First Piling** | no qualifying dock at accept; `V,B` | `building(dock, 1, new)` | `M` |
| 2 — `mcaquests:townstead_harbor_working_tide` — **Working Tide** | dock level 1+; `V,S,C,B` | fish cod ×8; fish salmon ×8; `shift(2, 0.60)` | `M` |
| 3 — `mcaquests:townstead_harbor_lantern_line` — **A Line of Lanterns** | dock level 2+; `V,B` | place lantern ×6 within 16 blocks of frozen level-2 dock; place chain ×12 within the same radius | `H`; objective positions count once |
| 4 — `mcaquests:townstead_harbor_deep_water` — **Deep Water, Safe Return** | `V,B,SP` | `building(dock, 3, new)`; `spirit(nautical, 1)` | `H`; +8 reputation; title `harborhand` |

If a level-3 dock already exists before stage 4 is accepted, require another qualifying dock or a qualifying post-accept upgrade; never complete immediately from pre-existing state.

### 7.3 Arc C — Wool and Winter (4)

**Arc key:** `wool_and_winter`  
**Giver:** adult `minecraft:shepherd`  
**Intent:** connect animal husbandry, registered pasture infrastructure, wool storage, real shifts, and the supported shepherd progression track.

| Stage / ID / title | Gate and capabilities | Required objectives | Band / outcome |
|---|---|---|---|
| 1 — `mcaquests:townstead_pasture_first_fence` — **The First Fence** | `V,B` | `building(pen, 1, new)` | `M` |
| 2 — `mcaquests:townstead_pasture_lambing_day` — **Lambing Day** | pen exists; `V,B` | breed sheep ×6 within 24 blocks of frozen pen; `inventory(shears ×2)` | `M` |
| 3 — `mcaquests:townstead_pasture_wool_under_roof` — **Wool under Roof** | `V,S,C,B` | `building(wool_shed, 1, new)`; `inventory(white_wool ×32)`; `shift(2, 0.60)` | `H` |
| 4 — `mcaquests:townstead_pasture_keeper_of_the_flock` — **Keeper of the Flock** | progressive shepherd track with max tier 3+; `V,P,PX,B,SP` | shepherd reaches tier 3; `spirit(pastoral, 1)` | `H`; +8 reputation |

Accept any wool color through a new built-in item tag `mcaquests:wool` if `ItemTarget` supports tags for delivery. If it does not, retain white wool rather than adding eight near-duplicate objectives.

### 7.4 Arc D — Smokehouse Legacy (4)

**Arc key:** `smokehouse_legacy`  
**Giver:** adult `minecraft:butcher`  
**Intent:** a workplace arc that moves from construction to routine, winter provisioning, and the supported butcher progression track.

| Stage / ID / title | Gate and capabilities | Required objectives | Band / outcome |
|---|---|---|---|
| 1 — `mcaquests:townstead_smokehouse_first_fire` — **The First Fire** | no qualifying butcher workplace; `V,B` | `building(butcher_shop, 1, new)` | `M`; normalize `butcher` and `butcher_shop` aliases |
| 2 — `mcaquests:townstead_smokehouse_honest_shift` — **An Honest Shift** | workplace exists; `V,S,C,B` | `inventory(charcoal ×16)`; `shift(2, 0.65)` | `M` |
| 3 — `mcaquests:townstead_smokehouse_winter_stores` — **Smoke against Snow** | season `autumn` or `winter`; `V,N,C,B` | `inventory(cooked_beef ×16)`; `inventory(cooked_porkchop ×16)`; `inventory(cooked_mutton ×16)`; `healthy(5, 0.70, hunger:60, thirst:10, energy:8, hold:1200)` | `H` |
| 4 — `mcaquests:townstead_smokehouse_legacy` — **The Smokehouse Legacy** | progressive butcher track with max tier 3+; `V,P,PX,SP` | butcher reaches tier 3; `spirit(pastoral, 1)` | `H`; +8 reputation |

### 7.5 Arc E — The Apprenticeship Pact (4)

**Arc key:** `apprenticeship_pact`  
**Giver:** adult farmer, shepherd, or butcher with a progressive track  
**Intent:** a generic mentorship arc that safely binds whichever supported profession the giver actually practices. No explicit profession ID appears in the objectives.

| Stage / ID / title | Gate and capabilities | Required objectives | Band / outcome |
|---|---|---|---|
| 1 — `mcaquests:townstead_apprentice_tools_of_calling` — **Tools of a Calling** | giver tier 0–1; track supports tier 3; `V,P,PX` | `inventory(clock ×1)`; `inventory(book ×1)`; `inventory(bread ×6)` | `E`; supplies symbolize time, notes, and a meal |
| 2 — `mcaquests:townstead_apprentice_first_full_shift` — **The First Full Shift** | same frozen profession; `V,S,P,PX,C` | `shift(2, 0.65)` | `M` |
| 3 — `mcaquests:townstead_apprentice_trusted_hand` — **A Trusted Hand** | same frozen profession; at least 80 reachable XP remains; `V,S,P,PX,C` | profession XP increases by 80; `shift(2, 0.65)` | `H` |
| 4 — `mcaquests:townstead_apprentice_masterwork` — **The Masterwork** | same frozen profession; track supports tier 3; `V,S,P,PX,C` | target reaches tier 3; `shift(4, 0.70)` | `H`; +10 reputation; title `trusted_hand` |

If the giver changes profession after acceptance, show the frozen original profession in the unavailable line. Do not retarget.

### 7.6 Arc F — A Village with a Name (4)

**Arc key:** `village_with_a_name`  
**Giver:** adult cleric, librarian, mason, or cartographer  
**Intent:** grow from an anonymous settlement into a stable civic identity. This arc may take substantial building work but never demands an unreachable spirit family.

| Stage / ID / title | Gate and capabilities | Required objectives | Band / outcome |
|---|---|---|---|
| 1 — `mcaquests:townstead_character_first_mark` — **The First Mark** | classification `settlement`; `V,SP` | `spirit(any, 1)` | `M` |
| 2 — `mcaquests:townstead_character_choose_our_name` — **What We Call Ourselves** | spirit tier 1+; `V,SP` | hold `spirit.classification != settlement` for 1200 ticks; require `spirit.primaryId exists` | `M` |
| 3 — `mcaquests:townstead_character_strength_of_place` — **The Strength of Place** | classification not settlement; `V,B,SP` | grow current primary spirit by 20 points from acceptance; reach five contributing buildings | `H` |
| 4 — `mcaquests:townstead_character_living_legacy` — **A Living Legacy** | current primary ID has loaded contributors; `V,N,SP` | `spirit(any, 2)`; `healthy(6, 0.80, hunger:60, thirst:10, energy:9, hold:2400)` | `H`; +12 reputation; title `village_chronicler` |

The primary identity may change while the arc unfolds. Stage 3 freezes the primary spirit ID at acceptance so the requested growth cannot drift between categories. Stage 4 intentionally accepts any tier-2 identity.

### 7.7 Needs and schedule standalones (6)

| ID / title | Giver and gate | Required objectives | Caps / repeat / band |
|---|---|---|---|
| `mcaquests:townstead_breakfast_before_bells` — **Breakfast before the Bells** | farmer, mason, fisherman, or shepherd; hunger ≤40; planned work soon | `inventory(bread ×8)`; hunger increases by 20 and finishes at 60+ | `V,N,S,C`; cooldown 48,000; `E`; group `townstead_need` |
| `mcaquests:townstead_day_off_means_day_off` — **A Day Off Means a Day Off** | any adult worker; planned activity `rest`, energy ≤8 | energy increases by 6 and finishes at 12+; giver remains not-working for 600 observed ticks | `V,N,S`; period `townstead_week`; `M`; group `townstead_schedule` |
| `mcaquests:townstead_lanterns_for_late_shift` — **Lanterns for the Late Shift** | fisherman, butcher, toolsmith, or guard; current/planned work at night | `inventory(lantern ×6)`; hold on-schedule work for 600 ticks | `V,S,C`; cooldown 72,000; `M`; group `townstead_schedule` |
| `mcaquests:townstead_water_bearers_rounds` — **The Water-Bearers’ Rounds** | farmer or cleric; at least 4 observable thirsty residents | `healthy(5, 0.70, thirst:12, hunger:40, energy:6, hold:1200)` | `V,N`; cooldown 48,000; `H`; priority 8; group `townstead_need` |
| `mcaquests:townstead_rest_after_the_alarm` — **Rest after the Alarm** | guard, cleric, or farmer; giver energy ≤6; not collapsed | energy increases by 8 and finishes at 12+; protect giver near player for 1,200 ticks | `V,N`; cooldown 72,000; `M`; group `townstead_need` |
| `mcaquests:townstead_a_week_kept_well` — **A Week Kept Well** | farmer, shepherd, or butcher with work schedule | `shift(5, 0.65)`; final hunger 55+, thirst 10+, energy 8+ | `V,N,S,C`; period `townstead_week`; `H`; group `townstead_schedule` |

### 7.8 Buildings and workplaces standalones (5)

| ID / title | Giver and gate | Required objectives | Caps / repeat / band |
|---|---|---|---|
| `mcaquests:townstead_infirmary_before_frost` — **An Infirmary before Frost** | cleric or mason; autumn; no infirmary | `building(infirmary, 1, new)`; deliver honey bottles ×12 and golden carrots ×12 | `V,C,B`; once; `H`; group `townstead_season` |
| `mcaquests:townstead_rooms_for_the_road` — **Rooms for the Road** | cleric, cartographer, or unemployed adult; no inn | `building(inn, 1, new)`; place white bed ×4 and lantern ×8 at the frozen inn | `V,B`; once; `H`; group `townstead_spirit` |
| `mcaquests:townstead_bookkeepers_census` — **The Bookkeeper’s Census** | librarian or cartographer; bookkeeper exists | deliver paper ×32 and book ×8 into giver inventory; talk to farmer, guard, and cleric once each | `V,C,B`; period `year`; `M`; group `townstead_life` |
| `mcaquests:townstead_lanterns_for_the_departed` — **Lanterns for the Departed** | cleric; graveyard exists | place candle ×8 and lantern ×4 at frozen graveyard; kill zombie ×6 within 24 blocks of it | `V,B`; period `year`; `H`; group `townstead_life` |
| `mcaquests:townstead_a_real_workshop` — **A Real Workshop** | toolsmith or weaponsmith; no blacksmith level 1+ | `building(blacksmith, 1, new)`; place anvil ×1 and blast furnace ×2 at it | `V,B`; once; `H`; group `townstead_work` |

### 7.9 Life and calendar standalones (5)

| ID / title | Giver and gate | Required objectives | Caps / repeat / band |
|---|---|---|---|
| `mcaquests:townstead_first_workday_as_an_adult` — **The First Workday** | giver’s canonical stage presents as adult, not senior, profession level 0–1, and a work shift is planned | deliver compass ×1 and book ×1 to giver inventory; visit giver’s workstation; observe 600 ticks of on-schedule work | `V,S,P,R`; once per giver; `M`; group `townstead_life` |
| `mcaquests:townstead_the_elders_old_route` — **The Elder’s Old Route** | any senior adult with bed and workstation | escort giver to frozen workstation, then to frozen bed; protect for 1,200 ticks total; `fail_on_giver_death:true` | `V`; once per giver; `H`; group `core_relationship` |
| `mcaquests:townstead_names_in_the_family_book` — **Names in the Family Book** | giver `rootId exists`, has a same-village relative | deliver book ×1 to bound relative’s inventory; talk to that relative; return to giver | `V,R`; once per giver; `M`; group `townstead_life` |
| `mcaquests:townstead_spring_bells_and_blossoms` — **Spring Bells and Blossoms** | season spring | place flower-tag blocks ×12 and bell ×1 near home village | `V,C`; period `year`; `M`; priority 5; group `townstead_season` |
| `mcaquests:townstead_stores_against_winter` — **Stores against Winter** | autumn or first quarter of winter | `inventory(coal ×32)`; `inventory(bread ×24)`; `inventory(cooked_beef ×16)` | `V,C`; period `year`; `M`; group `townstead_season` |

Do not hard-code a numerical adulthood boundary. Townstead roots can name stages freely and expose a canonical `presentsAs` axis; this standalone and the transition situation use that semantic axis.

### 7.10 Eight identity commissions (8)

Each commission requires the named spirit to be the primary identity at tier 1+, uses period `season`, priority 2, and group `townstead_spirit`. The validator must prove the spirit has a loaded contributor. “At the building” always uses a frozen registered-building anchor.

| ID / title | Spirit / giver | Required objectives | Caps / band |
|---|---|---|---|
| `mcaquests:townstead_commission_salt_and_lanterns` — **Salt and Lanterns** | nautical; fisherman or cartographer | fish cod ×12; place lantern ×6 at dock | `V,B,SP`; `M` |
| `mcaquests:townstead_commission_breadth_of_the_fields` — **The Breadth of the Fields** | pastoral; farmer or shepherd | breed sheep ×6 near pen; place hay block ×12 there | `V,B,SP`; `M` |
| `mcaquests:townstead_commission_watch_at_the_gate` — **Watch at the Gate** | martial; guard, armorer, or weaponsmith | kill pillager ×8 within 32 blocks of armory or prison; place lantern ×4 | `V,B,SP`; `H` |
| `mcaquests:townstead_commission_ink_and_index` — **Ink and Index** | scholar; librarian or cartographer | craft book ×12; deliver paper ×24; place lectern ×2 at library/bookkeeper | `V,B,SP`; `M` |
| `mcaquests:townstead_commission_iron_sings` — **When Iron Sings** | industrious; toolsmith, weaponsmith, or mason | craft anvil ×1; place blast furnace ×2 and iron block ×4 at blacksmith | `V,B,SP`; `H` |
| `mcaquests:townstead_commission_market_bells` — **Market Bells** | commercial; farmer, butcher, or unemployed adult | complete 12 villager trades; deliver emerald ×16 to giver | `V,B,SP`; `M` |
| `mcaquests:townstead_commission_welcome_lights` — **Welcome Lights** | tourism; cartographer, cleric, or unemployed adult | place lantern ×8 and beds ×4 at inn; escort giver to nearest other village and back is **not** required | `V,B,SP`; `M` |
| `mcaquests:townstead_commission_bells_for_old_names` — **Bells for Old Names** | haunted; cleric or librarian | place candle ×12 at graveyard; defeat zombie ×8 and skeleton ×4 within 32 blocks | `V,B,SP`; `H` |

Where an identity has several possible contributor buildings, anchor resolution uses the nearest qualifying family in this preference order: a row’s named building, then the strongest loaded contributor, then nearest qualifying contributor. Freeze the chosen family/ID and display it on the card.

---

## 8. Twenty-four new core Minecraft/MCA personal quests

These definitions contain no Townstead condition, objective, reward, destination, capability, or registered-building anchor. They must load and play identically when Townstead has never been installed, is removed, or fails binding.

### 8.1 Arc G — The Broken Road (4)

**Arc key:** `the_broken_road`  
**Giver:** adult cartographer  
**Intent:** survey and reopen a route between two real MCA villages, improve its far end, and guard a villager through the journey. The new `nearest_other_village` anchor is resolved deterministically for the same giver, so all four stages point at the same destination unless the village is removed.

| Stage / ID / title | Gate | Required objectives | Band / outcome |
|---|---|---|---|
| 1 — `mcaquests:road_the_missing_mile` — **The Missing Mile** | giver has a home village; another MCA village within 2,048 blocks | reach frozen nearest other village with `min_journey:96`; talk to one cartographer there | `M`; group `core_adventure` |
| 2 — `mcaquests:road_clear_the_cut` — **Clear the Cut** | stage 1 complete | defeat pillager ×8 and spider ×6 within 48 blocks of the frozen destination village | `H`; group `core_adventure` |
| 3 — `mcaquests:road_raise_the_waystation` — **Raise the Waystation** | stage 2 complete | place gravel ×32, lantern ×8, campfire ×1, and chest ×1 within 24 blocks of the destination village center | `H`; every position counts once |
| 4 — `mcaquests:road_caravan_through` — **The Caravan Through** | stage 3 complete | lead giver to frozen other village, radius 8, `min_journey:96`, `wait_distance:8`; protect giver for 2,400 ticks; fail on giver death after escort staging begins | `H`; +10 reputation; title `roadwarden` |

The route does not place blocks automatically and does not claim to pathfind an entire caravan. It uses one named villager so the existing stable target, highlight, and escort safeguards remain honest.

### 8.2 Arc H — The Ashen Remedy (4)

**Arc key:** `the_ashen_remedy`  
**Giver:** an infected adult MCA villager  
**Intent:** turn curing into an expedition and relationship story rather than a single golden-apple handoff.

| Stage / ID / title | Gate | Required objectives | Band / outcome |
|---|---|---|---|
| 1 — `mcaquests:remedy_the_fevered_word` — **The Fevered Word** | giver infected; a cleric exists in the village | deliver spider eye ×2, glass bottle ×3, and paper ×1 to one bound cleric; return to original giver | `M`; group `core_relationship` |
| 2 — `mcaquests:remedy_embers_and_wart` — **Embers and Wart** | giver remains alive/infected | visit Nether; obtain nether wart ×6; kill blaze ×4 | `H`; group `core_adventure` |
| 3 — `mcaquests:remedy_gold_against_ash` — **Gold against Ash** | giver remains alive/infected | craft golden apple ×1; obtain fermented spider eye ×2; obtain gunpowder ×2 | `M`; no potion-NBT matching required |
| 4 — `mcaquests:remedy_the_returning_voice` — **The Returning Voice** | giver remains infected | cure giver with the existing cure objective; keep giver alive for 1,200 ticks; remain within 16 blocks for that recovery | `H`; +12 reputation; title `mender_of_kin`; fail on giver death |

Accepted stages suspend, rather than auto-fail, when the giver is merely unloaded. A confirmed death follows the existing failure rule. If another player cures the giver during stages 1–3, record a resolved “cured externally” outcome and unlock stage 4 as a short recovery/protection step; do not strand the chain behind an infection condition that has become false.

### 8.3 Arc I — The Bell at Dawn (4)

**Arc key:** `the_bell_at_dawn`  
**Giver:** adult `mca:guard`; fallback armorer or fletcher when no guard can sponsor  
**Intent:** prepare a village defense in visible stages and culminate in a dangerous field defense.

| Stage / ID / title | Gate | Required objectives | Band / outcome |
|---|---|---|---|
| 1 — `mcaquests:bell_shields_for_neighbors` — **Shields for Neighbors** | home village exists | deliver shield ×3 and bow ×2 to giver via ordinary `deliver_to_villager` consumption | `M`; group `core_relationship` |
| 2 — `mcaquests:bell_the_lantern_line` — **The Lantern Line** | stage 1 complete | place lantern ×12 and cobblestone wall ×24 within 40 blocks of home-village center | `H`; group `core_adventure` |
| 3 — `mcaquests:bell_watch_before_dawn` — **The Watch before Dawn** | stage 2 complete; night | defeat pillager ×8 and skeleton ×8 within 48 blocks of home village; sleep only after both counters finish | `H` |
| 4 — `mcaquests:bell_when_the_horns_answer` — **When the Horns Answer** | stage 3 complete | kill ravager ×1; defeat pillager ×12 within 64 blocks of home village; protect giver for 2,400 ticks; fail on giver death | `H`; +12 reputation; title `bellwarden` |

Stage 4 remains an open preparedness quest until a raid/ravager appears; it has no deadline. The card must say this plainly. Do not spawn a ravager or fake a raid.

### 8.4 Exploration and adventure standalones (6)

| ID / title | Giver and gate | Required objectives | Repeat / band / group |
|---|---|---|---|
| `mcaquests:relic_beneath_the_well` — **The Relic beneath the Well** | mason or librarian; player has not completed this quest | enter structure tag `mcaquests:trail_ruins`; obtain item tag `mcaquests:pottery_sherds` ×2; craft decorated pot ×1 | once; `H`; `core_adventure` |
| `mcaquests:atlas_of_four_horizons` — **Atlas of Four Horizons** | cartographer; player level 10+ | visit biome tags `minecraft:is_forest`, `minecraft:is_mountain`, `minecraft:is_ocean`, and `minecraft:is_badlands`; return to giver | once per giver; `H`; `core_adventure` |
| `mcaquests:drowned_ledger` — **The Drowned Ledger** | fisherman or cartographer | enter structure tag `mcaquests:ocean_ruins`; kill drowned ×10; obtain prismarine_crystals ×8 | cooldown 96,000; `H`; `core_adventure` |
| `mcaquests:echoes_below` — **Echoes Below** | librarian or cleric; player level 20+ | enter `minecraft:ancient_city`; obtain echo_shard ×4; craft recovery_compass ×1; leave the Deep Dark alive | once; `H`; `core_adventure` |
| `mcaquests:nether_relay` — **The Nether Relay** | cleric or weaponsmith; player has Nether access | visit Nether; kill blaze ×8; deliver blaze_rod ×6 to giver | cooldown 120,000; `H`; `core_adventure` |
| `mcaquests:trial_by_fire` — **Trial by Fire** | weaponsmith; player level 15+ | visit Nether; kill blaze ×6; craft diamond_sword ×1; deliver blaze_rod ×4 | once per giver; `H`; `core_adventure` |

Create the two custom tags in the built-in datapack. `mcaquests:trail_ruins` contains the 1.20.1 trail-ruins structure ID(s); `mcaquests:ocean_ruins` contains warm and cold ocean ruins; `mcaquests:pottery_sherds` contains every vanilla pottery sherd. Tests must validate every tag has at least one member in the 1.20.1 dynamic registry.

### 8.5 Relationship, rescue, and civic standalones (6)

| ID / title | Giver and gate | Required objectives | Repeat / band / group |
|---|---|---|---|
| `mcaquests:long_way_home` — **The Long Way Home** | cartographer, adventurer, or unemployed adult; another village within 2,048 blocks | lead giver to nearest other village, `min_journey:128`; protect for 2,400 ticks; fail on giver death after staging | cooldown 120,000 per giver; `H`; `core_relationship` |
| `mcaquests:honey_for_the_healer` — **Honey for the Healer** | cleric or farmer; bee biome/advancement not required | breed bee ×2 within 48 blocks of home village; craft honey_block ×4; deliver honey_bottle ×8 | cooldown 72,000; `M`; `core_relationship` |
| `mcaquests:last_banner_home` — **Bring the Last Banner Home** | guard, fletcher, or armorer | kill pillager ×12; kill vindicator ×4; deliver crossbow ×2 to giver | cooldown 96,000; `H`; `core_adventure` |
| `mcaquests:horse_for_the_courier` — **A Horse for the Courier** | cartographer or adventurer; another village exists | tame horse within 48 blocks of home village; reach nearest other village with `min_journey:128` | once per giver; `M`; `core_relationship`; card says the travel objective does not verify mounting |
| `mcaquests:feast_of_many_tables` — **A Feast of Many Tables** | farmer, butcher, cleric, or spouse; village has all three professions | hand bread ×12 to a bound farmer, cooked_beef ×8 to a bound butcher, and cake ×1 to a bound cleric; each recipient must be distinct | cooldown 168,000; `H`; `core_relationship` |
| `mcaquests:monument_of_names` — **A Monument of Names** | cleric, librarian, or villager with a deceased relative | place stone_bricks ×24 and candle ×8 within 24 blocks of home-village center; deliver written_book ×1 to giver | once per village/player; `H`; +8 reputation; `core_relationship` |

`feast_of_many_tables` deliberately uses a fixed cooldown so it remains a pure core quest with identical behavior when Townstead is absent.

---

## 9. Eight new village projects

### 9.1 Shared project rules

- Every project uses village scope with `fallback_radius:64` and sponsor death behavior `transfer`.
- Donation phases pay XP to `contributors` and semantic currency to `top_contributor`.
- State-only phases pay `hearts_with_participants` to `all_participants` and reputation to `sponsor_village`; do not target an empty contributor set.
- Every Townstead project declares/derives the exact capabilities needed by its active phase and suspends that phase when they are unavailable.
- All profession workforce objectives validate each loaded track. A resident counts only when their profession has a real progressive track whose maximum tier reaches `minimum_tier`.
- Project progress and banked rewards remain exact-once across restart and `/reload` under the existing `ProjectSavedData` rules.

### 9.2 Townstead projects (6)

#### `mcaquests:townstead_four_seasons_larder` — Four Seasons Larder

**Sponsor:** farmer or butcher  
**Category:** community  
**Capabilities:** `V,N,C`  
**Project-complete reputation:** 20

| Phase key | Objectives | Rewards |
|---|---|---|
| `seed_chest` | donate wheat_seeds ×128, carrot ×64, potato ×64 | 30 XP contributors; currency top contributor |
| `summer_table` | donate bread ×96, honey_bottle ×24, cooked_chicken ×32 | 34 XP contributors; currency top contributor |
| `autumn_stores` | donate wheat ×128, cooked_beef ×48, cooked_porkchop ×48 | 40 XP contributors; currency top contributor |
| `winter_wellbeing` | resident wellbeing: observed 8, fraction 0.80, hunger 65, thirst 12, energy 10, not collapsed, loaded fraction 0.60, hold 4,800 | 14 hearts all participants; +12 village reputation |

The name is thematic; phases do not hard-lock to seasons, so a server with a very short/custom calendar cannot strand a community project. Dialogue should still acknowledge the live season when `READ_CALENDAR` is available.

#### `mcaquests:townstead_harbor_quarter` — Harbor Quarter

**Sponsor:** fisherman or cartographer  
**Category:** infrastructure  
**Capabilities:** `B,SP`  
**Project-complete reputation:** 22

| Phase key | Objectives | Rewards |
|---|---|---|
| `timber_and_iron` | donate oak_planks ×128, oak_log ×48, chain ×32, lantern ×16 | 34 XP contributors; currency top contributor |
| `working_dock` | village has dock level 2+ ×1; nautical spirit grows by 5 points from phase activation | 8 hearts all participants; +8 reputation |
| `deep_harbor` | village has dock level 3+ ×1; nautical spirit grows by 10 points from phase activation | 14 hearts all participants; +12 reputation |

If the required dock tier already exists when a phase activates, its building objective is complete, but the spirit delta is still measured from activation and must be earned elsewhere. This is intentional; it prevents an old harbor from completing a new civic drive for free.

#### `mcaquests:townstead_civic_quarter` — The Civic Quarter

**Sponsor:** cleric, librarian, or mason  
**Category:** infrastructure  
**Capabilities:** `B,SP`  
**Project-complete reputation:** 24

| Phase key | Objectives | Rewards |
|---|---|---|
| `public_fund` | donate stone_bricks ×128, glass ×48, book ×24, golden_carrot ×24 | 36 XP contributors; currency top contributor |
| `care_and_record` | village has infirmary ×1 and bookkeeper ×1; scholar spirit reaches tier 1 | 14 hearts all participants; +12 reputation |
| `civic_identity` | scholar spirit grows by 10 points from phase activation; at least five contributing buildings total | 14 hearts all participants; +12 reputation |

Do not assume the two named buildings alone can reach scholar tier 1. The card should list other loaded scholar contributors (for 0.7.6: library, cartographer, music store, graveyard) so the project is a planning goal rather than a mystery.

#### `mcaquests:townstead_apprentices_guild` — The Apprentices’ Guild

**Sponsor:** farmer, shepherd, butcher, librarian, or mason  
**Category:** community  
**Capabilities:** `V,P,PX,B`  
**Project-complete reputation:** 24

| Phase key | Objectives | Rewards |
|---|---|---|
| `books_tools_meals` | donate book ×24, clock ×6, iron_ingot ×48, bread ×64 | 34 XP contributors; currency top contributor |
| `trusted_workers` | three residents across farmer/shepherd/butcher have progressive tracks and tier 2+ | 10 hearts all participants; +10 reputation |
| `one_master_many_hands` | one supported resident reaches tier 3+; blacksmith ×1 and library ×1 registered | 14 hearts all participants; +12 reputation |

Do not include fisherman or leatherworker in the bundled workforce list unless the loaded registry adds valid tracks; the UI may show additional eligible professions dynamically but the JSON’s guaranteed baseline remains the supported three.

#### `mcaquests:townstead_rest_for_all` — Rest for All

**Sponsor:** cleric, farmer, or shepherd  
**Category:** community  
**Capabilities:** `V,N`  
**Project-complete reputation:** 18

| Phase key | Objectives | Rewards |
|---|---|---|
| `beds_and_supper` | donate white_bed ×12, bread ×96, potion ×24 | 30 XP contributors; currency top contributor |
| `a_restored_village` | wellbeing: observed 10, fraction 0.80, hunger 60, thirst 12, energy 11, not collapsed, loaded fraction 0.60, hold 4,800 | 14 hearts all participants; +14 reputation |

If generic potion stacks cannot reliably help Townstead thirst, change that donation to honey bottles or another item Townstead 0.7.6 actually consumes; do not claim the donation itself restores thirst. The state objective is the proof.

#### `mcaquests:townstead_known_far_and_wide` — Known Far and Wide

**Sponsor:** cartographer, cleric, or unemployed adult  
**Category:** community  
**Capabilities:** `B,SP`  
**Project-complete reputation:** 22

| Phase key | Objectives | Rewards |
|---|---|---|
| `welcome_fund` | donate white_bed ×12, lantern ×24, cake ×6, filled_map ×6 | 34 XP contributors; currency top contributor |
| `open_our_doors` | inn ×1 registered; tourism grows by 2 points from phase activation | 8 hearts all participants; +8 reputation |
| `music_and_market` | music_store ×1 registered; commercial grows by 5 points from phase activation; project talk objective records 12 villager conversations | 14 hearts all participants; +12 reputation |

This project grows the reachable low-point tourism identity without asking for tourism tier 1, which would require an unreasonable number of 0.7.6 inns.

### 9.3 Core projects (2)

#### `mcaquests:roads_and_lanterns` — Roads and Lanterns

**Sponsor:** cartographer or mason  
**Category:** infrastructure  
**Project-complete reputation:** 18

| Phase key | Objectives | Rewards |
|---|---|---|
| `road_fund` | donate gravel ×256, stone_bricks ×128, lantern ×32 | 34 XP contributors; currency top contributor |
| `light_the_roads` | project-place gravel ×192 and lantern ×24 inside the village scope; count unique positions | 10 hearts all participants; +10 reputation |
| `road_opening` | talk to cartographer ×6 and farmer ×6 through project talk progress | 8 hearts all participants; +8 reputation |

#### `mcaquests:walls_before_winter` — Walls before Winter

**Sponsor:** guard, mason, armorer, or weaponsmith  
**Category:** defense  
**Project-complete reputation:** 20

| Phase key | Objectives | Rewards |
|---|---|---|
| `stone_and_iron` | donate cobblestone ×256, iron_ingot ×64, lantern ×24 | 36 XP contributors; currency top contributor |
| `raise_the_wall` | project-place cobblestone_wall ×128 and lantern ×16 within village scope | 10 hearts all participants; +10 reputation |
| `prove_the_watch` | project kills: pillager ×24, zombie ×24, skeleton ×16 inside village scope | 14 hearts all participants; +12 reputation |

---

## 10. Ten new living-village situations

### 10.1 Situation rules

- All situation offers include `failed` dialogue and use the existing dynamic offer lifecycle.
- Emergency offers use priority 8–10 and should temporarily outrank ordinary personal quests.
- A transition signal’s subject/building/village is frozen into the situation instance. Offer selection must prefer that subject as giver when the giver spec allows it.
- Failure reputation is modest. A player should not be punished for an offer they never accepted; apply negative outcomes only under the existing accepted/active situation semantics.
- Townstead situations contain `townstead_available` gates for the exact capabilities in the table.

### 10.2 Townstead situations (7)

| ID / title | Trigger / timing | Offer and objectives | Outcome |
|---|---|---|---|
| `mcaquests:townstead_first_frost` — **The First Frost** | calendar transition to winter; village scope; duration 24,000; cooldown one Townstead year or fallback 96,000 | farmer/butcher/cleric; donate bread ×24, coal ×24, cooked_beef ×16 to giver inventory; `healthy(5, 0.70, hunger:55, thirst:10, energy:8, hold:600)`; caps `V,N,C` | success +14 rep; failure −5; `H` |
| `mcaquests:townstead_spring_planting` — **Spring Planting** | calendar transition to spring; village; duration 24,000; one-year cooldown | farmer; deliver wheat_seeds ×48, carrot ×24, potato ×24; complete one work shift at 0.55 coverage; caps `V,S,C` | success +12; failure −4; `M` |
| `mcaquests:townstead_coming_of_age` — **A Place among Adults** | villager canonical-stage transition from child to adult; family scope; duration 24,000; once per subject | transitioned adult is preferred giver; deliver compass ×1 and book ×1 to their inventory; accompany them to workstation or village center; caps `V,R` | success +10; failure 0; `M` |
| `mcaquests:townstead_retirement_feast` — **The Last Shift Feast** | `senior:false → true`; family scope; duration 36,000; once per subject | new senior, spouse, or child giver; deliver cake ×1 and cooked_chicken ×8; escort senior to bed/home; protect 1,200 ticks; caps `V,C` | success +12; failure 0; `M` |
| `mcaquests:townstead_broken_routine` — **The Broken Routine** | 5+ observed residents, 50% off schedule for 1,200 ticks; recovery arm below 35%; village; duration 24,000; cooldown 48,000 | cleric/farmer; restore giver to on-schedule for 600 ticks; `healthy(5, 0.65, hunger:45, thirst:9, energy:8, hold:600)`; caps `V,N,S,C` | success +14; failure −6; `H` |
| `mcaquests:townstead_new_harbor` — **A Harbor Opens** | dock crosses into level 2+; village; duration 24,000; cooldown 72,000 | fisherman/cartographer; place lantern ×6 and chain ×8 at signal dock; fish cod ×8; caps `V,B` | success +12; failure −4; `M` |
| `mcaquests:townstead_crossroads_festival` — **The Crossroads Festival** | spirit classification transitions into `blend` or `mixed`, with tier 1+; village; duration 36,000; cooldown 96,000 | cleric/librarian/unemployed; deliver cake ×3, bread ×24, lantern ×8; complete trades ×8; caps `V,SP` | success +14; failure −4; `M` |

Extend the existing Townstead spirit trigger with optional `from_classification`, `to_classification`, and `transition_only`. First sighting seeds state. `community_identity` continues to use its old minimum-tier behavior unchanged.

### 10.3 Core situations (3)

| ID / title | Trigger / timing | Offer and objectives | Outcome |
|---|---|---|---|
| `mcaquests:caravan_stranded_at_night` — **Stranded after Sundown** | `villager_stranded`: 96+ blocks outside home border, night, held 600 ticks; villager scope; duration 18,000; cooldown 72,000 | signal subject or guard giver; lead subject home, `min_journey:64`, wait distance 8; protect 1,200 ticks; fail on subject death after staging | success +12; failure −6; `H` |
| `mcaquests:monster_in_the_cellar` — **Something below the Floor** | `hostiles_near_home`: 3+ common undead within 16 blocks of a bed for 200 ticks; family scope; duration 12,000; cooldown 48,000 | resident/guard giver; defeat entity tag `mcaquests:common_undead` ×6 within 24 blocks of frozen bed; protect resident 600 ticks | success +10; failure −5; `M` |
| `mcaquests:lost_survey_party` — **The Survey Party Did Not Return** | existing `missing_kin` signal for `any`, with cartographer/adventurer family context; family scope; duration 48,000; cooldown 96,000 | cartographer or adult relative giver; `find_missing_relative` with relation `any`, structure tag `mcaquests:trail_ruins`, minimum distance 128, discover radius 24; return to giver | success +14; failure −6; `H` |

Add `mcaquests:common_undead` as an entity-type tag containing zombie-family and skeleton-family enemies that can actually spawn in the target version. Do not include bosses or passive undead.

---

## 11. Titles, narrative, and localization

### 11.1 Seven new granted titles

Create title definitions and locale entries for:

| ID | English display | Granted by |
|---|---|---|
| `mcaquests:keeper_of_seasons` | Keeper of Seasons | Seasons of the Soil finale |
| `mcaquests:harborhand` | Harborhand | Harbor of Hands finale |
| `mcaquests:trusted_hand` | Trusted Hand | Apprenticeship Pact finale |
| `mcaquests:village_chronicler` | Village Chronicler | A Village with a Name finale |
| `mcaquests:roadwarden` | Roadwarden | Broken Road finale |
| `mcaquests:mender_of_kin` | Mender of Kin | Ashen Remedy finale |
| `mcaquests:bellwarden` | Bellwarden | Bell at Dawn finale |

Use village scope for all seven. A player may earn the same conceptual title in more than one village if existing title storage supports village-scoped ownership; the journal should show the currently viewed village’s grant.

### 11.2 Dialogue voice rules

Each personal quest ships `offer`, `accept`, `decline`, `in_progress`, `ready`, and `complete`. Each situation additionally ships `failed`. Project phases ship `offer` and `in_progress` at minimum. Follow these rules:

- The giver speaks about a concrete state the card can prove: “our dock has outgrown its lights,” not “something feels different.”
- Townstead context changes the line. A hungry villager asks for a meal, not “eight bread”; a senior speaks about memory, not a numerical age.
- Chain completion lines foreshadow the next stage and, for seasonal arcs, name the season.
- Avoid generic gratitude repeated across a chain. Each completion should acknowledge what changed in the world.
- Do not claim an item was eaten, drunk, equipped, or used merely because it entered an inventory. Say it was stocked or handed over unless live state proves consumption.
- Do not claim that a Townstead profession tier is an official guild rank unless Townstead’s loaded definition names it that way.
- Root and heritage dialogue may mention family history, origin, tradition, or records. It must never rank, stereotype, shame, romanticize “purity,” or predict behavior from ancestry/genetics.
- Senior/coming-of-age content is celebratory and autonomy-respecting. It should not imply that seniors are helpless or new adults are children.
- Failed situation dialogue describes the unresolved problem without blaming the player for declining or never seeing the offer.

### 11.3 Localization budget and parity

Minimum new content keys per locale:

| Source | Calculation | Keys |
|---|---:|---:|
| Personal quests | 72 × (title + 6 dialogue states) | 504 |
| Projects | 8 titles + 24 phases × 2 lines | 56 |
| Situations | 10 × (offer title + 7 dialogue states) | 80 |
| Titles | 7 × (name + description) | 14 |
| **Minimum content subtotal** | | **654** |

Add system keys for new objective descriptions, unavailable reasons, calendar periods, anchors, context lines, validation messages exposed to users, and configuration comments. The exact total will exceed 654.

`en_us.json` and `pt_br.json` must have identical key sets and matching format placeholders. Preserve positional placeholders (`%1$s`, `%2$s`) so translators can reorder arguments. The build must fail on missing keys, extra keys, invalid JSON, placeholder mismatch, or untranslated English copied into Portuguese where the existing parity test detects it.

### 11.4 Content naming rules

- JSON filenames are the ID path without the `mcaquests:` namespace.
- Keep the `townstead_` prefix on every Townstead-only definition.
- Chain keys are plain snake case and stable across localization.
- Avoid duplicating existing titles such as “Deep Water Days”; new harbor titles are intentionally distinct.
- Translation key format remains `mcaquests.quest.<id_path>.*`, `mcaquests.project.<id_path>.*`, and `mcaquests.situation.<id_path>.*` according to current conventions.

---

## 12. Balance and exploit-resistance

### 12.1 Pacing

- Keep default personal offers at three. Diversify through groups rather than increasing screen density.
- Need emergencies may recur after 48,000–72,000 ticks but cannot appear more than once per offer reroll due to grouping.
- Identity commissions repeat once per loaded Townstead season. A giver cannot offer the same commission twice in one season.
- Long travel and structure quests are once-per-giver or have cooldowns of at least four Minecraft days.
- A four-stage chain should take roughly 45–120 minutes of active play excluding intentional season/profession progression waits.
- Progression stages respect Townstead’s daily XP cap. No bundled reward bypasses it.
- Spirit tier 1 is suitable for personal arcs. Tier 2 belongs in a hard capstone/project. Do not require tiers 3–5 in this release.

### 12.2 Reward policy

- Currency always uses the semantic currency reward so server economy configuration remains authoritative.
- Hearts reward relationship labor; village reputation rewards civic labor. Do not replace one with the other.
- Routine deliveries use `E`; a sustained shift, small build, or local trip uses `M`; dangerous travel, population-wide state, large build, profession capstone, or multi-objective defense uses `H`.
- Item rewards should be rare, thematic, and not exceed the expected input value by enough to create a conversion loop.
- Do not grant Townstead need mutations as the primary completion reward for a quest whose premise is restoring that same need; that makes the causal story backwards.
- `townstead_reaction` is flavor. A missing reaction capability never invalidates earned player rewards.

### 12.3 Acceptance-time anti-triviality

Retain `QuestObjective.isTriviallySatisfied` filtering and implement it for new objectives:

- `townstead_schedule_streak` is never trivially complete because it has no credited future shift at acceptance.
- A registered-building objective with `require_new_or_upgraded:true` is never satisfied by a pre-existing building.
- A building-anchor reach/escort objective uses the existing `min_journey` check.
- A period quest completed in the live period is ineligible before objective evaluation.
- A profession delta whose remaining track cannot accommodate the delta is ineligible.
- A spirit delta freezes its baseline and cannot count pre-acceptance buildings.

### 12.4 Multiplayer ownership

- Personal objectives remain owned by the accepting player even when Townstead state changes because another player helped. That is acceptable for state-observation objectives and should be described as communal assistance, not exclusive credit.
- Project donations retain contributor accounting. State-only phases reward all prior participants, never a nonexistent “top contributor” for passive state.
- Situation success resolves once per situation instance; two simultaneous turn-in packets cannot pay twice.
- A missing relative or signal subject can materialize/bind only once globally under existing UUID safeguards.

---

## 13. Normative JSON examples

These examples show the intended final data shape. They may be copied into codec tests.

### 13.1 Building-anchored chain stage

```json
{
  "format_version": 1,
  "id": "mcaquests:townstead_harbor_lantern_line",
  "enabled": true,
  "weight": 6,
  "priority": 6,
  "offer_group": "townstead_work",
  "category": "townstead",
  "difficulty": "hard",
  "repeat": { "type": "once" },
  "giver": {
    "professions": ["minecraft:fisherman"],
    "adult_only": true
  },
  "title": {
    "translate": "mcaquests.quest.townstead_harbor_lantern_line.title"
  },
  "dialogue": {
    "offer": { "translate": "mcaquests.quest.townstead_harbor_lantern_line.dialogue.offer" },
    "accept": { "translate": "mcaquests.quest.townstead_harbor_lantern_line.dialogue.accept" },
    "decline": { "translate": "mcaquests.quest.townstead_harbor_lantern_line.dialogue.decline" },
    "in_progress": { "translate": "mcaquests.quest.townstead_harbor_lantern_line.dialogue.in_progress" },
    "ready": { "translate": "mcaquests.quest.townstead_harbor_lantern_line.dialogue.ready" },
    "complete": { "translate": "mcaquests.quest.townstead_harbor_lantern_line.dialogue.complete" }
  },
  "objectives": [
    {
      "type": "mcaquests:build_near_location",
      "block": "minecraft:lantern",
      "location": {
        "anchor": "townstead_building",
        "building_type": "dock",
        "minimum_level": 2,
        "selection": "nearest_to_giver"
      },
      "radius": 16,
      "count": 6
    },
    {
      "type": "mcaquests:build_near_location",
      "block": "minecraft:chain",
      "location": {
        "anchor": "townstead_building",
        "building_type": "dock",
        "minimum_level": 2,
        "selection": "nearest_to_giver"
      },
      "radius": 16,
      "count": 12
    }
  ],
  "rewards": [
    { "type": "mcaquests:currency" },
    { "type": "mcaquests:xp", "amount": 55 },
    { "type": "mcaquests:hearts", "amount": 14 }
  ],
  "conditions": {
    "all_of": [
      {
        "type": "mcaquests:townstead_available",
        "capabilities": ["READ_VILLAGER", "READ_BUILDING"]
      },
      {
        "type": "mcaquests:townstead_building",
        "building_type": "dock",
        "minimum_level": 2,
        "count": 1
      }
    ]
  },
  "chain": {
    "chain": "harbor_of_hands",
    "stage": 3,
    "stage_total": 4,
    "relationship_arc": {
      "translate": "mcaquests.chain.harbor_of_hands.name"
    },
    "chapter": {
      "translate": "mcaquests.chain.harbor_of_hands.stage_3"
    },
    "prerequisites": ["mcaquests:townstead_harbor_working_tide"],
    "unlocks": ["mcaquests:townstead_harbor_deep_water"]
  }
}
```

Both objectives must resolve the same frozen dock. Implement anchor binding by a normalized anchor fingerprint shared within the active quest so identical anchor specs cannot drift between two buildings.

### 13.2 Calendar-relative standalone

```json
{
  "format_version": 1,
  "id": "mcaquests:townstead_spring_bells_and_blossoms",
  "enabled": true,
  "weight": 8,
  "priority": 5,
  "offer_group": "townstead_season",
  "category": "townstead",
  "difficulty": "medium",
  "repeat": {
    "type": "period",
    "period": "year",
    "scope": "giver",
    "fallback_cooldown_ticks": 96000
  },
  "giver": {
    "professions": ["minecraft:farmer", "minecraft:cleric"],
    "adult_only": true
  },
  "title": {
    "translate": "mcaquests.quest.townstead_spring_bells_and_blossoms.title"
  },
  "dialogue": {
    "offer": { "translate": "mcaquests.quest.townstead_spring_bells_and_blossoms.dialogue.offer" },
    "accept": { "translate": "mcaquests.quest.townstead_spring_bells_and_blossoms.dialogue.accept" },
    "decline": { "translate": "mcaquests.quest.townstead_spring_bells_and_blossoms.dialogue.decline" },
    "in_progress": { "translate": "mcaquests.quest.townstead_spring_bells_and_blossoms.dialogue.in_progress" },
    "ready": { "translate": "mcaquests.quest.townstead_spring_bells_and_blossoms.dialogue.ready" },
    "complete": { "translate": "mcaquests.quest.townstead_spring_bells_and_blossoms.dialogue.complete" }
  },
  "objectives": [
    {
      "type": "mcaquests:build_near_location",
      "tag": "minecraft:flowers",
      "location": { "anchor": "home_village" },
      "radius": 32,
      "count": 12
    },
    {
      "type": "mcaquests:build_near_location",
      "block": "minecraft:bell",
      "location": { "anchor": "home_village" },
      "radius": 32,
      "count": 1
    }
  ],
  "rewards": [
    { "type": "mcaquests:currency" },
    { "type": "mcaquests:xp", "amount": 34 },
    { "type": "mcaquests:hearts", "amount": 8 }
  ],
  "conditions": {
    "all_of": [
      {
        "type": "mcaquests:townstead_available",
        "capabilities": ["READ_VILLAGER", "READ_CALENDAR"]
      },
      {
        "type": "mcaquests:townstead_value",
        "source": "calendar",
        "path": "season",
        "operator": "eq",
        "value": "spring"
      }
    ]
  }
}
```

The year-period history prevents a second completion in the same year; the season gate allows the quest throughout spring and does not misinterpret Townstead’s day-of-month field as a day-of-season.

### 13.3 Safe progression capstone gate

```json
{
  "all_of": [
    {
      "type": "mcaquests:townstead_available",
      "capabilities": [
        "READ_VILLAGER",
        "READ_PROFESSION",
        "READ_PROFESSION_SPEC"
      ]
    },
    {
      "type": "mcaquests:townstead_profession_track",
      "target": "giver",
      "minimum_max_tier": 3,
      "missing": false
    }
  ]
}
```

Both checks belong in the same effective eligibility gate.

### 13.4 Transition situation trigger

```json
{
  "format_version": 1,
  "id": "mcaquests:townstead_first_frost",
  "scope": "village",
  "duration_ticks": 24000,
  "cooldown_ticks": 96000,
  "trigger": {
    "type": "mcaquests:townstead_calendar_transition",
    "transition": "season",
    "to": "winter"
  },
  "outcomes": {
    "success": { "reputation": 14 },
    "failure": { "reputation": -5 }
  },
  "offer": {
    "weight": 12,
    "priority": 9,
    "offer_group": "townstead_season",
    "difficulty": "hard",
    "giver": {
      "professions": ["minecraft:farmer", "minecraft:butcher", "minecraft:cleric"],
      "adult_only": true
    },
    "title": {
      "translate": "mcaquests.situation.townstead_first_frost.offer.title"
    },
    "dialogue": {
      "offer": { "translate": "mcaquests.situation.townstead_first_frost.offer.dialogue.offer" },
      "accept": { "translate": "mcaquests.situation.townstead_first_frost.offer.dialogue.accept" },
      "decline": { "translate": "mcaquests.situation.townstead_first_frost.offer.dialogue.decline" },
      "in_progress": { "translate": "mcaquests.situation.townstead_first_frost.offer.dialogue.in_progress" },
      "ready": { "translate": "mcaquests.situation.townstead_first_frost.offer.dialogue.ready" },
      "complete": { "translate": "mcaquests.situation.townstead_first_frost.offer.dialogue.complete" },
      "failed": { "translate": "mcaquests.situation.townstead_first_frost.offer.dialogue.failed" }
    },
    "objectives": [
      {
        "type": "mcaquests:item_delivery",
        "item": "minecraft:bread",
        "count": 24,
        "destination": {
          "type": "townstead_villager_inventory",
          "target": "giver"
        }
      },
      {
        "type": "mcaquests:item_delivery",
        "item": "minecraft:coal",
        "count": 24,
        "destination": {
          "type": "townstead_villager_inventory",
          "target": "giver"
        }
      },
      {
        "type": "mcaquests:item_delivery",
        "item": "minecraft:cooked_beef",
        "count": 16,
        "destination": {
          "type": "townstead_villager_inventory",
          "target": "giver"
        }
      },
      {
        "type": "mcaquests:townstead_healthy_residents",
        "minimum_observed": 5,
        "minimum_fraction": 0.70,
        "minimum_loaded_fraction": 0.50,
        "hunger_min": 55,
        "thirst_min": 10,
        "energy_min": 8,
        "require_not_collapsed": true,
        "hold_ticks": 600
      }
    ],
    "rewards": [
      { "type": "mcaquests:currency" },
      { "type": "mcaquests:xp", "amount": 55 },
      { "type": "mcaquests:hearts", "amount": 14 }
    ],
    "conditions": {
      "all_of": [
        {
          "type": "mcaquests:townstead_available",
          "capabilities": ["READ_VILLAGER", "READ_NEEDS", "READ_CALENDAR"]
        }
      ]
    }
  }
}
```

---

## 14. File-level implementation map

The coding agent should follow existing package boundaries and avoid broad rewrites. The paths below are relative to the MCAQuests repository root.

### 14.1 Townstead bridge and views

| File | Change |
|---|---|
| `src/main/java/dev/otectus/mcaquests/compat/TownsteadCapability.java` | Add `READ_PROFESSION_SPEC` and `READ_SKILL_REGISTRY`. |
| `.../compat/TownsteadBridge.java` | Add optional profession-track lookup, skill-existence lookup, and calendar period-token helpers. Use Java/Minecraft/MCAQ-owned types only. |
| `.../compat/NoopTownsteadBridge.java` | Return empty/false for every new method. |
| `.../compat/TownsteadProfessionTrackView.java` | New immutable MCAQ-owned view described in §5.1. |
| `.../compat/TownsteadCompat.java` | Public guarded accessors; capability checks; status/probe output. |
| `.../compat/TownsteadEvaluation.java` | Memoize profession specs, calendar period tokens, normalized building candidates, and spirit contributor lookups per evaluation pass. |
| `.../compat/TownsteadPaths.java` | Add documented derived track metadata paths only when an unambiguous upstream value exists. |
| `.../compat/townstead/TownsteadBinding.java` | Bind profession spec/skill registry by member name and arity, never by Townstead parameter class. Report capabilities independently. |
| `.../compat/townstead/TownsteadHandles.java` | Store new handles as erased `Object`-based method handles. |
| `.../compat/townstead/ReflectiveTownsteadBridge.java` | Convert reflected progression/calendar/building values into MCAQ-owned records; contain all reflective failure handling. |
| `.../command/TownsteadCompatCommands.java` | Show the two new capabilities; snapshot profession max tier/XP and calendar period token; probe each call against a nearby villager/village. |

The existing build-time prohibition against Townstead static links must cover all new class files and generic signatures.

### 14.2 Quest codecs, objectives, targeting, and history

| File | Change |
|---|---|
| `.../quest/condition/ConditionTypes.java` | Register `townstead_profession_track`. |
| `.../quest/condition/leaf/TownsteadProfessionTrackCondition.java` | New codec/evaluator/validator. |
| `.../quest/objective/ObjectiveTypes.java` | Register `townstead_schedule_streak`. |
| `.../quest/objective/TownsteadScheduleStreakObjective.java` | New objective with persistence and unavailable-state behavior from §5.3. |
| `.../quest/objective/TownsteadProfessionProgressObjective.java` | Consult live track view before offer and while active; preserve current frozen-profession semantics. |
| `.../quest/objective/TownsteadHealthyResidentsObjective.java` | Add thirst and observation-coverage rules. |
| `.../quest/target/LocationAnchor.java` | Add `townstead_building` and `nearest_other_village` variants, validation, deterministic freeze, and shared fingerprint. |
| `.../quest/target/TownsteadTargetResolver.java` | Reuse cached building snapshots; do not reflect independently. |
| `.../quest/objective/DeliveryDestination.java` | Permit recipient destination context; reuse capacity simulation/commit API. |
| `.../quest/objective/DeliverToVillagerObjective.java` | Add optional destination and exact-once inventory transfer. |
| `.../quest/RepeatRule.java` | Add additive `period` rule and fallback cooldown. |
| `.../state/QuestHistory.java` | Persist completion period token per quest/scope without changing old entries. |
| `.../quest/OfferShaping.java` | Add optional `offer_group`; keep it inside the existing map codec tail to avoid expanding `QuestDefinition`’s already-full `RecordCodecBuilder` group. |
| `.../quest/QuestManager.java` | Group-diverse weighted selection within priority tiers; track resolver availability before offering. |
| `.../quest/TownsteadContextLines.java` | Add season, shift, life, building, spirit, and track context lines only when referenced. |
| `.../data/ObjectiveValidator.java` | Static semantic checks for new objective/anchor/destination fields. |
| `.../data/ProgressionValidator.java` or new `TownsteadContentValidator.java` | Registry-aware post-reload achievability pass. |

If frozen anchors currently live only in objective-specific progress, extract a small MCAQ-owned `FrozenLocation` codec/serializer rather than duplicating NBT layouts across six objective classes.

### 14.3 Projects

| File | Change |
|---|---|
| `.../project/objective/TownsteadResidentWellbeingProjectObjective.java` | Add thirst, collapse, and loaded-fraction fields. |
| `.../project/objective/TownsteadWorkforceProjectObjective.java` | Count only provably progressive tracks that can reach `minimum_tier`; unavailable when registry cannot be read. |
| `.../project/objective/TownsteadSpiritProjectObjective.java` | Validate spirit ID and contributor reachability; retain frozen delta baseline. |
| `.../project/objective/TownsteadBuildingProjectObjective.java` | Normalize building aliases and expose the matching families in descriptions. |
| `.../project/data/ProjectValidator.java` | Capability, workforce, contributor, state-phase reward, and loaded-spirit checks. |

Do not add contribution credit for passive Townstead state. Preserve the existing distinction between donated/event progress and observed world state.

### 14.4 Situations

| File | Change |
|---|---|
| `.../quest/situation/SituationTriggerTypes.java` | Register calendar transition, life transition, schedule disruption, stranded villager, and hostiles-near-home triggers. |
| `.../quest/situation/trigger/TownsteadCalendarTransitionTrigger.java` | New codec and match logic. |
| `.../quest/situation/trigger/TownsteadLifeTransitionTrigger.java` | New codec and subject binding. |
| `.../quest/situation/trigger/TownsteadScheduleDisruptionTrigger.java` | New threshold/hysteresis codec. |
| `.../quest/situation/trigger/TownsteadSpiritTrigger.java` | Add optional classification transition fields without changing old minimum-tier behavior. |
| `.../quest/situation/trigger/VillagerStrandedTrigger.java` | New MCA-backed signal contract. |
| `.../quest/situation/trigger/HostilesNearHomeTrigger.java` | New server-side hostile detector contract. |
| `.../quest/situation/TownsteadSituationDetector.java` | Sample new Townstead transitions with shared cache and bounded cadence. |
| `.../quest/situation/SituationDetectors.java` | Schedule the two new core detectors without full-world entity scans. |
| `.../quest/situation/state/TownsteadSignalStateSavedData.java` | Persist first-seen baselines, subject values, period tokens, and schedule hysteresis. |
| `.../data/SituationDataLoader.java` | Registry-aware validation for transition values and capabilities. |

### 14.5 Resources and documentation

Add definitions under existing directories:

```text
src/main/resources/data/mcaquests/mcaquests/quests/townstead/
src/main/resources/data/mcaquests/mcaquests/quests/
src/main/resources/data/mcaquests/mcaquests/projects/townstead/
src/main/resources/data/mcaquests/mcaquests/projects/
src/main/resources/data/mcaquests/mcaquests/situations/townstead/
src/main/resources/data/mcaquests/mcaquests/situations/
src/main/resources/data/mcaquests/mcaquests/titles/
src/main/resources/data/mcaquests/tags/items/
src/main/resources/data/mcaquests/tags/entity_types/
src/main/resources/data/mcaquests/tags/worldgen/structure/
```

Update:

- `src/main/resources/assets/mcaquests/lang/en_us.json`
- `src/main/resources/assets/mcaquests/lang/pt_br.json`
- `DATAPACK.md` for all additive fields/types/examples
- `TOWNSTEAD.md` for new capabilities, tracks, schedule streaks, calendar repeats, buildings, situations, and truthful 0.7.6 profession support
- `CONFIG.md` for sub-toggles and offer groups
- `README.md` counts/features
- `CHANGELOG.md` with compatibility repairs called out before new content

---

## 15. Implementation sequence and commits

Keep commits reviewable and avoid mixing hundreds of locale lines with engine behavior.

### Milestone 0 — compatibility repairs

1. Add regression tests proving fisherman/leatherworker 0.7.6 tracks are non-progressive.
2. Implement track introspection and validation.
3. Repair the seven personal definitions and `master_artisan` listed in §4.
4. Update `townstead_a_working_village` so its bundled workforce list contains farmer, shepherd, and butcher; dynamic valid tracks may still count at runtime.

**Exit:** no current built-in can become unwinnable under Townstead 0.7.6 solely because a progression track does not exist.

### Milestone 1 — new primitives

Implement schedule streak, building/other-village anchors, recipient delivery destinations, calendar periods, wellbeing coverage, and offer groups. Add codecs, NBT round trips, descriptions, validators, and docs before content.

**Exit:** each primitive has a minimal test datapack and survives accept → save → restart → progress → complete.

### Milestone 2 — signals

Implement calendar/life/schedule/spirit transitions and the two core detectors. Add seed-without-fire and hysteresis tests.

**Exit:** no signal replays on first sight, restart, chunk reload, or `/reload`.

### Milestone 3 — Townstead quests

Add the six arcs first, then standalones. Land English text with mechanics; add Portuguese in a dedicated follow-up commit without allowing main to stay red.

**Exit:** exactly 48 new Townstead personal definitions, every one capability-gated and content-valid under 0.7.6.

### Milestone 4 — core quests

Add three arcs, 12 standalones, and custom tags.

**Exit:** exactly 24 new definitions pass tests in a runtime where Townstead classes are completely absent.

### Milestone 5 — projects and situations

Add six Townstead/two core projects and seven Townstead/three core situations.

**Exit:** exactly 8 projects and 10 situations; contribution/reward and trigger persistence tests pass.

### Milestone 6 — balance, localization, docs, release QA

Run manual playtests, tune weights/counts without changing the catalog total, complete both locales, update public docs, and build the release jar.

**Exit:** every acceptance criterion in §19 is checked.

---

## 16. Save, datapack, network, and optional-mod compatibility

### 16.1 Datapacks

- Keep `format_version:1`; all new fields have defaults.
- Old quests without `offer_group` remain ungrouped and select as before.
- Old repeat rules decode unchanged.
- Old `deliver_to_villager` definitions preserve consume-on-interaction behavior.
- Old location anchors preserve their exact codec and arrival semantics.
- Unknown optional Townstead types still register under `NoopTownsteadBridge`, allowing a pack to parse without Townstead.

### 16.2 Saved state

- New NBT keys are optional on read.
- Legacy active schedule objectives do not exist and need no migration.
- A period-history entry is additive alongside existing completion timestamps/counters.
- Frozen building anchors include a schema/version key so future fields can be ignored.
- Transition saved data seeds missing fields from live state without firing.
- Never discard an active quest because its new adapter capability is missing; expose unavailable state and keep abandonment possible.

### 16.3 Network

Avoid a protocol bump if quest cards already send resolved text/objective lines generically. `offer_group` is server-only and need not cross the wire. New context lines should use existing component/string lists. If any packet record shape must change, increment the protocol once, add encode/decode tests, and reject mismatched clients clearly.

### 16.4 Townstead removal and restoration

Test this exact sequence:

1. Accept a schedule streak, building anchor quest, seasonal quest, project, and situation with Townstead present.
2. Save and stop cleanly.
3. Remove Townstead and restart.
4. Confirm definitions parse, ordinary quests play, Townstead offers vanish, active Townstead work shows unavailable, deadlines stop, projects/situations do not resolve, and all are abandonable.
5. Restore the same Townstead version.
6. Confirm frozen baselines/anchors/period tokens remain, progress resumes, and suspended time is excluded from deadlines.

### 16.5 MCA compatibility

MCA exposes no stable public API for all required internals. Keep new village/residency/family/building reads behind the existing `McaCompat` binding and its supported package-root probes. No new source file outside `compat.mca` may mention an MCA type in bytecode, generic signatures, annotations, or descriptors.

---

## 17. Performance requirements

The current Townstead documentation targets an average scan below 1 ms, no scan above 5 ms in normal play, and more cache hits than bridge reads. Preserve those targets with the larger catalog.

- Query eligibility only when offers reroll/open, not every server tick.
- Poll only active Townstead objectives and active project phases.
- Share one `TownsteadEvaluation` per player/village/tick pass.
- Resolve/freeze building and other-village anchors at acceptance; do not repeatedly search village registries afterward.
- Cache profession specs by profession ID for a reload generation, not per villager/tick.
- Sample schedules at a bounded cadence (recommended every 20 ticks) and use elapsed time between samples; do not reflect every game tick.
- Spread village-wide need/schedule scans round-robin across ticks. Never scan every loaded villager once per active quest.
- Core hostile detectors query bounded AABBs around known beds/village centers. Never scan all entities in a dimension.
- Transition state stores compact primitive snapshots and UUID keys; purge only confirmed-dead/removed subjects on a slow maintenance cadence.
- Cap credited shift-key sets by objective requirement.

Performance test scene: dedicated server, 100 loaded MCA residents across four villages, 10 players, each with one active schedule quest, one needs quest, and one ordinary quest; four active Townstead projects and seven armed situation detectors. Capture average, p95, and max MCAQ tick time for ten minutes. Acceptance: average Townstead integration work <1 ms/tick, p95 <2 ms, and no unexplained >5 ms spike attributable to MCAQ after warm-up.

---

## 18. Verification plan

### 18.1 Automated unit/codec tests

Add or extend tests for:

1. **Content totals:** 262 quest JSONs, 21 projects, 25 situations; 73 total Townstead quests; exactly 48/6/7 newly added Townstead definitions.
2. **Built-in parsing:** every definition decodes with `format_version:1` both with a real bridge test double and the noop bridge.
3. **Locale parity:** identical key sets and positional placeholders in `en_us`/`pt_br`; at least 654 new content keys plus system keys.
4. **No static links:** scan all class constant pools for Townstead and prohibited MCA class names.
5. **Profession spec:** built-in farmer/shepherd/butcher progressive; fisherman/leatherworker non-progressive under a 0.7.6-shaped fake; data-driven override wins; default spec never passes; registry removal suspends.
6. **Skill validation:** absent ID fails built-in validation and produces one external-pack warning.
7. **Schedule streak:** first sample does not credit; coverage boundary; on-schedule requirement; miss reset/no-reset; unload unknown; duplicate shift; restart; calendar profile change; required count cap.
8. **Building anchor:** family normalization; minimum tier; deterministic nearest and tie; shared fingerprint in one quest; frozen after building removal; absent Townstead unavailable.
9. **Other-village anchor:** excludes home; border-aware arrival; deterministic tie; no destination makes offer non-trivial/ineligible rather than crashing.
10. **Recipient transfer:** full capacity, insufficient capacity, partial-stack merge, replayed packet, recipient unload, recipient death, unexpected remainder, inventory persistence.
11. **Period repeat:** week/season/year token; custom calendar lengths; year rollover; same-period denial; giver/global scope; missing calendar fallback; profile switch.
12. **Wellbeing:** thirst boundary, collapse, minimum observed, loaded fraction, hold reset, unload pause, project parity.
13. **Transitions:** first sight seeds; one crossing fires once; restart no replay; custom profile change no synthetic event; life subject UUID; schedule hysteresis; spirit classification crossing.
14. **Core signals:** stranded distance/night/hold/home return; hostiles bounded to bed/home; cooldown/throttle; unloaded subject.
15. **Offer groups:** one per group first pass; priority remains authoritative; weighted second pass; ungrouped legacy behavior; deterministic seeded picker.
16. **Achievability:** unsupported track, impossible spirit, no contributor, unknown building family, missing capability, bad season, hard delivery-only definition, duplicate recipient, missing dialogue.
17. **Chains:** every prerequisite/unlock exists, stages are contiguous 1–4, same chain key, final stage recognized, no cycles.
18. **Projects:** donation credit, passive-state no contributor, banked rewards exact-once, workforce dynamic filter, sponsor transfer.
19. **Situations:** subject/building freeze, accepted deadline, suspension, success/failure exact-once, decline/no-view no penalty.

### 18.2 Compatibility matrix

| MCA Reborn | Townstead | Expected |
|---|---|---|
| supported 7.6.x layout | absent | all 24 new core quests, 2 core projects, and 3 core situations work; no Townstead offers |
| supported 7.7.x package layout | absent | same, with successful MCA binding probe |
| supported MCA | 0.7.5 | capability-level degradation; definitions needing unavailable new capabilities hide/suspend, server remains healthy |
| supported MCA | 0.7.6 | primary full target; all guaranteed content valid |
| supported MCA | current 0.7.7 source/build | probe and regression target before release; partial feature loss remains contained |
| supported MCA | 0.7.6 + datapack fisherman track | fisherman progression content becomes eligible only when track is demonstrably reachable; bundled quest repairs remain valid |
| supported MCA | 0.7.6 + custom calendar | period tokens and season validation follow the custom profile |
| supported MCA | Townstead removed/restored | suspension/resume sequence in §16.4 |

### 18.3 Manual playtest scripts

Run on both single-player integrated server and dedicated multiplayer:

- Complete each of the nine arcs at least once using commands only to move calendar/profession state, never to complete MCAQ objectives directly.
- Play one needs emergency with a full villager inventory and verify honest refusal/no item loss.
- Accept two objectives using identical dock anchors; verify one dock and one map marker.
- Observe a work shift across logout/restart and a partially unloaded chunk.
- Change a custom Townstead season length mid-world; verify no duplicate period reward.
- Upgrade a dock while no player is beside it; verify project/situation polling notices it without a fake contributor.
- Trigger coming-of-age and senior transitions; confirm the subject is named and first-load does not produce a backlog of events.
- Run a raid through Bell at Dawn with two players attacking the same mobs; verify player credit rules and one final payout.
- Complete/abandon/fail a chain stage, kill or unload its giver, and inspect journal/history.
- Open offer menus on farmer, fisherman, cleric, guard, unemployed villager, child, senior, and infected villager; verify eligibility and offer diversity.
- Test GUI at every supported UI scale and with long Portuguese lines; no clipped objectives/rewards/context.
- Run `/mcaquests validate`, `/mcaquests compat townstead status`, `probe`, and `snapshot`; messages identify definitions, objective indices, paths, and capabilities.

### 18.4 Build commands

At minimum, the final branch must pass:

```bash
./gradlew test
./gradlew check
./gradlew build
```

Also run any repository-specific data generation/schema export task documented by the project and compare generated examples with `DATAPACK.md`.

---

## 19. Definition of done

The update is complete only when every checked item below is true.

### Scope and content

- [ ] Version is recommended as `1.5.0`; `mods.toml`, build metadata, README, and changelog agree.
- [ ] Exactly 72 new personal quest JSONs exist: 48 Townstead and 24 core.
- [ ] Exactly 8 new projects exist: 6 Townstead and 2 core.
- [ ] Exactly 10 new situations exist: 7 Townstead and 3 core.
- [ ] All nine four-stage chains have contiguous stages, giver-scoped prerequisites, and no cycles.
- [ ] The seven new titles exist and are granted by the correct finales.
- [ ] No new bundled quest uses `townstead_skill`, `MUTATE_SKILLS`, or an invented skill ID.
- [ ] No built-in goal requires magical, spiritual, mining, or natural spirit without a loaded contributor.
- [ ] At least half of medium/hard new quests include a non-possession world-state objective.

### Existing-content repair

- [ ] Fisherman/leatherworker progression objectives from 1.4.0 are removed or safely reworked.
- [ ] Unsupported fisherman/leatherworker XP rewards no longer silently no-op.
- [ ] `master_of_the_trade`, `master_artisan`, and workforce projects validate the actual loaded track.
- [ ] An external datapack that supplies a valid fisherman/leatherworker track can use it without an MCAQ code change.

### Systems

- [ ] `READ_PROFESSION_SPEC` and `READ_SKILL_REGISTRY` degrade independently.
- [ ] Schedule streaks survive unload, logout, restart, and reload without free or duplicate credit.
- [ ] Townstead building and other-village anchors freeze deterministically and share identical bindings in one quest.
- [ ] Recipient inventory delivery is full-capacity, exact-once, and replay-safe.
- [ ] Period repeats follow custom Townstead calendar profiles.
- [ ] Resident wellbeing includes thirst and adequate population observation.
- [ ] Transition signals seed without firing and do not replay.
- [ ] Offer groups diversify slots without breaking priority or legacy ungrouped packs.
- [ ] `/mcaquests validate` finds every achievability class in §5.11.

### Compatibility and safety

- [ ] The jar contains no static Townstead references.
- [ ] No new prohibited MCA static reference escapes the MCA compatibility binding.
- [ ] All definitions parse when Townstead is absent.
- [ ] Active Townstead content suspends/resumes through mod removal/restoration with deadlines paused.
- [ ] Old saves and old format-1 datapacks load without manual migration.
- [ ] Turn-in, project rewards, and situation resolution are idempotent under packet replay.
- [ ] Server-only operation succeeds on a dedicated server; no client class is loaded by common/server code.

### Quality

- [ ] `./gradlew test`, `check`, and `build` pass on Java 17.
- [ ] Content totals, codec, NBT, locale, static-link, compatibility, and scenario tests pass.
- [ ] `en_us` and `pt_br` have identical keys/placeholders and all new UI text fits supported scales.
- [ ] Performance meets §17 in the 100-resident stress scene.
- [ ] README, DATAPACK, TOWNSTEAD, CONFIG, changelog, command help, and exported schema agree.
- [ ] Every row in §§7–10 has a corresponding JSON and at least one automated content assertion.

---

## 20. Risk register

| Risk | Likely failure | Mitigation / release gate |
|---|---|---|
| Townstead reflection member moves | One new feature fails binding | Independent capabilities, erased handles, 0.7.5/0.7.6/current probes; dependent content hides/suspends only |
| Default progression masquerades as a valid track | Impossible XP/tier quest appears | Treat `{thresholds:[0], maxXp:0, dailyCap:0}` as non-progressive; test explicit built-ins and data override |
| Custom calendars omit `spring`/`winter` | Seasonal arc stage never appears | Registry-aware season validation and clear warning; content ineligible, never active/impossible |
| Building aliases drift (`butcher` vs `butcher_shop`) | Conditions/anchors disagree | One normalization table in bridge; status command prints raw and normalized family; tests cover aliases |
| Low tourism contribution | Tier target demands many inns | New built-ins use tourism point deltas, not tier 1 |
| Three offer slots become need spam | New content feels repetitive | Offer groups and priority-first diverse selection |
| Long seasonal arc giver dies | Player waits a year and loses continuity | No deadline; clear giver-death handling; chain remains per-giver by design; journal shows failed arc rather than corrupt state |
| Villager inventory full | Items disappear or quest traps player | Full insertion simulation, honest refusal, remainder return, abandonment always available |
| Schedule sampling misses unloaded shifts | False failure or free credit | Unknown-coverage state, minimum observation, bounded persistent shift keys |
| Passive project phase pays nobody | Confusing missing rewards | Contributor rewards only on contribution phases; all-participant/village rewards on state phases |
| Locale volume causes drift | Missing/incorrect dialogue | Key-generation/check tooling and parity tests in every content commit |
| Hostile detector becomes expensive | Server tick spikes | Bounded AABB around known anchors, slow cadence, per-village throttle; §17 stress gate |

---

## 21. Source anchors for implementers

These are the primary source files behind the constraints in this specification.

### MCA: Quests

- [Townstead integration contract](https://github.com/otectus/MCAQuests/blob/621b7d72a11dc55595c9280ad24d7029f67ceda8/TOWNSTEAD.md)
- [Datapack format](https://github.com/otectus/MCAQuests/blob/621b7d72a11dc55595c9280ad24d7029f67ceda8/DATAPACK.md)
- [Quest definition codec](https://github.com/otectus/MCAQuests/blob/621b7d72a11dc55595c9280ad24d7029f67ceda8/src/main/java/dev/otectus/mcaquests/quest/QuestDefinition.java)
- [Townstead bridge](https://github.com/otectus/MCAQuests/blob/621b7d72a11dc55595c9280ad24d7029f67ceda8/src/main/java/dev/otectus/mcaquests/compat/TownsteadBridge.java)
- [Reflective Townstead bridge](https://github.com/otectus/MCAQuests/blob/621b7d72a11dc55595c9280ad24d7029f67ceda8/src/main/java/dev/otectus/mcaquests/compat/townstead/ReflectiveTownsteadBridge.java)
- [Townstead evaluation cache](https://github.com/otectus/MCAQuests/blob/621b7d72a11dc55595c9280ad24d7029f67ceda8/src/main/java/dev/otectus/mcaquests/compat/TownsteadEvaluation.java)
- [Townstead profession objective](https://github.com/otectus/MCAQuests/blob/621b7d72a11dc55595c9280ad24d7029f67ceda8/src/main/java/dev/otectus/mcaquests/quest/objective/TownsteadProfessionProgressObjective.java)
- [Location anchors](https://github.com/otectus/MCAQuests/blob/621b7d72a11dc55595c9280ad24d7029f67ceda8/src/main/java/dev/otectus/mcaquests/quest/target/LocationAnchor.java)
- [Atomic delivery destinations](https://github.com/otectus/MCAQuests/blob/621b7d72a11dc55595c9280ad24d7029f67ceda8/src/main/java/dev/otectus/mcaquests/quest/objective/DeliveryDestination.java)
- [Townstead situation signal state](https://github.com/otectus/MCAQuests/blob/621b7d72a11dc55595c9280ad24d7029f67ceda8/src/main/java/dev/otectus/mcaquests/quest/situation/state/TownsteadSignalStateSavedData.java)

### Townstead 0.7.6

- [Built-in profession XP types](https://github.com/AetherianArtificer/Townstead/blob/0.7.6/src/main/java/com/aetherianartificer/townstead/villager/ProfessionXpType.java)
- [Progression resolution and default fallback](https://github.com/AetherianArtificer/Townstead/blob/0.7.6/src/main/java/com/aetherianartificer/townstead/villager/ProfessionProgressions.java)
- [Data-driven profession registry](https://github.com/AetherianArtificer/Townstead/blob/0.7.6/src/main/java/com/aetherianartificer/townstead/profession/def/ProfessionDefs.java)
- [Data-driven skill registry](https://github.com/AetherianArtificer/Townstead/blob/0.7.6/src/main/java/com/aetherianartificer/townstead/profession/def/SkillDefs.java)
- [Fisherman work task](https://github.com/AetherianArtificer/Townstead/blob/0.7.6/src/main/java/com/aetherianartificer/townstead/hunger/FishermanWorkTask.java)
- [Leatherworker work task](https://github.com/AetherianArtificer/Townstead/blob/0.7.6/src/main/java/com/aetherianartificer/townstead/leatherworking/LeatherworkerWorkTask.java)
- [Spirit registry](https://github.com/AetherianArtificer/Townstead/blob/0.7.6/src/main/java/com/aetherianartificer/townstead/spirit/SpiritRegistry.java)
- [Spirit aggregation/classification](https://github.com/AetherianArtificer/Townstead/blob/0.7.6/src/main/java/com/aetherianartificer/townstead/spirit/VillageSpiritAggregator.java)
- [Building tiers and spirit contributions](https://github.com/AetherianArtificer/Townstead/blob/0.7.6/src/main/java/com/aetherianartificer/townstead/upgrade/BuildingTierReconciler.java)
- [Calendar snapshot API](https://github.com/AetherianArtificer/Townstead/blob/0.7.6/src/main/java/com/aetherianartificer/townstead/api/TownsteadCalendarSnapshot.java)

### MCA Reborn 1.20.1

- [Village state](https://github.com/Luke100000/minecraft-comes-alive/blob/4d824551b30654e5792e19e84f3933e3e3d90ea2/common/src/main/java/net/mca/server/world/data/Village.java)
- [Registered building state](https://github.com/Luke100000/minecraft-comes-alive/blob/4d824551b30654e5792e19e84f3933e3e3d90ea2/common/src/main/java/net/mca/server/world/data/Building.java)
- [Village manager](https://github.com/Luke100000/minecraft-comes-alive/blob/4d824551b30654e5792e19e84f3933e3e3d90ea2/common/src/main/java/net/mca/server/world/data/VillageManager.java)
- [Family tree](https://github.com/Luke100000/minecraft-comes-alive/blob/4d824551b30654e5792e19e84f3933e3e3d90ea2/common/src/main/java/net/mca/server/world/data/FamilyTree.java)
- [MCA villager entity](https://github.com/Luke100000/minecraft-comes-alive/blob/4d824551b30654e5792e19e84f3933e3e3d90ea2/common/src/main/java/net/mca/entity/VillagerEntityMCA.java)
- [Residency/home data](https://github.com/Luke100000/minecraft-comes-alive/blob/4d824551b30654e5792e19e84f3933e3e3d90ea2/common/src/main/java/net/mca/entity/ai/Residency.java)

---

## 22. Final implementation instruction

Implement the update in the milestone order, with compatibility repairs and tests first. Treat Townstead’s loaded registries and calendar—not assumptions in this document—as runtime authorities. Where the upstream state cannot prove a quest is achievable, hide or suspend that definition and explain why; never guess, silently no-op, or mark it complete.

The content totals, stable IDs, objective contracts, optional-mod behavior, and definition-of-done checklist are fixed. Flavor wording and small numeric balance adjustments may change after playtesting as long as they do not weaken the simulation integration, create an exploit, or turn a medium/hard quest back into a plain bulk fetch.
