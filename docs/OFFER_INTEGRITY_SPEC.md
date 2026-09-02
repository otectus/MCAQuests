# MCA: Quests — Offer Integrity Fix Spec

**Target:** MCA: Quests `1.4.0`, Minecraft 1.20.1 / Forge 47.4.10, Java 17.
**Audience:** a coding agent with full write access to the `MCAQuests` repo.
**Source of truth for this document:** the `otectus/MCAQuests` tree at commit `621b7d7` and the
`Luke100000/minecraft-comes-alive` upstream tree. Every claim tagged **[verified]** was read directly
out of one of those two trees while writing this. Anything not so tagged is design guidance you must
still confirm as you implement.

---

## 0. The two reports, and what they actually are

> "When you hit **decline**, it changes the context/story of every quest of that villager, but the
> three quests (by default) remain the same, so it doesn't actually decline the quest."

> "Also sometimes there are family quests (I got the *1 paper to a sibling*) when the villager
> doesn't have family (or at least a sibling in this case)."

These are not two isolated bugs. They are two symptoms of the same structural gap: **the quest offer
menu is recomputed from scratch on every open, is never persisted, and is never validated against the
world it claims to describe.** Everything below follows from that.

Report 1 is *"a player action that changes nothing"*.
Report 2 is *"content that names something that does not exist"*.

This spec fixes both **as classes**, not as two patches. Section 1 and Section 2 are the two reported
bugs; Section 3 is the full inventory of every other place in the mod where the same two failure modes
already exist and must be fixed in the same pass; Sections 4–8 are the implementation plan, the
datapack/API contract changes, tests, and verification.

**Ground rules**

1. **Server-authoritative, always.** Every new decision is made on the server, persisted on the
   server, and re-validated on the server. The client is never trusted, exactly as the existing code
   already documents.
