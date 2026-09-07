# Compatibility stabilization audit — 2026-09-06

This pass covers the shared compatibility registry and lifecycle, MCA reflection and gameplay helpers,
Townstead binding/reads/mutations, FTB Quests tasks/rewards/events, MCA: Reputation import/mirror/backend,
Bountiful completion hooks, Ice & Fire registry capability discovery, Capitals capability/binding/query
paths, JourneyMap/Xaero waypoint ownership, conditional datapack mounting, and profession matching.
The NeoForge port was compared separately; its loader, data-component, FTB serialization, JourneyMap API,
and pack-construction differences were preserved. Existing Forge Capitals changes were retained and
their corresponding capability, mutation, and binding fixes transferred to the port.

## Implemented fixes

- Compatibility registration now retains insertion order, including replacement, so namespace ownership
  and diagnostics have the documented deterministic precedence.
- MCA scans reject invalid radii, ignore dead entities, and enforce spherical distance after their bounded
  AABB query. Spouse tasks and rewards filter spouses before ranking, so a better-liked friend cannot hide
  a qualifying spouse. Existing public methods remain; spouse-specific helpers are additive.
- Missing-relative checks inspect loaded entities and resident registries across server dimensions.
  Materialization checks the same facts again immediately before spawning and requires the relevant MCA
  lifecycle/safety handles. An entity loaded elsewhere or registered as an unloaded resident is not cloned.
  Missing village/building/mood handles no longer fabricate a valid zero-valued result.
- Townstead rejects non-finite/invalid need mutations and blank profession IDs. XP aliases compare the
  entire profession path rather than a suffix. Derived profession thresholds are cleared before reload,
  after datapack application, and on server shutdown; immutable reflection bindings remain cached.
- FTB publishes its real bridge only after registration succeeds. Partial startup failure leaves event
  handlers behind the unavailable bridge. The existing task cache already clears on book reload and
  server shutdown; event-driven/polled task checks retain their bounded scan and team-lock guards.
- Reputation initialization restores the built-in backend on failure. Incident success uses the canonical
  transaction's `applied` flag rather than its resulting score. Modern per-player, dimension-aware standing
  is imported before considering retained shared v1 tags; already-canonical communities are excluded to
  avoid replaying fallback mirrors as extra baseline. The existing migration marker remains unchanged.
  Zero-score communities retain their titles and tier high-water marks. Global title mirrors mutate the
  fallback capability silently, avoiding duplicate title events. Shutdown deactivates the old mirror and
  independently unregisters the mirror and import provider; failed integration startup cannot translate
  canonical events into a fallback session.
- Bountiful completion dedupe now spans its intended TTL, retains already-credited keys under floods,
  expires on clock rewind, and clears between server sessions.
- JourneyMap retains ownership when failed creation/readback cannot be cleaned up, preventing retry
  duplicates. JourneyMap and Xaero retain failed cleanup work for retry. The client reconciler's retry and
  disabled-cleanup behavior is covered in the separate client/network audit.
- Newly mounted built-in compatibility datapacks use low priority, allowing selected server datapacks to
  override bundled defaults. A real `PackRepository` regression checks insertion order rather than relying
  on the `TOP`/`BOTTOM` enum names. Existing saved pack selection order is intentionally preserved.
- Capitals retains capability-based failure handling, exact reflected member manifests, mutation guards,
  sovereign/role lookup fixes, and safe absent/partial integration behavior. The NeoForge port now includes
  the applicable existing Forge fixes; related quest/project/situation changes are covered by their owner.

## Verification and artifacts

The root task runs complete builds and probe tasks sequentially. Compatibility regression coverage
includes MCA proximity/spouse selection, real pack priority, Townstead mutation/cache behavior, failed
Xaero/JourneyMap publication and cleanup, registry ordering, and Reputation snapshot precedence,
dimension identity, mirror replay exclusion, eligibility, and zero-score title/high-water preservation.
Forge real-jar probes passed for JourneyMap/Xaero (3), Capitals 1.3.7 (3), Townstead 0.7.6 (4), Bountiful
6.0.4 (5), and Ice & Fire original/CE (2). These inspect declarations, constants and binary API shapes;
they do not imply that a running game exercised those integrations. Use [STABILIZATION.md](STABILIZATION.md)
for the final full-suite counts, additional version runs, and NeoForge outcomes.

