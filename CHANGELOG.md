# Changelog

All notable changes to **MCA: Quests** are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.5.4] - Unreleased

A compatibility framework for third-party mod integrations — **Ice & Fire** (original and Community Edition) and **Bountiful** — each with conditional built-in content, new objective and condition types to bind them, and a registry-probed capability system so quests suspend rather than fail when optional content is missing. The marker gains frame-rate-independent direction smoothing, behind-camera bearing in a screen-edge indicator, an occluded mode with rate-limited sampling, and `/mcaquestsclient debug marker`. Quest suspensions show why ("quest paused: missing Bountiful"), and an unregistered entity type in a target no longer fails to load. No network protocol change; `QuestNetwork.PROTOCOL_VERSION` unchanged.

### Added — compatibility framework

A new `compat/CompatProvider` system and `compat/CompatRegistry` let the mod detect and probe optional mods at startup and again at reload. Each mod has a `CompatProvider` that can be `absent`, `partial` (some features work), or `full` (all expected capabilities present). Probes run on `AddReloadListenerEvent` and `ServerAboutToStartEvent`, and dispatch to `CompatLifecycleEvents`. Conditional embedded datapacks are mounted via `AddPackFindersEvent` from resources under `src/main/resources/compatpacks/`, gated by `compat/pack/ConditionalCompatPack.isEnabled(registry)` evaluated by `compat/pack/CompatPackFinder.java` inside the event handler.

### Added — Ice & Fire: Community Edition

MCA: Quests detects which build of Ice & Fire is installed (original or Community Edition, they share mod id `iceandfire`) by probing the class path. The original has Myrmex hives; Community Edition dropped them for Seekers and netherite armors. A `compat/iceandfire/` bridge detects flavor by class presence and resolves registry capabilities (dragons, hippogryphes, Myrmex — the latter absent in Community Edition). Config keys (true by default):
- `compat.iceandfire.enabled` — turn off Ice & Fire integration entirely
- `compat.iceandfire.enableBuiltinContent` — load the built-in quest pack (`iafce_quests`, eight quests)

A Myrmex-dependent quest objective is gated by a registry capability so a World using the mod can freely upgrade to Community Edition and back, the affected quests suspending through the absence. Godly Dragon Seeker titles are never required. Diagnostics and status available via `/mcaquests compat iceandfire status`.

### Added — Bountiful

A `compat/bountiful/` bridge reaches Bountiful through reflection, selecting one of three modes:
- `AUTO` (default): hook the completion callback and observe cash-ins, or degrade if the method is unavailable or reshaped
- `DATA_ONLY`: mount bounty pools and read rarity; bounty-completion objectives disabled
- `OFF`: as if Bountiful were not installed

Config keys (true by default):
- `compat.bountiful.mode` — `AUTO`, `DATA_ONLY`, or `OFF`
- `compat.bountiful.enableBuiltinContent` — load the built-in quest pack (`bountiful_core`, three quests)
- `compat.bountiful.enableIceAndFirePools` — extend built-in pools with `bountiful_iafce` (two bounty pools and one decree, with placeholder `unitWorth` balance values)

The second common (non-client) mixin, `mcaquests.compat.mixins.json`, holds `mixin/compat/BountyDataCashInMixin.java` targeting `io.ejekta.bountiful.bounty.BountyData.tryCashIn` — observes the return value only, never cancels or mutates, and is `required: false` with plugin-gating via `compat/bountiful/BountifulMixinPlugin`. It can only interact with other mods that also inject into `tryCashIn`. Diagnostics via `/mcaquests compat bountiful status`.

### Added — objectives and conditions

Three new objective types:
- `mcaquests:use_item` — use a held item; gated internally so it works without Bountiful but can represent actions Bountiful defines
- `mcaquests:interact_block` — right-click a block; likewise gated internally
- `mcaquests:bountiful_bounties` — cash in Bountiful bounties with rarity filtering; evaluates to unavailable if Bountiful is absent

One new condition type:
- `mcaquests:compat_capability` — gate a quest on a registry capability being present, with optional negation; allows a pack to require a certain Myrmex or board implementation without naming the mod

New Forge event handlers in `QuestProgressEvents`: `RightClickItem`, `LivingEntityUseItemEvent.Finish`, and `RightClickBlock` (LOWEST priority, never cancelling). A new public static method `creditObjectives` lets add-ons credit progress toward objectives.

### Added — MCA: Reputation

- **`mcareputation:villager_opinion` condition:** queries what an individual giver personally makes of the player, as opposed to their village's public standing, with optional filtering by tier and knowledge basis (involved, witnessed, hearsay, or none).

### Changed — quests

- **Unknown entity targets no longer fail to load.** A quest targeting an entity from an uninstalled mod now keeps the target id as unresolved, loads successfully, keeps its progress intact, and shows "objective unavailable" for that step. The quest suspends cleanly and resumes when the mod is installed again. A round-trip through the codec on such a world symmetrically encodes the id back, so datapack edits do not lose data.
- **Quests with missing optional content quarantine and suspend.** A quest that names a provider (Ice & Fire, Bountiful) not currently installed is quarantined; the definition loads with placeholder entities/blocks, and the quest suspends with `mcaquests.quest.suspended.compat` saying which mod is missing. Installing the mod brings it back. Files that fail to parse are separately quarantined; validity warnings/errors no longer block a load.
- **Objective validation**: `data/ObjectiveValidator.java` applies a warn-vs-error rule: an entity id in an optional mod's namespace yields a warning (the mod is likely not installed), while an id in `minecraft` or a loaded mod's namespace yields an error (a misspelling). This allows a quest pack to name content from missing optional mods without failing validation.
- **Quarantine logging**: `compat.validation.logMissingOptionalContent` (true by default) gates warnings about quests that cannot load because they name missing optional content. Useful during development; turn off on a server that deliberately ships packs for uninstalled mods.
- **Quest suspension accrual**: time a quest spends suspended (due to missing optional content) accrues separately in `suspendedTicks`, recorded per-objective; the quest log shows total *active* time, excluding suspensions.
- **Giver UI reports suspension reason**: a quest that cannot be offered shows `mcaquests.quest.suspended.compat` naming the missing mod instead of "unknown quest".

No breaking changes to `EntityTarget`: a third record component `unresolved` was added, but the original two-arg constructor is preserved for add-ons. The legacy constructor calls the canonical with `Optional.empty()` for unresolved.

### Changed — marker

Frame-rate-independent direction smoothing via a new `EdgeIndicatorSmoother`. Targets behind the camera now report bearing to the screen edge, not bearing to the camera, so a target behind-right points to the right edge of the screen. New classes: `EdgeSafeRect`, `EdgeIndicatorState`, `MarkerOcclusionSampler` (rate-limited raycast for visibility in occluded mode), and `EdgeIndicatorDebug`. `MarkerProjection.clamp` and `EdgePoint` removed in favour of `projectInto`. `MarkerFrameState.publish` adds an edge side parameter. Marker updates are rendered frame-per-frame without jumping.

### Added — marker configuration

A new client config section `[client.marker.edge]` (documented in `CONFIG.md`) with eight keys:
- `mode` (enum: AUTO / DISABLED / OFFSCREEN_ONLY / OFFSCREEN_OR_OCCLUDED, default AUTO) — when the edge arrow appears. AUTO follows the deprecated `questMarkerEdgeIndicator` for compatibility.
- `inset` (pixels, default 18, range 0–128) — how far inside the screen the arrow stays
- `smoothingMs` (milliseconds, default 80, range 0–500) — time constant of the arrow's direction smoothing filter
- `enterHysteresisPx` (pixels, default 4, range 0–32) — how far outside the screen the target must be before switching to the edge arrow
- `exitHysteresisPx` (pixels, default 2, range 0–32) — how far back inside the screen it must come before switching back
- `transitionFrames` (frames, default 2, range 1–10) — consecutive frames either threshold must hold before changing
- `showDistance` (boolean, default true) — write the distance next to the arrow
- `occlusionSampleMs` (milliseconds, default 50, range 25–1000) — shortest gap between terrain raycasts in OFFSCREEN_OR_OCCLUDED mode

The deprecated `questMarkerEdgeIndicator` is honored only when `mode = AUTO`.

### Added — client command

New `/mcaquestsclient debug marker` shows raycast sampling state, target positions, and edge indicator smoothing state when `client.marker.edge.debugMode = true`.

### Fixed — journal

- **Journal screen looped requests:** `JournalScreen.init()` ran on every `rebuildWidgets()`, and `tick()` calls rebuild whenever the cached journal list references change — which every server reply does. This created a loop: request → reply → rebuild (which runs `init()` again) → request. Each pass recreated the tab buttons and reset their tooltip timers, making the "Journal" tab tooltip flicker and spamming the server with requests. A new `snapshotRequested` flag now makes `init()` request the snapshot only once per screen open.
- **Tab inset for visual clarity:** `addBookTabs` placed the first tab button flush against the inside of the window frame, causing its bevel to merge with the frame's corner bevel and read as a tab clipped by the panel. A new `TAB_INSET` constant (value 4 pixels) offsets the tab strip inward, fixing the issue on both screens that use `addBookTabs` — the Journal and the Quest Log.

### Compatibility