2. **Fail closed.** Where MCA data is unreadable or ambiguous, content becomes *ineligible*, never
   accidentally eligible. This is the mod's existing stated policy for Townstead reads
   ("a `missing` answer that defaults to **false** so an unreadable value makes content *ineligible*
   rather than accidentally satisfied" — `CHANGELOG.md`, 1.4.0) and it must now apply to MCA family
   reads too.
3. **No silent controls.** After this work, no config key, no datapack field, and no button may exist
   that does nothing. Anything that cannot be honoured is either implemented or removed — and if it is
   removed, it is removed from `CONFIG.md` / `DATAPACK.md` in the same commit.
4. **Backwards compatible saves.** New NBT is additive; absent sub-tags load as empty. Existing worlds
   keep their active quests, history, titles, standing, projects and situations.
5. **One commit per phase**, each compiling and each with green tests.

---

## 1. Bug A — "Decline" does nothing

### 1.1 Evidence

`src/main/java/dev/otectus/mcaquests/quest/QuestManager.java:98–109` **[verified]**

```java
public static void acceptFromPacket(ServerPlayer player, UUID villagerUuid, ResourceLocation questId, boolean accept) {
    Entity villager = resolve(player, villagerUuid);
    if (villager == null) {
        return;
    }
    if (accept) {
        accept(player, villager, questId);
    }
    // decline is a no-op for now (it simply does not create state); both refresh the menu.
    sendMenu(player, villager);
    syncLog(player);
}
```

The client sends a real decision — `QuestDecisionC2SPacket(villagerUuid, questId, false)` from the
Decline button at `client/QuestMenuScreen.java:118–119` **[verified]** — and the server drops it on
the floor and re-renders the same menu.

### 1.2 Root cause chain

**A1 — No decline state exists.** There is no per-player, per-villager, per-quest "declined" record
anywhere. `PlayerQuestData` holds `active`, `history`, `titles`, `stats` and nothing else
(`state/PlayerQuestData.java:19–22`) **[verified]**. `QuestHistory` records `COMPLETED`, `FAILED`,
`ABANDONED` outcomes and cooldowns (`state/QuestHistory.java:23–26`) **[verified]** — there is no
`DECLINED` outcome.

**A2 — The offer set is a pure function of (player, villager, world day).**

```java
// QuestManager.java:1151  [verified]
private static long offerSeed(ServerPlayer player, UUID villagerUuid, long worldDay) {
    return player.getUUID().hashCode() * 31L + villagerUuid.hashCode() * 17L + worldDay * 1000003L;
}
```

`selectOffers` (`QuestManager.java:1055–1078`) **[verified]** re-derives the eligible pool, re-groups
it into priority tiers, and re-runs `WeightedPicker.pickMany(bucket, weightFn, seed, slots)` with that
seed. Since nothing about the player's state changed when they declined, the pool is identical, the
seed is identical, and the same three quests come back. **This is exactly the reported symptom.**

**A3 — Offers are never persisted, so there is nothing to mutate.** There is no offer cache at all.
Every `sendMenu` rebuilds the whole menu: eligibility pass, snapshot, weights, weighted draw, template
resolution, dialogue resolution. The mod's own naming even implies a cache that does not exist —
`offerRefreshTicks` is commented *"Ticks before a villager's **cached** offers reroll"*.

**A4 — Why the *story* changes even though the *quests* do not.** Each rebuild re-runs
`buildCard` → `QuestDialogueHooks.resolve(player, villager, def, dialogueState, staticFallback)`
(`QuestManager.java:243–246`) **[verified]**. With no add-on registered this returns the static
datapack line and is stable. With the **MCA: Conversations** add-on installed — the mod ships hooks
specifically for it (`README.md:33`, `api/QuestDialogueHooks.java`) **[verified]** — the resolver is
called afresh for *every card* on *every menu build*, so a villager re-voices all three offers each
time the menu is reopened. The reporter is watching a full re-voicing of the entire menu, which is why
they describe "the context/story of **every** quest" changing while the quests themselves do not.
Template variables are *not* the cause: `QuestContext.stableRandom` is seeded per
(player, villager, world day, quest id, variable name) (`quest/condition/QuestContext.java:60–66`)
**[verified]**, so template values are stable within a day.

**A5 — The `decline` dialogue line is parsed and never displayed.** `QuestDefinition.DECLINE = "decline"`
(`quest/QuestDefinition.java:56`) **[verified]**, every shipped quest JSON authors a
`"dialogue": { "decline": … }` entry, the mod's own in-game schema help advertises it
(`command/McaQuestsCommand.java:693`: `"decline": { "text": "Maybe another time." }`) **[verified]** —
and `QuestDefinition.DECLINE` has **zero** call sites in `src/` **[verified]**. Only `OFFER`,
`ACCEPT`, `IN_PROGRESS`, `READY`, `COMPLETE`, `FAILED` are ever passed to `dialogueOr`. So the villager
never even says the "maybe another time" line the pack author wrote.

**A6 — `offerRefreshTicks` is a dead config key.** Declared at `McaQuestsConfig.java:36, 124–125`,
documented in `CONFIG.md:21` and in `mca-quests.md`, and read **nowhere** in `src/` **[verified]**.
`selectOffers` hardcodes `getDayTime() / 24000L`. Setting it to 6000 to get four offer rotations a day
does nothing at all.

### 1.3 Required behaviour after the fix

| # | Requirement |
|---|---|
| A-R1 | Declining an offer removes that quest from this villager's offer set for this player, immediately and durably (survives menu close, relog, server restart, dimension change and death). |
| A-R2 | The declined slot is **refilled** from the remaining eligible pool if a candidate exists; if none exists the menu shows fewer cards, and if none remain it shows the existing `NO_QUESTS` state. |
| A-R3 | The **other offers do not change** when one is declined — not their identity, not their template values, not their dialogue. Only the declined card is replaced. |
| A-R4 | Reopening the menu without declining anything changes nothing at all: same quests, same template values, same dialogue text, including under MCA: Conversations. |
| A-R5 | A declined quest becomes offerable again from that villager after a configurable interval (default: the next offer refresh). Declining is not a permanent ban and never blocks a chain. |
| A-R6 | The quest's `dialogue.decline` line is shown to the player when they decline (chat message, gated by the existing `questChatMessages` config, and routed through `QuestDialogueHooks` exactly like `accept`). |
| A-R7 | `offerRefreshTicks` actually controls when a villager's offer set rerolls. |
| A-R8 | Declining is free: no hearts penalty, no reputation penalty, no failure outcome, no effect on `QuestHistory` completion counts. It is recorded as its own outcome so datapacks can branch on it. |
| A-R9 | Declining a **situation** offer behaves identically, and never resolves, fails or cancels the underlying situation for anyone else. |

### 1.4 Design — persist the offer session

Introduce a server-side, per-player **offer session** as the single source of truth for what a villager
is currently offering. This is the piece the mod is missing, and it is what makes A-R1 through A-R4
possible at once.

**New type: `dev.otectus.mcaquests.state.OfferSession`**

```java
/**
 * One villager's current offer set for one player: what was drawn, what it resolved to, and what the
 * player has already refused. Persisted with the player's quest data, so an offer the player is
 * looking at is the same offer after a relog — and a decline is a decision the world remembers.
 */
public final class OfferSession {
    private final UUID villagerUuid;
    private long refreshedAtGameTime;          // when this session was drawn
    private final List<Slot> slots;            // ordered; index == card position in the menu
    private final Map<String, Long> declinedUntil;  // questId -> game time the refusal lapses

    public record Slot(ResourceLocation questId,
                       @Nullable ResolvedTemplate frozenValues,  // null for non-template quests
                       @Nullable Component voicedOffer) {}       // cached QuestDialogueHooks result
}
```

**New store: `dev.otectus.mcaquests.state.OfferSessions`**, held on `PlayerQuestData` alongside
`history`/`titles`/`stats`, keyed by villager UUID, with `save()`/`load()`/`copyFrom()` in the same
shape as `QuestHistory` (`state/QuestHistory.java:153–191` is the pattern to copy) **[verified]**.

**Lifecycle**

1. `sendMenu` asks `OfferSessions` for the session for this villager.
2. The session is **stale** and must be redrawn when any of these hold:
   - it does not exist;
   - `now - refreshedAtGameTime >= McaQuestsConfig.COMMON.offerRefreshTicks.get()` (this is A-R7 —
     the dead key becomes live here);
   - the datapack was reloaded since it was drawn (bump a static `QuestRegistry` generation counter on
     reload and store it on the session);
   - every slot has become ineligible.
3. Drawing a session runs today's `eligibleOffers` → `selectOffers` **minus quests whose
   `declinedUntil` has not lapsed**, then freezes each slot's template values and voiced dialogue.
4. Rendering a session **does not redraw it**. Each slot is re-validated cheaply (still eligible? not
   already active? not on cooldown? giver still matches?) and a slot that has gone invalid is
   individually replaced from the remaining pool. Everything else is rendered from the frozen slot,
   so template values and voiced dialogue are byte-identical between opens (A-R3, A-R4).
5. Declining marks `declinedUntil[questId] = now + declineCooldownTicks`, drops that slot, and refills
   **that slot only**.

> **Why freezing dialogue is correct, not a hack.** The mod already freezes randomized currency rewards
> at accept time for exactly this reason ("the amount the player is shown is the amount they are
> eventually paid … there is no way to reroll a payout by reopening the UI",
> `QuestManager.freezeRandomizedRewards` **[verified]**). Offer text deserves the same contract: what
> the villager said is what the villager said.

**Determinism note.** `WeightedPicker.pickMany` draws sequentially from a shrinking pool
(`quest/WeightedPicker.java:26–48`) **[verified]**, so removing one candidate from the pool changes
every subsequent draw. That is precisely why the fix must be "persist the set and replace one slot",
not "re-run selection with the declined quest filtered out" — the latter would satisfy A-R1 while
violating A-R3.

### 1.5 Implementation steps (Bug A)

1. **`state/OfferSession.java`, `state/OfferSessions.java`** — new. NBT round-trip; `declinedUntil`
   entries whose time has lapsed are pruned on load and on write so the map cannot grow without bound.
   Prune sessions for villagers the player has not interacted with in `> 8 × offerRefreshTicks`.
2. **`state/PlayerQuestData.java`** — add the `offers()` accessor, and `save`/`load`/`copyFrom` wiring.
   `load` must treat an absent `"offers"` compound as an empty store (pre-1.5.0 saves).
3. **`quest/OfferSessionService.java`** — new; all of §1.4's lifecycle logic. `QuestManager` calls into
   it and keeps no offer state of its own. Keep it free of Minecraft rendering types so it is unit
   testable, in the style of `WeightedPicker`.
4. **`quest/QuestManager.java`**
   - `sendMenu`: replace the `eligibleOffers → selectOffers → buildOfferCard` block
     (`QuestManager.java:196–209`) with `OfferSessionService.currentOffers(player, villager, data)`,
     which returns the resolved slots to render.
   - `acceptFromPacket`: replace the no-op comment with a real branch:
     ```java
     if (accept) {
         accept(player, villager, questId);
     } else {
         decline(player, villager, questId);
     }
     ```
   - New `public static boolean decline(ServerPlayer player, Entity villager, ResourceLocation questId)`:
     server-authoritative and idempotent, mirroring `accept`'s shape. It must
     (a) verify the quest is actually in this villager's current session for this player — never trust
     a client-supplied id, exactly as `accept` re-validates against `eligibleOffers`
     (`QuestManager.java:272–275`) **[verified]**;
     (b) record `declinedUntil`;
     (c) drop and refill the slot;
     (d) record `QuestHistory.Outcome.DECLINED` (see step 6);
     (e) send the `DECLINE` dialogue line via `QuestDialogueHooks.resolve(...)` under
     `McaQuestsConfig.COMMON.questChatMessages`, copying the `ACCEPT` branch at
     `QuestManager.java:331–336` **[verified]**;
     (f) post a new `QuestDeclinedEvent` on the Forge bus.
   - `accept`: re-validate against the **session**, not a fresh `eligibleOffers` call, and take the
     frozen template values from the slot rather than re-resolving them. This closes a latent
     accept-time reroll: today `accept` calls `spec.resolveValues(context)` a second time
     (`QuestManager.java:288–296`) **[verified]** and relies on determinism to reproduce the offer —
     which stops being true the instant the world day ticks over between the menu opening and the
     player clicking Accept.
   - Delete `offerSeed` only after `OfferSessionService` owns the draw; keep the same seed formula plus
     a session epoch so behaviour is unchanged for a fresh draw.
5. **`McaQuestsConfig.java`** — wire `offerRefreshTicks` into `OfferSessionService`. Add:
   ```java
   declineCooldownTicks = b.comment(
           "Ticks a quest stays out of a villager's offers after the player declines it.",
           "0 = the decline only lasts until the villager's offers next refresh.")
       .defineInRange("declineCooldownTicks", 0, 0, Integer.MAX_VALUE);
   declineRefillsSlot = b.comment(
           "Whether declining an offer immediately draws a replacement offer into that slot.")
       .define("declineRefillsSlot", true);
   ```
6. **`state/QuestHistory.java`** — add `DECLINED` to `Outcome`. It flows through the existing
   `recordOutcome(quest, villager, outcome)` path and therefore through `outcomes`/`outcomesByGiver`
   NBT with no format change.
7. **`quest/condition/leaf/QuestDeclinedCondition.java`** — new, registered as
   `mcaquests:quest_declined`, mirroring `QuestAbandonedCondition` exactly (same `HistoryScope`
   handling). This lets a pack write "if they turned this down, offer the softer version instead".
8. **`api/event/QuestDeclinedEvent.java`** — new, in the existing `QuestEvent` hierarchy next to
   `QuestAbandonedEvent`. Public API: document it in `README.md` and `DATAPACK.md`.
9. **`command/McaQuestsCommand.java`** — add `/mcaquests debug offers [player]`: prints the current
   session for the villager the caller is looking at — slot ids, refresh time, time until reroll, and
   the declined map with remaining ticks. This is the observability that would have made this bug a
   five-minute diagnosis. Add `/mcaquests debug offers reroll` to force a redraw.
10. **`network/QuestNetwork.java`** — bump `PROTOCOL_VERSION` from `"10"` to `"11"` **[verified as
    current]** if and only if any packet shape changes (it does if you carry a per-card "declinable"
    flag; it does not if you leave the packets alone). Prefer leaving the packets alone.

---

## 2. Bug B — quests that name family who do not exist

### 2.1 What the reporter actually got

The quest is `mcaquests:relations_letter_to_brother`
(`src/main/resources/data/mcaquests/mcaquests/quests/relations/letter_to_brother.json`) **[verified]** —
deliver **1 `minecraft:paper`** to `{"mode": "family", "relation": "sibling"}`, gated by:

```json
"conditions": { "type": "mcaquests:related_villager_status", "relation": "sibling", "status": "same_village" }
```

So the quest *is* gated. The gate passed anyway. Here is why.

### 2.2 Root cause chain

**B1 — The gate and the target use different predicates.** `McaCompat.relativesWithStatus`'s own
javadoc claims otherwise:

> *"The relation sets are shared with `giverRelativeUuids` so a quest gated on '`relation` exists with
> `status`' and a target selecting that same `relation` can never disagree about who is in scope."*
> — `compat/McaCompat.java:464–467` **[verified]**

Only the **relation** set is shared. The **status** filter is not. The gate asks *"does at least one
sibling exist with status `same_village`?"*; the target then calls
`McaCompat.findGiverRelative(level, giver, "sibling")` (`compat/McaCompat.java:892–904`) **[verified]**,
which returns *the first sibling in list order, preferring a loaded one, otherwise any UUID at all* —
with **no status filter, no deceased filter, no existence filter**. One sibling can satisfy the gate
while a completely different one is handed to the objective.

**B2 — `same_village` is satisfied by dead relatives.** `McaCompat.matchesRelativeStatus`
(`compat/McaCompat.java:513–538`) **[verified]**:

```java
case "same_village" -> {
    Object village = McaHandles.homeVillage(giver);
    yield village != null && McaHandles.villageResidentUuids(village).contains(uuid);
}
```

No `deceased` check, no `probablyGenerated` check, no entity-existence check — unlike the `missing`
branch immediately above it, which carefully checks all three.

And MCA never removes the dead from the resident roll. In the upstream tree **[verified]**:

- `Village.residentNames` is a persisted `Map<UUID, String>`; `getResidentsUUIDs()` is just
  `residentNames.keySet().stream()` (`server/world/data/Village.java:248–250`).
- The only removal path is `removeResident(UUID)` (`Village.java:430`), called from exactly two places:
  `Residency.leaveHome()` (villager changes village) and MCA's admin command.
- `Relationship.onDeath` calls `getFamilyEntry().setDeceased(true)` and **never** touches the village
  roll (`entity/ai/Relationship.java:131–136`).

So: **any villager who has ever lived in a village stays on that village's resident list forever after
they die.** A giver whose sibling died last week still satisfies `sibling / same_village`, the quest is
offered, `findGiverRelative` hands back the dead sibling's UUID, and the player is told to deliver a
letter to someone who no longer exists. That is the reported bug, end to end.

**B3 — `findGiverRelative` will happily bind a UUID with no body.**

```java
// compat/McaCompat.java:892–904  [verified]
public static Optional<UUID> findGiverRelative(ServerLevel level, Entity giver, String relation) {
    UUID firstKnown = null;
    for (UUID uuid : giverRelativeUuids(level, giver, relation)) {
        Entity entity = level.getEntity(uuid);
        if (entity != null && entity.isAlive()) {
            return Optional.of(uuid); // prefer a loaded relative
        }
        if (firstKnown == null) {
            firstKnown = uuid;
        }
    }
    return Optional.ofNullable(firstKnown);
}
```

`firstKnown` is returned even when nothing in the world has that UUID. `giverRelativeUuids`
(`compat/McaCompat.java:847–886`) **[verified]** filters only `null`, `Util.NIL_UUID` and the giver
itself — not deceased, not `probablyGenerated`, not `isPlayer`.

**B3a — `relation: "any"` almost always resolves to a fabricated dead parent.** MCA gives *every*
naturally spawned villager two invented, deceased parents:

```java
// upstream entity/VillagerEntityMCA.java:293–302  [verified]
FamilyTreeNode father = tree.getOrCreate(UUID.randomUUID(), Names.pickCitizenName(Gender.MALE), Gender.MALE);
FamilyTreeNode mother = tree.getOrCreate(UUID.randomUUID(), Names.pickCitizenName(Gender.FEMALE), Gender.FEMALE);
father.setDeceased(true);
mother.setDeceased(true);
```

`giverRelativeUuids(giver, "any")` builds its list in the order **spouse → parents → children →
siblings**. So for any villager with no spouse, the first entry is a phantom parent. Every `relation:
"any"` quest in the pack — `kin_errand`, `family_reunion_feast`, `cure_infected_kin`,
`courting/3_meet_the_family`, `farmer_family/4_feast`, `widow_memorial` **[verified]** — can bind to a
person MCA invented to pad a family tree. The mod already knows these are not people:
`McaCompat.materializeRelative` explicitly refuses them ("*a filler ancestor MCA invented to pad a
family tree, never a person who can be found*") **[verified]**. That knowledge is simply not applied on
the offer/bind path.

**B3b — `widow_memorial` is structurally guaranteed to mismatch.** Its gate is
`spouse/dead` **AND** `any/same_village`; its objective target is `family/any` **[verified]**. Since
the spouse is dead and dead relatives are never removed from the tree, `relation: any` can — and for a
spouseless-otherwise villager will — bind straight onto the dead spouse the quest is a memorial *for*.

**B4 — Accept-time binding locks the phantom in permanently.**

```java
// QuestManager.bindVillagerTargets:406–408  [verified]
McaCompat.findGiverRelative(level, villager, selector.relation().orElse("any"))
        .ifPresent(active.progress(i)::setTargetUuid);
```

This writes whatever `findGiverRelative` returned — including a bodiless UUID — into the objective's
progress, permanently. Note the inconsistency with the lazy path: `ObjectiveSupport.resolveLocked`
(`quest/objective/ObjectiveSupport.java:67–79`) **[verified]** only locks a target once an actual
`LivingEntity` resolved. The accept-time binder is stricter about *when* and looser about *what*.

**B5 — Nothing checks at offer time that a villager target can resolve.** The only objective-level
offer gate is `isTriviallySatisfied` (`quest/objective/QuestObjective.java:79–81`, default `false`)
**[verified]**, and it is deliberately the *opposite* check — "would this already be done?". Nine
objective types override it — `EscortEntityObjective`, `ReachLocationObjective` and the seven Townstead
ones **[verified]** — but of the `VillagerTargeted` family, only `EscortEntityObjective` does.
`DeliverToVillagerObjective` (the one in the report), `DefendVillagerObjective`, `HealEntityObjective`,
`CureVillagerObjective`, `ProtectEntityObjective` and `FindMissingRelativeObjective` have **no**
resolvability pre-check whatsoever. There is no `isUnofferable`/`canResolveTarget` concept in the
codebase.

**B6 — Two shipped content files have no gate at all.**

- `quests/chains/lost_child/2_deeper.json` **[verified]**: objective
  `mcaquests:find_missing_relative` with `{"mode":"family","relation":"child"}`, and **no
  `conditions` block**. `effectiveConditions()` folds in only the chain prerequisite
  (`quest/QuestDefinition.java:180–201`) **[verified]** — "you finished stage 1 with this villager" —
  which does not re-check that a missing child still exists at stage-2 offer time.
- `situations/cure_the_infected.json` **[verified]**: no `conditions`, `scope: village`, and its offer
  objective targets `{"mode":"family","relation":"any"}` **of whichever villager gives it** — while the
  situation's trigger is about a *specific* infected villager whose UUID the instance already carries.
  Any adult in the village can offer "cure your relative" with no infected relative anywhere.

**B7 — Situation offers bypass the condition system entirely.** In `eligibleOffers`, the static pool
runs the full filter chain (enabled → profession → adult → hearts → not active → not on cooldown →
repeat rule → `effectiveConditions` → `isTriviallySatisfied`) and *then*:

```java
// QuestManager.java:1024–1026  [verified]
List<QuestDefinition> eligible = new ArrayList<>(collapseChainsToFurthestStage(filtered));
eligible.addAll(DynamicOfferSource.collect(player, villager, data, profession, adult, hearts));
```

`DynamicOfferSource.collect` (`quest/situation/DynamicOfferSource.java:37–83`) **[verified]** gates only
on scope, `GiverSpec`, and "not already active". Situation offers therefore skip **conditions,
cooldowns, repeat rules and the trivially-satisfied check**. Every guarantee this spec adds to the
static path must be added to the dynamic path too, or half the fix is missing.

**B8 — `alive` does not require a body either.** `case "alive" -> !deceased && (loaded || entry != null)`
**[verified]**. `entry != null` is true for any node in the tree, so `alive` reduces to "not flagged
deceased" — a villager removed from the world without a death event (`/kill` variants, a mod removing
them, a lost chunk) reads as alive forever.

**B9 — The relation vocabularies disagree.** `VillagerTarget.RELATIONS` =
`{any, spouse, parent, child, sibling, grandparent}` **[verified]**;
`McaConditionCodecs.FAMILY_RELATIONS` (used by `is_family_member`) =
`{any, parent, child, sibling, grandparent}` — **no `spouse`** **[verified]**;
`McaConditionCodecs.RELATED_RELATIONS` (used by `related_villager_status`) does include `spouse`. A pack
author writing `"relation": "spouse"` gets a hard load error from one condition and silent success from
another.

**B10 — The UI cannot tell the player the difference.** When no relative resolves,
`VillagerTarget.describeResolved` falls back to `describe()` **[verified]**, which renders
`mcaquests.target.relation.sibling` = `"the quest giver's sibling"`
(`assets/mcaquests/lang/en_us.json:147`) **[verified]**. A perfectly healthy unloaded sibling and a
sibling who does not exist render identically. Even when everything works, this is why the reporter
could not tell what the game meant.

### 2.3 Required behaviour after the fix

| # | Requirement |
|---|---|
| B-R1 | A quest is never **offered** if any of its objectives targets a villager that cannot be resolved to a real, findable person right now. |
| B-R2 | A quest is never **accepted** if that is still true at the moment of accept (re-checked server-side; the world may have changed between menu build and click). |
| B-R3 | The predicate used by the eligibility gate, by the accept-time binder, by target resolution, by `matches`, and by the display name is **one predicate in one place**. It is impossible for them to disagree. |
| B-R4 | "A real, findable person" excludes, by default: flagged deceased; `probablyGenerated` filler ancestors; player nodes; nodes with no entity anywhere in the world **and** no persistent record that could materialise one. |
| B-R5 | Content that *deliberately* targets someone dead or missing (`widow_memorial`, `missing_child_search`, `search_the_ruins`, `lost_child`) declares that intent explicitly and is validated against it, rather than relying on the absence of a check. |
| B-R6 | The datapack loader **fails the reload** for a quest whose objective targets `mode: family` with no condition constraining that same relation — a load error the pack author sees, not a player-facing mystery. |
| B-R7 | Situation offers pass through the same eligibility, condition and target-resolvability gates as static quests. |
| B-R8 | A situation whose instance names a focal villager can target *that* villager, not "a relative of whoever is talking". |
| B-R9 | An active quest whose bound target later dies or is permanently lost tells the player so and can be abandoned cleanly. It must not sit in the log forever at 0/1. |
| B-R10 | Objective text names the person when it can (`"Ada (the quest giver's sibling)"`) and says so plainly when it cannot — never a bare generic label for a target the mod has actually bound. |

### 2.4 Design — one relative predicate, one resolution path

**New type: `dev.otectus.mcaquests.compat.RelativeCandidate`**

```java
/**
 * One relative of a giver, with everything any caller needs to decide whether they are a person a
 * quest may be about. Built once per (giver, relation) pass and shared by the condition gate, the
 * offer-time resolvability check, the accept-time binder, and the display name.
 */
public record RelativeCandidate(UUID uuid,
                                String relation,      // the relation that produced this candidate
                                @Nullable String name,
                                boolean deceased,
                                boolean generated,    // MCA's probablyGenerated() filler ancestor
                                boolean player,
                                boolean loaded,       // an entity with this UUID exists right now
                                boolean nearby,       // loaded && within interact range of the giver
                                boolean sameVillage,  // on the giver's home village resident roll
                                boolean materialisable) { // could McaCompat.materializeRelative bring them in?

    /** The default "a quest may name this person" test (B-R4). */
    public boolean isReachable() {
        return !deceased && !generated && !player && (loaded || materialisable);
    }
}
```

**New method: `McaCompat.relativeCandidates(ServerLevel level, Entity giver, String relation)`**
returning `List<RelativeCandidate>` in a deterministic order (sort by UUID after the relation walk, so
selection is stable across passes). This replaces the three divergent walks that exist today:
`giverRelativeUuids`, `findGiverRelative` and the `relatives` switch inside `relativesWithStatus`.
Every one of those becomes a thin filter over `relativeCandidates`. Keep the existing
`try/catch (Throwable) → safe default` contract — `McaCompatSafeFailTest` enforces it **[verified]**.

`materialisable` must be computed from the *same* predicate `McaCompat.materializeRelative` uses
(`compat/McaCompat.java:933–945`: node exists, not deceased, not a player, not `probablyGenerated`, no
entity in the world) **[verified]** — extract that into a shared `canMaterialise(tree, uuid)` helper and
call it from both, so "the mod says they can be found" and "the mod can actually produce them" cannot
drift apart.

**Status semantics, corrected.** `matchesRelativeStatus` becomes a switch over a `RelativeCandidate`:

| status | today | after |
|---|---|---|
| `alive` | `!deceased && (loaded \|\| entry != null)` | `!deceased && !generated && !player && (loaded \|\| materialisable)` |
| `nearby` | `loaded && dist <= range` | unchanged, plus `!generated && !player` |
| `missing` | already correct **[verified]** | unchanged (it is the reference implementation) |
| `dead` | `deceased` | `deceased && !generated` — a fabricated ancestor is not a bereavement |
| `same_village` | resident roll only | resident roll **and** `!deceased && !generated && !player` |

Add two new statuses so intent can be stated instead of inferred:
`any_known` (the old loose behaviour, for packs that genuinely want it) and `reachable`
(`RelativeCandidate.isReachable()`). Document all seven in `DATAPACK.md`.

**Target selection.** `VillagerTarget` gains an optional `require` field:

```json
{ "mode": "family", "relation": "sibling", "require": "reachable" }
```

`require` defaults to `reachable` for `mode: family` — i.e. **the safe behaviour is the default**, and a
pack that wants a dead or missing target must say so (`"require": "dead"`, `"require": "missing"`,
`"require": "any_known"`). `resolveFrom`, `matches`, `describeResolved` and the accept-time binder all
select from `relativeCandidates(...).stream().filter(require::accepts)`; when that list is empty the
target is *unresolvable*, which is a first-class state, not `Optional.empty()` overloaded to mean five
different things.

**Offer-time resolvability.** Add to `QuestObjective`:

```java
/**
 * Why this objective cannot be OFFERED right now — the target it names does not exist, or cannot be
 * found. Distinct from {@link #isTriviallySatisfied} ("already done") and from
 * {@link #unavailableReason} ("temporarily unreadable"). Default: empty (offerable).
 */
default Optional<Component> unofferableReason(QuestContext context) {
    return Optional.empty();
}
```

Implement it once in a shared default on `VillagerTargeted` so all seven villager-targeted objectives
get it for free, and add it to the `eligibleOffers` filter chain immediately beside the existing
`isTriviallySatisfied` filter (`QuestManager.java:1014–1020`) **[verified]** — same position, same
"most expensive filter goes last" rationale, sharing the same `McaVillagerSnapshot`.

Memoize `relativeCandidates` on `McaVillagerSnapshot` next to the existing `familyMemo` /
`relativeStatusMemo` maps (`compat/McaVillagerSnapshot.java:54–55`) **[verified]** so the whole
eligibility pass walks the family tree once per relation, not once per quest.

**Accept-time.** `accept` re-runs `unofferableReason` for every objective and refuses with the reason
text if any is present (B-R2). `bindVillagerTargets` binds only a candidate that passes `require`;
if none does, the accept is refused rather than binding a phantom.

**Runtime loss.** `ObjectiveSupport.resolveLocked` gains a companion `boundTargetLost(...)`: a bound
target that is now flagged deceased, or has been absent for longer than a grace window, surfaces
through the existing `unavailableReason` channel (`QuestObjective.java:83–91`) **[verified]** — which
already renders as a reason line instead of a counter in the quest log
(`QuestManager.objectiveLines`) **[verified]**, and already has the "suspend, do not fail" semantics
1.4.0 introduced for Townstead. Reuse that machinery; do not invent a second one. Whether a lost target
should eventually fail the quest is a datapack decision: add `"fail_on_target_lost": true|false` to
`FailureSpec`, default `false` (suspend).

### 2.5 Implementation steps (Bug B)

1. **`compat/RelativeCandidate.java`** — new record as above.
2. **`compat/McaCompat.java`**
   - Add `relativeCandidates(level, giver, relation)` and the shared `canMaterialise(tree, uuid)`.
   - Rewrite `giverRelativeUuids`, `findGiverRelative`, `relativesWithStatus`, `matchesRelativeStatus`,
     `hasMissingRelative` and `getRelativeDisplayName` as filters over it.
   - **Delete the false javadoc claim at lines 464–467** and replace it with a statement of the new
     invariant. It is now true, so say so.
   - Keep every method's `try/catch (Throwable) → documented safe default`.
3. **`compat/McaVillagerSnapshot.java`** — memoize candidates per relation; expose
   `relativeCandidates(relation)` and keep `relativesWithStatus(relation, status)` as a filter over it.
4. **`quest/condition/McaConditionCodecs.java`** — add `reachable` and `any_known` to
   `RELATED_STATUSES`; add `spouse` to `FAMILY_RELATIONS` so the two vocabularies agree (B9), or
   deliberately reject it in both with a clear message. Do not leave them different.
5. **`quest/target/VillagerTarget.java`**
   - Add the `require` field (codec + `validate` + `describe` suffix).
   - Route `resolveFrom`, `resolve`, `matches`, `describeResolved` through `relativeCandidates`.
   - `describe()` for an unresolvable family target must never be shown as if it were a normal
     target — the quest should not have been offered at all, so make it an assertion-grade log line.
6. **`quest/objective/QuestObjective.java`** — add `unofferableReason(QuestContext)`.
7. **`quest/objective/VillagerTargeted.java`** — add a default implementation that checks
   `targetSelector()` against the candidate list. All seven implementors inherit it.
8. **`quest/QuestManager.java`** — add the `unofferableReason` filter to `eligibleOffers`; re-check in
   `accept`; tighten `bindVillagerTargets`.
9. **`quest/situation/DynamicOfferSource.java`** — run situation offers through the same filters:
   conditions (`effectiveConditions`), history cooldown, repeat rule, `isTriviallySatisfied` and
   `unofferableReason`. Extract the static path's filter chain from `eligibleOffers` into a shared
   `OfferFilters.passes(def, context, ...)` used by both, so they cannot drift.
10. **`quest/target/VillagerTarget.Mode`** — add `SITUATION_FOCUS`, resolving to
    `SituationInstance.villagerUuid()` of the situation the offer came from (the id is already carried:
    `ActiveQuest.situationInstance()` and `SituationIds.isSyntheticId` **[verified]**). Then fix
    `situations/cure_the_infected.json` to use it.
11. **`data/` validators** — add a `TargetGateValidator` run from the quest loader:
    - error: an objective with `mode: family` and `require: reachable` (i.e. the default) in a quest
      whose `conditions` contain no `related_villager_status`/`is_family_member` leaf constraining the
      same relation. (Fixes `lost_child/2_deeper.json` and `cure_the_infected.json` by refusing to load
      them until they declare a gate.)
    - error: `require` and the quest's gating `status` are contradictory (e.g. `require: reachable`
      gated on `status: dead`).
    - warning: `relation: "any"` with `require: reachable` — permitted, but the pack author should know
      the candidate order is spouse → parent → child → sibling.
    - Wire it into `/mcaquests validate` so pack authors get it from the command too.
