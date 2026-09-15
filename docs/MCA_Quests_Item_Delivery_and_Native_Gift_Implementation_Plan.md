# MCA: Quests — Intuitive Item Delivery and Native MCA Gift Support

## Implementation brief

Implement one consistent, server-authoritative item-delivery system for MCA: Quests. Players must be able to hand in items explicitly through the quest interface or through MCA's actual **Gift** action. Both routes must share recipient rules, item matching, partial-delivery accounting, inventory transactions, and completion safeguards.

This is a targeted delivery improvement, not permission to rewrite the quest framework, change loaders, replace MCA's entire interaction system, or redesign unrelated objectives. Preserve existing quests, saved progress, template values, rewards, integrations, and public compatibility constructors.

### Reviewed baseline and limits

- Repository: `otectus/MCAQuests`, default branch `main`.
- Reviewed commit: `af372b6951191eac2b87b35e3108af3a7f64edfe`. The branch was checked again on September 15, 2026 and still pointed to this commit.
- Declared baseline: MCA: Quests **1.6.4**, Minecraft **1.20.1**, Forge **47.4.10**, Java **17**. [Q1]
- Upstream MCA reference: branch `1.20.1`, commit `4d824551b30654e5792e19e84f3933e3e3d90ea2`. Its source identifies the gift lifecycle; it is **not** a substitute for examining each supported published Forge JAR. [M1][M2]
- This report is based on source inspection, including the earlier audit and rechecking the main branch, both reported quest definitions, villager-delivery implementation, item matcher, and upstream gift implementation. Minecraft was not launched and the reported modpack was not reproduced. Builds, runtime mixin transformations, and gameplay tests remain implementation acceptance work.

Before editing, read repository instructions, confirm the checkout and current HEAD, preserve uncommitted work, and reconcile changes since the reviewed commit. Do not assume the player's installed release equals this baseline. Read `CLAUDE.md`, `MODMAP.md`, and the current build/test configuration before changing compatibility code. [Q2]

## 1. Findings that the implementation must address

### F1. Two delivery objective types currently mean different things

`ItemDeliveryObjective` (`mcaquests:item_delivery`) computes progress from the player's current inventory. It consumes or transfers items during final quest turn-in. Its `current()` and `isSatisfied()` do not currently interpret an accumulated delivered-item count. [Q3]

`DeliverToVillagerObjective` (`mcaquests:deliver_to_villager`) waits for a qualifying interaction with its specified recipient. It requires the entire requested quantity at once, consumes/transfers the items at that interaction, and records a binary completion flag through `progress.count()`. Its `required()` is **1**, even when the payload is two crossbows or six rods. [Q4]

Consequently, an inventory containing everything requested can still produce an incomplete delivery line. Merely changing the text, or setting `progress.count()` for `item_delivery`, will not fix both systems.

### F2. Both reported examples use the interaction-driven type

`quests/adventurer/last_banner_home.json` requests 12 pillager kills, 4 vindicator kills, and a `deliver_to_villager` objective for two `minecraft:crossbow` items, with `recipient.mode = self`. Its quest ID is `mcaquests:last_banner_home`. [Q5]

The matching built-in Nether quest, `quests/adventurer/nether_relay.json`, requests visiting the Nether, killing eight blazes with a fortress source constraint, and delivering six `minecraft:blaze_rod` items, also with `recipient.mode = self`. Its quest ID is `mcaquests:nether_relay`. The player's description matches this definition, but their precise quest ID and installed release still need confirmation. [Q6]

For these definitions, `self` means the original giver, not any villager of a similar profession. Neither definition requests an inventory-transfer destination; their payloads are consumed at hand-off. A full villager inventory therefore is not the condition blocking these two default delivery definitions. [Q4][Q5][Q6][Q9]

These are multi-objective definitions. Do not assume that the listed objectives are sequentially activated stages: the inspected event dispatcher visits matching objectives of active quests without a preceding-objective-completed gate. Preserve authored activation rules where they actually exist; do not invent a new kill-first requirement. [Q7]

### F3. Dropped, damaged crossbows are not rejected by the item matcher

`ItemTarget.matches()` checks item identity or tag membership, not pristine durability, enchantments, or a default NBT representation. `ObjectiveSupport.countMatching()` sums matching counts across the player inventory. Two unstackable crossbows in separate slots can meet a count of two. [Q8][Q9]

Do not “fix” this by requiring repaired crossbows, treating the required amount as one stack, or stripping item data. Characterize damage, names, enchantments, and charged crossbows in tests.

### F4. The hand-in action is hidden and can fail silently

`QuestProgressEvents.onTalkToVillager()` observes a non-cancelled, server-side, main-hand `PlayerInteractEvent.EntityInteract`. It calls healing, curing, and villager-delivery objectives. There is no empty-hand requirement for this delivery hook. The separate empty-hand conversation check in `QuestEventHandlers` must not be confused with delivery eligibility. [Q7][Q10]

The villager-delivery routine returns silently for an incorrect recipient, insufficient quantity, or an unsuccessful transfer. A comment says the interaction handler explains refusal, but the inspected handler does not return or display a delivery result. [Q4][Q7]

