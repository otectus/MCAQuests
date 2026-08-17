# MCA Quests 1.21.1 NeoForge Port — Parity & Correctness Audit

**Date:** 2026-08-16 · **Audited:** branch `port/1.21.1-neoforge` @ `21d4c61` · **Baseline:** commit
`e57b11c` (verified byte-identical, modulo CRLF, to the 1.20.1 Forge repo `c:/Projects/MCAQuests`
@ `2d1757d`, v1.1.0). All parity claims below derive from `git diff e57b11c HEAD` plus verification
against the actual resolved dependency jars in the Gradle cache.

**Audience:** a coding agent applying fixes. Every actionable item has file:line, evidence, a
concrete fix, and an acceptance check. Items in §5 are explicitly **not** to be "fixed".

---

## Resolution status (2026-08-16)

Everything actionable in this audit has been applied except where noted. The body below is kept
verbatim as the record of *why* each change was made — read it as a history, not as a to-do list.

| Item | Status |
|---|---|
| §1.1 toast checkerboard | **Fixed** — all four toasts draw the `toast/advancement` sprite. Sprite presence (and the absence of `textures/gui/toasts.png`) re-confirmed against `minecraft_1.21.1_client.jar`. |
| §1.2 double background | **Fixed** — all four screens let `super.render` draw background + widgets first, then paint content; per-frame button repositioning moved above it. `Screen.render` → `renderBackground` re-confirmed in `neoforge-21.1.248-sources.jar` (`Screen.java:132,377`). |
| §2.1 ForgeCaps fragility | **Fixed** — `<uuid>.dat_old` fallback, ERROR-level logging naming the fallback, and a per-server-session checked-player set. Six new tests cover the two-file fallback; a read failure deliberately leaves the player *unchecked* so the next login retries. |
| §2.2 `pack.mcmeta` | **Fixed** — `pack_format` 34 with `supported_formats` 34–48. |
| §2.3 `FTBQUESTS.md` install text | **Fixed** — 2101 artifacts/ranges, `type="optional"`; CurseForge slug unified on `ftb-quests-forge`. |
| §2.4 "five Forge events" | **Fixed.** |
| §2.5 `ftb_library_version` | **Fixed** — 2101.1.35. |
| §2.6 unbounded platform ranges | **Applied** — `[1.21.1,1.21.2)` / `[21.1.0,21.2)`. Chosen over inherited looseness because this build genuinely cannot run on 1.21.2+. |
| §3 MCA Reputation gap | **Open, blocked** — the sibling `MCAReputation_1.21.1` still does not exist, so `compat/reputation/**` is still excluded from the jar. The cross-mod login-ordering constraint from §3.2 is now pinned in `ForgeCapsMigration`'s class javadoc. |
| §4.1–4.5, 4.7, 4.8 | **Fixed.** |
| §4.6 `v1.1.0-forge-final` tag | **Not done** — it is one command in the *other* repo (`c:/Projects/MCAQuests`) and out of scope for this branch. |
| §4.9 `dispatchMap` encode | **Deliberately not changed**, per the item's own advice: the path is dead today. |
| §5 | Untouched, as instructed. |

Verification after the fixes: `gradlew build` green; **316 tests / 45 classes, 0 failures, 0
skipped** (310 before; the 6 new ones are the `.dat_old` fallback cases). §7's residual risk stands
unchanged — the dev-client, dedicated-server, and world-upgrade smokes still need a human at a
running game.

---

## 0. Verdict

The port is high quality and near-complete parity. The full unit suite passes (**310 tests / 45
classes, 0 failures, 0 skipped**, forced re-run via `gradlew test --rerun-tasks`). Networking,
persistence/migration, core quest logic, FTBQ compat, config, commands, events, and mixins were
audited subsystem-by-subsystem and are at parity, with symbols verified against the real
`mca-neoforge-7.7.22+1.21.1`, `ftb-quests-neoforge-2101.1.31`, `neoforge-21.1.248`, and FML
`loader-4.0.43` artifacts.

**Two real runtime bugs exist, both client rendering regressions invisible to unit tests** (§1).
One structural parity gap exists: the MCA Reputation integration is currently excluded from the
built jar because the sibling `MCAReputation_1.21.1` port does not exist yet (§3).