12. **Content fixes** in `src/main/resources/data/mcaquests/mcaquests/`:
    - `quests/chains/lost_child/2_deeper.json` — add `"conditions": {"type":"mcaquests:related_villager_status","relation":"child","status":"missing"}` and `"require": "missing"` on the target.
    - `situations/cure_the_infected.json` — retarget to `{"mode":"situation_focus"}`.
    - `quests/relations/widow_memorial.json` — change the objective target from `any` to an explicit
      living relation with `"require": "reachable"`, so it can never bind the dead spouse it commemorates.
    - `quests/relations/missing_child_search.json`, `search_the_ruins.json`, `chains/lost_child/*` —
      add `"require": "missing"`.
    - Every remaining `mode: family` target — add the explicit `"require"` that matches its gate, so the
      pack is self-documenting and the new validator is exercised by the shipped content.
13. **`FailureSpec`** — add `fail_on_target_lost` (default `false`).

---

## 3. The rest of the class — everything else that is inert or unvalidated

Fixing only the two reported bugs leaves the same failure modes live elsewhere. All of the following
were found in the same sweep and are in scope for this work. Each is stated as: what is declared, what
actually happens, and what to do.

### 3.1 Controls that do nothing

| Item | Location **[verified]** | Reality | Action |
|---|---|---|---|
| `offerRefreshTicks` | `McaQuestsConfig.java:36,124` + `CONFIG.md:21` | Zero reads in `src/`. Offers reroll on the hardcoded MC day. | Implement (§1.5 step 5). |
| `enableDefaultQuestPack` | `McaQuestsConfig.java:32,116` | Zero reads. The built-in pack always loads. | Implement: skip `data/mcaquests/mcaquests/**` in the loaders when false, or delete the key and its docs. |
| `defaultQuestCooldownTicks` | `McaQuestsConfig.java:37,126` | Zero reads. The default comes from the hardcoded `24000` in `RepeatRule.DEFAULT`. | Implement: `RepeatRule` resolves its default from config at use time. |
| `requireOriginalVillagerForTurnIn` | `McaQuestsConfig.java:38,131` | Zero reads. Turn-in is governed entirely by `TurnInSpec`/`TurnInMode` (`QuestManager.java:823`). | Implement as a server-side override that forces `TurnInMode.ORIGINAL_GIVER`, or delete key + docs. |
| `maxConcurrentProjectsPerScope` | `McaQuestsConfig.java:79,260` | Zero reads. No cap is enforced in `project/`. | Implement in the project opener, or delete key + docs. |
| `townsteadPollIntervalTicks` | `McaQuestsConfig.java:107,361` | Zero reads (its project-side sibling `townsteadProjectPollIntervalTicks` *is* read). | Implement in the Townstead objective poll, or delete key + docs. |
| `dialogue.decline` | `QuestDefinition.java:56`; advertised in `McaQuestsCommand.java:693` | Parsed, never displayed. | Display on decline (§1.5 step 4e). |
| `dialogue.cooldown`, `dialogue.locked` | `QuestDefinition.java:60–61` | Parsed, never displayed. Zero call sites. | Display them: `cooldown` when a villager has a quest on cooldown for this player, `locked` when conditions are unmet but the quest exists — both are useful "why isn't this offered" affordances and are already authored in the pack. Otherwise delete the constants and the JSON. |
| `ProjectPhase` `dialogue.ready` / `dialogue.complete` | `ProjectPhase.java:24–25` javadoc vs the single call site `ProjectManager.java:230` | Only `offer`/`in_progress` are ever requested. `ProjectMenuStatus` has no `READY`, and complete projects are filtered out before card build (`ProjectManager.java:190–194`). | Either wire the states through or correct the javadoc, the JSON and `DATAPACK.md`. |
| `SponsorSpec.required_count` | `project/SponsorSpec.java:18,25,34` | Its own javadoc says "informational/UX"; zero call sites, and it is not displayed either. | Display it in the project card, or remove the field. |
| `VillagerDeathTrigger.relation` | `quest/situation/trigger/VillagerDeathTrigger.java:16,32–34` | `matches()` is `return true;`. The javadoc claims "that player-relative filter is applied at offer eligibility" — there is no such call site anywhere. | Implement the filter (in `DynamicOfferSource`, using `relativeCandidates` from §2.4) or delete the field. Stale javadoc that asserts a guarantee the code does not provide is the most expensive kind of comment in this codebase — it is the same lie as §2.2 B1. |
| `MissingKinTrigger.relation` | `quest/situation/trigger/MissingKinTrigger.java:14,29–31` | `matches()` is `return true;`; zero reads of `relation()`. | Same. |
| Situation `outcomes.failure` / `outcomes.cleared` reputation & hearts | `SituationManager.java:207,225` pass `player = null`; `applyOutcome:296–324` requires non-null | A datapack's failure penalty is parsed and silently discarded. The javadoc gives a rationale ("with nobody to credit there is nothing to award"), but `SituationInstance.participants()` exists and is populated. | Apply failure outcomes to the recorded participants, or make the loader reject `failure.reputation`/`failure.hearts` with a message saying they are unsupported. Silent discard is not an option. |

