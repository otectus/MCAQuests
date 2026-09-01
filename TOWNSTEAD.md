# MCA: Quests × Townstead

**[Townstead](https://www.curseforge.com/minecraft/mc-mods/townstead)** gives MCA villagers hunger,
thirst and tiredness, a shift schedule, a data-driven profession with experience and skills, and gives
each village a character built from what it has raised. This integration makes all of that into
something a quest can be *about*.

A farmer who has not eaten asks you for bread — and the bread goes into their inventory, where
Townstead lets them actually eat it, so the quest completes when they genuinely recover rather than
when you press a button. An apprentice asks you to stay while they work a real shift. A village asks
you to raise it a dock, and later notices it has become the kind of place that has one.

Everything here is **optional in both directions**. Without Townstead the types still register, so your
datapacks parse identically, the bundled content simply never becomes eligible, and nothing else about
MCA: Quests changes.

---

## Install

1. Install **MCA Reborn**, **MCA: Quests**, and **Townstead** (`[0.7.5,0.8)`; verified against
   **0.7.6**). Townstead requires **Patchouli** — if the game will not start, check that first, it is
   the most common cause and has nothing to do with this integration.
2. Start the server. That is all — no configuration is needed.

Confirm it took with `/mcaquests compat townstead status`. You want to see all fifteen capabilities.

---

## How it works

MCA: Quests never compiles against Townstead. Every member is looked up by name at runtime, and what
bound is reported as **capabilities** rather than as a single yes-or-no. That matters in practice: if a
Townstead update moves one internal method, only the feature that needed it stops working, and only the
quests that declared it suspend.

| Capability | What it unlocks |
|---|---|
| `READ_VILLAGER` | Identity, life stage, age, personality, fertility, heritage |
| `READ_NEEDS` | Hunger, thirst, energy, collapse |
| `READ_SCHEDULE` | Shift mode and template, what they are doing versus what was planned |
| `READ_PROFESSION` | Profession id, tier and experience |
| `READ_PROFESSION_SPEC` | The progression **track** behind a profession: how many tiers exist, the XP ceiling, and whether it advances at all |
| `READ_SKILL_REGISTRY` | Proving a skill id exists before a condition tests it or a reward teaches it |
| `READ_CALENDAR` | World day, season, weekday, calendar profile |
| `READ_BUILDING` | Registered buildings and their tiers |
| `READ_ROOT` | Species, ancestry and lineage |
| `READ_GENE` | Gene definitions and variants |
| `READ_SPIRIT` | A village's spirit points, tier and identity |
| `MUTATE_NEEDS` | Rewards that feed, water or rest a villager |
| `AWARD_PROFESSION_XP` | Rewards that grant profession experience |
| `MUTATE_SKILLS` | Rewards that teach or remove a skill |
| `DISPATCH_REACTION` | Villagers reacting to quest, project and situation transitions |

**Every bundled definition names the exact capabilities it uses**, and you should do the same. That is
what makes content degrade precisely instead of all at once.

### Why `READ_PROFESSION_SPEC` is separate from `READ_PROFESSION`

They answer different questions, and 1.4.0 shipped a bug because it only had the first.
`READ_PROFESSION` says where a villager currently stands. `READ_PROFESSION_SPEC` says whether their trade
goes anywhere at all — and Townstead answers a progression query for *every* profession, handing back a
zero/default track for the ones it has no progression for. A villager reports profession XP of 0 whether
their trade has a hundred tiers or none.

So MCA: Quests 1.4.0 offered a fisherman 120 profession XP that no Townstead 0.7.6 work task ever awards.
The quest parsed, validated, was offered, was accepted, and then waited forever. See
[`townstead_profession_track`](#townstead_profession_track--is-this-trade-actually-going-anywhere) for
how to write content that cannot do that.

**In Townstead 0.7.6, only farmer, butcher, cook and shepherd have progression.** Fisherman and
leatherworker have work tasks that award no XP at all. That is a fact about the bundled content of one
Townstead version, not a rule: a datapack that supplies a real fisherman track makes fisherman content
eligible again with no change to this mod. Never write a hard-coded profession whitelist — ask the
loaded registry.

---

## Gating content — `mcaquests:townstead_available`

Open every Townstead definition with this. It is true only when Townstead is installed, bound, and
providing the capabilities you list.

```json
{ "type": "mcaquests:townstead_available", "capabilities": ["READ_VILLAGER", "READ_NEEDS"] }
```

`capability` (singular) is accepted for a single one. Capability names are case-insensitive, and a name
that is not a real capability **fails the datapack reload** rather than silently gating on nothing.

---

## Reading Townstead state — the query

Four of the types below share one query language: a `source`, a dot `path` into it, an `operator`, and
a `value`.

```json
{
  "source": "villager",
  "target": "giver",
  "path": "needs.hunger",
  "operator": "lte",
  "value": 30
}
```

| Field | Default | Meaning |
|---|---|---|
| `source` | (required) | `villager`, `calendar`, `building`, `spirit`, `root`, `gene` |
| `target` | `giver` | `giver`, `bound`, `related`, `nearest`, `village_any` |
| `path` | (required) | Dot path, max 128 characters and 8 segments |
| `operator` | (required) | `eq`, `ne`, `lt`, `lte`, `gt`, `gte`, `contains`, `in`, `matches`, `exists` |
| `value` | (required except `exists`) | A JSON primitive, or an array for `in` |
| `missing` | `false` | What to answer when the source, target, path or capability is unavailable |

**Comparison rules.** Numbers compare by value, so `30` and `30.0` agree. Strings compare
case-insensitively. A bare id matches a `minecraft:`-namespaced one — but *only* when the other side
actually has a namespace, so `"work"` is never quietly turned into `"minecraft:work"`. `contains` works
on a string, a list, or a map's keys. `matches` is a full match, not a substring, and is compiled when
the datapack loads so a broken expression fails the reload rather than a quest.

**`missing` is the important one.** It defaults to `false`, which means an unreadable value makes
content *ineligible* rather than accidentally satisfied — a hunger check against a mod that is not
installed must not read as "starving" and start handing out famine quests.

### Paths worth knowing

| Path | Notes |
|---|---|
| `needs.hunger` | `0`–`100` |
| `needs.thirst`, `needs.quenched` | `0`–`20` |
| `needs.fatigue` | `0`–`20`, where **lower is more rested** |
| `needs.energy` | The same axis the other way up, so higher is better. Usually what you want |
| `needs.collapsed`, `needs.gated` | `gated` is true when Townstead is not simulating thirst |
| `schedule.currentActivity` | What they are doing: `work`, `meet`, `rest`, `idle` |
| `schedule.plannedActivity` | What their shift said they should be doing |
| `schedule.onSchedule` | Whether those two agree |
| `professionId`, `professionLevel`, `professionXp` | |
| `lifeStage`, `apparentAgeYears`, `senior` | |
| `heritage.<root id>` | Their share of one ancestry |

`/mcaquests compat townstead snapshot` prints the nearby villager's state **using these exact paths**,
so its output pastes straight into a condition.

---

## Conditions

| `type` | Fields |
|---|---|
| `mcaquests:townstead_available` | `capability`, `capabilities` |
| `mcaquests:townstead_value` | the query fields above |
| `mcaquests:townstead_building` | `building_type` (required), `minimum_level`, `count`, `minimum_size` |
| `mcaquests:townstead_spirit` | `spirit`, `minimum_points`, `minimum_tier`, `classification`, `primary`, `minimum_share` |
| `mcaquests:townstead_skill` | `target`, `skill` (required), `has` |
| `mcaquests:townstead_profession_track` | `target`, `profession`, `minimum_max_tier`, `minimum_remaining_xp`, `missing` |

`building_type` matches a **family**, so `dock` covers `dock_l1` through `dock_l3` and `minimum_level`
picks the tier. On `townstead_spirit`, points and share are per-spirit when `spirit` names one and
village-wide when it does not.

### `townstead_profession_track` — is this trade actually going anywhere?

Townstead answers "what is this profession's progression?" for **every** profession, handing back a
zero/default track for the ones it has none for. That is the trap this condition exists to close: MCA:
Quests 1.4.0 shipped quests asking a fisherman for 120 profession XP, and no fisherman work task in
Townstead 0.7.6 awards a single point. The quest parsed, validated, was offered, was accepted — and then
waited forever.

So a profession goal must prove itself against the **loaded** registry before it is offered:

```json
{
  "type": "mcaquests:townstead_profession_track",
  "target": "giver",
  "minimum_max_tier": 3,
  "missing": false
}
```

With no `profession`, it asks about whatever the target currently practises, which is what pairs it with
`townstead_profession_progress`'s own "the trade they were doing when you accepted" rule.
`minimum_remaining_xp` additionally requires that much XP to still be *reachable*, so a villager already
at the ceiling is not asked to earn more.

This is deliberately **not** a whitelist of professions. Townstead supports datapack-provided progression
definitions, so a pack that supplies a real fisherman track makes fisherman content eligible with no
code change here — and removing one makes it ineligible again. It needs the `READ_PROFESSION_SPEC`
capability; without it the condition takes its `missing` answer, which defaults to `false` so unproven
content hides rather than misleads.

---

## Objectives

| `type` | Fields | Meaning |
|---|---|---|
| `mcaquests:townstead_state` | query fields, `hold_ticks`, `reset_on_false` | Hold a value true for a stretch |
| `mcaquests:townstead_change` | query fields, `direction` (required), `amount` (required), `minimum_final`, `maximum_final`, `baseline_on_accept` | Move a value from where it started |
| `mcaquests:townstead_profession_progress` | `target`, `profession`, one of `xp_delta` / `target_xp` / `target_tier`, `require_current_profession` | Advance a trade |
| `mcaquests:townstead_building_registered` | `building_type` (required), `minimum_level`, `count`, `minimum_size`, `require_new_or_upgraded` | Get something built |
| `mcaquests:townstead_spirit_progress` | `spirit`, one of `points_delta` / `target_tier` | Grow a village's character |
| `mcaquests:townstead_healthy_residents` | `minimum_observed`, `minimum_fraction`, `hunger_min`, `thirst_min`, `energy_min`, `require_not_collapsed`, `minimum_loaded_fraction`, `hold_ticks` | Keep a village well |
| `mcaquests:townstead_schedule_streak` | `target`, `activity`, `required_shifts` (required), `minimum_coverage`, `require_on_schedule`, `reset_on_miss` | Work whole shifts, across days |

### `townstead_schedule_streak` — whole shifts, not one long stare

`townstead_state` with a large `hold_ticks` is the wrong tool for "help them through a few days at the
forge". A hold demands one unbroken stretch, which in practice demands the player stand and watch. A
streak counts **shifts the villager completed**, which is a thing the world does whether or not anyone
is looking:

```json
{
  "type": "mcaquests:townstead_schedule_streak",
  "activity": "work",
  "required_shifts": 3,
  "minimum_coverage": 0.65
}
```

A shift is identified by calendar profile, year, day, schedule template and shift ordinal, and credited
keys are persisted — so the same shift survives a logout, a restart, a dimension change and a `/reload`,
and a clock running backwards cannot mint a second one.

A finished shift ends **credited**, **missed**, or **unknown**. Unknown is what makes this honest: if the
villager was unloaded for most of the shift there is no evidence either way, so it neither counts nor
breaks a streak. Without it, walking away for a night would silently break a streak the player had no way
to see coming — and the opposite mistake would credit shifts nobody observed. `reset_on_miss` is off by
default for the same reason: an accumulating count is kinder than a fragile one, and the fragile version
is available to authors who want it.

Requires `READ_VILLAGER`, `READ_SCHEDULE` and `READ_CALENDAR`.

**Baselines are frozen once, when the quest is accepted, and never re-taken.** "Raise their hunger by
45" means nothing without a record of how hungry they were when you were asked, and re-reading it on a
later poll — or after a restart — would either make the quest unwinnable or complete it for free. The
baseline also records *which* question it answered and about *whom*, so editing a datapack under a live
quest stops the objective rather than comparing a hunger baseline against a fatigue reading.

**`minimum_final` and `maximum_final` are worth using.** A delta alone is satisfiable by starving a
villager to 5 and feeding them to 50: forty-five hunger gained, and nobody well fed.

**`require_new_or_upgraded`** is what makes "build us a dock" mean building one. On acceptance it
records the buildings that already qualify; only something that appears afterwards, or an existing
building raised to a higher tier, counts.

**Leave `profession` out** of `townstead_profession_progress` and it means *whatever trade they
practise*, fixed at accept time. That is what you want for a quest several professions can offer —
naming one would give every other giver a quest that could never progress.

**`minimum_observed` is not optional in spirit.** Only loaded villagers can be read, so without a floor
on how many the check actually sees, standing next to one contented farmer would satisfy "the village
is well fed".

Hold progress is counted in **seconds**, so a `hold_ticks` of `1200` reads as `(17/60)` in the log.

---

## Project objectives

These watch the world rather than waiting to be told about it — "the village has three docks" is not an
event, it is a condition that becomes true quietly, usually with nobody nearby.

| `type` | Fields | Meaning |
|---|---|---|
| `mcaquests:townstead_building_project` | `building_type` (required), `minimum_level`, `count` | The village has the buildings |
| `mcaquests:townstead_spirit_project` | `spirit`, `points_delta`, `target_tier` | The village has grown into something |
| `mcaquests:townstead_workforce_project` | `professions` (required), `minimum_tier`, `count` | Enough people can do the job |
| `mcaquests:townstead_resident_wellbeing_project` | `minimum_observed`, `minimum_fraction`, `hunger_min`, `thirst_min`, `energy_min`, `require_not_collapsed`, `minimum_loaded_fraction`, `hold_ticks` | The village has been well for a while |

**`townstead_workforce_project` counts only trades that can really get there.** The `professions` list is
the guaranteed baseline, not the whole answer: a resident is counted when their profession has a real
progressive track whose maximum tier reaches `minimum_tier`. A pack that adds a fisherman track gets
fishermen counted with no change to the JSON, and one that removes a track stops them counting again.
Before 1.4.1 a village whose only candidates were fishermen could be asked for three tier-2 workers it
was never going to produce.

**`minimum_loaded_fraction` (default `0.50`) is why an empty village no longer reads as a healthy one.**
`minimum_observed` alone was not enough: in a village of forty, seeing three contented residents is
evidence about three people, not about the village. The hold now waits until that share of MCA's resident
roll is actually observable.

**A phase finished by watching the world records no contributions**, because nobody handed anything
over. The `contributors` and `top_contributor` reward targets therefore have nobody to pay on such a
phase — this is real, not a bug, and the fix is compositional: pair a contribution phase with a state
phase and put contributor-targeted rewards on the first. Every bundled project does exactly that.

---

## Rewards

| `type` | Fields | Notes |
|---|---|---|
| `mcaquests:townstead_needs` | `target`, `need` (required), `mode`, `amount` (required) | `mode` is `delta` (default) or `target`. Clamped to that need's own range |
| `mcaquests:townstead_profession_xp` | `target`, `profession` (required), `amount` (required), `respect_daily_cap` | |
| `mcaquests:townstead_skill` | `target`, `skill` (required), `forget`, `force` | Teaching a known skill is a success that changes nothing |
| `mcaquests:townstead_reaction` | `target`, `task` (required), `phase` | An *extra* flourish; lifecycle reactions are automatic |

**Bypassing the daily XP cap takes two keys**: `"respect_daily_cap": false` in the pack *and*
`allowUncappedProfessionXp` on the server. A repeatable quest with an uncapped award would let a player
outrun the pacing Townstead deliberately sets, and a datapack alone should not decide that for somebody
else's server. `force` on a skill reward works the same way.

**A reward that cannot be applied does not block the turn-in**, by default. The player has already done
the work; trapping them with a finished quest they can never hand in is worse than quietly skipping the
villager-facing half. Set `rewardFailureBlocksCompletion` if you disagree.

---

## Delivering goods to a villager

Give any `mcaquests:item_delivery` objective a `destination` and the goods go somewhere instead of
being destroyed:

```json
{
  "type": "mcaquests:item_delivery",
  "item": "minecraft:bread",
  "count": 12,
  "destination": { "type": "townstead_villager_inventory", "target": "giver" }
}
```

| `type` | Meaning |
|---|---|
| `consume` | Destroy them. The default, and what every pre-1.4.0 quest does |
| `townstead_villager_inventory` | Put them in the villager's inventory, where Townstead lets them be used |

The transfer is **exact-once and atomic**. Capacity is checked before the turn-in is committed, so a
villager with no room refuses the hand-over and says so rather than the player paying for a transfer
that cannot happen. Items exist in exactly one place at every step, and anything that will not fit is
handed straight back.

`townstead_village_storage` is **not implemented**. Townstead exposes no registered storage API that
can be written to safely, and guessing at a nearby chest would be worse than refusing — so the parse
error says so plainly rather than leaving you wondering.

---

## Situation triggers

Villages ask for help on their own account.

| `type` | Fields | Fires when |
|---|---|---|
| `mcaquests:townstead_need` | `need` (required), `minimum_fraction` | Enough of a village crosses into a need crisis |
| `mcaquests:townstead_collapse` | *(none)* | A villager collapses |
| `mcaquests:townstead_profession_tier` | `profession`, `minimum_tier` | Somebody rises a tier |
| `mcaquests:townstead_spirit` | `spirit`, `minimum_tier` | A village gains a spirit tier or changes identity |
| `mcaquests:townstead_building` | `building_type`, `minimum_level` | A building is registered or upgraded |

**These fire on transitions, never on states.** A villager who is still collapsed is not news; the
moment they collapsed was. Need crises additionally use two thresholds — one to open, a lower one to
close — so a village sitting on the line cannot flap the same emergency in and out of your quest list.
The gap is `needCrisisHysteresis`.

**Nothing is replayed after a restart**, and a first sighting is never news: installing this on an
existing world will not open a situation for every villager in it.

---

### Transition triggers (1.4.1)

The five triggers above answer "what is true now". These three answer "what just changed", which is
what a festival, a coming-of-age or an emergency actually needs:

| `type` | Fields | Fires when |
|---|---|---|
| `mcaquests:townstead_calendar_transition` | `transition` (`week`/`season`/`year`), `from`, `to` | The calendar turns over |
| `mcaquests:townstead_life_transition` | `transition` (`canonical_stage`/`life_stage`/`senior`), `from`, `to` | A villager crosses a life threshold |
| `mcaquests:townstead_schedule_disruption` | `minimum_observed`, `minimum_fraction`, `hold_ticks` | A village stops keeping to its routine |

```json
{ "type": "mcaquests:townstead_calendar_transition", "transition": "season", "to": "winter" }
```

**Nothing here assumes four seasons or a seven-day week.** Every value comes from the loaded calendar
profile, so a datapack that defines two seasons or a thirty-day year works untouched — and a definition
naming a season the profile does not contain is reported by `/mcaquests validate` rather than waiting
forever for a winter that will never come.

**Prefer `canonical_stage` over `life_stage`.** Townstead roots may name their stages anything; the
canonical axis resolves the stage through the root's own `presentsAs` value, so a custom root whose
adult stage is called "butterfly" still produces the semantic `child → adult` crossing. The raw
`life_stage` axis is there for a pack that knows exactly which root it is writing for.

`townstead_spirit` also gained `from_classification`, `to_classification` and `transition_only`, so a
definition can ask "has this village become a **blend**" rather than only "which spirit went up".
Without those fields it behaves exactly as it did before.

**A first sighting never fires.** Baselines are seeded silently, so installing this update on an
existing world does not greet the player with a backlog of seasons that already happened, and neither
a restart, a chunk reload nor a `/reload` replays one. Changing calendar profile seeds a fresh baseline
rather than synthesising a season change out of two incomparable calendars.

Two more triggers are **not** Townstead at all and work on a plain MCA install:

| `type` | Fields | Fires when |
|---|---|---|
| `mcaquests:villager_stranded` | `minimum_distance`, `hold_ticks`, `require_night` | A resident is far outside their village after dark |
| `mcaquests:hostiles_near_home` | `count`, `radius`, `hold_ticks` | Hostiles gather at a resident's bed |

Both search a small box around a bed or village centre rather than sweeping the world, and both require
the condition to persist across sweeps — a villager who stepped past the border for a moment is not
stranded, and a zombie passing through is not a siege.

---

## What happens when Townstead is removed

This is the case worth understanding before you build a pack around it.

A quest you accepted while Townstead was installed **does not fail**. It:

- keeps its progress and its frozen baselines, exactly as they were;
- stops polling, so nothing advances and nothing regresses;
- never reads as complete, so it cannot be turned in;
- can never be failed by a deadline — **the clock stops too**, so a quest suspended for three in-game
  days is not instantly expired the moment Townstead comes back;
- shows an amber **"On hold — waiting on a mod that is not installed"** line in the quest log;
- stays abandonable, from both the log and the villager menu;
- and picks up exactly where it left off if Townstead returns.

Suspension is decided fresh every pass rather than written into the save, so recovery needs no
migration and nothing can go stale. Offers simply stop appearing, because every bundled definition
gates on `townstead_available`.

The same applies at a finer grain: if a Townstead update moves one internal method, only the quests
that declared the capability that needed it suspend.

---

## Commands

All at permission level 2, all read-only.

| Command | What it tells you |
|---|---|
| `/mcaquests compat townstead status` | Townstead's version, which MCA layout it was built against, how many capabilities bound, which did not, and the feature toggles. With `debugBindingLogs` on it also reports the performance counters |
| `/mcaquests compat townstead probe` | Checks each capability by **actually using it** against a nearby villager — "bound" and "returns something" are not the same thing |
| `/mcaquests compat townstead snapshot` | The nearby villager's state, printed as quest-author paths you can paste into a condition |

---

## What happens when things aren't installed, or come and go

| Situation | Result |
|---|---|
| Townstead absent | Types register, packs parse, `townstead_available` is false, no bundled content is offered, no Townstead class is loaded. Nothing is logged — this is the normal state |
| Townstead present, all capabilities bound | Everything in this document works |
| Townstead present, some capabilities missing | One WARN naming them. Content declaring those capabilities is ineligible; everything else works. `status` lists what is missing |
| Townstead installed but unbindable | One WARN, the integration disables itself, the server keeps running |
| Townstead removed from an existing world | Active quests suspend as described above. The world loads normally |
| Townstead restored | Suspended quests resume against their **original** baselines. Nothing is duplicated and nothing is re-announced |
| `enabled = false` | As "absent", except one INFO at startup saying so |

---

## Release checklist

These need a real client and a dedicated server with MCA and Townstead installed, so they are not part
of the automated suite — MCA's mixins ship without a refmap and cannot load in a development run, which
makes an in-development GameTest against an MCA villager impossible. Run them before shipping.

**Installation matrix**

| MCA line | Townstead | Client | Server | Expect |
|---|---|---|---|---|
| 7.6.x | absent | ☐ | ☐ | 1.3.x behaviour, unchanged, nothing logged |
| 7.7.x | absent | ☐ | ☐ | As above |
| matched | 0.7.5 | ☐ | ☐ | `status` reports `FULL` |
| matched | 0.7.6 | ☐ | ☐ | `status` reports `FULL` |
| matched | removed after a save | ☐ | ☐ | World loads; active quests suspend; abandonable |
| matched | restored | ☐ | ☐ | Original baselines resume; nothing duplicated |
| mismatched MCA/Townstead | — | ☐ | ☐ | A clear loader or binding message, **not** a crash and **not** a misleading `FULL` |

**Scenarios**

1. ☐ Spawn an MCA villager; `snapshot` prints a complete, sensible state.
2. ☐ A hunger quest freezes its baseline on accept and completes only on observed recovery.
3. ☐ Thirst content is not offered when Townstead has thirst gated off, and works when it is on.
4. ☐ A rest quest's hold resets when the villager is interrupted.
5. ☐ A profession XP reward respects the daily cap, and tiers up correctly across a day boundary.
6. ☐ A skill reward applied twice is an idempotent success, not an error.
7. ☐ A registered building completes a building objective; a lookalike pile of blocks does not.
8. ☐ Spirit delta, tier and identity-change objectives all complete.
9. ☐ `townstead_healthy_residents` refuses to complete on fewer than `minimum_observed` loaded villagers.
10. ☐ Collapse, tier, building and spirit signals each fire once, and do **not** replay after a restart.
11. ☐ Inventory delivery is exact-once; a full villager refuses the hand-over and the player keeps the goods.
12. ☐ An active quest survives Townstead being removed, suspends, and resumes its original baseline when restored.
13. ☐ Completion still succeeds when a reaction dispatch throws.

**Performance** — on a dedicated server with 100+ villagers, `debugBindingLogs` on, check
`status`: average scan under 1 ms, no scan above 5 ms, and cache hits clearly outnumbering reads.

**UI** — the Quests button is visible and overlaps nothing at GUI scales 1–4 with Townstead's Pose
button present; Talk still opens Townstead's RPG dialogue afterwards and restores camera and HUD; the
quest log's context lines show only what the quest is about.

---

## FAQ

**Do I need Townstead?** No. Without it MCA: Quests behaves exactly as it did in 1.3.x.

**Will my existing quests break?** No. Every new field is optional and every new type is additive. A
pack written for 1.3.x loads unchanged.

**Can I write Townstead quests without Townstead installed?** Yes — the types register regardless, so
your pack parses and validates. It just will not be offered until Townstead is there.

**Why does my Townstead quest say "On hold"?** Townstead is not installed, or the specific capability
that quest needs did not bind. Run `/mcaquests compat townstead status`.

**Why did my villager only get 40 of the 120 XP I awarded?** Townstead's daily cap. That is deliberate,
and the reward reports what actually landed rather than what was asked for.

**Why will my villager not take the bread?** Their inventory is full. The hand-over is refused rather
than the goods vanishing — you keep them.

**Does the client need Townstead?** No. Everything Townstead-related is read on the server and sent as
finished text, so a vanilla-ish client sees the same thing.
