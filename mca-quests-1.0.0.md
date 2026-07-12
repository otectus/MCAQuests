# MCA: Quests × FTB Quests — Optional Compatibility Build Specification

**Target release:** MCA: Quests **v1.0.0** (from current `0.9.0`)
**Platform:** Minecraft 1.20.1 · Forge 47.4.10+ · Java 17 · ForgeGradle 6
**Integration target:** FTB Quests `2001.4.22` (branch `1.20.1/main`), FTB Library `2001.2.9`, FTB Teams `2001.3.0`
**Author of record:** otectus · **Spec status:** ready for implementation
**House style:** this document follows the structure and conventions of `mca-quests.md` (the original build spec). Where this spec says "spec section N" it refers to sections of *this* document unless prefixed "original spec".

---

## Part I — Context and Design

---

## 1. Purpose and Scope

MCA: Quests v1.0.0 ships **optional, full, feature-rich, two-way compatibility with FTB Quests**:

1. **MCA → FTB:** Modpack authors building an FTB Quests progression book can gate chapters and quests on what the player has done in the villager life-sim layer — quests completed for villagers, relationship arcs finished, village reputation tiers reached, titles earned, village projects completed or contributed to, situations resolved, MCA hearts earned, marriage. These appear as **first-class FTB Quests task types** in the in-game editor, with icons, tooltips, live progress bars, and config screens — indistinguishable in quality from FTB's built-in tasks.
2. **MCA → FTB (rewards):** FTB quest books can **feed back into the villager world** — reward types that grant village reputation, MCA hearts, and MCA: Quests titles.
3. **FTB → MCA:** Villager quest datapack authors can gate villager offers on FTB Quests progress (**conditions**), send players into the FTB quest book (**an objective**: "complete FTB quest X"), and push FTB Quests progress from a villager quest turn-in (**a reward**).

The integration must be **strictly optional**: with FTB Quests absent, MCA: Quests must behave byte-for-byte identically to a build without the integration — no classloading of FTB types, no config noise, no behavior change, no crash under any circumstance. This mirrors the mod's two existing optionality precedents: the `McaCompat` isolation layer (original spec §7) and the MCA: Conversations soft-dependency hooks (`QuestDialogueHooks` / `ExternalSignalObjective`, v0.9.0).

This spec is written for a coding agent with access to both repositories. Every FTB Quests class, method, and behavior cited here was verified against the `1.20.1/main` source; every MCA: Quests citation was verified against the current `main` at v0.9.0.

---

## 2. The Two Systems at a Glance

Understanding the data-model mismatch is the foundation of every design decision below.

| Dimension | MCA: Quests | FTB Quests |
|---|---|---|
| Quest identity | `ResourceLocation` (e.g. `mcaquests:farmer_wheat_request`), datapack JSON | random 64-bit `long`, rendered as 16-hex-char code strings (`getCodeString` → `%016X`), SNBT files in `config/ftbquests/quests/` |
| Authoring | JSON datapacks, validated on `/reload` | In-game GUI editor (editing mode), SNBT on disk |
| Progress owner | **Per-player** (Forge capability `mcaquests:player_quests` → player NBT) | **Per-team** (FTB Teams UUID → `TeamData`, `<world>/ftbquests/<team-uuid>.snbt`) |
| Progress driver | Forge game events + ~1/sec player tick poll (`QuestProgressEvents`) | Architectury events + `autoSubmitOnPlayerTick()` poll driven by `FTBQuestsEventHandler` |
| Quest instances | Dynamic: per-villager offers, frozen templates, transient situations | Static: one global book, same objects for everyone |
| Giver | A specific living villager entity | None (a book) |
| Repeatability | Cooldowns, once, per-villager arcs | Repeatable quests with cooldown, per-team completion count |
| Events out | Forge bus: `QuestAccepted/Ready/Completed/Abandoned/Failed`, `ProjectEvent.*` | Architectury: `ObjectStartedEvent`, `ObjectCompletedEvent` (`FILE/CHAPTER/QUEST/TASK/GENERIC`), `CustomTaskEvent`, `CustomRewardEvent`, `ClearFileCacheEvent` |
| Extension | `McaQuestsApi.registerObjective/Reward/Condition(id, Codec)` | `TaskTypes.register(rl, Provider, iconSupplier)` / `RewardTypes.register(...)` (internal-but-idiomatic; no API-package equivalent exists) |
| Network | `SimpleChannel mcaquests:main`, `PROTOCOL_VERSION = "4"`, strict handshake | Architectury `SimpleNetworkManager("ftbquests")`; full-file sync assigns each TaskType an `internalId` by registry iteration and **NPEs on a client missing a registered type** |
| Sidedness | Both sides mandatory | Both sides mandatory |

Two verified FTB Quests behaviors anchor the optionality story:

- **Disk degradation is graceful.** `BaseQuestFile.readDataFull` (lines ~613–633): an unknown task type becomes a placeholder `CustomTask` titled `"Unknown type: <type>"`; unknown rewards become placeholder `CustomReward`s. A pack whose book references `mcaquests:*` tasks does not crash when MCA: Quests is removed — the tasks become inert (and their extra fields are lost on next editor save; documented in §24).
- **Network sync is not graceful.** `readNetDataFull` maps type ids through the client's registry and NPEs if a type is missing. **Therefore task/reward type registration must be unconditional on both sides whenever FTB Quests is present — never gated by config or server-only state.** Config toggles may gate *behavior* (progress crediting, reward effects) but never *registration* (§12).

---

## 3. Research Summary — How FTB Quests Wants to Be Extended

Verified integration surface (file references are to the FTB Quests repo, `common/src/main/java/dev/ftb/mods/ftbquests/` unless noted):

1. **Register a task type:** `TaskTypes.register(ResourceLocation, TaskType.Provider /* (long id, Quest quest) -> Task */, Supplier<Icon>)` — a plain `LinkedHashMap` put. FTB's own loader-specific `ForgeEnergyTask` registers from the Forge mod constructor (`forge/.../FTBQuestsForge.java:52`), i.e. **after** `new FTBQuests()` has initialized the built-ins — exactly the window our mod constructor occupies given `ordering="AFTER"` on the `ftbquests` dependency. Registered types automatically appear in the editor's "Add task" list (`AddTaskButton` iterates `TaskTypes.TYPES.values()`); the display name defaults to lang key `ftbquests.task.<ns>.<path>`.
2. **Boolean/stateful tasks:** extend `AbstractBooleanTask`, implement `boolean canSubmit(TeamData, ServerPlayer)`, return N>0 from `autoSubmitOnPlayerTick()` — `FTBQuestsEventHandler.playerTick` then polls it every N ticks with zero extra wiring, guarded by `!isCompleted && canStartTasks`, inside `withPlayerContext`. `StageTask` (poll 20), `AdvancementTask` (poll 5), `DimensionTask` (poll 100) are the precedents.
3. **Counter tasks:** extend `Task`, override `getMaxProgress()`, mutate via `TeamData.setProgress/addProgress` on the server. `StatTask` establishes the **monotone high-water pattern** this spec adopts: on each poll, compute the submitting player's value and `setProgress` if it exceeds current team progress (`if (set > teamData.getProgress(this)) teamData.setProgress(this, set)`).
4. **External push hook:** `StageTask.checkStages(ServerPlayer)` is FTB's blessed idiom for an outside mod to force re-checks when its own event fires: get `TeamData` via `ServerQuestFile.INSTANCE.getOrCreateTeamData(player)`, bail if null/`isLocked()`, then `withPlayerContext(player, () -> { for (Task t : getAllTasks()) if (t instanceof X && data.canStartTasks(t.getQuest())) t.submitTask(data, player); })`. Our event bridge (§15.0) replicates this exactly, with a type-filtered cache invalidated by `ClearFileCacheEvent`.
5. **Rewards:** `RewardTypes.register(...)`; implement `void claim(ServerPlayer player, boolean notify)`. Base class already provides per-team/per-player tristate, autoclaim modes, and the editor plumbing.
6. **Reading FTB state from outside:** `ServerQuestFile.INSTANCE` (server singleton) or `FTBQuestsAPI.api().getQuestFile(false)`; object lookup via `file.get(long)` / `getQuest(long)` / `getChapter(long)`; id parse via `QuestObjectBase.parseCodeString(String)` (accepts optional leading `#`, returns `0L` on failure); progress via `TeamData.getOrCreateTeamData(player)` → `isCompleted(QuestObject)`, `getProgress(Task)`.
7. **Reacting to FTB progress:** Architectury events `ObjectCompletedEvent.QUEST/TASK/CHAPTER` (payload exposes `getData() -> TeamData`, `getOnlineMembers()`, typed object getters).
8. **Editor config:** override `fillConfigGroup(ConfigGroup)` (client), using FTB Library builders (`addString`, `addInt`, `addBool`, `addEnum` with `NameMap`, …). Call `super.fillConfigGroup(config)` first. Serialization: `writeData/readData(CompoundTag)` (disk SNBT) and `writeNetData/readNetData(FriendlyByteBuf)` (full-file sync) — always call `super` first, and **every field the client displays must be in net data**.
9. **API stability warning:** the sanctioned `dev.ftb.mods.ftbquests.api` package is tiny (quest-file getter + item-filter adapter); everything above is internal-but-idiomatic. FTB's Javadoc explicitly says internals may change. Mitigations in §32.
10. **Distribution:** group `dev.ftb.mods`, artifacts `ftb-quests-forge`, `ftb-library-forge`, `ftb-teams-forge` on `https://maven.ftb.dev/releases` (mirrored to `maven.saps.dev`). License: **All Rights Reserved, "visible source"** — see §8.

---

## 4. Design Philosophy and Invariants

