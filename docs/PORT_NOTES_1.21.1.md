# MCA: Quests 1.5.3 → Minecraft 1.21.1 / NeoForge 21.1 — Port Notes

This document captures decisions and implementation details from the NeoForge 1.21.1 port (spec: `docs/MCAQuests-1.5.3-to-1.21.1-NeoForge-Port-Spec.md`). It is a companion to [CHANGELOG.md](../CHANGELOG.md) and `CLAUDE.md`.

---

## Platform Move & Requirements

- **Minecraft**: 1.21.1 (exactly; not compatible with 1.20.1 or 1.21.0)
- **Mod loader**: NeoForge 21.1.248 (Forge 47.x no longer supported)
- **Java**: JDK 21 (Gradle provisions via foojay toolchain resolver if JAVA_HOME is 17)
- **Mod version**: unchanged at `1.5.3` (platform is expressed by MC/loader targeting, not the mod's semver)

Dependencies have shifted:

| Dependency | 1.20.1 Forge | 1.21.1 NeoForge | Change |
|---|---|---|---|
| MCA Reborn | 7.6.x | 7.7+ | MCA dropped Architectury |
| Architectury API | required | not required | no longer a transitive dep of MCA |
| FTB Quests | 2001.4.x | 2101.1.x | major version bump |
| Townstead | 0.7.5–0.7.6 | 0.7.5–0.7.6 | unchanged |
| MCA: Reputation | `[0.2,)` | `[0.2,)` | unchanged from 1.20.1 |
| JourneyMap | `[1.20.1-6.0.0,)` | `[1.21.1-6.0.0,)` | game-version prefix only; artifact `journeymap-api-neoforge:2.0.0-1.21.1` |
| Xaero's Minimap | `[26.0.0,)` | `[26.0,27)` | floor unchanged, capped below untested next major |

A 1.20.1 config file loads as-is; file names, keys, defaults, and ranges are unchanged.

---

## Network & Wire Data

- **Protocol**: 15 (was 14; bumped for payload rewrite to NeoForge `CustomPacketPayload` + `StreamCodec`)
- **Incompatibility**: Clients and servers on different protocol versions cannot join; handshake is strict
- **Components**: Use `ComponentSerialization.TRUSTED_STREAM_CODEC` (no cast helpers)
- **ItemStacks**: Use `ItemStack.OPTIONAL_STREAM_CODEC` (note: 1.20.1 `writeItem` accepted empty stacks; 1.21.1 throws on empty — the `OPTIONAL` codec handles this)

21 payloads via `event.registrar(MOD_ID).versioned("15")` with structured `CustomPacketPayload` + `STREAM_CODEC` idiom.

---

## Player Data Migration: Forge → NeoForge (One-Way)

**Existing Minecraft 1.20.1 Forge worlds are automatically upgraded on first login.** This is one-way — players must back up worlds before upgrading.

### How it works

1. On server tick 20 (after login), `ForgeCapsMigration` reads `playerdata/<uuid>.dat` and looks for `ForgeCaps` tag
2. If found, `mcaquests:player_quests` is extracted and written to the NeoForge attachment
3. The attachment is marked `migrated_from_forge: true` so the import never re-runs
4. If read fails (corrupted NBT), the error is **not cached** — retry on next login, never block login

### World Save Names (Unchanged)

All five `SavedData` stores keep their names:

- `mcaquests_projects` — village project progress (shared across villages)
- `mcaquests_situations` — active situations (time-limited village emergencies)
- `mcaquests_townstead_signals` — Townstead integration state (if Townstead is installed)
- `mcaquests_dead_givers` — recorded giver deaths (pruned after 20 in-game days)
- `mcaquests_pending_hearts` — deferred heart rewards (for unloaded villagers)

These stores are defined in `project/state/ProjectSavedData.java`, `quest/situation/state/*.java`, `state/DeadGiversData.java`, `state/PendingHeartsData.java` via `static final SavedData.Factory` pattern.

---

## Configuration System

Config is now NeoForge `ModConfigSpec` (was Forge `ForgeConfigSpec`). The change is transparent:

- File names: `mcaquests-common.toml`, `mcaquests-client.toml`
- All option keys, defaults, ranges, documentation: **identical** to 1.20.1
- Existing 1.20.1 config files load without modification

---

## Rendering Pipeline Changes

### Screens

Four screens (ProjectMenuScreen, QuestLogScreen, QuestMenuScreen, JournalScreen) follow a unified z-order pattern:

1. Update widget state (before any draw)
2. Call `super.render(...)` — draws background, widgets, and the Minecraft blur
3. Draw foreground overlays (panels, labels, etc.)
4. Draw tooltips last

This avoids the 1.20.1 flicker from explicit `renderBackground` and ensures correct layering. See commit `863b357` for the reference diff.

### Toasts

Single background sprite: `blitSprite(withDefaultNamespace("toast/advancement"), 0, 0, width(), height())`. Deleted the `TEXTURE` field.

### HUD Tracker

Implements `LayeredDraw.Layer`, rendering via `render(GuiGraphics, DeltaTracker)`. Registered in `QuestClientSetup`:

```java
RegisterGuiLayersEvent.registerAboveAll(
    fromNamespaceAndPath(MOD_ID, "quest_tracker"),
    layer
);
```

### Quest Marker

Uses 1.21 vertex API:

- Partial tick: `DeltaTracker.getGameTimeDeltaPartialTick(boolean)`
- Vertex consumer: `addVertex(...).setColor(...).setUv(...).setOverlay(...).setLight(...).setNormal(...)`
- Buffer: `ByteBufferBuilder` + `MultiBufferSource.immediate(ByteBufferBuilder)`

---

## Datapack Format

- **Pack format**: 34 (1.21.1); supported formats 34–48 per `pack.mcmeta`
- **Custom JSON schemas**: **Identical to 1.20.1** — all quest, project, situation, title, and tier JSON schemas are unchanged
- **Bundled data**: Byte-identical to 1.5.3 (only `pack.mcmeta` changed; data and assets are the same)

Datapacks authored for Forge 1.20.1 load without modification on NeoForge 1.21.1.

---

## Known Content Quirk: `defend_location` Flat Shape

About ten bundled `defend_location` objectives are written in a flat shape (legacy format):

```json
"entity": "minecraft:zombie",
"entity_count": 8
```

Instead of the correct nested shape:

```json
"threat": {
  "entity": "minecraft:zombie",
  "entity_count": 8
}
```

These decode to an empty objective list (the nested `"threat"` key is required). This is **unchanged from 1.5.3** — the quirk predates this port and is tracked as a datapack maintenance item, not a port issue.

Affected files (to review when updating bundled content):

- `src/main/resources/data/mcaquests/mcaquests/quests/chains/bell_watch_before_dawn.json`
- `src/main/resources/data/mcaquests/mcaquests/quests/chains/bell_when_the_horns_answer.json`
- `src/main/resources/data/mcaquests/mcaquests/quests/chains/road_clear_the_cut.json`
- `src/main/resources/data/mcaquests/mcaquests/quests/townstead/townstead_commission_bells_for_old_names.json`
- `src/main/resources/data/mcaquests/mcaquests/quests/townstead/townstead_commission_watch_at_the_gate.json`
- `src/main/resources/data/mcaquests/mcaquests/quests/townstead/townstead_lanterns_for_the_departed.json`
- `src/main/resources/data/mcaquests/mcaquests/situations/monster_in_the_cellar.json`

Note: `src/main/resources/data/mcaquests/mcaquests/quests/guard/hold_the_gate.json` is the one `defend_location` use that IS correctly nested. Future content audits will fix the seven files above.

---

## Datapack Codec Changes

Two implementation details preserve the JSON wire format:

### MapCodec + Dispatch

Codec fields shifted from `SimpleCodec.of(…)` to `MapCodec` with dispatch. This affects how optional fields are handled but does not change JSON output — fields still appear in the same place, with the same names and types.

### Lenient Optional Semantics

Custom optional fields keep the 1.20.1 lenient behavior via `lenientOptionalFieldOf` (defined in `data/StrictCodecs.java`). Unknown fields do not throw; they are skipped. This preserves forward/backward compatibility with datapacks authored for 1.5.3.

---

## Reflection Boundaries

To isolate and make safe the optional-mod integrations:

### MCA Reborn

- **Binding**: `compat/mca/McaBinding.java` + `McaHandles.java`
- **Package root probe** (in order): `forge.net.conczin.mca.`, `forge.net.mca.`, `net.conczin.mca.`, `net.mca.` (MCA 7.7.36-beta.3+1.21.1 binds at `net.conczin.mca.`)
- **Access**: Reflective; no static imports anywhere
- **Enforcement**: `NoMcaStaticLinkTest` fails the build if any compiled class imports an MCA type

### Townstead

- **Binding**: `compat/townstead/` — methods resolved by name and arity, invoked via `MethodHandle` with `Object` arguments only
- **Access**: Purely reflective; no static imports, no compile link
- **Enforcement**: `NoTownsteadStaticLinkTest` fails the build if any compiled class imports a Townstead type

### Xaero's Minimap

- **Binding**: `compat/xaero/` — types resolved by name
- **Access**: Purely reflective; no static imports, no compile link
- **Enforcement**: `noCompiledClassReferencesXaero` (in `NoMinimapStaticLinkTest`, ~line 86) fails the build if any compiled class imports a Xaero type

### JourneyMap (Exception)

- **Binding**: `compat/journeymap/` — **typed against the compile-only API 2.0**, loaded via JourneyMap's own plugin discovery mechanism
- **Access**: Static imports allowed **only in `compat/journeymap/`** (not reflective; uses the published API)
- **Enforcement**: `NoMinimapStaticLinkTest` restricts JourneyMap references to the dedicated package
- **Note**: The API jar is never shipped; it is used at compile time only

### FTB Quests

- **Binding**: `compat/ftbq/` — internal bridge routes FTB-facing tasks/rewards through no-op stubs if FTB is absent
- **Access**: Reflective; no shipped code
- **Enforcement**: Build checks that FTB types are not imported outside `compat/ftbq/`

---

## Probe & Jar Blockers

Three optional probe tasks require supplied NeoForge jars:

### MCA Binding Probe

```ps1
./gradlew McaBindingProbeTest
```

Replays the binding manifest against every MCA build listed in `mca_probe_versions` (default: `S2Ln2tIn` and `YKhJZ85x`). Catches package-root migrations at build time. **No jar needed** — probes against cached Modrinth artifacts.

Exit code 0 = both versions bound successfully. Exit code non-0 = at least one failed.

### Townstead Probe

```ps1
./gradlew townsteadProbeTest -PtownsteadModernJar=<path-to-neoforge-jar> [-PtownsteadFloorJar=<path>]
```

Replays the Townstead binding manifest against a real Townstead jar. Requires:
- `-PtownsteadModernJar=<path>` — Townstead 0.7.5+ NeoForge jar (required)
- `-PtownsteadFloorJar=<path>` — Townstead 0.7.5 NeoForge jar (optional; used to test version transitions)

Skipped silently if jars are absent (unless `-PrequireTownsteadJars=true` is passed).

### Map Backend Probes

```ps1
./gradlew mapProbeTest -PjourneymapJar=<path> -PxaeroJar=<path> [-PrequireMapJars=true]
```

Replays map bindings against real JourneyMap and Xaero jars. Requires:
- `-PjourneymapJar=<path>` — JourneyMap 1.21.1-6.0.x NeoForge jar (optional)
- `-PxaeroJar=<path>` — Xaero's Minimap 26.0+ NeoForge jar (optional)

Skipped silently if jars are absent (unless `-PrequireMapJars=true` is passed).

---

## Runtime Verification

### Completed

**Dedicated-server startup smoke (2026-09-03).** The server was launched via `./gradlew runServer` with only the required mods (MCA 7.7.36-beta.3+1.21.1 from the runtime classpath). Results:

- **Server reached** `Done (6.638s)!` — no crashes, no `NoClassDefFoundError` or `ClassNotFoundException`, no `net.minecraft.client` references.
- **Mod list** showed `mcaquests 1.5.3` and `mca 7.7.36-beta.3+1.21.1`.
- **MCA binding** logged `Bound to Minecraft Comes Alive at 'net.conczin.mca.' (68 members)` with one optional member absent and a fallback used (`server.world.data.Village#hasResident/1`).
- **Data loaders** reported: 262 quests (0 errors, 6 authoring warnings about `targets 'any' relative`), 1 reputation tier ladder, 25 situations (0 errors), 9 titles, 4 dialogue pools, 21 projects (0 notes).
- the pre-existing `run-server/world` from the older 1.21.1 port (saved under mod version 2.0.0) loaded without error.

### TODO

Not yet verified (awaiting supplied jars or environment setup):

- [ ] Townstead 0.7.5/0.7.6 NeoForge jars bound and probe green
- [ ] JourneyMap 6.0.x and Xaero 26.4.x NeoForge jars bound and probe green
- [ ] A real 1.20.1 Forge world upgraded end-to-end:
  - [ ] Player data migrated correctly (quests, history, titles, stats tracked)
  - [ ] Village data preserved (projects, situations, reputation)
  - [ ] No crashes on login or during normal gameplay
- [ ] Client visual smoke (multiple quest types, marker styles, occlusion modes, Townstead integration)
- [ ] Multiplayer: two-client session with server-persisted progress

---

## Code Parity & Path Classification

Every 1.5.3 source and resource file present except:

**Justified deletions** (replaced by NeoForge equivalents):

- `src/main/java/dev/otectus/mcaquests/state/PlayerQuestDataProvider.java` → capability provider; replaced by data attachment
- `src/main/java/dev/otectus/mcaquests/state/QuestCapabilityEvents.java` → capability events; replaced by data attachment
- `src/test/java/dev/otectus/mcaquests/support/PlayerQuestDataProviderTest.java` → replaced by `PlayerQuestDataAttachmentTest`
- `src/main/resources/META-INF/mods.toml` → replaced by `neoforge.mods.toml`

**Files changed minimally** (structure preserved, platform details updated):

- `src/main/resources/pack.mcmeta` — only `pack_format` and `supported_formats` changed
- Build files (`build.gradle`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`, `settings.gradle`) — NeoForge/MDG substitutions
- `src/main/resources/mcaquests.mixins.json` — `compatibilityLevel` bumped to JAVA_21

All other Java, test, data, and asset files are present with their structure intact.

---

## Build System Notes

### JAR Smoke Check

The `jarSmokeCheck` task (wired into `check`) runs after `jar` and asserts:

1. `META-INF/neoforge.mods.toml` is present
2. `mcaquests.mixins.json` is present
3. No `net.minecraftforge.*` classes are bundled (Forge port artifact would be an error)
4. No third-party classes from MCA, FTB Quests, JourneyMap, Xaero, or Townstead are bundled

This prevents accidental shipping of compile-only or optional-only code.

### Reputation Compilation

MCA: Reputation is compiled against via property `mcaReputationClasses`, defaulting to `../MCAReputation_1.21.1/build/classes/java/main`. If the path does not exist:

- Normal `compileJava`, `test`, etc. fail with a `GradleException` naming the path and the build command
- Passing `-PskipReputationCompat=true` excludes `compat/reputation/**` from compilation and disables the integration
- **However**: `jar` task fails in `doFirst` with `-PskipReputationCompat=true`, preventing accidental shipping without Reputation support

This ensures the build either compiles with Reputation support or explicitly opts out (and refuses to ship).

### Test Fixtures

Probe tasks (`McaBindingProbeTest`, `townsteadProbeTest`, `mapProbeTest`) are separate `Test` tasks sharing the NeoForge unit test classpath. They use `-P` properties to accept jar paths at build time:

```gradle
test {
    useJUnitPlatform()
}

task McaBindingProbeTest(type: Test) {
    useJUnitPlatform()
    filter { includeTestsMatching "McaBindingProbeTest" }
    // classpath = test.classpath
    // jvmArgs = test.jvmArgs
}

task townsteadProbeTest(type: Test) {
    useJUnitPlatform()
    filter { includeTestsMatching "TownsteadBindingProbeTest" }
    doFirst {
        if (project.hasProperty("townsteadModernJar")) {
            // copy to temp location for classpath
        }
    }
}

// Similar for mapProbeTest
```

Skipping is via `@EnabledIfSystemProperty` and the `-PrequireXxxJars` properties.

