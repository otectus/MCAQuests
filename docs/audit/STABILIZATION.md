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
