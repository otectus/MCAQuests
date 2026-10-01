# MCA: Quests 1.7.1 audit — 2026-09-30

Scope: the Forge 1.20.1 line (`main`), with every fix mirrored to the NeoForge 1.21.1 port (`1.21.1`;
see that tree's `docs/PORT_PARITY.md`). Version stays 1.7.1 (unreleased); the changes are recorded in
`CHANGELOG.md` under that entry.

## What the mod does (as read from code and data)

Villagers of MCA Reborn offer datapack-defined quests (`data/<ns>/mcaquests/quests`, plus templates,
chains and situation offers). Offers are drawn per player and per villager into a remembered session
(`OfferSessionService`) and re-validated on accept. Accepting freezes the giver's identity, profession,
dimension and village, randomized rewards and villager targets onto an `ActiveQuest` in the player's
`PlayerQuestData` capability (copied on `PlayerEvent.Clone` through a full NBT round trip). Progress is
server-side only: event objectives through `QuestProgressEvents.forActiveObjectives`, polled objectives
once a second, inventory objectives read live, and item hand-ins through `DeliveryService` (Deliver
button, MCA's Gift via a mixin on `VillagerCommandHandler.handle`, legacy right-click, turn-in), which
records a per-objective ledger. Turn-in follows the quest's mode (original giver, same profession,
named professions, any villager, self-complete); completion pays rewards (inventory overflow is held
as pending item rewards, a reward that throws is held for `/mcaquests rewards retry`), records history
and cooldowns, and awards village standing: the authored `reputation` outcome, else the difficulty
band's default (`easy/medium/hardQuestReputation`, 2/4/7). Failure (deadline, time of day, weather,
giver or target death, lost target) and abandonment cost standing only when authored. Standing is held
by MCA: Reputation when installed (canonical backend) and by Quests' own per-player store otherwise.
The client sees a quest log/HUD snapshot (re-sent on every mutation and once a second), the villager
menu, guidance markers, and a journal of standing, titles and completions.

## Known issue from the brief

- **Delivery progress: not reproduced.** On production Forge dedicated servers with real MCA (7.6.26,
  7.7.1-beta.2, and 7.6.26 with MCA: Reputation 0.6.1), Gift through MCA's own command handler paid
  1/2 then 2/2 with no hearts, the Deliver button paid the rest, and turn-in charged nothing more
  (fixture phase `delivery`, rows `DELIVERY` and `VILLAGE-STANDING`: PASS).
- **Standing not updating: reproduced** for a giver who lives in no village at turn-in (finding 1), and
  partly (finding 7) for a journal left open. Both fixed and re-run on the same servers and a production
  client.

## Findings

| # | Severity | Location | Issue | Fixed |
|---|---|---|---|---|
| 1 | Critical | `quest/QuestManager.java` `grantQuestReputation` (was :1470-1473) | Standing lost when the giver resolved to no village at turn-in, although the village was frozen at accept | Y |
| 2 | Major | `quest/reward/RecordIncidentReward.java:77`, `ResolveIncidentReward.java:106` | Incident rewards ignored the frozen village; dropped for unloaded or homeless givers | Y |
| 3 | Major | `quest/QuestManager.java` `retryHeldReward` | Retry granted with no giver: hearts, village titles and Capitals rewards paid nothing, reported "Paid" and were removed | Y |
| 4 | Major | `quest/QuestManager.java` `isComplete`, `notifyExternalObjective`; `quest/delivery/DeliveryService.java` `paused` | A copy paused by a definition edit could still read ready, be turned in against reinterpreted progress, take goods and take add-on signals | Y |
| 5 | Minor | `quest/QuestManager.java` `failQuest` | `failure_hearts` skipped whenever the giver was unloaded | Y |
| 6 | Minor | `quest/QuestManager.java` `deliver` | Deliver & complete re-sent the menu without a commission board's restriction (Forge only) | Y |
| 7 | Minor | `quest/JournalService.java` | Journal refreshed only on open; standing, titles and completions changed under an open journal | Y |
| 8 | Minor | `data/QuestDataLoader.java` | `unlock` / `hearts_with_participants` in a quest parse and silently pay nothing | Y (warning) |
| 9 | Minor | `data/TranslationKeyValidator.java` | An untitled quest's fallback title key was never checked; the raw key reached chat (seen with the audit's own fixture quest) | Y (warning) |
| 10 | Minor | `compat/mca/McaHandles.java:372` | `rewardHearts` swallows an invocation failure with no log | N — deliberate, commented; `McaBindingProbeTest` verifies the member on every MCA in the fleet |
| 11 | Minor | `compat/LegacyReputationBackend.java` `recordIncident` | Returns false when an applied delta leaves the score at 0 | N — no caller reads the result |

