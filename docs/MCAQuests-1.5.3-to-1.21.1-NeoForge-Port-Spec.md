# MCA: Quests 1.5.3 — Minecraft 1.21.1 NeoForge Port Specification

Status: implementation-ready specification  
Prepared: 2026-09-03  
Source repository: <https://github.com/otectus/MCAQuests>  
Source snapshot: `main` at `e588340fa7e3d24f490887b6395db3d591e82a07` (`mod_version=1.5.3`)  
Reference port: branch `1.21.1` at `863b357491a75385c25a5a82d86e043a37403868` (older 1.1.0 feature set)  
Target: Minecraft 1.21.1, NeoForge 21.1.x, Java 21

## 1. Purpose

Port the complete MCA: Quests 1.5.3 Forge 1.20.1 codebase to Minecraft 1.21.1 NeoForge without losing behavior, content, save data, optional integrations, multiplayer authority, or dedicated-server safety.

This is a parity port, not a redesign. The finished artifact must still identify as MCA: Quests `1.5.3`; moving to a different loader is not itself a reason to change the mod version. Mixed client/server builds must be rejected through a new network protocol version.

## 2. Normative language

- **MUST**: release-blocking requirement.
- **SHOULD**: expected unless the implementation records a concrete reason to deviate.
- **MAY**: optional.
- **Parity**: externally observable behavior, serialized data, content, commands, configs, integrations, or UI behavior present in the analyzed 1.5.3 snapshot.

## 3. Source-of-truth order

When sources disagree, use this order:

1. `main` at `e588340...` for features, data formats, content, config, network fields, and runtime behavior.
2. Tests and documentation on that same `main` snapshot for invariants.
3. The existing `1.21.1` branch for proven mechanical NeoForge migrations only.
4. NeoForge/Minecraft 1.21.1 APIs and the actual dependency jars used by the target build.
5. Compiler, unit-test, probe-test, client, and dedicated-server results.

Do not copy an older port implementation when doing so would replace newer `main` behavior.

## 4. Critical repository finding and branch strategy

The repository already has a `1.21.1` branch, but it is an independent port of version 1.1.0. GitHub reports no common ancestor between the analyzed heads. It contains 373 main Java files, 189 main resource files, and 43 test files. The 1.5.3 `main` snapshot contains 604 main Java files, 334 main resource files, and 107 test files.

Relative to the existing port, current `main` has 240 Java paths that are absent from the old port and 259 same-path Java files whose blobs differ. It also has 67 additional test paths and 31 modified same-path tests. Most 1.3–1.5 functionality is therefore not represented on the existing port.

Implementation rules:

- Create the target branch from `main` at `e588340...`.
- Do not merge, rebase, or wholesale copy the existing `1.21.1` branch.
- Manually reuse small, reviewed patterns from the old branch: ModDevGradle setup, NeoForge event imports, payload structure, attachments, saved-data factories, component codecs, `neoforge.mods.toml`, toast sprites, and screen-render ordering.
- After each reused change, compare the 1.5.3 class with its old-port counterpart to preserve newer fields and behavior.
- Keep a port checklist in the PR description keyed to the sections in this document.

## 5. Scope

### 5.1 In scope

- All production Java and resources present in the 1.5.3 snapshot.
- All 107 test files, adapted rather than dropped unless the underlying invariant is obsolete.
- The complete datapack-driven quest/project/situation system.
- Vanilla UI, journal, HUD, direction guidance, world marker, player-specific target glow, keyboard behavior, accessibility settings, and client commands.
- All 21 application packets and every field they currently carry.
- Player data, all five `SavedData` stores, config persistence, and 1.20.1 Forge-world migration.
- MCA reflection binding and its probe matrix.
- Optional MCA: Reputation, FTB Quests, Townstead, JourneyMap, and Xaero integrations.
- Dedicated-server and client-only class-loading boundaries.
- Packaging, metadata, mixins, run configurations, probes, and release verification.

### 5.2 Out of scope

- New gameplay, balance changes, data-format cleanup, or API redesign.
- Replacing reflection integrations with hard links merely because a current jar happens to compile.
- Changing quest IDs, translation keys, config keys, `SavedData` names, NBT keys, or public API identifiers.
- Supporting Minecraft versions other than 1.21.1 in the target jar.
- Shipping third-party mod jars.

## 6. Release invariants

The port MUST preserve these behaviors:

1. The server remains authoritative for offers, acceptance, progress, contribution, abandonment, turn-in, rewards, reputation, and tracking.
2. Client packets never supply trusted completion, reward, target, or inventory state.
3. Existing 1.20.1 worlds retain active quests, offer sessions, tracked quest, history, titles, statistics, project state, situation state, dead-giver reconciliation, pending hearts, and Townstead signal state.
4. Optional integrations never prevent startup when absent.
5. No MCA type is statically linked. `McaBinding`/`McaHandles` remain the only bridge.
6. No Townstead type is statically linked. The integration remains probe-driven and safely suspends affected content when unavailable.
7. Xaero remains reflective. JourneyMap may use its documented v2 plugin API only inside the guarded JourneyMap package.
8. Client-only types do not resolve on a dedicated server.
9. All datapack types remain registered even when the optional mod that gives them meaning is absent.
10. Removing Townstead suspends applicable accepted content with progress/baselines intact; it does not fail or erase it.
11. Waypoint ownership, lifecycle, dimension filtering, retry/backoff, and cleanup semantics remain unchanged.
12. Quest target glow remains local-player-only and never applies a server-side glowing effect.

## 7. High-risk 1.5.3 surfaces

The old port predates these systems. They require an explicit parity pass rather than assuming compilation equals completion.

| Surface | Representative paths/packages | Required result |
|---|---|---|
| Offer integrity | `state/OfferSession*`, quest managers, offer validators | Preserve signed/session-bound decisions, expiry, stale-offer rejection, dedupe, reconnect behavior |
| Tracking/guidance | `TrackedQuest`, `ClientGuidanceData`, `QuestGuidanceS2CPacket`, `QuestTrackC2SPacket` | Preserve one destination per active quest plus primary tracked index and resync behavior |
| Marker rendering | `client/marker/**`, `QuestMarkerRenderer`, `QuestMarkerHud` | Port render buffers, partial ticks, surface placement, entity interpolation, fades, reduced motion, and off-screen cue |
| Map integrations | `client/map/**`, `compat/journeymap/**`, `compat/map/XaeroWaypoints` | JourneyMap persistent pins/map-only automatic waypoints; Xaero session-only pins; cleanup and diagnostics |
| Townstead | `compat/townstead/**`, `townstead/**`, Townstead objectives/conditions/rewards/triggers | Re-probe 0.7.5/0.7.6 NeoForge API and preserve suspend/resume semantics |
| Offline reconciliation | `DeadGiversData`, `PendingHeartsData`, login/death flows | Preserve offline giver death resolution, queued hearts, frozen villages, and exactly-once delivery |
| New UI | `client/gui/**`, `McaQuestsScreen`, `McaToast`, `McaScreenButtons`, `CardText` | Fix 1.21 screen background ordering without changing layout, focus, narration, keyboard, or tooltips |
| Data growth | added objectives, conditions, rewards, situations and Life of Town definitions | All loaders, codecs, validators, built-ins, translation keys, and tests remain present |