Priority order for a fixing agent: §1 (P1, must fix) → §2 (P2, should fix) → §4 (P3, optional).
After §1 fixes, run the Phase-14 dev-client smoke from `PORTING_1.21.1_NEOFORGE.md` §16.2 — no
recorded evidence of a post-port client smoke run exists in the repo, and both P1 bugs would have
been caught by one.

---

## 1. P1 — Runtime bugs (must fix)

### 1.1 [MAJOR] All four toasts render the missing-texture checkerboard

**Files:** `src/main/java/dev/otectus/mcaquests/client/QuestToast.java:13,23` ·
`ProjectToast.java:13,25` · `ReputationTierToast.java:13,23` · `SituationToast.java:13,23`

**Evidence:** each class does

```java
private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/toasts.png");
...
graphics.blit(TEXTURE, 0, 0, 0, 0, this.width(), this.height());
```

`minecraft:textures/gui/toasts.png` was removed in the 1.20.2 GUI-sprite split. Verified against
the 1.21.1 client jar in the NeoForm cache: `toasts.png` — 0 matches; sprites
`assets/minecraft/textures/gui/sprites/toast/*` present. The blit compiles and binds the fallback
texture, so every quest-ready / project-phase / reputation-tier / situation toast draws its
background as the magenta/black checkerboard. (Root cause: `PORTING_1.21.1_NEOFORGE.md` §11
wrongly prescribed "Toasts: only the ResourceLocation constructor fix".)

**Fix (all four classes):** replace the blit with the sprite call and delete the constant:

```java
graphics.blitSprite(ResourceLocation.withDefaultNamespace("toast/advancement"), 0, 0, this.width(), this.height());
```

`toast/advancement` is the sprite carved from the exact sheet region (0,0,160,32) the old code
sampled. The `Toast` interface itself is unchanged in 1.21.1 (`Visibility render(GuiGraphics,
ToastComponent, long)`, 160×32 defaults) — only the background draw needs changing.

**Accept:** dev client; trigger each toast (quest ready, project phase, tier up, situation);
background renders as the grey toast frame, no checkerboard.

### 1.2 [MAJOR] Four screens draw the menu background twice, blurring/darkening their own text

**Files:** `src/main/java/dev/otectus/mcaquests/client/QuestLogScreen.java:144,217` ·
`QuestMenuScreen.java:153,183` · `ProjectMenuScreen.java:119,141` · `JournalScreen.java:40,55,111`

**Evidence:** every screen keeps the 1.20.1 structure:

```java
this.renderBackground(graphics, mouseX, mouseY, partialTick);   // top of render()
// ... custom drawString / scissored card content ...
super.render(graphics, mouseX, mouseY, partialTick);            // bottom of render()
```

In 1.20.1 `Screen.render` only drew widgets, so this was correct. In 1.21.1 (verified in the
decompiled `Screen.java` from the NeoForm cache) **`Screen.render` itself calls
`renderBackground` first**, and `renderBackground` runs the full-framebuffer blur post-process
plus the semi-transparent `INWORLD_MENU_BACKGROUND` tile. The custom text is already flushed to
the framebuffer when the trailing `super.render` runs (screen-phase `GuiGraphics` is unmanaged —
`drawString`/scissor flush immediately), so the just-drawn content gets re-blurred and darkened
every frame; only the widgets stay crisp. It also fires NeoForge's
`ScreenEvent.BackgroundRendered` twice per frame.

**Fix (mechanical, per screen):**
1. Delete the explicit `this.renderBackground(...)` call.
2. Move `super.render(graphics, mouseX, mouseY, partialTick)` to the **top** of `render()`
   (it now draws background + widgets).
3. Draw the custom/scissored content **after** `super.render`.
4. Any per-frame widget repositioning/visibility logic that currently runs between the two calls
   (e.g. the scrolled-button reposition blocks) must move **above** the `super.render` call so
   widgets are placed before they are drawn.
5. Visually check z-order per screen afterwards: content now draws over widgets where they
   overlap — none of the four screens intentionally overlaps cards with buttons, but confirm
   tooltips/buttons still sit on top where expected.

**Accept:** dev client; open quest log (`L`), quest menu (villager), project menu, journal (`J`):
text is crisp (not blurred/dimmed), background drawn once, buttons render above content and are
clickable.

---

## 2. P2 — Robustness & metadata (should fix)

### 2.1 [MINOR] ForgeCaps migration: one transient read failure permanently destroys legacy data

**File:** `src/main/java/dev/otectus/mcaquests/state/ForgeCapsMigration.java:64-68`