### 3.2 Targets that are never validated

| Item | Location **[verified]** | Reality | Action |
|---|---|---|---|
| `StructureTarget` | `quest/target/StructureTarget.java:58` | `validate()` checks field presence only, never registry/tag membership. An unknown structure id loads fine and then never matches — a permanently uncompletable quest. | Validate ids and tags against the level's registries at first use, and surface the failure through `unofferableReason`. |
| `BiomeTarget` | `quest/target/BiomeTarget.java` | No `validate()` at all. Same failure mode. | Same. |
| `LocationAnchor` | `quest/target/LocationAnchor.java` | Fails closed at runtime (objective pauses), but there is no offer-time check outside `EscortEntityObjective`/`ReachLocationObjective`. | Route through `unofferableReason` — e.g. do not offer "escort them to the village centre" when the giver has no home village. |
| `ProjectObjective` | `project/objective/` | The interface has **no** `validate()` and no offer-time check at all — strictly weaker than `QuestObjective`. | Add both, mirroring the quest side. |
| Translation keys | anywhere | No validator checks that a datapack's `translate` keys exist in `assets/mcaquests/lang/en_us.json`. | Add a warning-level check to `/mcaquests validate` for keys in the `mcaquests` namespace. |

`BlockTarget`, `ItemTarget` and `EntityTarget` are safe: they decode through `byNameCodec()` on static
registries, so an unknown id is a hard load error **[verified]**. No action.