The real Xaero probe exposed a test-runtime mapping error: `WaypointColor.LIGHT_BLUE` is present in
26.4.2, but initializing its raw Forge class invokes SRG-named `Component.m_237115_`, which is absent
from the mapped unit-test Minecraft classes. The shipped-jar probe now verifies the actual public enum
constant declarations without initialization. Separate runtime binding tests verify name-based lookup,
actual ordinals after reordering, and safe failure when an initializer cannot link. No invented palette
alias or hardcoded ordinal was added to production.

Strict item registry decoding correctly rejects the five optional CE armor IDs when Ice & Fire is
absent. The all-resource structural test now uses a test-only present-item stand-in for those exact
manifest IDs inside the CE pack, preserving every other authored field. A regression still rejects
unknown IDs, base-pack missing items, and malformed/zero counts. The CE jar probe additionally checks
the armor registration format, material/part inputs and all five exact recipe result IDs. Direct
`javap` inspection confirmed these declarations in Forge CE 1.2.7; NeoForge CE 2.1.2 carries corresponding
declarations and recipe results. Production codecs never substitute a missing item with AIR.

Available real artifacts supplied to the probe runner:

| Integration | Forge 1.20.1 evidence | NeoForge 1.21.1 evidence |
|---|---|---|
| MCA | Cached mapped jars: 7.6.20, 7.7.0-beta.2, 7.7.1-alpha.2 | Configured cached Modrinth releases `S2Ln2tIn` (7.7.36-beta.3) and `YKhJZ85x` (7.7.22) |
| Capitals | `build/capitals-source-audit/mcacapitals-1.3.5.jar`, `mcacapitals-1.3.6.jar`; local instance also has 1.3.7 | No separate NeoForge Capitals jar found |
| Townstead | `C:/Users/crims/Downloads/townstead-0.7.6+1.20.1.jar` (legacy) | No modern Townstead jar found |
| Bountiful | `C:/Users/crims/curseforge/minecraft/Instances/RealCraft/mods/Bountiful-6.0.4+1.20.1-forge.jar` | `C:/Users/crims/curseforge/minecraft/Instances/1.21.1 Neoforge Testing Grounds/mods/bountiful-neoforge-8.0.0-beta.2.jar` |
| Ice & Fire original | `C:/Users/crims/curseforge/minecraft/Instances/Towns & Dragons/mods/iceandfire-2.1.13-1.20.1-beta-5.jar` | No original NeoForge jar found |
| Ice & Fire CE | Cached Modrinth `iceandfire-ce/er2PNtdp` jar (metadata version 1.2.7) | `C:/Users/crims/curseforge/minecraft/Instances/1.21.1 Neoforge Testing Grounds/mods/iceandfire-2.1.2.jar` (metadata identifies Community Edition) |
| JourneyMap | `libs/journeymap-forge-1.20.1-6.0.4.jar` | No actual NeoForge map jar found |
| Xaero | `libs/xaerominimap-forge-1.20.1-26.4.2.jar` | No actual NeoForge map jar found |

Forge MCA jars are under
`C:/Users/crims/.gradle/caches/forge_gradle/deobf_dependencies/maven/modrinth/minecraft-comes-alive-reborn/`
with version directories ending `_mapped_official_1.20.1`. Direct class inspection confirmed the private
`villager` field and `gui.button.interact` layout marker in the **Forge** `InteractScreen` class of all
three releases. The earlier two use `forge.net.mca`; 7.7.1-alpha.2 uses `forge.net.conczin.mca`.
This is evidence for the inspected releases and package roots, not proof for every possible 7.6/7.7 jar.

The CE jar is
`C:/Users/crims/.gradle/caches/modules-2/files-2.1/maven.modrinth/iceandfire-ce/er2PNtdp/3055710405d3850721142513f59c8b6ce0794a25/iceandfire-ce-er2PNtdp.jar`.
Additional Capitals 1.3.7 evidence is
`C:/Users/crims/curseforge/minecraft/Instances/1.20.1 Test Zone/mods/mcacapitals-1.3.7.jar`.

FTB adapters compile against Forge 2001.4.22 and NeoForge 2101.1.31 plus their declared Library/Teams
dependencies. Reputation adapters compile against the corresponding local sibling's API v1 class
output. These compile checks and the guarded-package source/classfile tests are not live FTB or
Reputation playthroughs. Conversations and Numismatics are handled by the quest/API/reward audit; they
do not have a separate compatibility jar probe in this matrix.

## Build and port review