**Evidence:** first NeoForge login of a 1.20.1-upgrader reads `playerdata/<uuid>.dat` raw; on any
`Throwable` (e.g. transient AV/file lock — plausible on Windows hosts) it logs and lets the player
"start fresh". NeoForge then rewrites the player file **without** the unknown `ForgeCaps` tag on
the next save, so the retry on the following login finds nothing — the 1.20.1 quest data is gone.
Vanilla's `<uuid>.dat_old` still holds the blob for one more save cycle and is never consulted.

**Fix:** on primary-read failure *or* on a successful read that lacks the `ForgeCaps` →
`mcaquests:player_quests` blob, additionally try `<uuid>.dat_old` before giving up; log at ERROR
(not WARN) when the primary read throws, mentioning the `.dat_old` fallback. To avoid re-reading
files every login for quest-less players (currently the gate `!data.isEmpty() ||
data.migratedFromForge()` never becomes true for them — a per-login synchronous full-file read at
HIGHEST priority forever), add a per-server-session in-memory `Set<UUID>` of already-checked
players. Keep the migration idempotent via the existing persisted `migrated_from_forge` flag;
do not write any new NBT for players with no legacy data.

**Accept:** existing `ForgeCapsMigrationTest` still green; add a test case: primary tag missing +
`.dat_old` containing the legacy blob → data imported. Manual: corrupt `<uuid>.dat`, keep valid
`.dat_old`, log in → data present, ERROR logged.

### 2.2 [MINOR] `pack.mcmeta` declares the data-pack format, not the resource-pack format

**File:** `src/main/resources/pack.mcmeta:5` — `"pack_format": 48` (48 = 1.21.1 *data* format;
the resource half wants 34). Near-zero functional impact (NeoForge force-loads mod packs; the
cached MCA jar ships format 6 and works), but it's the one metadata value that's simply wrong.

**Fix:** set `"pack_format": 34`, optionally add `"supported_formats": {"min_inclusive": 34,
"max_inclusive": 48}`. **Accept:** dev client loads, no pack-format warning in the log.

### 2.3 [MINOR] `FTBQUESTS.md` install section is stale 1.20.1/Forge data

**File:** `FTBQUESTS.md:7` — claims "Tested against FTB Quests **2001.4.x** (`ftb-quests-forge`),
FTB Library 2001.2.9, FTB Teams 2001.3.0 … `mandatory=false`, version range `[2001.4,)`".
Reality (gradle.properties / neoforge.mods.toml): `ftb-quests-neoforge` **2101.1.31**, FTB Library
2101.1.35 (see §2.5), FTB Teams 2101.1.10, `type="optional"`, `versionRange="[2101.1,)"`. Only the
protocol-"8" row of this file was updated in the port; the install paragraph was missed (README
and CHANGELOG already say 2101.x).

**Fix:** rewrite the paragraph with the 2101 artifacts/ranges and NeoForge `type="optional"`
wording. While in the file: `FTBQUESTS.md:3` and `DATAPACK.md:1224` link the CurseForge slug
`ftb-quests-forge` while `README.md:43` links `ftb-quests` — unify (the historical, known-good
slug is `ftb-quests-forge`, which also hosts NeoForge files).

### 2.4 [MINOR] `README.md:31` — "five Forge events"

The API events now extend `net.neoforged.bus.api.Event`; every other doc was reworded.
**Fix:** "five Forge events" → "five NeoForge events".

### 2.5 [MINOR] `gradle.properties:41` — `ftb_library_version=2101.1.34` is not what compiles

The ftb-quests-neoforge 2101.1.31 POM requires ftb-library-neoforge **2101.1.35**; Gradle conflict
resolution upgrades the classpath (cache holds a jar only for .35). The pin is misleading
documentation of what was verified. **Fix:** set `ftb_library_version=2101.1.35`.

### 2.6 [MINOR, optional] Jointly unbounded platform ranges claim 1.21.2+/21.2+ compatibility

`gradle.properties:9,11` → shipped toml: `minecraft_version_range=[1.21.1,1.22)`,
`neoforge_version_range=[21.1.0,)`. This build (1.21.1 payload codecs, mojmap mixin targets,
MCA 7.7.22+1.21.1) will not run on 1.21.2+, and the `mca [7.7,8)` dep does not constrain MC
version. This mirrors the 1.20.1 convention (`[1.20.1,1.21)`, `[47,)`), so it is inherited
looseness, not a port regression — fix at the maintainer's discretion.
**Fix:** `[1.21.1,1.21.2)` and `[21.1.0,21.2)`.