### 3.3 The two invariants to enforce going forward

Write these into `DATAPACK.md` and the contributing notes, and make the tests in §6 the enforcement:

> **I1 — No inert surface.** Every config key, datapack field, dialogue state and UI button either
> changes observable state or does not exist. A field that is parsed and ignored is a bug with a
> documentation page.

> **I2 — Nothing is offered that names something unresolvable.** Any content that references a
> villager, structure, biome, location or village must prove that reference resolves *before* it is
> offered, using the same predicate that will later resolve it for real.

---

## 4. Phasing

Each phase compiles and has green tests before the next begins.

| Phase | Content | Gate |
|---|---|---|
| **P0** | Regression tests first, all failing: `OfferSessionDeclineTest`, `OfferStabilityTest`, `FamilyTargetGateTest`, `RelativeCandidateFilterTest`, `DeadConfigTest`, `DialogueStateCoverageTest`. | `./gradlew test` fails with exactly these, for the documented reasons. |
| **P1** | §2 — `RelativeCandidate`, `McaCompat` rewrite, snapshot memoization, status semantics. No behaviour change to the offer pipeline yet. | `McaCompatSafeFailTest`, `McaConditionParsingTest`, `RelativeCandidateFilterTest` green. |
| **P2** | §2 — `VillagerTarget.require`, `unofferableReason`, `eligibleOffers` filter, accept-time re-check, binder tightening. | `FamilyTargetGateTest`, `QuestFilterTest`, `QuestLogicTest` green. |
| **P3** | §2 — `OfferFilters` extraction; `DynamicOfferSource` routed through it; `SITUATION_FOCUS` mode. | `SituationOfferEligibilityTest` green, extended. |
| **P4** | §2 — `TargetGateValidator`; content JSON fixes; `/mcaquests validate` wiring. | Built-in pack validates clean; deliberately-broken fixtures fail with the expected message. |
| **P5** | §1 — `OfferSession`/`OfferSessions`/`OfferSessionService`; `sendMenu` rewired; NBT round-trip. | `OfferStabilityTest` green. |
| **P6** | §1 — real `decline`, `DECLINED` outcome, `quest_declined` condition, `QuestDeclinedEvent`, decline dialogue, `declineCooldownTicks`, `offerRefreshTicks`. | `OfferSessionDeclineTest` green. |
| **P7** | §3.1 — every remaining inert control implemented or removed (config, dialogue states, trigger `relation` fields, situation failure outcomes, `required_count`). | `DeadConfigTest`, `DialogueStateCoverageTest` green. |
| **P8** | §3.2 — structure/biome/anchor/project validation; lang-key check. | `/mcaquests validate` clean on the shipped pack. |
| **P9** | Docs: `CHANGELOG.md`, `CONFIG.md`, `DATAPACK.md`, `README.md`, `TOWNSTEAD.md` if touched. Version bump. | Docs match code; no doc describes a removed key. |

