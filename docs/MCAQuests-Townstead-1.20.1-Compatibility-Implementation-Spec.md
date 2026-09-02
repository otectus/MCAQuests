# MCA: Quests × Townstead — Forge 1.20.1 Compatibility Implementation Specification

**Status:** implementation-ready design  
**Primary repository:** [otectus/MCAQuests](https://github.com/otectus/MCAQuests)  
**Integrated mod:** [AetherianArtificer/Townstead](https://github.com/AetherianArtificer/Townstead)  
**Platform:** Minecraft 1.20.1, Forge 47.x, Java 17  
**Target MCA: Quests baseline:** `1378ecece9332ad398882ffd8f314b8791644c67` (`main`, inspected 2026-08-25)  
**Target Townstead baseline:** `4d6206cdf8b9d0f558694d7b35b223f4f6ace61e` (`main`, version `0.7.7`, inspected 2026-08-25)

> This is a build specification for a coding agent. Implement it in MCA: Quests as reviewable commits. Do not make Townstead required, do not fork Townstead, and do not regress MCA: Quests when Townstead is absent. “Full support” means every capability, content pack, lifecycle hook, and UI path below works against **both** Townstead 1.20.1 Forge artifacts: `townstead-mca-modern` and `townstead-mca-legacy`.

---

## 1. Required outcome

Produce one MCA: Quests 1.20.1 Forge jar with two complete modes:

| Installation | Required behavior |
| --- | --- |
| MCA + MCA: Quests | Existing 1.3.x behavior remains unchanged. No Townstead class is loaded, no Townstead data is queried, Townstead offers stay ineligible, and no warning is emitted merely because Townstead is absent. |
| MCA + MCA: Quests + supported Townstead | Townstead is a first-class quest state provider and action target. Quests, projects, situations, rewards, dialogue reactions, UI, diagnostics, documentation, and bundled content use Townstead needs, schedules, life state, professions, skills, buildings, calendar, and village spirit. |

The work is complete only when the matrix in §18 passes. “The mods no longer crash together” is not sufficient.

### 1.1 Non-negotiable constraints

1. **Townstead stays optional.** Its absence is a normal, silent path.
2. **One MCA: Quests jar supports both Townstead 1.20.1 variants.** Do not publish modern/legacy MCA: Quests jars.
3. **No static Townstead linkage in common/base code.** Townstead is compiled twice because MCA moved from `forge.net.mca` to `forge.net.conczin.mca`; static descriptors can bind the wrong variant.
4. **No global entity scans.** Reuse existing quest polling and situation village scans with budgets.
5. **Server authority.** Eligibility, progress, mutation, rewards, and transitions happen on the logical server. Client data is display-only.
6. **Fail by capability, not by crash.** A failed optional binding disables only that feature, makes its content ineligible, and produces one actionable diagnostic.
7. **Data-driven integration.** Pack authors can use the new conditions, objectives, project objectives, rewards, and signals without Java add-ons.
8. **Save-safe removal.** A world with active Townstead quest state still loads after Townstead is removed. Entries suspend and remain abandonable; they do not corrupt saves.
9. **No semantic shortcuts.** XP respects Townstead progression/daily caps; needs use Townstead mutators; building/spirit objectives use registered Townstead state, not block guesses.

### 1.2 Compatibility range

Declare Townstead as optional with range `[0.7.5,0.8)`, and make `0.7.7` the release-gate baseline. CI must probe `0.7.5`, `0.7.6`, and `0.7.7` when artifacts are available. Do not claim full support for a point release until its matrix row passes.

```toml
[[dependencies.mcaquests]]
modId="townstead"
mandatory=false
versionRange="[0.7.5,0.8)"
ordering="AFTER"
side="BOTH"
```

Absence is allowed. An installed unsupported version should get Forge’s normal version message rather than a reflective crash.

---

## 2. Source-grounded baseline

Re-check these links at implementation time and update the SHAs if `main` moved. Source wins over README assumptions.

### 2.1 MCA: Quests facts

- Current main targets Minecraft 1.20.1/Forge and Java 17 and avoids static MCA linkage.
- `McaScreenButtons` adds a collision-aware Quests button to MCA `InteractScreen` through Forge events and matches by suffix: [source](https://github.com/otectus/MCAQuests/blob/1378ecece9332ad398882ffd8f314b8791644c67/src/main/java/dev/otectus/mcaquests/client/McaScreenButtons.java).
- Optional FTB Quests and MCA Reputation integrations use guarded initialization.
- `PollingObjective` is evaluated roughly once per second; `ObjectiveProgress.extra` can store frozen baselines.
- Projects have no polling-project equivalent; this specification adds one rather than abusing events.
- Situation detection already scans nearby MCA villages and throttles events. Extend it rather than adding another unbounded scanner.
- `QuestDialogueHooks` resolves add-on voiced lines; it is not a Townstead state bridge: [source](https://github.com/otectus/MCAQuests/blob/1378ecece9332ad398882ffd8f314b8791644c67/src/main/java/dev/otectus/mcaquests/api/QuestDialogueHooks.java).
- Existing tests include static-link tripwires and pack/locale validation; mirror them.

### 2.2 Townstead facts

- Townstead publishes two 1.20.1 Forge variants:
  - `1.20.1-forge` / `townstead-mca-modern`, compiled against `forge.net.conczin.mca`.
  - `1.20.1-forge-legacy` / `townstead-mca-legacy`, compiled against `forge.net.mca`.
- Both have mod id `townstead`; package variation must not affect pack APIs.
- `com.aetherianartificer.townstead.api.TownsteadAPI` is a stable read-only facade. `entity(Entity)` is the safe entry because its parameter descriptor is vanilla-only.
- Townstead reorganizes MCA dialogue but appends unknown MCA answer ids as modded content: [`DialogueMenuOrganizer`](https://github.com/AetherianArtificer/Townstead/blob/4d6206cdf8b9d0f558694d7b35b223f4f6ace61e/src/main/java/com/aetherianartificer/townstead/client/gui/dialogue/DialogueMenuOrganizer.java).
- It intercepts Talk/Pose while preserving the underlying `InteractScreen`: [`InteractScreenMixin`](https://github.com/AetherianArtificer/Townstead/blob/4d6206cdf8b9d0f558694d7b35b223f4f6ace61e/src/main/java/com/aetherianartificer/townstead/mixin/InteractScreenMixin.java). Thus the existing top-level Quests button is the safest entry.
- `RpgDialogueScreen` owns camera restoration, HUD visibility, closure, and late content: [source](https://github.com/AetherianArtificer/Townstead/blob/4d6206cdf8b9d0f558694d7b35b223f4f6ace61e/src/main/java/com/aetherianartificer/townstead/client/gui/dialogue/RpgDialogueScreen.java). Do not bypass its close state machine.

### 2.3 Townstead read model

Normalize at least these public snapshots:

| Snapshot | Required fields |
| --- | --- |
| Villager | UUID, name, entity type, root id, life stage, biological/apparent age, immortal/ageless/senior, personality id, profession id/level/XP, fertility, schedule, needs, carried variants, expressed alleles, heritage |
| Needs | hunger, saturation, hunger exhaustion, thirst, quenched, thirst exhaustion, fatigue, collapsed, gated |
| Schedule | mode, template id, custom shifts, current tick/display hour, current/planned activity, current template id, shifts, weekday templates |
| Calendar | profile id, world day, epoch offset, time mode, year, month, day, day-of-year, weekday, season |
| Building | id, village id, type, size, center, min/max bounds |
| Root | id, display name, species, ancestry, lineage, effective species, default genes, life stages |
| Gene | id, display name, description, category, dominance, locus, weight, display mode, variants |
| Spirit | total points, per-spirit points, contributing buildings, tier, classification, primary id, secondary id |

Townstead’s `TownsteadQuery.resolve(Object, String)` supports dot paths. The bridge may call it internally, but MCA: Quests exposes its own validated query contract so Townstead changes cannot silently alter pack semantics.

---

## 3. Architecture

### 3.1 Package layout

```text
src/main/java/dev/otectus/mcaquests/compat/townstead/
  TownsteadCompat.java
  TownsteadStatus.java
  TownsteadCapability.java
  TownsteadBridge.java
  NoopTownsteadBridge.java
  ReflectiveTownsteadBridge.java
  TownsteadBindings.java
  TownsteadQueryEngine.java
  TownsteadTargetResolver.java
  TownsteadMutationService.java
  TownsteadTransitionDispatcher.java
  TownsteadDiagnostics.java
  model/
    TownsteadVillagerView.java
    TownsteadNeedsView.java
    TownsteadScheduleView.java
    TownsteadCalendarView.java
    TownsteadBuildingView.java
    TownsteadSpiritView.java
    TownsteadRootView.java
    TownsteadGeneView.java
    TownsteadMutationResult.java
```

All model records are owned by MCA: Quests and contain only JDK, vanilla/Forge, and MCA: Quests types. Never return a Townstead object outside this package.

### 3.2 Bootstrap/status

```java
public final class TownsteadCompat {
    private static volatile TownsteadBridge BRIDGE = NoopTownsteadBridge.absent();

    public static void initialize() {
        if (!ModList.get().isLoaded("townstead")) return;
        BRIDGE = ReflectiveTownsteadBridge.bind();
    }

    public static TownsteadBridge bridge() { return BRIDGE; }
}
```

Do not import Townstead here. Bind classes by string, validate capabilities once, cache method handles, and publish an immutable binding table.

```java
enum TownsteadStatus {
    ABSENT,   // normal when mod is not loaded
    FULL,     // all required capabilities bound
    PARTIAL,  // safe reads work, optional actions missing
    DISABLED  // no safe baseline facade
}
```

Required capability ids:

```text
READ_VILLAGER
READ_CALENDAR
READ_BUILDING
READ_ROOT
READ_GENE
READ_NEEDS
READ_SCHEDULE
READ_PROFESSION
READ_SPIRIT
MUTATE_NEEDS
AWARD_PROFESSION_XP
MUTATE_SKILLS
DISPATCH_REACTION
```

Supported matrix rows must report `FULL`. `PARTIAL` is graceful degradation, not a release shortcut.

### 3.3 Binding strategy

Use two layers:

1. **Public reads:** bind `TownsteadAPI.entity(Entity)`, `calendar(MinecraftServer)`, `buildingAt(ServerLevel, BlockPos)`, `origin(ResourceLocation)`, and `gene(ResourceLocation)`, then public record accessors.
2. **Narrow actions:** bind only public internal methods needed for mutations, XP, skills, spirit aggregation, and reactions. Select overloads by owner string, name, arity, assignability, and return type—never an MCA class literal.

For descriptors containing relocated `VillagerEntityMCA` or `Village`, enumerate candidates and choose one where `parameterType.isInstance(runtimeArgument)`. Adapt cached handles to object signatures. Do no lookup inside polling loops. Avoid private access; if unavoidable, isolate it to one capability and explain it.

### 3.4 Bridge contract

```java
public interface TownsteadBridge {
    TownsteadStatus status();
    Set<TownsteadCapability> capabilities();
    String detectedVersion();
    Optional<String> variant();

    Optional<TownsteadVillagerView> villager(Entity entity);
    Optional<TownsteadCalendarView> calendar(MinecraftServer server);
    Optional<TownsteadBuildingView> buildingAt(ServerLevel level, BlockPos pos);
    Optional<TownsteadRootView> root(ResourceLocation id);
    Optional<TownsteadGeneView> gene(ResourceLocation id);
    Optional<TownsteadSpiritView> spiritForHomeVillage(Entity villager);

    TownsteadMutationResult changeNeeds(Entity villager, NeedMutation mutation);
    TownsteadMutationResult awardProfessionXp(Entity villager, String professionId,
                                               int requestedXp, boolean respectDailyCap);
    TownsteadMutationResult learnSkill(Entity villager, ResourceLocation skillId, boolean force);
    TownsteadMutationResult forgetSkill(Entity villager, ResourceLocation skillId);
    TownsteadMutationResult dispatchTransition(ServerLevel level, LivingEntity villager,
                                                ResourceLocation taskId, String phase);
}
```

Every method is total and catches failures at the bridge boundary. `NoopTownsteadBridge` returns empty reads and `MOD_ABSENT` mutations.

### 3.5 Error/log policy

- Bind once; log one INFO for `FULL` with version, variant, and capability count.
- Log one WARN for `PARTIAL`/`DISABLED`, listing missing capabilities and the diagnostic command.
- Rate-limit each runtime capability failure to once/session at WARN; later instances DEBUG.
- Never log polling misses.
- Mutation reasons: `MOD_ABSENT`, `CAPABILITY_MISSING`, `TARGET_MISSING`, `FEATURE_GATED`, `INVALID_VALUE`, `DAILY_CAP`, `INTERNAL_ERROR`.

### 3.6 Static-link tripwire

Add `NoTownsteadStaticLinkTest` modeled after existing tripwires. Scan production constant pools and reject class descriptors beginning:

```text
com/aetherianartificer/townstead/
forge/net/mca/
forge/net/conczin/mca/
```

String literals in `TownsteadBindings` are allowed; descriptors are not. Keep any compiled Townstead fixtures in an isolated test source set.

---

## 4. Read and mutation semantics

### 4.1 Snapshot caching

Use a per-evaluation cache, not a long-lived stale cache:

- one villager snapshot per UUID per eligibility/progress pass;
- one calendar snapshot per server per pass;
- one spirit snapshot per village per situation scan;
- one building lookup per source/position per pass.

Extend evaluation with a lazy Quests-owned `TownsteadEvaluation` sidecar. Never add Townstead objects to `QuestContext`.

### 4.2 Query engine

| Field | Values |
| --- | --- |
| `source` | `villager`, `calendar`, `building`, `spirit`, `root`, `gene` |
| `target` | Existing selector plus `giver`, `bound`, `related`, `nearest`, `village_any`; ignored for calendar |
| `path` | Dot identifiers/list indices; max length 128, max depth 8 |
| `operator` | `eq`, `ne`, `lt`, `lte`, `gt`, `gte`, `contains`, `in`, `matches`, `exists` |
| `value` | JSON primitive/array; omitted only for `exists` |
| `missing` | `false` (default) or `true` |

Rules:

- Numeric comparison converts both values to `BigDecimal`.
- Resource ids normalize to lowercase `namespace:path`; enum-like values compare case-insensitively.
- `matches` compiles at reload, max 256 chars, rejecting invalid expressions.
- `contains` supports string/map/list; `in` tests a scalar against a primitive array.
- Missing source/target/path/capability follows `missing`, default false.
- Mutation is never available through query paths.

### 4.3 Needs

Route changes through verified Townstead setters (`setHunger`, `setThirst`, `setQuenched`, `setFatigue`, `restoreEnergy`, or equivalents) so dirty/sync state is correct.

- Clamp hunger to Townstead’s range (currently 0–100).
- Change thirst only when its feature bridge is active; otherwise `FEATURE_GATED`.
- Townstead fatigue is currently 0–20, where lower fatigue is more energy. Expose reward JSON as `energy` or explicit `fatigue`.
- Recover collapse through Townstead recovery logic/threshold, not a lone boolean toggle.
- Re-read after mutation and return applied delta/final state.

### 4.4 Profession XP

Use `ProfessionProgressions` and `ProfessionXp`:

1. Resolve profession and normally require it to match the snapshot’s current profession.
2. Read current XP record and progression spec.
3. Reset `xpToday` when recorded day differs from world day.
4. With cap enabled, apply `min(requested, remainingDailyCap, maxXp-currentXp)`.
5. Compute tier through `tierForXp`.
6. Preserve `lastTierUpTick` unless tier rises; then use current game time.
7. Write one new record via `professionMemory.setProfessionXp`.
8. Re-read and report requested/applied XP, old/new totals and tiers, and cap reason.

Default `respect_daily_cap=true`. Bypass requires both pack JSON and a server opt-in.

### 4.5 Skills and reactions

Bind `LearnedSkills.learn`, `forceLearn`, and `forget`. Default to ordinary learn; force requires JSON plus server permission. Already-known is idempotent success.

Bind `ReactionDispatcher.onTaskTransition(ServerLevel, LivingEntity, ResourceLocation, String)` and call it only after the Quests transaction commits. Stable phases:

```text
accepted
ready
completed
failed
abandoned
project_started
project_stage_completed
project_completed
project_failed
situation_started
situation_resolved
```

Reaction failure never rolls back quest state or rewards.

---

## 5. Data-pack API

Register Townstead type ids unconditionally so packs parse when Townstead is absent; runtime behavior is capability-gated.

### 5.1 Conditions

| Type | Purpose |
| --- | --- |
| `mcaquests:townstead_available` | Requires Townstead and optionally a capability. |
| `mcaquests:townstead_value` | Compares a normalized snapshot path. |
| `mcaquests:townstead_building` | Tests registered building type/count/size. |
| `mcaquests:townstead_spirit` | Tests points/tier/classification/identity/share. |
| `mcaquests:townstead_skill` | Tests a target villager’s learned skill. |

```json
{
  "type": "mcaquests:townstead_value",
  "source": "villager",
  "target": "giver",
  "path": "needs.hunger",
  "operator": "lte",
  "value": 30
}
```

```json
{
  "type": "mcaquests:townstead_available",
  "capability": "READ_NEEDS"
}
```

Every bundled Townstead definition starts with `townstead_available` and exact capabilities.

### 5.2 Quest objectives

#### `mcaquests:townstead_state`

Complete when a value predicate remains true for a hold period.

```json
{
  "type": "mcaquests:townstead_state",
  "target": "giver",
  "source": "villager",
  "path": "schedule.currentActivity",
  "operator": "eq",
  "value": "work",
  "hold_ticks": 600,
  "reset_on_false": true
}
```

#### `mcaquests:townstead_change`

Freeze a baseline on acceptance and complete on delta/target:

```json
{
  "type": "mcaquests:townstead_change",
  "target": "giver",
  "source": "villager",
  "path": "needs.hunger",
  "direction": "increase",
  "amount": 45,
  "minimum_final": 70,
  "baseline": "on_accept"
}
```

Versioned `ObjectiveProgress.extra` fields:

```text
townstead_schema: 1
townstead_source: "villager"
townstead_path: "needs.hunger"
townstead_target_uuid: <uuid>
townstead_baseline_type: "number"
townstead_baseline_number: <decimal>
townstead_baseline_tick: <long>
townstead_absent_since: <optional long>
```

Never re-baseline after restart, unload, or Townstead removal.

#### `mcaquests:townstead_profession_progress`

Supports `xp_delta`, `target_xp`, or `target_tier`, freezing target/profession/baseline.

```json
{
  "type": "mcaquests:townstead_profession_progress",
  "target": "giver",
  "profession": "minecraft:farmer",
  "xp_delta": 120,
  "require_current_profession": true
}
```

Profession changes pause rather than reset when `require_current_profession=true`.

#### `mcaquests:townstead_building_registered`

```json
{
  "type": "mcaquests:townstead_building_registered",
  "village": "giver_home",
  "building_type": "townstead:dock",
  "minimum_level": 2,
  "count": 1,
  "require_new_or_upgraded": true
}
```

Use registered data. Freeze matching ids/levels on acceptance when new/upgrade is required.

#### `mcaquests:townstead_spirit_progress`

```json
{
  "type": "mcaquests:townstead_spirit_progress",
  "village": "giver_home",
  "points_delta": 60,
  "target_tier": 2,
  "spirit": "townstead:industrious"
}
```

#### `mcaquests:townstead_healthy_residents`

```json
{
  "type": "mcaquests:townstead_healthy_residents",
  "village": "giver_home",
  "minimum_observed": 4,
  "minimum_fraction": 0.75,
  "hunger_min": 60,
  "energy_min": 8,
  "hold_ticks": 1200,
  "unloaded": "ignore"
}
```

Allow `ignore`, `fail`, or `last_known`; default `ignore` plus required `minimum_observed`.

### 5.3 Polling behavior

- Use existing `PollingObjective` cadence (about once/second) and a shared evaluation cache.
- Cap residents at 64/player/pass and round-robin larger sets.
- Send client progress only on visible changes.
- Do not write NBT on unchanged polls.

### 5.4 Project objectives

```java
public interface PollingProjectObjective extends ProjectObjective {
    boolean poll(ProjectContext context, ProjectInstance instance,
                 ProjectObjectiveProgress progress);
}
```

Add a bounded pass to `ProjectManager`, default 20 ticks, sharing caches. Register:

```text
mcaquests:townstead_building_project
mcaquests:townstead_spirit_project
mcaquests:townstead_workforce_project
mcaquests:townstead_resident_wellbeing_project
```

State-driven completion credit goes only to online project members who contributed an ordinary objective or joined before the final transition. Do not fabricate contribution counts; persist completion reason and observed Townstead state.

### 5.5 Rewards

| Reward | Behavior |
| --- | --- |
| `mcaquests:townstead_needs` | Target plus hunger/thirst/energy/fatigue delta or target; clamped/gated. |
| `mcaquests:townstead_profession_xp` | Target, profession, amount, cap policy; reports applied amount. |
| `mcaquests:townstead_skill` | Target, skill, learn/forget, optional force. |
| `mcaquests:townstead_reaction` | Explicit extra reaction; lifecycle reactions remain automatic. |

```json
{
  "type": "mcaquests:townstead_profession_xp",
  "target": "giver",
  "profession": "minecraft:farmer",
  "amount": 35,
  "respect_daily_cap": true
}
```

Validate capability/target before mutation. By default mutation failure does not block completion because removal/reload must not trap a finished player. Persist an idempotency marker before acknowledging success; never retry an applied mutation because a client packet failed.

### 5.6 Supply delivery

Extend item delivery with an optional destination:

```json
{
  "type": "mcaquests:item_delivery",
  "item": "minecraft:bread",
  "count": 32,
  "destination": {
    "type": "townstead_villager_inventory",
    "target": "giver"
  }
}
```

Destinations:

```text
consume
townstead_villager_inventory
townstead_village_storage (only after a safe registered storage API is verified)
```

Exact-once transaction:

1. Simulate player extraction.
2. Simulate destination insertion.
3. If full amount will not fit, change nothing and show capacity feedback.
4. Commit both in the same server tick.
5. Return unexpected remainder to the player; if full, drop owner-protected at player.
6. Record committed transfer before completing the objective.

Do not implement village storage by guessing a nearby chest. Villager inventory delivery is required for the first full release.

---

## 6. Target resolution

Reuse MCA: Quests target selectors and add a shared `TownsteadTargetResolver`.

| Selector | Meaning |
| --- | --- |
| `giver` | Quest giver frozen by UUID. |
| `bound` | Existing objective-bound UUID. |
| `related` | Existing MCA family resolver result, then frozen. |
| `nearest` | Nearest loaded MCA villager matching filters/radius, frozen once chosen. |
| `village_any` | Any observed resident; aggregate conditions only, never mutation rewards. |

Rules:

- Mutations require one UUID; reject `village_any`.
- Never retarget a baseline merely because its villager unloads or dies.
- UUID is authoritative; entity id is transient/client-only.
- Resolve home village through existing `McaCompat`; bridge to Townstead spirit/buildings only inside the compat island.
- Dimension changes, unloads, and dead targets pause or report missing rather than complete.

---

## 7. Lifecycle and situations

### 7.1 Quest transitions

After each successful MCA: Quests state transaction, dispatch once:

| MCA: Quests transition | Townstead phase |
| --- | --- |
| Offer accepted | `accepted` |
| All objectives satisfied / awaiting turn-in | `ready` |
| Turn-in and rewards committed | `completed` |
| Failure committed | `failed` |
| Abandonment committed | `abandoned` |

Add a versioned phase bitset to instance data if no equivalent audit exists.

### 7.2 Projects

Dispatch project phases to an explicit project giver, else the project village leader, else nobody. Never choose a random nearby villager merely to play a reaction.

### 7.3 Townstead-aware situation signals

Extend `SituationSignalType`:

```text
TOWNSTEAD_NEED
TOWNSTEAD_COLLAPSE
TOWNSTEAD_PROFESSION_TIER
TOWNSTEAD_SPIRIT
TOWNSTEAD_BUILDING
```

Extend `TriggerSignal` with optional versioned context while preserving old constructors/fields:

```java
record SignalContext(
    @Nullable ResourceLocation key,
    @Nullable String stringValue,
    @Nullable Double numericValue,
    @Nullable Integer oldTier,
    @Nullable Integer newTier,
    @Nullable UUID subjectUuid
) {}
```

Detection requirements:

- Piggyback on `SituationDetectors.scan`; no second world tick subscriber.
- Need crises use thresholds plus hysteresis (for example, enter hunger crisis ≤20 and leave ≥30).
- Collapse fires false→true, not on every scan.
- Profession tier fires only on increase.
- Spirit fires on tier increase or classification/primary identity change after one initial observation.
- Building fires on registration, level increase, or removal only when requested by a definition.
- Persist normalized primitive state in `TownsteadSignalStateSavedData` so restarts do not replay “new” events.
- Apply existing definition cooldown/throttle after transition detection.
- Default caps: 8 villages and 64 residents per player/pass, round-robin.

Required bundled situations:

| Suggested id | Trigger | Response |
| --- | --- | --- |
| `townstead/hunger_crisis` | Village hunger fraction above threshold | Emergency food/cook-farmer help. |
| `townstead/dehydrated_worker` | Thirst active and worker reaches crisis | Water delivery; absent when gated. |
| `townstead/exhausted_workforce` | Fatigue crisis fraction | Beds, schedule relief, recovery. |
| `townstead/collapsed_villager` | Collapse transition | Urgent villager-specific aid. |
| `townstead/master_artisan` | Profession tier increase | Celebration/reward chain. |
| `townstead/community_identity` | Spirit identity/classification change | Village celebration/project. |
| `townstead/new_civic_building` | Registered building/level change | Stocking/staffing follow-up. |

Every definition includes availability and exact capability gates.

---

## 8. UI and dialogue coexistence

### 8.1 Canonical entry point

Keep MCA: Quests’ Quests button on MCA `InteractScreen`. Townstead preserves this screen and intercepts its own Talk/Pose buttons, so this avoids both MCA roots and Townstead’s RPG close/camera state.

Harden `McaScreenButtons`:

- Include all visible widgets in collision detection.
- Keep measured rows and test Talk, Interact, Trade, Inventory, Work, Pose, and Quests together.
- Re-add Quests after MCA rebuilds the main layout.
- Send the existing open-menu packet once per click.
- Verify Townstead leaves transition flag, HUD, and camera untouched because RPG dialogue has not opened.
- Retain renderable/child/narratable registration.

### 8.2 RPG dialogue

Do **not** attach a generic Forge button directly to `RpgDialogueScreen`; replacing it with pending text/response can fight Townstead’s reopen/late-content logic.

Optional enhancement, only without overriding MCA dialogue resources:

1. Register additive MCA main answer id `mcaquests_open` via a package-agnostic extension hook.
2. Translate `dialogue.main.mcaquests_open=Quests`.
3. Let Townstead append it as an unknown modded answer.
4. Server intercepts it, ends MCA interaction cleanly, and opens Quests after close acknowledgement.
5. Verify camera/HUD restore before the quest screen.

Do not replace MCA `main` dialogue JSON, statically import either MCA root, or mix into Townstead’s private state machine. If no safe additive hook exists for both roots, ship the fully supported pre-dialogue button and document that quests open before RPG dialogue.

### 8.3 Context display

When relevant, show read-only normalized context in giver/quest details:

- profession name/tier;
- need state used by an objective;
- schedule activity used by an objective;
- building/spirit progress for village objectives.

Send normalized Quests DTO fields. The client does not reflect Townstead or treat its client stores as authoritative. Hide the panel when irrelevant.

### 8.4 UI acceptance

- 320×240-equivalent through 4K; GUI scales 1–4 and auto.
- Pose present/absent; modern/legacy Townstead.
- Mouse, keyboard, narrator, and controller where MCA supports it.
- Reopen MCA screen after quest log; Talk still opens RPG dialogue.
- Enter/exit RPG dialogue, reopen villager, open Quests; HUD/camera remain normal.

---

## 9. Bundled Townstead content

```text
src/main/resources/data/mcaquests/mcaquests/quests/townstead/
src/main/resources/data/mcaquests/mcaquests/projects/townstead/
src/main/resources/data/mcaquests/mcaquests/situations/townstead/
```

Definitions may always ship because types register unconditionally, but all begin with `townstead_available`. They must not surface as broken offers absent Townstead.

### 9.1 Design rules

- Use Townstead state changes rather than duplicating generic item chores.
- Every definition uses profession, life, schedule, needs, buildings, or spirit.
- Secondary mods require explicit gates.
- Thirst content is ineligible when thirst is inactive.
- XP is capped by default.
- Use conservative weights/cooldowns.
- Satisfy the repository locale-parity strategy.

### 9.2 Required quest set

| Chain | Required Townstead mechanics |
| --- | --- |
| Pantry Run | Hungry giver; inventory delivery; verify hunger recovery. |
| Water for the Weary | Thirst capability; delivery; thirst/quenched recovery. |
| A Proper Night’s Rest | High fatigue; Rest schedule; sustained energy recovery. |
| Care for the Young | Life-stage/age gate; food/shelter; no adult offers. |
| First Shift | Apprentice tier; Work schedule; capped XP. |
| Master of the Trade | XP/tier progress, skill check, celebration reaction. |
| Plan the Fields | Farmer, work schedule, registered farm context. |
| Full Granary | Farmer progression, healthy needs, inventory delivery. |
| Mend the Nets | Fisherman, dock building, profession progress. |
| Dockside Catch | Fisherman, dock level, nautical spirit. |
| Shears and Shelter | Shepherd, pen/wool-shed registration. |
| Fill the Wool Shed | Shepherd progress plus building state. |
| Fuel the Smoker | Butcher schedule and supply delivery. |
| Stock the Smokehouse | Butcher building and tier progress. |
| Leatherworker’s Order | Leatherworker progress and supply destination. |
| A Balanced Day | Schedule template/activity sequence with holds. |
| Founding Character | Village spirit absolute/delta objective. |
| Growing Community | Civic building/upgrade plus spirit tier. |
| Healthy Workforce | Aggregate resident hunger/energy. |

For farmer, fisherman, shepherd, butcher, and leatherworker, ship at least three early/mid/late definitions. Use Townstead’s data-driven profession ids rather than only vanilla enums.

### 9.3 Secondary content

Separately gate:

- Farmer’s Delight cook/kitchen;
- Rustic Delight barista/café;
- Butchery butcher/smokehouse;
- Thirst Was Taken support;
- other Townstead-supported building/profession packs only after ids are verified.

Secondary rows are not required for core Townstead success.

### 9.4 Required projects

1. **Raise the Docks** — dock level, fisherman contribution, spirit delta.
2. **Pastures and Wool** — pen/wool shed, shepherd workforce tier.
3. **A Working Village** — qualified workforce across core professions.
4. **Well-Fed Townstead** — sustained resident wellbeing.
5. **Find Our Character** — spirit tier and stable identity/classification.

Combine ordinary contributions with one authoritative Townstead state objective.

---

## 10. Save, reload, and removal

Persist only Quests-owned primitive/JSON/NBT data—never Townstead class names, objects, enum ordinals, or graphs.

### 10.1 Suspension

If an active Townstead objective loads without its mod/capability:

- retain definition, progress, UUID, baseline;
- mark runtime `SUSPENDED_COMPAT`;
- do not poll or auto-fail;
- display “Requires Townstead compatibility”;
- allow abandonment;
- resume the original baseline when compatibility returns.

Do not hide the quest.

### 10.2 Reload/idempotency

- Reload revalidates paths/operators/capability declarations.
- Active instances keep frozen values through definition reload.
- Incompatible schema changes use existing orphan behavior plus migration warning; never silently initialize.
- `TownsteadSignalStateSavedData` has schema version and deterministic migration.
- Persist per-reward applied state before client acknowledgement so restart cannot double-apply.

---

## 11. Configuration

Server/common defaults:

```text
compat.townstead.enabled = true
compat.townstead.contentEnabled = true
compat.townstead.reactionsEnabled = true
compat.townstead.needRewardsEnabled = true
compat.townstead.professionXpRewardsEnabled = true
compat.townstead.skillRewardsEnabled = true
compat.townstead.allowUncappedProfessionXp = false
compat.townstead.rewardFailureBlocksCompletion = false
compat.townstead.pollIntervalTicks = 20
compat.townstead.projectPollIntervalTicks = 20
compat.townstead.maxVillagersPerPass = 64
compat.townstead.maxVillagesPerPass = 8
compat.townstead.needCrisisHysteresis = 10
compat.townstead.debugBindingLogs = false
```

Client:

```text
client.showQuestButtonInMcaMenu = true
client.showTownsteadQuestContext = true
```

Validate intervals ≥10 ticks, residents 1–256, villages 1–64, and sane hysteresis. Document restart/reload requirements. Feature toggles affect content/actions, not safe reads/diagnostics.

---

## 12. Diagnostics

```text
/mcaquests compat townstead status
/mcaquests compat townstead probe
/mcaquests compat townstead snapshot <villager>
/mcaquests compat townstead explain <quest-id>
```

- `status`: version, variant, status, capabilities, feature toggles.
- `probe`: non-mutating binding checks by contract; permission level 2.
- `snapshot`: normalized quest-author values, not object internals; permission level 2.
- `explain`: why each Townstead condition/objective is eligible, false, suspended, or missing.

Normal player messages use friendly language, not reflection terminology.

---

## 13. Documentation deliverables

Update:

- `README.md`: optional compatibility, versions/artifacts, headline features.
- `DATAPACK.md`: full schemas/defaults/operators/targets/baselines/examples.
- Existing config documentation: all new settings.
- `CHANGELOG.md`: user-visible release notes.
- `assets/mcaquests/lang/*.json`: UI/content/diagnostic strings with locale parity.
- New `TOWNSTEAD.md`: installation matrix, author guide, troubleshooting, status/capabilities, secondary gates.

Name both Townstead 1.20.1 artifact families prominently.

---

## 14. Concrete file-change map

Adapt names to current conventions but preserve responsibilities.

### 14.1 Existing files/areas

| Area | Change |
| --- | --- |
| `McaQuests.java` | Initialize `TownsteadCompat` after mod-list availability, before data systems need it. |
| `META-INF/mods.toml` | Add optional dependency. |
| Objective/condition/reward registries | Register all Townstead types unconditionally. |
| Quest context/evaluation | Add one lazy Quests-owned Townstead cache per pass. |
| `QuestProgressEvents` | Poll new types through the existing bounded pass. |
| `ProjectManager` | Add `PollingProjectObjective` pass and registry entries. |
| Lifecycle mutation points | Dispatch phases once after commit. |
| `SituationSignalType`, `TriggerSignal`, `SituationDetectors` | Add versioned context, signals, budgets, persistent transition state. |
| Item delivery | Add safe destination transaction; preserve `consume` default. |
| `McaScreenButtons` | Harden coexistence/collision behavior; no Townstead linkage. |
| Network DTOs/screens | Add normalized context only where relevant. |
| Config | Add §11 entries/validation. |

### 14.2 New production files

In addition to §3.1:

```text
quest/condition/TownsteadAvailableCondition.java
quest/condition/TownsteadValueCondition.java
quest/condition/TownsteadBuildingCondition.java
quest/condition/TownsteadSpiritCondition.java
quest/condition/TownsteadSkillCondition.java
quest/objective/TownsteadStateObjective.java
quest/objective/TownsteadChangeObjective.java
quest/objective/TownsteadProfessionProgressObjective.java
quest/objective/TownsteadBuildingRegisteredObjective.java
quest/objective/TownsteadSpiritProgressObjective.java
quest/objective/TownsteadHealthyResidentsObjective.java
quest/reward/TownsteadNeedsReward.java
quest/reward/TownsteadProfessionXpReward.java
quest/reward/TownsteadSkillReward.java
quest/reward/TownsteadReactionReward.java
project/PollingProjectObjective.java
project/objective/TownsteadBuildingProjectObjective.java
project/objective/TownsteadSpiritProjectObjective.java
project/objective/TownsteadWorkforceProjectObjective.java
project/objective/TownsteadResidentWellbeingProjectObjective.java
situation/TownsteadSignalStateSavedData.java
command/TownsteadCompatCommands.java
```

Use existing codecs/parsers and saved-data patterns; do not add a parallel framework.

### 14.3 Test files/source sets

```text
src/test/java/.../NoTownsteadStaticLinkTest.java
src/test/java/.../TownsteadQueryEngineTest.java
src/test/java/.../TownsteadBaselineTest.java
src/test/java/.../TownsteadProfessionXpMathTest.java
src/test/java/.../TownsteadMutationIdempotencyTest.java
src/test/java/.../TownsteadSignalTransitionTest.java
src/test/java/.../TownsteadAbsentPackTest.java
src/test/java/.../TownsteadScreenLayoutTest.java
src/townsteadProbeTest/...
```

Extend existing pack/locale tests rather than duplicating them where practical.

---

## 15. Implementation sequence

Each milestone compiles and passes focused tests before the next.

### Milestone 1 — Optional boundary

1. Add optional metadata.
2. Implement status/capabilities, interface, no-op bridge, guarded bootstrap.
3. Add static-link and absent-mod boot tests.

**Exit:** Quests-only suite passes; Townstead absence is silent.

### Milestone 2 — Read-only facade

1. Bind public API/snapshot accessors.
2. Normalize views and implement query engine.
3. Add evaluation cache and status/probe/snapshot diagnostics.
4. Probe modern and legacy jars.

**Exit:** equivalent normalized snapshots on both variants; all `READ_*` capabilities green.

### Milestone 3 — Conditions and polling

1. Register conditions.
2. Implement state/change/profession/building/spirit/healthy-resident objectives.
3. Persist baselines/suspension.
4. Extend pack parsing tests.

**Exit:** reload works present/absent; restart/baseline tests pass.

### Milestone 4 — Actions and lifecycle

1. Bind needs, XP, skills, reactions.
2. Implement rewards with caps/idempotency.
3. Dispatch quest phases after commit.
4. Implement atomic villager-inventory delivery.

**Exit:** mutation/reward GameTests pass on both variants; retries cannot double-apply.

### Milestone 5 — Projects and situations

1. Add bounded polling-project framework.
2. Add Townstead project objectives/contribution rules.
3. Add persistent transition detectors/signals.
4. Add project/situation reactions.

**Exit:** no restart replay/spam; budgets proven.

### Milestone 6 — UI/content

1. Harden canonical button coexistence.
2. Add normalized context panels.
3. Ship quests/projects/situations/translations.
4. Attempt enhanced dialogue answer only if §8.2 safe criteria pass.

**Exit:** content is absent/ineligible without Townstead and fully playable on both variants.

### Milestone 7 — Release hardening

1. Full matrix, dedicated server, removal/re-add, profiling.
2. Documentation and compatibility report.
3. Record tested jar hashes/versions.

**Exit:** §19 fully checked.

---

## 16. Test requirements

### 16.1 Unit tests

- Query parsing, limits, record/map/list access, all operators/types.
- Missing policy and invalid regex rejection.
- Baseline freeze/delta/hold/reset/UUID persistence.
- Hunger/thirst/fatigue clamp/conversion.
- XP same/new day, cap, max, multi-tier jump, retry, tier-up tick.
- Skill idempotency.
- Capability failure isolation.
- Signal hysteresis and transition-only firing.
- Saved signal migration and restart non-replay.
- Atomic inventory simulation/commit/rollback.

### 16.2 Classloading/pack tests

- Production passes `NoTownsteadStaticLinkTest`.
- Registries and every built-in definition parse with no Townstead classpath.
- Every bundled definition has availability/exact capability gates.
- Extend `BuiltinPackParsesTest` and `LocaleParityTest`.
- Active Townstead progress serializes/deserializes with bridge absent.

### 16.3 Binding-probe harness

Run each external Townstead jar in a fresh classloader/process, isolated from normal tests:

```text
./gradlew townsteadProbeTest \
  -PtownsteadModernJar=/path/townstead-mca-modern-0.7.7.jar \
  -PtownsteadLegacyJar=/path/townstead-mca-legacy-0.7.7.jar
```

For each assert metadata/version, variant detection, every member binding, record mappings, internal action selection without MCA literals, `FULL` status, and no relocated-root production descriptors.

### 16.4 GameTests/in-game automation

1. Spawn MCA villager; read complete snapshot.
2. Hunger recovery freezes baseline and completes only on observed recovery.
3. Thirst gates off inactive and works active.
4. Fatigue/rest hold resets when interrupted.
5. XP respects cap/tier progression.
6. Skill reward is idempotent.
7. Registered building/upgrade completes; lookalike blocks do not.
8. Spirit delta/tier/classification works.
9. Resident aggregate respects minimum/cap.
10. Collapse/tier/building/spirit signals fire once and do not replay after restart.
11. Inventory delivery is exact-once; capacity failure is atomic.
12. Active quest survives removal, suspends, resumes original baseline when restored.
13. Completion succeeds if reaction dispatch throws.

---

## 17. Performance budgets

Measure on a dedicated server with at least 100 MCA villagers across villages.

- Townstead absent: no measurable extra tick cost beyond a cheap status/reference check in an existing pass.
- No reflection lookup after bootstrap.
- Townstead polling/situations average under 1 ms/tick and no integration scan spike above 5 ms on reference hardware.
- Network only on visible progress/context change.
- No per-tick allocation proportional to all villagers; use budgets/round-robin.
- Diagnostics may be more expensive but are on demand.

Debug counters (not INFO spam): polls, entities/villages observed, cache hits, capability misses, signals, mutation failures, average/max scan time.

---

## 18. Release compatibility matrix

| MCA: Quests | MCA root | Townstead | Secondary mods | Client | Dedicated server | Result |
| --- | --- | --- | --- | --- | --- | --- |
| New | legacy supported MCA | absent | none | ✓ | ✓ | Existing behavior unchanged. |
| New | modern supported MCA | absent | none | ✓ | ✓ | Existing behavior unchanged. |
| New | legacy | `townstead-mca-legacy` 0.7.5/0.7.6/0.7.7 as available | none | ✓ | ✓ | `FULL`; core scenarios pass. |
| New | modern | `townstead-mca-modern` 0.7.5/0.7.6/0.7.7 as available | none | ✓ | ✓ | `FULL`; core scenarios pass. |
| New | matched | corresponding Townstead | Farmer’s Delight | ✓ | ✓ | Core plus cook/kitchen. |
| New | matched | corresponding Townstead | Rustic Delight | ✓ | ✓ | Core plus barista/café. |
| New | matched | corresponding Townstead | Butchery | ✓ | ✓ | Core plus butcher content. |
| New | matched | corresponding Townstead | supported thirst mod | ✓ | ✓ | Thirst content/rewards active. |
| New | matched | Townstead removed after save | none | ✓ | ✓ | Loads; active content suspends/abandons. |
| New | matched | Townstead restored | none | ✓ | ✓ | Original baselines resume; no duplicates. |

Test mismatched Townstead/MCA too: expect a clear loader incompatibility, not a Quests crash or misleading `FULL`.

### 18.1 Manual UI checklist per variant

- Quests button visible on MCA main screen and overlaps nothing at all GUI scales.
- It survives MCA layout rebuilds.
- Quest log does not hide HUD/change camera.
- Talk still opens RPG dialogue afterward; dialogue restores camera/HUD.
- Details show only relevant normalized context.
- Narrator reads button/context meaningfully.

---

## 19. Definition of done

- [ ] One Quests jar runs with Townstead absent, modern, and legacy.
- [ ] Optional metadata/range is correct.
- [ ] Production has no Townstead or relocated-MCA descriptors.
- [ ] Required reads/actions report `FULL` on supported artifacts.
- [ ] Conditions cover needs, schedule, life/root/gene, profession, calendar, buildings, skills, spirit.
- [ ] Objectives cover state/delta, professions, buildings, spirit, resident wellbeing.
- [ ] Projects have bounded polling and Townstead objectives.
- [ ] Rewards safely mutate needs, XP, skills exactly once.
- [ ] Delivery atomically inserts into target villager inventory.
- [ ] Quest/project/situation phases dispatch reactions after commit.
- [ ] Situation transitions are persistent, hysteretic, throttled, budgeted.
- [ ] Active content survives removal and resumes without reset/duplication.
- [ ] MCA UI coexists with Townstead Talk/Pose on both variants.
- [ ] Required content is polished, gated, localized, and parses absent Townstead.
- [ ] Both artifacts pass probes, GameTests, dedicated server, manual UI.
- [ ] README, DATAPACK, config, Townstead guide, translations, changelog are complete.
- [ ] 100-villager performance targets pass.

---

## 20. Risk register

| Risk | Impact | Mitigation/release gate |
| --- | --- | --- |
| Dual MCA roots leak through descriptor | Variant-specific class error | String binding, runtime assignability, constant-pool test, both probes. |
| Townstead 0.x internals change | Action/reaction failure | Version range, capabilities, narrow adapters, point probes, actionable warning. |
| Polling multiplies | Tick regression | Shared cache, scan reuse, budgets, round-robin, performance test. |
| Baseline resets | False completion/exploit | Frozen NBT schema; no implicit baseline; restart tests. |
| XP bypasses caps | Progression exploit | Progression/day-cap math; uncapped needs dual opt-in. |
| Need state desyncs | UI/progress confusion | Townstead setters, authoritative re-read, sync acceptance test. |
| RPG screen replacement | Stuck HUD/camera | Canonical pre-dialogue button; only safe additive answer. |
| Removal breaks active quests | Save loss | Primitive serialization, unconditional types, suspension. |
| Signals replay after restart | Spam | Persistent observations, first-observation suppression, cooldown. |
| Delivery duplicates/voids items | Economy/save damage | Simulate, same-tick commit, rollback, marker, capacity tests. |

---

## 21. Coding-agent instructions

1. Start from recorded SHAs or update the baseline in the PR.
2. Preserve unrelated changes and existing compat behavior.
3. Reuse registries, codecs, targets, saved data, packets, config, and tests.
4. Keep reflection in `TownsteadBindings`/`ReflectiveTownsteadBridge`; gameplay consumes typed Quests views.
5. Bind once and branch cheaply; reflection failures become capability data.
6. Catch at the optional bridge boundary, not around a whole tick pass.
7. Do not ship guessed ids. Extract profession/building/skill/gene/root/spirit ids from Townstead data and test every bundled id.
8. Keep defaults backward-compatible.
9. Commit by boundary → reads → objectives → actions → projects/situations → UI/content/docs.
10. Attach matrix results, both probe outputs, commands, jar hashes, and performance comparison to the PR.

---

## 22. Source index

- [MCA: Quests](https://github.com/otectus/MCAQuests)
- [MCA: Quests `McaScreenButtons`](https://github.com/otectus/MCAQuests/blob/1378ecece9332ad398882ffd8f314b8791644c67/src/main/java/dev/otectus/mcaquests/client/McaScreenButtons.java)
- [MCA: Quests `QuestDialogueHooks`](https://github.com/otectus/MCAQuests/blob/1378ecece9332ad398882ffd8f314b8791644c67/src/main/java/dev/otectus/mcaquests/api/QuestDialogueHooks.java)
- [Townstead](https://github.com/AetherianArtificer/Townstead)
- [Townstead public `TownsteadAPI`](https://github.com/AetherianArtificer/Townstead/blob/4d6206cdf8b9d0f558694d7b35b223f4f6ace61e/src/main/java/com/aetherianartificer/townstead/api/TownsteadAPI.java)
- [Townstead public `TownsteadQuery`](https://github.com/AetherianArtificer/Townstead/blob/4d6206cdf8b9d0f558694d7b35b223f4f6ace61e/src/main/java/com/aetherianartificer/townstead/api/TownsteadQuery.java)
- [Townstead `ProfessionProgressions`](https://github.com/AetherianArtificer/Townstead/blob/4d6206cdf8b9d0f558694d7b35b223f4f6ace61e/src/main/java/com/aetherianartificer/townstead/villager/ProfessionProgressions.java)
- [Townstead `LearnedSkills`](https://github.com/AetherianArtificer/Townstead/blob/4d6206cdf8b9d0f558694d7b35b223f4f6ace61e/src/main/java/com/aetherianartificer/townstead/profession/skill/LearnedSkills.java)
- [Townstead `VillageSpiritAggregator`](https://github.com/AetherianArtificer/Townstead/blob/4d6206cdf8b9d0f558694d7b35b223f4f6ace61e/src/main/java/com/aetherianartificer/townstead/spirit/VillageSpiritAggregator.java)
- [Townstead `ReactionDispatcher`](https://github.com/AetherianArtificer/Townstead/blob/4d6206cdf8b9d0f558694d7b35b223f4f6ace61e/src/main/java/com/aetherianartificer/townstead/reaction/ReactionDispatcher.java)
- [Townstead `DialogueMenuOrganizer`](https://github.com/AetherianArtificer/Townstead/blob/4d6206cdf8b9d0f558694d7b35b223f4f6ace61e/src/main/java/com/aetherianartificer/townstead/client/gui/dialogue/DialogueMenuOrganizer.java)
- [Townstead `RpgDialogueScreen`](https://github.com/AetherianArtificer/Townstead/blob/4d6206cdf8b9d0f558694d7b35b223f4f6ace61e/src/main/java/com/aetherianartificer/townstead/client/gui/dialogue/RpgDialogueScreen.java)
- [Townstead `InteractScreenMixin`](https://github.com/AetherianArtificer/Townstead/blob/4d6206cdf8b9d0f558694d7b35b223f4f6ace61e/src/main/java/com/aetherianartificer/townstead/mixin/InteractScreenMixin.java)

If implementation finds a conflict with pinned source, source wins. Update the spec/PR with file, SHA, changed assumption, and design adjustment.