---

## 3. Structural parity gap — MCA Reputation integration not in the built jar

**File:** `build.gradle:112-119`. `MCAReputation_1.21.1` does not exist on disk, so the build
logs a warning and applies `sourceSets.main.java.exclude 'dev/otectus/mcaquests/compat/reputation/**'`.

Consequences, all verified:
- A jar built today **ships without** `CanonicalReputationBackend`, `QuestsReputationEvents`,
  `QuestsReputationMirror`, `QuestsLegacyImportProvider` — the 1.20.1 v1.1.0 release jar had them.
  Runtime degrades gracefully: `ReputationBridge`'s `Class.forName` seam catches the absence and
  falls back to `LegacyReputationBackend` with one ERROR line; the `isLoaded("mcareputation")`
  gate is preserved (`ReputationBridge.java:46`).
- None of the four `compat/reputation` sources have ever been **compile-checked against 1.21.1**
  — their diffs (bus renames, `ResourceLocation.fromNamespaceAndPath`) look correct by
  inspection, but `McaReputationApi`/`ReputationRequest`/`TitleScope`/event signatures are
  unverifiable until the sibling exists.
- The 1.20.1 build **failed loudly** in this situation by spec ("spec 9.2"); the port silently
  excludes. Deliberate for porting sequencing, but easy to forget.

**Actions when porting `MCAReputation_1.21.1`** (policy reminder: its version stays **0.1.0**):
1. Build it, rebuild this mod, confirm `compat/reputation/**` compiles and the classes are in the jar.
2. Verify its `PlayerLoggedInEvent` handler stays at **NORMAL** priority: this mod's
   `ForgeCapsMigration` runs at HIGHEST and must complete before Reputation's
   `LegacyImportProviders.runFor(...)` reads the quest attachment
   (`QuestsLegacyImportProvider.java:106,117`). If Reputation's handler ever moves to HIGHEST,
   a Forge-upgrader could be judged ineligible against a not-yet-migrated attachment. Add a
   comment pinning this ordering in one of the two mods.
3. Consider restoring the loud-fail build behavior once the sibling exists.

---

## 4. P3 — Cosmetic / latent / housekeeping (optional)

| # | Where | Item |
|---|-------|------|
| 4.1 | `QuestProgressEvents.java:72`, `QuestEventHandlers.java:12`, `ProjectLifecycleEvents.java`, `ProjectProgressEvents.java`, `McaQuestsCommand.java` | Dead `import net.neoforged.fml.common.Mod` left after `@Mod.EventBusSubscriber` → `@EventBusSubscriber`. Compiles fine; delete. |
| 4.2 | `ClientPayloadHandlers.java:14` | Javadoc overstates the classload-safety mechanism: the class itself *does* load on a dedicated server (method-ref registration resolves it); safety comes from client classes appearing only in never-executed `playToClient` handler bodies. Reword; behavior is correct. |
| 4.3 | `FtbqEventBridge.java:29-30` | Broken javadoc `{@link MinecraftForge#EVENT_BUS}` (class gone). Doc-only. |
| 4.4 | `McaBooleanTaskBase.java:16-21`, `McaCounterTaskBase.java:22-29`, `FtbqTaskTypes.java:16-21,56-63`, `FtbqRewardTypes.java:16-19`, `FtbqEventBridge.java:38-64` | Javadocs still cite FTBQ **2001.4.22** file:line evidence. Every load-bearing claim was re-verified true against 2101.1.31 bytecode — update the citations or note "re-verified 2101.1.31". |
| 4.5 | `PORTING_1.21.1_NEOFORGE.md:62,148,615,940` | Doc still prescribes `mod_version=2.0.0`, contradicting the adopted keep-1.1.0 policy (all shipped metadata correctly says 1.1.0). Add a one-line erratum so the doc doesn't mislead sibling ports. |
| 4.6 | 1.20.1 repo (`c:/Projects/MCAQuests`) | Porting doc §17.4 prescribes tagging the last Forge commit `v1.1.0-forge-final`; no tag exists. One command in the *old* repo. |
| 4.7 | `TestBootstrap` | Real-`Bootstrap` path silently falls back to the reflective flag-flip on Throwable. Not a weakening (old suite only had the flip), but print one `System.err` line on fallback so regressions are visible. |
| 4.8 | `build.gradle:46` | `neoforge.enabledGameTestNamespaces` sysprop is vestigial — the old `gameTestServer` run config wasn't ported and no GameTest sources exist. Delete the line (or port the run config). |
| 4.9 | `StrictCodecs.java:68` | `dispatchMap` encode path would duplicate a `"type"` key if an inner codec ever emitted one. Dead path today (all dispatched codecs are decode-only; no inner codec emits `type`). Filter the key only if encode is ever used. |