Seven principles govern every decision in this spec. They extend the mod's existing invariants (server-authoritative, dup-proof, data-driven, fail-safe).

1. **Optional means invisible.** With FTB Quests absent, no `dev.ftb.mods.*` class is ever classloaded, no new config section changes behavior, no log line above DEBUG appears, and every `mcaquests:ftbq_*` datapack reference degrades exactly as an author-selectable policy dictates (§17). The integration is a pure additive shell around an unchanged core.
2. **One seam, mirrored from `McaCompat`.** All FTB Quests imports live in one package, `dev.otectus.mcaquests.compat.ftbq`, reached exclusively through a bridge interface whose no-op implementation is the default. Every bridge method is fail-safe: any `Throwable` is caught, logged at DEBUG, and a documented default returned — an FTB Quests internals drift can degrade the integration but can never crash a server (`McaCompatSafeFailTest` precedent; §10, §29).
3. **Registration is unconditional; behavior is configurable.** When `ftbquests` is loaded, task/reward types register on both sides no matter what the config says (network-sync correctness, §2). The `enableFtbQuestsIntegration` master switch gates progress crediting, reward effects, condition evaluation, and command output — never type registration.
4. **Monotone, recomputable progress.** Wherever possible, FTB-side tasks derive progress from **persistent MCA: Quests state** (per-player counters, reputation values, title sets) rather than from counting transient events. Progress becomes an idempotent, order-independent, restart-safe function — the `StatTask` pattern — and survives `/ftbquests change_progress` resets, offline completions, late book edits, and team merges without special cases (§14).
5. **Per-team credit follows FTB semantics; per-player state follows MCA semantics.** An FTB task completes for the whole team when **any one member** meets the bar — identical to `StatTask`/`AdvancementTask`/`StageTask`. We document this loudly rather than fight it (§14).
6. **The core grows only genuinely general API.** Everything the bridge needs that MCA: Quests does not yet expose — lifecycle events for situations/tiers/titles/contributions, per-player progression counters, a polling-objective interface, reputation read accessors — is added as **public, FTB-agnostic core API** (§11). Any future integration (BetterQuesting, KubeJS, leaderboards) gets it for free, and all of it is unit-testable without FTB Quests on the classpath.
7. **Author experience is a feature.** Editor dropdowns populated with real quest/tier/title ids, precise validation messages naming the offending object, `/mcaquests ftbq validate` for cross-referencing both directions, and complete authoring docs are in-scope, not gold-plating (§20, §21, §28).

---

## 5. Goals

1. Ten FTB Quests **task types** under the `mcaquests:` namespace (§15), fully functional in the editor (icons, display names, config screens, progress formatting, tooltips) and in play (event-pushed and/or polled, per §14 semantics).
2. Three FTB Quests **reward types** under the `mcaquests:` namespace (§16) that route through MCA: Quests' existing safety funnels (hearts clamps, `ReputationService`, `TitleService`).
3. Three MCA: Quests **condition types** (§17), one **objective type** (§18), and one **reward type** (§19) that read/write FTB Quests state, registered through `McaQuestsApi` and usable in any quest, situation offer, project `unlock`, or template.
4. Core API additions (§11): `SituationResolvedEvent`, `ReputationTierReachedEvent`, `TitleGrantedEvent`, `ProjectEvent.Contributed`, per-player `ProgressionStats`, `PollingObjective`, reputation read accessors, two `McaCompat` additions — all functional and documented independently of FTB Quests.
5. Editor quality-of-life: known-id sync to clients for dropdown pickers (§20), with graceful free-text fallback.
6. `/mcaquests ftbq status|validate|recheck` commands (§21).
7. Complete documentation updates (§28), localization (§22), unit tests and a manual test matrix (§29).
8. Version bump to **1.0.0**, changelog entry, network protocol bump `"4"` → `"5"` (§20).

## 6. Non-Goals for v1.0.0 (with rationale)

1. **No mirroring of MCA quests as auto-generated FTB chapters.** MCA offers are per-villager, per-player, template-frozen, and transient (situations); FTB's book is one static, team-shared graph edited by hand. Auto-generation would fight both data models, produce a book that lies to half the team, and double every UI surface. The HUD/log/journal remain MCA: Quests' own UI.
2. **No FTB Quests → MCA "quest browser".** Same reason, inverted.
3. **No support for MCA tasks inside FTB loot crates / task screens.** Our tasks are stateful checks, not item submissions (`canInsertItem() == false`); the task-screen block is `ItemTask`-specific today.
4. **No Fabric/NeoForge/1.21 work.** MCA: Quests is Forge 1.20.1-only; the seam design (bridge interface + isolated package) is deliberately portable later.
5. **No KubeJS bindings and no `CustomTask.Check` scripting layer.** First-class task types are strictly better for pack authors here; `ftbquests:custom` + `CustomTaskEvent` remains available to packs independently of us.
6. **No per-player emulation of FTB progress.** We adopt FTB's per-team model for FTB-side objects rather than shadow-tracking per-player copies (§14).
7. **No dependence on FTB XMod Compat.** We integrate directly; XMod Compat's `StageTask.checkStages` idiom is copied, not invoked.
8. **No new hard dependencies.** FTB Library / FTB Teams types are touched only inside the guarded package (they are transitive presences whenever `ftbquests` is loaded — its `mods.toml` makes both mandatory).

---

## 7. Integration Architecture Overview

```
                        ┌────────────────────────────────────────────────────┐
                        │        MCA: Quests core (unchanged behavior)       │
                        │                                                    │
   Forge events ───────▶│ QuestManager · ProjectManager · SituationManager   │
                        │ ReputationService · TitleService · QuestHistory    │
                        │        + NEW: ProgressionStats, 4 new events (§11) │
                        └───────────────┬────────────────────────────────────┘
                                        │ public, FTB-agnostic API only
                        ┌───────────────▼───────────────┐
                        │  compat/FtbqBridge (interface) │  ◀── always classloaded
                        │  compat/NoopFtbqBridge         │      (zero FTBQ imports)
                        └───────────────┬───────────────┘
                 ModList.isLoaded("ftbquests")?  ──no──▶  Noop stays installed
                                        │yes (+ Throwable-guarded init)
                        ┌───────────────▼────────────────────────────────────┐
                        │  compat/ftbq/** (ONLY classloaded when FTBQ present)│
                        │  FtbqBootstrap — registers types, wires listeners  │
                        │  FtbqBridgeImpl — fail-safe read/write of FTBQ     │
                        │  FtbqTaskTypes — 10 TaskTypes (§15)                │
                        │  FtbqRewardTypes — 3 RewardTypes (§16)             │
                        │  FtbqEventBridge — MCAQ events → task re-checks    │
                        └───────────────┬────────────────────────────────────┘
                                        │ TaskTypes.register / TeamData / events
                        ┌───────────────▼───────────────┐
                        │           FTB Quests           │
                        └────────────────────────────────┘
```

**Flow A (MCA activity → FTB task progress):** a villager quest completes → `QuestCompletedEvent` fires on the Forge bus → `FtbqEventBridge` updates the player's `ProgressionStats` snapshot is already updated by core → bridge runs the `checkStages`-style sweep for the affected task classes → each task's `canSubmit`/recompute reads persistent MCA state → `TeamData.setProgress`. The poll (`autoSubmitOnPlayerTick`) provides the catch-all for anything event push misses (login, offline changes, admin resets).

**Flow B (FTB book rewards → MCA world):** player claims `mcaquests:hearts` (etc.) reward in the FTB book → `claim(ServerPlayer, notify)` → routes through `McaCompat.addHearts` / `ReputationService.award` / `TitleService.grant` with the existing clamps and funnels.

**Flow C (FTB state → villager offers):** villager menu opens → `eligibleOffers` evaluates `mcaquests:ftbq_quest_completed` condition → condition calls `FtbqBridge.get().isQuestCompleted(player, hexId)` → Noop returns the author's `when_missing` policy; the real impl parses the code string and reads `TeamData`.

**Flow D (villager quest ↔ FTB book):** an active villager quest with an `mcaquests:ftbq_complete_quest` objective is satisfied when the player's team completes the referenced FTB quest (polled ~1/sec via the new generic `PollingObjective` hook); an `mcaquests:ftbq_progress` reward pushes FTB progress at turn-in.

---

## 8. Licensing and Distribution Constraints

Facts (verified): MCA: Quests is **GPL-3.0-only** (required — it links MCA Reborn internals). FTB Quests is **"All Rights Reserved … visible source"** (README §Licence; `mods.toml` declares `license="All Rights Reserved"`; no LICENSE.md is actually committed to the 1.20.1 branch). FTB publishes compile-consumable artifacts to a public maven and optional third-party integrations against those artifacts are ubiquitous and encouraged by FTB's own architecture (the `StageTask` XMod hook exists precisely for outside mods).

Hard rules for the implementation:

1. **`compileOnly` linkage only.** FTB Quests, FTB Library, and FTB Teams jars must never be shaded, repackaged, jar-in-jar'd, or redistributed in any MCA: Quests artifact. The shipped jar contains zero bytes of FTB code.
2. **No source copying.** Idioms (the `checkStages` sweep, the `StatTask` monotone pattern) are reimplemented from understanding, not pasted. Do not copy FTB method bodies.
3. **README note.** Add one sentence to the README compatibility section stating the integration compiles against FTB's publicly published maven artifacts and ships none of them (§28).
4. This spec records the factual situation; whether GPL-3.0 § "System Libraries"/aggregation reasoning satisfies the author is **the author's call, not the coding agent's** — flag, don't decide. (This is not legal advice.)

---

## Part II — Core Plumbing

---

## 9. Build and Dependency Changes

### 9.1 `gradle.properties`

