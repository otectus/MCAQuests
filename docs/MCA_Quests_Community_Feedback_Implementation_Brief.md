# MCA: Quests — Community Feedback Fix & Refinement Brief

**Prepared:** September 22, 2026  
**Repository:** [otectus/MCAQuests][repo]  
**Assignment:** Implement and validate the fixes below; do not stop at an audit or a rewritten plan.  
**Release target:** The next appropriate patch release. Confirm the checked-out version before selecting a number.

## 1. Mission and non-negotiable outcomes

Fix the reported progression failures, make each requirement understandable in-game, and eliminate optional-mod content leakage. Preserve existing worlds, legitimate progress, contributions, and pending rewards. Treat this as one coordinated reliability update: event recognition, objective eligibility, progress calculation, guidance, and recovery must agree about what the player actually needs to do.

**Townstead-dependent content must not load into the playable definition registries when Townstead is absent. MCA Capitals-dependent content must not load into those registries when MCA Capitals is absent.** Hiding a button, suppressing a final objective, or waiting until a later phase to discover the dependency does not satisfy this requirement. A definition requiring both mods requires both. Neither mod may become a mandatory dependency of MCA: Quests as a workaround.

Retaining an inert record of an already accepted quest is different from loading a playable definition. Preserve old records in an unavailable/suspended state with an accurate explanation; never delete them just to achieve an empty active registry. Installing the missing dependency later must allow safe recovery without duplicated rewards or reset progress.

Implement the smallest coherent changes that satisfy the acceptance tests. Reuse existing compatibility adapters, delivery transactions, guidance infrastructure, and persistence conventions. Do not replace functioning systems, introduce a second reputation system, invent Townstead mechanics, or blame an entire modpack without reproducing the relevant interaction.

## 2. Evidence, scope, and repository baseline

### 2.1 Coverage of the community reports

The four screenshots supplied with this assignment are the direct evidence for the current reports. They contain no visible author names, timestamps, complete version lists, logs, or comment IDs. Do not invent those details.

The [CurseForge comments page][comments] was reachable during preparation, but its comment bodies were not exposed to the available browser reader; alternate page requests did not recover them. Consequently, this brief **does not claim to inventory every live comment**. It covers every concern in the supplied screenshots and the owner's additional dependency requirement, plus clearly identified related reports from earlier discussion.

At kickoff, review the newest comments and their replies when accessible. Use the latest 30 days as an initial review window, follow unresolved older threads that recent replies reference, and record the actual coverage. Add newly discovered concerns to the ledger below. An author reply saying a fix is planned is not evidence that the released build fixes it. A duplicate report should link to the same work item rather than generate a parallel implementation. Do not delay the known fixes because further comments cannot be retrieved.

### 2.2 Inspected baseline

| Platform | Branch and inspected reference | Build metadata |
|---|---|---|
| Forge 1.20.1 | `main` at `5348af1865d94dd279a19e539aba94b9684c3964` | Version `1.6.5`; Java 17; Forge `47.4.10`. |
| NeoForge 1.21.1 | `1.21.1` at `93787a66e34ae39c53f313ac2711f6c8058c240c` | Version `1.6.5`; Java 21; NeoForge `21.1.248`. |

Sources: [Forge properties][properties], [NeoForge properties][neo-properties], [repository orientation][claude], and [source map][modmap]. Detailed implementation inspection for this brief was on the pinned Forge branch; NeoForge's build baseline was verified, but its complete gameplay implementation was not independently audited. Inspect the corresponding code before porting changes and report validation separately for each platform.

The [published 1.6.5 release notes][release-neo] already describe partial item deliveries and MCA Gift support. The inspected repository's [changelog][changelog] still labels its 1.6.5 section “Unreleased.” Reconcile this documentation discrepancy; do not rebuild the delivery feature as though it were absent or equate a changelog claim with a passing gameplay test.

**Evidence status:** This is a source-inspected implementation brief, not a runtime test report. No compilation, automated suite, dedicated-server run, or modpack reproduction was performed while preparing it. Code facts below are distinguished from hypotheses that the implementing agent must test.

## 3. Concern ledger

| ID | Source and concern | Required disposition | Priority |
|---|---|---|---|
| C01 | Screenshot 1: **The Missing Mile** does not count talking to the indicated cartography/quartermaster villager. A newly spawned villager assigned cartography also failed. The reported environment is Waccy's Fantasia; exact versions were not supplied. | Fix conversation credit and ensure the marker, profession requirements, and destination rules match. Explain any remaining reach/turn-in requirement. | P1 |
| C02 | Screenshot 2: **A Working Village** says to train three villagers to level 2; the player does not know how or which mod is needed. | Correct dependency gating and explain Townstead profession progression, eligible residents, and actual training prerequisites. | P1 |
| C03a | Screenshot 3: A village wall project does not register progress, and the player cannot tell where to build. | Verify the likely match, **Walls before Winter**, then fix placement recognition and show the actual permitted area and materials. | P1 |
| C03b | Screenshot 3: The player asks whether a command can skip the stuck phase. | Document current command behavior and add safe, instance-specific recheck/recovery. Do not recommend a silently global mutation. | P1 |
| C04a | Screenshot 4: **Known Far and Wide**, phase 2, does not recognize an inn built after accepting the project. | Correct building registration, village binding, polling, and phase refresh; prove the after-acceptance scenario. | P1 |
| C04b | Screenshot 4: The player does not understand the “welcome bonus.” Earlier discussion clarified the wording as earning **2 Welcoming Points**. | Identify the exact Townstead metric and valid earning actions. Fix baseline timing and explain the requirement in the objective itself. | P1 |
| C05 | Owner requirement: no Townstead or MCA Capitals quests without their relevant mods. | Enforce definition-level exclusion and all activation-path checks, including entire multi-phase projects and dependency removal from existing worlds. | **P0 release gate** |
| C06 | Related earlier report, not another newly retrieved screenshot: **Library Restoration** fails to count three librarians, possibly after an update mid-project. | Include in the shared conversation and save-upgrade regression suite. Confirm current reproduction status. | P1 regression |
| C07 | Related earlier reports: crossbow/blaze-rod deliveries, unclear hand-in flow, and requested MCA Gift delivery with other MCA add-ons installed. | Verify the existing 1.6.5 implementation; fix only reproduced residual problems and preserve its safety properties. | P1 regression |
| C08 | Related earlier question: whether MCA: Quests prevents normal MCA relationship/reputation gains. | Test ordinary MCA behavior and integration ownership; clarify the distinction without making an unsupported claim of interference. | P2 regression |