---

## 5. Save, protocol and datapack compatibility

- **Player NBT** — `PlayerQuestData.save()` gains `"offers"`. `load()` must treat an absent `"offers"`
  compound as an empty store. `QuestHistory` gains a `DECLINED` value inside the existing
  `outcomes`/`outcomes_by_giver` string keys; no format change. `ActiveQuest` is untouched.
- **World `SavedData`** — untouched. Do not change any `DATA_NAME`.
- **Network** — leave the 19 registered packets alone if possible; then `PROTOCOL_VERSION` stays `"10"`
  **[verified as current]** and mixed-version clients are unaffected. If a packet shape must change,
  bump to `"11"` and add the line to `QuestNetwork`'s protocol-history comment.
- **Datapack** — `require` on `VillagerTarget` is optional with a **safe default**, which means
  existing third-party packs get the fix without editing. But the new `TargetGateValidator` (§2.5 step
  11) will now *reject* a third-party quest that targets family with no gate. That is intentional and is
  a breaking change for pack authors: it must be a headline `### Changed` entry in `CHANGELOG.md`, and
  the error message must name the file, the objective index, the relation, and the exact `conditions`
  block to add. Consider shipping it as an error under `strictJsonValidation` (already a config key
  **[verified]**) and a loud warning otherwise, for one release, then promote to hard error.