```properties
mod_version=1.0.0

## FTB Quests optional-compat (compileOnly; never shipped)
ftb_quests_version=2001.4.22
ftb_library_version=2001.2.9
ftb_teams_version=2001.3.0
## Set true to add the FTB stack to dev runs for manual testing
enableFtbqInDev=false
```

### 9.2 `build.gradle`

Add the FTB maven (scoped) to `repositories`:

```gradle
maven {
    name = 'FTB'
    url = 'https://maven.ftb.dev/releases'
    content { includeGroup 'dev.ftb.mods' }
}
```

Add to `dependencies` (ForgeGradle 6 consumes FTB's production Forge jars via `fg.deobf`, exactly like the existing MCA dependency):

```gradle
// FTB Quests optional integration — compile-time only. NEVER shipped, NEVER 'implementation'.
compileOnly fg.deobf("dev.ftb.mods:ftb-quests-forge:${ftb_quests_version}")
compileOnly fg.deobf("dev.ftb.mods:ftb-library-forge:${ftb_library_version}")
compileOnly fg.deobf("dev.ftb.mods:ftb-teams-forge:${ftb_teams_version}")

// Optional: put the FTB stack on the dev runtime for hands-on testing.
if (project.hasProperty('enableFtbqInDev') && project.enableFtbqInDev.toBoolean()) {
    runtimeOnly fg.deobf("dev.ftb.mods:ftb-quests-forge:${ftb_quests_version}")
    runtimeOnly fg.deobf("dev.ftb.mods:ftb-library-forge:${ftb_library_version}")
    runtimeOnly fg.deobf("dev.ftb.mods:ftb-teams-forge:${ftb_teams_version}")
}
```

Note (carried from the existing build's comments): MCA's own mixins do not resolve in dev runs, so end-to-end verification of MCA-touching paths happens in a production-style instance regardless; the dev runtime toggle still allows exercising the FTB editor and the pure-FTB paths (§29.2).

### 9.3 `META-INF/mods.toml`

Append an **optional** dependency so Forge orders us after FTB Quests when it exists (guaranteeing `TaskTypes`' built-ins are initialized before our constructor registers types):

```toml
[[dependencies.${mod_id}]]
    modId="ftbquests"
    mandatory=false
    versionRange="[2001.4,)"
    ordering="AFTER"
    side="BOTH"
```

Do **not** add ftblibrary/ftbteams entries — they are mandatory dependencies of `ftbquests` itself and their presence is implied.

### 9.4 Version-range policy

`[2001.4,)` accepts future 1.20.1-line patches. Because we compile against internals (§3 item 9), the bridge init is wrapped in a `Throwable` guard (§10.4): if a future FTB build breaks binary compatibility, the integration disables itself with one ERROR log line instead of crashing. Pin the tested version in docs (§28).

---

## 10. The Bridge Seam

### 10.1 `compat/FtbqBridge.java` (always classloaded — **zero FTB imports**)

```java
package dev.otectus.mcaquests.compat;

/**
 * Seam to the optional FTB Quests integration. The default NOOP instance is replaced by
 * FtbqBootstrap.init() iff the "ftbquests" mod is present AND init succeeds. Every method
 * on every implementation is fail-safe: never throws, returns the documented default.
 * Only java.* / net.minecraft.* types may appear in this interface.
 */
public interface FtbqBridge {

    enum ProgressAction { COMPLETE_TASK, COMPLETE_QUEST, RESET_TASK }

    boolean isAvailable();                       // real impl present AND enableFtbQuestsIntegration

    /** ids are FTB 16-hex code strings, optional leading '#'. Absent/invalid/unknown → false. */
    boolean isQuestCompleted(ServerPlayer player, String hexId);
    boolean isChapterCompleted(ServerPlayer player, String hexId);
    boolean isTaskCompleted(ServerPlayer player, String hexId);

    /** true if the id parses AND resolves to an object of the right kind in the server book. */
    boolean questIdExists(String hexId);
    boolean chapterIdExists(String hexId);
    boolean taskIdExists(String hexId);

    /** Applies an FTB progress change for the player's team. False on any failure (logged DEBUG). */
    boolean grantProgress(ServerPlayer player, ProgressAction action, String hexId);

    /** Re-evaluate all mcaquests-namespaced FTB tasks for this player (login, /recheck, admin ops). */
    void recheckAll(ServerPlayer player);

    /** Diagnostic counts for /mcaquests ftbq status: [mcaTasksInBook, mcaRewardsInBook]. */
    int[] integrationObjectCounts();

    final class Holder {
        private static volatile FtbqBridge instance = NoopFtbqBridge.INSTANCE;
        public static FtbqBridge get() { return instance; }
        /** Called once from FtbqBootstrap; package-private setter, last writer wins. */
        static void set(FtbqBridge b) { instance = b; }
    }
}
```

`NoopFtbqBridge` implements every method as the documented default (`false`, `new int[]{0,0}`, no-op). Include it in the same file or as a sibling class — either way, **no FTB imports**.

### 10.2 `compat/ftbq/FtbqBridgeImpl.java` (guarded package)

Mirrors `McaCompat`'s contract: every public method wraps its body in `try { … } catch (Throwable t) { McaQuests.LOGGER.debug("[MCA: Quests] FTBQ bridge {} failed", "<op>", t); return <default>; }`. Implementation notes:

- `isAvailable()` → `McaQuestsConfig.COMMON.enableFtbQuestsIntegration.get() && ServerQuestFile.INSTANCE != null`.
- Lookup helper: `long id = QuestObjectBase.parseCodeString(hex); if (id == 0) return false; QuestObject o = ServerQuestFile.INSTANCE.get(id);` (verified: `BaseQuestFile.get(long)` returns `QuestObject`; `Quest`, `Chapter`, and `Task` all extend it) then `instanceof Quest/Chapter/Task` check, then `ServerQuestFile.INSTANCE.getOrCreateTeamData(player).isCompleted(o)`.
- `grantProgress`: guard `data.isLocked()`; wrap in `ServerQuestFile.INSTANCE.withPlayerContext(player, …)`; `COMPLETE_TASK` → `data.setProgress(task, task.getMaxProgress())`; `COMPLETE_QUEST` → same for every task of the quest; `RESET_TASK` → `data.resetProgress(task)`. All gated by `isAvailable()` and config `allowFtbqProgressRewards`.
- `recheckAll`: the `checkStages` idiom over all ten `Mca*Task` classes (§15.0).
- All methods must be safe to call from the server thread only; document this. Never call from client code.

### 10.3 `compat/ftbq/FtbqBootstrap.java`

```java
public final class FtbqBootstrap {
    public static void init() {                       // called iff ModList.get().isLoaded("ftbquests")
        FtbqTaskTypes.register();                     // 10 TaskTypes — BOTH sides, unconditional
        FtbqRewardTypes.register();                   //  3 RewardTypes — BOTH sides, unconditional
        FtbqEventBridge.register();                   // Forge-bus + Architectury listeners
        FtbqBridge.Holder.set(new FtbqBridgeImpl());
        McaQuests.LOGGER.info("[MCA: Quests] FTB Quests integration active (types registered: 10 tasks, 3 rewards).");
    }
}
```

### 10.4 Wiring in `McaQuests` (mod constructor, after config registration)

```java
if (ModList.get().isLoaded("ftbquests")) {
    try {
        dev.otectus.mcaquests.compat.ftbq.FtbqBootstrap.init();
    } catch (Throwable t) {   // binary drift in a future FTBQ build must not kill the game
        McaQuests.LOGGER.error("[MCA: Quests] FTB Quests detected but integration failed to start; "
            + "it will be disabled. Report this with your FTB Quests version.", t);
    }
}
```

Classloading rules (enforced by code review + the classload unit test, §29.1): the fully-qualified call above is the **only** reference to the `compat.ftbq` package outside that package; no always-loaded class may import from it; no method signature outside it may mention an FTB type. The constructor timing is correct because `ordering="AFTER"` sorts our constructor after FTB Quests' (which initializes `TaskTypes`/`RewardTypes` built-ins), matching the `ForgeEnergyTask` precedent.

---

## 11. Core API Additions (FTB-agnostic, ship regardless)

These are v1.0.0 public-API completions. Each is useful to any add-on and is implemented and unit-tested with **no** reference to the bridge.

### 11.1 New events (`dev.otectus.mcaquests.api.event`, Forge bus, server-side, not cancellable)

```java
/** Posted by SituationManager.resolveSuccess/resolveFailure/resolveCleared, after outcomes apply. */
public class SituationResolvedEvent extends Event {
    public enum Resolution { SUCCESS, FAILURE, CLEARED }
    // definitionId (RL of the SituationDefinition — the SOURCE id, not the synthetic offer id),
    // villageId (int), resolution, participants (immutable Set<UUID>),
    // resolvingPlayer (@Nullable ServerPlayer — non-null only for SUCCESS)
}

/** Posted by ReputationService when a "v:<id>" identity crosses into a new tier (after high-water update). */
public class ReputationTierReachedEvent extends Event {
    // player (@Nullable ServerPlayer — null for offline/system awards), villageId (int),
    // ladderId (RL), tier (String id), tierIndex (int)
}

/** Posted by TitleService.grant after PlayerTitles reports a NEW title (not on re-grants). */
public class TitleGrantedEvent extends Event {
    // player (ServerPlayer), titleId (RL), scope (TitleScope), villageId (OptionalInt)
}
```

Add to the existing `ProjectEvent` family:

```java
/** Posted by ProjectManager after a contribution is banked (per contribute call, server-side). */
public static class Contributed extends ProjectEvent {
    // + player (ServerPlayer), objectiveIndex (int), amount (int)
}
```

Post sites are the existing single funnels — `SituationManager.resolve*`, `ReputationService.award`, `TitleService.grant`, `ProjectManager.contributeFromPacket`/objective-credit paths — so no call-site can bypass them. Update the README API bullet and `DATAPACK.md`/docs accordingly (§28).

### 11.2 `ProgressionStats` — per-player lifetime counters (`dev.otectus.mcaquests.state`)

A small additive struct inside `PlayerQuestData` (alongside `QuestHistory` and `PlayerTitles`):

```java
public final class ProgressionStats {
    // Map<ResourceLocation,Integer> situationSuccesses;   // keyed by situation SOURCE definition id
    // Map<ResourceLocation,Integer> projectCompletions;   // keyed by project definition id
    // Map<ResourceLocation,Integer> projectContributions; // keyed by project id, summed units
    // increment(map, id, amount); count(map, id); total(map)
    // NBT: additive keys "situation_successes", "project_completions", "project_contributions"
    //      under the existing player-quests capability tag. Absent keys load as empty (back-compat).
}
```

Incremented server-side from the new events' post sites (same tick, same funnel): situation success → +1 for each **online** participant; project completed → +1 for each online participant; contribution → +amount for the contributor. Offline participants intentionally receive no credit (documented; consistent with the mod's "online, server-authoritative" stance and avoids a pending-queue for stats). Existing `QuestHistory` already provides quest/chain counters; do not duplicate it here.

### 11.3 `PollingObjective` (`dev.otectus.mcaquests.api`)

```java
/** An objective advanced by the ~1/sec active-quest poll rather than a game event.
 *  Implementations must be cheap (no world scans) and idempotent. */
public interface PollingObjective {
    /** @return true if progress changed (triggers ready-check + log sync). */
    boolean poll(ServerPlayer player, ActiveQuest quest, ObjectiveProgress progress);
}
```

`QuestProgressEvents`' player-tick handler gains one generic pass: `forActiveObjectives(player, PollingObjective.class, …)` invoked on the existing ~1/sec cadence alongside the built-in polled objectives. This turns the hardcoded poll list into an extensible surface (the FTB objective in §18 is its first consumer; MCA: Conversations could be its second).

### 11.4 Reputation read accessors (`ReputationService`)

```java
public static OptionalInt villageReputation(MinecraftServer server, int villageId);
public static Map<Integer,Integer> allVillageReputations(MinecraftServer server); // "v:<id>" identities only
public static Optional<ReputationTier> currentTier(MinecraftServer server, int villageId, ResourceLocation ladder);
public static int tierIndex(ReputationTierSet ladder, int reputation); // pure; unit-tested
```

Backed by the existing `ProjectSavedData.reputation` map; read-only; safe when data absent (empty/absent returns).

### 11.5 `McaCompat` additions (same fail-safe contract as every existing method)

```java
/** True if this player is married (to a villager or player) per MCA player save data. False on any failure. */
public static boolean isPlayerMarried(ServerPlayer player);

/** Highest MCA hearts value between the player and any loaded MCA villager within `radius` blocks.
 *  Empty when none loaded / MCA absent. Cost: one bounded getEntitiesOfClass — callers must throttle. */
public static OptionalInt maxHeartsWithin(ServerPlayer player, double radius);
```

Implementation hint (not a contract): MCA's `forge.net.mca.server.world.data.PlayerSaveData` exposes marriage state; if its surface proves unstable, `isPlayerMarried` may degrade to scanning loaded villagers for `isPlayerSpouse` — with the fail-safe default `false` and a DEBUG log either way. Extend `McaCompatSafeFailTest` for both methods.

---

## 12. Registration, Lifecycle, and Caching Rules

1. **When:** `FtbqBootstrap.init()` runs in the mod constructor (§10.4). Types must exist before `ServerQuestFile` loads (server start) and before any client connects — constructor time satisfies both with margin.
2. **Both sides, unconditionally:** never gate `FtbqTaskTypes.register()`/`FtbqRewardTypes.register()` on config, side, or server properties (§2). The dedicated-server and client paths both execute the same constructor code.
3. **Task-list caching:** `FtbqEventBridge` maintains one cached `List<Task>` per Mca task class (lazily built from `ServerQuestFile.INSTANCE.collect(...)`, mirroring `FTBQuestsEventHandler.autoSubmitTasks`) and clears all caches on `ClearFileCacheEvent.EVENT` (fired on file load/edit). Never iterate the full book per event without the cache.
4. **Sweep guardrails (every push path):** obtain `TeamData` via `getOrCreateTeamData(player)`; return if null or `data.isLocked()`; wrap in `withPlayerContext(player, …)`; per task check `!data.isCompleted(task) && data.canStartTasks(task.getQuest())` before `submitTask` (the `AbstractBooleanTask.submitTask` re-checks `isCompleted`/`checkTaskSequence` anyway — keep both; they are cheap).
5. **Server-thread only:** all bridge writes happen on the server thread (Forge event handlers and MCA: Quests funnels already are). Assert-with-log if not.
6. **Reload interplay:** `/mcaquests reload` (datapack swap) must trigger `recheckAll` for online players **after** the registry swap (new quest ids may change matching), and re-send the editor-ids packet (§20).

---

## 13. Configuration Additions

`mcaquests-common.toml`, new `[compat.ftbquests]` block (Forge `ForgeConfigSpec`, following the `enableSituations` master-switch pattern — behavior-gating, fail-safe, live-read):

| Option | Default | Meaning |
|---|---|---|
| `enableFtbQuestsIntegration` | `true` | Master behavior switch. When `false` (or FTBQ absent): Mca tasks never progress (poll returns early, pushes skip), Mca rewards no-op with a one-time WARN, `ftbq_*` conditions follow their `when_missing` policy, `ftbq_progress` rewards no-op with log, commands report "disabled". Registration is unaffected (§4.3). |
| `ftbqStatePollIntervalTicks` | `100` | `autoSubmitOnPlayerTick()` value for the stateful tasks (§15). Clamp 20–1200. Counter tasks are event-pushed and poll at 4× this interval as a safety net. |
| `ftbqHeartsScanRadius` | `16.0` | Radius for `McaCompat.maxHeartsWithin` used by the hearts task. Clamp 4–64. |
| `allowFtbqProgressRewards` | `true` | Gates the MCA-side `mcaquests:ftbq_progress` reward (§19). Not as dangerous as command rewards (scope is quest-book progress), hence default-on; still a server-owner lever. |
| `syncFtbqEditorIds` | `true` | Send the known-ids packet (§20) on login/reload when FTBQ is present. |

Client config: no additions (the editor dropdowns are driven by synced data, not client options). Update `CONFIG.md` (§28).

---

## 14. Progress-Crediting Semantics (the team/player matrix)

The single most important thing to get right and document. Rules:

1. **FTB-side Mca tasks are per-team** (FTB's model, non-negotiable). A task completes when **any one team member** meets the bar at poll/push time. Progress bars for counter tasks show the **best single member's** value (monotone high-water, `StatTask` precedent): on each evaluation for player P, compute `v = f(P's persistent MCA state)`, clamp to `getMaxProgress()`, and `if (v > teamData.getProgress(this)) teamData.setProgress(this, v)`. Never `addProgress` from events for counter tasks — recompute-from-state only (§4.4). Consequences, all documented in FTBQUESTS.md (§28): pre-existing history counts the moment the quest unlocks (like `StatTask`/`AdvancementTask`); `/ftbquests change_progress … reset` re-completes on the next poll unless the underlying MCA state is also reset; team merges take the max, never the sum; leaving a team leaves the old team's progress behind (FTB's own `copyData`/`mergeData` rules apply, untouched).
2. **MCA-side `ftbq_*` conditions and the `ftbq_complete_quest` objective read team state** (`TeamData.isCompleted`) — a party member's completion counts for everyone in the party. Documented; authors who dislike it should gate with MCA-native conditions instead.
3. **Mca reward types (FTB book side) affect the claiming player only** — hearts/titles are inherently per-player; village reputation is village-scoped anyway. Authors control claim multiplicity with FTB's own per-team/per-player reward tristate (base `Reward` config, untouched by us; our rewards default to FTB's default = per-player).
4. **`ftbq_progress` (MCA reward) affects the turn-in player's team** — the only possible target under FTB's model.

---

## Part III — Feature Surface

---

## 15. FTB Quests Task Types (namespace `mcaquests:`)

### 15.0 Shared infrastructure (`compat/ftbq/`)

- **`McaBooleanTaskBase extends AbstractBooleanTask`** — common plumbing for the stateful tasks: `autoSubmitOnPlayerTick()` returns `McaQuestsConfig.COMMON.ftbqStatePollIntervalTicks.get()` clamped; `canSubmit` delegates to an abstract `check(ServerPlayer)` wrapped in the fail-safe try/catch; `checkOnLogin()` stays default `true` (re-evaluated on login for free).
- **`McaCounterTaskBase extends Task`** — common plumbing for the counter tasks: field `long count` (default 1, min 1) = `getMaxProgress()`; `submitTask` computes the submitting player's monotone value via abstract `currentValue(ServerPlayer)` and applies rule §14.1; `autoSubmitOnPlayerTick()` returns `4 × pollInterval` (event push is primary); `fillConfigGroup` adds the shared `count` int config; disk/net serialization for `count`.
- **`FtbqEventBridge`** — Forge-bus subscriber (registered in bootstrap, NOT via `@Mod.EventBusSubscriber`, so it never registers when FTBQ is absent). Listens to: `QuestCompletedEvent` → sweep(`McaQuestCompletedTask`, `McaChainCompletedTask`); `SituationResolvedEvent` → sweep(`McaSituationResolvedTask`) for each online participant; `ProjectEvent.Completed` → sweep(`McaProjectCompletedTask`) for online participants; `ProjectEvent.Contributed` → sweep(`McaProjectContributionTask`) for the contributor; `ReputationTierReachedEvent` → sweep(`McaReputationTask`, `McaReputationTierTask`) for the player when non-null; `TitleGrantedEvent` → sweep(`McaTitleTask`); `PlayerLoggedInEvent` → `recheckAll(player)`. Plus Architectury `ClearFileCacheEvent` → drop caches (§12.3). Each sweep is the §12.4-guarded `checkStages` idiom over the cached task list. All handlers early-return when `!FtbqBridge.Holder.get().isAvailable()`.
- **String-matching helper `QuestFilter`** (pure, unit-testable): given (`questIdPattern`, `profession`, `chainId`, `category`) and a resolved `QuestDefinition`, decides whether a history entry matches. `questIdPattern` supports exact id (`mcaquests:farmer_wheat_request`), namespace wildcard (`somepack:*`), or empty = any. Resolution goes through `QuestDefinitions.resolve(id)` so situation-offer completions (synthetic ids) resolve to their offer definitions; entries whose definition no longer resolves match only the "any" pattern. Profession matching reuses `ProfessionMatcher` with the configured mode.

All ten tasks: constructor `(long id, Quest quest)` with editor-safe defaults; `getType()` returns the static `TaskType`; `writeData/readData` (SNBT keys as listed) and `writeNetData/readNetData` (same fields, same order) always call `super` first; `fillConfigGroup` calls `super` first, then adds fields via `addString`/`addInt`/`addEnum(NameMap)` with `.setNameKey("ftbquests.task.mcaquests.<path>.<field>")`, preferring dropdowns from synced known-ids when available (§20); `getAltTitle()` returns a human sentence (e.g. *"Complete 5 Farmer villager quests"*); `addMouseOverText` appends the submitting player's current value where cheap. Icons are suggestions — any valid texture id may be substituted:

| # | Type id | Class | Base | Icon suggestion |
|---|---|---|---|---|
| 1 | `mcaquests:quest_completed` | `McaQuestCompletedTask` | Counter | `minecraft:item/writable_book` |
| 2 | `mcaquests:chain_completed` | `McaChainCompletedTask` | Boolean | `minecraft:item/lead` |
| 3 | `mcaquests:reputation` | `McaReputationTask` | Counter* | `minecraft:item/emerald` |
| 4 | `mcaquests:reputation_tier` | `McaReputationTierTask` | Boolean | `minecraft:item/golden_apple` |
| 5 | `mcaquests:title` | `McaTitleTask` | Counter | `minecraft:item/name_tag` |
| 6 | `mcaquests:project_completed` | `McaProjectCompletedTask` | Counter | `minecraft:item/bell` |
| 7 | `mcaquests:project_contribution` | `McaProjectContributionTask` | Counter | `minecraft:item/bundle` |
| 8 | `mcaquests:situation_resolved` | `McaSituationResolvedTask` | Counter | `minecraft:item/clock_00` |
| 9 | `mcaquests:hearts` | `McaHeartsTask` | Boolean | `minecraft:item/heart_of_the_sea` |
| 10 | `mcaquests:married` | `McaMarriedTask` | Boolean | `minecraft:item/gold_nugget` |

\* reputation renders a progress bar toward the threshold — see 15.3.

### 15.1 `mcaquests:quest_completed` — complete villager quests

Fields (SNBT key · type · default): `quest_id · String · ""` (empty = any; supports exact / `ns:*`), `profession · String · ""` (a profession id matched via `ProfessionMatcher`; empty = any), `chain_id · String · ""` (matches `ChainSpec.chain`), `category · String · ""`, `count · long · 1`.
Value function: number of entries in the player's `QuestHistory.completionsView()` whose resolved definition passes `QuestFilter`, counting **repeat completions** (per-quest completion counts sum, capped at `count`). Situation-offer completions count when they pass the filter (their giver/profession comes from the offer definition); to target situations specifically use task #8.
Push: `QuestCompletedEvent`. Poll: safety net. Editor: `quest_id` gets the known-ids dropdown (§20); `getAltTitle` examples: *"Complete any 3 villager quests"*, *"Complete 'Feather Light'"*, *"Complete 5 quests for Farmers"*.
Edge cases: filters compose with AND; a `quest_id` that no longer exists in the registry simply never matches (surface via `/mcaquests ftbq validate`, §21); `count` > achievable is the author's problem, same as any FTB stat task.

### 15.2 `mcaquests:chain_completed` — finish a relationship arc

Fields: `chain_id · String · ""` (required in practice; validate non-empty).
Check (boolean): the player's history contains a completion of a quest whose `ChainSpec.chain` equals `chain_id` **and** which is a *final stage* — `stage == stageTotal` when `stageTotal` is present, otherwise `unlocks` empty. Implement final-stage detection as a pure helper on the definitions (unit-tested; branching arcs may have multiple finals — any counts).
Push: `QuestCompletedEvent`. `getAltTitle`: *"Finish the '<arc name>' story"* (arc display name from any chain member's `relationshipArc`/`chapter`, falling back to the raw id).

### 15.3 `mcaquests:reputation` — reach village reputation

Fields: `reputation · int · 100` (threshold, may be any int ≥ 1), `village_count · int · 1` (villages that must independently meet it).
This is a **counter task with a twist**: `getMaxProgress()` returns `reputation` when `village_count == 1` (progress bar = best village's current reputation, clamped, via `ReputationService.allVillageReputations`), else `village_count` (progress = number of qualifying villages). Both are monotone under normal play; reputation *can* decrease (situation failures), so this task keeps FTB's completed state once set (FTB semantics: completion is latched) — document that.
Push: `ReputationTierReachedEvent` is only fired on tier crossings, so additionally sweep on `QuestCompletedEvent` and `ProjectEvent.*` (reputation only changes through `ReputationService`, whose callers are those funnels plus situations — all already swept). Poll covers drift.

### 15.4 `mcaquests:reputation_tier` — reach a named tier

Fields: `ladder · String · "mcaquests:default"`, `tier · String · ""` (tier id within the ladder), `village_count · int · 1`.
Check: `count(villages where ReputationService.currentTier(...).indexOf ≥ requiredIndex) ≥ village_count`, using `tierIndex` (§11.4). Unknown ladder/tier → `false` + one-time WARN per book load (and a validate finding).
Push: `ReputationTierReachedEvent`. Editor: `ladder`/`tier` dropdowns from synced ids.

### 15.5 `mcaquests:title` — earn titles

Fields: `title_id · String · ""` (empty = any), `count · long · 1`.
Value: `title_id` set → 1 if `PlayerTitles` holds it (global or any village), else 0; empty → total distinct titles held (global + per-village de-duplicated by id).
Push: `TitleGrantedEvent`.

### 15.6 `mcaquests:project_completed` — village projects completed

Fields: `project_id · String · ""` (empty = any), `count · long · 1`.
Value: from `ProgressionStats.projectCompletions` (§11.2). Push: `ProjectEvent.Completed`.
Note: credit accrues to **online participants at completion time** (§11.2); document.

### 15.7 `mcaquests:project_contribution` — contribute to projects

Fields: `project_id · String · ""`, `count · long · 64` (units: items donated / kills / blocks / talks as banked by project objectives).
Value: from `ProgressionStats.projectContributions`. Push: `ProjectEvent.Contributed`. `formatProgress` shows raw units.

### 15.8 `mcaquests:situation_resolved` — resolve village situations

Fields: `situation_id · String · ""` (SOURCE definition id, e.g. `mcaquests:raiders_at_the_gate`; empty = any), `count · long · 1`.
Value: from `ProgressionStats.situationSuccesses` (successes only — failures/cleared don't count). Push: `SituationResolvedEvent`.

### 15.9 `mcaquests:hearts` — earn a villager's hearts

Fields: `hearts · int · 100`, `spouse_only · boolean · false`.
Check (boolean): `spouse_only` → the player's spouse is loaded nearby and `McaCompat.getHearts ≥ hearts` (or, simpler and acceptable: `isPlayerSpouse` on the best-hearts nearby villager); else `McaCompat.maxHeartsWithin(player, ftbqHeartsScanRadius) ≥ hearts`.
**Performance contract:** the scan runs only from this task's poll (every `ftbqStatePollIntervalTicks`, default 5 s), only for players whose team hasn't completed it, and is bounded by the config radius. No event push exists for hearts (MCA has no such event); the poll is authoritative. Document that hearts must be "witnessed" — the qualifying villager must be loaded near the player at a poll instant (natural in practice: you earn hearts by being near villagers).

### 15.10 `mcaquests:married` — marry a villager

No fields. Check: `McaCompat.isPlayerMarried(player)` (§11.5). Poll-driven; `checkOnLogin` covers weddings that happened before the book unlocked.

---

## 16. FTB Quests Reward Types (namespace `mcaquests:`)

All three: constructor `(long id, Quest quest)`; `getType()`; serialization + `fillConfigGroup` per §15.0 conventions; `claim(ServerPlayer, notify)` bodies wrapped fail-safe (a failed claim logs WARN and messages the player when `notify`); all effects route through existing MCA: Quests funnels so every existing clamp/toggle applies. Early-return no-op (with one-time WARN) when `!isAvailable()`.

### 16.1 `mcaquests:village_reputation` — grant village reputation

Fields: `amount · int · 10` (may be negative ≥ −1000), `target · enum {NEAREST, HIGHEST_REPUTATION} · NEAREST`.
Claim: resolve the target village — `NEAREST`: `McaCompat.findNearestVillageId(level, player.blockPosition(), 128)`; `HIGHEST_REPUTATION`: argmax over `allVillageReputations` (ties → nearest). Found → `ReputationService.award(server, "v:"+id, amount, player)` (tier-ups and toasts fire normally). **Not found → bank it:** enqueue into the existing per-player pending-reward machinery (`ProjectSavedData.pending`) with a new pending kind that re-attempts on login/daily tick until a village resolves; tell the player (*"The nearest village will remember this — visit one to receive your standing."*). Never silently waste a claim.

### 16.2 `mcaquests:hearts` — grant MCA hearts

Fields: `amount · int · 10`, `target · enum {NEAREST_VILLAGER, SPOUSE, VILLAGE_RESIDENTS} · NEAREST_VILLAGER`.
Claim: `NEAREST_VILLAGER` → nearest loaded adult MCA villager within 16 blocks → `McaCompat.addHearts` (which the config multiplier/clamps govern — verify the clamp lives in the reward path; if it lives in `HeartsReward` only, apply the same clamp here explicitly). `SPOUSE` → loaded spouse nearby, else bank (as 16.1). `VILLAGE_RESIDENTS` → `McaCompat.pushVillageHearts` for every resident of the nearest village (works for unloaded residents — verified capability). No target → bank + message.

### 16.3 `mcaquests:grant_title` — grant an MCA: Quests title

Fields: `title_id · String · ""` (required; validated), `scope · enum {GLOBAL, VILLAGE} · GLOBAL`.
Claim: `TitleService.grantGlobal` / `grantVillage(player, nearestVillageId, title)`; VILLAGE with no village → bank. Unknown title id → WARN + player message + no-op (and a validate finding). `TitleGrantedEvent` fires from the funnel, which may in turn complete a `mcaquests:title` task — the loop is safe (monotone, latched) but document it as a feature (title-chains).

---

## 17. MCA: Quests Condition Types (read FTB state)

Registered **always** (in `onCommonSetup` beside the built-ins, via `ConditionTypes.register` — they contain zero FTB imports; evaluation goes through the bridge), so datapacks referencing them validate and load identically whether or not FTB Quests is installed.

Shared shape (all three; codecs in `quest/condition/leaf/`):

```json
{ "type": "mcaquests:ftbq_quest_completed", "quest": "1A2B3C4D5E6F7081", "when_missing": "not_met" }
{ "type": "mcaquests:ftbq_chapter_completed", "chapter": "0123456789ABCDEF", "when_missing": "not_met" }
{ "type": "mcaquests:ftbq_task_completed", "task": "F00DF00DF00DF00D", "when_missing": "met" }
```

- The id field is FTB's 16-hex code string (leading `#` tolerated). Codec-validate the format (regex `#?[0-9a-fA-F]{1,16}`) at load; bad format = the standard lenient-skip/strict-error path, naming quest id and field.
- `when_missing · enum {not_met, met} · not_met` — the result when FTB Quests is absent, the integration is disabled, the id doesn't resolve, or any bridge failure occurs. This one knob lets authors write both "FTB-gated bonus quests" (`not_met`: offer vanishes without FTBQ) and "catch-up quests hidden once the book is done" (combine with `not`).
- `test(QuestContext ctx)` → `FtbqBridge.Holder.get().isQuestCompleted(ctx.player(), quest)` etc.; absent/failed → `when_missing`. Server-side, menu-time evaluation only — same cost profile as every existing condition (no per-tick evaluation).
- Composable everywhere conditions already work: quest `conditions`, `weight_bonus.when`, situation offers, project `unlock`, templates.

## 18. MCA: Quests Objective Type — `mcaquests:ftbq_complete_quest`

```json
{ "type": "mcaquests:ftbq_complete_quest", "quest": "1A2B3C4D5E6F7081",
  "already_complete": "satisfy", "display_name": {"text": "the Ancient Tome chapter"} }
```

- Registered always (no FTB imports; bridge-evaluated). Implements the new `PollingObjective` (§11.3): `poll` sets the objective satisfied when `bridge.isQuestCompleted(player, quest)`. `required() == 1`; `current` = 0/1; `isEventDriven() == false`.
- `already_complete · enum {satisfy, block_offer} · satisfy`: `satisfy` → an already-completed FTB quest satisfies the objective at accept (first poll); `block_offer` → the *loader* auto-wraps the quest's conditions with `not(ftbq_quest_completed)` so the offer is hidden once done (desugaring in `QuestDefinition.effectiveConditions()`, mirroring the chain-prerequisite desugar precedent).
- `display_name` (optional `QuestText`): the objective line — server cannot read FTB quest titles cheaply and localizably at describe-time on every path, so authors name the target; fallback line: *"Complete the linked FTB quest"* + the hex id. `describe()` uses lang key `mcaquests.objective.ftbq_complete_quest`.
- FTB Quests absent at load: `ObjectiveValidator` flags every quest using this objective; under lenient validation the **quest is skipped at load** with a clear log line (an unsatisfiable objective must never enter the offer pool); strict mode errors. If FTB Quests disappears *mid-save* with the quest active, the objective simply never satisfies and the player may abandon (documented; no crash).

## 19. MCA: Quests Reward Type — `mcaquests:ftbq_progress`

```json
{ "type": "mcaquests:ftbq_progress", "action": "complete_task", "id": "F00DF00DF00DF00D" }
```

- `action · enum {complete_task, complete_quest, reset_task}`; `id` hex string (format-validated at load).
- `grant(player, villager)` → `bridge.grantProgress(player, action, id)` — no-op with DEBUG log when unavailable, id unknown, or `allowFtbqProgressRewards` is false (the `describe()` line still renders so authors see it in the card; mirror the `command` reward's disabled behavior).
- Turn-in remains atomic/idempotent: the grant runs inside the existing single reward-claim path; FTB's own `setProgress` is idempotent at max.
- Use cases: villager quest completes an FTB checkmark/gate task; a "village elder blesses your expedition" quest unlocking an FTB chapter via a gate quest.

---

## 20. Editor Experience and the Known-Ids Sync (protocol v5)

**Problem:** `fillConfigGroup` runs on the client, but MCA: Quests' registries (quest/chain/tier/title/project/situation ids) are server-datapack state — a dedicated-server client has none of it.

**Solution:** one small S2C packet.

- **`FtbqEditorIdsS2CPacket`** (network id 15): six `List<String>` payloads — quest ids, chain ids (+ display names), ladder ids, tier ids per default ladder (flattened `ladder|tier`), title ids, project ids, situation ids. Sent to a player on login and after `/mcaquests reload`, only when FTB Quests is loaded server-side **and** `syncFtbqEditorIds` — but **registered unconditionally** (both sides always share the channel; the packet just never flies otherwise). Payload cap: ~64 KB guard, truncate with WARN (a datapack with thousands of quests still syncs the first N; free-text entry always works).
- Client landing: `ClientKnownIds` (plain holder in `client/`, **no FTB imports**) — `volatile ImmutableList`s + `lookupChainName(id)` etc.
- Consumption: the `compat/ftbq/` tasks' `fillConfigGroup` check `ClientKnownIds`; non-empty → `addEnum` with a `NameMap` built from ids **plus a leading "(custom…)" sentinel** that falls back to `addString` behavior (FTB Library pattern: present both — an enum row and a string row, the enum writing into the same field — implement whichever reads cleaner in FTB Library's API, but free-text entry must always remain possible for ids from not-yet-loaded datapacks).
- **`QuestNetwork.PROTOCOL_VERSION` bumps `"4"` → `"5"`.** The strict handshake already rejects mismatched client/server, which is the intended behavior for 1.0.0.
- Server-side validation is authoritative regardless of what the editor wrote: unknown ids surface via `/mcaquests ftbq validate` and one-time WARNs at book load — never crashes, never blocks editing (authors legitimately reference ids from datapacks they haven't written yet).

## 21. Commands (extend `/mcaquests`)

| Command | Perm | Behavior |
|---|---|---|
| `ftbq status` | 2 | Integration state: FTBQ detected? version? bridge active? config master switch? counts from `integrationObjectCounts()` (mcaquests tasks/rewards in the current book), ids-sync on? |
| `ftbq validate` | 3 | Two sweeps. **Book → MCA:** every `mcaquests:*` task/reward in the FTB book whose referenced quest/chain/ladder/tier/title/project/situation id is unknown to the current registries (names the chapter, quest code string, task, field). **MCA → book:** every loaded quest definition using `ftbq_*` conditions / `ftbq_complete_quest` / `ftbq_progress` whose hex id does not resolve (names quest id + field). Exit summary: N errors, M warnings. |
| `ftbq recheck [player]` | 2 | `bridge.recheckAll` for the target (default self). For post-`/ftbquests change_progress` fixups and debugging. |

All three degrade gracefully (clear chat message) when FTBQ is absent or the integration is disabled.

## 22. Localization (add to `assets/mcaquests/lang/en_us.json`)

Complete key inventory the implementation must ship (values are suggestions):

```
# FTB-side type display names (FTB's convention: ftbquests.task/reward.<ns>.<path>)
ftbquests.task.mcaquests.quest_completed        = Villager Quests
ftbquests.task.mcaquests.chain_completed        = Relationship Arc
ftbquests.task.mcaquests.reputation             = Village Reputation
ftbquests.task.mcaquests.reputation_tier        = Reputation Tier
ftbquests.task.mcaquests.title                  = Villager Title
ftbquests.task.mcaquests.project_completed      = Village Project
ftbquests.task.mcaquests.project_contribution   = Project Contribution
ftbquests.task.mcaquests.situation_resolved     = Village Crisis Resolved
ftbquests.task.mcaquests.hearts                 = Villager Hearts
ftbquests.task.mcaquests.married                = Married
ftbquests.reward.mcaquests.village_reputation   = Village Reputation
ftbquests.reward.mcaquests.hearts               = Villager Hearts
ftbquests.reward.mcaquests.grant_title          = Villager Title

# Per-field editor labels: ftbquests.task.mcaquests.<path>.<field> — one per field in §15/§16
# Alt-title sentence templates: mcaquests.ftbq.alt_title.<path> (+ .any / .filtered variants)
# Tooltip lines: mcaquests.ftbq.tooltip.<path>
# MCA-side: mcaquests.condition.ftbq_quest_completed (+chapter/task), mcaquests.objective.ftbq_complete_quest,
#           mcaquests.reward.ftbq_progress (+ .describe.<action>)
# Reward player messages: mcaquests.ftbq.reward.banked, mcaquests.ftbq.reward.no_target, mcaquests.ftbq.reward.disabled
# Command output: mcaquests.command.ftbq.status.*, .validate.*, .recheck.*
```

## 23. Authoring Examples (ship in docs, verify in testing)

**FTB book chapter (SNBT fragment)** — gate a tech chapter on villager standing:

```snbt
{
    title: "A Friend of Oakvale"
    tasks: [{ id: "5F3C0A1B2D4E6F70", type: "mcaquests:reputation_tier", ladder: "mcaquests:default", tier: "friend", village_count: 1 }]
    rewards: [{ id: "70E1D2C3B4A59687", type: "mcaquests:hearts", amount: 15, target: "nearest_villager" }]
}
```

**Villager quest gated on the book** (datapack JSON):

```json
{
  "id": "mypack:archivist_bonus",
  "giver": { "professions": ["minecraft:librarian"], "min_hearts": 20 },
  "dialogue": { "offer": {"text": "You finished the Ancient Tome chapter? Then you're ready for this."}, "...": "..." },
  "conditions": { "all_of": [
      { "type": "mcaquests:ftbq_chapter_completed", "chapter": "0123456789ABCDEF", "when_missing": "not_met" } ] },
  "objectives": [ { "type": "mcaquests:ftbq_complete_quest", "quest": "1A2B3C4D5E6F7081",
      "display_name": {"text": "the Librarian's Challenge"}, "already_complete": "block_offer" } ],
  "rewards": [ { "type": "mcaquests:hearts", "amount": 30 },
               { "type": "mcaquests:ftbq_progress", "action": "complete_task", "id": "F00DF00DF00DF00D" } ]
}
```

---

## Part IV — Robustness and Delivery

---

## 24. Degradation and Compatibility Matrix

| Scenario | Behavior (all verified against source or specified above) |
|---|---|
| FTB Quests absent | `compat.ftbq` never classloads; Noop bridge; `ftbq_*` conditions follow `when_missing`; quests with `ftbq_complete_quest` objectives are skipped at load (validator, §18); `ftbq_progress` rewards no-op with DEBUG log; commands report "not installed"; zero other deltas. |
| FTBQ present, `enableFtbQuestsIntegration=false` | Types still registered (net-sync alignment); tasks never progress; rewards no-op with one-time WARN; conditions follow `when_missing`; commands report "disabled". Book quests using Mca tasks become un-completable — documented server-owner tradeoff. |
| FTBQ present, MCA: Quests removed later | FTB's disk fallback: our tasks/rewards load as inert `"Unknown type: mcaquests:…"` placeholders (no crash). An editor **save** in that state strips our fields — warn pack authors in FTBQUESTS.md to back up `config/ftbquests/quests/`. |
| MCA: Quests present, FTBQ removed later | Datapack `ftbq_*` references degrade per §17/§18/§19; active `ftbq_complete_quest` quests never satisfy (abandonable); world save unaffected (no FTB state is stored in our save data — `ProgressionStats` is FTB-agnostic). |
| FTBQ version drifts binary-incompatibly | `FtbqBootstrap.init()` Throwable guard → one ERROR line, Noop bridge, game runs (§10.4). Individual bridge calls are also fail-safe (§10.2), covering post-init drift. |
| Client/server mod mismatch (one side lacks MCA: Quests) | Unchanged from today: mcaquests is a both-sides mod; Forge's mod-list handshake plus channel version `"5"` reject the connection. |
| Player has no FTB team | Impossible in practice — FTB Teams auto-creates a player team; `getOrCreateTeamData` always resolves. Bridge still null-guards. |
| Team merge / leave (party mechanics) | FTB's own `mergeData`/`copyData` rules apply. Monotone recompute makes our tasks self-heal on the next poll/push (§14.1). |
| `/ftbquests change_progress` reset | Stateful/counter tasks re-complete on next evaluation unless underlying MCA state was also reset — documented; `ftbq recheck` makes it immediate. |
| `/mcaquests reload` | Registry swap → `recheckAll` online players + re-send editor ids (§12.6). |
| Situation synthetic ids | `quest_completed` counts them via resolved offer definitions; `situation_resolved` targets them by SOURCE id (§15.1, §15.8). |
| Dedicated server, LAN, single-player | No side-specific code paths beyond what's specified; editor ids sync covers the dedicated case; integrated server reads the same server-side registries. |

## 25. Performance Budget

1. Stateful task polls: O(1) map/set lookups against in-memory MCA: Quests state — except `mcaquests:hearts`, whose bounded entity scan is explicitly budgeted (§15.9) at one `getEntitiesOfClass` per player per 5 s default, radius-capped, skipped once completed.
2. Event sweeps: O(tasks-of-that-type-in-book) with cached type lists (§12.3), only on actual MCA lifecycle events (rare by definition — quest turn-ins, tier-ups).
3. Counter recomputes: O(history entries) worst case for `quest_completed` with filters; `QuestHistory` is per-player and modest (hundreds). Cache the per-player match count per (task, player) pair invalidated on `QuestCompletedEvent` if profiling ever demands it — do not pre-build this cache in v1.
4. Editor ids packet: on login + reload only; ≤64 KB guarded.
5. Zero per-tick work when FTBQ absent (Noop bridge, no registered listeners) and near-zero when present-but-disabled (poll early-returns on `isAvailable`).

## 26. Safety and Anti-Exploit Rules

1. All progress writes are server-side, inside FTB's own guarded paths (`canStartTasks`, `isLocked`, `withPlayerContext`); the client never submits our stateful tasks directly (`onButtonClicked` default is acceptable because `submitTask` re-validates via `canSubmit` server-side — packet-spamming `SubmitTaskMessage` cannot fabricate state).
2. Mca reward claims route through existing funnels: hearts clamps (`heartsRewardMultiplier`/min/max) apply to §16.2 exactly as to the native hearts reward; reputation through `ReputationService` (tier/high-water consistency); titles through `TitleService` (dedup).
3. `ftbq_progress` is config-gated (§13) and can only touch the claiming team's own progress.
4. Banked rewards (§16.1/16.2) persist in world save (`pending`) — idempotent single-delivery like existing project pending rewards.
5. No new C2S packets — attack surface unchanged (the one new packet is S2C).

## 27. File-by-File Implementation Plan

```text
src/main/java/dev/otectus/mcaquests/
  McaQuests.java                              [edit] bootstrap guard (§10.4); bump protocol usage unchanged
  McaQuestsConfig.java                        [edit] [compat.ftbquests] block (§13)
  api/
    PollingObjective.java                     [new]  §11.3
    event/SituationResolvedEvent.java         [new]  §11.1
    event/ReputationTierReachedEvent.java     [new]  §11.1
    event/TitleGrantedEvent.java              [new]  §11.1
    event/ProjectEvent.java                   [edit] + Contributed subclass
  state/
    ProgressionStats.java                     [new]  §11.2
    PlayerQuestData.java                      [edit] hold + persist ProgressionStats
  quest/reputation/ReputationService.java     [edit] read accessors (§11.4) + post TierReachedEvent
  quest/title/TitleService.java               [edit] post TitleGrantedEvent
  quest/situation/SituationManager.java       [edit] post SituationResolvedEvent; feed ProgressionStats
  project/ProjectManager.java                 [edit] post Contributed; feed ProgressionStats
  event/QuestProgressEvents.java              [edit] generic PollingObjective pass (§11.3)
  quest/condition/leaf/FtbqQuestCompletedCondition.java    [new] §17
  quest/condition/leaf/FtbqChapterCompletedCondition.java  [new] §17
  quest/condition/leaf/FtbqTaskCompletedCondition.java     [new] §17
  quest/condition/ConditionTypes.java         [edit] register the three
  quest/objective/FtbqCompleteQuestObjective.java          [new] §18
  quest/objective/ObjectiveTypes.java         [edit] register
  quest/reward/FtbqProgressReward.java        [new]  §19
  quest/reward/RewardTypes.java               [edit] register
  data/ObjectiveValidator.java                [edit] ftbq objective absent-mod rule (§18)
  data/ConditionRefs.java / validators        [edit] hex-format checks, cross-ref warnings
  network/QuestNetwork.java                   [edit] PROTOCOL_VERSION "5"; register packet 15
  network/FtbqEditorIdsS2CPacket.java         [new]  §20
  client/ClientKnownIds.java                  [new]  §20 (no FTB imports)
  command/McaQuestsCommand.java               [edit] ftbq status|validate|recheck (§21)
  compat/
    FtbqBridge.java                           [new]  §10.1 (interface + Holder + Noop)
  compat/ftbq/                                [new package — the ONLY FTB-importing code]
    FtbqBootstrap.java                        §10.3
    FtbqBridgeImpl.java                       §10.2
    FtbqTaskTypes.java / FtbqRewardTypes.java §15/§16 registration
    McaBooleanTaskBase.java / McaCounterTaskBase.java / QuestFilter.java   §15.0
    McaQuestCompletedTask.java … McaMarriedTask.java   (10 tasks, §15.1–15.10)
    McaVillageReputationReward.java / McaHeartsReward.java / McaGrantTitleReward.java  §16
    FtbqEventBridge.java                      §15.0
  compat/McaCompat.java                       [edit] isPlayerMarried, maxHeartsWithin (§11.5)

src/main/resources/assets/mcaquests/lang/en_us.json   [edit] §22
src/test/java/dev/otectus/mcaquests/…                 [new tests] §29.1
gradle.properties / build.gradle / META-INF/mods.toml [edit] §9
README.md / CONFIG.md / DATAPACK.md / CHANGELOG.md / FTBQUESTS.md [edit/new] §28
```

## 28. Documentation Updates

1. **New `FTBQUESTS.md`** (root, linked from README): pack-author guide — installing, every task/reward type with fields + SNBT examples, every condition/objective/reward with JSON examples, team-credit semantics (§14 verbatim, in author language), degradation matrix summary, the editor-save-strips-unknown-types backup warning, `/mcaquests ftbq` commands, FAQ (resets, retroactivity, offline credit).
2. **README.md**: feature bullet; Requirements table row (*FTB Quests — optional, 2001.4.x*); Compatibility-note paragraph (bridge pattern, compileOnly/no-shipping licensing sentence, tested version).
3. **CONFIG.md**: the `[compat.ftbquests]` table (§13).
4. **DATAPACK.md**: the three conditions, the objective, the reward, with the §23 example.
5. **CHANGELOG.md**: `## [1.0.0]` entry following house format — Added (integration + core API additions listed separately), Changed (protocol v4→v5, matching-version requirement), Compatibility (fully save-compatible; `ProgressionStats` loads empty on old saves; older clients rejected by handshake).
6. **CURSEFORGE_DESCRIPTION.md**: one feature line.

## 29. Testing Requirements

### 29.1 Unit tests (JUnit, no Minecraft/FTB classpath — extend the existing 19-test suite's style)

1. `FtbqIdParsingTest` — hex-format regex accept/reject table (with/without `#`, short, long, invalid chars). (Bridge-side `parseCodeString` is FTB's; we test *our* validator.)
2. `QuestFilterTest` — id/wildcard/profession/chain/category matching incl. synthetic-situation resolution stubs and missing-definition fallback.
3. `ChainFinalStageTest` — final-stage detection: stage_total present, absent+unlocks-empty, branching multi-final arcs.
4. `ProgressionStatsCodecTest` — NBT round-trip, absent-key back-compat, increment semantics.
5. `TierIndexTest` — `ReputationService.tierIndex` pure function across ladder edges (§11.4).
6. `FtbqConditionPolicyTest` — `when_missing` truth table via a stubbed `FtbqBridge` (the seam makes this trivial — that's the point).
7. `NoFtbqClassloadTest` — reflection walk asserting no class outside `compat.ftbq` references `dev.ftb.mods` (readily done by scanning constant pools of compiled classes, or minimally: asserting `FtbqBridge`/conditions/objective/reward classes load with FTB jars absent from the test classpath — which they are).
8. `McaCompatSafeFailTest` — extend for `isPlayerMarried` / `maxHeartsWithin` null-safety.
9. `EditorIdsPacketCodecTest` — encode/decode round-trip + 64 KB truncation.

### 29.2 Manual test matrix (production-style instance; document results in the PR)

| # | Setup | Verify |
|---|---|---|
| 1 | MCA+MCAQ only (no FTBQ) | Boots clean; no `compat.ftbq` classload (add temporary trace); `ftbq_*` datapack quest with `when_missing:met` appears, `not_met` hidden; `ftbq_complete_quest` quest skipped with log; `/mcaquests ftbq status` says not installed. |
| 2 | + FTBQ/FTBLib/FTBTeams, single-player | Editor lists all 10 tasks + 3 rewards with names/icons; create each, configure (dropdowns populated), save, relaunch, fields persist (SNBT inspect). |
| 3 | Play-through, SP | Complete a fletcher quest → `quest_completed(any)` ticks; profession filter counts correctly incl. a pre-existing completion at book-unlock; finish an arc → `chain_completed`; earn tier → `reputation_tier` + `reputation` bar; title task; donate to a project → contribution counter; resolve a situation → situation task; hearts task completes near beloved villager within one poll; marry → married task. |
| 4 | Rewards, SP | Claim all three near a village (effects + toasts); claim `village_reputation` in the wilderness → banked message → enters village → delivered once. |
| 5 | Dedicated server, 2 players, same FTB party | Player A completes villager quest → task completes for team; B sees progress; counter task shows max(A,B) not sum; `ftbq_complete_quest` objective satisfies for B's active villager quest when A finishes the FTB quest (documented team semantics). |
| 6 | Dedicated, editor ids | Non-op vs op; dropdowns on dedicated client populated post-login; `/mcaquests reload` refreshes them. |
| 7 | Admin ops | `/ftbquests change_progress reset` → task re-completes next poll; `/mcaquests ftbq recheck` immediate; `/mcaquests ftbq validate` catches a book task with a bogus quest id AND a datapack condition with a bogus hex. |
| 8 | Degradation | World from #3, remove MCAQ → book shows Unknown-type placeholders, no crash; restore MCAQ → tasks work again (fields intact absent an editor save). Remove FTBQ instead → MCAQ world loads, `ftbq` references degrade per policy. |
| 9 | Config | `enableFtbQuestsIntegration=false` → tasks inert, rewards WARN-no-op, conditions follow policy; toggle back → recheck heals. |
| 10 | Handshake | 0.9.0 client vs 1.0.0 server → rejected (protocol 5). |

## 30. Implementation Milestones

- **M0 — Plumbing (§9–§10):** gradle/mods.toml, bridge interface + Noop + bootstrap + guard, empty impl. *Gate: matrix #1 passes; NoFtbqClassloadTest green.*
- **M1 — Core API (§11):** events, ProgressionStats, PollingObjective, accessors, McaCompat additions, all unit tests. *Gate: full unit suite green with no FTB jars on the test classpath.*
- **M2 — FTB tasks (§15):** bases, ten tasks, event bridge, caching. *Gate: matrix #2–#3.*
- **M3 — FTB rewards (§16) + banking.** *Gate: matrix #4.*
- **M4 — MCA-side types (§17–§19) + validators.** *Gate: datapack examples from §23 load and run.*
- **M5 — Editor ids sync + commands (§20–§21), protocol bump.** *Gate: matrix #6–#7.*
- **M6 — Polish & release (§22, §28, §29):** lang, docs, changelog, version 1.0.0, full matrix. *Gate: §31 acceptance criteria.*

## 31. Acceptance Criteria

1. Matrix #1 (FTBQ absent) shows **zero behavioral delta** from 0.9.0 apart from the new core API/events and version/protocol bump.
2. All ten task types and three reward types are creatable, configurable, persistent, syncing, and completable in a production-style dedicated-server test (matrix #2–#5).
3. All five MCA-side types load, validate, evaluate, and degrade exactly per §17–§19.
4. No class outside `compat/ftbq` references `dev.ftb.mods.*` (test-enforced); the shipped jar contains no FTB bytecode (inspect).
5. Every bridge method and reward claim is Throwable-guarded; a deliberately-sabotaged bridge (throwing impl in a test harness) produces zero crashes across the matrix flows.
6. Unit suite green; manual matrix documented; docs (§28) complete; CHANGELOG accurate; `mod_version=1.0.0`; protocol `"5"`.

## 32. Key Engineering Risks

1. **FTB internals drift** (their explicit non-guarantee): mitigated by the single-package seam, per-call Throwable guards, init guard, `[2001.4,)` + documented tested version, and CI compiling against the pinned artifact. Worst case is a disabled integration, never a crash.
2. **Per-team semantics surprising players** ("my friend finished my villager task"): mitigated by loud documentation (§14, FTBQUESTS.md) — not by fighting FTB's model.
3. **Monotone recompute vs. admin resets** (tasks self-re-completing): intended and documented; `reset_task` + MCA-state reset is the admin path.
4. **Hearts-task scan cost on crowded servers**: bounded radius + interval + completed-skip (§15.9); profile in matrix #5.
5. **MCA dev-runtime limitation** (MCA mixins don't load in dev): FTB-editor work is testable in dev via `enableFtbqInDev`; MCA-touching flows verified in the production-style instance as the project already does.
6. **License posture** (GPL-3.0 mod compiling against ARR visible-source): constrained to compileOnly/no-shipping/no-copying (§8); final judgment flagged to the author.
7. **Protocol bump forcing lockstep updates**: standard for this mod (v2, v3, v4 precedents); called out in the changelog.

## 33. Future Ideas (explicitly out of scope for 1.0.0)

Baseline-vs-retroactive toggle for counter tasks ("complete 5 *more* quests"); offline-participant credit queues for `ProgressionStats`; an FTB task for *active* (not completed) villager quest state; Jade/WAILA tooltips on quest-giver villagers showing FTB-gated offers; a `#tag`-based FTB object addressing option in MCA-side types; NeoForge/1.21 port of the seam.

## 34. Final Instruction to the Coding Agent

Implement this specification completely and in milestone order (§30). Where this spec cites an FTB Quests or MCA: Quests identifier, verify it against the actual source before use and prefer the source if they disagree — then note the deviation in the PR description. Do not invent additional FTB-facing features beyond §15–§21, and do not remove or weaken any of the seven invariants in §4 — in particular: registration is never config-gated, no FTB import escapes `compat/ftbq`, every cross-mod call is fail-safe, and FTB Quests' absence must be behaviorally invisible. When a detail is genuinely unspecified, choose the option that keeps the core mod byte-identical in the FTBQ-absent case and document the choice. Ship it as MCA: Quests v1.0.0.