P0 means the release must not ship while the invariant is unproven. P1 covers blocked progression, misleading requirements, and recovery hazards. P2 covers focused refinements and related regressions; elevate a reproduced data-loss or duplication issue immediately.

## 4. Important findings already visible in the source

These are starting points, not substitutes for end-to-end reproduction.

| Finding | Verified source and implementation consequence |
|---|---|
| Townstead projects are in the ordinary resource tree. | The two reported projects live under `data/mcaquests/mcaquests/projects/townstead/`. Their first phases contain ordinary donations and their JSON lacks an explicit availability condition. [Working Village][working-village]; [Known Far and Wide][known-far]. |
| The current Townstead project offer gate is configuration-based, not a mod-presence gate. | `TownsteadContentGate.allowsProject` reads content toggles. `ProjectManager.isEligibleSponsor` invokes it; `readsTownstead` scans objectives across phases. This does not establish Townstead's presence or capabilities. [Gate][townstead-gate]; [manager][project-manager]. |
| Loader-level exclusion is not provided by the existing default-pack switch. | `BuiltinPack.filter` concerns whether bundled content is enabled. `ProjectDataLoader` then decodes/registers project definitions. Do not confuse that switch with dependency filtering. [Filter][builtin-filter]; [project loader][project-loader]; [quest loader][quest-loader]. |
| Capitals already has a conditional pack. | `CAPITALS_COURT` in `CompatPacks` requires `registry.has("mcacapitals", "capitals.registry")` and its built-in-content toggle. Preserve and test it rather than claiming Capitals has no gate. Townstead is not among that class's four declared packs. [Pack definitions][compat-packs]; [finder][pack-finder]. |
| The base conversation hook is narrow. | `QuestEventHandlers` subscribes to `PlayerInteractEvent.EntityInteract`, requires the main hand and an empty main-hand stack, and rejects cancellation. An explicit `notifyVillagerConversation` API route is documented in the same class. Whether every supported MCA build and add-on actually reaches these routes requires verification. [Handler][talk-events]. |
| Missing Mile has two independent objective definitions. | It requires reaching the nearest other village and talking to one `minecraft:cartographer`. The talk JSON has no destination field; its objective implementation searches nearby matching villagers for guidance. Audit consistency with the intended destination rather than assume a marker guarantees validity. [Definition][missing-mile]; [talk objective][talk-objective]. |
| Workforce implementation and explanatory comments disagree. | The workforce class describes accepting additional progressive tracks, but `poll` still calls `matches(view)`, which checks the explicit profession list. Fix the policy/documentation mismatch deliberately. [Workforce objective][workforce]. |
| Spirit baselines are first initialized inside `poll`. | `TownsteadSpiritProjectObjective` writes `townstead_spirit_baseline` when absent. The normal project phase transition calls `enterPhase`. Test the interval between phase entry and the first successful spirit poll; do not assume the baseline was captured at phase activation. [Spirit objective][spirit-objective]; [manager][project-manager]. |
| Scope and recovery need particular care. | Scope resolution reads the project's fallback-radius override, while `ProjectManager.inScopeAt` passes the global fallback radius to containment. The current `adminAdvance` and `adminReset` helpers operate on all matching project-ID instances. [Scope resolver][scope]; [manager][project-manager]. |

## 5. Work package A — Strict optional-dependency loading and activation

**Addresses C05 and prevents the misleading starts in C02/C04. Implement this first.**

### A1. Define one availability contract

Use the real loader IDs: `townstead` and `mcacapitals`, as declared in the inspected [mod metadata][metadata]. MCA Reborn itself remains required; these integrations remain optional.

Separate four questions: whether the mod is installed, whether its compatible adapter is usable, whether the server enables the relevant content, and whether a particular world/player currently satisfies gameplay prerequisites. A missing library is not the same as an existing world with no inn or no nearby cartographer. Do not permanently suppress all content merely because a world-dependent registry cannot be queried before server initialization.

For a definition with mandatory integration dependencies, require every necessary mod and capability. Missing mandatory capabilities must prevent new activation and produce a reason, not silently downgrade the objective to something easier. Configuring content on cannot override an absent dependency. Support mixed-dependency definitions with AND semantics; do not accidentally require both mods for Townstead-only or Capitals-only content.

The expected installation behavior is:

| Townstead usable | Capitals usable | Townstead-dependent content | Capitals-dependent content | Content requiring both | Unrelated core content |
|---|---|---|---|---|---|
| No | No | Excluded | Excluded | Excluded | Available under its normal rules |
| Yes | No | Eligible to load | Excluded | Excluded | Unchanged |
| No | Yes | Excluded | Eligible to load | Excluded | Unchanged |
| Yes | Yes | Eligible to load | Eligible to load | Eligible to load | Unchanged |

“Usable” here includes the capability needed by that definition; it does not mean every optional feature of the integration must bind. Content toggles apply in addition to this matrix.

### A2. Enforce the contract before playable registration

Prefer extending the existing conditional-pack infrastructure for bundled Townstead content, preserving logical resource paths and definition IDs. Add a loader-level dependency check as defense in depth so custom namespaces, copied definitions, overrides, and embedded templates cannot bypass the rule. Reuse an existing dependency schema where possible; any new metadata must be documented, exported, and backward-compatible.

Inventory personal quests, **all project phases**, situations and their embedded offers, templates, mandatory rewards, and chained/follow-up activation. A vanilla donation in phase 1 must not make a Townstead-dependent phase-2 project appear in a base installation. A dependency used only by a later phase or a mandatory reward still matters before the project starts.