---

## 5. Do **NOT** "fix" these (verified non-issues)

A fixing agent must not act on the following — each was investigated and is correct as-is:

1. **`QuestClientSetup.java:15` missing `bus = MOD` is CORRECT.** An intermediate review flagged
   this as a client crash. Verified false against FML `loader-4.0.43` sources: the
   `EventBusSubscriber.bus()` attribute is **ignored**; `AutomaticEventSubscriber` routes each
   `@SubscribeEvent` method by `IModBusEvent.class.isAssignableFrom(eventType)` — mod-bus events
   (`RegisterKeyMappingsEvent`, `RegisterGuiLayersEvent`, both verified `IModBusEvent`) go to the
   mod bus, mixed classes are split per-method ("registering them separately" path). Keybinds and
   the HUD layer register correctly. Re-adding `bus = MOD` would only add deprecated noise.
2. **`NetComponents.java:22` unchecked `(RegistryFriendlyByteBuf)` cast** — safe: every caller is
   a play-phase payload and play buffers are always `RegistryFriendlyByteBuf` in 1.21.1; the
   javadoc documents the assumption. Only revisit if reused for configuration-phase payloads.
3. **Protocol/registrar version `"8"`** (`QuestNetwork.java:31`) — deliberate wire-protocol bump
   (Component wire format JSON→NBT), documented in-file; unrelated to the mod version, which
   correctly stays **1.1.0** everywhere (gradle.properties, jar name, manifest, toml, CHANGELOG).
4. **`versionRange="[2101.1,)"` FTBQ floor** — same open-floor policy as 1.20.1 (`[2001.4,)`);
   parity, not a regression.
5. **Client caches not cleared on disconnect** (`ClientKnownIds` et al.) — pre-existing 1.20.1
   behavior, deliberately documented in the class javadoc; server pushes fresh sync on join.
   Wiring `ClientPlayerNetworkEvent.LoggingOut` is a future enhancement, not a port fix.
6. **`lenientOptionalFieldOf` sweep** — this is the port's *correct* response to DFU making
   `optionalFieldOf` strict; per-file counts match the 1.20.1 `optionalFieldOf` sites 1:1, and
   deliberately-strict `StrictCodecs.strictOptional` sites are untouched.
7. **Static `lastSituationScan`/`lastBankedRetryDay`** (`QuestProgressEvents.java:339,364`) —
   pre-existing identical code in 1.20.1; not a port regression.
8. **`SavedData.Factory` with `null` DataFixTypes** — explicitly tolerated by NeoForge's patched
   `DimensionDataStorage`; verified in 21.1.248 sources.

---

## 6. Parity confirmation inventory (what was verified clean)

- **Baseline integrity:** `e57b11c` = 1.20.1 v1.1.0 tree byte-identical (CRLF aside) to
  `c:/Projects/MCAQuests` @ `2d1757d`. Diff surface: 234 M / 7 A / 3 D / 1 R.
- **Tests:** 310/310 green on forced re-run. Test-suite weakening pass: 27 modified files all
  mechanical, 3 added (`ForgeCapsMigrationTest`, `TestConfigs`, `TestPaths`), **0 deleted**; no
  `@Disabled`/`assumeTrue`/swallowed exceptions/narrowed inputs; exactly one changed expected
  value (`mods.toml mandatory=false` → `neoforge.mods.toml type="optional"` — same invariant).
- **Networking:** all 17 payloads (7 C2S / 10 S2C) ported 1:1 on a `versioned("8")` non-optional
  registrar; encoder/decoder symmetry field-by-field incl. all nested wire records; NeoForge
  main-thread default reproduces every old `enqueueWork`; all send sites 1:1
  (`PacketDistributor.sendToPlayer`/`sendToServer`); null-sender guards preserved; zero
  `SimpleChannel` remnants.