The 40 latest staged compatibility Java/test paths matched the live NeoForge sources after application.
Later resource-probe and build-verifier deltas were staged separately for the final build. NeoForge
metadata correctly bounds Minecraft to `[1.21.1,1.21.2)` and NeoForge to `[21.1.0,21.2)` with Java 21;
Forge targets Minecraft `[1.20.1,1.20.2)`, Forge 47.x and Java 17. Forge's built manifest actually carries
both mixin config names. The NeoForge artifact uses its metadata's mixin declarations and official
runtime mappings, without a Forge SRG check.

NeoForge archive verification now requires complete expanded metadata, only Quests-owned class entries,
at least one class, and class-file major versions no newer than Java 21. Its verifier test first validates
the complete real artifact, then independently corrupts a foreign class entry, removes loader metadata,
leaves a metadata placeholder, and raises one class version. Each corruption must fail for its own reason.
The existing sibling-API compile prerequisite and refusal to package a skipped Reputation adapter remain.

The NeoForge optional probe tasks now inherit ModDevGradle's JVM argument providers and launch-file
prerequisites as well as ordinary JVM arguments. Inspection of ModDevGradle 2.0.141 confirmed that its
provider supplies `fml.modFolders`; omitting it put the optional mixin plugin in the app classloader
instead of the loader holding the Mixin interface. This fixes the test launcher, without suppressing
production mixin diagnostics. The final build checks that the reported classloader error disappears.

NeoForge CE 2.1.2 does not register either spelling of the legacy cyclops multipart entity. Its
`CyclopsEyeEntity` extends `MultipartPartEntity`, which extends NeoForge `PartEntity`. The probe now
checks that actual implementation and the remaining technical registry declarations. The legacy
technical exclusions remain for older jars; no nonexistent corrected registry ID was invented.
The NeoForge-only compat pack parse test reuses the exact five-armor structural stand-in described above,
while preserving original ResourceLocation targets and their missing-content suspension checks.

## Public source signature review

Compared changed production sources against each repository's HEAD baseline. The `api/` package is
unchanged; no removed public/protected methods or public fields were found. Failure, project, reward,
and target method signatures remain callable. New interface methods have default implementations.
`PendingReward`, `CapitalRelationCondition`, and `CapitalChronicleReward` gained record components,
while retaining their original five-, two-, and two-argument constructors and their original accessors.
Their reflective record shape and generated equality/toString include the new components; NeoForge
Java 21 record-pattern source code expecting the old component count would need adjustment. This is
a source-signature review, not a blanket binary, reflective, or record-deconstruction compatibility proof.

## Manual runtime limitations

- A compiled manifest probe does not boot Minecraft or exercise third-party AI, real player capabilities,
  map UI attachment, rendering, mixin ordering, or multiplayer timing. Verify dedicated-server start,
  two-client play, login/logout, death/respawn, world switches, reload, and actual completion/rewards with
  each installed optional integration. Modern Townstead and actual NeoForge map runtime remain unverified
  without their jars.
- Exercise a missing-relative quest with the relative loaded in another dimension and with a registered
  resident unloaded. The safeguards cannot prove the absence of an arbitrary unregistered entity whose
  chunk has never loaded. MCA initialization/spawn behavior still needs the real game.
- Verify queued heart rewards with a nearby spouse and a higher-heart friend, and offline title grants
  followed by login. Reputation's API exposes global titles through community snapshots; a global-only
  canonical record may require additional upstream enumeration support for a complete title list.
- Restoring Reputation after earning more standing while it was removed creates two histories without
  provenance. Existing canonical communities remain authoritative; this pass does not guess an additive
  reconciliation that could duplicate a score. Administrators can reconcile such histories explicitly.
- A world's existing enabled-datapack order remains authoritative. If an older world explicitly retained a
  built-in pack above a custom override, move the custom pack to last/highest precedence with Minecraft's
  datapack command or selection UI.
- The code is not uniformly reflection-only: guarded FTB Quests and MCA: Reputation packages use typed
  compile-only APIs; JourneyMap uses its typed compile-only plugin API. MCA, Townstead, Bountiful,
  Capitals, and Xaero bindings avoid unconditional static third-party links.

## Final integrated result

Both release builds and all supplied-jar probes pass. The NeoForge launcher-provider repair
was exercised: the companion-plugin classloader error is absent from the final log. Forge
Capitals 1.3.5, 1.3.6 and 1.3.7 each pass all three probes. Full suite counts, skipped optional
cases, artifact fingerprints and remaining runtime checks are in [STABILIZATION.md](STABILIZATION.md).
