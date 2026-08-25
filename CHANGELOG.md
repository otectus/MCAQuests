# Changelog

All notable changes to **MCA: Quests** are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.0] - 2026-08-25

### Added — Townstead integration (optional)

**[Townstead](https://www.curseforge.com/minecraft/mc-mods/townstead) gives MCA villagers needs, shifts,
professions, skills and ancestry, and gives villages a character of their own. With it installed, all of
that becomes something a quest can be about.** Feed a farmer who has not eaten since the fields flooded
and watch Townstead's own simulation register the meal. Hold someone to their rest through the night.
Take an apprentice to master of their trade. Raise a dock. Keep a whole village fed through winter.
Twenty-five quests, five village projects and seven situations ship with it, and the whole surface is
open to datapacks: **five conditions, six objectives, four project objectives, four rewards, five
situation triggers and a delivery destination**. Entirely optional — see
**[TOWNSTEAD.md](TOWNSTEAD.md)**.

- **Bread you hand over actually arrives.** `mcaquests:item_delivery` takes a new `destination`; set it
  to `townstead_villager_inventory` and the goods go **into that villager's inventory**, where Townstead
  lets them be eaten and used, instead of being destroyed on hand-over. The transfer is
  all-or-nothing — a turn-in that will not fit is refused with a message rather than half-completing,
  and the goods are never taken twice. The default, `consume`, is what every existing pack already does.
- **Conditions** — `mcaquests:townstead_available`, `townstead_value`, `townstead_building`,
  `townstead_spirit`, `townstead_skill`.
- **Objectives** — `mcaquests:townstead_state`, `townstead_change`, `townstead_profession_progress`,
  `townstead_building_registered`, `townstead_spirit_progress`, `townstead_healthy_residents`.
- **Project objectives** — `mcaquests:townstead_building_project`, `townstead_spirit_project`,
  `townstead_workforce_project`, `townstead_resident_wellbeing_project`. These are the first **polled**
  project objectives: they read village state on a sweep rather than banking a donation, so they
  progress — and regress — with the village itself.
- **Rewards** — `mcaquests:townstead_needs`, `townstead_profession_xp`, `townstead_skill`,
  `townstead_reaction`.
- **Situation triggers** — `mcaquests:townstead_need`, `townstead_collapse`,
  `townstead_profession_tier`, `townstead_spirit`, `townstead_building`.
- **One query language across all of it.** A `source` (`villager`, `calendar`, `building`, `spirit`,
  `root`, `gene`), a dot `path`, an `operator` and a `value`, with a `missing` answer that defaults to
  **false** so an unreadable value makes content *ineligible* rather than accidentally satisfied.
  Regexes are compiled at load, so a broken one fails the reload rather than a quest.
- **Baselines are frozen when the quest is accepted**, not read fresh on every check, so "raise their
  hunger by 40" means forty from where they were when you took the job. Stored with the quest, which is
  what lets one survive Townstead being absent without silently re-basing itself.
- **New commands.** `/mcaquests compat townstead status` reports the detected version and which of the
  thirteen capabilities bound; `probe` exercises each one for real; `snapshot` prints a nearby
  villager's state **using the exact paths a condition takes**, so its output pastes straight into a
  datapack.
- Network protocol bumped to `10`.

### Added — quests suspend instead of failing

**Removing a mod should not destroy the work you have already done.** A quest whose objectives need
Townstead now *suspends* when Townstead is absent, disabled or unbound, rather than failing:

- It keeps its progress and its frozen baselines, and resumes exactly where it was if Townstead comes
  back.
- **It stops counting down.** Suspended time is accumulated on the quest and subtracted at the
  comparison, so a deadline is not eaten by an absence. Applied at the comparison rather than by
  shifting the accept time, because `deadline_time_of_day` anchors on the hour you accepted.
- **It can still be abandoned.** Suspension is derived rather than stored as a new menu status,
  precisely so the existing card buttons keep working — a new status would have silently made suspended
  quests unabandonable.
- The quest log says **why**, from the objective itself, rather than showing a stalled bar with no
  explanation.

### Added — the bundled Townstead content

- **Twenty-five quests** across five professions, from a pantry run to *The Master Tanner*, including
  *A Proper Night's Rest*, *Water for the Weary*, *Deep Water Days*, *The Long Harvest* and
  *Master of the Trade*.
- **Five village projects** — *A Working Village*, *Well-Fed Townstead*, *Raise the Docks*,
  *Pastures and Wool*, *Find Our Character*.
- **Seven situations** — hunger crisis, dehydrated worker, exhausted workforce, collapsed villager,
  master artisan, new civic building, community identity.
- All of it is behind `contentEnabled`, so a server can keep the mechanics for its own datapacks
  without the built-in content competing for menu slots.

### Added — the quest log shows the state a quest is about

- A Townstead quest's card carries a short read-only summary: the villager's trade and tier, the need or
  schedule the quest turns on, the village's spirit. **Only what that quest actually reads is shown**,
  and a quest that is not about Townstead state shows nothing. New client option
  `showTownsteadQuestContext` (default `true`) hides it for you alone.

### Changed

- **Fourteen new common config options under `[compat.townstead]`** — `enabled`, `contentEnabled`,
  `reactionsEnabled`, `needRewardsEnabled`, `professionXpRewardsEnabled`, `skillRewardsEnabled`,
  `allowUncappedProfessionXp`, `rewardFailureBlocksCompletion`, `pollIntervalTicks`,
  `projectPollIntervalTicks`, `maxVillagersPerPass`, `maxVillagesPerPass`, `needCrisisHysteresis`,
  `debugBindingLogs` — plus the client option `showTownsteadQuestContext`. See
  [CONFIG.md](CONFIG.md).
- **Bypassing Townstead's own pacing takes two keys, not one.** An XP reward that asks to skip the daily
  cap, or a skill reward that asks to skip prerequisites, is honoured only when the server has also set
  `allowUncappedProfessionXp`. A datapack alone should not be able to undo the progression pacing
  Townstead deliberately sets on somebody else's server.
- **A Townstead reward that cannot be applied is skipped and the quest still completes.** The player has
  already done the work, and trapping them with a finished quest they can never hand in is worse than
  quietly missing the villager-facing half of the reward. `rewardFailureBlocksCompletion` reverses this.
- **Villager and village sweeps are bounded and round-robin.** At most `maxVillagersPerPass` residents
  and `maxVillagesPerPass` villages are inspected per pass, continuing where the last pass stopped, so
  nobody is skipped and no single tick is unbounded on a large server.
- **Need crises are banded.** A crisis opens at its threshold and closes only once the village recovers
  past `needCrisisHysteresis` percentage points, so a village sitting exactly on the line does not flap
  the same famine on and off every scan.
- **The Quests button in MCA's villager menu now avoids every widget, not only buttons.** It looked for
  `Button` instances at a similar height; Townstead adds its own controls to that screen (Pose among
  them), which the old check could not see, and it compared top edges rather than testing whether the
  rows actually overlap. Both are fixed, so the button places itself correctly beneath whatever is
  there.
- **Reactions are cosmetic, always.** Quest, project and situation lifecycle transitions play Townstead
  reactions automatically; a reaction that fails never blocks a completion, and `mcaquests:townstead_reaction`
  is an extra flourish rather than a mechanism.
- Six members were added to the MCA binding manifest — `Village#getBuildings`, `#getBuildingsOfType`, and
  `Building`'s `getId`/`getType`/`getSize`/`getCenter`. **Village-wide building reads belong to MCA, not
  Townstead**, which only adds type ids to buildings MCA already owns; routing them through MCA means the
  same code serves a vanilla-MCA library and a Townstead dock.
- **314 new translation keys**, shipped complete in both `en_us` and `pt_br` as always. No existing key
  was reworded or removed.

### Fixed

- **Loading a younger single-player world stopped situation detection permanently.** The "is a
  server-wide pass due" guards are static and survive a world change, so after loading a world whose
  game time was *lower* than the last one's, `now - lastScan` stayed negative for as long as it took to
  catch up — which for a fresh world after a long-lived one is effectively forever. Both that guard and
  the new project sweep now tolerate time going backwards. This bug predates 1.4.0.

### Compatibility

- **Townstead `[0.7.5,0.8)`, verified against 0.7.6**, declared in `mods.toml` as a soft optional
  dependency (`mandatory=false`, `ordering="AFTER"`). MCA: Quests loads and plays exactly as before
  without it.
- **Nothing is bound by parameter type, and the jar contains zero Townstead code.** Townstead is itself
  built against MCA, so its own method signatures name MCA classes — binding any of them directly would
  tie this mod to one MCA package layout and undo the runtime resolution added in 1.3.0. Members are
  therefore matched by **name and arity** and invoked through handles whose arguments are all `Object`.
  A build-time tripwire scans every compiled class's constant pool and fails the build if one so much as
  mentions a Townstead type.
- **Binding reports capabilities, not a yes or no.** Thirteen of them, resolved independently, so a
  Townstead point release that moves one internal method disables exactly the feature that needed it and
  leaves the rest working. An unresolved member becomes an inert stub; nothing can throw.
- **Removing Townstead from an existing world is safe.** Active Townstead quests suspend with their
  progress and baselines intact and stay abandonable; datapack types stay registered either way, so a
  pack always parses. See [TOWNSTEAD.md](TOWNSTEAD.md) for the full removal contract.
- **Protocol `9` → `10`.** `QuestLogEntry` gained a `suspended` flag and a list of context lines, so the
  client and server must be on matching versions — as always for a protocol bump.
- **Save format — additive only.** `ActiveQuest` gained `suspended_ticks` (read as `0` when absent) and
  `SharedObjectiveProgress` gained an `extra` compound that is **written only when non-empty**, so a
  project that never polled serialises byte-for-byte as it did in 1.3.0. No migration is needed in
  either direction.
- **Add-on API — `SituationSignalType` gained five constants.** They are **appended**, never inserted:
  the ordinal is one term of the per-village situation draw seed, so inserting above would silently
  reshuffle which situation an existing village opens on an existing day. An exhaustive `switch` over
  this enum without a `default` will no longer compile; add one.
- **Add-on API — `QuestObjective` gained a defaulted `unavailableReason(...)`.** It is what a suspended
  quest shows in the log instead of a stalled bar. Defaulted, so nothing breaks.
- **Add-on API — `ItemDeliveryObjective` gained a `destination` component.** Its three-argument
  constructor is kept as a convenience that defaults to `CONSUMED`, so existing add-on code compiles
  unchanged; `TriggerSignal` likewise keeps its previous-arity constructor beside the widened one.
- `mcaquests.mixins.json` is unchanged and still targets only vanilla classes — the Townstead
  integration adds no mixins at all.

### Notes for maintainers

- `./gradlew townsteadProbeTest -PtownsteadLegacyJar=<path to townstead jar>` binds against a real
  Townstead jar and reports every capability, catching a signature change before players do. It is not
  part of `check`, because it needs a jar the repository does not ship.
- **The MCA binding probe now replays the manifest against every known package root on every run**, not
  only whichever build the dev runtime happened to pin. `mca_probe_versions` lists one MCA version per
  root — `7.6.20` and `7.7.0-beta.2` are `forge.net.mca`, `7.7.1-alpha.2` is
  `forge.net.conczin.mca` — each resolved through its own configuration, because two versions of one
  module in a shared configuration would be collapsed to the newer by Gradle's conflict resolution and
  the older root would go untested. That is exactly how the missing `forge.net.conczin.mca` root reached
  players in the first place. Add a version there whenever MCA moves again.
- With `debugBindingLogs` on, `/mcaquests compat townstead status` adds a counters line — reads, cache
  hits, villages and residents observed, signals fired, capability misses, mutation failures, and
  average and maximum scan time — for checking the performance budget on a large server.
- **The end-to-end scenarios and the installation matrix are a checklist in
  [TOWNSTEAD.md](TOWNSTEAD.md), not an automated suite.** MCA cannot load in a dev run (its Forge mixins
  ship refmap-less with hard-coded SRG names), so a GameTest cannot reach an MCA villager at all. The
  arithmetic that those tests would otherwise have been the only cover for — the XP award algorithm, the
  per-need clamps, the crisis hysteresis band — is extracted into pure functions and unit-tested
  directly instead.

## [1.3.0] - 2026-08-25

### Added — Journal link into MCA: Reputation

- **The Journal links to MCA: Reputation's standing screen (§29.7).** With Reputation installed, each
  village row in the Journal carries a **[View Deeds]** link that opens Reputation's screen for that
  exact village — the same screen everyone else uses, so the two can never disagree. The server
  validates that the player actually knows the named village before opening anything, and Reputation
  sends a fresh snapshot ahead of the push. Without Reputation the link simply is not offered.
- Journal village entries now carry the village's **dimension and id** on the wire, so the client can
  name a community without guessing. Network protocol bumped to `8`.

### Added — family quests bind a real villager, and you can find them

**Quests whose text is about the giver's family used to reference nobody**: the relative existed only in
the dialogue, so there was no one to look for and nothing to highlight. Every one of them now names an
actual MCA villager, and the mod shows you where that villager is.

- **Per-player quest-target highlighting.** The villager a quest wants you to find is **outlined through
  walls** while it is loaded — for you and nobody else. A quest whose objectives target no one in particular
  (the many errands written in the first person: "bring *me* six loaves") outlines the **giver** instead, so
  every active quest points at a real person. Toggle with `highlightQuestTargets`.
- **A direction cue on the quest tracker.** A new HUD line names the target with a live distance and an
  eight-point compass bearing — "Hans — 84 blocks to your right" — updating as you turn. When the target is
  too far away to be loaded it falls back to their last known home ("last seen 310 blocks ahead-left"), and
  it hides once the quest is ready to hand in. Toggle with the new client option
  `showQuestTargetDirection`.
- **New objective `mcaquests:find_missing_relative`, and missing kin you can actually find.** MCA defines a
  *missing* relative as one who exists in the family tree with **no entity anywhere in the world** — so "my
  child wandered off" quests could never involve the child. Searching the named biome or structure, far
  enough from the giver, now **materialises them**: same UUID, name, gender, profession and every family
  link intact, held safe until you reach them, and highlighted the moment they appear. Never spawns twice,
  and never spawns a relative who is merely unloaded. *A Parent's Plea*, *Search the Old Tunnels* and stage
  two of the *Bring Them Home* arc are rebuilt around it. **Finding someone is permanent** — abandoning or
  failing the quest drops the quest, never the villager.
- Five family quests now hand their goods to the relative they talk about instead of to thin air:
  *A Family Feast*, *In Loving Memory*, *Mind the Apprentice*, *Guest of Honour* and *Meeting the Family*.
  Their offer and in-progress dialogue is reworded to match ("hand it to them yourself"), as are the
  completion lines of the two search quests, which now describe finding the relative rather than merely
  looking.
- *The Sickness Among Us* and *While They Gather Flowers* carried **no conditions at all** and could be
  offered by a villager with no such relative to cure or protect. Both are now gated on
  `related_villager_status`.
- `"mode": "family"` targets and `related_villager_status` both accept **`grandparent`** (a two-hop walk
  through the family tree, deliberately not part of `any`), and `related_villager_status` also accepts
  **`any`** — so a quest can gate on exactly the relation it then targets.
- New config: `highlightUsesGlowingEffect` (common, default `false`) and `showQuestTargetDirection`
  (client, default `true`). See [CONFIG.md](CONFIG.md).
- Brazilian Portuguese ships complete as always: 12 new keys (the HUD target line, the eight compass
  bearings, the new objective's two forms, and the `grandparent` relation label) plus every reworded line,
  in both locales.
- Network protocol bumped to `9` for the highlight packet and the tracker's target hint.

### Fixed — MCA: Quests crashed a server on MCA 7.7

**Right-clicking any entity killed a dedicated server** running MCA Reborn 7.7.1-alpha.2:

```
java.lang.NoClassDefFoundError: forge/net/mca/entity/VillagerEntityMCA
    at dev.otectus.mcaquests.event.QuestProgressEvents.onTalkToVillager(QuestProgressEvents.java:652)
    at net.minecraftforge.common.ForgeHooks.onInteractEntity(ForgeHooks.java:765)
```

MCA repackaged mid-7.7-line. Through 7.6.20 it shipped a Forgix-merged jar whose Forge classes live at
`forge.net.mca.*`; a later 7.7 build dropped the merge and renamed the base package. `McaCompat`
imported the old root directly, so on a renamed build the very first MCA reference failed to link — and
it failed inside an `EntityInteract` handler, which is why a right-click was enough to take the server
down. `mods.toml` accepts `[7.6,8)`, so Forge admitted the combination rather than refusing it.

- **MCA is now resolved by name at runtime, and one jar serves every layout.** New
  `compat.mca.McaBinding` probes `forge.net.mca.` → `net.conczin.mca.` → `net.mca.` for MCA's villager
  class and binds a manifest of ~60 classes and members against whichever root matched. **The root is
  never inferred from the version number** — 7.7.0-beta.2 still ships `forge.net.mca` while later 7.7
  builds do not, so only a class probe can tell.
- **No MCA type is named anywhere in the mod any more.** `compat.mca.McaHandles` presents MCA entirely
  in vanilla and JDK types, MCA enums included (reads return the lowercase `name()`).
- **Nothing can crash.** An unresolved member becomes a constant stub returning that type's default, so
  there are no null handles and no NPE path; the resolver never throws, and all ~60 `McaCompat` methods
  now carry a `try`/`catch` — ten of them, `isMcaVillager` among them, previously had none at all.
  Fully unbound, the mod is inert but installed: no quest is offered, no villager menu opens, no hearts
  move, and the server stays up.
- **New `/mcaquests debug mca`** reports the matched root and anything in the manifest that did not
  resolve. Ask for this first on any MCA-shaped bug report.
- Startup logs the binding outcome exactly **once** — never per call, which a partially-bound MCA would
  otherwise turn into a flood during an eligibility pass.

### Fixed — escort quests could be turned in without doing them

**Some quests were free emeralds.** Nothing asked whether a quest's objectives were *already* satisfied
before offering it. `escort_entity` freezes its destination on the first poll, about a second after
accept, and evaluates arrival in the same call — so a villager standing at the destination completed the
quest before the player moved. Accept it, hand it straight back to the giver standing right there,
collect currency, XP and hearts, and repeat every cooldown. *Walk Me to Bed*, offered at night by a
villager already at their bed, was the worst case; seven shipped quests were affected.

- **Such a quest is no longer offered.** `QuestManager.eligibleOffers` now drops any quest whose
  objectives report themselves already satisfied for this player and giver.
- **And it cannot be credited even if it is granted another way.** `escort_entity` and `reach_location`
  refuse to credit arrival until the subject has genuinely been away from the destination. This is what
  covers a quest that never passed the offer gate — a chain stage, or one granted by command.
- **New objective field `min_journey`** on `escort_entity` and `reach_location`: how far the subject must
  *start* from the destination for the trip to count. Defaults to the new `minEscortJourney` config
  (24 blocks), so third-party datapacks that never added a distance guard are fixed too. See
  [DATAPACK.md](DATAPACK.md) and [CONFIG.md](CONFIG.md).
- Six shipped quests gained an explicit `min_journey`: *Walk Me to Bed*, *Night Pilgrimage* and
  *Guide the Surveyor* (32), *A Last Walk* and *Reunite with Spouse* (24), and *Walk Together* (64) —
  the last of which had **no conditions at all**.
- *Escort to Market* was only half-guarded: its `giver_distance_from_village` measured 64 blocks from the
  village **centre**, so a giver that far out but still inside the border resolved `nearest_village` to
  the village it was already standing in. It now also sets `require_outside_border`.
- *Reunite with Spouse* required the spouse to be `nearby` — within about 12 blocks — while asking you to
  escort the giver *to* them, so it was a free reward by construction regardless of any distance floor.
  It now gates on `same_village`, which keeps the spouse findable while letting the walk be a walk.

### Changed

- **A family delivery must now go to the villager the quest named.** Family targets are bound to one
  specific relative when the quest is accepted and never re-resolve. MCA's family lookup prefers whichever
  relative happens to be *loaded*, so without that binding a giver with two children could have the quest
  log naming one, the highlight following another, and the hand-off crediting either. The trade-off is that
  handing the parcel to a *different* sibling no longer counts. Only `family` targets bind — `profession`
  stays live so a quest cannot dead-end when the smith it picked wanders off, and `escort_entity` continues
  to pin its escortee in every mode. A quest already in flight from an older save binds on its next tick;
  no save migration is needed.
- Quest-target highlighting is now drawn per-player instead of applying a real Glowing **status effect** to
  the villager. That effect was world state, so one player's quest markers were visible to everyone on the
  server and could surface in minimaps and shader outlines. Set `highlightUsesGlowingEffect = true` to
  restore the old behaviour.
- Datapacks gating on `related_villager_status <relation> missing` will see those quests offered **less
  often**, because `missing` no longer counts a villager who is merely unloaded (see Fixed).
- **The Quests button is no longer added to MCA's menu by a mixin.** Two client mixins used to target
  MCA's `AbstractDynamicScreen#setLayout` and `InteractScreen`'s private `villager` field; both named MCA
  classes at compile time, and a Mixin `@Accessor`'s descriptor is validated against the target field's
  declared type, so the accessor *could not* be made agnostic of MCA's package root. Both are replaced by
  ordinary Forge `ScreenEvent` handlers (`client.McaScreenButtons`) that identify MCA's screen by class
  name and read the villager reflectively. `mcaquests.mixins.json` now targets **only vanilla classes**,
  which both narrows the conflict surface with other MCA add-ons and makes its `"required": true` safe —
  nothing it targets can be absent. The button behaves as before, including re-appearing after you leave
  and re-enter MCA's main menu.
- **Hearts owed to an unloaded villager are now MCA: Quests' own ledger, and are per-player.** MCA deleted
  `Village#pushHearts(UUID,int)` and the entire "unspent hearts" queue behind it in the 7.7 line, so there
  is nothing left to hand off to. A new saved-data store (`<world>/data/mcaquests_pending_hearts.dat`)
  records what is owed and pays it when the villager next loads or the player next logs in. This also
  fixes a long-standing inconsistency: MCA's queue was village-wide and player-agnostic while the
  loaded-villager path beside it has always been per-player, so the same community-project payout meant
  two different things depending on whether a chunk happened to be loaded. It now means the same thing
  either way, on every MCA version.
- **A situation that pays hearts now needs a player to credit**, exactly as its reputation award already
  did — MCA hearts are a relationship between one villager and one player, so with nobody to credit there
  is nothing to award. Situations resolved with no attributable player no longer move hearts.
- Dev runs pick their MCA build from `mca_dev_version`, overridable per invocation with
  `-PmcaDevVersion=…`, so both MCA package layouts can be exercised without editing a file. MCA is a
  `runtimeOnly` dependency now — nothing compiles against it — and is excluded from the unit-test runtime
  so "MCA is absent" is the genuine, exercised state there.

### Fixed

- `related_villager_status` with `"relation": "any"` was **silently discarded**, because `any` was missing
  from the accepted value set and a malformed condition parses as an absent one. The built-in
  *A Kindness for Kin* template was therefore offered ungated.
- A relative who was alive but simply **outside render distance counted as missing**, since the check only
  looked at loaded entities. They are now recognised by their village's resident roll, and filler ancestors
  MCA generates to pad a family tree are excluded — without which the new search quests would have spawned
  a duplicate of a living villager.
- `protect_entity` failed if **any** relative of the target relation died anywhere in the world, rather than
  the one the quest was actually about.
- The quest log and HUD could name a different relative than the one being highlighted, because the
  objective line and the highlight resolved the target independently.
- **Player titles are now keyed by dimension.** Quests' own per-player title store (the fallback used
  by the Journal, the FTB title task, and the legacy import) keyed villages by bare integer id, so a
  Nether village sharing a numeric id with an overworld one shared its titles too. Titles are now
  keyed `<dimension>|<id>`; a bare-integer key from an older save is read as the overworld — the same
  assumption the §32.2 score migration has always made.
- Legacy-import registration now goes through `McaReputationApi.registerImportProvider` instead of an
  internal Reputation package.

### Compatibility

- **MCA 7.7 is supported, and 7.6 still is.** `mods.toml` continues to accept `[7.6,8)`; the difference is
  that the mod now binds to whichever package layout is actually installed instead of assuming one. On an
  MCA build whose layout is unknown to this version, MCA-backed features disable themselves with a single
  `ERROR` naming the roots that were tried — the server does not crash.
- **Add-on API — `QuestObjective` gained a defaulted `isTriviallySatisfied(QuestContext)`.** It answers
  "would this objective already be satisfied if the quest were offered right now?", and returning `true`
  withholds the offer. It **defaults to `false`**, so it is purely additive: existing objective types,
  add-ons included, need no change and keep compiling. (Contrast `VillagerTargeted#targetSelector()` above,
  which is source-breaking.)
- **Add-on API — `McaCompat.asMcaVillager` was removed.** It returned `Optional<VillagerEntityMCA>`, and a
  typed MCA reference cannot survive MCA's package rename. It had no callers anywhere in the mod. Anything
  that used it should call `McaCompat.isMcaVillager(entity)` and keep the plain `Entity`; every other
  `McaCompat` signature is now vanilla-typed, as its documentation always claimed.
- **Add-on API — `McaCompat.pushVillageHearts` was replaced.** Use `awardHearts(level, villagerUuid,
  player, amount)`, which applies hearts immediately when the villager is loaded and ledgers them
  otherwise; `queueHeartsForLater` is the ledger-only half. The village id parameter is gone (the ledger is
  keyed by villager) and a `ServerPlayer` is now required, because MCA's own hearts API needs one.
- **Datapack/add-on — `EscortEntityObjective` and `ReachLocationObjective` gained a record component**
  (`Optional<Integer> minJourney`). Datapack JSON is unaffected — `min_journey` is optional — but code
  constructing either record directly needs the extra argument.
- **`LocationAnchor.resolveTarget` and `VillagerTarget.resolveFrom` gained giver-based overloads** that
  take the giver `Entity` instead of an `ActiveQuest`, which is all the `ActiveQuest` was ever used for.
  Purely additive; the existing methods delegate to them and behave identically.
- The optional MCA: Reputation dependency floor is now `0.2` — this version calls API surface that
  first exists there. With an older (never-published) build the integration disables itself with one
  log line, exactly as with the mod absent.
- **Add-on API — `VillagerTargeted` gained a required method.** Any add-on objective implementing
  `dev.otectus.mcaquests.quest.objective.VillagerTargeted` must now also implement
  `VillagerTarget targetSelector()`, returning whatever the objective exposes as its `villager` /
  `recipient` field. This is what lets the accept-time binder and the HUD reach a selector without an
  `instanceof` chain over every objective type. Source-breaking, one line per implementation; no other
  add-on surface changed.
- `QuestObjective` gained a **defaulted** `describe(player, active, progress, level)` overload alongside the
  existing three-argument form, so a villager-targeted objective can name the villager it actually bound.
  Purely additive — existing objective types need no change.
- **New client mixin into a vanilla class.** `MinecraftGlowMixin` HEAD-injects
  `Minecraft#shouldEntityAppearGlowing` and only ever forces `true`, so it never suppresses vanilla's own
  glow (spectator creeper outlines, the Glowing effect) and should coexist with other outline mods. It is
  needed because `Entity#setGlowingTag` cannot drive the outline from the client in 1.20.1: it ends with
  `setSharedFlag(6, isCurrentlyGlowing())`, and on the client `isCurrentlyGlowing()` *reads* flag 6, so the
  call writes the flag back to its own value and does nothing. Client-only — dedicated servers never load
  it.
- **Protocol `8` → `9`.** `HighlightTargetsS2CPacket` is new and `QuestLogEntry` carries an optional target
  hint, so client and server must both be on 1.3.0; the channel handshake enforces it. World saves are
  unaffected.
- Materialising a missing relative goes through MCA's `initialize(MobSpawnType)` rather than
  `finalizeSpawn`, because MCA's `finalizeSpawn` invents two random deceased parents whenever a family node
  has no valid father/mother — which would rewrite real genealogy for a relative being restored. This is
  the first thing to re-check on an MCA version bump.

## [1.1.0] - 2026-08-13

### Added — MCA: Reputation integration (optional)

**MCA: Quests now speaks to [MCA: Reputation](https://github.com/otectus/MCAReputation) when it is
installed, and is completely unchanged when it is not.** Reputation becomes the canonical owner of
village standing; Quests supplies the deeds and keeps a mirrored fallback copy so removing it later
does not reset anybody.

- **Village standing is now per player.** This is the headline fix, and it applies *whether or not*
  Reputation is installed. Quests' own store was keyed by village and shared by the entire world, so on
  a server every player read the same number even though the Journal calls it "your standing" — one
  group's quest work silently moved everyone's reputation. The new store (`standingV2`) is keyed by
  player UUID first.
- **Village standing is now dimension-aware.** MCA allocates village ids per level, so village 3 in the
  Nether and village 3 in the overworld shared one entry. They no longer do.
- **The v1 tags are retained, read-only.** `reputation` and `repTierHW` are still written on every save
  so a pre-1.1.0 world stays hand-recoverable and the one-time import can read them. A build-time
  assertion fails the build if a gameplay call site starts using them again.
- **A top-level `reputation` block on quest definitions**, with optional `complete`, `fail`, and
  `abandon` outcomes. Failure and abandonment default to **nothing**: abandoning has always been free
  from the villager menu, and attaching a penalty by default would change every existing pack.
- **Project and situation reputation now names its recipients.** Each field still accepts the legacy
  bare integer and means what it always did, but the delta is applied per eligible contributor instead
  of once anonymously to a shared village total. `sponsor_village` is read as "every participant", with
  a one-time warning recommending explicit `recipients`.
- **New conditions and rewards**, registered unconditionally so a suite-authored pack still loads on a
  Quests-only install (where they simply never match): `mcareputation:has_incident`,
  `mcareputation:resolve_incident`, `mcareputation:record_incident`. Together they make a restitution
  quest possible — the villager offers amends only for something you actually did, and finishing the
  work softens the original deed without erasing it.
- **Every reputation path is deduplicated.** Quest, project, situation, and FTB outcomes each carry a
  stable key, so a duplicated turn-in packet, a doubled event, or a relog mid-claim cannot pay twice.
- **The Journal, the commands, and the FTB tasks and rewards all delegate** through one bridge, so the
  numbers they show can never disagree with each other or with Reputation's own screen.
- **A one-time legacy import.** With Reputation installed, an eligible player's pre-Reputation village
  scores, tier high-water marks, and titles are copied once into their canonical record — as a
  non-decaying baseline rather than as invented deeds, because the old data cannot say who earned what.
  See MCA: Reputation's `MIGRATION.md`.
- **Legacy events are translated exactly once.** Reputation's first-time upward tier change becomes
  `ReputationTierReachedEvent`, and a new title becomes `TitleGrantedEvent`, so existing consumers and
  title-chain quests keep working.

### Changed

- `ReputationService` is now a thin, deprecated shim over the new `QuestReputation` facade. It still
  works for outside callers, but its signatures cannot express a player or a dimension, so it has to
  assume both. New code should use `QuestReputation` with an explicit community.
- `gradle.properties` no longer hardcodes an absolute Linux JDK path, which made the build fail on any
  other machine. Set `JAVA_HOME` to a JDK 17 instead.
- Optional datapack fields in the new reputation blocks report a malformed value rather than silently
  substituting the default — a misspelled `recipients` used to mean "pay nobody", with no diagnostic.

### Compatibility

- **MCA: Reputation is entirely optional.** Without it Quests uses its own store and every reputation
  surface behaves as before; the bridge is reached by name after a `ModList` check and every failure is
  contained to one ERROR.
- Existing quest, project, and situation JSON needs no edits. `mcaquests:default`,
  `mcaquests:honored_of_village`, and `mcaquests:revered_of_village` all still resolve.
- 306 automated tests pass, including all 285 that existed before this work.

### Earlier in 1.1.0


A compatibility, clarity, balance, and localization pass driven by player feedback. The headline fix
removes a leftover debug shortcut that had been quietly breaking MCA's own villager interactions and
other mods; alongside it, the "talk to three cartographers" project now actually completes, babies no
longer offer adult errands, family objectives say *whose* relative they mean, relationship progression
is no longer trivially farmable into marriage, and the whole built-in pack is finally translatable —
with Brazilian Portuguese shipping alongside English.

### Removed

- **The sneak-right-click quest shortcut is gone.** A Phase 0 debug behaviour opened the quest menu on
  sneak-right-click and then cancelled *every* such interaction with an MCA villager, whatever the player
  was holding. That swallowed MCA's own sneak actions (the villager editor book, inventory, trading) and
  any other mod bound to sneak-right-click — and, because Forge does not deliver a cancelled event to
  later listeners, it could also starve MCA: Quests' own progress handlers, which is one reason project
  contributions went missing. **MCA: Quests now never cancels an entity interaction.** The injected
  **Quests** button is the only entry point.

### Fixed

- **"Talk to N villagers of a profession" objectives now work as advertised.** Two defects fed the
  "cartographer project stuck at 0/3" report:
  - Both the quest and project talk objectives compared profession ids with `equals`, ignoring the
    configured `professionMatchingMode` — so a datapack asking for `minecraft:cartographer` never matched
    a villager whose profession id carried a different namespace. Both now use `ProfessionMatcher`, so
    `STRICT` / `NORMALIZED` / `LOOSE` behave identically for quests and projects.
  - The quest-side objective counted *interactions* rather than *distinct villagers*. It now dedupes by
    villager UUID, matching the project side (and the objective text now says "different villagers").
- **`mcaquests:missing_villager_search` is village-scoped**, not family-scoped. Family scope resolves to a
  fixed radius around wherever the sponsor happened to be standing, so cartographers elsewhere in the
  village silently didn't count. Its objective and dialogue now state plainly that you must talk to three
  *different* cartographers *in this village*, and the ambiguous verb "Rally" is gone.
- **Saved project instances are quarantined when a pack changes a project's scope.** Every lookup keys on
  the current scope, so an instance created under the old one becomes unreachable — but was still being
  credited and still paying out phase rewards in parallel with its replacement, a double payout. Such an
  instance now stops accruing and stops paying, while its data stays in the save (reverting the pack
  restores it).
- **Babies and toddlers no longer offer written quests.** `relations/child_treat` was the only built-in
  quest with `adult_only: false`, which opted out of the *only* age gate and let any age offer it. It is
  now gated to `child` and `teen` via `age_group`, combined with its existing family requirement through
  `all_of`. A new loader warning flags any datapack quest that sets `adult_only: false` without saying
  which ages it means, and `BuiltinAgeEligibilityTest` holds the shipped pack to the stricter rule.
- **Family objectives say whose relative they mean.** `VillagerTarget` resolves `family` relations
  relative to the *quest giver*, but the labels read "your sibling", sending players to look for their own
  relative. They now read "the quest giver's sibling", and so on for spouse, parent, child, and family.
  Resolved targets still show the actual name and village, and target glow is unchanged.
- **A conversation is counted once.** Quest and project talk credit now share a single gate and both
  dedupe by villager UUID, so a conversation reported by both the interaction hook and MCA: Conversations
  advances progress once. Talk progress is also synced immediately rather than on the next per-second tick.

### Added

- **`mcaquests:currency` reward** — semantic money. A datapack asks for *currency*; the server chooses what
  currency is via `currencyProvider`: vanilla emeralds (default), **Create: Numismatics** coins, or any
  custom item. Numismatics is resolved by **registry id** and never linked against, so MCA: Quests cannot
  classload it and an absent Numismatics is just an unresolvable id — handled by `currencyFallback`
  (`EMERALDS` or `DISABLE`), logged once per id rather than once per turn-in.
  - The amount is **rolled exactly once, at accept time**, and persisted with the quest (or, for a project
    phase, with the project). Reopening the menu, reconnecting, reloading, or a retried turn-in packet all
    read the same stored number, so a payout can never be rerolled. Offers show an honest range; accepted
    quests show the frozen amount, which is what is paid.
  - Built-in emerald payouts are migrated to it; explicit `mcaquests:item` rewards are untouched.
- **Optional `difficulty` metadata** (`easy` / `medium` / `hard`) on a quest, supplying default reward
  ranges per band. Backward compatible: a quest without it keeps its explicit rewards exactly as written.
- **`currencyRewardMultiplier` and `xpRewardMultiplier`** server-side scaling levers, applied *before* the
  amount is displayed so a card never shows a number different from what is granted.
- **Brazilian Portuguese (`pt_br`) — the complete pack**, all 1,582 strings: interface, objectives,
  rewards, quest titles and dialogue, relationship arcs, village projects, and situations. This required
  first making the pack translatable at all: the 1,283 hard-coded English strings in the built-in quests,
  situations, and projects are now translation keys (literal `text` remains fully supported for
  third-party packs), and template placeholders became positional `with` arguments so a translation can
  reorder them where Portuguese word order differs.
- **Locale parity tests** — every locale must cover all of `en_us` and define nothing `en_us` lacks,
  placeholders must agree with the source, no value may be blank or a leftover `TODO`, no value may mix
  in a non-Latin writing system, and no built-in data file may go back to hard-coding English.
- **Debug tracing for unrewarded contributions.** With `debugLogging` on, a rejected villager interaction
  now says *why*: inactive project, wrong phase, out of scope, profession mismatch, duplicate villager,
  cancelled event, held item, or invalid player.
- **`McaQuestsApi.notifyVillagerConversation(player, villager)`** so MCA: Conversations can credit a real
  conversation the interaction hook cannot see. Safe to double-report — credit is deduped by villager.

### Changed

- **Relationship progression is materially harder to farm.** MCA needs **100** hearts to marry (50 to
  engage, 40 for friendship); the pack previously granted up to **35** hearts for a repeatable quest on a
  one-day cooldown, putting marriage about **three in-game days** away via a single trivial errand.
  Built-in hearts rewards are now banded by difficulty — **4 / 8 / 14** — situations (which are throttled
  and time-limited) sit one band higher, and hard repeatables carry at least a two-day cooldown. Marriage
  now takes roughly **12–25 in-game days** of sustained attention to one villager. `heartsRewardMultiplier`
  remains the lever for servers that want it faster or slower, and is now documented prominently in
  [CONFIG.md](CONFIG.md#relationship-pacing).
- **Talk objectives count only a real conversation**: a main-hand, empty-handed, non-cancelled interaction
  with an MCA villager, or an explicit MCA: Conversations signal. Holding the MCA editor book or another
  mod's interaction item is no longer "talking". Item-driven objectives (deliver, heal, cure) are
  unaffected and still work with something in hand.

### Compatibility

- **Fully save-compatible** — existing worlds and datapacks load unchanged; no migration needed.
- Two new NBT fields are additive and simply **absent** on saves from 1.0.0 or earlier: `talked_to` on a
  quest objective's progress (the distinct-villager set) and `frozen_rewards` on an active quest and on a
  project instance (the rolled currency amounts). Both load as empty and start accumulating from first
  load; an objective or quest that uses neither serialises to exactly the tag it always did.
- **Third-party datapacks are unaffected.** `difficulty` and `mcaquests:currency` are both optional and
  purely additive, literal `"text"` remains fully supported, and a quest that declares no difficulty keeps
  its explicit reward amounts exactly as written.
- **A project instance whose definition changed `scope` is quarantined, not deleted** — it stops accruing
  and stops paying out, but its data stays in the save and reverting the pack's `scope` restores it. This
  affects the built-in `mcaquests:missing_villager_search`, which moved from family to village scope.
- **Clients running 1.0.0 or earlier are rejected by the network handshake** (protocol `"6"` vs `"7"`) —
  this is intentional; update client and server together. The packet shapes did not change, but the whole
  built-in pack now travels as translation keys, and a pre-1.1.0 client has none of them in its lang file:
  connecting anyway would render raw ids like `mcaquests.quest.farmer_wheat_request.dialogue.offer` in
  place of every quest title and line.

### Notes

- **The Portuguese translation should be reviewed by a fluent speaker before release.** It is complete
  and the automated checks pass, but tone and idiom across ~14,000 words of flavour prose are worth a
  human read.
- **The FTB Quests "hidden functions/images" report is not addressed here.** It could not be reproduced
  from the description. Reporting it usefully needs: the other mod's name and version, which screen
  (FTB editor / quest-book page / HUD / MCA's interaction screen), the GUI scale, and a screenshot.

## [1.0.0] - 2026-07-16

The headline release: an optional, two-way **FTB Quests** integration (see the new
**[FTBQUESTS.md](FTBQUESTS.md)**) — ten FTB-side task types, three FTB-side reward types, and three
MCA-side conditions plus an objective and a reward that read/write FTB Quests book progress. Fully
optional in both directions: nothing here requires FTB Quests to be installed, and MCA: Quests' own
datapack format is unaffected either way. Also folds in the follow-up fixes to 0.9.1's `{player}` /
MCA-name feature (template quests, custom dialogue, username fallback) that had been sitting unreleased,
a conversation-UI fix for villagers offering more than one quest, and a way to abandon a quest from the
Quest Log so a stuck one can always be cleared.

### Added

- **FTB Quests integration** (optional; see [FTBQUESTS.md](FTBQUESTS.md)):
  - Ten `mcaquests:` FTB Quests **task** types reading real MCA: Quests progress: `quest_completed`,
    `chain_completed`, `reputation`, `reputation_tier`, `title`, `project_completed`,
    `project_contribution`, `situation_resolved`, `hearts`, `married`.
  - Three `mcaquests:` FTB Quests **reward** types granting MCA: Quests effects from the book:
    `village_reputation`, `hearts`, `grant_title` — with automatic banking and retry (on login, and once
    per in-game day while online) when no target village/villager can be found at claim time.
  - Three MCA: Quests **conditions** (`mcaquests:ftbq_quest_completed`, `mcaquests:ftbq_chapter_completed`,
    `mcaquests:ftbq_task_completed`), one **objective** (`mcaquests:ftbq_complete_quest`), and one
    **reward** (`mcaquests:ftbq_progress`) that read and write FTB book progress from a datapack quest.
  - A synced editor experience: FTB editor id fields (quest/chain/tier/title/project/situation ids) offer
    a dropdown built from the server's known ids, alongside the usual free-text entry.
  - `/mcaquests ftbq status|validate|recheck` commands.
  - New `[compat.ftbquests]` config block — see [CONFIG.md](CONFIG.md#compatftbquests).
- **Abandon a quest from the Quest Log** — each active quest in the log now has an **Abandon** button,
  with a confirmation prompt naming the quest. Abandoning from the log behaves exactly like abandoning
  from a villager's menu (records a `quest_abandoned` outcome; no cooldown or penalty), but does **not**
  require the giver, so a quest can always be dropped.
- **Core API additions** powering the integration (also usable by other add-ons):
  - Four new Forge events: `SituationResolvedEvent`, `ReputationTierReachedEvent`, `TitleGrantedEvent`,
    and `ProjectEvent.Contributed`.
  - `ProgressionStats` — a per-player snapshot of quest/project/situation completion counts, persisted
    alongside the player's existing quest data.
  - `PollingObjective` — a new objective interface for objectives that need a periodic server-side check
    rather than pure event-driven progress.
  - New `ReputationService` read accessors: `tierIndex`, `villageReputation`, `allVillageReputations`,
    `currentTier`.
  - New `McaCompat` helpers: `isPlayerMarried`, `maxHeartsWithin`, `bestHeartsVillagerWithin`,
    `nearestVillagerWithin`, `nearestAdultVillagerWithin`.

### Changed

- **Network protocol bumped `"4"` → `"6"`**, to carry the new FTB-editor known-ids packet, the
  abandon-from-log packet, and the giver id on quest-log entries. Client and server must run **matching
  versions** — the existing strict handshake already rejects a mismatch, so this is an enforced lockstep
  update, not a soft one. Save data is unaffected.
- **Admin title-command feedback identifies the account** — `/mcaquests title grant|list|clear` feedback
  now shows the MCA character name **and** the Minecraft username (which is unique) when they differ, so a
  mistargeted command is no longer indistinguishable in the output.
- **A template variable named `player` is now rejected at load time** — it would resolve in objective/reward
  JSON but be shadowed by the reserved token in dialogue, silently disagreeing. `/mcaquests validate` now
  reports it (rename the variable).
- Internal cleanup with no behavior change: consolidated the player-name → placeholder-resolver plumbing,
  removed a per-tick MCA save-data lookup on the quest-progress path, and dropped now-unreachable code
  branches.

### Fixed

- **Quest cards overflowed the villager's Quests screen.** With more than one offer (the default
  `offersPerVillager` is 3), cards ran past the bottom of the screen and drew over the "View Project"
  and "Back" buttons — and because card buttons were registered first, an Accept/Decline sitting on top
  of the footer would swallow its clicks. Cards now sit in a clipped, mouse-wheel scrollable area
  between the header and the footer, with a scrollbar when they overflow. The same fix is applied to the
  **village project** screen and the **Quest Log**, which shared the bug.
- **A quest whose giver was gone could not be abandoned.** Abandon existed only inside the villager
  conversation menu, which silently did nothing when the giver was dead, despawned, in an unloaded
  chunk, or in another dimension — leaving the quest stuck in an active slot forever with no command to
  clear it. It can now be abandoned from the Quest Log.
- **A quest whose definition was removed from a datapack no longer disappears from the Quest Log.** It
  is now listed under its raw id, so it can be abandoned instead of silently occupying an active slot.
- **Template quests using `{player}` no longer fail to load** — the reserved `{player}` token was flagged as
  an undeclared variable, so any template quest using it in `dialogue`/`title` failed validation and, under
  `strictJsonValidation`, refused to load. The reserved token is now exempt from the check.
- **Custom `accept` / `complete` dialogue now shows in chat** — a quest's own `accept`/`complete` dialogue
  line (and any `{player}` in it) was ignored on accept and turn-in unless the MCA: Conversations add-on was
  installed; only the generic "Quest accepted/completed" message appeared. Both now render the datapack's
  line, matching the `failed` state.
- **Players without an MCA character name are named by their username again** — MCA auto-creates a family
  node named "Unnamed Adventurer" for unresolved/offline players, which suppressed the Minecraft-username
  fallback. That placeholder is now treated as "no name set", so cards, messages, toasts, and command
  feedback no longer address players as "Unnamed Adventurer".

### Compatibility

- **Fully save-compatible** — existing worlds and datapacks load unchanged; no migration needed.
- `ProgressionStats` is new per-player save state: on a save from before 1.0.0 it loads **empty**, not
  absent or an error, and simply starts accumulating from first load onward.
- Banked FTB-reward pending entries are new kinds within the existing per-player pending-rewards list;
  they are simply **absent** on saves that predate this release, same as any other save that never
  queued one.
- **Clients running 0.9.x or earlier are rejected by the network handshake** (protocol `"4"` vs `"6"`) —
  this is intentional; update client and server together.

## [0.9.1] - 2026-07-11

Address the player by their **MCA character name** (the name set in MCA's character-creation screen)
instead of their Minecraft username wherever the mod names them. The new `{player}` token is optional, and
every name lookup falls back to the Minecraft username when MCA is absent or no character name was set.
Note that hand-authored (non-template) `text` dialogue now goes through the placeholder pass so `{player}`
works there too: this means `{{`/`}}` escapes now resolve in that text (a literal `{{` renders as `{`), and a
`"with"` list on a hand-authored `"translate"` line is now applied instead of ignored.

### Added

- **`{player}` dialogue token** — quest authors can now write the player's MCA character name into any
  quest's `dialogue`, `title`, and chain arc/chapter text (e.g. `"Well met, {player}!"`), not just
  template quests. It is a reserved token that cannot be shadowed by a template variable named `player`,
  falls back to the Minecraft username when MCA is absent or no name is set, and is dialogue-only (never
  substituted into objective/reward JSON). Resolved server-side per recipient, so situation broadcasts
  name each nearby player correctly. Documented in `DATAPACK.md`.

### Changed

- **Admin command feedback uses the MCA name** — the chat messages from `/mcaquests title grant|list|clear`
  now show the target player's MCA character name instead of their Minecraft username (with the username
  as a safe fallback). Debug logs continue to use the username for account-level troubleshooting.

## [0.9.0] - 2026-07-07

An **MCA: Conversations** add-on bridge, lead-style escorts, and a substantial quest-pack expansion.
Existing saves and datapacks are unaffected — the new `escort_entity` fields are optional and default
to the previous behavior, and the conversation hooks do nothing unless the add-on registers them.

### Added

- **MCA: Conversations — voiced quest dialogue** — a new add-on API (`QuestDialogueHooks` /
  `QuestDialogueResolver`) lets **MCA: Conversations** speak a quest's lifecycle line (offer /
  in-progress / ready / complete / failed) in the villager's own personality instead of the static
  datapack `dialogue` text. Resolved server-side at Component-build time; **degrades safely to the
  static line** when no resolver is registered, the resolver returns `null`, or it throws — so the
  base mod is unchanged without the add-on.
- **MCA: Conversations — conversation-driven objectives** — objectives implementing
  `ExternalSignalObjective` advance when the add-on pushes a signal via
  `QuestManager.notifyExternalObjective(player, signalId, villagerUuid)` — e.g. "the player talked to
  this villager about topic Y" — letting talk-based quests progress from an actual conversation
  rather than a built-in detector.
- **NPC-led escorts** — `escort_entity` gains a `lead` flag (default `false`). With `lead: true` the
  villager walks to the destination **itself** and **pauses whenever the player is farther than
  `wait_distance` blocks** (default 6), so the player must stay close to keep it safe — the inverse of
  the old player-leads / villager-follows behavior. Driven server-side through MCA's brain
  (`MoveState.MOVE` + the vanilla `WALK_TARGET` memory), isolated behind `McaCompat`; the lead pace is
  configurable via the new `leadVillagerSpeed` option. Lead/follow movement is now also released cleanly
  on quest complete/abandon/fail. Pairs with `failure.fail_on_giver_death`.
- **Staged relative-escorts** — when a `lead` escort's target is someone *other than the giver* (a
  relative or other villager), the escortee now **waits invulnerable and motionless at its spot** until the
  player comes within `wait_distance`; the escort then "truly begins" — the escortee becomes mortal, starts
  being led, and from that point its **death fails the quest** (new `ESCORT_TARGET_DIED` reason, heart
  penalty applied to the giver). Auto-detected for `lead` + non-`self` villager, overridable with the new
  `stage_until_near` field. The escortee is locked by UUID (so a re-resolving `family` target can't swap
  relatives) and the hold is released on engage/cleanup so a held villager is never left frozen.
- **Findable quest targets** — for an active quest, objectives that target a specific villager
  (`deliver_to_villager`, `heal_entity`, `cure_villager`, `escort_entity`, `protect_entity`,
  `defend_villager`) now resolve the target's **real name** and home village in the objective line — e.g.
  "Deliver 1× Paper to **Hans (your brother) — Oakvale**" (the name comes from MCA's persistent family tree,
  so it shows even when the relative is unloaded) — and the target villager **glows** through walls while it
  is loaded, so it can be found. New `highlightQuestTargets` config (default on) toggles the glow. Objective
  lines are resolved server-side, so no protocol change. Family-relative quests are also gated on
  `related_villager_status <relation> same_village` so they are only offered when a findable relative exists
  — fixing `relations/letter_to_brother`, which could previously be offered to a villager with no sibling at
  all (an impossible quest).
- **`mcaquests:giver_distance_from_village` condition** — gates a quest on the giver being at least
  `min_distance` blocks from its home-village center (optionally also outside the village border). Fails
  safe to *not met* when the giver has no village. Combine with `mcaquests:time` `NIGHT` via `any_of` to
  reserve escort / "out after dark" quests for villagers genuinely far from home or caught out at night.
- **`mcaquests:reach_location` objective** — the player travels to a location anchor; arrival sticks
  complete (distinct from `enter_structure`, which keys off a named structure).
- **`mcaquests:defend_location` objective** — defeat hostile threats near a fixed place anchor (the
  place-anchored sibling of `defend_villager`).
- **~54 new built-in quests plus 3 chains, 2 projects, 2 situations, and 2 templates**, emphasizing
  combat/defense, relationship & family arcs, and village/emergent events: lead-escort and
  night/distance-gated quests, gate defenses and night watches (showcasing `defend_location`), spouse /
  child / parent storylines, the multi-stage **courting**, **lost_child** (branching), and
  **aging_parent** relationship arcs, the **muster_the_militia** and **rebuild_the_walls** community
  projects, and the **defend_the_gate** / **raiders_at_the_gate** situations.

### Changed

- The built-in `relations/escort_me_home` quest now uses `lead: true` and is gated to a villager a short
  way from home during the day (keeping its "before nightfall" deadline). A new night/far variant,
  `relations/lead_me_home`, covers being caught out after dark with no time limit.

### Fixed

- **Escort/lead quests now actually work.** Three compounding bugs are resolved:
  - **Erratic, stuttering movement.** A led villager's walk target was only re-issued once per second, so
    MCA's per-tick brain behaviors overwrote it for the other 19 ticks — the villager drifted/stuttered
    instead of walking to its destination. Lead actuation now runs **every tick** so the walk target sticks,
    and the `wait_distance` leash gained hysteresis so it no longer thrashes start/stop at the boundary.
  - **"Escort to the village" while already in it / never completing.** Arrival at a village anchor
    (`home_village`/`nearest_village`) now triggers when the villager is **inside the village border**, not
    within a small radius of the single center point, and the check is horizontal (the village center's `Y`
    no longer blocks completion). Home-village lead quests are also gated with `require_outside_border` so a
    villager already inside the village isn't offered an "escort me home".
  - **Drifting destination.** The escort destination is now **resolved once and frozen when the quest is
    accepted**, so a `nearest_village` (previously recomputed against the *player's* position every tick) or a
    moving relative target no longer snaps around. `nearest_village` also resolves relative to the
    escortee/giver rather than the player.
- `reach_location` uses the same border-aware, horizontal arrival as escorts.

### Compatibility

- The **MCA: Conversations** integration is **optional**. MCA: Quests only ships the hooks and their
  safe fallbacks; the consumer lives in the separate MCA: Conversations add-on. With the add-on
  absent, dialogue stays the static datapack text and `ExternalSignalObjective` quests progress
  through their normal detectors — nothing else changes.

## [0.8.0] - 2026-06-22

Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x** and **Architectury API**. The
**Living Village** phase: villages now react to what actually happens in the world and ask for help.
Instead of only standing, author-authored offers, gameplay events open transient **Situations** that
surface dynamic, time-limited quest offers on nearby villagers and resolve into reputation outcomes.
Saves remain forward/backward compatible (new data loads as empty when absent). **Network protocol
bumped to v4** — client and server must run matching versions.

### Added

- **Situations** — a new, fully datapack-driven system of emergent, world-driven quests loaded from
  `data/<ns>/mcaquests/situations/**.json`. A situation pairs a **trigger** (what world event opens it)
  with lifetime/throttle metadata, a **scope**, resolution **outcomes**, and the dynamic **offer**
  surfaced while it is open. Open situations are stored in the world save (`mcaquests_situations.dat`),
  so they survive logout, chunk/villager unload, and restart; everything is server-authoritative.
  - **Six trigger types**, registry-driven like objectives/conditions/rewards: `mcaquests:raid`,
    `villager_death`, `infection` (`min_progress`), `missing_kin` (`relation`), `low_food`
    (`threshold`, read from MCA village storage), and `night` (`require_full_moon`). Detection is
    player-proximity-driven (a periodic sweep of villages near players) plus event-driven on villager
    death; all MCA access stays behind `McaCompat`.
  - **Three scopes** decide who surfaces the offer and where the outcome lands: `village`, `villager`
    (the focal one), and `family` (an MCA lineage).
  - **Dynamic offers** reuse the entire existing quest lifecycle. A situation's `offer` block is the
    body of a quest (objectives, rewards, dialogue, turn-in, templates, offer shaping) and is surfaced
    through the same selection/shaping pipeline as static quests, defaulting to a higher priority so the
    village's needs stand out (`situationDefaultPriority`). The offer is time-limited: its deadline is
    anchored to the situation's open time, so the existing HUD countdown and failure machinery apply.
  - **Resolution & outcomes** — the first participant to complete the offer resolves the situation as a
    **success** (village reputation, routed through the single `ReputationService`, plus optional hearts
    to the focal villager); its deadline expiring resolves a **failure** (a reputation penalty, and any
    still-active copies fail with the new `SITUATION_CLOSED` reason); a condition lifting on its own
    (the raid ends, food recovers) closes it **cleared**, usually neutral.
  - **Throttling** — a per-village concurrency cap, a per-definition cooldown, and a global anti-spam
    cooldown gate how often situations open; every suppression is logged (caps are never silent).
  - **"Village needs help" toast** to nearby players when a situation opens (client `showSituationToast`)
    and a card tag marking situation offers in the menu.
  - **Six built-in example situations**: `after_raid_recovery`, `cure_the_infected`, `find_missing_child`,
    `avenge_the_fallen`, `famine_relief`, and `night_watch`.
  - **New commands** — `/mcaquests situation list|info <id>|validate|debug` (list/info/debug at op level
    2, validate at op level 3).
  - **Config** — a new `situations` block: `enableSituations` (master switch),
    `maxConcurrentSituationsPerVillage`, `situationGlobalCooldownTicks`, `situationDetectionIntervalTicks`,
    `maxSituationOffersPerMenu`, `situationDefaultPriority`, and client `showSituationToast`.

### Changed

- **Network protocol bumped v3 → v4** for the situation toast packet. The handshake rejects mismatched
  client/server; save data is unaffected.
- All active-quest definition lookups now route through a single resolver so dynamic situation offers
  reuse the quest lifecycle (accept, track, turn in, fail) unchanged alongside static quests.

### Compatibility

- Fully save backward/forward compatible: open situations and the new optional `ActiveQuest` situation
  link load as empty/absent on pre-0.8.0 saves, and existing quests, projects, conditions, and rewards
  are unchanged. When `enableSituations` is off, nothing is detected, opened, or surfaced.

## [0.7.0] - 2026-06-21

Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x** and **Architectury API**. Turns the
quest loop into a long-term **progression** system: village reputation now has named tiers, players earn
titles, and a new journal screen makes it all visible. Saves are forward/backward compatible (new data
loads as empty when absent). **Network protocol bumped to v3** — client and server must run matching
versions.

### Added

- **Reputation tiers** — a datapack-defined, ordered ladder of named thresholds over the existing
  per-village reputation (default: Stranger → Acquaintance → Friend → Honored → Revered). Loaded from
  `data/<ns>/mcaquests/reputation_tiers/**.json`; the shipped `mcaquests:default` ladder can be overridden.
  A built-in fallback ladder is used if none is defined.
- **`mcaquests:reputation_tier` condition** — gate a quest's eligibility on a minimum (and optional maximum)
  tier with the giver's village; supports an optional named `ladder`. Fails safe when no village resolves
  or tiers are disabled.
- **Player titles** — earned per-village or globally and persisted on the player. New
  **`mcaquests:grant_title` reward** (scope `village` or `global`), and tiers may auto-grant a title via
  `grants_title` when first reached. Optional title definitions load from `data/<ns>/mcaquests/titles/**.json`.
- **Tier-up toast** — shown when the player pushes a village into a new reputation tier.
- **Journal screen** — opened via the new (unbound by default) **Open Journal** keybind or a button in the
  Quest Log. Shows each village's reputation and tier, earned titles, and a completed-quest archive.
- **New admin/test commands** — `/mcaquests reputation get|set|add|tiers` and
  `/mcaquests title grant|list|clear`. `/mcaquests validate` now also reports progression cross-references
  (undefined granted titles, unknown tier ids).
- **Config** — `enableReputationTiers` (default on) gates the loaders, condition, UI, toasts, and titles.
- Example quest `mcaquests:honored_envoy` exercising the new condition and reward.

### Changed

- **Network protocol bumped v2 → v3** for the tier-up toast and journal request/sync packets. The
  handshake rejects mismatched client/server; save data is unaffected.
- All village-reputation writes (quests and projects) now route through a single `ReputationService` so
  tier crossings and title grants fire consistently.

### Compatibility

- Fully save backward/forward compatible: new player NBT (`titles`) and the per-village tier high-water
  mark load as empty when absent. Existing `village_reputation` rewards keep working and now additionally
  drive tier-ups. A 0.7.0 save opened by 0.6.0 ignores the new keys (losing titles/high-water on a
  round-trip through the older version).

## [0.6.0] - 2026-06-21

Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x** and **Architectury API**. Turns
MCA: Quests into a living-village RPG system: quests can now involve people, homes, families, and places
rather than only inventory checks. No network protocol change; fully backward compatible — every existing
quest, condition, and objective works unchanged.

### Added

- **Twelve NPC- and village-centered objective types**, all registry-driven (no hardcoded quest cases),
  server-authoritative, and persisted through the existing quest state (they survive logout, death,
  dimension change, villager/chunk unload, and dedicated-server restart):
  - `mcaquests:escort_entity` — lead a villager to a home, village, workstation, another villager, or coords.
  - `mcaquests:protect_entity` — keep a villager alive for a duration (optionally only while near you).
  - `mcaquests:defend_villager` — kill hostile threats near a villager.
  - `mcaquests:trade_with_villager` — complete trades with a villager or profession.
  - `mcaquests:heal_entity` — use a remedy item on a hurt villager.
  - `mcaquests:cure_villager` — cure an infected (zombifying) MCA villager.
  - `mcaquests:breed_animals` / `mcaquests:tame_animal` — breed/tame animals, optionally near a place.
  - `mcaquests:sleep_or_rest` — sleep through to morning.
  - `mcaquests:build_near_location` — place blocks near a place (each position counts once — no farming).
  - `mcaquests:enter_structure` — enter a configured structure (id or tag).
  - `mcaquests:deliver_to_villager` — hand an item to a specific villager (family member, profession, UUID).
- **Villager targets** (`self` / `profession` / `family` relation / `uuid`) and **location anchors**
  (`home_village` / `nearest_village` / `giver_pos` / `villager` / `workstation` / `bed` / `coords`),
  resolved relative to the quest giver — unloaded targets pause the objective rather than failing it.
- **Per-objective datapack validation** (`ObjectiveValidator`) reporting bad targets/anchors/structures by
  quest id, objective index, and field, honouring `strictJsonValidation`.
- **Seven example quests** demonstrating the new system (escort home, protect a child, deliver a letter,
  cure infected kin, repair the village well, trade with the blacksmith, defend the guard captain).
- **`McaCompat`** gains `getHomePos`, `getWorkstationPos`, `isInfected`, `findGiverRelative`, and
  `giverRelativeUuids` — all safe-fail, with MCA internals kept inside that one class.
- **`ObjectiveProgress`** extended (elapsed ticks, locked target UUID, deduped placed positions, scratch
  tag) — backward compatible: old count-only progress loads unchanged.

### Known limitations

- `cure_villager` and `enter_structure` depend on MCA/dynamic-registry state and ship with documented
  fallbacks (see DATAPACK.md). The "villager uses their bed" variant of `sleep_or_rest` is not implemented.

## [0.5.0] - 2026-06-19

Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x** and **Architectury API**. Hardens the
relationship-quest-chain system (0.2.0) into a production-grade tool for long-term, author-built villager
stories. No network protocol change. **Behaviour change:** relationship arcs are now **per-villager** — see
*Changed* and the migration note below.

### Added

- **Per-villager relationship arcs** — chain progress is tracked against the individual villager you deal
  with, so the same arc can be lived out independently with different villagers. Chain `prerequisites` compile
  to `quest_completed` with `scope: giver`, and the four quest-state conditions (`quest_completed`,
  `quest_not_completed`, `quest_failed`, `quest_abandoned`) gain an optional `scope` field (`global` default,
  or `giver`). `QuestHistory` persists per-giver completion/outcome counts alongside the global ones, so arc
  progress survives logout, death, dimension change, villager unload/reload, and dedicated-server restart. No
  client-side state is trusted.
- **Offer priority & weighted bonuses** — two optional top-level fields shape *which* eligible quest a villager
  offers. `priority` (int) tiers offers (higher fills slots first; a chain continuation defaults above
  standalone). `weight_bonus` is a list of `{ "when": <condition>, "amount": <int> }` that adds to a quest's
  selection weight when its condition holds — e.g. likelier as MCA hearts rise or as earlier stages are
  completed (`quest_completed` `scope: giver`). Selection stays deterministic and server-authoritative.
- **Chain-aware debug commands** — `/mcaquests debug villager` now lists every chain stage the nearest villager
  could give and why each is offered / eligible / locked / hidden-superseded / completed / on cooldown;
  `/mcaquests debug quest <id>` prints the full per-quest gate checklist, per-villager chain progress, history
  counts, and effective weight/priority — built for datapack authors diagnosing stuck arcs.
- **Expanded chain validation** — `/mcaquests validate` splits **errors** (blank chain id, `stage` above
  `stage_total`, self-references, circular `prerequisites` or `unlocks`, unknown/disabled condition targets,
  impossible "completed and not-completed" gates) from non-fatal **warnings** (inconsistent `stage_total`, two
  non-branching quests sharing a stage, dangling `unlocks`, a branch gated on a quest that can never fail).
  Only errors honour `strictJsonValidation`. Every message names the quest, chain, field, and referenced id.
- **New worked arc** — `chains/mapmaker_expedition` (cartographer), a 3-stage branching arc demonstrating
  prerequisites, a `failure` deadline with `retry_after`, a `quest_failed` `scope: giver` recovery branch,
  `priority`, and hearts-based `weight_bonus`.

### Changed

- Relationship arcs are **per-villager** rather than global (see *Added*). Standalone (non-chain) quests are
  unchanged and keep global completion/cooldown semantics. The bundled `guard_safety` and `jobless_friendship`
  branch conditions now carry `scope: giver` so each arc is coherently per-villager.
- Offer selection is organised into datapack-controllable priority tiers with context-sensitive effective
  weights, generalising the previous hardcoded "chain continuations first" rule (which remains the default
  when no `priority` is set).

### Migration

- Save-data compatible with 0.1.0–0.4.0 worlds; the per-villager history maps are additive and load empty on
  older saves. **One caveat:** an arc that was *in progress* before this update restarts under per-villager
  tracking (an earlier stage completed globally no longer counts toward the new per-villager gate). Standalone
  quests, cooldowns, and already-finished arcs are unaffected.

## [0.4.0] - 2026-06-19

Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x** and **Architectury API**.
Save-data compatible with 0.1.0–0.3.0 worlds; the new **village projects** are additive and pre-0.4.0
worlds load cleanly. **Datapack breaking change:** `chain.time_limit_ticks` has been replaced by the
new `failure` block (see *Changed*/*Removed*). **Network protocol bumped to v2** — client and server
must run matching versions (the project packets are rejected at handshake otherwise).

### Added

- **Quest failure states & deadlines** — a new optional `failure` block makes a quest *expire* while
  it's active, with author-controlled triggers and outcomes. Failure is server-authoritative,
  persisted (the deadline is anchored to acceptance time, so it survives logout/restart), grants no
  rewards, and records a `FAILED` outcome that follow-up quests branch on via the existing
  `mcaquests:quest_failed` condition.
  - **Triggers** (combine freely; first to fire wins): `deadline_ticks` (relative deadline),
    `deadline_time` (fail at a Minecraft time-of-day, e.g. "before sunrise"), `require_weather`
    (fail when the weather stops matching, e.g. "while it's raining"), and `fail_on_giver_death`
    (per-quest, independent of the global `failQuestIfGiverDies` config).
  - **Outcomes**: `failure_hearts` (relationship penalty, or `0` for non-punitive), `retry_after`
    (cooldown before the quest can be offered again), and `block_retry` (permanent lock). The giver's
    `dialogue.failed` line is shown on failure; recovery quests are expressed with `quest_failed`.
  - A quest already complete ("ready to turn in") is **not** failed by a time/weather trigger — a
    grace window to hand it in. All failure paths route through one idempotent handler, so no quest
    ever double-fails or duplicates a completion.
- **HUD deadline countdown** — the quest tracker shows a live `m:ss` countdown for quests with a time
  deadline, turning red in the final minute. Weather/giver-death failures show no countdown.
- **3 built-in failure examples** — `guard/dawn_defense` (kill zombies before sunrise),
  `cleric/urgent_medicine` (+ `cleric/urgent_medicine_recovery`, a recovery quest gated on
  `quest_failed`), and `fisherman/rain_catch` (fish while it's raining).
- **Failure validation** — `/mcaquests validate` reports a `failure` block with no trigger, a
  `failure_hearts` magnitude past the hearts clamp, and `block_retry` combined with `retry_after`.
- **Village projects** — a new, separate system of **shared, multi-stage community goals** loaded from
  `data/<namespace>/mcaquests/projects/**.json`. A project's progress is **shared** (stored in the
  world save, not per-player), so multiple players contribute toward one common objective. Fully
  additive and backward compatible — existing quests are unchanged and pre-0.4.0 worlds load cleanly.
  - **5 scopes** decide who shares the progress: `player`, `villager`, `family`, `profession`, and
    `village`. The MCA-backed scopes (`family`/`profession`/`village`) resolve via MCA's village/
    relationship data and **fail safe** (the project never appears) when that data is missing.
  - **Phases** run in order — a phase is entered only after every earlier phase completes — each with
    its own dialogue, objectives, rewards, and an optional `unlock` gate.
  - **4 project objective types** that track shared progress: `donate_item` (items consumed and banked
    into the pool, with an optional `per_player_cap`), `project_kill_entity`, `project_place_block`,
    and `project_talk_to_profession`.
  - **Shared rewards** wrap any existing quest reward with a `target` (`contributors`,
    `all_participants`, `sponsor_village`, `top_contributor`) and add new reward types
    (`hearts_with_sponsor`, `hearts_with_participants`, `village_reputation`, `unlock`). Projects also
    carry independent **mod-side village reputation** deltas (`on_phase_complete` /
    `on_project_complete` / `on_fail`), and a new `mcaquests:village_reputation` condition tests a
    giver's reputation in any quest **or** project condition tree.
  - **Sponsors** (by profession) surface a project via a **View Project** button in the MCA villager
    menu; with `oneSponsorPerProjectPerDay` only one deterministically chosen villager per village
    offers a given project per day. Contributions are atomic and server-authoritative (items validated
    and consumed server-side, then banked, then synced); per-phase rewards distribute exactly once,
    with offline players' non-hearts rewards queued for next login and villager hearts queued via MCA.
  - **6 built-in example projects**: `guardhouse_stockpile`, `library_restoration`,
    `festival_preparation`, `well_repair`, `after_raid_recovery`, and `missing_villager_search`.
  - **Project commands** — `/mcaquests project list`, `info <id>`, `validate`, `reset <id>`,
    `advance <id>` (test-only force-advance), and `debug <id>` (explains availability from the nearest
    villager); list/info/debug at op level 2, validate/reset/advance at op level 3.
  - **Project validation** flags unknown scope/objective/reward type ids, missing phases, and
    unknown/disabled or circular `follow_up` chains as errors, with warnings for empty non-final
    phases, MCA-dependent scopes while MCA is absent, disabled command rewards, and mismatched reward
    targets. Hard errors abort the load under `strictJsonValidation`.
  - A **project menu, quest-log section, and HUD tracker** (`showProjectTrackerHud` /
    `projectTrackerMaxEntries`) surface active projects and their shared progress.
  - **Network protocol bumped to v2.** Project sync/contribution packets require a **matching client
    and server**; mismatched versions are rejected at handshake.

### Changed

- The `chain.time_limit_ticks` deadline is now `failure.deadline_ticks` — it works on **any** quest,
  not just chains, and gains the richer triggers/outcomes above. The two built-in chain quests that
  used it (`farmer_family/3_apprentice`, `guard_safety/2_patrol`) were migrated.

### Removed

- `chain.time_limit_ticks` (replaced by the `failure` block). Datapacks still using it will fail
  validation; move the value to `failure.deadline_ticks`.

## [0.3.0] - 2026-06-19

Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x** and **Architectury API**.
Fully backward compatible with 0.1.0–0.2.0 worlds and datapacks.

### Added

- **MCA-aware quest conditions** — 11 new optional, datapack-driven condition types that gate a quest's availability on the giver's MCA Reborn state. Each is usable standalone and composes inside the existing `all_of`/`any_of`/`not` composites and with every existing gate (hearts, profession, biome, time, weather, advancement, level, random chance, quest history):
  - `mcaquests:is_player_spouse` — the giver is married to the player.
  - `mcaquests:relationship_state` — the giver's relationship state (single/promised/engaged/married/widow).
  - `mcaquests:is_family_member` — the giver is the player's parent/child/sibling/grandparent (or any).
  - `mcaquests:age_group` — the giver's MCA age (baby/toddler/child/teen/adult).
  - `mcaquests:personality` — the giver's MCA personality (13 values).
  - `mcaquests:mood` — the giver's mood value range and/or named mood.
  - `mcaquests:village_member` / `mcaquests:has_home` — the giver's village/home status.
  - `mcaquests:health_below` — the giver's health fraction is below a threshold.
  - `mcaquests:infected` — the giver is zombie-infected past a threshold.
  - `mcaquests:related_villager_status` — a relative of the giver is alive/nearby/missing/dead/same-village.
- **6 built-in sample quests** in `relations/` exercising every category: a spouse errand (composed with a hearts gate), a child's request, a sick-villager remedy, a guard village patrol, a missing-child search, and a widower's memorial.
- **Quest templates** — an optional `template` block turns one quest file into many concrete quests. Authors declare variable pools (`item`/`block`/`entity`/`biome`/`dimension` drawn from ids or tags, `int` ranges, and `text` phrase pools) and reference them from objectives, rewards, and dialogue with `{placeholder}` tokens. Values are resolved **server-side at offer time**, deterministic per villager per day, then **frozen onto the accepted quest** and persisted — they never reroll until the quest is completed, failed, or abandoned, surviving logout/death/restart. `int` variables can scale by player level (`per_player_level`) or giver hearts (`per_heart`) with a `limit` clamp. Placeholders fill objective/reward JSON (whole-token `"{var}"`) and dialogue/titles (`{var}` value, `{var_name}` translated display name), preserving translation keys via `translate` + `with`.
- **5 built-in template examples** in `templates/`: farmer crop request, guard mob cull, fisherman catch, librarian knowledge, and cartographer survey.

### Notes

- All MCA access is isolated behind the mod's compatibility layer; conditions **fail safe** to *not met* with debug logging (never crashing the server) when MCA data is missing or the giver is not an MCA villager. Field values are validated on load (lenient skip-with-log, or hard error under `strictJsonValidation`).
- Evaluation stays server-authoritative and runs only at quest-menu time (not per tick); each villager's MCA state is snapshotted once per eligibility pass.
- `age_group` does not support `elder` — MCA Reborn has no elder age state.
- Quest templates resolve server-side only (no client randomization); empty pools fail safe (the offer is skipped with a debug log) and template definitions are validated on load like conditions.
- Fully backward compatible: every new condition and the `template` block are optional and additive; existing quests, datapacks, and save data load unchanged.

## [0.2.0] - 2026-06-18

Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x** and **Architectury API**.
Fully backward compatible with 0.1.0 worlds and datapacks.

### Added

- **Relationship quest chains** — an optional `chain` block on any quest turns a set of standalone quests into a multi-stage relationship arc, with no Java required:
  - `prerequisites` gate later stages on completing earlier ones (compiled into the existing condition system, so it composes with hearts/profession/time gates).
  - **Branching** on outcomes via two new conditions, `mcaquests:quest_failed` and `mcaquests:quest_abandoned`, alongside the existing `quest_completed`.
  - **Per-quest time limits** (`time_limit_ticks`) that fail a quest when they expire; the deadline survives logout/restart.
  - Offer selection prioritizes arc continuations and only ever shows the **furthest unlocked** stage of a chain at a villager.
  - UI shows the arc name and "Part 2 of 4" in the conversation menu and quest log; standalone quests are unchanged.
  - `/mcaquests validate` now reports chain problems (unknown/disabled references, bad stages, circular `unlocks`, unreachable stages), each naming the quest and field.
- **4 built-in sample chains** demonstrating the system: a farmer family arc, a guard community-safety arc (with a failure-redemption branch), a librarian chronicle arc, and a jobless friendship arc (with an abandonment branch).

### Notes

- Fully backward compatible: every chain field is optional, the 69 existing quests and any existing datapacks load unchanged, and new player save data is additive.

## [0.1.0] - 2026-06-18

First public release. Built for **Minecraft 1.20.1 / Forge**, requiring **MCA Reborn 7.6.x**
and **Architectury API**. Prod-tested against MCA Reborn 7.6.20.

### Added

- **In-menu Quests button** injected into MCA Reborn's villager interaction screen (client mixin), gated by config.
- **Conversation UI** — multi-offer cards showing each quest's title, dialogue, objectives, and reward summary, with inline Accept / Decline / Complete / Abandon.
- **Quest engine** (server-authoritative, datapack-driven):
  - 10 objective types: item delivery, obtain, craft, fish, kill entity, break block, place block, visit biome, visit dimension, talk to profession.
  - 7 reward types: item, XP, XP levels, status effect, loot table, command (disabled by default), and **MCA hearts**.
  - 12 condition types + `all_of` / `any_of` / `not` composites for gating offers.
  - Profession matching (strict / normalized / loose), 5 turn-in modes, cooldown/once repeat rules, and weighted, per-day-deterministic offer selection.
- **69 built-in quests** across every vanilla profession (4 each), jobless villagers and nitwits (6, shared), the MCA guard (5), and bonus quests for MCA archers, adventurers, and mercenaries.
- **Quest tracking** — keybind-toggled Quest Log, a fully repositionable HUD tracker (anchor + X/Y offset) that names the quest giver, and a toast + sound when a quest is ready to turn in.
- **Persistence** — quest state lives on the player (Forge capability), surviving death, dimension changes, villager unload, and restarts.
- **Java API + Forge events** — `McaQuestsApi` for add-ons to register custom objective/reward/condition types; `QuestAccepted/Ready/Completed/Abandoned/Failed` events on the Forge bus.
- **Commands** — `/mcaquests list`, `validate`, `reload`, `export-schema`, and `debug villager`.
- **Configuration** — common (gameplay) and client (visual) config, including a villager **auto-follow** toggle (off by default), chat confirmations, hearts-reward scaling/clamps, offer/cooldown tuning, and HUD placement.
- **Documentation** — `README.md`, `CONFIG.md` (every option), and `DATAPACK.md` (full quest schema + a datapack-authoring walkthrough).

### Notes

- MCA Reborn exposes no public API, so this release links against MCA's internal classes and is pinned to the **7.6.x** line; all access is isolated behind a single `McaCompat` adapter.
- Turn-in is atomic and idempotent — rewards cannot be duplicated by packet spam.

[1.3.0]: https://github.com/otectus/MCAQuests/releases/tag/v1.3.0
[1.1.0]: https://github.com/otectus/MCAQuests/releases/tag/v1.1.0
[0.9.0]: https://github.com/otectus/MCAQuests/releases/tag/v0.9.0
[0.2.0]: https://github.com/otectus/MCAQuests/releases/tag/v0.2.0
[0.1.0]: https://github.com/otectus/MCAQuests/releases/tag/v0.1.0