## 8. Target toolchain and dependency baseline

Pin a known-good baseline first. Do not chase latest versions while doing the mechanical port.

| Component | Initial target | Policy |
|---|---:|---|
| Java | 21 | MUST use a Java 21 toolchain |
| Minecraft | 1.21.1 | Exact |
| NeoForge | 21.1.248 | Known-good reference-port baseline; minimum 21.1.228 if JourneyMap 6.0.3 is in the test matrix |
| ModDevGradle | 2.0.141 | Known-good 1.21.1 baseline; update only if required by the build environment |
| Parchment | Minecraft 1.21.1 / mappings 2024.11.17 | Known-good reference-port baseline |
| MCA Reborn dev runtime | 7.7.22+1.21.1 | Runtime/probe baseline; confirm the exact Modrinth/Maven coordinate resolves |
| FTB Quests | 2101.1.31 | Compile-only and integration test baseline |
| FTB Library | 2101.1.35 | Pin explicitly; this matches the FTB Quests baseline |
| FTB Teams | 2101.1.10 | Pin explicitly |
| JourneyMap API | `info.journeymap:journeymap-api-neoforge:2.0.0-1.21.1` | Compile-only plus test implementation |
| JourneyMap runtime test | 1.21.1-6.0.3 or another recorded 1.21.1 v6 release | Supplied-jar smoke/probe; do not bundle |
| Xaero runtime test | `xaerominimap-neoforge-1.21.1-26.4.2.jar` or another recorded 1.21.1 release | Supplied-jar reflection probe; do not bundle |
| Townstead runtime test | 0.7.5 and 0.7.6 NeoForge 1.21.1 | Test both supported floor and current compatible release through supplied jars |

All optional dependency versions above are validation baselines, not permission to widen metadata ranges without evidence. Record exact filenames, hashes, and successful probe output in the release PR.

## 9. Build-system migration

### 9.1 Replace ForgeGradle/MixinGradle

- Remove `net.minecraftforge.gradle` and the legacy MixinGradle plugin/configuration.
- Apply `net.neoforged.moddev`.
- Change the Java toolchain and release target from 17 to 21.
- Configure NeoForge, Parchment, client, server, and game-test runs through ModDevGradle.
- Register the main source set under the `mcaquests` mod in the ModDevGradle block.
- Keep JUnit 5 and all existing test tasks.
- Remove Forge `fg.deobf(...)`, SRG reobfuscation, refmap-generation, and `verifyReobfJar` assumptions. Replace them with a jar-content/smoke verification appropriate to ModDevGradle.
- Keep reproducible archive settings and license inclusion.
- Update the Gradle wrapper only as required by the chosen ModDevGradle version.
- Add Foojay toolchain resolver convention if the build cannot otherwise provision Java 21; the reference branch uses version 0.8.0.