The hook can also take delivery goods merely because the player interacted to open another menu. It loops over matching objectives independently, rather than letting the player choose which obligation to pay. These are design risks to remove, not proof of a demonstrated duplication exploit. [Q7]

Cancelled interactions or a mod using another interaction path are plausible explanations for a hand-off that never fires. They are not established causes in the reported pack. Investigate `EntityInteract` versus `EntityInteractSpecific`, event cancellation, and the actual native command path with instrumentation. Do not consume items from cancelled interactions as a workaround.

### F5. The quest menu offers no explicit delivery action

`QuestMenuScreen.addCardButtons()` offers Accept/Decline for an offer, Complete/Abandon when ready, and only Abandon while in progress. A player seeing an incomplete delivery objective is not presented with a hand-in button. The objective renderer appends the numeric `current/required`, so the binary villager-delivery state is also exposed as a hand-off count rather than useful item progress. [Q11]

`QuestManager.turnIn()` requires `isComplete()` before entering finalization. Therefore, adding a Complete button without changing the action path cannot satisfy an undelivered `deliver_to_villager` objective. [Q12]

### F6. MCA Gift is a different server path

In the inspected MCA 1.20.1 source, `VillagerCommandHandler.handle(..., "gift")` calls `BreedableRelationship.giveGift(...)`. This is not the Forge interaction event consumed by the inspected delivery path. [M1]

The ordinary gift implementation can reject unknown gifts, unsuitable gifts, saturated gifts, or gifts without inventory room. An accepted ordinary gift takes **one item** from the main-hand stack and inserts that item into the villager's inventory. It also has special handling for cake, dyes, name tags, golden apples, and other special gifts. [M2]

`giveGift()` returns `void`; reaching a RETURN injection does not establish success. Heart changes and a positive-looking interaction are not a safe transfer receipt either. Do not infer gift delivery from those signals or from a generic inventory decrement. [M2]

## 2. Required player experience

### 2.1 One obvious bulk-delivery path

Keep the existing quest screen. Add a prominent **Deliver items** action to active quest cards with eligible item obligations for the villager being addressed. Opening the conversation, opening the quest screen, hovering a button, or closing a screen must not consume goods.

The server must include incoming deliveries from quests given by **other** villagers. A recipient-only villager must be able to accept a delivery even when they did not give the quest and have no quest offers of their own. Audit both card generation and the entry-point visibility checks: adding a button to a screen the recipient cannot open is not sufficient.

Example card, using a placeholder villager name:

```text
Bring the Last Banner Home
Deliver crossbows to Rowan
Delivered: 0 / 2     Available: 2

[Deliver 2 crossbows]

Or hold a crossbow and choose Gift in Rowan's MCA menu.
```

After a partial hand-in:

```text
Delivered: 1 / 2     Available: 0
You have already delivered one crossbow. Bring one more to Rowan.
```

Treat **available in inventory**, **delivered**, and **ready to claim rewards** as separate facts. Never show an undelivered payload as already handed over merely because the player carries it.

When all other work is complete and the current villager can finalize the quest, expose **Deliver & complete**. This action must enter the same validated completion flow as the legacy Complete action. When delivery goes to a different villager, give explicit return-to-giver guidance instead of silently bypassing the quest's turn-in mode.

A quest-log view opened away from the recipient should show the recipient and tracking guidance, but must not provide remote hand-in. Explain a disabled action, such as “Visit Rowan to deliver these items,” rather than presenting an unexplained grey button.

### 2.2 Native Gift is an equal alternative

Choosing MCA's actual Gift action while holding an eligible item must advance the matching delivery. Preserve the ordinary one-item-per-Gift gesture: two crossbows can be delivered with two Gift actions, and six rods with six actions. The Deliver button provides the bulk alternative.

Gift must use the **actual main-hand slot**, not silently search another slot for an equivalent item. A player deliberately holding their named crossbow is authorizing that crossbow; holding an ordinary one must not cause an enchanted one elsewhere to be selected instead.

Mixing routes is supported: gift one crossbow, deliver the other through the quest menu; gift two rods, hand in the remaining four through Deliver; gift all items, then claim any remaining reward through the usual valid turn-in route. Previously delivered units are never charged again.

### 2.3 Partial delivery and selection

Enable partial deliveries for the two genuine item-delivery types by default, without requiring all existing JSON files to be rewritten. Record each committed contribution immediately through normal saved quest state.

Bulk hand-in defaults to the lesser of available selected goods and outstanding quantity. Do not consume surplus. Show the intended quantity and, for non-fungible items, the exact selected stacks before confirmation. Ordinary simple stack deliveries should remain a one-click action; expand a selection row only when it adds value.

Use regular inventory/hotbar goods for automatic bulk selection. Do not silently strip worn armor or a protected offhand stack. Allow explicitly selected offhand goods where appropriate, and make the displayed “available” count use exactly the same slot policy. Do not recurse into backpacks, bundles, shulker boxes, ender chests, or external storage without a separately specified integration.