- **Third-party mod integration:** Ice & Fire and Bountiful are reached by registry probing and reflection, never by static import. `NoIceAndFireStaticLinkTest` and `NoBountifulStaticLinkTest` enforce the ban.
- **`EntityTarget` third component:** add-ons building a target in code use the two-arg constructor (`Optional<EntityType<?>>, Optional<TagKey<EntityType<?>>`), which is kept. The three-arg canonical is `(entity, tag, unresolved)`. Calling code is unaffected; wire-format changed only in padding — the id is always encoded/decoded.
- **Bountiful completion hook:** A new functional interface `BountifulCompletionListener` with method `onBountyCompleted(ServerPlayer player, BountyCompletion completion)` lets add-ons observe bounty cash-ins. Listeners are registered via `BountifulCompat.addCompletionListener()` and persist across re-probes because `BountifulCompat` owns the listener list, not the bridge. `BountyCompletion` is a record carrying `(playerId, serverGameTime, rarity, objectiveCount, dedupeKey)`.
- **Mixin configs:** `mcaquests.compat.mixins.json` is `required: false` and plugin-gated. No other mods can conflict (Bountiful's internals are unique to it).
- **No network protocol change:** `QuestNetwork.PROTOCOL_VERSION` unchanged; clients and servers need not match on 1.5.4 exactly, but must be 1.5.4 or later and same Minecraft/Forge version.

### Translations

New translation keys (both `en_us.json` and `pt_br.json`):
- Compat status and diagnostics: `mcaquests.command.compat.status.*`, `mcaquests.command.compat.iceandfire.*`, `mcaquests.command.compat.bountiful.*`, `mcaquests.compat.{iceandfire, bountiful, mca, townstead, ftbquests}.name`, `mcaquests.compat.iceandfire.{ce, original}`
- Objective availability and details: `mcaquests.objective.{use_item, interact_block, bountiful_bounties}.{count, min_rarity}`, `mcaquests.objective.unavailable.{compat, bountiful_hook, bountiful_rarity}`, `mcaquests.objective.{use_item, interact_block, bountiful_bounties}`, `mcaquests.bountiful.rarity.{common, uncommon, rare, epic, legendary, unknown}`, `mcaquests.target.unavailable`
- Quests: `mcaquests.quest.{bountiful_board_discovery, bountiful_contractor, bountiful_specialist, iceandfire_*}.dialogue.{offer, accept, decline, in_progress, ready, complete}`, `mcaquests.quest.suspended.compat`, `mcaquests.tag.mcaquests.iceandfire_dread`
- Marker debug: `mcaquests.marker.debug.{off, on, state}`
- Compatibility packs: `mcaquests.compatpack.{iafce_quests, bountiful_core, bountiful_iafce}`

### Build

- **New probe tests:** `iceAndFireProbeTest` (accepts `-PiceandfireCeJar=` and/or `-PiceandfireOriginalJar=`) and `bountifulProbeTest` (accepts `-PbountifulJar=`) verify the optional-mod bindings against supplied jars. Bytes-only parsing via class constant pools avoids load-time issues.
- **Second mixin config:** `mixin { config }` now lists both `mcaquests.mixins.json` (client) and `mcaquests.compat.mixins.json` (common, Bountiful).
- **Mixin annotation processor flag:** `-AMSG_MIXIN_SOFT_TARGET_NOT_FOUND=note` suppresses warnings about unreachable mixin targets in opt-mod jars.
- **Second `[[mixins]]` entry:** `src/main/resources/META-INF/mods.toml` lists both configs.
- **New tests:** `NoIceAndFireStaticLinkTest`, `NoBountifulStaticLinkTest`, `ClassConstantPoolTest`, and a save-fixture round-trip suite `SaveFixtureRoundTripTest` with five SNBT fixtures under `src/test/resources/migration/`.
- **Regenerated:** `MODMAP.md`.

## [1.5.3] - 2026-09-03

A comprehensive marker and map-layer rework: the in-world marker is redesigned as a compact diamond, its position corrected, its appearance made configurable and colorblind-safe; JourneyMap now integrates through its documented plugin API instead of reflection; Xaero pins are now explicitly labelled as session-only on Xaero-alone installs; waypoint reconciliation is event-driven and recoverable from failures; the marker's per-quest destinations are shown in the quest log and HUD. Network protocol 14 required (EntityHeight added to `GuidanceTarget`).

### Fixed — marker

- **Icon float-away bug:** the glyph was drawn 25–27 blocks above the target entity because its translation included `MarkerGeometry.labelHeight()` (the sky beam's apex). The beam is gone; only the glyph and ground ring remain. Fixed targets sit just above their block's collision/visual shape. Unloaded entities now use a last-known height sent by the server instead of being pushed 25 blocks up the y-axis.
- **Marker twitch:** `Entity#getY(double)` was called as though its argument were a render partial tick, sweeping a full entity-height per frame. X and Z were not interpolated at all. All three axes now use the partial tick from `RenderLevelStageEvent.getRenderTick()`. Glyph sits at 72% of the entity's bounding-box height.

### Fixed — map waypoints

- **Dimension changes left stale waypoints:** JourneyMap stores each waypoint's true dimension; Xaero only shows current-dimension waypoints. Changing dimensions now explicitly clears automatic points and recreates them on arrival instead of leaving the old ones stranded.
- **Automatic cleanup deleted pinned waypoints:** `/mcaquests clear()` called JourneyMap's `removeAllWaypoints(modId)` unconditionally. Only automatic points are now removed; player pins stay. Xaero's session store is cleared and rebuilt per dimension.
- **Backend failures were never retried:** a failed write was recorded as success and never attempted again. Failures now carry backoff (1,2,4,8,16,32,60 s) and are logged with their fingerprint, once per unique failure.
- **Colour and icon changes didn't update:** a target that changed kind at the same position kept the old colours and icon. Points are now removed and recreated on kind change. Colourblind-safe palette applied to both map waypoints and in-world glyphs.
- **Reconciliation ran every tick:** the waypoint sync checked the desired state every client tick even when nothing changed. Changes now trigger on guidance updates, config changes, world/dimension transitions, and login/logout/clone.

### Changed — marker

- **New compact style:** 24 px diamond frame with a 20 px kind glyph, ground ring, and label within 48 blocks (default). The old 24-block sky beam is gone. Behind terrain only a faint hollow outline shows (default). Depth-tested. Opacity fades on acquire/clear/retarget (reduced-motion option).
- **Palette change:** colorblind-safe Deuteranopia-compatible colours. This also changes JourneyMap waypoint and Xaero waypoint colours.
- **HIGH_VISIBILITY style:** a depth-tested 6-block solid column instead of the old beam. Glyph never moves. Configurable in `questMarkerStyle`.
- **Screen-edge indicator:** marked quests that are off-screen or behind the camera show an 18 px diamond + 8 px chevron in an 18 px safe inset on the HUD, with distance and angle. Toggled via `questMarkerEdgeIndicator`.

### Added — marker configuration

Eight new client config keys in `mcaquests-client.toml`, all documented in `CONFIG.md`:

| Option | Default | What it does |
|---|---|---|
| `questMarkerStyle` | `COMPACT` | Marker appearance: `COMPACT` (new default), `ICON_ONLY`, `HIGH_VISIBILITY`. |
| `questMarkerOcclusion` | `DIM_OUTLINE` | When behind terrain: `DIM_OUTLINE` (faint outline), `HIDDEN` (vanish), `FULL` (see through). |
| `questMarkerEdgeIndicator` | `true` | Show HUD indicator when the quest is off-screen or behind the camera. |
| `questMarkerLabels` | `NEARBY` | When to show labels: `NEARBY` (within 48 blocks), `ALWAYS`, `NEVER`. |
| `questMarkerHighContrast` | `false` | Apply extra contrast to marker shapes for visibility in bright environments. |
| `questMarkerReducedMotion` | `false` | Disable opacity fades and retain instant cutover (accessibility). |

Existing `showQuestMarker`, `questMarkerMaxDistance` and `mapWaypoints` / `mapWaypointsFollowedOnly` remain. `mapWaypoints=false` now overrides both `journeyMapWaypoints` and `xaeroWaypoints`.

### Added — map configuration

Two new client config keys for per-backend toggling:

| Option | Default | What it does |
|---|---|---|
| `journeyMapWaypoints` | `true` | Put quest destinations on **JourneyMap** (if installed). |
| `xaeroWaypoints` | `true` | Put quest destinations on **Xaero's Minimap** (if installed). |

`mapWaypoints=false` disables both regardless of these values.

### Added — client command

New `/mcaquestsclient waypoints [status|probe]` command, fully localized:
- `status` renders the last reconciliation report, showing each backend's state, capability, and version.
- `probe` runs every backend's self-test and reports what bound and what failed.

### Changed — JourneyMap

- **Plugin API 2.0:** JourneyMap's documented entry point (`@JourneyMapPlugin(apiVersion = "2.0.0")`, `IClientPlugin`) is now used instead of reflection against the internal `ClientAPI.INSTANCE` singleton. The plugin is discovered and initialized by JourneyMap's own classloader before or after MCA: Quests' client setup (no ordering guarantee).
- **In-world icon/beacon suppressed:** automatic quest waypoints are created with `showOnMap=true`, `showInWorld=false`, `showBeacon=false`, so MCA: Quests' own marker is the only in-world visual. JourneyMap no longer draws a duplicate.
- **Pinned waypoints are persistent:** the quest log's **add waypoint** button now uses `persistent=true`, and waypoints are read back after creation to confirm their id. They live in JourneyMap's player data and survive mod uninstall like any other player waypoint.

### Changed — Xaero

- **Session waypoint labelled:** on Xaero-only installs (no JourneyMap), the quest log's star button now explicitly offers a **session waypoint** that exists only while the instance is running, with distinct tooltip and success message. With JourneyMap installed, pins go there instead (persistent). This fixes the previous false claim of permanence while keeping the button visible.
- **Named palette and origin:** waypoint colour/purpose are resolved by enum name (`WaypointColor`, `WaypointPurpose`) instead of hard-coded ordinals. Automatic points use origin `mcaquests:quests`, pinned waypoints use `mcaquests:pins`. Pin keys now include the dimension.
- **Store identity tracking:** the backend now tracks Xaero's session identity via `WeakReference` and catches when the session changes (e.g., `/reload`) by comparing references, resetting applied state and rebuilding on next sync. `getCurrentSession()==null` → `RETRY_LATER`.

### Changed — guidance updates

Guidance (the marker and map waypoints) is now recomputed immediately when a quest changes: when a quest is accepted, turned in, abandoned, failed, followed (pinned), or progresses (`GuidanceService.markDirty` from `QuestManager.syncLog`). The recompute happens at the end of the server tick, coalescing multiple mutations to one player into one pass. On respawn and dimension change, guidance is reset and resent, so the first snapshot after death is not suppressed by the equality check that would normally drop an unchanged state. The once-per-second pass remains as a safety net for objectives no event can mark (like a passive timer objective reaching its deadline). This removes most of the one-second latency between a quest action and the marker/waypoint updating.

### Compatibility

- **Network protocol 14:** `GuidanceTarget` gained a trailing `float entityHeight` (clamped 0–64) used when the target entity is unloaded. Strict channel version requires exact match; old clients and servers cannot communicate.
- **`MapWaypointBridge` and `Holder` removed:** add-ons that referenced `compat.MapWaypointBridge` must migrate to the new `compat.MapWaypointBackend` + `client.map.ClientMapWaypointRegistry` contract. See README.md for the extension API.
- **`/mcaquests debug waypoints` redirect:** use the new `/mcaquestsclient waypoints` command instead.
- **JourneyMap API 2.0 required:** `mods.toml` declares JourneyMap `[1.20.1-6.0.0,)`. JourneyMap 6.0.0+ is the minimum for API 2.0 support. Older 5.x builds will not load the plugin and will fall back to the no-op backend (waypoints disabled, no warning).

### Translations

New translation keys for marker, map configuration, and diagnostics (both `en_us.json` and `pt_br.json`):
- `mcaquests.marker.edge_distance`
- `mcaquests.tooltip.add_session_waypoint`
- `mcaquests.message.session_waypoint_added`
- `mcaquests.message.waypoint_failed`
- `mcaquests.command.debug.waypoints.redirect`
- `mcaquests.command.waypoints.{header, none_installed, backend, version_unknown, state.usable, state.not_bound, state.retry_pending, disabled, capabilities, yes, no, pins.none, pins.session, pins.persistent, last_sync, last_sync.never, pending_retry, last_failure, probe.step, probe.step_detail, probe.passed, probe.failed}`

### Build

- **JourneyMap API compiled against:** `build.gradle` declares `compileOnly 'info.journeymap:journeymap-api-forge:1.20.1-2.0.0'` (from `gradle.properties`), sourced from modmaven.dev (the reachable mirror of the artifact JourneyMap 1.20.1-6.0.x embeds; byte-for-byte identical, verified by new `journeyMapEmbeddedApiMatchesCompiledApi` probe). The API jar is never shipped; it is used at compile time only for type checking. Runtime plugin discovery is handled by JourneyMap's own plugin loader.
- **Fallback:** if the official API jar is unavailable, pass `-PjourneymapApiJar=<path>` to use a git-ignored `libs/` copy instead.
- **Map probe test hardened:** `mapProbeTest` accepts `-PrequireMapJars=true` to fail the build if a jar is missing instead of skipping silently.
- **Static link test:** `NoMinimapStaticLinkTest` restricts `journeymap/` type references to the dedicated `compat/journeymap/` package (typed against the compile-only API, loaded only via plugin discovery). XaeroMinimap type references remain banned everywhere.

## [1.5.2] - 2026-09-03

A patch release from a static audit of the bundled quest pack (`docs/audit/QUEST_AUDIT.md`): eight
logic fixes, a pack the audit found offering one quest that could never be finished, and three new
validator checks so the next one is caught before a player meets it. No network protocol change, and
no save migration in either direction.

### Fixed — a cure quest offered about a healthy relative

`relations_cure_infected_kin` gated on "a relative in the same village" and then asked the player to
cure them. Nothing required the relative to be infected, so the quest was offered about a perfectly
well brother and its objective could not advance until he happened to be zombified — the pack's only
uncompletable quest. There was no vocabulary to say what it meant, so there is one now: `infected`
joins the seven statuses `related_villager_status` and a villager target's `require` share, meaning
"alive and part-way through MCA's infection right now". The quest gates on it and targets it.
(`DATAPACK.md`)

### Fixed — a plain quest could be held from two villagers and paid twice

"Already active" was asked per villager. A quest offered by several professions could be accepted from
two givers at once, and because the progress events credit every active copy, one set of kills
completed both and paid both. A definition with no `chain` is now active for the **player**: 115
bundled quests were exposed to this. Chain stages keep the per-villager question, because an arc is
per-villager by design. `/mcaquests debug quest` reports the new case as
`ALREADY_ACTIVE (with another villager)`.

### Fixed — a giver who died while you were offline was never reconciled

`onGiverDeath` only walked players who were online. Anyone else kept an active quest pointing at a
villager who no longer existed, and `fail_on_giver_death` never fired for them — in a chain, the
failure branch never opened. Deaths of MCA villagers are now written to a small world store
(`mcaquests_dead_givers`, pruned after twenty in-game days) and applied on login, using the same rule
as the live handler. Only a **recorded death** counts: a giver whose chunk is merely unloaded is left
strictly alone, since "not loaded" and "does not exist" are the same answer from the level.

### Fixed — kills that vanished on their way to the objective

`kill_entity` credited only `getSource().getEntity()`, so a kill by a tamed wolf, by TNT, by lava or
by a fall after the player landed the first hit counted for nothing while the quest text said "kill".
Credit now falls back to a pet's owner and then to vanilla's own kill credit — the rule the death
message uses. `defend_villager`, `defend_location` and the project kill objectives share the one
helper, so a project and a quest counting the same mob can never disagree. (`DATAPACK.md`)

### Fixed — a defended villager who blinked out of a chunk lost you the kill

`defend_villager` credited a kill only if the villager resolved at that instant, so a village-edge
render-distance flicker dropped kills in silence. A kill now also counts within 200 ticks of where the
villager was last seen, inside the objective's own radius. Outside that window the old rule stands.

### Fixed — one project contribution locked you out of every other project

The anti-spam gate was keyed by player alone, so contributing to the mill blocked the bridge and the
granary for `projectContributeMinIntervalTicks`. It is now keyed by player **and** project instance;
the interval config is unchanged.

### Fixed — a project reward that quietly disappeared with its sponsor

`hearts_with_sponsor` no-ops on a null villager, and a phase completing after the sponsor died passed
exactly that. The reward now falls back to every sponsor recorded for the instance, banking hearts for
the unloaded ones the way `hearts` already does, and logs the fallback at debug. No bundled project
uses the reward; this closes it for packs that do.

### Fixed — a reward that failed said nothing to the player

`grantSafely` kept turn-in atomic by swallowing an add-on's exception, but the player saw a completed
quest that had quietly paid less. The failure is still contained; it now also names the reward in
chat (`mcaquests.reward.failed`).

### Added — three new checks in `/mcaquests validate`

- **Empty or unknown item/block/entity tags** on an objective, as a warning. Template pools have been
  checked since 1.2.0; plain objectives now are too.
- **Biome, dimension and structure ids this world does not have**, as warnings, including tags that
  resolve to nothing. They live in dynamic registries, so a running world is the earliest anything can
  check them (new `data/RegistryIdValidator`).
- **A `cure_villager` objective about a relative with no infection gate**, an error under
  `strictJsonValidation` and a warning otherwise, alongside the existing family-gate rules.

### Changed — quests

- `mcaquests:relations_cure_infected_kin` — gate is now `all_of(same_village, infected)` and the
  objective requires `infected`.
- `mcaquests:fisherman_rain_catch` — the `failure` block is gone. `require_weather` failed the quest
  the moment the rain stopped, which is pure weather luck; rain remains an **offer** condition.
- `mcaquests:bell_when_the_horns_answer`, `mcaquests:remedy_the_returning_voice`,
  `mcaquests:road_caravan_through` — chain finales with `fail_on_giver_death` gain
  `"retry_after": 24000`, so losing the giver no longer means restarting a four-stage arc.
- `mcaquests:townstead_commission_bells_for_old_names` — `defend_location` counts 8→4 (zombie) and
  4→2 (skeleton), radius 32→40.
- `mcaquests:townstead_commission_watch_at_the_gate` — pillager count 8→4, radius 32→40.
- `mcaquests:townstead_lanterns_for_the_departed` — zombie count 6→3, radius 24→32.
- `mcaquests:townstead_heat_over_the_fields` — the delivery is three **honey bottles** rather than
  three potions; item targets are NBT-blind, so three water bottles satisfied it.
- `mcaquests:townstead_pasture_first_fence`, `mcaquests:townstead_pasture_wool_under_roof` — gain the
  `not(townstead_building ...)` offer guard their sibling registration quests already use.
- `mcaquests:last_banner_home`, `mcaquests:mercenary_witch_hunt`, `mcaquests:drowned_ledger` —
  dialogue now hints where the quarry actually is (woodland mansion or raid; swamp hut; the ruin's own
  sea lanterns), instead of leaving three hard finds unsignposted.

### Translations

New key `mcaquests.reward.failed`, and reworded `offer` / `in_progress` dialogue for
`last_banner_home`, `mercenary_witch_hunt` and `drowned_ledger`. All present in `en_us` and `pt_br`.

### Compatibility

- `RelativeCandidate` gains an `infected` component. The previous 13-argument constructor is kept, so
  code that builds a candidate without reading infection still compiles.
- `data/ObjectiveValidator.validate` takes a third argument, the warnings list. Datapacks are
  unaffected; only a mod calling the validator directly needs the extra list.
- No network protocol bump: no packet's shape changed.

## [1.5.1] - 2026-09-02

A patch release from a full audit of the quest lifecycle, rewards, persistence, multiplayer sync and
the 1.5.0 interface. Nothing here changes the network protocol or breaks a save in either direction.

### Fixed — refusing everything a villager had brought it all back

Decline all of a villager's offers and the next menu showed the same quests. With nothing left to
draw, the empty menu counted as stale, the redraw threw away the refusals, and the one after that
drew from the full pool — the 1.4.3 decline fix, undone in the one case where a player most means it.
An exhausted menu now stays exhausted until `offerRefreshTicks` elapses, refilling as quests become
eligible, and the villager says so (`mcaquests.status.all_declined`). Refusals given an explicit
`declineCooldownTicks` longer than eight refresh windows are no longer cut short by session pruning.

### Fixed — "before sunrise" was measured on the wrong clock

`deadline_time` is documented as a world time-of-day and was computed in game time, which sleeping
and `/time set` do not advance. After one night's sleep `guard/dawn_defense`, `guard/last_stand`,
`mapmaker_expedition_2_expedition` and `relations/escort_me_home` expired at arbitrary hours, with a
countdown that agreed with the wrong answer. An accepted quest now remembers the world clock at
acceptance (`start_day`), and the deadline is derived from it on every check and every sync, so the
tracker corrects itself within a second of a sleep or a `/time set`. `deadline_ticks` is unchanged.
Quests accepted before this version carry no world-clock stamp and keep their game-time deadline
until re-accepted. (`DATAPACK.md`)

### Fixed — rewards that needed the villager in the room

Hearts, village reputation and village-scoped titles were dropped when the giver's chunk was not
loaded — the normal case for a quest that completes in the field, and the only case for
`SELF_COMPLETE`. The quest completed, its cooldown armed, and the reward vanished without a line in
the log. Every quest accepted from now on freezes its giver's village at acceptance (`village`);
quests accepted earlier fall back to a reflective scan of MCA's villages for the giver's UUID. With
that in hand, hearts bank into the same pending store a delivery already uses, and reputation and
village titles resolve the village without the entity. `HeartsWithParticipantsReward` is unchanged;
its grant was already a deliberate no-op.

A reward that throws no longer strands the quest with `rewardClaimed` set and no way to hand it in
again: each grant runs on its own, a failure is logged with the reward index and quest id, and the
remaining rewards are still granted.

### Fixed — a situation that had closed was still on the menu

Since 1.4.3 a villager's offers are drawn once and kept. A situation offer kept that way outlived its
situation for up to `offerRefreshTicks`, and Accept did nothing at all. Offers are now re-checked for
a live situation every time the menu is drawn, a dead one is dropped and its slot refilled like a
declined one, and an offer that is gone by the time Accept arrives says so
(`mcaquests.message.offer_gone`, under `questChatMessages`).

Accept is also validated against the offers the villager actually showed — the drawn set, refusals
included — rather than the whole eligible pool, which is what Decline already did.

### Fixed — the last quest to fail never left the log

Failure did not resync the client, and the per-tick resync skips an empty list. A deadline, a dead
giver or a lost protect target on a player's only quest left it on the tracker until relog.

### Fixed — `/reload` did not reach a template quest already accepted

The concretised definition of a template quest was cached on first use and never invalidated; it now
follows the quest registry's generation, so a pack author's fix applies to quests already held.

### Fixed — Townstead hold timers counted polls, not time

`hold_ticks` objectives credited twenty ticks per poll while polls run every
`townsteadPollIntervalTicks` (10–1200); at the top of that range a thirty-second hold took thirty
minutes, and `reset_on_false` made it unfinishable. Elapsed time between polls is now measured and
credited, capped at four intervals so a lag spike is not a free hold — the rule `schedule_streak`
already used.

### Fixed — the world save grew with every turn-in

On an install without MCA: Reputation, every quest award, situation resolution, project phase, FTB
claim and banked delivery wrote a permanent dedupe marker into `mcaquests_projects.dat`, and nothing
ever removed one. Dedupe is now a bounded per-player ring of the last 128 award keys (`dedupe`), and
the accumulated markers are stripped the first time the world loads; every other migration marker is
kept.

### Fixed — titles were invisible across the MCA: Reputation seam

With MCA: Reputation installed, a title granted by a quest went only into this mod's own store and
the canonical backend never saw it, while global titles were read from whichever community snapshot
came first. `hasTitle`, `globalTitles` and `villageTitles` now union MCA: Reputation's answer with
the online player's local titles, so quest-granted titles satisfy conditions and tier titles appear
in the journal. Writing quest titles *into* MCA: Reputation is deferred to 1.6.

### Fixed — smaller things
- Two per-player maps in the progress tick (`lastTownsteadPoll`, `lastBankedRetryDay`) are cleared on
  logout, alongside the highlight and guidance services that already were.
- Block-break, block-place and talk handlers return on a cancelled event before crediting progress.
- A datapack with two possession objectives for one item, or a `specified_profession` turn-in with
  no professions, is rejected at load — logged, and skipped in lenient mode — instead of shipping a
  quest that completes on one stack or can never be handed in. No bundled quest is affected.
  (`DATAPACK.md`)

### Changed — interface
- Every screen scrolls from the keyboard: arrows, PageUp/PageDown, Home/End. Tab reaches widgets
  below the fold — they are made visible for the focus walk, the focused one is scrolled into view,
  and the rest are hidden again. Arrow keys scroll rather than move focus on these screens.
- A complete quest shown to a villager who cannot take it says where it goes
  (`mcaquests.hint.turn_in.*`) instead of offering only Abandon. A full quest log says it is full
  (`mcaquests.status.at_cap`) instead of the villager's "nothing today".
- The tracker shows a quest's first unfinished objective, not its first objective, and a ready quest
  says so in words (`mcaquests.hud.ready_suffix`) as well as colour.
- The tracker's rows are no longer packed onto a 10-pixel rhythm: every line gets a little more
  leading, section headings a little more room, and each quest or project after the first opens with
  a blank gap, so a title and its objective, destination and deadline read as one block instead of
  as a wall of text. A row carrying a progress bar now reserves the bar's height as well as the
  text's, so the bar no longer overlaps the line beneath it.
- Titles no longer run under the difficulty pips on the offer menu, nor under the pin, copy-coordinates
  and waypoint buttons in the log; project titles wrap inside their card on both screens.
- Icon buttons scale their glyph to fit, so the 12-pixel pin and waypoint buttons no longer overhang.
- The quest log rebuilds its widgets only when a quest's destination or the map integration actually
  changes, and restores focus to the same control afterwards — a guidance update for a walking villager
  no longer drops focus every second.
- A villager's menu only replaces an empty screen, one of this mod's own, or MCA's interact screen;
  it no longer pulls a player out of a chest or their inventory.
- Back from a village project returns to the villager's menu rather than the world.
- Every client cache — active quests, projects and their per-villager menus, the journal, and known
  ids — is cleared on logout, so nothing from the previous world is shown before the first sync.
- `key.mcaquests.toggle_hud` is labelled "Show/Hide Quest Tracker", which is what it does. The quest
  log and journal keys stay unbound by design; the only route is the button on MCA's villager screen.

### Compatibility — add-on API
- `QuestReward` gains `RewardContext` (giver UUID and name, dimension, frozen village id, quest id)
  and a default `grant(player, villager, context)` that delegates to the existing two-argument
  method. Existing rewards compile and behave unchanged; override the three-argument form to grant
  when the villager is absent. (`README.md`)

### Added — translation keys
`mcaquests.status.all_declined`, `mcaquests.status.at_cap`, `mcaquests.message.offer_gone`,
`mcaquests.hint.turn_in.original_giver`, `mcaquests.hint.turn_in.same_profession`,
`mcaquests.hint.turn_in.specified_profession`, `mcaquests.hud.ready_suffix`; `key.mcaquests.toggle_hud`
relabelled. Both locales.

### Changed — protocol
Unchanged at 13. Deadlines are still sent as an absolute game time; the server re-derives it from
the world clock at every sync.

### Save compatibility
**Old saves load unchanged, and a save written by this version loads on 1.5.0.** Three keys are new,
each written only when it holds something and absent-tolerant on read:
- `start_day` and `village` on an active quest — the world clock and the giver's village at
  acceptance. A quest accepted before this version has neither: its `deadline_time` stays on game time
  until re-accepted, and its offline rewards resolve the village by scanning for the giver.
- `dedupe` on a player's village standing — the bounded award ring. The old `dedupe:` migration
  markers are removed on first load and the save shrinks; nothing else in `standingV2` moves.
Downgrading to 1.5.0 leaves the three as unread tags and brings back the behaviour they fix.

### Tests
- `FailureSpecDeadlineTest` — `deadline_time` on the world clock, sleep jumps fire, `deadline_ticks`
  untouched, and a quest with no stamp reproduces the old value.
- `ActiveQuestResolveCacheTest` — a template quest's cached definition changes with the registry
  generation and is stable within one.
- `RewardContextDefaultTest` — a two-argument reward is reached through the three-argument default;
  `RewardContext.community()` resolves from dimension and village id.
- `VillageStandingDedupeTest` — the ring caps at 128 and evicts oldest, round-trips, and loading strips
  `dedupe:` markers while keeping other migrations.
- `OfferSessionTest` — declining everything is not stale, becomes stale after the refresh window, is
  cleared by a redraw, and a timed refusal survives pruning.
- `TownsteadHoldElapsedTest` — elapsed credit per poll, first-poll and clamp rules.
- `ObjectiveValidatorTest` — duplicate possession objectives and an empty `specified_profession` list
  are rejected; the bundled pack still validates clean.
- `ScrollViewTest` — `scrollIntoView` moves the least distance in both directions, pins an oversized
  band to its top, and always clamps.
- `CardTextWrappingTest` — now also fails on a raw `drawString` of a card title.

### Documentation
`DATAPACK.md` (`deadline_time` is on the world clock; validator rejections), `README.md`
(`RewardContext`), `CHANGELOG.md`.

## [1.5.0] - 2026-09-02

The mod shipped a complete quest system behind a placeholder interface. Every screen drew rows of
text onto vanilla's dimmed world with no window, no frame and no panel; `assets/mcaquests/` contained
nothing but `lang/`. This release gives the mod an interface, gives the villagers inside it
something to say when they have nothing to offer, and — the part players will notice first — stops the
quest system answering *what* without ever answering *where*. It also makes finishing a quest worth
something to the village that asked for it, which 252 of the 262 bundled quests were not.

### Added — a texture-based, vanilla-style interface

**The mod's first textures.** `assets/mcaquests/textures/gui/panel.png` and `icons.png` — 28 panel
sprites and 48 glyphs, both 256×256. They are generated by `tools/gen_gui_textures.py` from a palette
table, so the art is reviewable as source and regenerable; the committed PNGs are what ships and the
build never runs the script. The generator also writes `tools/gui_layout.json` — deliberately outside
the shipped assets, because the game never reads it — which `GuiTexturesExistTest` compares against
every sprite constant in `GuiTextures`, so a moved UV cannot silently leave the code that blits it
behind.

**The register is vanilla's own container grey** — the `#C6C6C6` face, `#FFFFFF`/`#555555` bevel and
`#8B8B8B` inset that every chest, furnace and inventory in the game is drawn from, with vanilla's dark
widgets sitting on it and its white shadowed labels.

The sheets were first cut in dark oak and leather, on the argument that every text colour the mod
already used was light and a grey panel would have made all of it unreadable. That argument was
backwards: it kept a palette and retinted the game. All nineteen text colours are now derived *from*
the panel instead — dark inks chosen against `#C6C6C6`, each clearing roughly 4.5:1 — and text on a
panel is drawn **without a shadow**, because vanilla's shadow is a dark offset copy that reads as a
smear on a light ground rather than as depth.

One surface stays dark and keeps its shadows: the HUD tracker. It is a translucent plate over the live
world, where a light grey slab is unreadable against snow and sky, so it has its own light-on-dark
palette in `Palette.Hud`. That is the only exception, and it is documented as one.

**Vanilla where vanilla already draws it.** The toast frame stays `textures/gui/toasts.png`, and the
**Quests** button injected into MCA's interaction screen stays a plain vanilla `Button` — it has to
match the buttons around it, not the ones in here. `McaScreenButtons` is untouched.

**All four screens** rewritten onto a shared `McaQuestsScreen`: nine-sliced window, header and footer
bands, an inset content well, framed cards with resting / ready / hovered states, and hover tooltips
throughout. The mod previously had **no tooltips at all**.

- **Offer menu** — the villager now stands in the header, rendered live and turning to follow the
  cursor, beside their name, profession and real heart icons. Cards carry a **difficulty badge**,
  **per-objective progress bars**, and **reward item icons in vanilla slots with vanilla's own
  tooltips**.
- **Quest log** and **journal** are now two tabs of one window, ending the Back-then-Journal round
  trip.
- **Project menu** — the mod's only progress bars were two flat rectangles two pixels tall; they are
  now textured and show two quantities, the village's shared progress with your own share over it.
- **HUD tracker** — nine-sliced background, section glyphs, and a progress bar under the project
  objective it was already printing the numbers for.

**The scrollbar is draggable.** It was wheel-only, and its drawing was a byte-identical private copy
in three separate screens; it is now one `Scrollbar` with a grabbable thumb, hover and drag states,
and click-to-jump on the track.

**The panel adapts.** Because every sprite is nine-sliced there is no fixed window size: the frame is
sized to the screen and clamped into a readable range, which is what lets it meet the
320×240-equivalent-through-4K bar at GUI scales 1 to 4. `PanelGeometry` holds that arithmetic as pure
integers with no Minecraft types — the same shape as `ScrollView` — and `PanelGeometryTest` sweeps it
across every screen size and screen variant the mod can produce.

### Added — the quest tells you where to go

The mod could always answer *who* and *what*. It had no way at all to answer **where**: `QuestObjective`
had no notion of a place, and the only navigation aid in the game was one line of tracker text. So
"enter an ancient city" was an instruction a player could not act on, and "fetch six nether wart" left
them to work out unaided that this meant building a portal.

**One marker, for the objective you are actually on.** A beam of light with an icon and a label, drawn
through walls at the place the quest you are **following** is currently sending you, fading out as you
arrive so it never stands in your face once you are there. Never more than one: the server decides which
quest and which objective, and sends a single target.

**It walks the quest by itself.** The focused objective is the first one, in the order the pack declared
them, that is neither satisfied nor suspended and can say where to go. Declaration order is not an
arbitrary rule — it is how the log already lists objectives and how the bundled quests are written.
`nether_relay` is *reach the Nether, then kill blazes, then bring back the rods*, which is the route: the
marker is a **portal** until you are through, a **fortress** once you are in the Nether, and the giver
once there is nothing left to do. Nothing is left behind pointing at the overworld, because a target in
another dimension produces a route to the way in rather than an arrow through bedrock.

**Following a quest is a choice.** A pin on each row of the quest log picks it; clicking the one you are
already following clears it; `Follow Next Quest` cycles (unbound by default). Accepting a quest follows
it when you are not already following something (`autoTrackNewQuests`). The choice is persisted with the
player, so it survives a relog, and a reference to a quest that has ended simply stops being an answer
rather than needing to be tidied up.

**What can be marked, and what deliberately cannot.** Escorts point at their frozen destination — the
same position the villager is being walked to, so the marker and the villager can never disagree about
where home is. Location, defend, build, breed and tame objectives point at their anchor. `enter_structure`
and `visit_biome` run vanilla's own `/locate` search **once**, cache the answer on the objective (it
survives a restart), and retry a failed search no more often than `guidanceSearchIntervalTicks`.
`talk_to_profession` points at the nearest villager of that profession you have **not** already spoken to,
so the marker steps along as you work through them. `find_missing_relative` points at the place to search
until somebody is out there, then at them.

Everything else needs the new optional `source` field, and **nothing is inferred**. There is no index of
where eight prismarine crystals are, and a guess would send the player somewhere confidently wrong, which
is worse than sending them nowhere because they would go. An objective with no `source` draws no marker
and its text carries the whole instruction, exactly as before.

### Added — and it tells you the coordinates

The mod could say **how far** and **which way** and never **where**. "Anna's home — 84 blocks
ahead-right" is a fine sentence to act on while you are looking at it and useless the moment you want
to write the place down, type it into a minimap, or send it to somebody else. The server had the
number the whole time — it is the position the marker is drawn at — and dropped it at the client
boundary.

**Every destination line now carries its coordinates**: *"Nether Fortress — 412 blocks ahead-left
(1024, 68, -330)"*. `showQuestTargetCoordinates` turns them off.

**A destination in another dimension carries them too.** That line used to read only "Nether Fortress
— in the Nether", on the sound argument that a bearing across dimensions would be a lie. A
*coordinate* across dimensions is not a lie; it is exactly what a player wants written down before
they go looking for a portal.

**The quest log gained a destination at all.** It listed objectives and never said where any of them
were, so the HUD was the only surface that answered *where*. Each row now shows the same line, with a
button to copy the coordinates to the clipboard and — where a supported minimap is installed — one
to drop a waypoint you keep. Real widgets, not drawn rectangles hit-tested by hand: that mistake was
already made once, by the journal's **View Deeds** link, and it made it invisible to the keyboard and
to the narrator.

The line is built in one place, `client/GuidanceText`, and the HUD, the quest log and the world
marker's floating label all draw it from there. The mod has been bitten twice by the alternative —
the scrollbar was a byte-identical copy in three screens, and the objective counter is *still*
formatted two different ways in three files.

### Added — a destination for every quest, not just the marked one

Guidance resolved **one** place per player. That was right for the marker — a beam per quest is five
beacons for five quests, which is the mistake highlighting used to make — and quietly wrong for the
tracker, which lists every active quest and could therefore only tell the player where **one** of
them was going. A player holding *Echoes Below* (an ancient city) and *Nether Relay* (a fortress) was
told where one was and left to guess at the other. Both answers existed on the server; only one was
ever sent.

The two are now separated. The server resolves a destination **per quest** and marks one of them as
the marker's; the tracker prints them all, the map gets one waypoint each, and **there is still
exactly one beam**.

**Asking every quest costs more, so it is budgeted.** A `LocateCache` miss can fire a real `/locate`,
and five quests whose structures are all out of range would have fired five at once.
`guidanceSearchesPerPass` (default `1`) caps them, and a quest that does not get a turn records
nothing and is asked again next pass — being skipped for a lack of budget is not the same fact as
finding nothing, and recording it as one would have silenced that objective for the whole retry
interval.

### Fixed — one quest could switch the marker off for all the others

The same failure the fall-through above was introduced to fix, reproduced one level down, between
objectives instead of between quests.

Guidance counted an objective as *the answer* when it produced **either** a place **or** a villager to
outline — and then sent the place. So an objective that knew a person but not where to find one sent
an **empty** payload, which is how a marker is taken away, and returned without asking anything else.

An unstaged `escort_entity` is exactly that combination: it outlines the escortee and points at the
destination, and the destination does not exist until the first poll freezes it — permanently, when
the anchor cannot resolve at all, which an unloaded giver or an unsurveyed Townstead building is
enough to cause. **17 bundled quests carry an escort.** One of them with an unresolved destination
switched the marker off for every other quest the player held.

Two changes, because it was two problems:

- **A quest answers with a place, or it does not answer.** The walk carries on past an objective that
  knows a person but no place, and a quest that never finds one is skipped rather than being treated
  as having spoken. The villager to outline still comes from the objective that named the place, which
  is the invariant the whole class is built around.
- **An escort with no frozen destination points at the escortee.** "Nothing" was not merely unhelpful
  there — the person you are walking is a true answer to "where next" even before the mod knows where
  you are walking them.

The selection rule now lives on `GuidanceSnapshot` as a pure function over per-quest answers, which is
what lets `GuidanceSelectionTest` exercise it without a running server. That was not possible before,
and is why this shipped.

### Fixed — sixty-one objectives that named a place and pointed nowhere

`QuestObjective.guidance` defaults to empty. That is the right default for an add-on and a silent one
for a built-in, and **eight shipped types took it**:

| Type | Bundled uses | Now points at |
|---|---|---|
| `townstead_state` / `townstead_change` | 16 | the resident the query names, else the village |
| `townstead_schedule_streak` | 12 | the resident whose working day it is about |
| `townstead_building_registered` | 11 | the nearest building of the family it asks for |
| `townstead_spirit_progress` | 9 | the village centre |
| `townstead_profession_progress` | 8 | the resident whose profession track it is about |
| `townstead_healthy_residents` | 5 | the village centre |
| `trade_with_villager` | 2 | the merchant it names, or the nearest one of the profession |
| `sleep_or_rest` | 0 | the player's own bed |

The Townstead family is the sharp case: those are the quests where the village **is** the subject —
"a week kept well", "the whole flock", "first shift" — and the mod already knew exactly where the
village was. `townstead_building_registered` is sharper still: it asks for a dock or a shed, and MCA
registers every building with a centre, which the objective was already reading to take its acceptance
snapshot.

`sleep_or_rest` points at the **player's** bed and deliberately not the giver's. It is the player who
has to sleep; sending them to somebody else's house would be a marker on a place the quest is not
about.

`ObjectiveGuidanceCoverageTest` fails the build if a registered type goes back to inheriting the empty
default. It found `trade_with_villager`, which nobody had noticed.

### Added — your quests on your map

**JourneyMap and Xaero's Minimap.** Both optional, both client-side, both bound by name at runtime.

The in-world marker is a beam with a maximum draw distance measured in hundreds of blocks, so for a
fortress eighteen hundred blocks away — the destination that most needs help — the mod's own marker
is precisely the one that cannot appear. A minimap can, and in a 1.20.1 MCA pack the player almost
certainly has one.

One waypoint per active quest that has somewhere to send you, created when it resolves, moved when the
quest advances to its next objective, and taken away when the quest ends. Coloured by what is waiting
there, using the same table the beam is coloured from. `mapWaypoints` turns them off;
`mapWaypointsFollowedOnly` reduces them to one, matching the beam.

**They belong to the quest, not to you.** Neither backend writes into the player's own saved waypoint
list — JourneyMap's are non-persistent and Xaero's go to its third-party store, the same one its own
Waystones support uses. Uninstalling this mod leaves nothing behind to tidy up. The quest log's
**add waypoint** button is the deliberate exception: that one is yours and nothing takes it away.

**A destination in another dimension gets no waypoint**, for the reason the beam is not drawn there:
the Nether's coordinates are the overworld's divided by eight, so the raw number would sit somewhere
the player has no reason to go. The tracker still names the place, the dimension and the coordinates.

**Nothing is compiled against either mod.** Neither is redistributable and neither publishes to a
maven this build can reach — the jars live in a gitignored `libs/` on one machine — so a compile
dependency would have meant the mod could only be built by somebody who had separately downloaded two
files, and the resulting classes would be unloadable for every player without both mods installed.
`compat/map/MapBinding` is `McaBinding`'s design applied to two more mods: members matched by name,
arity and parameter type hints, every handle adapted to an all-`Object` shape, never throwing and
never returning null. `NoMinimapStaticLinkTest` fails the build if any compiled class so much as
mentions a `journeymap.*` or `xaero.*` type.

Two things had to be true for that to work at all, and both were verified against the shipped jars
rather than assumed:

- **JourneyMap needs no plugin class.** Its documented entry point is a class carrying an annotation
  and implementing an interface, neither of which can be produced reflectively. But
  `journeymap.api.client.impl.ClientAPI` is an enum with an `INSTANCE` constant implementing
  `IClientAPI`, and its `addWaypoint(modId, waypoint)` resolves the mod id through a lookup that
  **creates and caches a wrapper for an id it has never seen** rather than rejecting it. An
  unregistered mod id is a first-class caller.
- **Xaero has no API package at all.** The integration goes through
  `xaero.hud.minimap.waypoint.thirdparty`, which exists for exactly this and is what its own Waystones
  support uses. The store hangs off the session's *current* world container and is replaced when the
  player changes world, so it is walked fresh on every publish — caching it would have looked like
  "the integration stopped working after I went to the Nether".

**Positional parameter hints** are new to the binding layer, and load-bearing. JourneyMap's
`WaypointFactory.createWaypoint` has four overloads: two take five arguments and differ only at index
three (a `String` dimension id against a `ResourceKey`), and a four-argument one takes the dimension
where the five-argument one takes the **name** — bind the wrong one and every waypoint is called after
the dimension it is in. Xaero's `Waypoint` constructor has a nine-argument all-primitive form and a
nine-argument form taking two of its own enums, and only the first can be called without naming a
Xaero type. Every hint is a JDK or Minecraft class; hinting a mod type would be the linkage the whole
arrangement exists to avoid.

**`/mcaquests debug waypoints`** reports which backends bound, which members did not, and the result of
a round-trip probe — add a waypoint, read it back, remove it. Both mods can decline one without
throwing and neither says so, which from inside the game is indistinguishable from having no minimap
installed. That silence is how the world marker shipped looking broken the first time.

`mods.toml` gains both as optional `CLIENT`-side dependencies: `journeymap` `[1.20.1-6.0.0,)` (where
the API moved to v2 and the `Waypoint` constructor became a factory) and `xaerominimap` `[26.0.0,)`.

### Fixed — "the nearest village" had one source and no fallback

`nearest_village` read MCA's own village roll, which is the right first answer: an MCA village has
residents, a name, a border and a standing, and that is what a quest about a village means. But a
settlement MCA has not taken over is not on that roll, so in a world where the nearest one is an
untouched vanilla village the anchor resolved to **nothing at all** and every objective built on it
silently pointed nowhere — 4 bundled objectives use it directly, and `escort_entity`,
`reach_location`, `defend_location` and `build_near_location` can all be written on it.

It now falls back to the nearest vanilla village (`#minecraft:village`, through the same structure
search `enter_structure` uses) when MCA knows of none. **MCA always wins**: the roll is re-read every
pass, so a village that becomes an MCA one takes over immediately. The fallback answer carries no
village id, so arrival uses the radius test rather than the border test — correct rather than a
compromise, since a vanilla village has no MCA border to be inside of. It is a real `/locate`, so it is
frozen onto the quest under its own fingerprint and paid for once.

**Visible to datapack authors**, which is why it is here: a `nearest_village` anchor now resolves in
worlds where it used to resolve to nothing.

### Fixed — the marker that was usually absent

The first cut of the guidance work asked exactly one quest where to send the player: the one they had
pinned. A pinned quest can legitimately have nothing to point at — *"deliver 24 wheat"*, with no
authored source, is a real objective with no place attached — and since accepting a quest pins it when
nothing else is pinned, the pinned quest was routinely the one that could not answer. A player holding a
wheat errand and an escort saw **no marker at all**: the quest that could not help was asked, and the
escort standing beside it with a perfectly good destination was not. The feature read as broken when it
was merely silent.

Three changes, because it was three problems wearing one coat:

- **Guidance falls through.** When the pinned quest has nothing to say, the next active quest that does
  answers instead. The pin still means "prefer this one"; it no longer means "and show nothing if it
  cannot help". The marker carries the quest it turned out to be about, so the tracker draws its
  direction line under that row rather than under the pinned one.
- **A delivery points at its recipient.** `item_delivery` was treated as having no location, and
  deliveries are most of what this mod asks for. It does not claim to know where wheat is — nothing
  can — but the person waiting for it is a real place and a true answer to "where next". This is a
  marker, not an outline: the giver still does not glow until the quest is ready to hand in, because a
  glowing villager means "go and interact with this person now" and that is not yet true.
- **A `source` can now name a block.** `{"block": "minecraft:wheat"}`, `{"block_tag":
  "minecraft:iron_ores"}` — searched outward from the player, only in loaded chunks, stopping at the
  first hit. This is the one search result that expires, so the cached position is re-checked against
  the world each pass and dropped the moment it stops matching: a berry bush gets picked, and a marker
  standing on the empty ground where one used to be is worse than no marker. Fourteen bundled
  objectives take one — the crops, the berries, the quarry stone and the iron.

**`/mcaquests debug guidance`** lists every active quest, what each would point at, and which one the
marker chose. "This objective has no place attached", "a search found nothing in range" and "the
feature is off" are indistinguishable from inside the game, which is how the first version shipped
looking broken; now they are one command apart.

### Changed — a villager glows because you are going to them, not because you met them

Highlighting used to outline a villager for **every** incomplete villager-targeted objective of **every**
active quest — and, for a quest that named no villager at all, to outline the **giver for the quest's
entire lifetime**. A player holding five errands had five permanently glowing villagers, four of them
glowing only because they had once handed out a quest. The outline meant "I have spoken to this person",
which is not worth a colour.

Now the outline is on the villager the quest you are following actually wants you to reach, and on the
villager you hand the quest back to **once it is ready** — decided by the same walk of the same quest that
places the marker, so the two cannot end up on different quests. `highlightAllActiveQuests` restores the
old every-quest behaviour for anyone who preferred it; the giver fallback is deliberately not restored.

### Fixed — an escort that walked the wrong person to the wrong house

`bed` and `workstation` anchors resolved the **giver's** home, always. That is right for "walk me home",
where the giver is the person being walked, and silently wrong the moment the escortee is anyone else.
The bundled *One Last Walk* told the player to see an ageing parent "safely back to their bed" and sent
them to the parent's **child's** house. Nothing failed — the escort completed, at the wrong building — so
the only way to notice was to know both villagers' addresses.

Both anchors now read the `villager` field the record already carried and only the `villager` anchor
ever used, defaulting to the giver so every pack written before this keeps its behaviour. An
`escort_entity` whose escortee is not the giver and whose destination is an unowned `bed` or
`workstation` is now a **load error**, because there is no reading of that shape anybody wants. And the
anchors name their owner: "Anna's home", not "their home", which was ambiguous in exactly the case the
field exists to express.

### Fixed — the arc called "Bring Them Home" never brought anyone home

`find_missing_relative` keeps highlighting its target after completion, and says why: *"the whole point
is that the player can find their way back to the villager they just found, and later stages of the arc
lead them home."* No bundled quest ever did. The lost-child chain materialised a frightened child in a
mineshaft, and its next stage was a cake delivery.

`lost_child_2_deeper` now escorts the child home after finding them, binding the same relative the find
bound. Its deadline goes from 12,000 ticks to 24,000, because twice the work under the same clock would
have made the quest unfinishable, and its dialogue says what it now asks for.

### Fixed — a quest that told you the villager's gender before MCA had picked one

`relations_letter_to_brother` bound a `sibling` — a relation MCA resolves to a villager of any gender —
and then called them "my brother", "he" and "him" throughout. Its twin, *Mend the Quarrel*, was already
written the neutral way. Rewritten to match, including the title (*The Letter I Won't Carry*); the quest
id and file name are unchanged, because they are in save data and in players' histories. The Portuguese
for both quests now follows the convention the locale's own systemic keys already set
("o irmão ou a irmã"), which *Mend the Quarrel* had not.

### Added — villagers who explain themselves

**`data/<ns>/mcaquests/dialogue/*.json`**, a new datapack type: shared lines a villager can say when
no quest of theirs has anything more specific, for the states `greeting`, `cooldown`, `locked` and
`no_quests`. Every line may be gated by **the existing condition language** — personality, mood, time
of day, weather, hearts, reputation tier, relationship state, age group, and anything added later —
so dialogue gained all of it without the mod gaining a second mini-language to document or teach.

Selection is deterministic per player, villager, day and state. Reopening a menu does not re-voice a
villager: the same guarantee 1.4.3 gave offers, applied one layer down.

Four pools ship, covering all thirteen MCA personalities in five manners of speech, plus an
unconditioned line each. A quest's own `cooldown`/`locked` line always wins where it has one; this is
the floor, not the ceiling.

### Fixed — the villager who had nothing to say, and never could

`QuestManager.whyNothingIsOffered` is a fully implemented feature: when a villager has no offers, it
finds the quest coming off cooldown soonest — or a locked one — and shows that quest's own `cooldown`
or `locked` line instead of a flat refusal. Its own comment said "every quest already authors a
`cooldown` and a `locked` line for exactly this".

**None did.** Not one of the 262 bundled quests, and neither key existed in either locale. So the
method searched, found candidates, rejected every one of them for having nothing to say, and returned
empty every single time it ran. Every busy villager in the game said "I do not need anything right
now."

The search no longer requires the quest to have authored a line, and falls through to the shared
voice pools when it has not. A villager who is genuinely out of work gets a `no_quests` line rather
than the flat one.

Two bugs behind one player report: *"no matter what I do I have '25 more to acquaintance' and my rank is
'stranger', additionally delivery quests don't seem to be registering."* The full trace is in
MCA: Reputation's `DIAGNOSIS.md`; both causes turned out to live here.

### Fixed — quests that were worth nothing to the village that asked for them

**252 of the 262 bundled quests granted no village standing.** Ten carried an
`mcaquests:village_reputation` reward worth 8–12 points; **not one** used the `reputation` block that
DATAPACK.md documents for exactly this, so that field had no shipped users at all. Meanwhile seven
quests are *gated* on standing — and six of those seven are themselves among the paying ten, so the
pack largely gated on a threshold it asked the player to reach some other way. Against a ladder that
puts Acquaintance at 25, Friend at 75, Honored at 150 and Revered at 300, an ordinary village
playthrough had about four quests and roughly 42 points to work with.

`QuestManager.grantQuestReputation` returned in silence every time, with no log line and no warning,
which is why this survived to a player report rather than being caught here.

- **A quest that authors no reputation outcome now grants the configured default for its difficulty
  band** — `easyQuestReputation` 2, `mediumQuestReputation` 4, `hardQuestReputation` 7, in a new
  `[rewards.reputation]` block beside the currency bands they mirror. A quest that authors its own
  outcome still wins outright, and the default fills in a *reward* only: failing or abandoning still
  costs nothing unless the pack says so. Set all three to `0` for the old behaviour.
- **Every path that declines to record standing now says so** under `debugLogging`, naming the quest
  and the reason — no giver, no resolvable village, nothing authored, or an authored outcome that
  resolves to no change. Each of those was a bare `return`.
- `BundledQuestReputationTest` fails the build if a bundled quest is worth no standing, or if the pack
  gates on standing it cannot produce.

### Fixed — a delivery to a family member that could not be handed over

`deliver_to_villager` with `"mode": "family"` credited the hand-over by **re-running the target's
`require` filter at the moment the goods changed hands**. Four bundled quests use
`"require": "nearby"` — bring mother a meal, bring the child a toy — and `nearby` means *loaded and
within twelve blocks of the quest giver*. The player's own next move after accepting is to walk away
from the giver to find the recipient, so by the time they arrived the predicate was false by
construction and the hand-over was refused. Nothing was logged, nothing was consumed, and the
objective sat at 0/1 for the rest of the quest.

`require` answers a **selection** question — who may this quest be about — and every other target mode
answers the **credit** question by identity instead: `self` compares the giver's UUID, `uuid` the
declared one, `situation_focus` the focal one. `family` alone re-ran a query. It no longer does.

- **`ObjectiveSupport.matchesLocked` binds the target before comparing**, so the credit check is a UUID
  comparison in every mode and goes through the same `resolveLocked` the quest log and the highlight
  already used. The log naming one person while the hand-over refused them is no longer expressible.
- **`RelativeCandidate.matchesIdentity`** is the identity half of a status: it drops `nearby`'s
  distance test and changes nothing else. The dead, MCA's fabricated ancestors, players and the
  genuinely missing are all still refused, so the 1.4.x fix that stopped quests naming dead relatives
  is untouched.
- **Binding at accept no longer depends on the moment the offer described.** 1.4.3 made offers
  persist rather than be recomputed, so a `nearby` relative has usually stopped being nearby by the
  time the player accepts; the bind then found nobody and wrote nothing, silently. It now falls back
  to the same person under `matchesIdentity`, and a bind that still fails is a WARN naming the quest,
  the relation and the `require`.
- Which quests are **offered** is unchanged: the offer gate still uses the full `require`.

### Fixed — a vanilla tag reported as empty on every load

`template_fisherman_catch` draws its catch from `#minecraft:fishes`, a vanilla tag with six items in
it, and every single world load answered *"template variable 'catch' uses item tag 'minecraft:fishes'
which is empty or unknown"* — logged at ERROR and counted in the load line, so the pack reported
`262 quest(s) with 1 error(s)` forever. Under `strictJsonValidation` it was worse than noise: a
`QuestValidationException` on a tag that is not in fact empty, refusing the whole quest pack.

Tag members are datapack content, bound to the registry only once a reload finishes, and the
existing guard for that — `RegistryKind.tagsBound()` — asked whether the registry knew any tag
*names*. It does, far too early: a registry gains an empty `HolderSet.Named` the moment anything
merely *asks* for a tag, and in a large modpack plenty of things ask long before the first datapack
reload applies. The guard read "bound" while nothing was bound, and every tag looked empty.

- **`tagsBound()` now asks whether any tag actually holds a member**, which only a real bind can
  produce. Empty holder sets left behind by early lookups no longer count as a bind.
- Behaviour for datapack authors is unchanged in kind: a genuinely empty or misspelled tag is still
  reported, on any reload where tags are bound (`/reload`, and every load after the first in a
  session). What is gone is the report against tags that were merely not bound yet.

### Fixed — the journal drew outside its own screen

`JournalScreen` had **no `ScrollView` and no scissor at all**. It measured its own height *while
drawing*, so scrolled content painted straight over the title and around the Back button. It now lays
the page out before drawing it, which is what makes both the measurement and the clipping possible.

Its **View Deeds** link was a hand-rolled rectangle rebuilt every frame and hit-tested by hand in
`mouseClicked` — clickable and nothing else: no keyboard, no focus, and invisible to the narrator. It
is a real widget now, which the UI acceptance bar requires.

### Fixed — smaller things the rewrite surfaced

- **The quest log's Abandon button was drawn squashed.** At 60×12 it was nine-slicing vanilla's
  200×20 sprite with 4px vertical borders, so two thirds of the button was border. There is now a
  compact button family drawn to work at that height.
- **Every toast had an empty icon slot.** All four drew their text at `x = 18`, which is vanilla's
  *icon gutter*; they now fill it, and start their text at 30 like vanilla's own.
- **Toasts ignored the notification-time accessibility setting.** All four hardcoded five seconds
  where vanilla multiplies by `ToastComponent.getNotificationDisplayTimeMultiplier()`.
- **A long toast title ran off the frame.** They now split and cross-fade at 125px, as vanilla does.
- **`ProjectToast.phaseLabel` was captured, stored, sent over the wire and never drawn**, so the
  toast for every phase of every project read identically. It is rendered.
- **The project menu threw away your scroll position** every time you contributed: the server pushes
  the whole menu again and the handler answered with a full `setScreen`. It refreshes in place.
- **The HUD's right-anchored corners lost their indent.** Every line was flushed to the right edge, so
  heading, quest and objective started in the same column and the hierarchy the indents exist to show
  was visible in two corners out of four.
- **`0xFFD24C` and `0xFFD24D`** were the same idea one blue-channel unit apart, in two different
  files. All ~24 raw ARGB literals are now named constants in `Palette`, and those two are one colour.
- **An objective's state was a colour and nothing else.** Green for done, grey for everything else —
  so "done", "waiting on a mod that is not installed" and "the villager this was about has died" were
  indistinguishable to anyone who cannot rely on colour, and the last two were indistinguishable to
  everybody. The sheets have carried a tick, a cross, a dash and an empty box since these textures
  existed and only two were ever drawn; all four are now blitted in the gutter each objective line was
  already indented by, so the states differ in shape as well as colour at no cost in layout.
- **`CardObjective.icon` was computed on the server, sent over the wire and never drawn.** An offer's
  objectives now show the item they are about, which answers "what is this asking for" before the
  sentence is read; the state glyph takes over once the quest is in progress.
- **Four `Palette` constants had no call sites at all** — `LORE`, `FILL_PORTRAIT`, `FILL_ROW_HOVER` and
  `FILL_SCRIM`, the last three superseded by `Panel.well`, `Panel.CardStyle.HOVERED` and vanilla's own
  `renderBackground`. Removed, and the last raw literal in the HUD (`0x80000000`) is named.
- **A structure marker was a skull.** `MarkerIcons` mapped `STRUCTURE` to the danger glyph, which is a
  guess about a fortress and simply false about trail ruins or a village — and either way it answers a
  question the player did not ask. The glyph is meant to say *what* the marker stands on; it is now the
  construction one, which is what every structure this can point at has in common. `BIOME` keeps the
  distance glyph: it reads as a dashed trail running off to an arrowhead, and a located biome is
  precisely a long walk in one direction.
- **A button label brightened on hover while the button behind it also changed.** Two signals for one
  event, and a coloured label on a dark button is not something Minecraft does; labels are vanilla's
  white, or vanilla's `#A0A0A0` when the control is disabled.

### Fixed — dying forgot everything

Every death, and every trip back from the End, silently reset a player's quest data: active quests,
completion history and cooldowns, titles, progression counters, the tracked quest and the villager
offers they had been shown. This has been true since 1.0.0, and the 1.4.3 note that a refusal
"survives dying" was wrong.

The cause was one line in `QuestCapabilityEvents`: the capability's optional was invalidated when
Forge removed the dying player, and `reviveCaps()` — which the clone handler correctly called — only
revives the provider, not the optional it hands out. `copyFrom` was correct and unit-tested, and was
never reached. `PlayerQuestDataProvider` now hands out a fresh optional after invalidation, and the
clone handler logs a warning if it ever finds nothing to copy again.

### Fixed — a quest stage that gained an objective crashed anyone still on it

`lost_child_2_deeper` gains an escort objective in this release. A player holding it from 1.4.x would
have crashed on login and on every tick after, because an active quest's progress list is sized once
when it is accepted and every reader indexes it by the live definition. Progress now pads itself to
the definition — the new objective simply appears at 0/1 — with one log line per quest id. The same
fix covers a pack author who adds an objective to a held quest under `/reload`.

Progress is positional: append new objectives to a live stage; do not insert one in the middle.
(`DATAPACK.md`)

### Changed — protocol

**Protocol 10 → 13. Client and server must both be updated.**

- `QuestCard.objectives` is now a list of the new `CardObjective` rather than pre-formatted
  sentences. The counts used to be baked into the text as a literal `"  (3/24)"`, which left the
  client with a string it had no business parsing; they are numbers now, which is what lets the
  progress bars and done/pending states exist.
- `QuestCard` gains `rewardIcons` and `difficulty`. Difficulty has been declared by quests since
  difficulty existed, set the currency payout, and was shown to nobody.
- `QuestLogEntry.objectives` changes the same way.
- `QuestMenuDataS2CPacket` gains `greeting`.

Then 11 → 12, for the guidance work:

- **`QuestGuidanceS2CPacket`** (new, S2C, one player) — the single place that player is being sent and
  which of their quests is sending them, or nothing. The quest is on the wire because the marker is not
  always about the quest the player pinned. An empty payload is a real message and not the absence of
  one: it is how a marker is taken away. The server only sends when the answer changes, so the steady
  state costs nothing.
- **`QuestTrackC2SPacket`** (new, C2S) — follow this quest, or stop following anything. Carries
  identifiers only; the server re-resolves against its own state and ignores a quest the player does not
  hold.
- `CardObjective.unavailable` becomes `CardObjective.state`, a four-valued enum. A boolean could say
  "on hold" but had no way to say "the villager this was about has died", so the two looked identical on
  screen. They are now drawn with different glyphs, which needs the wire to carry the difference.
- `QuestLogEntry` gains `tracked`, so the log can draw the pin on the right row.

Both new packets are **appended**. Ids in `QuestNetwork` are positional, so inserting anywhere above
renumbers every packet after it, and a client one build behind would decode a project contribution as a
toast.

Then 12 → 13, for the per-quest destinations:

- **`QuestGuidanceS2CPacket` carries a `GuidanceSnapshot`** — one `ActiveGuidance` per quest, plus the
  index of the one the marker stands on — where it carried a single optional target. The marker's
  identity travels as an **index into the list** rather than as a second copy of the record, so the
  beam and the row it belongs to can never disagree, and the wire does not carry a target twice. An
  index past the end decodes as "nothing marked" rather than throwing: it arrives over a network.
- **`GuidanceTarget` gains `lastKnown`**, appended. It is a different claim from `approximate` —
  "about 400 blocks" is about precision, "last seen 400 blocks away" is about age — and the tracker has
  worded them differently since before guidance existed. It arrived here when `TargetHint` was folded
  in, and `lastKnown` wins when a position is honestly both.
- **`QuestLogEntry.TargetHint` is gone**, and `QuestLogEntry` loses a component. It was a name and a
  `BlockPos` with **no dimension** — "an arrow across dimensions would be a lie" — and it could only
  ever name a *villager*, so a quest about an ancient city had nothing to put in it. `GuidanceTarget`
  answers the same question for places as well as people, with the dimension attached and now one per
  quest. Two answers to one question is how they drift apart.

The villager portrait deliberately did **not** cost a protocol field: the villager you are talking to
is by definition standing in front of you, so the client already has the entity and looks them up by
UUID.

### Compatibility — add-on API

Two additions, both `default` methods, so **add-ons keep compiling unchanged**:

- `QuestReward.previewIcons()` — item stacks the card may draw beside the reward text. Cosmetic only;
  `grant` remains the only thing that delivers a reward. Defaults to empty, which is the right answer
  for hearts, reputation and titles.
- `QuestObjective.icon()` — an item to draw beside an objective's line. Defaults to empty.

Then a third, also `default`:

- `QuestObjective.guidance(player, active, progress, level)` — where this objective wants the player to
  go right now, as an `Optional<GuidanceTarget>`. Defaults to empty, so an add-on's objective simply
  draws no marker until it chooses to answer. Three rules implementations must keep, documented on the
  method: answer empty once satisfied, answer empty rather than guess, and never search the world from
  it (it runs about once a second per player — anything as costly as `/locate` goes through
  `LocateCache`, which searches once and remembers across restarts).

This follows the pattern these interfaces already use for `isTriviallySatisfied` and
`unofferableReason`. Implemented for `ItemReward`, and for the item-naming objectives via a new
`ItemTarget.icon()` that represents a tag by its first member.

**One source-breaking change, on a record that has never shipped.** `CardObjective`'s `boolean
unavailable` is now a `CardObjective.State`. It is a network DTO introduced in this same unreleased
version, so nothing outside the mod can be holding it; an add-on that somehow constructs one replaces
`false` with `CardObjective.State.PENDING` and `true` with `State.UNAVAILABLE`.

**Six objective records gain a component**, each with a compatibility constructor in the shape the
record had before, so an add-on that builds one in code still compiles: `obtain_item`, `craft_item`,
`fish_item`, `kill_entity`, `break_block`, `place_block` and `item_delivery` all take
`Optional<SourceHint> source`. This is the same courtesy `ItemDeliveryObjective` was already given when
destinations were added.

**Two more source-breaking changes, both on records introduced in this same unreleased version**, so
nothing outside the mod can be holding either:

- `GuidanceTarget` gains a `boolean lastKnown` component. Every factory on it — `ofEntity`, both
  `ofPos` overloads, `withLabel` — keeps its signature and passes `false`, so only code that calls the
  canonical constructor is affected; `asLastKnown()` is the way to set it.
- `QuestLogEntry` loses its `Optional<TargetHint> target` component, and the nested `TargetHint` record
  is deleted. An add-on reading a quest's destination should read `GuidanceTarget` instead, which
  carries the dimension it never did.

**`TownsteadObjective` gains two `default` methods**, so add-on Townstead objectives keep compiling:
`guidance` (the village-or-resident answer described above) and `townsteadSubject`, which is how an
objective that is about a particular resident says so. An objective about the settlement as a whole
overrides neither.

### Added — config

- **`questTrackerStyle`** (`PANEL` / `SHADED`, default `PANEL`) — which background the HUD tracker
  draws. `questTrackerBackground` still decides whether there is one at all, so every existing config
  keeps exactly the meaning it had.
- **`highlightAllActiveQuests`** (default `false`) — restore the pre-1.5.0 outline-everything behaviour.
- **`guidanceSearchIntervalTicks`** (default `200`, range `20`–`24000`) — how long before the marker
  retries a world search that found nothing. A search that succeeds is remembered permanently on the
  objective, so this governs only the failed case.
- **`autoTrackNewQuests`** (default `true`) — accepting a quest follows it when nothing else is being
  followed. A **server** setting, because the server decides what to point you at.
- **`showQuestMarker`** (client, default `true`) — draw the world marker at all.
- **`questMarkerMaxDistance`** (client, default `256`, range `16`–`4096`) — how far away it is still
  drawn, so a beam does not stand on the horizon for a destination two thousand blocks away.
- **`easyQuestReputation`** / **`mediumQuestReputation`** / **`hardQuestReputation`** (defaults `2` /
  `4` / `7`, range `0`–`1000`) — what finishing a quest is worth to the village when the quest itself
  says nothing, in a new `[rewards.reputation]` block beside the currency bands they mirror. A quest
  that authors its own outcome still wins outright. Set all three to `0` for the behaviour they
  replace, where 252 of the 262 bundled quests were worth no standing at all.

- **`guidanceSearchesPerPass`** (default `1`, range `1`–`8`) — how many world searches one player's
  guidance pass may run. Needed because every quest now gets its own destination rather than only the
  marked one; a quest that does not get a turn is asked again next pass rather than recording a miss it
  never made.
- **`showQuestTargetCoordinates`** (client, default `true`) — append the destination's coordinates to
  the tracker line and the quest log's.
- **`showQuestLogDestination`** (client, default `true`) — show each quest's destination in the log at
  all. It never showed one.
- **`mapWaypoints`** (client, default `true`) — put quest destinations on JourneyMap and Xaero's
  Minimap, where either is installed.
- **`mapWaypointsFollowedOnly`** (client, default `false`) — reduce those to one, matching the beam.

All fourteen are documented in [CONFIG.md](CONFIG.md), along with a correction: `questTrackerMaxEntries`
was documented as defaulting to `3` and has defaulted to `5` since it existed.

### Added — translation keys

**57 new keys**, in both `en_us` and `pt_br` (2,834 → 2,891):

- `mcaquests.voice.<state>.<manner>` — 24 keys, the shipped villager voice.
- `mcaquests.tooltip.*` — 10 keys: accept, decline, complete, abandon, hearts, the two project buttons,
  and one per difficulty band.
- `mcaquests.toast.project_phase` — the project toast's title-and-phase line.
- `mcaquests.anchor.home_of` / `.workstation_of` — "Anna's home", where a destination used to be
  described as "their home" regardless of whose it was.
- `mcaquests.guidance.route_to`, `mcaquests.hud.target_approx`, `mcaquests.hud.other_dimension`,
  `mcaquests.marker.label`, `mcaquests.marker.label_approx` — the marker's own text, including the
  "about N blocks" form for a position that is a search result rather than a live reading, and the
  "in the Nether" form for a target that is not in this world.
- `mcaquests.button.track` / `.untrack`, `mcaquests.tooltip.track` / `.untrack`,
  `key.mcaquests.cycle_tracked` — following a quest.
- `mcaquests.hud.coords` — the single place X, Y and Z are formatted. Localisable, because not every
  language separates numbers with a comma.
- `mcaquests.hud.target_coords`, `.target_approx_coords`, `.target_last_known_coords`,
  `.other_dimension_coords` — the four destination lines with their coordinates appended. The
  four without them stay, for when `showQuestTargetCoordinates` is off.
- `mcaquests.guidance.your_bed` — what `sleep_or_rest` points at.
- `mcaquests.tooltip.copy_coords` / `.add_waypoint` and `mcaquests.message.coords_copied` /
  `.waypoint_added` — the quest log's two new buttons and what they say when pressed.

### Changed — dialogue

**Eight lines rewritten**, in both locales, because the quests behind them changed:

- `lost_child_2_deeper` — `offer`, `in_progress` and `ready` now say that the child is to be walked
  home, which the quest now asks for.
- `relations_letter_to_brother` — `title`, `offer`, `accept`, `in_progress` and `ready` no longer assert
  the gender of a villager MCA picks at random. The Portuguese for `relations_mend_the_quarrel` was
  corrected the same way, so the two sibling quests read alike in both locales.

Translators: these are eight existing keys whose English changed, not new keys, so they will not show up
as missing.

### Changed — bundled content

Seven quest files, no change to the totals (262 quests, 21 projects, 25 situations):

- `chains/aging_parent/3_last_walk` — the destination now names the parent, so the escort ends at their
  bed rather than the giver's.
- `chains/lost_child/2_deeper` — gains the escort home, and a deadline long enough for it.
- `adventurer/nether_relay`, `adventurer/trial_by_fire`, `chains/remedy_embers_and_wart` — blazes and
  nether wart carry `"source": {"structure": "minecraft:fortress"}`.
- `adventurer/drowned_ledger`, `adventurer/relic_beneath_the_well` — drowned, prismarine crystals and
  pottery sherds carry the ocean-ruin and trail-ruin structure tags.
- Eleven more files gain a `block` source for the fourteen objectives whose thing genuinely is a block:
  wheat, carrots, potatoes, sweet berries, quarry stone and iron ore, across the farmer, mason,
  toolsmith, cleric, unemployed, Townstead and chain content.

`adventurer/echoes_below` needs no hint: it already leads with an `enter_structure` objective, and
guidance follows the objectives in the order the pack declared them.

### Build — a jar that was never reobfuscated could be installed

A 1.5.0 development jar reached a mods folder having never been reobfuscated and died on the loading
screen: `NoSuchFieldError: UNKNOWN` from `QuestClientSetup`'s static keybinds, because
`InputConstants.UNKNOWN` is a name only a dev runtime knows. ForgeGradle reobfuscates *in place*, so
between `jar` and `reobfJar` the archive at `build/libs/mcaquests-<version>.jar` really is the
dev-mapped one; copying it by hand in that window produces a jar that looks entirely normal and then
fails the instant one of our classes touches Minecraft. An earlier copy out of the same window was
short a class outright, and hard-crashed the game at mixin-apply time on `ClientHighlightData`.

- **`gradlew installMod -PmodsDir=<mods folder>`** (or `$MCAQUESTS_MODS_DIR`) copies the finished jar,
  after reobfuscation and after the check below, so nobody has to copy `build/libs` by hand and race
  `reobfJar` for it. It warns, without deleting anything, if an older `mcaquests-*.jar` is still in
  the folder, since two of them is a duplicate-mod error rather than an upgrade.
- **`gradlew verifyReobfJar`** fails the build if the artifact names no Minecraft member by its SRG
  name, i.e. if it was never reobfuscated. `build` now depends on it, so a jar in that state cannot
  leave a completed build again.

### Documentation

- [DATAPACK.md](DATAPACK.md) — the Dialogue section said "Six states" where the code has had nine for
  some time; it now documents all nine and says when each is spoken. New **Shared voice pools** section
  for the `dialogue/` type. New **`source`** section: every field, what each one locates, why nothing is
  inferred, and what it costs. The location-anchor table gains `villager`, with the "whose bed?" rule
  and the load error that now enforces it.
- [CONFIG.md](CONFIG.md) — `questTrackerStyle` and `questTrackerBackground`, which was undocumented; the
  nine new keys above, including the new `[rewards.reputation]` block; a rewritten
  `highlightQuestTargets` row, since it no longer describes what the mod does; and
  `questTrackerMaxEntries`, documented as `3` while the code has always used `5`.
- [README.md](README.md) — the new interface, the villager voice, the marker, and
  `QuestObjective.guidance` in the extension-points bullet. "Building from source" told you to adjust an
  `org.gradle.java.home` pin that `gradle.properties` has not carried for some time; it now says to
  point `JAVA_HOME` at JDK 17, and documents `installMod` and why hand-copying `build/libs` mid-build is
  how you get a jar that dies on the loading screen.
- `RelativeCandidate`'s class javadoc claimed the gate, the binder, `matches` and the display name "all
  filter the same candidate list with the same predicate, so they cannot disagree". They filter the same
  list, but two of the seven statuses are positional and re-asking a positional predicate at a different
  instant is a different question. Corrected to say what the code does.
- `RegistryKind.tagsBound()`'s javadoc records why "the registry knows a tag name" is not the same
  question as "tags are bound", so the guard is not simplified back into the version that failed.
- [DATAPACK.md](DATAPACK.md) also gains the `nearest_village` vanilla fallback on the anchor example,
  a note on the `source` table's `anchor` row that it is how you point at a village (and why there is no
  separate `village` field), the eight objective types that moved onto the "already know where they are
  sending the player" list, and `/mcaquests debug waypoints` beside `/mcaquests debug guidance`.
- [README.md](README.md) and [CURSEFORGE.md](CURSEFORGE.md) — JourneyMap and Xaero's Minimap as
  optional integrations, with the version ranges, why neither is a compile dependency, and what happens
  to the waypoints when you uninstall this mod.
- Both docs carried **stale key counts** (README said 2,824, DATAPACK said 1,582, against an actual
  2,891). Corrected.

### Save compatibility

**Old saves load unchanged, and a save written by this version loads on 1.4.3.** Two things are new in
player data, both written only when they hold something and both absent-tolerant on read:

- `tracked` on the player's quest data — which quest they are following, as a quest id and a giver
  UUID. A player upgrading mid-quest simply follows nothing until the next quest they accept. A
  reference to a quest that is no longer active is ignored and never has to be tidied up, so nothing
  needs to run at the moment a quest completes, fails, is abandoned or vanishes in a datapack reload.
- Cached world-search results in an objective's own `progress.extra()` — the fortress a `source` or an
  `enter_structure` objective located, alongside the dimension it was found in, so a restart does not
  re-run `/locate` and a marker cannot survive a dimension change as a coordinate that means nothing.
  Downgrading leaves them as unread tags.
- Held quests whose definition gained objectives are padded on first read; nothing is written until
  the quest next saves.

### Tests

- `PanelGeometryTest` — the panel fits, stays centred and never inverts an edge, swept across every
  screen size and variant.
- `GuiTexturesExistTest` — every sprite constant matches the generator's manifest, nothing overlaps,
  and no nine-sliced sprite has an inset too large to leave a middle band.
- `VoicePoolsTest` — conditioned lines beat fallbacks, shadowing does not average, selection is
  deterministic, and no weight can make a line unreachable.
- `BuiltinVoicePoolsTest` — the shipped pools parse, cover every declared state, name only keys that
  exist in both locales, and each end in an unconditioned line.
- `QuestCardCodecTest` — round-trips the widened card, including the suspended, lost and iconless cases.
- `GuidanceCodecTest` — round-trips both guidance packets and both shapes of `GuidanceTarget`, draining
  the buffer each time. Covers entity id `0`, which a naive "0 means absent" encoding gets wrong, and an
  unknown `GuidanceKind` ordinal, which must decode as a generic place rather than drop the marker.
- `MarkerGeometryTest` — the fade is nothing inside the arrival radius and nothing past the draw
  distance, ramps at both ends rather than snapping, and never leaves `0..1` for any configured
  distance, including one set below the fade bands.
- `EscortDestinationOwnerTest` — the new load error fires on an escort sent to somebody else's bed, and
  stays quiet on the three shapes that are fine.
- `GuidanceCodecTest` also covers the quest identity now travelling with the marker, including that the
  same quest id from a different giver is a different quest.
- `BundledQuestReputationTest` — no bundled quest is worth no standing, and the pack never gates on
  standing it cannot produce.
- `RelativeCandidateIdentityTest` — the truth table for `matchesIdentity`: only `nearby` may differ from
  `matches`, and only by no longer asking where the person is standing.
- `GuidanceSelectionTest` — which quest takes the marker, and above all that a quest with nothing to
  say does not silence the ones that can. That regression could not be written as a test before,
  because the rule was tangled up in a walk over live server objects; moving it onto `GuidanceSnapshot`
  as a pure function over per-quest answers is what made it expressible.
- `ObjectiveGuidanceCoverageTest` — every registered objective type can say where it is sending the
  player, by reflection on the resolved method's declaring class rather than by scanning source. Its
  exceptions list holds one entry, `ftbq_complete_quest`, and a second test fails if that list ever
  names a type that no longer registers.
- `GuidanceTextTest` — the clipboard form, the last-known-beats-approximate rule, that every key the
  formatter can produce exists in `en_us`, and that each takes exactly the number of placeholders it is
  handed. Minecraft renders an unknown key as the key itself, so a typo would put a raw identifier on
  the HUD and nothing would throw.
- `NoMinimapStaticLinkTest` — no compiled class names a `journeymap.*` or `xaero.*` type, and nothing
  outside `compat/map/` reaches into it. Same byte-scan as the MCA and Townstead tripwires, and with no
  exemption list for the first half.
- `MapBindingProbeTest`, run by the new **`mapProbeTest`** Gradle task, replays both binding manifests
  against real jars in their own class loader:
  `./gradlew mapProbeTest -PjourneymapJar=... -PxaeroJar=...`. Either may be given alone. It unpacks
  JourneyMap's jar-in-jar API for itself — the outer jar carries none of `IClientAPI`, `Waypoint` or
  `WaypointFactory`, so probing it alone would pass while proving nothing.
- **`TestBootstrap` stopped depending on luck.** `Registries` and `BuiltInRegistries` each reach the
  other during class init, and only one entry order survives: from `BuiltInRegistries` it works, from
  `Registries` every field is still null and it dies with a bare `NullPointerException`. Whichever test
  class happened to touch a registry first therefore decided whether the whole worker ran — so several
  suites, including `GuidanceCodecTest`, passed in the full run and failed when run on their own. The
  bootstrap helper now enters from the side that survives.
- `PlayerQuestDataProviderTest` — an invalidated provider hands out a live optional over the same data.
- `ActiveQuestReconcileTest` — a one-entry progress list survives a two-objective definition, including
  the bundled `lost_child_2_deeper`.

## [1.4.3] - 2026-09-01

Two reports, one cause: the quest offer menu was recomputed from scratch every time it opened, never
remembered, and never checked against the world it described.

### Fixed — declining a quest now declines it

**"When you hit decline, it changes the context/story of every quest of that villager, but the three
quests remain the same, so it doesn't actually decline the quest."**

Both halves of that were true, and both came from the same gap. The client sent a real decision; the
server dropped it and re-rendered a menu recomputed from a seed that nothing about the refusal had
changed, so the same three quests came straight back. And because offer dialogue was resolved through
`QuestDialogueHooks` once per card per *render*, reopening the menu made a villager re-voice all three
offers — with MCA: Conversations installed, that is a whole new conversation about quests you had already
read.

A villager's offers are now **drawn once and remembered**, saved with the player's quest data:

- Reopening the menu inside the refresh window shows the same quests, the same numbers and the same
  words. Offer dialogue is voiced once, on the server thread, when the set is drawn.
- Declining removes one card and refills one card. The others do not move — not their identity, not their
  template values, not their dialogue. (`WeightedPicker` draws sequentially from a shrinking pool, so
  re-running selection with the declined quest filtered out would have replaced the entire menu.)
- The refusal survives closing the menu, relogging, dying, changing dimension and restarting the server.
- Declining is **free**: no hearts, no reputation, no failure, no effect on completion counts. Declining a
  situation offer refuses it for you alone and never resolves, fails or cancels it for anyone else.
- The villager finally says the quest's own `decline` line, which every shipped quest has always written
  and which the mod had never once displayed.
- A declined quest becomes offerable again when the villager's offers next refresh, or after
  `declineCooldownTicks` if a server owner sets one.

Declining records its own `DECLINED` outcome, so a pack can branch on it with the new
`mcaquests:quest_declined` condition — "if they turned this down, offer the softer version instead" — and
add-ons can react to the new `QuestDeclinedEvent`.

### Fixed — no more letters to siblings who do not exist

**"Sometimes there are family quests (I got the *1 paper to a sibling*) when the villager doesn't have
family (or at least a sibling in this case)."**

`relations_letter_to_brother` *was* gated: `related_villager_status sibling same_village`. The gate passed
anyway, because it asked one question and the objective asked another.

- The gate asked "is there a sibling on this village's resident roll?" — and **MCA never takes the dead
  off that roll**. `Village.residentNames` is only pruned when a villager changes village or an admin
  command removes them, never on death.
- The objective then called for "the first sibling in list order, preferring a loaded one", with no status
  filter, no deceased filter and no existence filter at all. A different sibling entirely.

`McaCompat`'s own javadoc claimed the two could never disagree. They could: only `any` and `grandparent`
shared the relation walk, and the other four relations re-derived their own list inline, skipping even the
self/nil/duplicate cleaning.

There is now **one predicate**. `McaCompat.relativeCandidates` walks the family tree once and reads the
village rolls once for the whole list, and the condition gate, the offer-time check, the accept-time
binder, `matches` and the display name are all filters over it. The statuses were tightened with it:

- **`same_village`** now requires the relative to be alive, not merely on the roll. This is the reported
  bug, and it is a behaviour change datapack authors should know about: the condition matches less often
  than it used to, and correctly so.
- **`dead`** excludes the two deceased ancestors MCA invents for every villager it spawns. A villager the
  game made up to pad a family tree was never alive, so mourning them is not a thing.
- **`alive`** and **`nearby`** exclude those invented ancestors and player nodes too.
- **`reachable`** is new: a real person a quest can send you to.
- **`any_known`** is new: the old unfiltered behaviour, for a pack that deliberately wants it.

A villager target now declares which of those it will accept, with `require`, defaulting to `reachable` —
so a pack that has never heard of the field gets the fix for free, and one that deliberately targets the
dead or the missing has to say so. Nothing is offered unless somebody satisfies it, and **accept asks
again**, because a relative can die between the menu opening and the button being clicked.

### Fixed — content that named people it had not established existed

- **`mcaquests:cure_the_infected`** asked the giver to cure "a relative of whoever is talking" while the
  situation instance already knew exactly who was infected. It uses the new `situation_focus` target.
- **`lost_child/2_deeper`** and **`lost_survey_party`** had a `mode: family` objective and **no conditions
  at all**. Both now gate on the relation they select.
- **`relations_widow_memorial`** could deliver its memorial poppies to the dead spouse it commemorates.
  Its target requires a reachable relative.
- **`townstead_names_in_the_family_book`** gated only on `is_family_member`, which asks how the *player*
  is related to the giver and says nothing about whether the giver has a findable relative.
- Every remaining `mode: family` target in the bundled pack now states its `require` outright, so the
  content is readable without knowing what the code defaults to.

### Fixed — situation offers skipped the entire eligibility chain

Situation offers were appended to the offer pool *after* the static filter chain had run, so they never
passed cooldowns, repeat rules, period completion, the Townstead content gate or the already-satisfied
check. That is how two shipped situations carried an ungated family objective for several releases. There
is now one chain, `OfferFilters`, used by the menu, by situation offers, and by `/mcaquests debug quest` —
which had kept its own copy of the chain and had already drifted out of step with the menu it described.

Situation JSON also had **no cross-reference validation of any kind** until now.

### Fixed — other gates that were not gating

- **A quest whose bound villager died sat in the log forever at 0/1.** It now says so, through the same
  suspension channel Townstead objectives use: a reason line instead of a counter, the deadline stopped,
  the quest still abandonable, and it resumes if they come back.
- **A situation's `failure` and `cleared` outcomes were parsed and discarded.** Both resolution paths
  passed a literal `null` player and both branches required one, so every `"failure": {"reputation": -10}`
  in the shipped pack was dead JSON. They reach the recorded participants now.
- **`find_missing_child` opened when a spouse went missing.** Its trigger says `"relation": "child"`;
  `MissingKinTrigger` read that field out of the JSON and ignored it.
- **`VillagerDeathTrigger.relation`** was likewise parsed and ignored, its javadoc claiming a filter "at
  offer eligibility" that had no call site anywhere. It has one now.
- **`is_family_member` rejected `spouse`** — not by design, but because `McaCompat.isFamilyOfPlayer` had
  no branch for it, so one condition hard-errored on a value its sibling accepted silently.
- **A project's `conditions` and the `maxConcurrentProjectsPerScope` cap** were not consulted when
  deciding whether to offer a *new* project. Both are now. A project already under way is deliberately
  exempt from both: hiding it would strand contributions players had already made.

### Added — controls that used to do nothing

Six config keys were declared, written into every server's TOML, documented in `CONFIG.md` with a
description of what they did, and read by nothing at all. All six are live. See
[CONFIG.md](CONFIG.md).

- **`offerRefreshTicks`** controls how long a villager keeps the same offers. The cadence had been
  hardcoded to one Minecraft day. It is now counted on the monotonic game clock rather than the world
  clock, so sleeping through a night no longer rerolls every villager in the village.
- **`enableDefaultQuestPack`** skips this mod's own bundled quests, projects, situations, titles and tier
  ladders. A datapack that *overrides* a bundled file keeps its override.
- **`defaultQuestCooldownTicks`** applies to a quest that states no `cooldown_ticks`. The effective
  default came from a hardcoded `24000`, so a server owner who set a week got a day.
- **`requireOriginalVillagerForTurnIn`** decides what a quest that states no `turn_in.mode` means — which
  is what `CONFIG.md` always said it did. At its default of `true` nothing changes.
- **`maxConcurrentProjectsPerScope`** caps how many projects one village can have running.
- **`compat.townstead.pollIntervalTicks`** gates the Townstead objective pass it had always claimed to
  share. At its default of 20 that pass is unchanged.

Two keys are new: **`declineCooldownTicks`** (default `0` — the refusal lasts until the villager's offers
next refresh) and **`declineRefillsSlot`** (default `true`).

Three dialogue states were parsed and never spoken:

- **`decline`** now is, on declining.
- **`cooldown`** and **`locked`** are shown when a villager has nothing to offer, so "I do not need
  anything right now" can become "you did that yesterday" or "not until you have done something else
  first". Rendered as an informational card under the existing no-quests state, so no protocol change was
  needed.

And **`sponsor.required_count`**, which described itself as "informational/UX" and was shown to nobody, is
on the project card when a project wants more than one sponsor.

### Added — the loader tells pack authors what the runtime is withholding

`TargetGateValidator` refuses a `mode: family` target whose `require` asserts the villager can be found
while nothing in the definition's conditions establishes that one does. The message names the file, the
objective index, the relation, and the exact block to add.

It is careful about what it will *not* claim. A leaf inside `any_of` establishes nothing, because it may
not be the branch taken. A narrower gate covers a broader target — proving a sibling exists proves a
member of `any` exists — but never the reverse, and `grandparent` covers neither. An `is_family_member`
gate does not count. And a contradiction between gate and target is only reported for a relation a
villager can have one of, because a giver may perfectly well have one dead sibling and another alive next
door.

`StructureTarget` and `BiomeTarget` can now be asked whether a running level knows what they name. A
mistyped id parses cleanly — both live in datapack-driven dynamic registries that do not exist at load
time — and then matches nothing for the rest of the save; such a quest is no longer offered. `BiomeTarget`
also gained its first `validate()`, so `{"biome": {}}` is reported rather than parsed into a target that
can never match. `LocationAnchor` refuses a `home_village` destination on a giver who has no home village.
`ProjectObjective` gained a `validate()` hook, which it had never had despite the stakes being higher than
on the quest side: an unfinishable quest strands one player, an unfinishable project phase strands a
village. And `/mcaquests validate` now reports `mcaquests`-namespaced translation keys with nothing behind
them.

### Added — observability

`/mcaquests debug offers` prints the offer set a villager is holding for you: what was drawn, when, how
long until it rerolls, and what you have already turned down. `/mcaquests debug offers reroll` discards it.
This is the observability that would have made the decline report a five-minute diagnosis.

### Changed — breaking for third-party datapacks

**A quest with a `mode: family` objective and no matching gate is now reported at load.** For this release
it is a **warning**, and a hard error only under `strictJsonValidation`; a future release will promote it.
The bundled pack is held to the strict standard at build time, because a player cannot fix a broken
built-in.

If your pack is affected, the message tells you exactly what to add — for a target selecting the giver's
sibling:

```json
"conditions": { "type": "mcaquests:related_villager_status", "relation": "sibling", "status": "same_village" }
```

Alternatively, state the intent on the target instead: `"require": "missing"`, `"require": "dead"` or
`"require": "any_known"` all assert nothing that needs establishing.

**`related_villager_status status: same_village` matches less often**, because it no longer counts
relatives who have died. That is the fix, but it will make some quests appear less than they used to.

### Compatibility

Source-breaking changes for add-ons that build these types in code. Datapacks are unaffected — every new
field is optional with a safe default.

- **`VillagerTarget`** gained a fifth component, `Optional<String> require`. The four-argument constructor
  is kept, so `new VillagerTarget(mode, profession, relation, uuid)` still compiles and means "the safe
  default".
- **`VillagerTargeted` now extends `QuestObjective`.** Every implementor in the mod already implemented
  both; an add-on that implemented `VillagerTargeted` alone must add `QuestObjective`, which it needed to
  be registered anyway. This is what lets all seven villager-targeted objectives inherit the offer-time
  and target-lost checks rather than copying them.
- **`RepeatRule`**'s `int cooldownTicks` component became `Optional<Integer> declaredCooldownTicks`, and
  **`TurnInSpec`**'s `TurnInMode mode` became `Optional<TurnInMode> declaredMode`. Both keep a
  convenience constructor taking the old shape, and both keep the old accessor name as a derived method —
  `repeat.cooldownTicks()` and `turnIn.mode()` are unchanged for readers. The Optional exists only to tell
  "the author wrote nothing" from "the author wrote the default", which is what makes the two config keys
  above possible.
- **`FailureSpec`** gained `fail_on_target_lost` (default `false`), with the old constructor kept.
- **`QuestObjective`** gained `unofferableReason(QuestContext)`, defaulting to empty, so existing objective
  types are unaffected.
- **`QuestFailedEvent.Reason`** gained `TARGET_LOST`.
- **`QuestHistory.Outcome`** gained `DECLINED`. It flows through the existing `outcomes` /
  `outcomes_by_giver` NBT, whose keys already carry the outcome name, so **the save format does not
  change**.
- **New API:** `QuestDeclinedEvent`, `RelativeCandidate`, `McaCompat.relativeCandidates`,
  `McaCompat.describeVillager`, and a `findGiverRelative` overload taking a required status.

### Save compatibility

`PlayerQuestData` gains an `offers` compound. An absent one loads as an empty store, so a pre-1.4.3 save
simply draws fresh offers on the next villager interaction. Active quests, history, titles, standing,
projects and situations are untouched. The network protocol is unchanged at version `10`.

### Localization

New keys in both shipped locales: the decline confirmation, the two target-lost suspension reasons, the
three unofferable reasons, the accept-refused line, the situation-focus target name, and the sponsor count
label.

## [1.4.2] - 2026-08-28

Three ways a quest could go quiet without saying why.

### Fixed

- **Ten periodic Townstead quests declared no `READ_CALENDAR` gate.** Every `townstead_commission_*`
  quest plus `townstead_day_off_means_day_off` and `townstead_lanterns_for_the_departed` repeats on a
  Townstead calendar period, but their `townstead_available` conditions never asked for the
  capability that reads the calendar. On any install without it they fell back to a raw tick cooldown
  — a silently different quest, which is the exact case `checkPeriodRepeat` was written to catch and
  had been reporting on every load. The capability is now declared where it is used.

- **A profession track that read as non-progressive was cached for the rest of the run.** Townstead
  resolves a profession against its data-driven registry *before* its built-in enum, so a query that
  lands before that registry is populated reports "no progression" for a trade that has one.
  `TownsteadHandles.TRACKS` had no eviction and no invalidation, so one unlucky lookup became
  permanent and the quests gated on it stayed hidden with nothing further in the log. Only
  progressive tracks are cached now; a negative answer is re-derived, which costs a single reflective
  call because the threshold bisection only ever runs on the progressive path.

- **Template tag validation could not tell an unknown tag from an unbound one.** `RegistryKind`
  treated `BuiltInRegistries` as fully known at datapack-load time, which is true of ids and false of
  tags — tags are datapack content and are bound only when a reload finishes. Until then every tag
  reads empty, which is how `minecraft:fishes`, a vanilla tag with six items in it, was reported as
  "empty or unknown" on `template_fisherman_catch`. `RegistryKind.tagsBound()` now gates the check,
  so it reports only when it can actually tell.

## [1.4.1] - 2026-08-27

### Fixed — three Townstead quests could never be finished

**MCA: Quests 1.4.0 shipped quests that were impossible.** Townstead answers a progression query for
*every* profession, handing back a zero/default track for the ones it has no progression for. This mod
could not tell that apart from a real track, so it offered a fisherman 120 profession XP that no
Townstead 0.7.6 work task ever awards. The quest parsed, validated, was offered, was accepted — and
then waited forever. Nothing in the JSON was wrong; only the running world could have said so.

Three quests were affected, and four more paid a reward that silently did nothing:

- **`townstead_deep_water_days`** now asks for a real day's fishing — 12 cod or salmon, two observed
  work shifts, and a level-2 dock — instead of fisherman XP. Title and hard reward band unchanged.
- **`townstead_the_master_tanner`** now asks for three completed work shifts, 32 leather delivered into
  the giver's inventory, and a registered leatherworker workplace. It is still a once-only capstone, but
  it no longer calls a numerical Townstead tier "master".
- **`townstead_master_of_the_trade`** is gated on the new progression-track condition and offered only
  by farmers, shepherds and butchers — the three trades Townstead 0.7.6 actually has progression for.
  A datapack that supplies a real fisherman track makes fisherman content eligible again with no code
  change here.
- **`dockside_catch`, `mend_the_nets`, `leatherworkers_order`, `tanned_and_ready`** no longer pay
  profession XP that could not be awarded. They pay +6 ordinary XP and a Townstead reaction instead.
- **`townstead_master_artisan`** (situation) requires a provably reachable tier-3 track and is offered
  only by those same three trades.
- **`townstead_a_working_village`** (project) lists the three supported trades as its guaranteed
  workforce baseline. The objective itself now counts *any* resident whose loaded track can really reach
  the tier, so a pack that adds one gets it counted without editing the JSON.

The dialogue for the three rebuilt quests was rewritten to match what they now ask for. Their titles are
unchanged, so a quest already in a journal is still recognisable.

### Fixed — other gates that were not gating

- **A situation's `conditions` block did nothing.** `SituationOffer` accepted one in the JSON and threw
  it away, so every `townstead_available` gate written into a shipped situation read like a gate and was
  not one. Offer conditions are now parsed, carried onto the derived quest, and tested against the
  candidate giver. A situation still *opens* on its signal; conditions decide who is allowed to ask.
- **`compat.townstead.contentEnabled` only ever hid situations**, despite promising to hide the quests
  and projects too. It now does what it says.
- **A datapack-defined title was pinned to English.** `Titles.displayName` returned the definition's
  `name` as a literal, so the Portuguese entry for every bundled title was unreachable. It now resolves
  the translation with the definition's name as the fallback, which leaves a pack that ships a name and
  no lang file working exactly as before.
- **Dispatched codecs built with `RecordCodecBuilder.create(...).flatXmap(...)`** are not
  `MapCodecCodec`s, so DataFixerUpper looked for their fields under a nested `value` key instead of
  inline beside `type` — and `optionalFieldOf` swallowed the mismatch without a word. `mood`,
  `townstead_value`, `townstead_profession_progress`, `townstead_spirit_progress`,
  `giver_distance_from_village`, `health_below`, `infected` and `is_family_member` were all affected.
- **Validator findings were counted but never printed.** Everything `QuestDataLoader` reported before the
  cross-reference validators logged as it went; the validators appended straight to the list, so their
  errors were summarised as "with 1 error(s)" and never shown.

### Fixed — quest cards showed internal ids instead of sentences

**A quest asking a villager to stay at work read "Keep villager.schedule.currentActivity eq work for
30 seconds".** A query path, an operator id and an enum constant, in a line meant for someone playing
the game — and because the result was long it also overflowed the offer card and was cut off mid-word,
so the objective was not merely ugly but partly unreadable.

Every Townstead description had the same shape. The rest of the mod had used a display-name helper for
this since 0.5.0; the Townstead surface simply never did. All of them now go through one vocabulary:

- **Objectives** — `townstead_state`, `townstead_change`, `townstead_schedule_streak`,
  `townstead_building_registered`, `townstead_spirit_progress`, `townstead_profession_progress`.
- **Conditions** — `townstead_value`, `townstead_building`, `townstead_spirit`, `townstead_skill`,
  `townstead_profession_track`.
- **Rewards** — `townstead_needs`, `townstead_profession_xp`, `townstead_skill`.
- **Project objectives** — building and spirit goals.
- **Context lines** — trade, schedule, village character, shifts and the anchored building.
- **Suspension reasons** — the trade a paused profession objective names.

"Keep them working for 30 seconds". "End with their hunger at least 55". "Get 1 dock built (tier 2 or
better)". "Requires: the season is winter".

An id nobody has curated — a spirit from a third-party pack, a building family this mod has never heard
of — falls back to a humanised form of its own name rather than the raw id, so nothing is ever shown
raw even where nobody has written a translation.

Three sentences were also wrong independently of the ids: a spirit goal with no named spirit rendered a
trailing space where the spirit should have been, a one-second hold read "for 1 seconds", and a boolean
comparison read "whether they are on schedule at yes" — now "Keep them on schedule".

### Fixed — long lines were cut off on every card screen

The quest menu, the quest log and the project menu all wrapped their **dialogue** and drew their
**objective and reward lines unwrapped**, straight into a scissor rectangle exactly one card wide.
Anything longer than the card was silently cut off mid-word. It surfaced through the raw-id bug above,
but the length was the trigger and not the cause: any datapack whose objective ran a little long had
always been unreadable, with no ellipsis and no way to see the rest.

All three now wrap, with continuation lines indented under the bullet so a wrapped objective still
reads as one item. The two scrolling screens **count the wrapped rows** when sizing a card, because
they position their buttons from that height and a line counted as one row while drawn as three would
have put the Accept button on top of the text.

### Added — the display vocabulary

The display vocabulary above: needs, shift activities, seasons, life stages, village spirits and
classifications, building families, comparison words, and — for the first time — **profession names**.
Those had never had curated entries at all, so `talk to a farmer` fell back to the humanised English
path on every locale; a fully translated Portuguese quest still said "Farmer".

### Added — Life of the Town: 90 new definitions

**The town is now the protagonist.** With Townstead installed, a quest observes or affects a real need,
shift, season, workplace, life stage, profession track or village character rather than hiding an
ordinary fetch quest behind a condition. Without it, the release is still substantial on its own.

- **72 new personal quests** — 262 total, of which 73 are Townstead. Nine four-stage arcs and 36
  standalones.
- **8 new village projects** — 21 total.
- **10 new living-village situations** — 25 total.
- **7 new titles**, granted by the major arc finales.

**Six Townstead arcs.** *Seasons of the Soil* is a year with one farmer, gated on the loaded calendar's
own seasons. *Harbor of Hands* takes a dock from wet stones to deep water. *Wool and Winter* runs a
pasture from its first fence to a shepherd who has earned the word. *Smokehouse Legacy* builds a
workplace and a trade in it. *The Apprenticeship Pact* names no profession at all — it binds whichever
supported trade the giver actually practises. *A Village with a Name* grows an anonymous settlement into
a civic identity.

**Three core arcs, with no Townstead anywhere.** *The Broken Road* surveys, clears, builds and finally
walks a route between two real MCA villages. *The Ashen Remedy* turns curing into an expedition and a
relationship. *The Bell at Dawn* prepares a village for a raid nobody can promise will come.

**Eight identity commissions**, one per Townstead spirit with a demonstrable building contributor in
0.7.6. Nothing bundled asks for `magical`, `spiritual`, `mining` or `natural`, because no loaded building
contributes to them and a goal nothing can reach is a quest that waits forever.

### Added — new mechanics

- **`mcaquests:townstead_schedule_streak`** counts *whole shifts a villager completed*, across days,
  logouts and restarts. This is what "help them through a week at the forge" should mean; a long
  `hold_ticks` could only fake it by asking the player to stand and watch. A finished shift ends
  **credited, missed, or unknown** — unknown being the one that makes it honest, because a villager who
  was unloaded for most of a shift is evidence of nothing either way.
- **`mcaquests:townstead_profession_track`** proves a profession goal is reachable in the *loaded*
  registry before the quest is offered. Deliberately not a whitelist: Townstead supports datapack-provided
  progression, so a pack that supplies a real fisherman track makes fisherman content eligible.
- **Two new location anchors.** `townstead_building` anchors at a registered building of a named family
  and tier; `nearest_other_village` finds the next MCA village along, never the giver's own. Both are
  **frozen at acceptance** — they are choices among several valid answers, and a choice re-made every
  second is not a destination. Two objectives in one quest that ask for the same thing are guaranteed the
  same building.
- **`deliver_to_villager` takes a `destination`.** Set it to `townstead_villager_inventory` and the goods
  go into the recipient's own inventory instead of vanishing. All-or-nothing: capacity is simulated before
  a single item leaves the player, a full recipient refuses rather than swallowing half a stack, and a
  replayed packet cannot pay twice.
- **Calendar-relative repeats.** `"repeat": {"type": "period", "period": "season"}` means once a season
  *on this server*: a fixed tick cooldown cannot, because a Townstead season may be three days or thirty.
  Custom calendar profiles are authoritative — nothing assumes four seasons, seven-day weeks or a fixed
  year length. When the calendar cannot be read, `fallback_cooldown_ticks` holds the quest instead.
- **Resident wellbeing gained `thirst_min` and `minimum_loaded_fraction`**, on both the personal objective
  and the project one. An unloaded population is no longer counted as a healthy one.
- **Offer groups.** `offer_group` on a quest names a *kind* — a need, a shift, a season, an adventure —
  and the offer menu takes at most one from each group before allowing seconds. With three slots and a
  catalogue this size, plain weighted selection regularly filled all three with variations of "someone is
  hungry". Priority still wins, and an ungrouped quest behaves exactly as it always did.

### Added — five new situation triggers

- **`townstead_calendar_transition`** (`week` / `season` / `year`) and **`townstead_life_transition`**
  (`canonical_stage` / `life_stage` / `senior`) fire on a crossing rather than a state. Prefer
  `canonical_stage`: it resolves the stage through the root's own `presentsAs` value, so a custom root
  whose adult stage is called "butterfly" still produces a coming-of-age signal.
- **`townstead_schedule_disruption`** fires when enough of a village has been off its own routine for
  long enough, with a recovery threshold well below the one that opened it so a village on the boundary
  cannot flicker a situation in and out.
- **`villager_stranded`** and **`hostiles_near_home`** contain no Townstead at all and work on a plain
  MCA install. Both search a small box around a bed or village centre rather than sweeping the world.
- **`townstead_spirit`** gained `from_classification`, `to_classification` and `transition_only`, so a
  definition can ask "has this village become a **blend**" rather than only "which spirit went up".
  Without those fields it behaves exactly as before.

**A first sighting never fires.** Baselines are seeded silently, so installing this update on an existing
world does not greet the player with a backlog of seasons that already happened, and neither a restart,
a chunk reload nor a `/reload` replays one.

### Added — two new Townstead capabilities

`READ_PROFESSION_SPEC` reads the progression *track* behind a profession — how many tiers exist, what the
XP ceiling is, whether it advances at all. `READ_SKILL_REGISTRY` proves a skill id exists before a
condition tests it or a reward tries to teach it. Both degrade independently of everything else: a
Townstead that can no longer be written to still answers "can this trade advance?", which is what decides
whether content is *offered*.

Bundled content declares **zero** skill ids — Townstead 0.7.6 has a skill registry and bundles no skill
definitions. The validation exists so a third-party pack's typo fails loudly instead of silently mutating
nothing.

### Added — achievability validation

`/mcaquests validate` now reports what the *running* Townstead cannot deliver: profession tiers and XP the
loaded track cannot reach, spirit ids the registry does not know, skill ids that do not exist, objectives
that read a capability their quest never declared, calendar-period repeats with no calendar gate, hard
quests whose every objective is carrying something, and objectives within one quest that read identically.
Findings are warnings, because a registry can change under a running server. They are also logged once at
server start and after a reload, so an operator hears about it without running a command.

Bundled content is held to a stricter standard at build time: a player cannot fix a broken built-in, so it
must never ship.

### Added — four content tags

`mcaquests:trail_ruins` and `mcaquests:ocean_ruins` (structures), `mcaquests:pottery_sherds` (items, all
twenty vanilla sherds), and `mcaquests:common_undead` (entity types — the zombie- and skeleton-family
enemies that actually spawn around a village, with no Nether-only mobs and no bosses).

### Changed

- **Config: `compat.townstead.content` sub-toggles.** `needsAndSchedules`, `professions`,
  `calendarAndLife`, `spiritAndBuildings`, `projects` and `situations`, all default `true` and all
  subordinate to `contentEnabled`. "Townstead content" is not one thing, and a server that wants the needs
  quests but not the civic identity ones previously had to take all of it or none. See **[CONFIG.md](CONFIG.md)**.
- **Quest cards say more, and only about what the quest reads.** New context lines cover the season and
  day, shifts worked out of shifts required, the frozen building a quest is anchored at, and how far a
  trade can actually go next to where the villager is in it. The needs line gained thirst — a Townstead
  villager can be perfectly fed and still going down from it.
- **`TownsteadTarget` gained `recipient`**, which resolves exactly as `bound` does. It exists because
  writing `"target": "bound"` on a delivery destination reads like an implementation detail while
  `"recipient"` says what the author means.
- **Building family names are normalised in one place.** MCA: Quests has shipped `butcher_shop` since
  1.4.0 while Townstead 0.7.6 contributes spirit under `butcher`; a condition normalising one way and an
  anchor the other would disagree about the same building, and the symptom — a quest insisting the village
  has no butcher while you stand in front of one — would be almost impossible to diagnose.
- **914 new translation keys** in each of English and Brazilian Portuguese (2,824 per locale in total),
  plus 34 reworded, covering every new quest, project phase, situation, title, objective description,
  context line, unavailable reason and display name.

### Compatibility

- **No save migration.** Every new NBT key is optional on read, frozen building anchors carry a schema
  version so a later release can add fields, and a period-history entry sits alongside the existing
  completion counters rather than replacing them.
- **No `format_version` bump.** Every new datapack field has a default, old repeat rules decode
  unchanged, old `deliver_to_villager` definitions keep consuming on hand-over, and a quest without an
  `offer_group` selects exactly as it always did.
- **Source-breaking for add-ons that implement `TownsteadBridge` directly.** The interface gained
  `professionTrack(String)`, `isKnownSkill(ResourceLocation)` and `knownSkillIds()`. An implementation
  can return `TownsteadProfessionTrackView.none(id)`, `false` and `Set.of()` to keep compiling.
  `SignalContext`, `LocationAnchor`, `OfferShaping`, `RepeatRule`, `SituationOffer`,
  `DeliverToVillagerObjective`, `TownsteadHealthyResidentsObjective` and
  `TownsteadResidentWellbeingProjectObjective` all gained record components; each keeps a constructor at
  its previous arity, so calling code compiles unchanged.
- **`townstead_healthy_residents` and `townstead_resident_wellbeing_project` complete less often than
  before** in one specific case: a village whose residents are mostly unloaded no longer counts as
  healthy. That is the bug being fixed, but it is a behaviour change for a datapack that relied on the
  old reading.
- **`townstead_profession_progress` now needs `READ_PROFESSION_SPEC`** and suspends without it. On a
  Townstead too old to expose the progression spec, these objectives pause with their progress intact
  rather than waiting on a goal nothing can verify.
- **No network protocol bump.** `offer_group` is server-only and the new context lines travel as the
  existing component list.

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