- **Version** — this is a behaviour change to offers and a datapack-validation tightening: `1.5.0`.

---

## 6. Tests

All new tests go in `src/test/java/dev/otectus/mcaquests/` alongside the existing 56 **[verified]**.
Keep them loader-independent in the style of `QuestFilterTest` / `TemplateResolutionTest` where possible.

**Bug A**

1. `OfferStabilityTest` — build a session, render twice, assert the two card lists are equal in quest
   ids, template values *and* dialogue components. Then register a `QuestDialogueResolver` that returns
   a fresh random line every call (the MCA: Conversations stand-in) and assert the rendered dialogue is
   *still* identical across the two renders. This is the direct regression test for the reported
   "the story changes" symptom.
2. `OfferSessionDeclineTest` —
   - declining removes exactly that quest and leaves the other slots byte-identical;
   - the declined quest does not reappear on the next `sendMenu`;
   - it does not reappear after a save/load round-trip of `PlayerQuestData`;
   - it *does* reappear after `offerRefreshTicks` elapses (and after `declineCooldownTicks` when set);
   - declining a quest id that is not in the session is rejected and changes nothing (client-trust test);
   - declining twice is idempotent;
   - declining records `Outcome.DECLINED` and leaves completion counts untouched.
3. `OfferRefreshTest` — set `offerRefreshTicks` to 6000 and assert the session rerolls four times a day,
   and that a session survives a menu close/reopen within the window.
4. `DeclineDialogueTest` — the `decline` line is resolved through `QuestDialogueHooks` with the static
   JSON line as the fallback, and a throwing resolver falls back rather than breaking the decline.

**Bug B**

5. `RelativeCandidateFilterTest` — table-driven over a fake family tree: deceased, `probablyGenerated`,
   player node, no-entity-but-materialisable, no-entity-and-not-materialisable, loaded, nearby,
   same-village-but-dead. Assert each status (`alive`/`nearby`/`missing`/`dead`/`same_village`/
   `reachable`/`any_known`) selects exactly the intended rows. **Include the dead-sibling-still-on-the-
   village-roll row explicitly — that is the reported bug.**
6. `FamilyTargetGateTest` — a `letter_to_brother`-shaped quest with a giver whose only sibling is
   deceased is **not offered**; with a living same-village sibling it **is** offered and binds that
   exact sibling; with two siblings (one dead, one alive) it binds the living one.
7. `GateTargetAgreementTest` — for every shipped quest with a `mode: family` objective, assert that any
   candidate satisfying the quest's gate also satisfies the objective's `require`, and vice versa. This
   is the executable form of the javadoc claim that was false.
8. `TargetGateValidatorTest` — a quest with a family target and no gate fails validation with a message
   naming the file and relation; the shipped pack validates clean.
9. `AcceptRevalidationTest` — the target becomes unresolvable between menu build and accept; accept is
   refused, no `ActiveQuest` is created, no reward is frozen, no binding is written.
10. `BoundTargetLostTest` — a bound target dies mid-quest; the objective reports an
    `unavailableReason`, the quest does not fail (default), stays abandonable, and resumes if the
    target returns.
11. `SituationOfferGateTest` — extend `SituationOfferEligibilityTest`: situation offers now respect
    conditions, cooldowns, repeat rules, `isTriviallySatisfied` and `unofferableReason`; a
    `situation_focus` target resolves to the instance's focal villager.

**Class-level guards (these are what stop the bug class returning)**

12. `DeadConfigTest` — reflect over every `ForgeConfigSpec.*Value` field on `McaQuestsConfig.COMMON` and
    `.CLIENT`, and assert each field name appears in at least one `.java` file outside
    `McaQuestsConfig.java`. Fails the build on any newly-added dead key.
13. `DialogueStateCoverageTest` — assert every `QuestDefinition` dialogue-state constant is passed to
    `dialogueOr` somewhere in `src/main/java`, and that every state key used in the shipped JSON is a
    declared constant.
14. `DatapackFieldCoverageTest` — for the record types listed in §3.1, assert each component has at
    least one read site outside its own declaring class. Maintain an explicit, commented allow-list for
    the few fields that are legitimately write-only, so future exceptions are a deliberate edit.

---

## 7. Manual verification (dev `runClient` with MCA installed)

Do all of these in one world. `1.4.0` **[verified]** already loads MCA in dev, so this is testable.

**Decline**

1. Talk to a villager with three offers. Note all three titles, all three dialogue lines, and the
   template numbers/items in each. Close and reopen the menu five times → nothing changes.
2. Install MCA: Conversations (or register a stub resolver that returns `"line #" + counter`). Reopen
   five times → the lines still do not change. *(This is the reported symptom.)*
3. Decline the middle offer. → It is replaced by a different quest; **the other two cards are
   character-for-character identical**; the villager says the quest's `decline` line in chat.
4. Reopen → the declined quest is gone. Relog → still gone. Restart the server → still gone.
5. `/mcaquests debug offers` → shows the session, its refresh countdown, and the declined entry.
6. Set `offerRefreshTicks = 6000`, wait 6000 ticks → a fresh set is drawn and the declined quest is
   eligible again.