Default automatic selection should prefer ordinary, unnamed, unenchanted items. When only valuable variants remain, show their tooltips and obtain explicit selection rather than treating them as generic stock. Damage alone must not disqualify the pillager crossbows at issue. A selected charged crossbow must retain its data if transferred; treat its loaded projectile as a reason to show the item tooltip, not to rewrite its NBT.

### 2.4 Multiple matching quests

For an explicit Deliver action, the selected active quest instance and objective determine allocation.

For native Gift: route automatically when there is exactly one eligible matching obligation. When several match, prefer an explicitly tracked matching quest only if its matching objective is unambiguous. Otherwise open a small inline chooser in the existing delivery UI, without taking the item first. Revalidate the held slot when the selection returns.

One item pays one item obligation. Never credit the same unit to every matching quest or fan a Gift receipt out to village project contributions. Independent non-item quest effects may still observe legitimate state changes, but that does not authorize charging the item again.

## 3. Implement a shared delivery service

Add a focused internal package such as `quest/delivery/`. Suggested components are responsibilities, not a requirement to create many tiny files:

| Component | Responsibility |
|---|---|
| `DeliveryService` | Find eligible obligations, preview transactions, commit hand-ins, and settle resulting quest state. |
| `DeliveryLedger` | Read/migrate/write committed delivery units and proof acknowledgements. |
| `DeliveryRecipientResolver` | Apply objective recipients and effective turn-in rules without duplicating UI logic. |
| `DeliveryRequest` / `DeliveryResult` | Carry intent and structured outcomes, not client-authoritative progress. |
| Delivery view model | Expose delivered, available, remaining, recipient, method capability, and failure reason to the client. |

Both objective implementations, the menu action, native Gift bridge, and legacy turn-in must use this core. Do not create separate `consumeItems()` implementations for each entry point.

Always operate on `active.resolve(base)` so accepted template values are honored. Reuse locked target resolution and the existing per-objective unavailability logic. Preserve global guards that really do suspend an entire applicable quest, but do not make an unrelated paused Townstead objective unnecessarily block an otherwise valid ordinary delivery. [Q7][Q12]

### 3.1 Transaction contract

For each request, validate the caller and exact active objective, resolve the authorized recipient and destination, then plan the selected source debits and any destination insertions. Only commit when the selected batch can succeed in full. “Partial delivery” means an explicitly bounded smaller batch, not silently losing part of a failed transfer.

Reuse and extend `InventoryTransfer.Plan`. It already snapshots inventories, reserves across multiple requirements, preserves stack data, checks snapshots before committing, removes source goods before insertion, and attempts rollback on container errors. [Q13]

**Necessary extension:** the existing plan reserves by predicate across its source. Add slot-specific or slot-allowlist reservations so native Gift consumes the held item and the menu consumes exactly the selected items. Do not approximate this by reserving any stack with the same item ID.

The commit flow must provide these guarantees:

1. Run world/inventory mutations on the server thread. Validate before mutating and cap request sizes and quantities. [F1]
2. Hold an in-flight/reentrancy guard while inventory callbacks execute. A callback must not initiate another delivery against the same source transaction.
3. Reserve each physical unit at most once across a bulk operation. Aggregate overlapping item and tag requirements rather than independently asserting they all have enough stock.
4. Commit inventory changes once; then record exactly the successfully committed units, before dispatching progress/completion callbacks or success messages.
5. On ordinary failure, no debit and no ledger increment. If an exceptional container cannot be rolled back, quarantine the affected operation and emit an actionable diagnostic; do not silently retry and pretend it is safe.
6. Settle progress and refresh the open menu, log, HUD, and guidance immediately after a successful commit. Do not require closing the screen, relogging, or waiting for the periodic poll.

Use a per-request identity and state revision for replayed menu requests. A re-accepted copy of a quest must have a new active-instance identity. Add an optional persisted active-instance UUID with lazy migration if existing identifiers cannot distinguish copies safely; retain compatibility constructors and old lookup APIs.

For native Gift, process each actual invocation once. Do not credit the same invocation at both the command handler and `giveGift`, or again from an inventory observer. Genuine successive Gift invocations may consume successive items up to the remaining requirement. Do not claim that arbitrary replay of an unmodified native command can be distinguished from another intentional Gift without a native request nonce; the important invariant is that each credited unit corresponds to its own committed item removal and rewards cannot duplicate.

Inventory callback rollback and request idempotence are not proof of atomic durability across player, entity, and world save files during sudden power loss. State the tested persistence guarantee accurately. A crash journal would be a separate, explicit design, not an incidental property of `setChanged()`.

### 3.2 Completion is not delivery

The service records deposits; `QuestManager` remains responsible for quest completion, reward claiming, frozen reward amounts, history, cooldowns, reputation, lifecycle callbacks, and chain/situation behavior. [Q12][Q14]

Extend `prepareDeliveries()` to reserve only the still-owed quantity. Do not execute the old whole-count consumption after deposits have been credited.

For an explicit combined Deliver & complete action, preflight the entire selected delivery and the completion eligibility. Once delivery is committed, preserve its ledger even if a later reward policy refuses finalization. Explain that goods were delivered and the remaining blocker concerns completion; never charge again on retry. Preserve the existing reward-failure policy rather than inventing a second payout path inside the Gift hook.