Distinguish mandatory dependencies from genuinely optional branches. Do not blindly union every mod reference in an `any_of` condition or disable a core quest because an explicitly optional side effect is unavailable. Conversely, namespace or filename checks alone are insufficient: the integration objective types themselves are in the `mcaquests` namespace. Validate declared requirements against the actual typed content and flag omissions in bundled definitions.

Absent integration content must be absent from normal quest/project registries, offer generation, automatic assignment, situation activation, follow-up seeding, active content lists, and ordinary journal discovery. Do not construct third-party types merely to discover that their mod is absent. Registering MCA: Quests' own safe codecs or retaining an inert diagnostic descriptor is acceptable; registering an available gameplay definition is not.

Use the same authoritative availability decision at acceptance, contribution, delivery, forced/API assignment, phase advancement, and reward/follow-up execution. Invalidate stale offers and client menus on reload. A stale packet or an old cached ID must never consume items or create a new unavailable instance.

### A3. Preserve overrides and reload behavior

Preserve datapack precedence and distinguish resource origin from namespace. An actual core-only override that removes every integration requirement must not be blocked solely because its path used to be Townstead-related. An override still using Townstead behavior remains gated regardless of its namespace or source pack.

Test both strict and non-strict JSON validation. Skipping a valid definition because a required optional mod is absent is a supported outcome, not malformed JSON. When its dependencies are present, genuinely malformed content must still fail or report errors according to the existing strict-mode policy. Prevent dangling-chain validation from misclassifying intentionally excluded optional content.

Re-evaluate supported content/config changes on `/reload`. Test installation/removal of mod jars through a complete restart, **not by pretending that `/reload` can unload Java mods**. Check the packaged jar for duplicate definitions left in both the original resource tree and the new conditional pack.

### A4. Migration and unavailable saved records

Preserve original IDs, phase keys, objective evidence, participants, sponsors, contribution totals, deposited items, baselines, timestamps, completion ledgers, and pending rewards. Missing dependencies must suspend progress and relevant timers without failing the project or paying it out. Present old records separately as unavailable, with the missing mod/capability named; do not keep advertising them as normal active content.

When the dependency returns, resume compatible records exactly once. When definitions materially changed, retain incompatible evidence for diagnosis and require a safe migration or targeted repair instead of reinterpreting an objective by index. Do not reset a spirit baseline or transfer a village's project to a different village during recovery.

Review pending rewards specifically: stricter registry exclusion will make some definition lookups return empty. Temporary dependency unavailability must defer an owed payout, not exhaust its retry allowance as a permanent failure. Do not double-pay a reward that was already committed before removal. Check sponsor-death behavior and follow-up seeding too; neither may bypass suspension.

**Acceptance:** In a fresh base installation, zero Townstead/Capitals-dependent definitions become playable or are offered. In an upgraded installation, unavailable records remain intact but inert. Reinstallation restores them without changed totals, timer penalties, duplicate instances, or duplicated payouts.

## 6. Work package B — Reliable conversations and consistent targets

**Addresses C01 and C06.**

### B1. Reproduce before choosing the hook

Use `mcaquests:road_the_missing_mile` and `mcaquests:library_restoration`. The [library definition][library] requires three librarian conversations in phase `catalogue`; its scope is profession-based. The [Missing Mile definition][missing-mile] has a `reach_location` objective anchored to `nearest_other_village`, search radius `2048`, reach radius `8`, and `min_journey: 96`, plus one cartographer conversation.

Record the exact MCA: Quests/MCA builds, loader, installed interaction add-ons, raw villager profession ID, objective state, and the actual event/packet path. Reproduce with MCA alone, then with relevant add-ons, then the reported Waccy's Fantasia configuration when its exact manifest is available. Do not mark the full modpack tested when only a minimal equivalent was run.

Investigate `EntityInteractSpecific`/`interactAt` routes as well as `EntityInteract`; these are a hypothesis for a bypass, not a confirmed diagnosis of the screenshot. Check the supported MCA jars' actual implementation and the explicit Conversations notification route. Cover ordinary interaction while holding an item: a genuine conversation should not be silently rejected merely because one adapter opens the same dialogue with a non-empty hand.

### B2. Credit a real conversation, not every right-click

Route successful, server-validated conversational interactions through one common credit service. Keep the adapter optional, narrowly targeted, and compatible with supported MCA package roots. Use a real server-side success signal where available; a client saying that it opened a screen is not sufficient evidence.

Do not count canceled interactions, remote/spoofed entity references, editor-book actions, inventory access, trading, combat actions, or arbitrary item use as conversations unless the existing quest contract explicitly includes that action. Do not “fix” the empty-hand filter simply by making every held-item click count.

Maintain UUID-based distinct-villager evidence for personal quests and shared projects. Receiving the base hook, a more-specific hook, and an add-on notification for the same interaction must not create extra credit. Repeated talk to the same librarian remains one distinct librarian, including across players where project progress is shared. Do not globalize personal quest credit or replay a prior quest instance's evidence into a new instance.

### B3. Make guidance and credit share eligibility

Trace what “quartermaster” means in the reported installation. Use the underlying profession ID and the configured matching policy. Do not hard-code a translated display label as an alias for `minecraft:cartographer`, and do not weaken strict matching globally to solve one pack.

For Missing Mile, align narrative, the frozen travel destination, talk eligibility, and guidance. The inspected talk objective only declares profession/count and finds a nearby uncredited villager; it does not itself declare destination membership. If the intended step is to speak at the destination village, add a backward-compatible target/scope constraint to that quest and use it for both credit and markers. Do not change every profession-talk quest to require that village or silently rebind its destination as the player travels.

Differentiate **travel complete**, **conversation credited**, and **ready to turn in**. If the conversation is counted but the reach check or final turn-in remains, show that accurately instead of leaving the impression that talking did nothing. Handle absent, dead, unloaded, or replaced targets without misleading markers or automatic free completion.