7. Decline every offer → the menu shows the `NO_QUESTS` state, not an empty broken card list.
8. Decline a situation offer → it disappears for you; the situation stays open for other players and
   still resolves normally.

**Family**

9. Find (or `/summon`) two MCA villagers, make them siblings via MCA's editor, keep both alive and in
   one village. → `letter_to_brother` can be offered; the log names the actual sibling; the glow
   highlights them; delivering to the *other* sibling does not credit it; delivering to the bound one
   completes it.
10. Kill one sibling. → the quest is **no longer offered** by the survivor. Confirm the dead sibling is
    still on MCA's village resident roll (`/mcaquests debug mca` on the giver) — i.e. the fix is
    working *despite* MCA's stale roll, which is the actual root cause.
11. A villager with no siblings at all → `letter_to_brother` and `mend_the_quarrel` never appear.
12. A freshly spawned villager with only MCA's two invented deceased parents → no `relation: any`
    quest (`kin_errand`, `family_reunion_feast`, `cure_infected_kin`) is offered on the strength of
    those phantoms.
13. Accept a family quest, then kill the bound relative. → the objective line shows a reason instead of
    a counter, the quest does not fail, it stays abandonable, and abandoning it works from both the
    villager menu and the quest log.
14. `widow_memorial` → offered only to a villager with a dead spouse *and* a living same-village
    relative; the delivery target is the living relative, never the dead spouse.
15. Trigger `cure_the_infected` → the objective names the actually-infected villager, from any giver in
    the village.
16. `/mcaquests validate` → clean. Then drop a quest JSON with a `mode: family` target and no
    conditions into a test datapack → the reload reports an error naming that file and relation.

---

## 8. Non-goals, risks, and things to leave alone

- **Do not fix MCA.** The stale village resident roll (`Village.residentNames` never pruned on death)
  is upstream behaviour. This mod's job is to be correct in spite of it. Do not write to MCA's data
  structures.
- **Do not change `McaCompat`'s isolation contract.** Every MCA symbol is resolved reflectively through
  `compat/mca/McaBinding` / `McaHandles`, and `NoMcaStaticLinkTest` enforces that no MCA type is
  statically linked **[verified]**. `RelativeCandidate` must contain only primitives, `UUID`, `String`
  and `boolean` — no MCA type may escape `McaCompat`.
- **Do not weaken the "suspend, never fail" contract** introduced in 1.4.0. A missing target is a
  suspension, not a failure, unless the pack opts in.
- **Do not make decline punitive.** No hearts, no reputation, no failure outcome. The reporter's
  complaint is that decline does nothing, not that it should hurt.
- **Watch the cost of the new offer-time check.** `unofferableReason` walks the family tree. It must sit
  *last* in the filter chain (after profession/hearts/cooldown/conditions have already cut the pool) and
  must read through the memoized `McaVillagerSnapshot`, or every villager interaction gets slower. Add a
  timing line to `/mcaquests debug quest` while developing.
- **`getDayTime()` vs `getGameTime()`.** The existing offer seed uses `getDayTime()` (which `/time set`
  and sleeping move) while cooldowns use `getGameTime()` (monotonic). Persist the session against
  `getGameTime()` so a night's sleep does not silently reroll everything, and note the change in the
  changelog.
- **Concurrency.** `QuestDialogueHooks.resolver` is `volatile` and add-on resolvers may be slow. Resolve
  offer dialogue once, on the server thread, at session draw time — do not call a resolver per render,
  which is what today's code effectively does.

---

## Appendix A — File-by-file change list

**New**

```
state/OfferSession.java
state/OfferSessions.java
quest/OfferSessionService.java
quest/OfferFilters.java
compat/RelativeCandidate.java
quest/condition/leaf/QuestDeclinedCondition.java
api/event/QuestDeclinedEvent.java
data/TargetGateValidator.java
```

**Modified (main)**

```
quest/QuestManager.java              decline(); session-backed sendMenu; accept re-validation; binder
quest/QuestDefinition.java           DECLINE/COOLDOWN/LOCKED wired; effectiveConditions unchanged
quest/target/VillagerTarget.java     require field; candidate-based resolve/matches/describe; SITUATION_FOCUS
quest/objective/QuestObjective.java  unofferableReason()
quest/objective/VillagerTargeted.java default unofferableReason for all seven implementors
quest/objective/ObjectiveSupport.java boundTargetLost()
quest/target/StructureTarget.java    registry validation
quest/target/BiomeTarget.java        registry validation
quest/situation/DynamicOfferSource.java  routed through OfferFilters
quest/situation/SituationManager.java    failure/cleared outcomes applied to participants
quest/situation/trigger/VillagerDeathTrigger.java  relation implemented or removed
quest/situation/trigger/MissingKinTrigger.java     relation implemented or removed
quest/condition/McaConditionCodecs.java  status + relation vocabularies reconciled
quest/condition/ConditionTypes.java      register quest_declined
quest/FailureSpec.java               fail_on_target_lost
quest/RepeatRule.java                default cooldown from config
compat/McaCompat.java                relativeCandidates(); canMaterialise(); all family walks rewritten; false javadoc corrected
compat/McaVillagerSnapshot.java      candidate memoization
state/PlayerQuestData.java           offers() + NBT
state/QuestHistory.java              Outcome.DECLINED
McaQuestsConfig.java                 six dead keys implemented or removed; declineCooldownTicks; declineRefillsSlot
command/McaQuestsCommand.java        debug offers / debug offers reroll; validate wiring
project/SponsorSpec.java             required_count displayed or removed
project/ProjectManager.java          phase dialogue states
project/objective/ProjectObjective.java  validate() + offer-time check
```

**Modified (resources)**

```
data/mcaquests/mcaquests/quests/chains/lost_child/2_deeper.json      add gate + require
data/mcaquests/mcaquests/situations/cure_the_infected.json           situation_focus target
data/mcaquests/mcaquests/quests/relations/widow_memorial.json        living target
data/mcaquests/mcaquests/quests/relations/missing_child_search.json  require: missing
data/mcaquests/mcaquests/quests/relations/search_the_ruins.json      require: missing
... all remaining `"mode": "family"` targets: explicit `require`
assets/mcaquests/lang/en_us.json                                     decline/cooldown/locked messages, unavailable-target reasons
```

**Docs**

```
CHANGELOG.md   1.5.0: decline implemented; offer sessions; family-target integrity; datapack validation tightened (breaking for packs)
CONFIG.md      offerRefreshTicks now honoured; declineCooldownTicks; declineRefillsSlot; any removed keys deleted
DATAPACK.md    VillagerTarget.require; new statuses; situation_focus; quest_declined; fail_on_target_lost; the two invariants
README.md      QuestDeclinedEvent in the API list
```

## Appendix B — The one-line summary of each bug, for the changelog

- **Declining a quest now declines it.** The offer was recomputed from scratch every time the menu
  opened, so a decline changed nothing and the same three quests came straight back. A villager's
  offers are now drawn once, remembered, and only the card you turn down is replaced — the other
  offers, their details and their dialogue stay exactly as they were, and the villager finally says the
  line the quest wrote for being turned down.
- **No more letters to siblings who do not exist.** A family quest was gated on one question ("is there
  a sibling in this village?") and then handed a different answer ("give me any sibling at all"), and
  the first question counted the dead, because MCA keeps the departed on the village roll. Both now ask
  the same question, and the answer excludes the dead, invented ancestors, and anyone who cannot be
  found — checked when the quest is offered, and again when you accept it.