Respect `original_giver`, `same_profession`, other authored turn-in modes, and the existing self-complete behavior. A delivery recipient and a reward claimant can be different entities. Do not auto-claim unrelated quests because a player gave one gift.

## 4. Progress representation and migration

`ObjectiveProgress.extra()` is already persisted with objective state and can host an additive, namespaced delivery compound. [Q15] For example:

```text
extra.mcaquests_delivery = {
  schema: 1,
  delivered_units: N,
  proof_acknowledged: false,
  definition_fingerprint: "..."
}
```

Treat this as a proposed layout. Centralize its encoding, validation, and migration in `DeliveryLedger`. Store only committed contributions. Clamp malformed counts to safe ranges, diagnose invalid values, and do not replace unrelated objective state.

For a genuine delivery:

```text
remaining = max(0, requiredUnits - deliveredUnits)
deposit = min(explicitlyAuthorizedUnits, availableSelectedUnits, remaining)
```

For `deliver_to_villager`, the item requirement is satisfied when committed units reach `itemCount`. Keep old binary `progress.count()` as a compatibility mirror if required, but do not use it as the new item-unit ledger. Update target highlighting and every other completion check that currently reads the binary count directly.

For `item_delivery`, preserve the legacy ability to complete from carried items: finalization can use committed units plus a valid reservation of the remaining inventory goods. Its UI must still distinguish what has been deposited from what is merely available. Aggregate reservation determines whether a multi-objective quest is truly ready to pay; two independent checks must not promise two obligations against the same stock.

### Required old-save migration

| Old state | New interpretation |
|---|---|
| `deliver_to_villager` with binary `count >= 1` | Already satisfied; migrate to the full resolved payload quantity, not one item. |
| `deliver_to_villager` with `delivered_to_inventory = true` | Preserve the committed transfer as fully delivered, including unusual saved marker/count ordering. |
| Incomplete legacy villager delivery | Zero deposited units. Inventory possession is not a historical deposit. |
| Ordinary `item_delivery` possession state | Zero deposited units, with current inventory still available for the legacy turn-in route. |
| `item_delivery` with a committed `delivered` transfer marker | Preserve the completed transfer; never charge it again. |
| Already claimed/completed/removed quest | Do not revive it or repeat rewards during migration. |

Make migration idempotent. Delay quantity-sensitive migration until the correct concrete definition is available. Preserve template snapshots, locked recipient UUIDs, other objective progress, and reward guards. For a legacy non-consuming proof objective, preserve its previous satisfaction semantics instead of inventing a physical item deposit.

Changing delivery requirements or reordering objective arrays during a datapack reload must not assign old deposits to a different obligation. Store a stable objective identity/fingerprint at the delivery boundary; remap only when unambiguous. If a changed definition cannot be reconciled safely, preserve the deposit record and expose an explicit migration blocker rather than resetting or minting credit. This does not require redesigning persistence for every objective type.

Deposits remain delivered if the player drops other goods, dies, logs out, or changes dimension. Verify the actual player capability clone path. Abandoning a quest does not automatically refund committed goods: many are already consumed or available to a villager. Warn before abandoning with a nonzero deposit. A repeat or re-accepted quest starts a fresh ledger.

Do not attempt to reconstruct old quest deposits from a villager's current inventory or relationship history. Those items may have been ordinary gifts, transferred by another player, or acquired elsewhere. Any recovery for a historical lost delivery must be an explicit, auditable administrative action rather than automatic fabricated progress.

## 5. Preserve non-consuming proof objectives

`consume = false` with a non-transfer destination is not a real item donation. Do not let a player repeatedly show one item and bank six units without transferring anything.

Retain the existing objective-specific proof meaning: inventory-based proof stays dependent on the full required possession at the appropriate completion check; villager-interaction proof can keep its full-quantity acknowledgement behavior. Present this as **Show items**, not as goods already taken.

Native Gift may route a matching proof objective to that full-quantity verification without consuming goods or triggering an unrelated ordinary gift. Show an explicit “Items shown; nothing taken” result. A transfer destination remains a real transfer, including when a legacy `consume` flag is false. Test and document this distinction rather than flattening all objectives into the deposit formula.

## 6. Native MCA Gift integration

### 6.1 Route the real server-side action

The inspected upstream lifecycle is:

```text
MCA Gift button
  -> VillagerCommandHandler.handle(player, "gift")
  -> BreedableRelationship.giveGift(player, memories)
  -> special gift / ordinary classification / acceptance or refusal
```

Use one verified integration boundary, preferably at `giveGift` entry before its normal classification and special-case effects. The bridge should resolve the real server player, owner villager, and main-hand slot, then ask `DeliveryService` whether this is a quest-directed gift. [M1][M2]

Return explicit outcomes:

| Outcome | Behavior |
|---|---|
| `PASS_THROUGH` | No relevant delivery intent; leave MCA's original implementation untouched. |
| `DELIVERED` / `PROOF_ACKNOWLEDGED` | The shared service succeeded; suppress this invocation's normal gift consumption/effects and provide delivery feedback. |
| `HANDLED_WITHOUT_TRANSFER` | A relevant quest hand-in is ambiguous, blocked, stale, or lacks capacity; explain or offer selection, take nothing, and do not fall through into an ordinary gift. |