### B4. Repair old saves without erasing evidence

Add a regression fixture for a partially completed Library Restoration project upgraded mid-phase. Preserve already credited UUIDs and reconcile compatible progress safely. Newly received conversations must update server state, the open project screen, journal, and tracker without requiring relogging.

**Acceptance:** Missing Mile credits an eligible conversation once; three distinct eligible librarians satisfy `catalogue`; invalid interactions do not count; guidance never advertises a target that the same objective would reject. Both fresh and upgraded saves pass.

## 7. Work package C — Explain and validate Townstead workforce progression

**Addresses C02.**

The [bundled Working Village definition][working-village] is `mcaquests:townstead_a_working_village`. Phase `skilled_hands` uses `townstead_workforce_project`, `minimum_tier: 2`, `count: 3`, and the listed professions farmer, shepherd, and butcher. The [implementation][workforce] reads Townstead profession levels and verifies that a loaded track supports the target tier. Vanilla trading rank and player XP are not the values it checks.

Replace the generic “train three villagers to level 2” presentation with an explanation that identifies **Townstead profession tier**, the relevant village, the qualifying profession policy, and the current count. Example wording for the current listed-policy behavior:

> Have 3 residents of this village reach Townstead profession tier 2 or higher. Eligible trades: Farmer, Shepherd, Butcher. This is not the vanilla trading level.

Generate the final wording from the actual definition and adapter data, rather than hard-code those three trades into a general UI. Add expandable help naming verified XP-producing work/training actions, required equipment or workplace conditions, and blocked status when the adapter exposes it. Confirm those instructions against the installed Townstead version and its loaded progression data; do not invent a training button, command, workstation, or additional required mod.

Resolve the source-policy discrepancy: the class's explanatory comments promise that additional valid progression tracks can count, but its implementation still checks the explicit list. Preserve explicit restrictions in existing third-party datapacks. A backward-compatible selection policy can keep existing definitions as “listed professions” while allowing the bundled general workforce project to opt into “any supported progressive trade,” if that is the chosen design. Otherwise correct the documentation and expose the restricted list. Record the decision and test it; do not silently broaden every authored quest.

Only count real eligible MCA residents of the bound village with a reachable Townstead tier. No duplicate resident IDs, wrong-village counts, vanilla-trade substitution, or default/unsupported tracks. Handle profession changes and config/datapack reloads predictably. The current implementation inspects loaded residents and retains a high-water count; distinguish incomplete observation from failure, preserve legitimate recorded progress, and do not force-load every resident or claim knowledge of unloaded residents that the adapter does not provide.

**Acceptance:** A player can discover the required mod, profession system, eligible residents, and valid next action from the project UI. Three qualifying Townstead residents satisfy it; vanilla trade leveling alone does not. With Townstead absent, the project never appears.

## 8. Work package D — Wall placement, village boundaries, and construction feedback

**Addresses C03a.**

The screenshot does not show a quest ID. Confirm that its title and active phase map to the [bundled `mcaquests:walls_before_winter`][walls] before applying quest-specific assumptions. Its `raise_the_wall` phase currently requires **128 `minecraft:cobblestone_wall` placements and 16 `minecraft:lantern` placements** within the project's scope. It does not declare a seasonal failure/deadline in that JSON. Do not invent a Townstead or seasons-mod dependency for this core project, or interpret any arbitrary wall-shaped build as satisfying its exact block predicates.

### D1. Fix and expose the real scope

Trace sponsorship → stored project instance → village identity/dimension → placement position → `inScopeAt` → `ScopeResolver.isWithinScope` → MCA containment. A village-bound project uses MCA's village containment; it does **not** universally accept every block within 64 blocks of the player or sponsor. An anchor-only fallback uses a radius around its stored anchor. [Scope implementation][scope]; [project event processing][project-manager].

Unify resolution, credit, diagnostics, and drawing around the same geometry. The inspected resolution path reads the definition's fallback-radius override, but subsequent containment passes the global fallback radius. Fix that inconsistency without silently resizing all saved instances. Explicitly persist or consistently resolve the effective scope, with an upgrade strategy for existing records.

Add a lightweight **Show build area** action or equivalent guidance in the existing project UI. Name the village, show the dimension and anchor as useful, and indicate whether the targeted block position is inside the valid area. Use actual bounds where available; clearly label an approximation when an adapter cannot provide an exact outline. Never draw a confident radius that disagrees with the credit predicate. This feature must work without installing a map mod.

Investigate building at the edge of a village: defensive walls naturally sit near its perimeter. If MCA's containment rejects sensible perimeter placement, select and document a bounded, project-specific construction allowance rather than globally weakening village scope for every objective. Apply the same allowance to server credit and visible guidance and migrate deliberately.

### D2. Count only valid committed placement

Check block ID/tag matching, the block's position rather than just the player's position, active phase, dimension, contribution caps, cancellation/event ordering, and any supported multi-block placement route. A protected/canceled placement cannot count. Repeatedly breaking and replacing one counted position must not produce unbounded progress. Do not accidentally count the same placed block twice through multiple events.

Preserve the current action-based semantics unless an explicit design change is necessary: building before `raise_the_wall` activates does not automatically establish that the required new placements happened during that phase. Explain this rule. If adding existing-build recognition, make it an explicit, bounded, evidence-based mode or recovery action with separate anti-exploit tests—not an unconditional scan that grants credit for nearby terrain.

Show `Walls: x/128` and `Lanterns: y/16`, the accepted material names, and the active phase. Explain why a relevant placement was rejected: wrong block, wrong village/area, wrong dimension, inactive phase, previously counted position, or contribution limit. Use contextual/rate-limited feedback; never broadcast a chat message for every unrelated block placement.

**Acceptance:** Correct placements in the displayed area count immediately. Outside/protected/wrong-material placements do not. Players can locate an eligible position without guessing. Completed work and unique-position evidence survive reload and restart.

## 9. Work package E — Inn recognition and “Welcoming Points”

**Addresses both parts of C04.**

