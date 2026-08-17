# Porting MCA: Quests from 1.20.1 Forge to 1.21.1 NeoForge

**Instruction set for a coding agent. Target: a complete port with full feature parity.**

This document was researched against the actual `MCAQuests` source tree (v1.1.0, 329 main-source
files) and against the actual upstream dependencies as they exist for 1.21.1 NeoForge. Facts below
marked **[verified]** were confirmed directly from source or Maven metadata in August 2026. Treat
everything else as strong guidance that you must still confirm as you compile.

> **Erratum — the mod version is NOT bumped.** Where this document prescribes `2.0.0` (§0.3, Phase 2's
> `gradle.properties`, the protocol-`"8"` note in Phase 5, and the Phase 15 changelog step), the
> adopted policy is that **`mod_version` stays `1.1.0`**: the platform break is carried by the
> artifact's Minecraft/loader targeting and metadata, not by the mod's own version number. The
> shipped build is correct; only these four passages in this document are stale. Sibling ports
> (e.g. MCA: Reputation, which stays `0.1.0`) follow the same rule. The network protocol bump to
> `"8"` is unaffected and still applies — it is a wire-format version, unrelated to the mod version.

---

## 0. Mission, ground rules, and definition of done

### 0.1 What this mod is (so you can judge parity)

MCA: Quests is a server-authoritative, datapack-driven RPG quest system for Minecraft Comes Alive:
Reborn (MCA) villagers. Its major systems, all of which must survive the port intact:

- **Quests**: datapack JSON quest definitions (objectives, rewards, conditions, targets, chains,
  templates, failure specs), offered by MCA villagers, tracked per player.
- **Community projects**: village-scoped multi-phase group efforts with a contribution UI.
- **Situations** ("Living Village"): world-state-triggered village emergencies with offers,
  throttles, and resolutions.
- **Progression**: per-player village standing (reputation tiers), titles, a journal/archive.
- **Client UI**: a Quests button injected into MCA's villager menu (mixin), quest menu / log /
  journal / project screens, HUD tracker overlay, four toast types, three keybinds.
- **Networking**: 17 custom packets on the mod's own channel (protocol version "7").
- **Persistence**: a player "capability" (`PlayerQuestData`) plus several world `SavedData` stores.
- **Optional integrations**: FTB Quests (compile-only, reflection-free but classload-gated) and the
  sibling mod MCA: Reputation (compile-only against sibling repo class output).
- **Public API**: custom events posted on the game event bus (`QuestCompletedEvent`, etc.),
  `McaQuestsApi`, dialogue hooks.
- **Unit tests**: ~35 JUnit test classes for the loader-independent quest engine.

### 0.2 Ground rules

1. **Do not change behavior.** This is a port, not a refactor. Where an API forces a semantic choice
   (e.g. tick event phases, payload threading), pick the option that reproduces 1.20.1 behavior and
   leave a `// PORT:` comment explaining the mapping.
2. **Keep the architecture.** All MCA access stays isolated in `compat/McaCompat.java`; all FTBQ
   classes stay isolated in `compat/ftbq/` (enforced by `NoFtbqClassloadTest`); the reputation
   backend switch stays in `ReputationBridge`. These invariants are load-bearing and tested.
3. **Work in phases, compile-gate each phase.** The phases below are ordered so that the build is
   broken for the shortest possible time. After Phase 5 the project must compile; after Phase 9 it
   must run.
4. **Verify against primary sources, not this document alone.** Clone the MCA upstream for
   reference — its default branch **is** the 1.21.1 branch **[verified]**:
   `git clone --depth 1 https://github.com/Luke100000/minecraft-comes-alive /tmp/mca-upstream`
   When a McaCompat call fails to compile, find the replacement in that tree before improvising.
5. **Never bundle optional deps.** FTBQ and MCA: Reputation remain `compileOnly`. Nothing outside
   `compat/ftbq` may import `dev.ftb.mods.*`.
6. **Commit per phase** with messages like `port(phase-3): vanilla 1.21.1 mechanical migration`.

### 0.3 Definition of done

- `./gradlew build` succeeds on Java 21 (jar + all unit tests green).
- `./gradlew runClient` reaches the title screen with MCA + MCA: Quests loaded; `runServer` boots
  and a client can join.
- Every item in the **Phase 14 parity matrix** passes.
- No references to `net.minecraftforge.*` anywhere in `src/`.
- `mods.toml` is gone; `META-INF/neoforge.mods.toml` is correct.
- Mod version bumped (suggest `2.0.0` to signal the platform break) and network protocol bumped to
  `"8"` (the payload registrar version — see Phase 5).

---

## 1. Target versions (all confirmed available, August 2026)

| Component | 1.20.1 Forge (current) | 1.21.1 NeoForge (target) | Notes |
|---|---|---|---|
| Java | 17 | **21** | Hard requirement of MC 1.20.5+ |
| Gradle wrapper | (FG6-era) | **8.12+** | Update wrapper first |
| Build plugin | ForgeGradle `[6.0,6.2)` + MixinGradle 0.7 | **ModDevGradle `net.neoforged.moddev` 2.0.141** (or newer 2.0.x) | Same plugin/version MCA upstream uses **[verified]** |
| Loader | Forge 47.4.10 | **NeoForge 21.1.x** — latest is `21.1.248` **[verified]**; MCA builds against `21.1.234` | Range `[21.1.0,)` is fine |
| Mappings | `official` 1.20.1 | Mojmap (MDG default), optionally + Parchment `1.21.1:2024.11.17` | No SRG, no refmaps, no reobf anywhere |
| MCA Reborn | `maven.modrinth:minecraft-comes-alive-reborn:7.6.20+1.20.1` via `fg.deobf` | **`maven.modrinth:minecraft-comes-alive-reborn:7.7.22+1.21.1`** (NeoForge file) **[verified]** as plain `implementation` | 7.7.23 exists only as alpha. Mod id is still `mca` **[verified]** |
| MCA packages | `forge.net.mca.*` (+ `net.mca` common) | **`net.conczin.mca.*`** — single common package, no loader wrapper **[verified]** | Global rename required |
| Architectury | `dev.architectury:architectury-forge:9.2.14` (MCA hard dep) | **REMOVED** — MCA 1.21.1 no longer uses Architectury **[verified]** | Delete the dependency and the `mods.toml` entry |
| FTB Quests | `dev.ftb.mods:ftb-quests-forge:2001.4.22` | **`dev.ftb.mods:ftb-quests-neoforge:2101.1.31`** **[verified]** | Note artifact rename `-forge` → `-neoforge` |
| FTB Library | `ftb-library-forge:2001.2.9` | **`ftb-library-neoforge:2101.1.34`** **[verified]** | |
| FTB Teams | `ftb-teams-forge:2001.3.0` | **`ftb-teams-neoforge:2101.1.10`** **[verified]** | |
| Mixin | 0.8.5 + annotation processor + refmap | NeoForge's bundled Mixin; **no AP, no refmap** | Config file keeps working |
| JUnit | 5.10.2 | 5.10.2 (unchanged) | Run via MDG `unitTest` support |
| MCA: Reputation | sibling repo classes, `compileOnly` | Must be **ported first or stubbed** — see Phase 11 | |

