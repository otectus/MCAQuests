# Living Village Update — Design Notes (Deferred)

> Status: **deferred design doc.** Captured ahead of the Progression & Economy phase (v0.7.0).
> This describes a future direction ("the reactive village") that was considered and parked.
> Nothing here is implemented yet.

## Vision

Today every quest is author-authored static JSON, offered when a player right-clicks a villager.
The "Living Village" phase would add **emergent, world-driven quests**: villagers proactively ask
for help in response to things that actually happen in the world — raids, deaths, zombie
infections, missing kin, famine, seasons. This is the thematic culmination of three systems
already built: MCA-aware conditions, NPC/village-centered objectives, and village projects.

The shift is from "the player browses a menu of standing offers" to "the village reacts and surfaces
timely, situational requests."

## Core concept: Situations

- A **Situation** is a transient world/village condition detected from gameplay events
  (raid started, villager died, villager infected, child went missing, food low, night/season).
- A `SituationRegistry` + detectors listen on the Forge event bus and the server tick, opening
  Situations scoped to a village (or a specific villager / family).
- While a Situation is open, eligible nearby givers surface **dynamic, time-limited offers** drawn
  from datapack-defined "situation templates" (reusing the existing template variable pools).
- Situations resolve (player helped, timer expired, condition cleared) and close, with success /
  failure outcomes that can move village reputation and relationship arcs.

## What it reuses (low new surface)

- `compat/McaCompat.java` + `McaVillagerSnapshot` — detect infection/death/family/village state.
- `FailureSpec` deadlines + the HUD countdown — Situations are inherently time-limited.
- The 10 NPC/village objective types (escort, defend, protect, cure, heal, deliver, …) — perfect
  payloads for situational requests.
- Quest templates (variable pools, deterministic per-offer resolution) — generate concrete asks.
- Village projects + village reputation — community-scale situations and their rewards.

## What is new

- `SituationRegistry` + situation detectors (event/tick driven).
- A `DynamicOfferSource` feeding the existing offer pipeline alongside `QuestRegistry`.
- Datapack `data/<ns>/mcaquests/situations/**.json` (trigger + cooldown + offer template + scope).
- Persistence for open Situations (world `SavedData`, like `ProjectSavedData`).

## Open questions

- Throttling: how many concurrent Situations per village; anti-spam cooldowns.
- Multiplayer fairness: who "owns" a Situation; shared vs per-player resolution.
- Determinism vs surprise: how much randomness in which Situations fire.
- Interaction with standing static offers (priority/weight integration via `OfferShaping`).

## Sequencing

Best built **after** Progression & Economy (v0.7.0): reputation tiers and the journal give players
a reason to care about reacting to their village, and the tier/title rewards become natural payoffs
for resolving Situations. Target this as a v0.8.0+ phase.