The [Known Far and Wide definition][known-far] is `mcaquests:townstead_known_far_and_wide`. Phase `open_our_doors` requires one Townstead building of type `inn` and a `townstead_spirit_project` with `spirit: "tourism"`, `points_delta: 2`. The screenshot calls the second part a welcome bonus; earlier clarification named Welcoming Points. This is not evidence of a separate currency or a separate reward to claim.

### E1. Read Townstead's registered building state correctly

The [building objective][building-objective] polls the registry through `TownsteadEvaluation.countBuildings`; a structure resembling an inn is not itself proof of registration. Reproduce the player's sequence exactly: activate the project, reach phase 2 without an inn, create and register a valid inn afterward, then observe the objective and open UI.

Trace the project's village ID and dimension into the adapter's registry lookup, building-family normalization, minimum level, ownership/association, and cache invalidation. Account for level-suffixed building families according to the actual Townstead adapter, not substring guesses. Compare the Townstead registry result with what the player-facing Townstead UI reports. A bridge lookup failure must read as unavailable/unknown, not a confident zero inns.

A valid inn already registered when this phase begins should satisfy the **building-state** objective, because this definition does not request a newly constructed building. A valid inn registered later should be recognized on the next bounded refresh. Wrong-village buildings, invalidated registrations, and unavailable adapters need distinct behavior and diagnostics. Recheck after registration, relevant changes, restart, and reload without requiring a contribution click to refresh state.

### E2. Correct and preserve the spirit baseline

The [spirit implementation][spirit-objective] initializes `townstead_spirit_baseline` during its first successful poll. Test the race in which Tourism increases after phase activation but before that poll. Without correct initialization, the increased value can become the baseline and the player is asked to earn the same increment again.

Establish the baseline at a well-defined phase-activation boundary through a shared initialization path covering normal advancement, new instances, seeded follow-ups, and admin recovery. Capture a valid source reading; absence of data is not zero. If the reading is unavailable, record the pending initialization explicitly and suspend activation/progress as appropriate rather than silently absorb later progress into a new baseline.

Persist it once per objective instance and never reset it on screen open, normal poll, save load, reload, or temporary dependency loss. Keep **baseline**, **current source total**, **required delta**, and **credited high-water progress** separate; the existing implementation retains achieved progress when spirit later falls, so do not accidentally erase that behavior while changing initialization.

For legacy records with no reliable baseline, use only defensible persisted evidence for automatic repair. Never pretend to reconstruct an unrecorded historical value. Report ambiguous records and provide the targeted operator recovery from package F. Preserve already completed objectives and issued rewards.

### E3. Explain the source metric and real earning actions

Show that the requirement refers to Townstead's Tourism-related spirit metric, with the installed version's user-facing terminology. If “Welcoming” is the localized alias, pair it with the identifiable Townstead metric rather than inventing a new stat. Present progress as a delta from phase activation and expose the baseline/current values in detailed help or diagnostics.

Inspect the relevant Townstead building/spirit definitions before promising that **one inn always adds exactly two points**. The inspected MCA: Quests JSON requires an inn and a +2 Tourism delta; it does not independently prove what every Townstead version or datapack awards. Document the actual supported earning actions and show an appropriate fallback explanation for modified packs.

Test an inn that predates the phase. It satisfies the building-state requirement, but existing Tourism is not automatically a new +2 delta. Ensure the remaining requirement is achievable under the supported data. If the bundled design can be stranded after prior development, make a deliberate definition change and migration instead of suggesting demolition/rebuild exploits or silently awarding fictitious new points.

**Acceptance:** The after-acceptance inn scenario works. A genuine +2 change after phase activation is counted once, including before the first periodic poll. Existing buildings and existing spirit are distinguished correctly. The player can identify the metric and a valid way to advance it.

## 10. Work package F — Diagnostics and safe project recovery

**Addresses C03b and recovery for C01/C02/C04/C06.**

The inspected [command registration][commands] includes these existing read-only entry points:

```text
/mcaquests debug mca
/mcaquests debug villager
/mcaquests debug quest <id>
/mcaquests debug guidance
/mcaquests debug delivery
/mcaquests project list
/mcaquests project info <id>
/mcaquests project debug <id>
```

Townstead's documented diagnostics include `/mcaquests compat townstead status` and `/mcaquests compat townstead snapshot`. Verify exact behavior on each target branch when documenting them. Current read diagnostics generally require permission level 2; project advancement/reset require level 3. [Commands][commands]; [Townstead documentation][townstead-doc].

**Important existing limitation:** `/mcaquests project advance <id>` currently calls a helper that advances every non-terminal instance with that project ID. Its helper changes the phase/status directly rather than using the normal completion/reward path. `reset <id>` also removes matching instances across scopes. These are not safe default recommendations for fixing one player's village. [Manager][project-manager].

Extend existing command infrastructure rather than add competing command roots. The exact new grammar is an implementation choice, but it must provide these operations:

| Operation | Required contract |
|---|---|
| Inspect one instance | Resolve an explicit instance/scope/dimension, not merely a definition ID. Display other matches when ambiguous. |
| Recheck | Re-evaluate legitimate current-state evidence and refresh UI; do not fabricate event history, reset baselines, consume items, or pay rewards merely for running a diagnostic. |
| Preview repair/skip | Describe the exact instance, current phase/revision, requested change, and reward/follow-up effects without mutation. |
| Confirm targeted repair/skip | Permission-gated, bounded to the previewed instance and phase; reject stale confirmation. Log actor, target, before/after state, and policy. |
| Explicit bulk operation | Preserve intentional administrative bulk capability only behind explicit selection and clear confirmation, not an ambiguous bare project ID. |

A phase skip is not proof that the skipped work occurred. Default to no new skipped-phase rewards; preserve previously earned/queued rewards. Any supported “complete with normal rewards” mode must be explicit and reuse idempotent settlement rules. Define and test completion events, statistics, follow-ups, baseline initialization, and payout ledgers for each recovery mode. Never enable reward duplication by alternating skip/reload/reset.