---

## 2. Phase 0 — Preparation and inventory

1. Create branch `port/1.21.1-neoforge` from the current default branch.
2. Clone the MCA upstream reference tree (see §0.2 rule 4). You will consult it constantly in
   Phases 7–8.
3. Record the baseline inventory (paste output into the PR description):
   ```bash
   grep -rhoE 'import net\.minecraftforge\.[A-Za-z0-9_.]+' src/main/java | sort | uniq -c | sort -rn
   grep -rn 'new ResourceLocation(' src/main/java | wc -l     # expect ~79
   grep -rhoE 'import forge\.net\.mca\.[A-Za-z0-9_.]+' src/main/java | sort -u   # expect 12 classes
   ```
4. Update the Gradle wrapper: `./gradlew wrapper --gradle-version 8.12` (run on the old build once,
   or edit `gradle/wrapper/gradle-wrapper.properties` directly).

---

## 3. Phase 1 — Build system: ForgeGradle 6 → ModDevGradle 2

Replace `build.gradle`, `settings.gradle`, and the tool-related parts of `gradle.properties`
wholesale. Delete every trace of ForgeGradle, MixinGradle, `fg.deobf`, SRG remapping, and the
refmap.

### 3.1 `settings.gradle`

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = 'https://maven.neoforged.net/releases' }
    }
}
plugins {
    // Provisions JDK 21 automatically on any machine.
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}
rootProject.name = 'mcaquests'
```

### 3.2 `gradle.properties` (new values; keep mod metadata keys as-is)

```properties
org.gradle.jvmargs=-Xmx4G
org.gradle.daemon=false

minecraft_version=1.21.1
minecraft_version_range=[1.21.1,1.22)
neoforge_version=21.1.248
neoforge_version_range=[21.1.0,)
loader_version_range=[1,)

# Optional but recommended: Parchment parameter names (matches MCA upstream).
parchment_minecraft=1.21.1
parchment_version=2024.11.17

## Dependencies
# MCA Reborn 1.21.1 NeoForge build via Modrinth Maven. NOT obfuscated; no deobf pipeline exists
# or is needed under NeoForge (mojmap at runtime).
mca_version=7.7.22+1.21.1
# architectury_version — DELETED. MCA 1.21.1 dropped Architectury.

mod_version=2.0.0

## FTB Quests optional-compat (compileOnly; never shipped). Artifacts renamed *-neoforge.
ftb_quests_version=2101.1.31
ftb_library_version=2101.1.34
ftb_teams_version=2101.1.10
enableFtbqInDev=false
```

### 3.3 `build.gradle`

```groovy
plugins {
    id 'idea'
    id 'eclipse'
    id 'java-library'
    id 'net.neoforged.moddev' version '2.0.141'
}

version = mod_version
group = mod_group_id
base { archivesName = mod_id }

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