An active matching delivery has priority over MCA gift preferences. A villager must accept the item they requested even when it is not a recognized ordinary gift, is normally disliked, or would hit gift saturation. Quest-specific eligibility and destination capacity still apply.

Do not implement candidate discovery by discarding all blocked matches before checking whether quest intent exists. A matching request to the correct recipient whose inventory is full or whose objective is paused must result in a clear blocked-delivery response, not a fallback ordinary gift that consumes the item without credit.

Scope automatic quest routing to a matching item and an authorized recipient. An unrelated villager must not lose ordinary Gift functionality merely because a quest elsewhere requests the same item. For explicit quest-delivery requests to the wrong villager, reject with the intended recipient and take nothing. Where a quest-directed UI context is available for Gift, preserve that context and reject a wrong recipient rather than silently changing the action to an ordinary gift.

Only suppress the native **gift invocation** you handled. Do not cancel generic entity interactions, replace the entire villager command handler, or mark every MCA action successful.

### 6.2 Effects and special cases

A quest-directed gift is payment toward a quest, not an extra ordinary social gift. By default, award no additional native gift hearts or saturation changes for the same unit. The quest's configured rewards continue through normal finalization. Make this policy visible in help text; do not accidentally award both reward streams.

For unmatched gifts, preserve native acceptance, rejection, mood/hearts, saturation, inventory transfer, and special behavior exactly. For matched quest goods, route them as quest items before special behavior so a requested cake does not accidentally trigger pregnancy and a requested dye does not change hair instead of paying the quest. Test all relevant special branches from the actual supported artifact.

A successfully completed delivery does not authorize silently eating another held item while the screen refreshes. For the currently displayed quest-delivery context, make completion visible and disable its Deliver control. Later independent ordinary Gift actions remain ordinary MCA actions; keep the distinction explicit. Do not promise that the unchanged native Gift packet can distinguish an accidental extra click from an intentional subsequent social gift.

### 6.3 Do not build a false “success observer”

Do not credit at `giveGift` RETURN, on menu close, from a heart delta, or whenever the held stack decreases. Those can reflect refusal, special actions, or unrelated consumption. [M2]

A passive compatibility observer is acceptable only for an integration with a verified committed-transfer receipt: actual owner/player, actual item and quantity, actual destination, and a unique operation boundary. Such an observer records already-transferred goods; it must not remove or insert them again. It must deduplicate against the primary router.

Do not ship a success observer alone and call the universal Gift requirement satisfied: ordinary MCA gift rejection would still block arbitrary requested quest items.

### 6.4 Version and packaging requirements

MCA: Quests currently treats MCA as runtime-only and resolves names behind compatibility gates. Its probe fleet includes `7.6.20+1.20.1`, `7.7.0-beta.2+1.20.1`, and `7.7.1-alpha.2+1.20.1`; the Forge package root changes between these builds. [Q1][Q2]

Inspect those exact JARs before writing the injection. Verify the class, method descriptor, owner access, threading, one-item behavior, and special-case ordering for each. Upstream Yarn-style source names are not the runtime Forge mappings.

Use a common/server-safe, plugin-gated MCA gift mixin configuration. Do not put the hook in the existing client-only `mcaquests.mixins.json`. Follow the repository's plain Sponge Mixin conventions and avoid introducing MixinExtras or direct MCA type imports without a separate justified dependency decision. Resolve unknown owners/parameters using the established binding approach or carefully validated coercion, not guesses about erased descriptors. [Q2]

An unknown MCA shape must disable only the gift bridge, leave core quest menus usable, and produce a clear once-per-session capability diagnostic. Hide or qualify the “Gift also works” hint when unavailable. A missing hook for a declared supported JAR is a failed release test, not an acceptable silent fallback.

## 7. Recipient and destination correctness

Keep four identities distinct: the player interacting, the original quest giver, the authorized delivery recipient, and the container receiving goods.

For `deliver_to_villager`, reuse `ObjectiveSupport.matchesLocked()` and its resolved recipient description. Preserve exact self/UUID/family/situation/capital bindings. Preserve intentionally live profession matching rather than pinning the first smith forever. [Q9]

For `item_delivery`, use the effective legacy turn-in policy to determine which villagers may accept a deposit. Handle self-complete quests explicitly: keep their inventory-based completion and use the original giver as the natural optional Gift recipient unless an authored recipient policy says otherwise. Do not interpret “self-complete” as permission to gift any villager.

The current implemented destinations are consumption and `townstead_villager_inventory`; shared Townstead village storage is explicitly unavailable. Villager inventory transfer does not itself require Townstead to be installed. Do not expand that into an invented storage integration. [Q16]

The datapack contract for `deliver_to_villager` inventory transfer uses destination `target = recipient`. Preserve that distinction and existing validation; do not mistake its intentional recipient-context transfer for an arbitrary container target. [Q17]

For other destination selectors, pass the correct original-giver/recipient context through the resolver and verify legacy expectations in tests. Do not substitute whichever villager happens to own the open UI.