Retain compatible command aliases where practical, but refuse ambiguous mutations rather than silently target every village. A useful support answer must state the permission requirement and scope, not just supply a dangerous command string. Provide a backup recommendation before mutations; normal bug fixes must not require world resets or manual save editing.

Diagnostics should identify the definition and instance, phase key, objective index/type, raw profession or block ID, bound village/dimension, effective geometry, dependency/capability status, progress evidence, baseline/current values, and the specific rejection reason. Distinguish **blocked**, **not yet satisfied**, **unavailable**, and **unobserved**. Keep logs opt-in/rate-limited and avoid exposing unnecessary player identity or location data to other players.

**Acceptance:** A server with two villages running the same project can repair one without changing the other. Rechecks are non-destructive. Repeated/stale confirmations do not duplicate progress, rewards, or lifecycle events.

## 11. Work package G — Preserve delivery improvements and ordinary MCA behavior

**Addresses C07/C08; regression work, not a new delivery rewrite.**

The [1.6.5 release notes][release-forge] and [repository changelog][changelog] already describe the shared delivery service, partial deposits, menu delivery actions, MCA Gift bridge, and legacy-save migration. Read the current implementation and tests before changing it.

Reproduce two-crossbow and six-blaze-rod requests through both the quest UI and MCA Gift, including relevant Townstead/Social Expansion/Conversations combinations when those exact builds are available. Verify that the intended recipient, outstanding quantity, and selected quest are authoritative. Partial deposits must remain credited; repeat clicks, replayed packets, and final turn-in cannot charge a deposited unit twice. Surplus stays with the player. Destination-full and wrong-recipient cases explain the failure and leave inventory unchanged.

Maintain the existing distinction between consuming deliveries and non-consuming proof objectives, exact inventory-slot policy, preference for ordinary items, and per-quest-instance identity. Test ambiguous same-item obligations, unavailable Gift hooks, old progress migration, abandoned/reaccepted copies, and dependency removal between menu opening and delivery. The menu route must remain useful when a verified optional bridge is unavailable; do not display a Gift hint when it cannot work.

For the reputation question, identify which value the player means: MCA relationship hearts, vanilla village reputation, MCA: Quests village standing, or the optional MCA: Reputation backend. Test normal MCA actions without Quests intercepting them. Ordinary gifts that are not claimed as quest deliveries must retain their normal behavior. A Gift unit deliberately consumed by a quest delivery is a separate documented path; the existing changelog says it avoids MCA's ordinary gift rewards for that unit. Do not “restore normal gains” by giving every consumed unit both reward paths.

**Acceptance:** Existing 1.6.5 delivery behavior remains safe and intelligible, and unrelated MCA interactions and reputation/relationship changes are not suppressed or duplicated.

## 12. Focused cross-cutting refinements

Apply refinements that support the fixes, not an unrelated feature expansion.

**A shared objective explanation.** Reuse server-side predicates to produce concise “what counts / where / next action / why blocked” details. Keep ordinary UI readable; put raw IDs and binding failures in expanded help or diagnostics. Do not turn each objective into a separate modal window.

**Consistent live refresh.** Update an already open menu, the journal, tracker, and guidance after accepted progress, phase changes, dependency/config reloads, and repairs. Reject stale responses using the appropriate instance/revision identity. Use bounded polling and targeted subscriptions; avoid rescanning every villager or resending all menus every tick.

**Compare effective values before syncing.** The inspected Townstead polling objectives clamp counts when storing them. Compare the clamped candidate with the stored value before reporting a change, so extra buildings/workers or spirit above the requirement do not continually dirty a still-open phase. Preserve intentional monotonic and live-state semantics for their respective objective types. [Building][building-objective]; [workforce][workforce]; [spirit][spirit-objective].

**Dimension-safe identity and stable migration.** Test villages with equal numeric IDs in different dimensions and distinguish frozen instance identity from a moving sponsor. The inspected `ScopeResolver` emits identities such as `v:<id>` while storing dimension separately; verify the full instance-key behavior before assuming collision safety. Fix any reproduced collision with explicit migration, never by silently merging two projects. [Scope resolver][scope].

**Localization and accessibility.** Keep `en_us` and `pt_br` key parity, pluralization, dynamic profession/material names, and wrapping across GUI scales. Use text as well as color for readiness and rejection states. Make build-area guidance toggleable and usable without external map integrations. Avoid promising source data that an unsupported integration cannot supply.

**Documentation and release accuracy.** Update `README.md`, `CONFIG.md`, `DATAPACK.md`, `TOWNSTEAD.md`, `CAPITALS.md`, schema output, and relevant agent orientation when their contracts change. Document new metadata, changed recovery semantics, loaded-vs-unavailable state, migration rules, and exact supported versions. Reflect real test evidence in the changelog and support responses.

## 13. Required regression and acceptance matrix

Turn these scenarios into automated tests at the appropriate layer and use real client/dedicated-server checks for hooks, resource loading, and UI behavior that mocks cannot prove. Suggested test IDs below are **new acceptance identifiers**, not claims that matching test classes already exist.

### Dependency loading and save safety

| Test | Scenario and expected result |
|---|---|
| DEP-01 | Base MCA + Quests, neither integration installed: no dependent personal quests, projects, situations, templates, or mandatory follow-ups become playable. Core Missing Mile, Library Restoration, and Walls before Winter remain eligible under normal rules. |
| DEP-02 | Townstead only; Capitals only; both: verify every row of the installation matrix, including a mixed-dependency fixture. |
| DEP-03 | Integration installed but required adapter capability unavailable: affected content is excluded from new activation with an accurate reason; unrelated integration features remain usable. |
| DEP-04 | Content toggles off/on and supported `/reload`: no stale offers or client cards, no duplicate registration, and no bypass when the mod is absent but its toggle is true. |
| DEP-05 | Vanilla-only phase 1, Townstead phase 2: no project offer or donation acceptance when Townstead is missing. Repeat with the dependency used only in a mandatory reward or nested offer. |
| DEP-06 | Third-party namespace, same-path override, copied template, and malformed present-dependency content: availability, resource precedence, and strict validation each retain their correct semantics. |
| DEP-07 | Save with partial progress, deposits, spirit baseline, participants, and pending rewards; remove a mod and restart: records remain inert and intact, timers do not expire them, and payouts are deferred. |
| DEP-08 | Restore that mod and restart: resume exactly once, preserving evidence and owed rewards. Repeat through multiple removal/restoration cycles. |
| DEP-09 | Previously open menu, stale packet, API/forced assignment, and follow-up seeding reference excluded content: deny safely with no item loss or new active record. |
| DEP-10 | Inspect production artifacts and launch dedicated servers without the integrations: optional metadata remains optional, external classes are not required, and no duplicate resource copies leak content. |

