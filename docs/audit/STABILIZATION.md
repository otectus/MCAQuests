# 1.7.0 verification — 2026-09-22

This section records what was run for 1.7.0 on the Forge 1.20.1 project (`MCAQuests`) and the NeoForge
1.21.1 port (`1.21.1 Ports/MCAQuests_1.21.1`). It replaces the manual lists of the 2026-09-06 report
below for everything it covers; the checks still left to a person are listed at the end. Commands use
`/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <project> <task>`; on Forge every Gradle call also
passed `-Dnet.minecraftforge.gradle.check.certs=false`, because ForgeGradle's certificate check hung once.

## Build and unit tests

| Loader | Task | Result |
|---|---|---|
| Forge | `build` | PASS — 1,486 tests, 0 failed, 16 skipped. `verifyReobfJar`: metadata, isolated classes, Java 17, 2,728 SRG member references, Townstead binding `typed`. `verifyApiJar`: 195 classes, no resources. |
| NeoForge | `build` | PASS — 1,524 tests, 0 failed, 19 skipped. `jarSmokeCheck`: 960 isolated classes, Java 21, Townstead binding `typed`. `verifyApiJar`: 105 exports, no resources. |

| Artifact | SHA-256 |
|---|---|
| Forge `build/libs/mcaquests-1.7.0.jar` | `db2a9eef699dda7c49c47b2ee9a1388e4f02eb6fe338cb003fedebe1967f36d9` |
| Forge `build/libs/mcaquests-1.7.0-api.jar` | `0605bec460db332ecbdb08e8bb8a01835c85d77e7a95ccc78268b031f726a60b` |
| NeoForge `build/libs/mcaquests-1.7.0.jar` | `207ee88c30948e427d7c3faaebe2b0b2ef0152d05817abfc858994723c900923` |
| NeoForge `build/libs/mcaquests-1.7.0-api.jar` | `0577ae87938120c508346a274d33e35255d51a054084e272ec308de4fae28293` |

`ApiJarClosureTest` was also checked against a deliberate break: removing `QuestContext` from
`apiReadModel` fails it, naming `QuestDefinition` and `WeightBonus` as the signatures that reach it.

## Binding probes against real jars

| Loader | Probe | Jar(s) | Result |
|---|---|---|---|
| Forge | `McaBindingProbeTest` (in `test`) | MCA 7.6.20, 7.7.0-beta.2, 7.7.1-alpha.2, 7.7.1-beta.1, 7.7.1-beta.2 | All resolve: `forge.net.mca` for the first two, `forge.net.conczin.mca` for the three 7.7.1 builds |
| Forge | `javap` of the two MCA hook targets | MCA 7.7.1-beta.2 | `VillagerCommandHandler.handle(ServerPlayer,String)Z` and `EntityCommandHandler.interactAt(Player,Vec3,InteractionHand)InteractionResult` keep the exact descriptors the plugin requires |
| Forge | `townsteadProbeTest` | Townstead 0.7.6; 0.7.7 legacy (MCA 7.6.20); 0.7.7 modern (`-PmcaDevVersion=7.7.1-beta.2+1.20.1`) | PASS, 4 tests, 0 skipped: bound, 15 capabilities; roots `forge.net.mca`, `forge.net.mca`, `forge.net.conczin.mca` |
| Forge | `townsteadProbeTest` | Townstead 0.8.0 built from `0.8-alpha-2` head `aaa558a0` | Reflective manifest unresolved: `villager.ProfessionXpType#values/0` and `#id/0` (0.8 removed the enum). Expected — with `api.v1` present only the typed bridge binds. Its 108 `api.v1` classes are `javap -public -s` identical to the pinned API jar the adapter compiles against |
| Forge | `capitalsProbeTest` | MCA Capitals 1.3.5, 1.3.6, 1.3.7 | PASS each, 9 capabilities |
| Forge | `mapProbeTest -PrequireMapJars=true` | JourneyMap 1.20.1-6.0.5, Xaero's Minimap 26.5.0 | PASS, 3 tests |
| Forge | `iceAndFireProbeTest` | Ice & Fire 2.1.13-beta-5, Ice & Fire CE 1.2.9 | PASS, 2 tests |
| Forge | `bountifulProbeTest` | Bountiful 6.0.4 | PASS, 5 tests |
| Forge | `mapAtlasesProbeTest` | Map Atlases 1.20-6.0.20, Moonlight 1.20-2.16.35 | PASS |
| NeoForge | `townsteadProbeTest` | Townstead 0.7.6, 0.7.7 (NeoForge) | PASS each, 15 capabilities, root `net.conczin.mca` |
| NeoForge | `capitalsProbeTest` | MCA Capitals 1.3.5 (NeoForge) | PASS, 9 capabilities |
| NeoForge | `mapAtlasesProbeTest` | Map Atlases 1.21-6.7.3, Moonlight 1.21.1-3.6.3 | PASS |
| NeoForge | `mapProbeTest -PrequireMapJars=true` | JourneyMap 1.21.1-6.0.9, Xaero's Minimap 26.5.0 (downloaded from Modrinth) | PASS, 3 tests |
| NeoForge | `bountifulProbeTest` | Bountiful 8.0.0-beta.2 (downloaded from Modrinth) | PASS, 8 tests |
| NeoForge | `iceAndFireProbeTest` | — | Not run: no Ice & Fire CE 1.21.1 jar is obtainable without CurseForge API access |