Gift is an alternative **input action**, not permission to change an authored destination. A consumed quest payload is consumed once. An inventory-transfer payload is inserted once, preserving its NBT. Do not first let MCA insert it and then separately run another consumption or transfer.

## 8. Networking, UI state, and diagnostics

Add a delivery-intent packet through the existing channel and scheduling helpers. A proposed shape is an active-instance ID, objective selection, villager UUID, authorized slot/quantity selection, expected revision, and request ID. The server supplies progress and matching; the client never supplies “items delivered” as truth.

Validate sender ownership, active instance membership, objective identity, resolved definition generation, nonterminal state, correct living MCA recipient, same dimension, permitted interaction range, relevant session/interaction authorization, availability guards, destination, source snapshots, and bounded counts. Do not force-load chunks or search arbitrary remote worlds to satisfy a client-supplied entity ID. Follow Forge's server-thread and defensive packet-handling guidance. [F1]

Use the same validation when entering from native Gift; do not assume an upstream command packet already enforces all delivery constraints.

Extend `CardObjective` or add a delivery-specific view model instead of parsing progress out of localized text. Preserve existing enum ordinal ordering and compatibility constructors. Bump `QuestNetwork.PROTOCOL_VERSION` for wire changes, and test client/server mismatch rejection. [Q18][Q2]

A global menu status must not hide valid per-card or per-objective delivery actions. Update card layout measurement together with rendering and button placement, so added guidance does not overlap controls at small GUI scales. Preserve scroll position, keyboard focus, narration, and optional project navigation.

Return localized reasons such as incorrect recipient, more items required, destination full, recipient unavailable, paused objective, changed inventory, ambiguous quest selection, or unavailable Gift bridge. Announce partial and final delivery successes distinctly. Keep errors visible in the screen, not only in a chat stream that the player cannot see behind it.

Suggested result vocabulary, to be implemented rather than assumed to exist:

```text
DELIVERED_PARTIAL
DELIVERY_SATISFIED
PROOF_ACKNOWLEDGED
ALREADY_DELIVERED
WRONG_RECIPIENT
NO_MATCHING_ITEMS
DESTINATION_FULL
RECIPIENT_UNAVAILABLE
OBJECTIVE_PAUSED
AMBIGUOUS_DELIVERY
STALE_REQUEST
INVALID_REQUEST
BRIDGE_UNAVAILABLE
```

Add a **new, documented** read-only delivery diagnostic command under the existing debug command structure. Its exact syntax is to be implemented, not assumed to exist today. Include active instance/quest/objective, expected and actual recipient UUIDs, delivered and available units, source slots selected, destination resolution, pause reason, MCA artifact/root, gift-hook capability, and last rejection. Do not print arbitrary full item NBT or player data. Gate verbose logging and throttle repeated refusals.

## 9. Implementation work packages

| Order | Work package | Exit condition |
|---|---|---|
| 1 | Characterize both reported quests and current Gift paths | Tests/trace distinguish possession, original-giver interaction, and native Gift; exact installed releases are recorded when available. |
| 2 | Add ledger, identity, migration, and source-selection transaction support | Old completed quests remain paid; partial deposits and exact held-slot debits serialize correctly. |
| 3 | Route both objectives and legacy finalization through the service | Menu/gift-ready core has no duplicate consumption path and respects authored destinations. |
| 4 | Add explicit delivery UI, incoming-recipient cards, packets, and immediate refresh | A player can finish either reported example using only the quest menu, with clear partial counts. |
| 5 | Add verified native Gift bridge and capability reporting | Real Gift advances either objective type in every supported MCA artifact, without altering unmatched gifts. |
| 6 | Update bundled dialogue/help and run compatibility matrix | Player instructions match actual behavior; release evidence covers persistence, dedicated server, and companion-mod combinations. |

Primary existing files to modify or audit are:

```text
src/main/java/dev/otectus/mcaquests/
  quest/objective/ItemDeliveryObjective.java
  quest/objective/DeliverToVillagerObjective.java
  quest/objective/ObjectiveSupport.java
  quest/objective/ObjectiveProgress.java
  quest/objective/InventoryTransfer.java
  quest/objective/DeliveryDestination.java
  quest/QuestManager.java
  state/ActiveQuest.java
  event/QuestProgressEvents.java
  client/QuestMenuScreen.java
  network/CardObjective.java
  network/QuestTurnInC2SPacket.java
  network/QuestNetwork.java
  compat/mca/...
```

Follow their actual callers into menu/log snapshots, packet request validation, tracking, serialization, and client injection. Do not update the menu alone while leaving a stale HUD or duplicate legacy consumption path behind.

Remove automatic **delivery** consumption from the generic `onTalkToVillager` handler. Keep unrelated healing, curing, and conversation behavior intact. Any retained legacy interaction-delivery behavior must be deliberately opt-in, documented, and use the same transaction service; it must not run by default alongside native Gift routing.

## 10. Reproduction and regression acceptance

### 10.1 Establish the reported failure before changing code

Use a disposable test world or a backup, not a live player's only save. Record the MCA: Quests, MCA Reborn, Forge, Townstead, and exact Social Expansion filenames and versions. Confirm the active quest ID and loaded datapack definition; do not assume a title establishes the definition or installed release.