### Conversation and workforce progression

| Test | Scenario and expected result |
|---|---|
| TALK-01 | Missing Mile in the minimal supported MCA setup: the real conversational route credits one eligible cartographer; remaining travel/turn-in requirements are displayed independently. |
| TALK-02 | Each supported interaction route and available add-on bridge: genuine conversations count, while one interaction arriving through multiple routes still counts once. |
| TALK-03 | Canceled, offhand-duplicate, editor/inventory/trade, invalid-target, and spoofed/remote events: no false credit. A held item does not itself disqualify a proven real conversation. |
| TALK-04 | Namespace matching modes, displayed quartermaster label, different village, missing/replaced villager, and frozen destination: credit and guidance use the same resolved eligibility. |
| TALK-05 | Library Restoration: three distinct librarians count across shared participants; repeated talk to one does not. Upgrading a partially completed `catalogue` save preserves existing UUID evidence. |
| WORK-01 | Three eligible same-village Townstead residents at tier 2+: complete the workforce objective. Vanilla trading rank or player XP alone does not count. |
| WORK-02 | Unsupported/default track, wrong village, below-tier villager, duplicate identity, and profession change: accurate qualification and explanation, no invented progress. |
| WORK-03 | Added/removed datapack progression track: obey the chosen profession-selection policy and preserve existing explicit restrictions. |
| WORK-04 | Unloaded residents and incomplete observations: no forced chunk loading, false failure, or loss of legitimate recorded progress; UI explains what was observed. |

### Construction, inn registration, and spirit baselines

| Test | Scenario and expected result |
|---|---|
| BUILD-01 | Correct blocks placed during `raise_the_wall` inside the displayed valid area: wall and lantern counters update separately and the phase advances at the exact requirements. |
| BUILD-02 | Wrong block, outside area, wrong dimension, protected/canceled placement, and inactive phase: no credit, with relevant contextual rejection feedback. |
| BUILD-03 | Break/re-place one counted position, duplicate/multi-place events, and reload/restart: no duplicate count and no lost unique-position evidence. |
| BUILD-04 | Real MCA village bounds, anchor fallback, per-definition radius different from global config, and village-edge construction: guidance and credit agree. |
| BUILD-05 | Two scopes or dimensions with potentially colliding village IDs: no cross-credit, accidental instance merging, or collateral repair. |
| INN-01 | Start phase 2 without an inn, register a valid inn afterward: registry result and project UI reach 1/1 within the documented refresh bound. |
| INN-02 | Inn already registered before phase 2: building objective recognizes current state; spirit objective follows its separately documented delta rule. |
| INN-03 | Wrong-village inn, level/family variations, invalidated registration, adapter unavailable, and stale cache: distinguish absence from unreadability and update correctly. |
| SPIRIT-01 | Capture phase-start baseline, then earn +2 before the first normal poll: credit 2, do not initialize baseline to the already-increased value. |
| SPIRIT-02 | Earn +1, save/reload/restart, then earn another +1: retain baseline and complete exactly once. Reopening UI does not change it. |
| SPIRIT-03 | Source temporarily unavailable, dependency removed/restored, or source total later falls: preserve the declared pause and high-water semantics without treating unknown as zero. |
| SPIRIT-04 | Legacy missing/ambiguous baseline and pre-developed village: no fabricated history or unavoidable stranded objective; explicit repair or a documented migrated design handles the case. |

### Recovery, existing delivery, and presentation

| Test | Scenario and expected result |
|---|---|
| REPAIR-01 | Two villages run the same project: targeted recheck/repair affects only the selected instance; bare ambiguous mutation is refused. |
| REPAIR-02 | Read-only inspection and preview: no changed progress, baselines, item counts, payout ledgers, or generated contributions. |
| REPAIR-03 | Permission failure, stale confirmation, repeat confirmation, final-phase skip, and explicit reward policy: no unauthorized change or duplicated payout/event/follow-up. |
| DELIVERY-01 | Crossbows and blaze rods via menu and Gift: partial quantities, correct recipient, ambiguous obligations, full destination, and final turn-in preserve item conservation. |
| DELIVERY-02 | Legacy saves, already deposited goods, named/enchanted items, proof objectives, replayed packets, and reaccepted quest instances preserve existing safety behavior. |
| MCA-01 | Ordinary conversation/gift/trading with and without the relevant add-ons: normal MCA behavior is preserved; quest-consumed gifts do not accidentally take both reward paths. |
| UI-01 | Open menus, journal, tracker, and guidance update after progress/phase/reload/repair; stale responses cannot restore obsolete state. Test GUI scales and both bundled locales. |
| BUILD-SMOKE | Compile, automated tests, production build, real adapter probes, client smoke, and dedicated-server smoke are reported separately for Forge and NeoForge. An unrun layer is not a pass. |

## 14. Implementation sequence and verification

Read `CLAUDE.md` and `MODMAP.md` first, then inspect relevant code and tests on the current branch. Record the starting SHA, working-tree state, mod versions, and available integration jars. Do not overwrite unrelated work or delete the user's saves to obtain a clean reproduction.