neoForge {
    version = project.neoforge_version

    parchment {
        minecraftVersion = project.parchment_minecraft
        mappingsVersion = project.parchment_version
    }

    runs {
        client {
            client()
            gameDirectory = project.file('run')
        }
        server {
            server()
            gameDirectory = project.file('run-server')
            programArgument '--nogui'
        }
        data {
            data()
            gameDirectory = project.file('run-data')
            programArguments.addAll '--mod', project.mod_id, '--all',
                    '--output', file('src/generated/resources/').getAbsolutePath(),
                    '--existing', file('src/main/resources/').getAbsolutePath()
        }
        configureEach {
            systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        "${mod_id}" { sourceSet sourceSets.main }
    }

    // JUnit tests that need Minecraft classes on the classpath (the whole test suite does —
    // TestBootstrap touches SharedConstants/Bootstrap).
    unitTest {
        enable()
        testedMod = mods."${mod_id}"
    }
}

sourceSets.main.resources { srcDir 'src/generated/resources' }

repositories {
    exclusiveContent {
        forRepository {
            maven { name = 'Modrinth'; url = 'https://api.modrinth.com/maven' }
        }
        filter { includeGroup 'maven.modrinth' }
    }
    maven {
        name = 'FTB'
        url = 'https://maven.ftb.dev/releases'
        content { includeGroup 'dev.ftb.mods' }
    }
}

dependencies {
    // MCA Reborn — plain implementation. The NeoForge jar is mojmap; it also LOADS AS A MOD in
    // dev runs now (its 1.21.1 mixins are mojmap too), so real MCA behaviour is finally testable
    // under runClient/runServer. Delete the old "MCA does not load in dev" caveats.
    implementation "maven.modrinth:minecraft-comes-alive-reborn:${mca_version}"

    // MCA: Reputation — optional integration, compile-only, from the sibling repo's ported class
    // output. See Phase 11 before touching this.
    def mcaReputationClasses = file("${projectDir}/../MCAReputation/build/classes/java/main")
    if (mcaReputationClasses.exists()) {
        compileOnly files(mcaReputationClasses)
    } else {
        logger.warn("MCA: Reputation classes not found; build MCAReputation (ported to 1.21.1 NeoForge) first.")
    }

    // FTB Quests optional integration — compile-time only. NEVER shipped, NEVER 'implementation'.
    compileOnly "dev.ftb.mods:ftb-quests-neoforge:${ftb_quests_version}"
    compileOnly "dev.ftb.mods:ftb-library-neoforge:${ftb_library_version}"
    compileOnly "dev.ftb.mods:ftb-teams-neoforge:${ftb_teams_version}"
    if (project.hasProperty('enableFtbqInDev') && project.enableFtbqInDev.toBoolean()) {
        runtimeOnly "dev.ftb.mods:ftb-quests-neoforge:${ftb_quests_version}"
        runtimeOnly "dev.ftb.mods:ftb-library-neoforge:${ftb_library_version}"
        runtimeOnly "dev.ftb.mods:ftb-teams-neoforge:${ftb_teams_version}"
    }

    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.2'
    testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.10.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test { useJUnitPlatform() }

processResources {
    var replaceProperties = [
            minecraft_version      : minecraft_version,
            minecraft_version_range: minecraft_version_range,
            neoforge_version       : neoforge_version,
            neoforge_version_range : neoforge_version_range,
            loader_version_range   : loader_version_range,
            mod_id                 : mod_id,
            mod_name               : mod_name,
            mod_license            : mod_license,
            mod_version            : mod_version,
            mod_authors            : mod_authors,
            mod_description        : mod_description,
    ]
    inputs.properties replaceProperties
    filesMatching(['META-INF/neoforge.mods.toml']) { expand replaceProperties }
}
```

### 3.4 Deletions

- `buildscript` block, `org.spongepowered.mixin` plugin, `mixin { ... }` block, the
  `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'` dependency.
- The `refmap` entry in `mcaquests.mixins.json` (Phase 8) and any generated
  `mcaquests.refmap.json` references.
- All `fg.deobf(...)` wrappers, the `forRepositories(fg.repository)` line, all
  `mixin.env.remapRefMap` / `refMapRemappingFile` run properties, `copyIdeResources`,
  `forge.logging.*` properties.
- The stale JDK-17 commentary in `gradle.properties`.

**Gate:** `./gradlew tasks` runs without error (compilation will still fail — that's expected until
Phase 5).

---

## 4. Phase 2 — Mod metadata: `mods.toml` → `neoforge.mods.toml`

Rename `src/main/resources/META-INF/mods.toml` → `src/main/resources/META-INF/neoforge.mods.toml`
and rewrite:

```toml
modLoader = "javafml"
loaderVersion = "${loader_version_range}"     # NeoForge language loader: use "[1,)"
license = "${mod_license}"

[[mods]]
modId = "${mod_id}"
version = "${mod_version}"
displayName = "MCA: Quests"
authors = "otectus"
description = '''${mod_description}'''

[[mixins]]
config = "mcaquests.mixins.json"

[[dependencies.${mod_id}]]
modId = "neoforge"
type = "required"
versionRange = "${neoforge_version_range}"
ordering = "NONE"
side = "BOTH"

[[dependencies.${mod_id}]]
modId = "minecraft"
type = "required"
versionRange = "${minecraft_version_range}"
ordering = "NONE"
side = "BOTH"

[[dependencies.${mod_id}]]
modId = "mca"
type = "required"
versionRange = "[7.7,8)"
ordering = "AFTER"
side = "BOTH"

# NOTE: the architectury dependency block is DELETED — MCA 1.21.1 does not use it.

[[dependencies.${mod_id}]]
modId = "ftbquests"
type = "optional"
versionRange = "[2101.1,)"
ordering = "AFTER"
side = "BOTH"

[[dependencies.${mod_id}]]
modId = "mcareputation"
type = "optional"
versionRange = "[0.2,)"        # the ported sibling's new version floor
ordering = "AFTER"
side = "BOTH"
```

Syntax changes to remember: `mandatory=true/false` → `type="required"/"optional"`; the `forge`
dependency id becomes `neoforge`. Keep the explanatory comments from the old file — they document
load-order invariants (MCA registries before us, FTBQ TaskTypes before our constructor).

---

## 5. Phase 3 — Mechanical vanilla migration (1.20.1 → 1.21.1)

Do this as a project-wide sweep. These are pure find-and-adapt changes with no design decisions.

### 5.1 `ResourceLocation` constructors (~79 sites) **[verified count]**

The constructor is private in 1.21. Replace:

- `new ResourceLocation(MOD_ID, path)` → `ResourceLocation.fromNamespaceAndPath(MOD_ID, path)`
- `new ResourceLocation(stringWithColon)` → `ResourceLocation.parse(s)`
- `new ResourceLocation("textures/gui/toasts.png")` (vanilla ns, e.g. `QuestToast`, `ProjectToast`,
  `SituationToast`, `ReputationTierToast`) → `ResourceLocation.withDefaultNamespace(path)`
- In datapack loaders/codecs, `ResourceLocation.tryParse` still exists and is unchanged.

### 5.2 `SavedData` (all world stores)

Affected **[verified]**: `SituationSavedData` (+ its `get()`), and the stores referenced from
`ReputationService` / `QuestReputation` / `SituationThrottle` / `DynamicOfferSource` /
`JournalService` / `QuestManager` / `ProjectManager` — sweep for `extends SavedData` and
`computeIfAbsent`.

1.20.1 pattern:
```java
overworld.getDataStorage().computeIfAbsent(SituationSavedData::load, SituationSavedData::new, DATA_NAME);
public CompoundTag save(CompoundTag tag) { ... }
```

1.21.1 pattern (this exact shape is what MCA upstream uses **[verified]**):
```java
overworld.getDataStorage().computeIfAbsent(
        new SavedData.Factory<>(SituationSavedData::new,
                (tag, provider) -> SituationSavedData.load(tag),
                null),                       // DataFixTypes: null is acceptable
        DATA_NAME);
@Override
public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) { ... }
```

**Keep every `DATA_NAME` string identical** (`mcaquests_situations`, etc.). The `.dat` files are
loader-independent; existing worlds keep their situations/projects/reputation through the upgrade.

### 5.3 Components on the network

`FriendlyByteBuf.writeComponent/readComponent` no longer exist. Affected **[verified]**:
`QuestLogEntry` (4 component fields incl. a collection), `ProjectPhaseToastS2CPacket`, and every
other packet/DTO that carried `Component`s — sweep for `writeComponent|readComponent`.

Replacement (requires `RegistryFriendlyByteBuf`, which all payloads get in Phase 5):

```java
ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, component);
Component c = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
// collections:
buf.writeCollection(list, (b, comp) -> ComponentSerialization.TRUSTED_STREAM_CODEC.encode((RegistryFriendlyByteBuf) b, comp));
```

Change the affected `encode`/`decode` signatures from `FriendlyByteBuf` to
`RegistryFriendlyByteBuf` (it extends `FriendlyByteBuf`, so packets that only carry primitives can
keep `FriendlyByteBuf` signatures untouched).

### 5.4 Loot tables are registry-backed in 1.21

`LootTableReward.grant()` **[verified]** calls `level.getServer().getLootData().getLootTable(id)`.
`LootDataManager` is gone; loot tables live in the reloadable registries:

```java
LootTable table = level.getServer().reloadableRegistries()
        .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootTable));
```

The `ResourceLocation lootTable` record field and its codec stay as-is (datapack format
unchanged); `LootParams.Builder` / `LootContextParams` / `LootContextParamSets.ADVANCEMENT_REWARD`
are all still present in 1.21.1. Sweep for any other `getLootData()` call.

### 5.5 Things that do **not** change (don't invent work)

- `GuiGraphics` drawing calls used by the screens/HUD (`drawString`, `blit`, `fill`) are compatible
  in 1.21.1. (The `blit`-needs-RenderType change lands in 1.21.2+ — not your problem.)
- The `Toast` interface (`Visibility render(GuiGraphics, ToastComponent, long)`) and
  `Minecraft.getInstance().getToasts().addToast(...)` are unchanged in 1.21.1. (The ToastManager
  rework is 1.21.2+.) All four toast classes port with only the `ResourceLocation` fix.
- `CompoundTag` getters still return values directly (the `Optional`-returning NBT rework is
  1.21.5+). `PlayerQuestData.save/load`, `SituationInstance`, `PlayerTitles` NBT code is untouched.
- `BuiltInRegistries` lookups (8 files use them **[verified]**; the mod never used
  `ForgeRegistries` **[verified]**) are unchanged.
- No `ItemStack` is serialized to NBT or network anywhere in the mod **[verified]** — the
  DataComponents migration does not apply. If you find a counterexample, use
  `ItemStack.CODEC`/`ItemStack.STREAM_CODEC` with registry context.
- Brigadier command registration (`RegisterCommandsEvent`) — same shape.
- `KeyMapping`, `InputConstants` — unchanged.

---

## 6. Phase 4 — Forge → NeoForge API sweep

Global import/idiom mapping. Apply mechanically; the table covers every Forge import present in the
codebase **[verified against the actual import inventory]**.

| Forge (1.20.1) | NeoForge (21.1) |
|---|---|
| `net.minecraftforge.fml.common.Mod` | `net.neoforged.fml.common.Mod` |
| `@Mod.EventBusSubscriber(modid=…, bus=Bus.MOD, value=Dist.CLIENT)` | `net.neoforged.fml.common.EventBusSubscriber` — top-level annotation, same attributes; `Bus.FORGE` → `Bus.GAME` (or omit `bus`; NeoForge infers it from the event type) |
| `net.minecraftforge.eventbus.api.SubscribeEvent` | `net.neoforged.bus.api.SubscribeEvent` |
| `net.minecraftforge.eventbus.api.Event` (custom API events) | `net.neoforged.bus.api.Event`; cancellable events implement `net.neoforged.bus.api.ICancellableEvent` instead of `@Cancelable` |
| `net.minecraftforge.eventbus.api.IEventBus` | `net.neoforged.bus.api.IEventBus` |
| `MinecraftForge.EVENT_BUS` (7 files: TitleService, SituationManager, QuestManager, ProjectManager, FtbqEventBridge, QuestsReputationEvents, …) | `net.neoforged.neoforge.common.NeoForge.EVENT_BUS` |
| `FMLJavaModLoadingContext.get().getModEventBus()` | Deleted — the `@Mod` class constructor may take `(IEventBus modEventBus, ModContainer modContainer)` parameters |
| `ModLoadingContext.get().registerConfig(type, spec)` | `modContainer.registerConfig(type, spec)` (from the ctor param) |
| `net.minecraftforge.common.ForgeConfigSpec` | `net.neoforged.neoforge.common.ModConfigSpec` (identical builder API; `McaQuestsConfig` is a rename-only edit) |
| `net.minecraftforge.fml.config.ModConfig` | `net.neoforged.fml.config.ModConfig` |
| `net.minecraftforge.fml.ModList` | `net.neoforged.fml.ModList` |
| `net.minecraftforge.fml.DistExecutor` (10 files, all packet handlers) | **Removed.** Not replaced 1:1 — the Phase 5 payload API solves sidedness structurally |
| `net.minecraftforge.api.distmarker.Dist` | `net.neoforged.api.distmarker.Dist` |
| `net.minecraftforge.fml.loading.FMLPaths` | `net.neoforged.fml.loading.FMLPaths` |
| `net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent` | `net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent` |
| `net.minecraftforge.event.AddReloadListenerEvent` (5 files: QuestDataLoader, SituationDataLoader, ProjectDataLoader, ReputationTierLoader, TitleLoader area) | `net.neoforged.neoforge.event.AddReloadListenerEvent` — same shape; bonus: `event.getRegistryAccess()` exists if a loader ever needs registry ops |
| `net.minecraftforge.event.RegisterCommandsEvent` | `net.neoforged.neoforge.event.RegisterCommandsEvent` |
| `net.minecraftforge.event.TickEvent.PlayerTickEvent` + `phase == Phase.END` check (QuestProgressEvents) | `net.neoforged.neoforge.event.tick.PlayerTickEvent.Post` — the phase field is gone; Pre/Post are separate events. Delete the phase guard |
| `TickEvent.ClientTickEvent` + `Phase.END` (QuestClientInput) | `net.neoforged.neoforge.client.event.ClientTickEvent.Post` |
| `PlayerEvent.PlayerLoggedInEvent`, `PlayerEvent.Clone`, other `PlayerEvent`s | `net.neoforged.neoforge.event.entity.player.PlayerEvent.*` — same names |
| `LivingDeathEvent` (3 uses) | `net.neoforged.neoforge.event.entity.living.LivingDeathEvent` |
| `BabyEntitySpawnEvent`, `AnimalTameEvent` | `net.neoforged.neoforge.event.entity.living.*` — same names |
| `PlayerInteractEvent`, `TradeWithVillagerEvent`, `ItemFishedEvent` | `net.neoforged.neoforge.event.entity.player.*` — same names |
| `BlockEvent` (break/place) | `net.neoforged.neoforge.event.level.BlockEvent` — same nested names |
| `SleepFinishedTimeEvent` | `net.neoforged.neoforge.event.level.SleepFinishedTimeEvent` |
| `ServerStartedEvent`, `ServerStoppingEvent` | `net.neoforged.neoforge.event.server.*` |
| `net.minecraftforge.server.ServerLifecycleHooks` (FtbqEventBridge) | `net.neoforged.neoforge.server.ServerLifecycleHooks` |
| `net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer` (3 uses: `ItemReward`, `LootTableReward`, `CurrencyReward`) | `net.neoforged.neoforge.items.ItemHandlerHelper` — same method |
| `net.minecraftforge.client.event.RegisterKeyMappingsEvent` | `net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent` |
| `RegisterGuiOverlaysEvent` / `IGuiOverlay` / `ForgeGui` | See Phase 9 — `RegisterGuiLayersEvent` + `LayeredDraw.Layer` |
| `NetworkRegistry` / `SimpleChannel` / `NetworkEvent` / `PacketDistributor` | See Phase 5 — full networking rewrite |
| `AttachCapabilitiesEvent` / `Capability` / `CapabilityToken` / `CapabilityManager` / `ICapabilitySerializable` / `LazyOptional` / `RegisterCapabilitiesEvent` | See Phase 6 — data attachments |

**Custom API events** (`api/event/QuestEvent.java` hierarchy, `TitleGrantedEvent`,
`SituationResolvedEvent`, `ProjectEvent.*`, `ReputationTierReachedEvent`): change the superclass
import to `net.neoforged.bus.api.Event`, post on `NeoForge.EVENT_BUS`. Their javadoc references to
"the Forge bus" should say "the NeoForge game bus". This is public API for addon mods — document
the new package in `CHANGELOG.md`.

**Main mod class** (`McaQuests.java`) becomes:

```java
@Mod(McaQuests.MOD_ID)
public final class McaQuests {
    public McaQuests(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, McaQuestsConfig.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, McaQuestsConfig.CLIENT_SPEC);
        if (ModList.get().isLoaded("ftbquests")) { /* unchanged FtbqBootstrap.init() guard */ }
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(QuestNetwork::onRegisterPayloads);   // Phase 5
        QuestAttachments.REGISTER.register(modBus);             // Phase 6
    }
    private void onCommonSetup(FMLCommonSetupEvent event) {
        // bootstrap() calls unchanged; QuestNetwork.register() is GONE (moved to the event);
        // ReputationBridge.init() stays in enqueueWork.
    }
}
```

**Gate:** after Phases 3–4 only networking, capability, mixin, and MCA-compat files should still
fail to compile.

---

## 7. Phase 5 — Networking rewrite: SimpleChannel → payloads

This is the largest single change. The old system **[verified]**: one `SimpleChannel` named
`mcaquests:main`, protocol string `"7"`, 17 packets registered in `QuestNetwork.register()`, each
with static `encode`/`decode`/`handle`, client-side handling via
`DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)` inside `ctx.enqueueWork`.

### 7.1 Packet inventory (direction matters — port each exactly)

C2S: `OpenQuestMenuC2SPacket`, `QuestDecisionC2SPacket`, `QuestTurnInC2SPacket`,
`QuestAbandonC2SPacket`, `QuestAbandonFromLogC2SPacket`, `ProjectContributeC2SPacket`,
`RequestJournalC2SPacket`.

S2C: `QuestMenuDataS2CPacket`, `QuestLogSyncS2CPacket`, `QuestReadyToastS2CPacket`,
`ProjectMenuDataS2CPacket`, `ProjectLogSyncS2CPacket`, `ProjectPhaseToastS2CPacket`,
`ReputationTierToastS2CPacket`, `JournalSyncS2CPacket`, `SituationToastS2CPacket`,
`FtbqEditorIdsS2CPacket`.

### 7.2 New shape

Each packet becomes a `CustomPacketPayload` (records work well, but you may keep the existing
classes and just implement the interface — smaller diff, preferred):

```java
public final class QuestTurnInC2SPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<QuestTurnInC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, "quest_turn_in"));
    // Keep the existing encode(...)/decode(...) bodies; wrap them:
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestTurnInC2SPacket> STREAM_CODEC =
            CustomPacketPayload.codec(QuestTurnInC2SPacket::encode, QuestTurnInC2SPacket::decode);
    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```

Payload ids: derive from the class purpose (`quest_turn_in`, `quest_menu_data`, …). They replace
the old int discriminators; ordering no longer matters.

### 7.3 Registration (`QuestNetwork`)

```java
public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
    // "8" replaces the old SimpleChannel protocol "7": clients without the mod, or with a
    // different registrar version, cannot join — same hard-mismatch behaviour as before.
    PayloadRegistrar registrar = event.registrar(McaQuests.MOD_ID).versioned("8");
    registrar.playToServer(QuestTurnInC2SPacket.TYPE, QuestTurnInC2SPacket.STREAM_CODEC, QuestTurnInC2SPacket::handle);
    registrar.playToClient(QuestMenuDataS2CPacket.TYPE, QuestMenuDataS2CPacket.STREAM_CODEC, ClientPayloadHandlers::handleQuestMenuData);
    // ... all 17, matching the directions in §7.1
}
```

Handler signature: `void handle(T payload, IPayloadContext context)`.
**Threading semantics [important parity point]:** the old code wrapped all work in
`ctx.enqueueWork(...)` (main thread). NeoForge payload handlers run on the main thread by default —
so the handler body is the old lambda body minus the `enqueueWork` wrapper and minus
`ctx.setPacketHandled(true)` (gone entirely).

Server handlers: `ServerPlayer player = (ServerPlayer) context.player();` replaces
`ctx.getSender()`.

### 7.4 Client sidedness without DistExecutor

Create `dev.otectus.mcaquests.network.ClientPayloadHandlers` — a class that is **only referenced
from the registration lambdas of S2C packets** and delegates to the existing
`QuestClientHandlers` / `ClientJournalData` / `ClientProjectData` / `ClientQuestData` /
`ClientKnownIds` entry points. Because S2C handlers only ever execute on the client, the
`DistExecutor.unsafeRunWhenOn(Dist.CLIENT, …)` wrappers in the 10 affected packet classes are
deleted, not translated. If you want extra safety on dedicated servers, register S2C payloads with
handlers that route through `context.enqueueWork` only client-side — but the standard NeoForge
pattern (client-only handler class referenced via method-ref from registration) is sufficient
because registration lambdas are not eagerly classloaded on the server. Verify on the dedicated
server smoke test (Phase 14) that no `ClassNotFoundError`/classload of client classes occurs.

### 7.5 Sending

| Old | New |
|---|---|
| `CHANNEL.sendToServer(pkt)` (mixin button, screens, `QuestClientInput`) | `PacketDistributor.sendToServer(pkt)` |
| `CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), pkt)` | `PacketDistributor.sendToPlayer(player, pkt)` |
| any `PacketDistributor.ALL` use | `PacketDistributor.sendToAllPlayers(pkt)` |

Sweep for `QuestNetwork.CHANNEL` — every send site must change. **[verified]** the 11 files:
`AbstractDynamicScreenMixin`, `SituationManager`, `JournalService`, `QuestManager`,
`FtbqEditorIdsSync`, `ProjectManager`, `LegacyReputationBackend`, `JournalScreen`,
`QuestLogScreen`, `ProjectMenuScreen`, `QuestMenuScreen`.

### 7.6 Protocol-bump comment

Preserve the long protocol-history comment from `QuestNetwork` (it documents why mismatches must
hard-fail) and append the port note: `"8" = 2.0.0: 1.21.1/NeoForge payload rewrite`.

**Gate:** `./gradlew compileJava` succeeds except for capability/mixin/MCA files.

---

## 8. Phase 6 — Player data: capability → data attachment

Old system **[verified]**: `QuestCapabilities` (CapabilityToken + `RegisterCapabilitiesEvent.register(PlayerQuestData.class)`), `PlayerQuestDataProvider implements ICapabilitySerializable<CompoundTag>`, attach via `AttachCapabilitiesEvent<Entity>` in `QuestCapabilityEvents`, death persistence via `PlayerEvent.Clone` copy.

### 8.1 New system

Delete `PlayerQuestDataProvider` entirely. Replace `QuestCapabilities` with:

```java
public final class QuestAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, McaQuests.MOD_ID);

    public static final Supplier<AttachmentType<PlayerQuestData>> PLAYER_QUESTS =
            REGISTER.register("player_quests", () -> AttachmentType
                    .serializable(PlayerQuestData::new)   // requires INBTSerializable
                    .copyOnDeath()
                    .build());

    public static Optional<PlayerQuestData> get(Player player) {
        return Optional.of(player.getData(PLAYER_QUESTS));   // keep the Optional-shaped API so
    }                                                        // ~30 call sites don't change
}
```

Make `PlayerQuestData implements INBTSerializable<CompoundTag>` — in 1.21.1 the interface methods
are `CompoundTag serializeNBT(HolderLookup.Provider)` / `void deserializeNBT(HolderLookup.Provider, CompoundTag)`;
delegate to the existing `save()`/`load(tag)` methods unchanged.

Keep the `QuestCapabilities.get(player)` call-site name if you prefer a smaller diff (rename the
class or add a delegating method); either way every `.resolve()`/`LazyOptional` idiom collapses to
a plain value.

### 8.2 `QuestCapabilityEvents`

- `onAttachCapabilities` — **delete** (attachments need no attach step; `getData` creates lazily).
- `onClone` — **delete**; `.copyOnDeath()` replaces it. **[verified]** the old handler copies in
  *all* clone cases (no `isWasDeath` filter — it revives caps, copies, re-invalidates), and
  NeoForge attachments survive non-death clones automatically, so `.copyOnDeath()` yields
  identical behavior across death, dimension change, and end-return.
- Keep any login-time sync in `QuestProgressEvents.onPlayerLogin` untouched — S2C sync packets are
  still the mechanism that populates the client.

### 8.3 Save-data migration (required for "full parity" on upgraded worlds)

Forge stored the data at `playerdata/<uuid>.dat → ForgeCaps → "mcaquests:player_quests"`. NeoForge
reads attachments from the `neoforge:attachments` tag and silently drops `ForgeCaps`. Without a
shim, every player's quest log/titles/history resets on world upgrade.

Implement a one-shot import in the `PlayerEvent.PlayerLoggedInEvent` handler (server side):

1. If the player's attachment is non-empty, do nothing.
2. Otherwise read the raw file `server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat")`
   with `NbtIo.readCompressed`, look up `ForgeCaps` → `mcaquests:player_quests`, and if present,
   `data.load(oldTag)` + mark a `migrated_from_forge` flag inside the attachment NBT.
3. Wrap the whole thing in try/catch-log — a corrupt file must not block login.
4. This works because the on-disk file keeps `ForgeCaps` until NeoForge first rewrites it; login
   ordering (read-file-after-load) is safe since we read the disk copy, which is only rewritten on
   the next save. Add a unit-style test with a canned 1.20.1 `ForgeCaps` NBT blob.

Do the same review for `PlayerTitles` if it rides inside `PlayerQuestData` (it does — one store).

---

## 9. Phase 7 — MCA compat layer (`McaCompat` + friends)

MCA 1.21.1 facts **[all verified against the upstream 1.21.1 tree]**:

- Packages: `forge.net.mca.*` and `net.mca.*` → **`net.conczin.mca.*`**. Every one of the 12
  imported classes exists at the same relative path:
  `entity.VillagerEntityMCA`, `entity.VillagerLike`, `entity.ai.Memories`, `entity.ai.MoveState`,
  `entity.ai.relationship.AgeState`, `entity.ai.relationship.EntityRelationship`,
  `server.world.data.FamilyTree`, `server.world.data.FamilyTreeNode`,
  `server.world.data.PlayerSaveData`, `server.world.data.Village`,
  `server.world.data.VillageManager`, `client.gui.InteractScreen` (+ `client.gui.AbstractDynamicScreen`).
- Mod id is still `mca`; version string `7.7.22+1.21.1`.
- Surviving APIs (spot-verified): `VillagerBrain.getMemoriesForPlayer(player)`,
  `Memories.getHearts()/setHearts/modHearts`, `VillagerBrain.setMoveState/getMoveState`,
  `MoveState.FOLLOW/MOVE`, `VillagerLike.getAgeState()` + `AgeState.ADULT`,
  `VillagerLike.asEntity()`, `EntityRelationship.of(entity)` (still `Optional`),
  `VillagerEntityMCA.getResidency()` → `Residency.getHomeVillage()` (`Optional<Village>`),
  `VillageManager.get(ServerLevel)`, `getOrEmpty(int)`, `findNearestVillage(BlockPos,int)`,
  `Village.getId/getName/getCenter/getResidentsUUIDs/isWithinBorder/pushHearts(UUID,int)`,
  `FamilyTree.getOrEmpty`, `FamilyTreeNode.streamParents`, `PlayerSaveData.getFamilyEntry`.
- **Known drift you must fix**:
  - `Village.hasResident(UUID)` is **gone**. Replace the single call site (McaCompat ~line 605)
    with `v.getResidentsUUIDs().anyMatch(uuid::equals)`.
  - `Memories`-adjacent `eraseMemory` calls in McaCompat are on the **vanilla** `Brain`
    (`MemoryModuleType.WALK_TARGET`) — those are vanilla API and fine.
  - MCA 1.21.1 added a native per-player `Village` reputation map. **Do not** silently switch
    `ReputationBridge` to it — the mod's own standing store remains canonical (spec 29.1). Note it
    in `CHANGELOG.md` as future work.
- MCA's own `SavedData` classes now take `(CompoundTag, HolderLookup.Provider)` loaders — relevant
  only if compat code constructs them (it shouldn't; it goes through `get()` accessors).

### Procedure

1. Project-wide replace `forge.net.mca.` → `net.conczin.mca.` (12 imports + the two mixin
   annotations + any fully-qualified references).
2. Compile. For each remaining error inside `compat/`, locate the symbol in `/tmp/mca-upstream`
   (`grep -rn 'methodName' common/src/main/java`) and adapt **inside McaCompat only** — its whole
   purpose is to absorb this drift; callers must not change.
3. Re-check every `try/catch(Throwable)` fallback in McaCompat still logs-and-defaults rather than
   throwing — that defensive contract is what makes MCA version drift survivable, keep it intact.
4. `McaVillagerRef`, `McaVillagerSnapshot`, `IncidentSelector`, `LegacyReputationBackend` follow
   the same treatment.

---

## 10. Phase 8 — Mixins

Files **[verified]**: `AbstractDynamicScreenMixin` (injects the Quests button into MCA's villager
menu at `AbstractDynamicScreen#setLayout(String)` TAIL), `InteractScreenAccessor` (gets the
villager), `ScreenAccessor` (accessors for `renderables`/`children`/`narratables`).

MCA 1.21.1 facts **[verified]**: `AbstractDynamicScreen.setLayout(String guiKey)` still exists with
the same signature and still builds the `"main"` layout; `InteractScreen` still exposes a
`VillagerLike<?> villager` field; the `gui.button.interact` / `gui.button.talk` translation keys
still exist (the button-position heuristics keep working); `VillagerLike.asEntity()` survives.

Changes:

1. `mcaquests.mixins.json`: `"compatibilityLevel": "JAVA_21"`, **delete the `"refmap"` line**.
   Everything else (client list, `defaultRequire: 1`) stays.
2. `@Mixin(forge.net.mca.client.gui.AbstractDynamicScreen.class)` →
   `@Mixin(net.conczin.mca.client.gui.AbstractDynamicScreen.class)`; same for the
   `InteractScreenAccessor` target.
3. Keep `remap = false` on the `setLayout` injection **and its comment** — still correct: it's a
   non-vanilla method (and with mojmap-at-runtime, remapping is a no-op anyway).
4. `ScreenAccessor`: with Parchment/mojmap the vanilla field names are `renderables`, `children`,
   `narratables` — the `@Accessor` annotations keep working without a refmap. If Mixin complains
   about the shadowed field names at runtime, add explicit `@Accessor("renderables")` values.
5. The button press callback changes `QuestNetwork.CHANNEL.sendToServer(new OpenQuestMenuC2SPacket(uuid))`
   → `PacketDistributor.sendToServer(new OpenQuestMenuC2SPacket(uuid))`.
6. Bonus check while you're here: 1.21.1 `AbstractDynamicScreen.setLayout` now calls
   `clearWidgets()` then `addRenderableWidget(...)` **[verified]** — the TAIL injection point
   remains correct (our button is added after the layout rebuild, exactly as before).

---

## 11. Phase 9 — Client: HUD overlay, ticks, screens, keybinds

1. **HUD** (`QuestHudOverlay implements IGuiOverlay`, registered via
   `RegisterGuiOverlaysEvent.registerAboveAll("quest_tracker", …)` **[verified]**):
   - Implement `net.minecraft.client.gui.LayeredDraw.Layer` instead:
     `void render(GuiGraphics graphics, DeltaTracker deltaTracker)`. Screen size comes from
     `graphics.guiWidth()/guiHeight()`; the `ForgeGui` param has no replacement (it was only used
     implicitly, if at all — the render body reads `Minecraft.getInstance()` already).
   - Register in `QuestClientSetup` via
     `net.neoforged.neoforge.client.event.RegisterGuiLayersEvent`:
     `event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(MOD_ID, "quest_tracker"), new QuestHudOverlay());`
   - Parity: keep the `minecraft.options.hideGui` and `ClientQuestData.isHudVisible()` guards, the
     anchor/offset config logic, and the amber/red countdown thresholds byte-for-byte.
2. **Client tick** (`QuestClientInput`): `ClientTickEvent.Post` (new package, see Phase 4 table),
   delete the `phase != END` guard. Keybind `consumeClick()` loops unchanged.
3. **Screens** (`QuestMenuScreen`, `QuestLogScreen`, `JournalScreen`, `ProjectMenuScreen`,
   `ScrollView`): expect near-zero changes beyond `ResourceLocation` and send-site updates.
   `Button.builder`, `EditBox`, `Screen#init`, `GuiGraphics` are all stable across this gap.
4. **Toasts**: only the `ResourceLocation` constructor fix (see §5.4).
5. `QuestClientSetup`'s `@Mod.EventBusSubscriber(bus = MOD, value = Dist.CLIENT)` →
   `@EventBusSubscriber(modid = …, value = Dist.CLIENT)` — mod-bus events are auto-routed.
6. `McaQuestsConfig.CLIENT` reads from client code are unchanged (`ModConfigSpec` values have the
   same `get()` API).

---

## 12. Phase 10 — FTB Quests integration (`compat/ftbq/`)

The 1.21.1 FTBQ artifacts exist **[verified]** but jumped a major line (2001.x → 2101.x); expect
real API drift here — this phase is compile-and-adapt.

1. Swap dependencies (done in Phase 1). Note artifact renames to `*-neoforge`.
2. `neoforge.mods.toml` optional dep range: `[2101.1,)` (done in Phase 2).
3. Expected drift when compiling `compat/ftbq/` (fix within this package only):
   - `Task`/`Reward` NBT hooks now carry registry context — signatures like
     `writeData(CompoundTag nbt, HolderLookup.Provider provider)` /
     `readData(CompoundTag nbt, HolderLookup.Provider provider)` and matching `writeNetData`/
     `readNetData` on `RegistryFriendlyByteBuf`. Update `McaBooleanTaskBase`,
     `McaCounterTaskBase`, `McaRewardBase`, and each concrete task/reward override.
   - `TaskType`/`RewardType` registration and icon APIs may have moved; mirror whatever a 2101.x
     built-in task does (unpack the `ftb-quests-neoforge` sources jar or read
     `FTBTeam/ftb-quests` GitHub `1.21.1` branch for one example task and copy its shape).
   - `FtbqEventBridge` listens to our own API events + `PlayerLoggedInEvent` — only imports move.
   - FTB Teams API (`ftb-teams-neoforge`) party lookups: verify `FTBTeamsAPI.api()` call shapes.
4. Invariants that must survive: `FtbqBootstrap.init()` remains the only cross-package entry;
   the `try/catch(Throwable)` guard in the mod constructor stays; `NoFtbqClassloadTest` and
   `FtbqBridgeSabotageTest` must pass; `FtbqEditorIdsS2CPacket` registration stays unconditional
   while sends stay gated (`FtbqEditorIdsSync`).
5. If 2101.x drift is deeper than expected, port the bridge in a follow-up: gate
   `FtbqBootstrap.init()` behind a temporary `false &&` with a `// PORT-TODO` and keep the rest of
   the mod shippable — but only as a last resort, and say so loudly in the PR.

---

## 13. Phase 11 — MCA: Reputation sibling integration

The build compiles `compat/reputation/` against `../MCAReputation/build/classes/java/main`
**[verified]**, and the build intentionally fails loudly when the sibling is absent.

Decision order:

1. **Preferred**: port MCAReputation to 1.21.1 NeoForge first (it is a much smaller mod; apply
   Phases 1–6 of this document to it), build its classes, then compile this mod against them.
2. **If the sibling port is out of scope right now**: keep the integration source intact but make
   the Gradle wiring tolerate absence — the `compat.reputation` package only compiles when the
   classes dir exists; add a source-set exclusion in that branch
   (`sourceSets.main.java.exclude 'dev/otectus/mcaquests/compat/reputation/**'` inside the `else`)
   plus a guard so `ReputationBridge.init()` treats missing classes as "mod not installed" (it
   already checks `ModList` — verify the classload path is also guarded with the same
   try/catch-Throwable pattern used for FTBQ).
   Mark this clearly: parity for the *optional* integration then lands with the sibling's port.
3. Either way `LegacyReputationBackend` (the built-in store) is the default and must be fully
   functional — it is the parity-critical path.

---

## 14. Phase 12 — Resources, datapacks, lang

1. Quest/project/situation/tier/title JSONs (`data/mcaquests/mcaquests/**`) are **format-stable**:
   the mod parses them with its own codecs (`StrictCodecs`, validators). No content changes.
2. Verify every registry-id lookup in the loaders/validators against 1.21.1 ids: block/item/entity
   ids referenced by the built-in pack still exist in 1.21.1 (vanilla removed nothing relevant
   between 1.20.1 and 1.21.1; `grep -h '"minecraft:' src/main/resources -r | sort -u` and
   spot-check anything unusual).
3. Lang files (`en_us.json`, `pt_br.json`) unchanged.
4. `assets/mcaquests` textures (if any appear) unchanged.
5. If a `pack.mcmeta` exists in resources, bump `pack_format` (data 48 / resource 34 for 1.21.1);
   if none exists (mod resources don't need one), skip.
6. Datagen run (`runData`) — only reconfirm it executes; the mod ships hand-written JSON.

---

## 15. Phase 13 — Tests

1. Keep JUnit 5.10.2. MDG's `unitTest { enable() }` (Phase 1) puts Minecraft + NeoForge on the
   test classpath the way ForgeGradle did.
2. `TestBootstrap` **[verified]** flips `Bootstrap.isBootstrapped` reflectively because Forge's
   `Bootstrap.bootStrap()` NPE'd through `NetworkHooks.init()`. On NeoForge that failure mode is
   gone — **first try replacing the reflection hack with a real `SharedConstants.tryDetectVersion();
   Bootstrap.bootStrap();`**. If that works, delete the hack and its warning javadoc; if NeoForge's
   bootstrap also fails in unit-test context, keep the reflection approach (update the javadoc to
   name the new reason) — the field name `isBootstrapped` must be re-verified against 1.21.1
   mojmap.
3. Test-by-test expectations:
   - Pure-logic tests (parsing, codecs, throttles, tiers, filters) — pass untouched.
   - `EditorIdsPacketCodecTest` — update to the payload/`StreamCodec` shape from Phase 5.
   - `NoFtbqClassloadTest`, `FtbqBridgeSabotageTest`, `McaCompatSafeFailTest` — these enforce the
     isolation invariants; they must pass unmodified in spirit (imports may move).
   - `PlayerTitlesNbtTest`, `ProgressionCodecTest` etc. — unchanged unless they construct
     `ResourceLocation`s (mechanical fix).
   - Add one new test: the Phase 8.3 ForgeCaps migration shim against a canned 1.20.1 NBT blob.
4. `./gradlew test` green is a phase gate.

---

## 16. Phase 14 — Verification and parity matrix

### 16.1 Static checks

```bash
grep -rn 'net\.minecraftforge' src/ && echo "FAIL: forge refs remain"
grep -rn 'forge\.net\.mca' src/ && echo "FAIL: old MCA packages remain"
grep -rn 'DistExecutor\|LazyOptional\|SimpleChannel\|ICapabilitySerializable' src/ && echo "FAIL"
./gradlew build   # jar + tests
```

### 16.2 Dev-run smoke (client)

`./gradlew runClient`, create a world (MCA now loads in dev — exploit that):

1. Find/summon an MCA villager; open its interaction menu → **Quests button appears under
   Interact** (mixin works; check log for mixin apply errors on startup).
2. Open Quests → offers render; accept a quest (e.g. a gather quest) → HUD tracker shows it.
3. Complete the objective → "ready to turn in" **toast**; turn in → rewards granted, hearts
   change (verify via MCA's own UI), reputation/standing increments.
4. Quest log keybind (J toggles HUD; bind and test the log + journal keys) → screens open,
   scroll, abandon-from-log works.
5. `/mcaquests` command tree: reload, dump/diagnostic subcommands run.
6. Community project: trigger/inspect a project via its menu, contribute items, phase-advance
   toast fires.
7. Situations: use command/config to accelerate a trigger (e.g. villager death → MissingKin);
   situation toast + offer appears; resolve it.
8. Reputation tier-up toast fires at a tier boundary; journal shows archive + titles.
9. Sleep/rest, fishing, trade, breed, tame, break/place-block objectives: run one quest of each
   objective family — this exercises every migrated event handler. Cover the full
   `ObjectiveTypes` list: craft, place, break, breed, visit-biome, visit-dimension, heal, deliver,
   build-near, fish, sleep/rest, escort, cure, plus condition/target variants.
10. Config: `mcaquests-common.toml` + `mcaquests-client.toml` generate in `run/config` with the
    same keys as before; flip `showQuestButtonInMcaMenu` false → button disappears.

### 16.3 Dedicated server smoke

`./gradlew runServer` + connect with the dev client:

1. Server boots with no client-class loading errors (validates §7.4).
2. Join succeeds (payload registrar version match); quest flow works end-to-end over the wire.
3. Vanilla client (no mod) join is rejected/handled exactly as a missing required mod should be.
4. Stop server; restart; active quests, projects, situations, standing all persist (SavedData +
   attachment round-trip).

### 16.4 World-upgrade parity (the Phase 8.3 shim)

1. In the 1.20.1 Forge build, create a world; accept quests, earn standing/titles.
2. Open that world in the 1.21.1 NeoForge build (after vanilla's own world upgrade prompt).
3. Player quest log, titles, history, standing: intact. Situations/projects `.dat` stores: intact.
4. Log shows the one-shot `migrated_from_forge` import exactly once.

### 16.5 Optional-integration matrix

| Setup | Expectation |
|---|---|
| No FTBQ, no MCAReputation | Everything above works; log says integrations inactive |
| `enableFtbqInDev=true` | FTBQ chapter editor lists MCA task/reward types; `McaQuestCompletedTask` completes when its quest completes; editor known-ids sync packet arrives; sabotage/fail-soft paths log-and-continue |
| MCAReputation present (if ported) | `ReputationBridge` selects `CanonicalReputationBackend`; standing mirrors; removing the mod later falls back without data loss |

---

## 17. Post-port housekeeping

1. `CHANGELOG.md`: new `2.0.0` entry — platform move, protocol `"8"`, API event package change
   (`net.neoforged.bus.api.Event`), MCA `net.conczin.mca` note, save-migration shim description.
2. Update `README.md` / `CURSEFORGE_DESCRIPTION.md` / `CONFIG.md` / `DATAPACK.md` /
   `FTBQUESTS.md`: versions, "requires NeoForge 21.1.x + MCA 7.7.x", drop every mention of
   Architectury as a required dependency, drop the "MCA doesn't load in dev" caveat.
3. `mods.toml`-era comments referencing Forge in javadoc: sweep `grep -rn 'Forge' src/main/java`
   and correct prose where it now lies (leave historical notes that are still true).
4. Tag the last Forge commit (`v1.1.0-forge-final`) so the 1.20.1 line can receive fixes.

---

## Appendix A — Quick-reference: the 17 payload ids

Suggested stable ids (namespace `mcaquests`): `open_quest_menu`, `quest_decision`,
`quest_turn_in`, `quest_abandon`, `quest_abandon_from_log`, `project_contribute`,
`request_journal` (C2S); `quest_menu_data`, `quest_log_sync`, `quest_ready_toast`,
`project_menu_data`, `project_log_sync`, `project_phase_toast`, `reputation_tier_toast`,
`journal_sync`, `situation_toast`, `ftbq_editor_ids` (S2C).

## Appendix B — Known traps, in one place

- **Modrinth Maven ambiguity**: MCA publishes Fabric and NeoForge files under similar version
  numbers. `7.7.22+1.21.1` is the NeoForge file **[verified]**; if resolution ever grabs a Fabric
  jar (you'd see `fabric.mod.json` inside), pin by Modrinth *version id* instead
  (`maven.modrinth:minecraft-comes-alive-reborn:<versionId>`).
- **Don't chase 1.21.2+ changes**: Toast/ToastManager, `blit(RenderType…)`, NBT Optional getters,
  `EventBusSubscriber` bus removal — none apply to 1.21.1. If a migration guide mentions them,
  check the version gate first.
- **`PlayerTickEvent.Post` fires for both sides** — the old handler filtered
  `player instanceof ServerPlayer`; keep that filter.
- **Payload handler exceptions disconnect the player** on NeoForge; the old `SimpleChannel`
  swallowed less loudly. Keep handler bodies defensive (they already are).
- **`event.enqueueWork` for network registration is gone** — registration must live in the
  `RegisterPayloadHandlersEvent` listener; registering late throws.
- **Attachment `getData` creates-on-read** — code that used `.resolve().isEmpty()` to detect
  "no data yet" must instead check the data's own emptiness (`PlayerQuestData` has natural
  empty-state semantics; verify the 2–3 call sites that branched on the capability being absent).
- **MCA is a mojmap jar loaded straight into dev** — if two MCA versions end up on the classpath
  (e.g. a stale `mods/` folder in `run/`), FML will crash with a duplicate-mod error; keep `run/`
  clean when switching branches.