For `mcaquests:last_banner_home`, obtain two damaged pillager crossbows in separate ordinary inventory slots. Establish the original giver UUID and complete the other objectives normally or through an existing documented test facility. Compare inventory possession alone, a direct right-click of the original giver, a direct right-click of a different eligible profession, opening the quest menu, and choosing native Gift. Use fresh copies or restore the test fixture between actions so a previous transfer does not change later evidence.

Repeat for `mcaquests:nether_relay` with six blaze rods. Observe server objective state as well as the screen. Distinguish “no inventory mutation,” “inventory mutation without credit,” and “server credit with stale UI.” Instrument actual Forge event class, hand, cancellation, recipient matching, active-definition resolution, objective availability, transfer outcome, and native gift entry.

The source-supported current expectation is a full-quantity interaction with the original giver, not inventory possession alone and not a supported native Gift receipt. This is a diagnostic expectation, not a confirmed workaround for the user's particular installation. Do not recommend gifting away items in the unpatched build as though that path already grants delivery credit.

### 10.2 Automated and gameplay matrix

Automated tests should exercise real objective definitions and inventory transactions, not only parsing or new helper methods in isolation. Use the existing JUnit infrastructure and `InventoryTransferTest` as foundations. Add gameplay automation only with an explicitly configured source set; the baseline instructions state that no GameTest source set currently exists. [Q2][Q13]

| Scenario | Required result |
|---|---|
| Two damaged crossbows in separate inventory slots | Bulk Deliver removes exactly those two; objective becomes 2/2. |
| One crossbow, then another acquired later | First hand-in persists 1/2; second reaches 2/2 without needing a replacement pair. |
| One crossbow gifted, second handed in through menu | Exactly two total debits; no second charge at reward claim. |
| Two rods gifted, four delivered | Six total units credited and consumed/transferred once. |
| All six rods gifted through MCA's actual Gift action | Each invocation advances one unit, including an item not ordinarily liked as a gift. |
| Original giver versus another same-profession villager | `recipient.mode=self` rejects an explicit hand-in to the wrong person with a useful explanation. |
| Ordinary Gift to an unrelated villager | Does not get hijacked because another quest elsewhere requests the same item. |
| Recipient differs from giver | Recipient's quest UI exposes the incoming obligation; final reward still follows its turn-in mode. |
| Named/enchanted/charged crossbow | Matching remains correct; selected data is preserved on transfer; valuable alternatives are not silently substituted. |
| Same item in main hand and other slots | Gift consumes the held unit, not an equivalent earlier inventory slot. |
| More goods than the remaining requirement | Surplus stays in the player's inventory. |
| Two objectives or quests request the same stock | No physical unit is reserved or credited twice; ambiguity is resolved visibly. |
| Gift request followed by menu request during refresh | Each new request revalidates remaining units; no duplicate rewards or overpayment. |
| Native callback accidentally observed twice | One underlying transfer yields one ledger increment. |
| Recipient inventory full or restricted | Transfer batch does not commit; item stays with player and reason is shown. Consumption-only quests are not incorrectly capacity-gated. |
| Capacity/source changes after preview | Stale plan is rejected or rebuilt with explicit consent; no partial unreported debit. |
| Inventory callback throws or reenters | No optimistic progress; rollback and diagnostics match the actual outcome. |
| Ordinary unmatched accepted/rejected Gift | Native behavior unchanged, including preferences and saturation. |
| Matched quest item normally rejected by MCA | Quest routing still works, subject to real quest and capacity constraints. |
| Matched quest item whose objective is paused or destination blocked | Helpful blocked result; no fallback ordinary gift and no lost item. |
| Cake/dye/name tag/golden apple | Matched quest delivery does not trigger unrelated special effects; unmatched Gift retains them. |
| Non-consuming proof objective | Full-quantity proof works; repeated presentation of one item cannot bank multiple units. |
| Transfer destination with consume=false | Real transfer still occurs according to the destination contract. |
| Opening conversation/quest menu or an unrelated action | No delivery items are taken. |
| Cancelled Forge interaction | No delivery consumption/credit; later valid native Gift remains independently testable. |
| Relog, normal server restart, death/respawn, dimension change | Deposits and correct recipient binding survive the applicable save/capability path. |
| Legacy completed binary delivery | Migrates as fully satisfied, never as 1/N; non-consuming proof retains proof semantics. |
| Legacy inventory-only item_delivery | Does not fabricate historical deposits; normal turn-in still works. |
| Datapack reload, changed objective order, template quest | Concrete requirements remain correct or an explicit safe migration blocker is shown. |
| Abandon and reaccept / repeat quest | No automatic duplicate refund; fresh instance starts with zero deposits. |
| Different reward turn-in modes and self-complete | Finalization obeys authored policy and rewards run at most once. |
| Two players delivering to the same villager | Each player's progress is isolated; actual shared destination capacity is respected. |
| Recipient dies, unloads, or changes profession | Authored binding/failure rules apply; no silent reassignment or remote forced loading. |
| Malformed/stale/remote packet | No mutation, no forced chunk load, and a bounded response. |