1. **Establish evidence:** Refresh the concern ledger, reproduce the blocked flows, and add failing regression tests or executable fixtures. Distinguish confirmed source defects, reproduced runtime defects, already-fixed reports, and unresolved hypotheses.
2. **Fix the availability boundary:** Implement package A and its migration tests before adding new UI affordances that might expose unavailable content.
3. **Repair shared progression:** Implement conversation credit, workforce qualification/help, scope/placement fixes, and building/spirit initialization. Keep source-of-truth predicates shared with guidance and explanations.
4. **Add safe recovery and preserve integrations:** Implement instance-targeted diagnostics/repair and run delivery/reputation regressions. Recheck phase settlement and follow-up behavior after every recovery-path change.
5. **Finish and verify both targets:** Port the equivalent fixes with platform-appropriate APIs, update localization/docs, and run the matrix. Keep unrelated refactors and speculative new systems out of the patch.

On Forge, the inspected [agent instructions][claude] specify `./gradlew compileJava`, `./gradlew test`, and `./gradlew build`; the build includes production-artifact verification. They also describe real-jar compatibility probes, including `townsteadProbeTest` and `capitalsProbeTest`, which need their documented jar inputs and are not substitutes for gameplay tests. The MCA binding test uses the versions configured in `mca_probe_versions`. Use the checked-out build scripts to verify task names and required parameters rather than guessing them.

For NeoForge, use its Java 21 toolchain and its own build/test tasks. Do not copy Forge event hooks, networking, mixin targets, or reobfuscation assumptions verbatim. A compilation pass on one loader does not prove the other works. New or changed packet shapes require the branch's appropriate protocol/codec compatibility change and matching client/server behavior.

At minimum, runtime evidence must cover a base-only dedicated server, a usable Townstead installation, a usable Capitals installation, and their combination on the claimed target platforms. Use supported matching dependency versions. Treat full-modpack compatibility as a separate claim requiring the exact manifest and reproduction. Where an environment or jar is unavailable, finish everything testable and record the smallest remaining acceptance procedure and its blocker explicitly.

## 15. Deliverables and definition of done

Deliver the code/resource changes, focused regression tests, backward-compatible migrations, updated player/datapack documentation, and a concise implementation report. Supply a player-facing explanation for each screenshot report, including current supported behavior and any required upgrade, without announcing a fix as released before it is actually released.

The implementation report should use this compact structure:

```text
Starting and final SHA / branch / platform:
Concern ID -> reproduced cause or current disposition:
Changed code, definitions, schemas, and player-facing behavior:
Migration and recovery behavior:
Automated tests: exact commands, results, failures, skipped cases:
Runtime checks: exact mod versions and scenarios actually exercised:
Unverified cases: specific blocker and remaining acceptance step:
Release/documentation notes:
```

The update is ready only when the optional-dependency matrix is proven, the supplied progression scenarios work or have an accurately evidenced disposition, players can understand the requirements in-game, one stuck instance can be recovered safely, and existing progress/items/rewards survive the upgrade. **“It compiles,” “a condition was added,” and “the counter was force-completed” are not substitutes for those outcomes.**

## Source references

The repository references below are pinned to the inspected commits so that later changes do not silently alter the evidence. Re-read current code before implementing. Screenshots and the owner's dependency requirement are supplied conversation evidence, not reconstructed public comments.

[repo]: https://github.com/otectus/MCAQuests
[comments]: https://www.curseforge.com/minecraft/mc-mods/mca-quests/comments
[release-forge]: https://www.curseforge.com/minecraft/mc-mods/mca-quests/files/8892151/dependencies
[release-neo]: https://www.curseforge.com/minecraft/mc-mods/mca-quests/files/8892152
[claude]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/CLAUDE.md
[modmap]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/MODMAP.md
[properties]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/gradle.properties
[neo-properties]: https://github.com/otectus/MCAQuests/blob/93787a66e34ae39c53f313ac2711f6c8058c240c/gradle.properties
[changelog]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/CHANGELOG.md
[metadata]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/resources/META-INF/mods.toml
[quest-loader]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/java/dev/otectus/mcaquests/data/QuestDataLoader.java
[project-loader]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/java/dev/otectus/mcaquests/project/data/ProjectDataLoader.java
[builtin-filter]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/java/dev/otectus/mcaquests/data/BuiltinPack.java
[townstead-gate]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/java/dev/otectus/mcaquests/compat/TownsteadContentGate.java
[compat-packs]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/java/dev/otectus/mcaquests/compat/pack/CompatPacks.java
[pack-finder]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/java/dev/otectus/mcaquests/compat/pack/CompatPackFinder.java
[project-manager]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/java/dev/otectus/mcaquests/project/ProjectManager.java
[scope]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/java/dev/otectus/mcaquests/project/scope/ScopeResolver.java
[commands]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/java/dev/otectus/mcaquests/command/McaQuestsCommand.java
[missing-mile]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/resources/data/mcaquests/mcaquests/quests/chains/road_the_missing_mile.json
[talk-events]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/java/dev/otectus/mcaquests/event/QuestEventHandlers.java
[talk-objective]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/java/dev/otectus/mcaquests/quest/objective/TalkToProfessionObjective.java
[library]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/resources/data/mcaquests/mcaquests/projects/library_restoration.json
[working-village]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/resources/data/mcaquests/mcaquests/projects/townstead/a_working_village.json
[workforce]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/java/dev/otectus/mcaquests/project/objective/TownsteadWorkforceProjectObjective.java
[walls]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/resources/data/mcaquests/mcaquests/projects/walls_before_winter.json
[known-far]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/resources/data/mcaquests/mcaquests/projects/townstead/townstead_known_far_and_wide.json
[building-objective]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/java/dev/otectus/mcaquests/project/objective/TownsteadBuildingProjectObjective.java
[spirit-objective]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/src/main/java/dev/otectus/mcaquests/project/objective/TownsteadSpiritProjectObjective.java
[townstead-doc]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/TOWNSTEAD.md
[capitals-doc]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/CAPITALS.md
[datapack-doc]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/DATAPACK.md
[config-doc]: https://github.com/otectus/MCAQuests/blob/5348af1865d94dd279a19e539aba94b9684c3964/CONFIG.md