- **Persistence & migration:** attachment `mcaquests:player_quests` serializable +
  `copyOnDeath()` = exact old clone semantics (verified against NeoForge `AttachmentInternals`);
  legacy key match exact; migration idempotent (`migrated_from_forge`), HIGHEST-priority login
  ordering before all consumers; NBT keys byte-identical (`active`/`history`/`titles`/`stats`);
  nested serializers byte-identical; **no ItemStacks in custom NBT** (1.21 item-format break
  moot); SavedData names `mcaquests_projects`/`mcaquests_situations` unchanged (world carry-over),
  1.21 save/Factory signatures correct, `setDirty` sites unchanged.
- **Core logic:** registration maps name-by-name identical (25 objective, 19 reward, all
  condition/project/trigger types); loaders still wired via `AddReloadListenerEvent`,
  log-and-skip error handling intact; no throw→tryParse drift; `dispatchMap` faithfully
  reproduces DFU6 assumeMap dispatch for every current decode path; EffectReward holder codec
  hard-fails unknown ids; LootTableReward params/EMPTY-fallback parity; StructureTarget
  `getStructureWithPieceAt` semantically identical; objective/condition/target logic
  byte-identical outside codec lines.
- **Events/config/MCA:** full Forge→NeoForge event mapping table verified OK (incl.
  `PlayerTickEvent.Post` = old END-phase frequency, `TradeWithVillagerEvent` exists,
  `ICancellableEvent` semantics); all 14 API-event post sites 1:1; config keys/sections/defaults/
  ranges/comments byte-identical, COMMON+CLIENT types unchanged (no serverconfig migration
  issue); commands byte-identical incl. permission levels and all 18 documented subcommands;
  **every MCA symbol javap-verified** against `mca-neoforge-7.7.22+1.21.1` (the Modrinth
  version-ID pin correctly selects the NeoForge jar); `pushVillageHearts` no-op proven correct
  (the 1.20.1 path it replaced was already dead); graceful-absence Throwable guards intact.
- **Client/mixins:** all three mixins' targets/fields/methods verified present in the MCA 1.21.1
  jar (`AbstractDynamicScreen.setLayout`, `InteractScreen.villager`, mojmap `Screen` fields);
  refmap correctly absent under MDG2; `[[mixins]]` toml wiring correct; keybind/HUD-layer/tick
  registration correct (above-all anchor, `hideGui` gate, `ClientTickEvent.Post` = END phase);
  screen/scroll/layout math byte-identical apart from the §1.2 structure bug; client/server
  separation preserved (client classes only in `playToClient` handler bodies).
- **FTBQ compat:** classload isolation intact (imports confined to `compat/ftbq`, tripwire tests
  not weakened); every base-class override compile-checked and re-verified against 2101.1.31
  bytecode (`writeData/readData(HolderLookup.Provider)`, `RegistryFriendlyByteBuf` net methods,
  `canSubmit` pre-gate, `checkOnLogin`); TaskTypes/RewardTypes registration signatures match;
  id/TeamData/`parseCodeString` semantics parity; SNBT enum leniency (580bea0) preserved;
  block_offer desugar untouched; ftbq_progress + known-ids editor sync (64 KB budget, twin-row
  commit mechanism re-verified against ftb-library 2101.1.35) fully ported; ftbq commands intact.
- **Resources:** all ~190 builtin datapack JSONs valid for 1.21.1 — every `minecraft:` item/
  entity/biome/structure/tag/loot-table id verified extant (no 1.21 renames apply); no `"nbt"`
  keys anywhere; lang parity en_us/pt_br 1582=1582 keys.
- **Build/meta:** MDG2 + neoforge 21.1.248 + parchment 2024.11.17 resolve and build; toml schema
  correct (`type=` syntax, loaderVersion `[1,)`, architectury dep correctly deleted); Java 21
  toolchain; FTB stack compileOnly-only; version policy 1.1.0 end-to-end; no Forge remnants in
  src (zero `net.minecraftforge` references); `run*/`/`bin/` untracked.

## 7. Residual risk

Unit tests cannot exercise client rendering, real logins, or FTBQ/MCA runtime behavior. After
applying §1 (and ideally §2.1), execute `PORTING_1.21.1_NEOFORGE.md` §16.2 dev-client smoke,
§16.3 dedicated-server smoke (also confirms the §4.2 classload property empirically), and §16.4
world-upgrade check with a real 1.20.1 world. §16.5's optional-integration matrix remains blocked
on the `MCAReputation_1.21.1` port (§3).