Run the core gift integration independently against all three supported MCA probe artifacts. Then test single-player and a dedicated Forge server with MCA alone, MCA plus Townstead, MCA plus the player's exact Social Expansion artifact, and both companions. Record filenames and versions. Add MCA: Conversations where applicable to alternate interaction entry points. Do not report a companion as responsible without a reproducing matrix row.

## 11. Bundled content and documentation

Update the two reported quests' dialogue/help so the recipient and both supported hand-in methods are clear. Preserve their IDs, kill targets, source constraints, counts, rewards, and broad progression balance unless a separate reproduced defect requires a change. Do not replace all `deliver_to_villager` objectives with `item_delivery` as a shortcut; that would erase distinct-recipient behavior rather than implement Gift support.

Audit remaining bundled quests, template output, conditional compatibility packs, and datapack samples for vague delivery instructions. Keep localization keys consistent in `en_us.json` and `pt_br.json`; do not hardcode English strings in event handlers.

Update `README.md`, `CONFIG.md` for any actual new switches, `DATAPACK.md` and its maintained documentation counterpart, and the changelog. Explain partial deposits, Gift's one-item gesture, bulk hand-in, non-consuming proof, surplus protection, normal-gift fallback, and non-refundable abandonment. Keep the available-inventory scope explicit.

Avoid proliferating settings. Explicit Deliver and native Gift should work by default. A narrowly scoped Gift-bridge disable switch is useful for diagnosing compatibility; a legacy automatic interaction switch, if retained at all, must default off. Do not let the config imply native support is active when binding failed.

## 12. Validation commands and delivery checklist

Use Java 17 and the repository's Gradle wrapper. Resolve the existing compile-only MCA: Reputation sibling dependency or use the documented `mcaReputationClasses` property rather than bypassing or deleting the compatibility tests. [Q2]

```bash
./gradlew compileJava
./gradlew test
./gradlew test --tests '*McaBindingProbeTest'
./gradlew build
```

Run any new gift-specific binding/bytecode tests with the actual supported artifacts. Run optional Townstead probes using the documented JAR properties when those artifacts are available. Verify the reobfuscated output through the existing build checks and use the verified JAR for in-game testing. A successful development compile does not prove a runtime gift injection applies. [Q2]

The coding agent's final report must include the reproduced behavior, changes by file, migration behavior, automated test results, runtime matrix results, exact tested artifacts, and remaining unverified cases. Do not describe tests as passed when they were only written or could not be launched.

### Definition of done

A player can read a delivery objective and immediately identify whom to visit, how many items remain, and the hand-in action. The player can deliver through the quest menu or MCA Gift, one item at a time or in batches, and mix those methods. The server records the actual items handed over once, never charges already-delivered goods again, preserves old quest progress, respects recipients/destinations, and grants rewards only through the existing valid completion flow.

## Source index

All MCA: Quests links below are pinned to the reviewed commit. Names introduced in the implementation sections are proposed additions, not existing APIs. Findings describe inspected source; implementation sections specify the desired changes.

[Q1]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/gradle.properties
[Q2]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/CLAUDE.md
[Q3]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/java/dev/otectus/mcaquests/quest/objective/ItemDeliveryObjective.java
[Q4]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/java/dev/otectus/mcaquests/quest/objective/DeliverToVillagerObjective.java
[Q5]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/resources/data/mcaquests/mcaquests/quests/adventurer/last_banner_home.json
[Q6]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/resources/data/mcaquests/mcaquests/quests/adventurer/nether_relay.json
[Q7]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/java/dev/otectus/mcaquests/event/QuestProgressEvents.java
[Q8]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/java/dev/otectus/mcaquests/quest/target/ItemTarget.java
[Q9]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/java/dev/otectus/mcaquests/quest/objective/ObjectiveSupport.java
[Q10]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/java/dev/otectus/mcaquests/event/QuestEventHandlers.java
[Q11]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/java/dev/otectus/mcaquests/client/QuestMenuScreen.java
[Q12]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/java/dev/otectus/mcaquests/quest/QuestManager.java
[Q13]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/java/dev/otectus/mcaquests/quest/objective/InventoryTransfer.java
[Q14]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/java/dev/otectus/mcaquests/state/ActiveQuest.java
[Q15]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/java/dev/otectus/mcaquests/quest/objective/ObjectiveProgress.java
[Q16]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/java/dev/otectus/mcaquests/quest/objective/DeliveryDestination.java
[Q17]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/DATAPACK.md
[Q18]: https://github.com/otectus/MCAQuests/blob/af372b6951191eac2b87b35e3108af3a7f64edfe/src/main/java/dev/otectus/mcaquests/network/CardObjective.java
[M1]: https://github.com/Luke100000/minecraft-comes-alive/blob/4d824551b30654e5792e19e84f3933e3e3d90ea2/common/src/main/java/net/mca/entity/interaction/VillagerCommandHandler.java
[M2]: https://github.com/Luke100000/minecraft-comes-alive/blob/4d824551b30654e5792e19e84f3933e3e3d90ea2/common/src/main/java/net/mca/entity/ai/BreedableRelationship.java
[F1]: https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/