## Production dedicated servers

The reliability fixture (`tools/reliability-runtime-test/`, built with `reliabilityRuntimeTestJar
-PreliabilityRuntimeFixture=true`, never shipped) drives real MCA villagers with a fake player through the
calls the network handler makes, 100 ticks after start:

- **TALK** — two conversations credited once each (quest and project), a sneak-click and a repeat not.
- **PLACE** — project placement inside the area counts, 40 blocks out does not, a re-placed spot once.
- **REPAIR** — an instance-targeted repair moves only its instance and refuses a reused token.
- **FARM** — personal `place_block` counts a position once and `break_block` ignores the player's own
  blocks: expected place 1, 1, 2 and break 0, 0, 1.
- **HELD** — a reward that throws during a real `QuestManager.turnIn`: the quest completes, the other
  reward pays, the failed one is held, survives a save, fails its retry and is dismissed.
- **GONE** — `McaCompat.villageKnown` on real MCA answers `Optional[false]` for village 424242, the instance
  is detected, and `rebind anchor` moves it with its progress.
- **RECEIPT** — a subscribed completion is captured, fenced through `PlayerList#save` and the reread
  player file, delivered once with one ready event, acknowledged and drained.
- **RELOAD** — `/reload` completes, the null-player datapack sync fires, the registry size is unchanged
  and the outage ledger stays attached.

Forge rows ran Forge 47.4.23 with Architectury 9.2.14; NeoForge rows ran NeoForge 21.1.250 with MCA
7.7.36-beta.3. Every row below passed all eight scenarios. The rows ran before the guidance change under
*Findings* 7; after it, the base row was run again on each loader with the final jars and passed all
eight plus GUIDANCE:

| Loader | Row | Mods beside MCA: Quests | Loaded quests / projects / situations (fixture adds 5 / 3 / 0) |
|---|---|---|---|
| Forge | base | MCA 7.6.26 | 194 / 13 / 11 |
| Forge | mca771b2 | MCA 7.7.1-beta.2 | 194 / 13 / 11 |
| Forge | townstead-076 | MCA 7.6.26, Townstead 0.7.6, Patchouli 85 | 267 / 24 / 25 |
| Forge | townstead-077-legacy | MCA 7.6.26, Townstead 0.7.7 legacy, Patchouli 85 | 267 / 24 / 25 |
| Forge | townstead-077-modern | MCA 7.7.1-beta.2, Townstead 0.7.7 modern, Patchouli 85 | 267 / 24 / 25 |
| Forge | townstead-080 | MCA 7.7.1-beta.2, Townstead 0.8.0 (test build, see *Findings*) | 267 / 24 / 25 |
| Forge | capitals | MCA 7.7.1-beta.2, Capitals 1.3.7 | 202 / 13 / 13 |
| Forge | both | MCA 7.7.1-beta.2, Townstead 0.7.7 modern, Patchouli 85, Capitals 1.3.7 | 275 / 24 / 27 |
| Forge | conversations | MCA 7.6.26, MCA: Conversations 1.8.0 | 194 / 13 / 11 |
| NeoForge | base | — | 194 / 13 / 11 |
| NeoForge | townstead-076, ts-076 | Townstead 0.7.6, Patchouli 93 | 267 / 24 / 25 |
| NeoForge | townstead-077, ts-077 | Townstead 0.7.7, Patchouli 93 | 267 / 24 / 25 |
| NeoForge | capitals | Capitals 1.3.5 | 202 / 13 / 13 |
| NeoForge | both | Townstead 0.7.7, Patchouli 93, Capitals 1.3.5 | 275 / 24 / 27 |
| NeoForge | conversations | MCA: Conversations 1.8.0 | 194 / 13 / 11 |