Suggested shape (adapt to the repository's actual run/task configuration):

```groovy
plugins {
    id 'java'
    id 'net.neoforged.moddev' version '2.0.141'
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

neoForge {
    version = neo_version
    parchment {
        minecraftVersion = parchment_minecraft_version
        mappingsVersion = parchment_mappings_version
    }
    runs {
        client { client() }
        server { server(); programArgument '--nogui' }
    }
    mods {
        mcaquests { sourceSet sourceSets.main }
    }
}
```

### 9.2 Dependencies

- MCA remains `runtimeOnly`; no production class may name an MCA type.
- Preserve the multi-version MCA probe configuration, using ordinary Maven artifacts rather than ForgeGradle remapping.
- FTB Quests remains `compileOnly`; add runtime/test configurations only where a test or run explicitly requires it.
- Pin compatible FTB Library and Teams versions instead of allowing Gradle to choose transitive mismatches.
- JourneyMap API is `compileOnly` and `testImplementation`; use the NeoForge artifact, not `journeymap-api-forge`.
- Xaero and Townstead stay supplied-jar probe dependencies unless a stable developer artifact is deliberately adopted. Never bundle them.
- The sibling `../MCAReputation/build/classes/java/main` compile surface must not silently disappear. Either supply the 1.21.1 sibling build/classes through a property, provide a documented Maven artifact, or isolate an intentionally minimal compile API. A release build MUST fail with an actionable message if Reputation support is advertised but its compile surface was skipped.
- Remove the hard Architectury dependency. The analyzed reference metadata records that MCA 1.21.1 no longer requires it. If the selected MCA runtime jar proves otherwise, declare it only according to that jar's metadata and record the evidence.

### 9.3 Preserve and update custom probes

Keep these tasks, renamed only if necessary:

- `McaBindingProbeTest`: one isolated classloader per supported MCA 1.21.1 jar.
- `townsteadProbeTest`: accept `-PtownsteadModernJar=<path>` and preferably a second floor-version property.
- `mapProbeTest`: accept `-PjourneymapJar=<path>`, `-PxaeroJar=<path>`, and `-PrequireMapJars=true`.
- `test`: all ordinary unit and structural tests without requiring redistributability-restricted jars.
- `check`: MUST not silently skip structural tests.

Probe tasks must emit a per-member binding report and fail if a required class, field, constructor, or method is missing.

## 10. Metadata and resources

### 10.1 Replace loader metadata

- Delete `META-INF/mods.toml`.
- Add `META-INF/neoforge.mods.toml`.
- Keep `modId=mcaquests`, `version=1.5.3`, display name, authors, license, and description.
- Declare `neoforge`, `minecraft`, and `mca` as `type="required"`, side `BOTH`.
- Declare FTB Quests, MCA: Reputation, and Townstead as `type="optional"`, side `BOTH`, ordering `AFTER`.
- Declare JourneyMap and Xaero as `type="optional"`, side `CLIENT`, ordering `AFTER`.
- Use `mca` range `[7.7,8)` unless the verified MCA matrix requires a narrower floor.
- Use FTB Quests range `[2101.1,)`.
- Use Townstead range `[0.7.5,0.8)`; verified NeoForge 1.21.1 releases exist for both 0.7.5 and 0.7.6.
- Keep JourneyMap's game-prefixed version range consistent with its actual metadata, initially `[1.21.1-6.0.0,)` if confirmed by a real jar.
- Use a Xaero range that admits the tested 1.21.1 releases but excludes an untested next major, initially `[24.6,27)` if NeoForge accepts the upstream version syntax.
- Do not declare Architectury unless runtime evidence requires it.
- Keep the `[[mixins]] config="mcaquests.mixins.json"` entry.

### 10.2 Pack metadata

Set the resource pack format to 34. If using a supported-format range, use 34 through 48. Set the data pack format to the 1.21.1 value (48) wherever data/resource formats are described separately.

### 10.3 Mixins

- Change `compatibilityLevel` to `JAVA_21`.
- Keep `MinecraftGlowMixin` and `ScreenAccessor`; both target vanilla classes, not MCA.
- Remove the legacy refmap property if ModDevGradle supplies remapped mixins without it.
- Verify `Minecraft#shouldEntityAppearGlowing(Entity)` and the `Screen` backing fields against the selected mappings at runtime, not just compilation.
- Keep the mixin config required only if both injections/accessors pass a client startup smoke test.

## 11. Main initialization

Change the mod entry point to NeoForge constructor injection:

```java
@Mod(McaQuests.MOD_ID)
public final class McaQuests {
    public McaQuests(IEventBus modBus, ModContainer container) {
        // register configs through container
        // register attachment DeferredRegister on modBus
        // add setup, client setup, config and payload listeners to modBus
        // register common gameplay subscribers on NeoForge.EVENT_BUS as appropriate
    }
}
```

Required ordering:

1. Bootstrap the mod's own objective, reward, condition, project-objective, and situation-trigger registries exactly once.
2. Register configs through `ModContainer`.
3. Register `QuestAttachments.REGISTER` on the mod bus before use.
4. Register `QuestNetwork::onRegisterPayloads` directly on the mod bus; payload registration cannot be deferred to common-setup work.
5. During common setup, initialize MCA reflection first, select Reputation integration next, and initialize Townstead last because its village-spirit paths depend on MCA state.
6. Initialize client-only key mappings, GUI layers, map bridges, and render events only on the physical client.

## 12. Package/import migration

Perform a complete source and test sweep.

| Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|
| `net.minecraftforge.*` | matching `net.neoforged.*` package |
| `MinecraftForge.EVENT_BUS` | `NeoForge.EVENT_BUS` |
| `ForgeConfigSpec` | `ModConfigSpec` |
| `net.minecraftforge.common.capabilities.*` | data attachments; do not mechanically rename |
| `net.minecraftforge.network.simple.SimpleChannel` | `CustomPacketPayload` + `PayloadRegistrar` |
| `net.minecraftforge.network.PacketDistributor` | `net.neoforged.neoforge.network.PacketDistributor` |
| `IGuiOverlay` / `ForgeGui` | `LayeredDraw.Layer` registered by `RegisterGuiLayersEvent` |
| `javax.annotation.Nullable/Nonnull` | prefer `org.jetbrains.annotations` or eliminate where unnecessary |

After migration, the following command MUST find no production Forge imports:

```bash
rg -n 'net\.minecraftforge|MinecraftForge|ForgeConfigSpec|SimpleChannel|NetworkRegistry' src/main src/test
```

Allow only literal text in migration documentation/tests that intentionally inspect legacy `ForgeCaps` data.

## 13. Event migration matrix

Use NeoForge's event classes and their current event-bus placement. Do not retain 1.20 phase enums when 1.21 exposes `Pre`/`Post` event classes.

| Current responsibility | 1.20.1 form | 1.21.1 target |
|---|---|---|
| Reload listeners | `AddReloadListenerEvent` | NeoForge `AddReloadListenerEvent`; keep all quest/project/situation/reputation/title/voice loaders |
| Server tick | `TickEvent.ServerTickEvent`, phase END | `ServerTickEvent.Post`; use `event.getServer()` |
| Player tick | `TickEvent.PlayerTickEvent`, phase END | `PlayerTickEvent.Post`; use `event.getEntity()` |
| Client tick | `TickEvent.ClientTickEvent`, phase END | `ClientTickEvent.Post` |
| Player login/logout/clone | Forge `PlayerEvent` / `ClientPlayerNetworkEvent` | NeoForge equivalents; use `LoggingIn`, `LoggingOut`, `Clone` on client |
| Commands | `RegisterCommandsEvent` | NeoForge `RegisterCommandsEvent` |
| Client commands | Forge client command event | `RegisterClientCommandsEvent` |
| GUI overlay | `RegisterGuiOverlaysEvent` + `IGuiOverlay` | `RegisterGuiLayersEvent.registerAboveAll(id, LayeredDraw.Layer)`; layer receives `GuiGraphics, DeltaTracker` |
| World marker | `RenderLevelStageEvent.AFTER_PARTICLES` | same NeoForge stage; partial tick is now a `DeltaTracker` |
| HUD post-render | `RenderGuiEvent.Post` | NeoForge `RenderGuiEvent.Post` |
| Screen injection | `ScreenEvent` | NeoForge `ScreenEvent`; preserve MCA-screen recognition by reflection |
| Entity/player/block hooks | Forge events | corresponding NeoForge events and getters |
| Sleep time | Forge sleep-finished event | NeoForge `SleepFinishedTimeEvent` |
| Config reload | `ModConfigEvent` | NeoForge `ModConfigEvent` on mod bus |

Audit every `@EventBusSubscriber` for `bus` and `value=Dist.CLIENT`. An import that compiles on the wrong bus can still produce a dead handler.

## 14. Networking rewrite

### 14.1 Protocol decision

Set the target `PROTOCOL_VERSION` to `"15"`.

Protocol 14 is the Forge `SimpleChannel` schema in 1.5.3. Version 15 denotes the NeoForge payload rewrite while preserving the same logical data. Use `event.registrar(MOD_ID).versioned(PROTOCOL_VERSION)` so incompatible client/server builds fail during negotiation.

### 14.2 Packet contract

Port all 21 packets. No packet may be omitted because it was absent from the older 1.21.1 branch.

| Direction | Payload class | Suggested payload id |
|---|---|---|
| C2S | `OpenQuestMenuC2SPacket` | `open_quest_menu` |
| C2S | `QuestDecisionC2SPacket` | `quest_decision` |
| C2S | `QuestTurnInC2SPacket` | `quest_turn_in` |
| C2S | `QuestAbandonC2SPacket` | `quest_abandon` |
| S2C | `QuestMenuDataS2CPacket` | `quest_menu_data` |
| S2C | `QuestLogSyncS2CPacket` | `quest_log_sync` |
| S2C | `QuestReadyToastS2CPacket` | `quest_ready_toast` |
| C2S | `ProjectContributeC2SPacket` | `project_contribute` |
| S2C | `ProjectMenuDataS2CPacket` | `project_menu_data` |
| S2C | `ProjectLogSyncS2CPacket` | `project_log_sync` |
| S2C | `ProjectPhaseToastS2CPacket` | `project_phase_toast` |
| S2C | `ReputationTierToastS2CPacket` | `reputation_tier_toast` |
| C2S | `RequestJournalC2SPacket` | `request_journal` |
| S2C | `JournalSyncS2CPacket` | `journal_sync` |
| S2C | `SituationToastS2CPacket` | `situation_toast` |
| S2C | `FtbqEditorIdsS2CPacket` | `ftbq_editor_ids` |
| C2S | `QuestAbandonFromLogC2SPacket` | `quest_abandon_from_log` |
| C2S | `OpenStandingC2SPacket` | `open_standing` |
| S2C | `HighlightTargetsS2CPacket` | `highlight_targets` |
| S2C | `QuestGuidanceS2CPacket` | `quest_guidance` |
| C2S | `QuestTrackC2SPacket` | `quest_track` |

Each payload MUST:

```java
public record ExamplePacket(/* fields */) implements CustomPacketPayload {
    public static final Type<ExamplePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "example"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExamplePacket> STREAM_CODEC =
        CustomPacketPayload.codec(ExamplePacket::encode, ExamplePacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```

Register with `playToServer` or `playToClient` in `RegisterPayloadHandlersEvent`. Server handlers take `(packet, IPayloadContext)`, verify `context.player()` is a `ServerPlayer`, and invoke existing server-side validation. Client handlers live in a `ClientPayloadHandlers` bridge. Do not keep per-packet `DistExecutor` wrappers.

Use:

```java
PacketDistributor.sendToServer(packet);
PacketDistributor.sendToPlayer(player, packet);
```

NeoForge play payload handlers use main-thread semantics by default; do not add nested work scheduling without a demonstrated need.

### 14.3 Wire fields that MUST survive

The implementation must compare record components and encode/decode order against `main`, especially for newer 1.5 fields:

- `CardObjective`: text/component, progress, target, state enum, and icon `ItemStack` where present.
- `QuestCard`: id, title, chain label, dialogue, objectives, rewards, reward preview stacks, and difficulty.
- `QuestMenuData`: giver UUID, name, profession, hearts, greeting/status, and cards.
- `JournalSync`: titles/deeds, villages, archive, and Reputation-present/canonical flag.
- `JournalVillageEntry`: dimension, village id/name, reputation, current and next tier, next threshold, and titles.
- `ProjectCard`: all text, objectives, rewards, phases/status, contribution state, and icons.
- `QuestLogEntry`: suspended state, giver identity, and tracked state as represented on `main`.
- `GuidanceSnapshot`: every active quest guidance record plus the primary index.
- `ActiveGuidance`: quest id, giver UUID, and `GuidanceTarget`.
- `GuidanceTarget`: kind, entity id, `BlockPos`, dimension key, label `Component`, arrival radius, approximate/last-known state, and entity bounding-box height.
- `HighlightTargets`: complete entity id array.

### 14.4 Component and item codecs

Minecraft 1.21 removed `FriendlyByteBuf.writeComponent/readComponent`. Add a small `NetComponents` helper using:

```java
ComponentSerialization.TRUSTED_STREAM_CODEC
```

against `RegistryFriendlyByteBuf`. Either give encode/decode methods `RegistryFriendlyByteBuf` parameters or keep narrow helpers that cast only buffers guaranteed to be registry-aware play payload buffers.

Unlike the older 1.1 port, 1.5.3 sends reward preview `ItemStack` values. Preserve them with the 1.21 registry-aware item codec/API. Add round-trip tests using an actual `RegistryFriendlyByteBuf`, including a stack with data components, not only a plain item/count.

### 14.5 Network tests

For every payload:

- round-trip all fields;
- test empty and maximum allowed collections;
- reject malformed enum ordinals/oversized collections where the buffer API does not already guard them;
- assert all 21 `TYPE` ids are unique;
- assert all 21 payloads are registered with the correct direction;
- assert protocol is `15`;
- assert a server handler cannot act for a UUID/entity not valid for the sending player;
- start a dedicated server to prove registration does not resolve client classes.

## 15. Player state: capability to attachment

### 15.1 Replace the capability

Delete the Forge capability registration/provider/attachment machinery:

- `QuestCapabilities`
- `PlayerQuestDataProvider`
- capability attach event handling
- clone-copy handler used solely for the capability

Create `QuestAttachments` with a `DeferredRegister<AttachmentType<?>>` against `NeoForgeRegistries.ATTACHMENT_TYPES`:

```java
public static final Supplier<AttachmentType<PlayerQuestData>> PLAYER_QUESTS =
    REGISTER.register("player_quests", () -> AttachmentType
        .serializable(PlayerQuestData::new)
        .copyOnDeath()
        .build());
```

Keep an Optional-shaped accessor if that minimizes churn, but document that `getData` now creates the attachment lazily. Register the deferred register on the mod bus.

### 15.2 Preserve the full 1.5.3 state model

The existing old-port `PlayerQuestData` is incomplete. The target implementation MUST include all current state, including:

- active quests and every current `ActiveQuest` field;
- history;
- player titles;
- progression statistics;
- offer sessions/offers;
- tracked quest selection;
- any current bounded dedupe or reconciliation markers stored in this object.

`copyFrom`, `isEmpty`, save, and load must cover every one of those fields. Add a `migrated_from_forge` boolean without changing existing key names.

Implement `INBTSerializable<CompoundTag>` with the 1.21 signatures:

```java
CompoundTag serializeNBT(HolderLookup.Provider provider)
void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag)
```

### 15.3 Mandatory Forge world migration

NeoForge attachments do not automatically read the old player-file path:

```text
playerdata/<uuid>.dat
  ForgeCaps
    mcaquests:player_quests
```

Implement `ForgeCapsMigration` based on the reference branch, with these requirements:

1. Subscribe to `PlayerLoggedInEvent` at `EventPriority.HIGHEST`.
2. Never overwrite non-empty NeoForge attachment data or data already marked migrated.
3. Read `<uuid>.dat` first, then `<uuid>.dat_old` even if the live file reads successfully but no longer has `ForgeCaps`.
4. Load exactly the nested compound at `ForgeCaps -> mcaquests:player_quests`.
5. Mark successful imports with `migrated_from_forge=true`.
6. Maintain a per-server-session set of UUIDs whose files were conclusively checked so empty players do not incur synchronous reads on every login.
7. Do not cache a failed read as conclusive; allow retry.
8. Clear the session set on `ServerStoppedEvent` so an integrated server opening another world starts clean.
9. Log imports and failures with enough context to diagnose them, but never block login for a corrupt file.
10. Complete migration before ordinary login sync and before MCA: Reputation's legacy eligibility logic observes quest state.

Migration tests MUST cover live-file import, backup fallback, live-file absent plus backup present, both absent, corrupt live plus valid backup, both corrupt, idempotence, non-empty attachment protection, and all 1.5.3 NBT fields (`active`, `offers`, `tracked`, `history`, `titles`, `stats`, start day, village binding, and any current active-quest additions).

## 16. SavedData migration

Port all five stores and preserve their `DATA_NAME` strings:

| Class | Data name |
|---|---|
| `ProjectSavedData` | `mcaquests_projects` |
| `SituationSavedData` | `mcaquests_situations` |
| `TownsteadSignalStateSavedData` | `mcaquests_townstead_signals` |
| `DeadGiversData` | `mcaquests_dead_givers` |
| `PendingHeartsData` | `mcaquests_pending_hearts` |

Changes:

- Change each save override to `save(CompoundTag tag, HolderLookup.Provider registries)`.
- Replace old `computeIfAbsent(load, create, name)` calls with `SavedData.Factory<T>`.
- Pass registry lookup to any item/component/registry serialization that needs it.
- Do not rename fields or normalize old NBT.
- Add load-save-load tests using representative 1.5.3 data.
- Add a real-world smoke test that copies a 1.20.1 world, starts it once under the port, verifies state, and only then permits further saves.

## 17. Minecraft 1.21 API changes

### 17.1 Resource locations

Replace constructors:

```java
new ResourceLocation(namespace, path)
// -> ResourceLocation.fromNamespaceAndPath(namespace, path)

new ResourceLocation(value)
// -> ResourceLocation.parse(value)

new ResourceLocation("minecraft", path)
// -> ResourceLocation.withDefaultNamespace(path)
```

Use `tryParse` only where null is an intentional validation outcome. Do not turn invalid required ids into silent defaults.

### 17.2 Registries and loot

Use resource keys for loot-table lookup:

```java
ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
LootTable table = server.reloadableRegistries().getLootTable(key);
```

Compile-drive other registry changes, especially holder-based mob effects, enchantments, item data components, and dimension/resource keys. Preserve semantic validation and do not unwrap `Holder` values without checking lifetime/registry ownership.

### 17.3 Codecs

Minecraft/DFU 1.21 is stricter around some optional codec paths. Preserve the repository's distinction between deliberately strict fields and ordinary lenient optional fields. Reuse `StrictCodecs.lenientOptionalFieldOf` (or its current equivalent on `main`) only for fields that were intended to tolerate absence/defaults. Do not globally replace strict validation to make datapacks load.

Run every bundled JSON through the loaders and validators under a real 1.21.1 registry environment.

### 17.4 Miscellaneous compile-driven changes

- Update `ItemHandlerHelper` to the NeoForge package and verify inventory insertion/remainder behavior.
- Port public API events to `net.neoforged.bus.api.Event` without changing their public fields or cancellation/result semantics.
- Update `@Nullable`/`@Nonnull` imports consistently.
- Adapt changed `DamageSource`, entity lookup, block interaction, recipe/ingredient, `ItemStack`, and data-component APIs where compilation identifies them.
- Do not use broad exception swallowing as a substitute for adapting a changed API.

## 18. Rendering, HUD, and screens

### 18.1 World marker renderer

In 1.21.1, `RenderLevelStageEvent#getPartialTick()` returns `DeltaTracker`, not a `float`. Obtain the game-time partial tick from the tracker and pass the resulting float through all interpolation calculations. Verify the boolean pause/freeze parameter against client behavior; the chosen call must not interpolate entities while the world is frozen.

The current marker uses 1.20-style vertex calls. Port:

```java
vertex(...).color(...).uv(...).endVertex()
```

to the 1.21 `VertexConsumer` builder form:

```java
addVertex(...).setColor(...).setUv(...)
```

Remove `endVertex()`. Adapt `BufferBuilder`/`MultiBufferSource.immediate(...)` to the 1.21 `ByteBufferBuilder` API and verify the selected `RenderType`/vertex format. Preserve blend, depth, culling, pose-stack balance, camera-relative positioning, billboard rotation, and batching.

Render on `RenderLevelStageEvent.Stage.AFTER_PARTICLES`. Validate:

- loaded entity interpolation;
- last-known entity position and transmitted entity height;
- block surface placement and clearance;
- compact diamond size and outline;
- distance fade and hard distance limit;
- acquire/clear/retarget fades;
- reduced-motion mode;
- third-person and first-person cameras;
- F1/debug/GUI scale behavior;
- Nether and other-dimension filtering;
- no GL/render-state leakage to later renderers.

### 18.2 HUD layers

Replace `RegisterGuiOverlaysEvent`/`IGuiOverlay` with `RegisterGuiLayersEvent`. Register the quest/project tracker through `registerAboveAll(ResourceLocation, LayeredDraw.Layer)` or a deliberately chosen neighboring vanilla layer. The render callback receives `GuiGraphics` and `DeltaTracker`.

Keep the marker direction cue on `RenderGuiEvent.Post` if z-order requires it. Verify the tracker, off-screen direction indicator, standing/journal buttons, and tooltips at GUI scales 1–4 and on narrow windows.

### 18.3 Screen double-background fix

In 1.21.1, `Screen.render` draws the background. Four old-port screens were identified as susceptible to calling `renderBackground` and then calling `super.render` at the end, producing double blur/darkening.

For every custom screen:

1. Update widget positions and visibility before `super.render`.
2. Call `super.render` once near the beginning of the override.
3. Draw custom foreground content afterward.
4. Draw tooltips last.
5. Do not explicitly call `renderBackground` unless that screen intentionally avoids `super.render` and the choice is documented.

Test quest menu, quest log, journal, project screen, standing bridge, empty/error/loading states, resizing, Escape, Enter/Space, Tab navigation, narration, and mouse tooltips.

### 18.4 Toasts

The old `textures/gui/toasts.png` atlas path is gone. Update all four toast renderers to use:

```java
graphics.blitSprite(ResourceLocation.withDefaultNamespace("toast/advancement"), ...);
```

Apply this to quest-ready, project-phase, reputation-tier, and situation toasts. Verify icon/item rendering and text wrapping.

### 18.5 Screen button injection and glow mixin

- Preserve `McaScreenButtons` as a NeoForge `ScreenEvent` integration that recognizes supported MCA screens reflectively.
- Keep `ScreenAccessor` limited to vanilla `Screen` collections.
- Preserve dedupe so resizing/re-init does not add duplicate buttons.
- Keep `MinecraftGlowMixin` forcing only `true` for entity ids in `ClientHighlightData`; it must never suppress vanilla glow.
- Clear client highlight/guidance/map state on logout and world changes.

## 19. MCA integration

The modern `main` branch intentionally removed static MCA linkage. Do not regress to the old port's earlier direct imports.

Requirements:

- Keep `compat/mca/McaBinding` and `McaHandles` as the runtime boundary.
- Probe package roots by class presence, never by version string. The current manifest considers `forge.net.mca.`, `net.conczin.mca.`, and `net.mca.`; update the ordered candidates only based on actual 1.21.1 jars.
- Re-run every roughly 60-member class/member manifest entry against each supported 1.21.1 MCA jar.
- Update only binding names/signatures that changed; do not spread reflection into gameplay call sites.
- Keep `NoMcaStaticLinkTest` scanning production constant pools.
- Keep MCA as required metadata and runtime dependency but not a compile-time production API.
- Verify villager recognition, adulthood, profession, names, hearts, relationship/family discovery, marriage, home/village resolution, dialogue/screen integration, and voice hooks.
- Preserve graceful diagnostics: startup must state which root bound; an unsupported layout must fail with the missing manifest member, not a later opaque `ClassNotFoundException`.

Test at least the selected development MCA jar and every version claimed by the metadata range that has a materially different package layout. If that matrix is impractical, narrow the advertised range.

## 20. Optional integrations

### 20.1 General rules

- Register the mod's own types unconditionally.
- Gate backend activation on NeoForge `ModList` plus a successful binding/API initialization.
- Do not load optional-mod classes just to decide whether the optional mod exists.
- Diagnostics commands must distinguish: not installed, disabled by config, bind failed, bound, and runtime call failed.
- A failed optional integration must log once with actionable details and leave base MCA: Quests playable.

### 20.2 MCA: Reputation

- Port the 1.5.3 bridge and current sibling API, not only the older branch's surface.
- Replace event bus imports and post/listen through `NeoForge.EVENT_BUS`.
- Preserve canonical-standing ownership, mirrored fallback standing, title/deed behavior, journal `View Deeds`, and legacy import ordering.
- Keep the `OpenStandingC2SPacket` registered unconditionally; handler no-ops safely if Reputation is not canonical.
- Verify removal/reinstallation does not reset standing.
- Run both without Reputation and with the exact 1.21.1 build used for compilation.

### 20.3 FTB Quests

- Port all current two-way task/reward/condition/objective integration classes.
- Use FTB Quests `2101.1.31`, FTB Library `2101.1.35`, and Teams `2101.1.10` as the first compatible matrix.
- Preserve unconditional registration of the MCA: Quests packet/type surface and conditional send/use behavior.
- Adapt any FTB serialization/UI APIs to 1.21.1; do not remove editor-known-id sync.
- Test without FTB, with FTB server+client, FTB book read/grant in both directions, and editor id sync.

### 20.4 Townstead

Townstead 0.7.5 and 0.7.6 have NeoForge 1.21.1 builds, so the target should preserve full integration rather than compiling it out.

- Keep the reflection boundary and `NoTownsteadStaticLinkTest`.
- Probe both 0.7.5 (metadata floor) and 0.7.6.
- Update the binding manifest for any moved class/member while keeping all third-party names inside the guarded binding package.
- Validate every need, profession/progress, skill, building, spirit/identity, resident-wellbeing, inventory insertion, shift/hold-timer, project, situation, condition, objective, trigger, and reward path present in 1.5.3.
- Preserve configuration split between mechanics enabled and bundled content enabled.
- Preserve frozen baselines and suspend/resume behavior if Townstead disappears mid-quest.
- Keep `townsteadProbeTest` outside ordinary `check` only because it requires a supplied jar; release validation MUST run it.

### 20.5 JourneyMap

- Compile against `info.journeymap:journeymap-api-neoforge:2.0.0-1.21.1` from JourneyMap's documented Maven repositories.
- Keep the documented v2 `IClientPlugin` entry point and plugin annotation in `compat/journeymap/**`.
- Recompile and probe `IClientAPI`, `IClientPlugin`, `JourneyMapPlugin`, waypoint factory overloads, dimensions, primary dimension, color, visibility, persistence, create/update/remove, and readback against the selected 1.21.1 JourneyMap jar.
- Automatic quest destinations remain map-only/non-user-persistent where supported; explicitly pinned quest destinations retain the current persistent-pin behavior.
- Keep the backend registry thread-safe because plugin activation order is not deterministic.
- Ensure no JourneyMap class is resolved on a dedicated server or when the mod is absent.

Update `NoMinimapStaticLinkTest` so JourneyMap types are allowed only inside `compat/journeymap/**`; Xaero types remain forbidden everywhere.

### 20.6 Xaero's Minimap

- Keep `XaeroWaypoints` reflection-based; do not add static Xaero imports.
- Re-run `MapBindingProbeTest` against a concrete NeoForge 1.21.1 jar, initially `xaerominimap-neoforge-1.21.1-26.4.2.jar` (and preferably the metadata floor).
- Update the centralized class/member manifest rather than scattering fallback reflection.
- Preserve session-only quest pins, named enum/origin construction, current-dimension-only capability, per-session identity, retry/backoff, and cleanup on logout/world change.
- Do not save automatic quest destinations into the user's permanent Xaero waypoint list.

### 20.7 Map reconciliation behavior

`WaypointReconciler` is gameplay logic, not loader glue. Keep its behavior unchanged:

- one resolved destination per active quest;
- a primary tracked destination;
- event-driven dirty marking rather than unconditional rebuilds;
- bounded retry/backoff after backend unavailability;
- cross-dimension suppression for backends that cannot represent it;
- update/move instead of duplicate create;
- remove on completion, failure, abandonment, logout, and config disable;
- no orphan waypoints after uninstall/reconnect.

## 21. Data, datapacks, and content

- Copy every `data/**` and `assets/**` file from the 1.5.3 source branch before making format edits.
- Do not use the old `1.21.1` branch's smaller resource tree as the target set.
- Preserve every quest, project, situation, reputation tier, title, voice pool, tag, schema example, and translation key.
- Preserve `data/<namespace>/mcaquests/{quests,projects,...}` discovery paths.
- Run all built-in content through reload listeners and validators in a real server environment.
- Keep author-facing diagnostics/export-schema commands.
- Update vanilla resource formats only where 1.21.1 changed them; custom MCA: Quests JSON schemas remain stable unless a Minecraft registry representation forces a change.
- If an item/entity/block id no longer exists in 1.21.1, record the exact replacement and migration rationale; do not silently drop the objective/content.
- Compare translated key sets across `en_us` and `pt_br`; fail on missing keys introduced by the port.

## 22. Commands and configuration

- Port the root server command and every subcommand, including validation, schema export, Townstead diagnostics, FTB diagnostics, and any repair/recheck tooling.
- Port `/mcaquestsclient` waypoint status/probe and marker/guidance client controls.
- Preserve permission levels and server/client command separation.
- Replace config types/imports with NeoForge `ModConfigSpec` while keeping file names, categories, keys, defaults, ranges, comments, and restart/reload semantics.
- Ensure all new marker/map/client settings from 1.5.x remain in the CLIENT spec and are never read from dedicated-server common code.
- Test config loading from an existing 1.20.1 file and generation from a clean instance.

## 23. Testing strategy

### 23.1 No test deletion by default

Start by porting all 107 tests. A test may be removed only if the PR records:

1. the exact obsolete invariant;
2. the target API behavior that replaces it; and
3. the replacement test or reason no automated replacement is possible.

Changing a package name is not a reason to weaken a structural test.

### 23.2 Required automated suites

1. **Pure model/codec tests**: quests, objectives, conditions, rewards, projects, situations, offer integrity, tracking, guidance, map reconciliation, and NBT.
2. **Datapack validation**: every built-in JSON and cross-reference under 1.21.1 registries.
3. **Network tests**: all 21 payloads, directions, ids, registry-aware Components/ItemStacks, and server validation.
4. **Persistence tests**: attachment full-field round trips, five `SavedData` stores, and ForgeCaps live/backup migration.
5. **Static-link tests**: MCA, Townstead, Xaero, client-only dedicated-server hazards, and JourneyMap package boundary.
6. **Binding probes**: all claimed MCA, Townstead, JourneyMap, and Xaero baselines.
7. **UI/model tests**: card text, layout, marker geometry/projection, target selection, keyboard/focus state, toast wrapping.
8. **Jar tests**: metadata, mixin config, pack formats, class/resource completeness, no Forge classes, no bundled third-party classes.

### 23.3 Runtime matrix

| Scenario | Required checks |
|---|---|
| Dedicated server, only required mods | Startup, datapack reload, player join, quest lifecycle, no client-class resolution |
| Client + integrated server | All screens, inputs, markers, HUD, toasts, reloads, save/reopen |
| Two multiplayer clients | Per-player offers, glow, tracked destination, reward authority, no information leakage |
| No optional mods | Full base gameplay and clean diagnostics |
| FTB only | Both directions of progress/rewards and editor ids |
| Reputation only | canonical/fallback standing and journal bridge |
| Townstead only | all Townstead content paths and suspend/resume |
| JourneyMap only | plugin activation, create/update/remove/persist policy |
| Xaero only | reflection bind, session pins, dimension restriction, cleanup |
| Both map mods | independent enable/disable and no duplicate churn |
| All optional mods | load ordering, classpath conflicts, full smoke |
| Upgraded 1.20.1 world | all player/world data retained, including offline cases |

### 23.4 Manual quest lifecycle smoke

For at least one simple quest, one chained/relative quest, one timed quest, one project, one situation, one Townstead quest, and one FTB-linked quest:

1. generate offer;
2. reconnect before decision;
3. accept valid offer and attempt a stale/replayed decision;
4. progress partially and save/restart;
5. change dimension and return;
6. test giver unload/death/offline resolution;
7. complete/turn in;
8. verify rewards/hearts/standing exactly once;
9. confirm history, title/stat updates, guidance cleanup, glow cleanup, and waypoint cleanup.

## 24. Implementation phases

Each phase should end in a reviewable commit. Do not postpone all tests until the final phase.

### Phase 0 — Freeze inventory

- [ ] Branch from `e588340...`.
- [ ] Record source file counts and dependency versions.
- [ ] Capture a representative 1.20.1 player file and world containing every persistent system.
- [ ] Save current unit/probe outputs as parity baselines.

Exit: source inventory and fixtures are committed or stored in the test-fixture mechanism.

### Phase 1 — Build and metadata

- [ ] Adopt Java 21 + ModDevGradle.
- [ ] Add NeoForge 21.1.x and Parchment.
- [ ] Replace metadata and pack formats.
- [ ] Update mixin config to Java 21.
- [ ] Establish empty client/server launch.

Exit: `compileJava` reaches source/API errors rather than build configuration failure; metadata expands correctly in the jar.

### Phase 2 — Mechanical source migration

- [ ] Replace imports, annotations, config types, ResourceLocation constructors, and basic APIs.
- [ ] Port bootstrap and event buses.
- [ ] Port all reload listeners and commands.

Exit: non-network/non-persistence production code compiles; no unintended Forge imports remain.

### Phase 3 — Networking

- [ ] Port all 21 payloads and DTO codecs.
- [ ] Add `NetComponents` and registry-aware ItemStack handling.
- [ ] Register payloads with protocol 15.
- [ ] Port sends and handlers.
- [ ] Add direction/round-trip/security tests.

Exit: all packet tests pass and dedicated-server registration is safe.

### Phase 4 — Persistence

- [ ] Replace capability with attachment.
- [ ] Include all 1.5.3 player-state fields.
- [ ] Add robust ForgeCaps migration.
- [ ] Port all five `SavedData` classes.

Exit: synthetic and real 1.20.1 fixtures migrate without loss and remain stable after a second save/load.

### Phase 5 — Required MCA behavior

- [ ] Re-probe MCA 1.21.1 layouts.
- [ ] Update reflection manifest only where necessary.
- [ ] Verify screen recognition, hearts, relationships, relatives, village, dialogue, and giver lifecycle.

Exit: complete base-mod quest lifecycle passes with no static MCA link.

### Phase 6 — Client UI and rendering

- [ ] Port screens, widgets, focus/narration, toasts, layers, marker render buffers, and DeltaTracker usage.
- [ ] Verify mixins and client state clearing.
- [ ] Fix double backgrounds and z-order.

Exit: UI/render manual matrix passes without visual state leakage or crashes.

### Phase 7 — Optional integrations

- [ ] Reputation.
- [ ] FTB Quests.
- [ ] Townstead 0.7.5/0.7.6.
- [ ] JourneyMap v2 NeoForge API.
- [ ] Xaero reflection probe.
- [ ] Combined-load matrix.

Exit: every advertised integration has a recorded successful target-jar probe and runtime smoke.

### Phase 8 — Content and parity audit

- [ ] Validate all resources and translations.
- [ ] Compare every 1.5.3 main path against the target branch.
- [ ] Classify each deleted/renamed path in the PR.
- [ ] Run full automated and runtime matrices.

Exit: no unclassified feature/resource/test loss.

### Phase 9 — Release packaging

- [ ] Build clean from a fresh checkout with Java 21.
- [ ] Inspect final jar contents and metadata.
- [ ] Start dedicated server and client from the release jar.
- [ ] Upgrade a copied world and verify backups exist.
- [ ] Record dependency matrix, jar hashes, probe logs, and known limitations.

Exit: all acceptance criteria below pass.

## 25. Acceptance criteria

The port is complete only when all are true:

- [ ] `./gradlew clean check build` succeeds on Java 21.
- [ ] Client and dedicated server both start from the built jar.
- [ ] Mod metadata reports MCA: Quests 1.5.3, Minecraft 1.21.1, and NeoForge dependencies correctly.
- [ ] No production `net.minecraftforge` references remain.
- [ ] All 21 payloads register, round-trip, and use protocol 15.
- [ ] No MCA, Townstead, or Xaero production static links exist; JourneyMap links are confined to its guarded package.
- [ ] All five `SavedData` ids are unchanged.
- [ ] A representative 1.20.1 world upgrades without losing any player or world state.
- [ ] A second launch after upgrade does not duplicate or reset data.
- [ ] All current built-in quest/project/situation content loads and validates.
- [ ] Base gameplay works with no optional mods.
- [ ] Every advertised optional integration passes its target matrix.
- [ ] Marker, HUD, screens, toasts, glow, and waypoints behave correctly and clean up.
- [ ] Two-client tests show server authority and player-local highlight/guidance behavior.
- [ ] Every source/resource/test path removed from 1.5.3 is explicitly justified.
- [ ] Release notes warn users to back up worlds before the one-way loader migration and list exact tested dependency versions.

## 26. Failure policy

- Required dependency or required MCA binding failure: fail startup early with a precise diagnostic.
- Optional integration bind failure: disable only that integration, log the failed member/version once, expose it in diagnostics, and keep base gameplay running.
- Corrupt legacy player file: try `.dat_old`, log failure, allow login, and do not mark the check successful if both reads failed.
- Invalid built-in datapack: fail the development/release validation; do not ship by downgrading it to a warning.
- Unsupported claimed dependency version: either fix and test it or narrow the metadata range.
- Unknown parity deletion: release blocker.

## 27. Suggested verification commands

Adapt property names to the final build, but retain equivalent gates:

```bash
./gradlew clean test
./gradlew check build

./gradlew McaBindingProbeTest
./gradlew townsteadProbeTest \
  -PtownsteadModernJar=/absolute/path/townstead-neoforge-1.21.1-0.7.6.jar
./gradlew mapProbeTest \
  -PjourneymapJar=/absolute/path/journeymap-neoforge-1.21.1-6.0.3.jar \
  -PxaeroJar=/absolute/path/xaerominimap-neoforge-1.21.1-26.4.2.jar \
  -PrequireMapJars=true

rg -n 'net\.minecraftforge|MinecraftForge|ForgeConfigSpec|SimpleChannel|NetworkRegistry' src/main src/test
rg -n 'forge\.net\.mca|net\.conczin\.mca|net\.mca\.' src/main/java \
  -g '!dev/otectus/mcaquests/compat/mca/**'
rg -n 'xaero/' src/main/java
```

Also inspect the built jar with `jar tf`, then launch it in clean client and server run directories rather than relying only on development classpaths.

## 28. PR review checklist

Reviewers should answer these questions explicitly:

1. Was the branch created from 1.5.3 `main`, not the old port?
2. Did any copied old-port class lose fields added after 1.1.0?
3. Are all 21 packets and all current wire fields present?
4. Does player attachment serialization include offers and tracking?
5. Can a real Forge player file migrate after NeoForge has already rewritten the live `.dat`, using `.dat_old`?
6. Do all five world stores load under their original names?
7. Are client and optional-mod class-loading boundaries enforced by tests?
8. Were Townstead, JourneyMap, and Xaero probed against actual 1.21.1 jars?
9. Are screens rendered with exactly one background pass?
10. Are toasts using the 1.21 sprite API?
11. Does the marker use `DeltaTracker`, 1.21 vertex methods, and the new buffer API correctly?
12. Does a full path-level parity diff have zero unexplained removals?
13. Are dependency ranges no wider than the tested matrix supports?
14. Do clean dedicated-server and two-client multiplayer smokes pass?

## 29. Reference material

Repository references:

- 1.5.3 source snapshot: <https://github.com/otectus/MCAQuests/tree/e588340fa7e3d24f490887b6395db3d591e82a07>
- Existing older 1.21.1 port: <https://github.com/otectus/MCAQuests/tree/1.21.1>
- Current network registry: <https://github.com/otectus/MCAQuests/blob/e588340fa7e3d24f490887b6395db3d591e82a07/src/main/java/dev/otectus/mcaquests/network/QuestNetwork.java>
- MCA binding: <https://github.com/otectus/MCAQuests/blob/e588340fa7e3d24f490887b6395db3d591e82a07/src/main/java/dev/otectus/mcaquests/compat/mca/McaBinding.java>
- Marker/map design: <https://github.com/otectus/MCAQuests/blob/e588340fa7e3d24f490887b6395db3d591e82a07/docs/MCAQuests_JourneyMap_Xaero_Marker_Spec.md>
- Offer integrity design: <https://github.com/otectus/MCAQuests/blob/e588340fa7e3d24f490887b6395db3d591e82a07/docs/OFFER_INTEGRITY_SPEC.md>
- Existing port audit: <https://github.com/otectus/MCAQuests/blob/1.21.1/PORT_AUDIT_1.21.1.md>
- Existing port notes: <https://github.com/otectus/MCAQuests/blob/1.21.1/PORTING_1.21.1_NEOFORGE.md>

External API references:

- NeoForge 1.21.1 documentation: <https://docs.neoforged.net/docs/1.21.1/>
- NeoForge 1.21.1 source: <https://github.com/neoforged/NeoForge/tree/1.21.1>
- NeoForge networking/payloads: <https://docs.neoforged.net/docs/1.21.1/networking/payload/>
- NeoForge data attachments: <https://docs.neoforged.net/docs/1.21.1/datastorage/attachments/>
- JourneyMap API: <https://github.com/TeamJM/journeymap-api>
- JourneyMap Maven/API setup: <https://github.com/TeamJM/journeymap-api/blob/1.21.1_2.0.0/README.md>
- Xaero developer Maven guidance: <https://www.curseforge.com/minecraft/mc-mods/xaeros-minimap>
- Townstead releases: <https://www.curseforge.com/minecraft/mc-mods/townstead/files/all>

## 30. Definition of done

“Builds on NeoForge” is not the definition of done. Done means the 1.5.3 feature and data surface has been accounted for path by path; required and optional integrations have been exercised against real target jars; a copied Forge world has survived migration; dedicated server and multiplayer have been tested; and every gate in Section 25 is green.