Evidence and root causes for 1–4 are in `CHANGELOG.md` (1.7.1, "Fixed — standing, rewards and pauses
that did not land"). No other category in the brief produced a finding: no client class is reachable
from common code (every client subscriber is `Dist.CLIENT`, packets reach client code through
`DistExecutor`), every handler is on the right bus (`check_mod.py`: 0 errors, 0 warnings), every catch
block that ignores is commented and intentional, every `SavedData` marks itself dirty at its mutation
sites, identity is UUID throughout, client caches clear on logout, the server never takes progress from
a packet, every packet handler enqueues to the server thread, both locales have identical key sets
(3,413), and every registered objective type has a live trigger.

## Open questions

1. **Which village earns a quest turned in to a different villager?** In `same_profession`,
   `specified_profession` and `any_villager` modes, and `original_giver`'s same-profession fallback, the
   turn-in villager's village receives the standing; the giver's frozen village is used only when that
   villager has none. Crediting the giver's village
   instead would be a design change.
2. **Journal across dimensions.** `JournalService.knownVillages` lists villages in the player's current
   dimension only.
3. **Journal tier names with MCA: Reputation installed** come from Quests' own ladder applied to
   Reputation's score, not from Reputation's ladder.

No change is needed in MCA: Reputation.

## Verification

| Check | Result |
|---|---|
| `gradlew-quiet.sh MCAQuests build` (tests + `verifyReobfJar`) | PASS, 1,509 tests, 0 failures, 16 skipped |
| `gradlew-quiet.sh MCAQuests_1.21.1 build` | PASS, 1,547 tests, 0 failures, 19 skipped |
| `check_mod.py MCAQuests` | 0 errors, 0 warnings |
| `runData` | Not applicable: no datagen providers, `src/generated` is empty |
| Production Forge 47.4.23 server, MCA 7.6.26 / 7.7.1-beta.2 / 7.6.26 + Reputation 0.6.1 | `DELIVERY`, `STANDING`, `VILLAGE-STANDING` PASS on all three; no MCA: Quests ERROR |
| Production Forge 47.4.23 client, MCA 7.6.26 | Loaded, joined, saved; `JOURNAL-PUSH` PASS; only ERROR is the machine's missing `libflite` |
| Dev `runServer` / `runClient` | Not run: MCA's mixins cannot load in a dev mapping (`build.gradle:43-45`), so real behaviour is verified on production instances |

### Not verified

- Anything on NeoForge at runtime: both jars build and test, but no NeoForge server or client was run.
- MCA's Gift sent from a real client's interaction screen (the server path through MCA's handler was).
- Townstead, Capitals, FTB Quests, Bountiful, Ice and Fire and Map Atlases rows were not re-run; no
  code they exercise changed.
- Two players at one giver at the same time.

### In-game checklist (singleplayer, then a dedicated server)

For each type, accept, progress, check the quest log and HUD update within a second, turn in, and check
standing in the journal (and that an open journal updates):

- **Item hand-ins:** `item_delivery` (carry, Deliver part, Gift one, turn in), `deliver_to_villager`
  (Gift one at a time, Deliver to a recipient who is not the giver), and a `consume: false` proof.
- **Combat and hunting:** `kill_entity` (melee, bow, pet kill), `defend_villager`, `defend_location`,
  `protect_entity`.
- **Gathering and crafting:** `obtain_item`, `craft_item`, `fish_item`, `break_block` (placed blocks
  must not count), `place_block`, `build_near_location`, `use_item`, `interact_block`.
- **Travel:** `visit_biome`, `visit_dimension`, `enter_structure`, `reach_location`, `escort_entity`
  (restart the server mid-escort).
- **People:** `talk_to_profession`, `trade_with_villager`, `heal_entity`, `cure_villager`,
  `find_missing_relative`.
- **Animals and rest:** `breed_animals`, `tame_animal`, `sleep_or_rest`.
- **With their mods:** `townstead_*`, `bountiful_bounties`, `ftbq_complete_quest`.
- **Outcomes:** a timed quest left to expire far from its giver (failure hearts apply when the giver next
  loads), abandon with deposits, a full inventory at turn-in, and a giver killed mid-quest.