**Townstead bridge fixture** (`tools/townstead-runtime-test/`, #1, ported to NeoForge for 1.7.0) shared
the Townstead boots with `-Dmcaquests.rt.haltAt=800`:

| Loader | Townstead | Binding | Result |
|---|---|---|---|
| Forge | 0.7.6, 0.7.7 legacy | reflective, `forge.net.mca` | `FULL` 15/15; villager read; hunger −10 and fatigue→3 applied; farmer +25 XP, a +100,000 request capped to 215 (tier 1→2), a further +25 refused `DAILY_CAP`; unknown skill `FEATURE_GATED`; no known skill in the registry |
| Forge | 0.7.7 modern | reflective, `forge.net.conczin.mca` | As above |
| Forge | 0.8.0 test build | `api-v1`, variant `api-v1-r4` | `FULL` 15/15; event feed subscribed; hunger and fatigue applied, thirst `FEATURE_GATED` (gated off in that build); the same XP results; a known skill (`townstead:scribe/long_ledger`) learned, re-learned as `NO_CHANGE`, forgotten, re-forgotten as `NO_CHANGE`; unknown skill `INVALID_VALUE` |
| NeoForge | 0.7.6, 0.7.7 | reflective, `net.conczin.mca` | As the Forge 0.7.x rows |

On every build the "energy → 0" push applied but the villager was not collapsed 400 ticks later, and the
fixture saw no collapse event; that is recorded, not asserted.

**Restart row** — three boots of one world (`-Dmcaquests.rt.phase=restart1..3`), MCA 7.6.26 on Forge and
7.7.36 on NeoForge, identical results on both after the fix under *Findings*:

| Boot | Mods | Result |
|---|---|---|
| 1 | Townstead 0.7.6 | Four MCA villagers: V and W held for an owner who never logs in (W was already `NoAI` from "another mod"), X held then queued for release, Y frozen the pre-1.7.0 way (no marker, no lease). `rt_drift` opened with fingerprints and progress [2,0]. Townstead content loaded, not covered by the outage ledger. PASS |
| 2 | Townstead removed; a world datapack reorders `rt_drift`'s objectives | ESCORT-RESTART PASS: V and W still held with leases, X released on load, Y untouched. DRIFT PASS: the reordered phase is detected and a stone-brick and a brick placement credit nothing. OUTAGE-OPEN PASS: Townstead quests and projects excluded, the ledger covering them |
| 3 | Townstead restored | OUTAGE PASS: the outage closed; a quest accepted in boot 1 is credited exactly 100 ticks, boot 2's length, by `QuestProgressEvents.creditOutage`. ESCORT-RELEASE PASS: V moving and vulnerable, W back to `NoAI` but vulnerable, Y freed by `/mcaquests escort release`. REBASE PASS: the phase is accepted, counts stay [2,0], and a brick then credits objective 1 |

## Guidance cost (F17)

The fixture's GUIDANCE scenario gives one fake player ten bundled quests whose objectives name a place
or a villager (and, as further players, the same ten again and the eight base-install quests with a block
`source` hint) and times twenty `GuidanceService.snapshot` walks each — right after acceptance, and again
280 ticks later. It ran on a normal world with structures (`build/rt/guidance-row.sh`), MCA 7.6.26:

| Build | First walk (cold JVM) | Settled | New player's first walk (warm) | Block-source quests |
|---|---|---|---|---|
| Before | 167 ms | max 0.7–1.2 ms | 139 ms | first 22 ms, then up to 13.9 ms, average 3.5 ms |
| After (Forge) | 11.5 ms | max 0.9 ms | 0.5 ms | warm player: first 0.4 ms, max 0.4 ms |
| After (NeoForge) | 24.5 ms | max 1.7 ms | 1.0 ms | warm player: first 0.8 ms, max 1.1 ms |

The 139–167 ms walk was one synchronous biome search (`adventurer_trailblazer`'s `visit_biome`) under the
per-pass budget; the block-source cost was one 48-block scan per pass. Both now run on the server-wide
search queue (see *Fixed — guidance hitches* in the CHANGELOG), which is why a warm walk stays under
2 ms. The first walk down a code path still pays for class loading and JIT, so the scenario reports it
without judging it. The scenario also passes on the flat worlds of each loader's final base row.

## Production clients

`client_smoke.py` (a scratch launcher) started the installed CurseForge Forge 47.4.23 and NeoForge
21.1.250 clients with MCA: Quests 1.7.0 and MCA (plus Architectury on Forge), quick-played a copy of a
fixture world, and stopped the client 45 seconds after the player joined. Both clients bound MCA, loaded
their content, passed the network handshake, joined the integrated server and saved on stop. The only
ERROR in either log is the narrator's missing `libflite`, from this machine.

## Ultima Kingdoms acceptance (Forge)

Ultima's `tools/test/integration_runtime.py`, run from a scratch copy whose `build/libs` Ultima jar differs
from Ultima's own only in `mods.toml`'s MCA: Quests range, widened to `[1.6.5,1.8)` as Ultima's
`gradle.properties` now says (the installed Ultima jar still declares `[1.6.5,1.7)` and refuses 1.7.0):

| Phase | Mods | Result |
|---|---|---|
| `civic-loop` | MCA: Quests 1.7.0, MCA: Conversations 1.8.0, MCA 7.6.26, Architectury, Townstead 0.7.6, FireSticks, Patchouli | PASS, and PASS after restart |
| `r2` | MCA: Quests 1.7.0, the R2 provider `mcacrime-0.7.5.jar` (`e851ed0c…`), MCA 7.6.26, Architectury, Townstead 0.7.6, Patchouli | PASS, and PASS after restart: native payment receipt, honor and hospitality knowledge |
| `r4-service` | Recruits 1.15.2, MCA, Architectury, MCA: Quests 1.7.0, MCA Crime, GeckoLib, Townstead, Patchouli | PASS: real scoped delivery and reward, voluntary obligation, invalid scope and duplicate denied |
| `r3` | as `r4-service` | PASS through `r3`, `r3-restart`, `r3-absent`, `r3-reinstalled` and `r3-future` |

## Findings

1. **Escort leases were written empty at shutdown (fixed before release).** `EscortHoldRegistry.detach()`
   ran on `ServerStoppingEvent`, which fires before the final world save, so the lease file was saved empty
   and the next boot released every held villager as an orphan. The restart row caught it. The registry is
   now detached on `ServerStoppedEvent`, and `attach()` is idempotent per server and runs on the first
   entity join, so a spawn-chunk villager is never judged against an empty registry. Both loaders.
2. **Townstead 0.8 (`0.8-alpha-2`, unreleased) cannot start a Forge dedicated server.**
   `client.catalog.CatalogDataLoader.apply` calls `RequirementNameResolver.invalidate()`, a class that
   imports `net.minecraft.client.Minecraft`, during the server's datapack load. The 0.8.0 test build used
   above wraps that one call; the typed-bridge results come from it. Upstream.
3. **Townstead 0.8's NeoForge build requires MCA 7.7.37**, newer than any published NeoForge MCA
   (7.7.36-beta.3), so no NeoForge 0.8 row could run. Upstream.
4. **MCA: Conversations 1.8.0 declares its optional Townstead dependency as `[0.7.5,0.8)`**, so Forge will
   refuse Conversations beside Townstead 0.8. Conversations' manifest, not this mod's.
5. **The current MCACrime build (`mcacrime-0.7.5.jar`, `a294180d…`) fails Ultima's R2 check** "unreported
   local theft does not become institutional knowledge"; the R2 provider build of the same version passes.
   MCACrime and Ultima, not this mod.
6. **Ultima Kingdoms' installed jar refuses MCA: Quests 1.7.0** until it is rebuilt with the widened range,
   so the Ultima CurseForge instance was left on 1.6.6.
7. **Guidance could stall the server for 140–170 ms** (F17, fixed before release): see *Guidance cost*.

## Still manual

- Client UI at GUI scales 1–4: the followed quest past the tracker limit with its "+N more" line, project
  Details and **Show build area**, an open Quests menu refreshing on `/reload`, and `pt_br`.
- Map Atlases drawing external points from Ultima, and the atlas integration generally, in a real client.
- MCA's Gift gesture delivering to a quest with a real player (DELIVERY-01).
- A real player's login crediting an outage on an accepted quest end to end (the restart row calls the
  crediting method directly).
- The Townstead checklist items that need a client or a day boundary (TOWNSTEAD.md scenarios 5, 7–13).
- NeoForge: Townstead 0.8 through `api.v1` at runtime, Ice & Fire CE, and a 1.20.1 world upgraded end to end.

## Installed

`installMod -PmodsDir=…` copied each verified jar, and the 1.6.6 jar it replaced was moved to the session
scratchpad (`replaced-jars/`), not deleted:

| Instance | Loader | Jar | SHA-256 |
|---|---|---|---|
| Towns & Dragons (1) | Forge 47.4.23 | `mcaquests-1.7.0.jar` | `db2a9eef…7f36d9` |
| NeoForge Testing Grounds | NeoForge 21.1.250 | `mcaquests-1.7.0.jar` | `207ee88c…900923` |

# Stabilization and refinement — 2026-09-06

This report covers the implemented pass in the Forge 1.20.1 project and the separately built
NeoForge 1.21.1 port. Existing workspace changes, including the Capitals and structure-guidance
work, were retained and reviewed as part of the integrated result. No world saves or installed
game instances were edited. This is a source, regression, resource, and artifact audit; it does
not claim that automated checks prove every possible modpack interaction.

## Coverage

| Area | Paths inspected and verified |
|---|---|
| Quest engine | Selection, eligibility, acceptance, decline, abandon, completion/failure, repeats and chains; every registered objective/reward/condition/target/template/trigger family; event and polling detectors; inventory handovers; situations; guidance; external signals and dialogue hooks |
| Projects and persistence | All scope/sponsor paths, phases/unlocks/follow-ups, contributions, failure/retry/pause clocks, online/offline/shared rewards, clone/login/logout, capabilities/attachments, saved data, malformed and missing-content recovery, reputation/title migration |
| Data and resources | All reload listeners and validators, nested codecs, schema/example exports, packaged datapacks and compatibility packs, text/locales, texture references and image decode, mixin/pack/mod metadata |
| Client and network | Every packet registration and decoder, C2S admission and authorization, menus/journal/log/HUD, keybinds, scroll/wrapping, delayed replies, guidance mirrors/markers, waypoint reconciliation, client mixins and logout lifecycle |
| Commands and configuration | Command branches, permission/player/dimension handling, diagnostics/reload, numeric/limit/reward switches and optional integration gates |
| Public API and integrations | Registration/event/dialogue/external-objective APIs; MCA, FTB Quests, Reputation, Townstead, Capitals, Bountiful, Ice & Fire, JourneyMap, Xaero and currency providers; presence/partial-failure/static-link checks |
| Build and documentation | Both Gradle projects, wrappers/toolchains, dependencies and metadata, test/probe tasks, artifact validation, install ordering, source-build prerequisites and authored format/reference documents |

The [engine/state report](STABILIZATION_ENGINE.md), [client/network report](STABILIZATION_CLIENT_NETWORK.md) and
[compatibility report](STABILIZATION_COMPAT.md) give the detailed findings for those workstreams.

## Implemented changes

- **Datapacks cannot silently lose requirements.** Loaders reject partial definitions and isolate
  broken add-on codecs. Malformed present optional fields fail throughout nested models; omitted
  fields retain their documented defaults. Unknown concrete item/block IDs cannot become air.
  Quarantine keeps explicit IDs as well as filenames, and a successful definition is not left
  falsely quarantined by another malformed alias. Strict reload failure preserves the previous
  catalogue. Composite negation validation follows required branches correctly; deep dependency
  graphs use heap traversal. Template ranges, scaling and weights avoid numeric wraparound.
- **Rewards and progress are bounded and durable.** Built-in handovers reserve combined source
  items and destination capacity before consuming anything, including tag matches and stack
  metadata. Progress and reward arithmetic clamp safely. Large item/currency payouts have a
  shared per-player delivery budget and a persistent exact remainder; dead players and full
  creative inventories cannot consume queued debt. Queued item scans are bounded and rotate
  past unavailable items. Project command rewards respect their explicit configuration gate.
- **Projects enforce their authored rules.** Limits, sponsors, unlocks and contribution caps
  are checked server-side. Deadlines, weather/sponsor failure, retry policy, pause offsets,
  failure hearts/reputation/events and localized notifications now run. Follow-ups require
  matching scopes. Offline payouts retain the original instance, snapshot and anchor dimension;
  missing/corrupt entries survive without discarding later rewards. Clone/save copies do not
  share mutable state. Canceled events do not count as successful actions or deaths.
- **Networking and client state resist stale or hostile input.** Forge message directions are
  explicit, impossible collection lengths fail before large allocations, and requests have a
  per-player rate budget. Valid large collections retain their wire representation. Delayed
  menus, scrolling, wrapped rows, live coordinate actions, gameplay keybinds and map retry/cleanup
  behavior are corrected. Guidance readiness uses aggregate payment and hand-in eligibility;
  search-queue completion callbacks can clear or repopulate work without corrupting iteration.
- **Optional integrations fail predictably.** Radius/spouse selection is exact, cross-dimension
  resident checks prevent known duplicate-relative cases, and unreadable MCA handles do not
  fabricate valid data. Integration initialization publishes only after hooks succeed. Reputation
  imports prefer current per-player/dimension history, exclude canonical mirrors and retain
  zero-score titles/high-water marks. Townstead cache invalidation and profession matching,
  Bountiful deduplication, and map ownership/cleanup are corrected. New compatibility packs
  default below owner datapacks; saved pack order remains authoritative.
- **Release checks verify the artifact.** Forge checks actual reobfuscated member references,
  required metadata, class ownership and Java 17 bytecode, and tests rejection of a dev-mapped
  fixture. NeoForge checks required metadata, class ownership and Java 21 bytecode, with four
  targeted corrupt-archive fixtures. Its separate probes inherit the ModDevGradle launcher
  providers so mod classes load in the correct layer. Platform ranges, listings and typed
  compile-only dependency documentation reflect the real build.

## Compatibility and upgrade contracts

| Artifact | Declared platform | Mandatory MCA probe fleet |
|---|---|---|
| Forge | Minecraft 1.20.1, Forge 47.x, Java 17 | 7.6.20, 7.7.0-beta.2, 7.7.1-alpha.2 for 1.20.1 |
| NeoForge | Minecraft 1.21.1, NeoForge 21.1.x, Java 21 | 7.7.36-beta.3 and 7.7.22 for 1.21.1 |

Forge's inspected MCA artifacts use `forge.net.mca` and `forge.net.conczin.mca`; runtime
bindings also recognize supported unmerged roots. Each probe jar is isolated so dependency
resolution cannot collapse the fleet into one version. The MCA interaction screen's private
villager field and layout marker were inspected in all three Forge artifacts. Supported version
ranges express intent; these probes establish the inspected manifests, not a playthrough of
every MCA point release. Platform jars are not interchangeable.

Existing packet IDs, field order and platform protocol versions are retained (Forge 14, NeoForge
15). Existing public constructors and registration/event entry points remain. Save additions are
optional: older data loads without conversion, pending debt/snapshots are additive, and absent
mod content remains recoverable. No existing valid datapack schema was renamed. Malformed
values that formerly loaded through accidental defaults now need correction; diagnostics name
the field. A legacy failed project is not unexpectedly restarted just because the mod upgraded.

The source API comparison against each checkout's own baseline found no removed public/protected
callable signatures or public fields. Record components were added to `PendingReward`,
`CapitalRelationCondition` and `CapitalChronicleReward`, while their original constructors and
accessors remain. Consumers inspecting record metadata, relying on generated equality/toString,
or recompiling Java 21 record patterns against the old component arity must account for the
expanded records. This is a precise constructor/method compatibility claim, not a guarantee
that reflection sees an identical class shape.

## Verification results

Both full release builds completed successfully on 2026-09-06. All executed tests passed.

| Verification | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| Full `build` / regression suite | 1,096 passed; 15 optional cases skipped (1,111 total) | 1,161 passed; 18 optional cases skipped (1,179 total) |
| MCA manifest fleet | All three configured artifacts passed | Both configured artifacts passed |
| Available optional-jar tasks | 17 passed: maps 3, Capitals 3, Ice & Fire 2, Townstead 4, Bountiful 5 | 9 passed: Ice & Fire CE 1, Bountiful 8; original Ice & Fire half skipped (no jar) |
| Additional Capitals versions | 1.3.5 and 1.3.6: 3 passed each; 1.3.7 passed in the release run | No matching jar available |
| Artifact checks | Metadata/ownership/Java 17 + 1,980 SRG member references; dev-mapped fixture rejected | Metadata/765 owned classes/Java 21; all four corrupt fixtures rejected |
| Static content checks | `check_mod.py`: no findings | Platform-independent `--only content`: no findings |
| Whitespace validation | `git diff --check`: passed | `git diff --check`: passed |

The source/resource census is 580 main Java files and 177 test Java files on Forge; 583 main
Java files and 184 test Java files on NeoForge. Each port contains 356 JSON resources, five
`pack.mcmeta` files and two PNG assets. Resource tests parse all JSON/mcmeta, reject duplicate
keys, decode PNGs, check owned texture references and enforce parity for 3,202 locale keys.

Final commands (optional jar properties supplied as documented in `build.gradle`):

```text
Forge:    gradlew build mapProbeTest capitalsProbeTest iceAndFireProbeTest townsteadProbeTest bountifulProbeTest --continue
NeoForge: gradlew build iceAndFireProbeTest bountifulProbeTest --continue
Forge:    gradlew capitalsProbeTest -PcapitalsJar=<1.3.5 jar>
Forge:    gradlew capitalsProbeTest -PcapitalsJar=<1.3.6 jar>
```

Full command output is retained in each checkout's `build-release.log`. Gradle XML and HTML
results are in `build/test-results` and `build/reports/tests`. The three Capitals version
reports are retained separately under Forge's `build/audit-results`. Deprecation warnings
remain in the pinned toolchains; there are no failing build/check tasks. The NeoForge probe
launcher no longer logs the former companion-plugin classloader error.

The version-only 1.6.2 release bump was rebuilt successfully with `gradlew build` on both
platforms; those logs are in `build-1.6.2.log`. The optional-jar probe results above were
recorded before the version bump and apply to the same implementation.

Built artifact: `build/libs/mcaquests-1.6.2.jar` in each project. SHA-256:

```text
Forge:    656a743a95d2b9872bd28c57a15478695389e7eb36ef943758fc4861a96cbe1c
NeoForge: c6d3fabf35b60299b8c486dd190984786d0d6f5823ef5bbcaa07d4f61c1bc42a
```

The source-build procedure and optional jar properties are in each project's `README.md` and
`build.gradle`. Normal tests deliberately keep optional mods off the classpath; their jar probes
are separate tasks. A skipped optional probe in `test` is not runtime validation. Actual supplied
jar versions and their availability are listed in the compatibility report.

## Manual runtime verification and limits

- Boot a dedicated server and two clients on each platform. Exercise accept/decline/complete,
  canceled interactions, repeated requests, reload during play, reconnect, death/respawn and
  dimension travel. Run a representative MCA 7.6/7.7 gameplay matrix on Forge; use the actual
  NeoForge MCA releases with that artifact.
- Inspect the menu, journal/log, narrator, long translations, HUD/toasts, portraits and markers
  at small windows and GUI scales 1–4. Test shader/resource-pack effects and actual map startup,
  cleanup failure/recovery and world switches. Automated geometry/probe tests cannot verify
  GPU rendering or third-party UI attachment.
- Exercise project failure, pause/retry, server restart, offline payout, inventory-full and large
  rewards. Restore missing definitions/items/integrations and confirm saved work/debt resumes.
  Test missing relatives loaded in another dimension and registered but unloaded MCA residents.
- Actual modern Townstead and NeoForge JourneyMap/Xaero/Capitals jars were unavailable; those
  production integrations still need matching-jar gameplay checks. FTB book/team operations,
  Reputation events and optional mixin execution also need the complete runtime stack.
- Arbitrary add-on/container callbacks can perform irreversible external work before throwing;
  inventory rollback cannot undo side effects outside the container transaction. Legacy pending
  rewards predating snapshots cannot reconstruct historical attribution after arbitrary resets.
- Reputation histories accrued while the canonical mod was removed have no shared provenance.
  Existing canonical communities remain authoritative; administrators must reconcile conflicting
  histories explicitly. A wholly unregistered MCA entity in a never-loaded chunk cannot be
  discovered by loaded-entity/resident checks. Existing explicit datapack priority is preserved.

Upstream reference: [Forge networking documentation](https://docs.minecraftforge.net/en/latest/networking/simpleimpl/).
Exact compatibility conclusions above are based on inspected local source and jar manifests.
