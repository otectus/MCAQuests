# MCA: Quests — Comprehensive Build Specification for a Minecraft Comes Alive: Reborn Add-on

## 1. Project Summary

Build a Minecraft 1.20.1 Forge add-on named **MCA: Quests** for **Minecraft Comes Alive: Reborn**.

The mod adds an RPG-style quest system to MCA villagers. When the player right-clicks an MCA villager and opens the normal MCA interaction menu, a new option called **Quests** should appear. Selecting this option opens a conversation-style quest interface where the villager explains what they need, what the player must do, and what reward will be given. The player can accept or decline the quest. Completing the quest grants the configured reward and a significant favor increase with that specific NPC.

The system must ship with a balanced set of default quests, but the core design must be data-driven so modpack authors and datapack creators can add, remove, override, and tune quests without editing Java code.

The mod should be built as a clean add-on, not a fork of MCA Reborn unless absolutely unavoidable.

## 2. Core Goals

MCA: Quests must accomplish the following:

Add a **Quests** button to MCA’s villager interaction menu.

Open a quest conversation UI when that button is selected.

Allow the villager to offer one or more valid quests based on profession, relationship, cooldowns, player state, biome, dimension, and other optional conditions.

Allow the player to accept, decline, complete, abandon, and track quests.

Reward completion with items, experience, effects, commands where enabled, and MCA favor.

Support profession-specific quest pools.

Support repeatable, one-time, cooldown-based, and relationship-gated quests.

Persist quest progress across logout, death, dimension travel, server restart, and villager unload/reload.

Expose the quest system through datapacks.

Ship with a default quest pack using only vanilla Minecraft and MCA concepts.

Remain safe on dedicated servers and multiplayer.

Fail gracefully when datapack quests are malformed.

## 3. Non-Goals for Version 1

Do not build a full cinematic dialogue engine.

Do not require FTB Quests, Better Questing, KubeJS, Patchouli, or any external quest mod.

Do not hardcode most quest content in Java.

Do not require modifying MCA Reborn’s source directly.

Do not grant rewards client-side.

Do not trust client packets for quest completion.

Do not make command rewards enabled by default.

Do not assume every MCA internal API will remain stable. Isolate MCA access behind a compatibility layer.

## 4. Target Environment

Minecraft: `1.20.1`

Loader: Forge

Recommended Forge version: `47.x`

Java: 17

Required dependency: Minecraft Comes Alive: Reborn for 1.20.1

Likely required dependency: Architectury API, because MCA Reborn itself depends on Architectury on modern versions.

Suggested mod id: `mcaquests`

Display name: `MCA: Quests`

Suggested package root: `com.mcaquests` or `dev.otectus.mcaquests`

The mod should work on both integrated single-player servers and dedicated servers.

## 5. High-Level Architecture

Use a server-authoritative quest system with a thin client UI.

The client is responsible for displaying the quest conversation, quest list, rewards, and progress. The server is responsible for selecting available quests, validating eligibility, tracking progress, consuming required items, granting rewards, and modifying MCA favor.

The major systems should be:

`McaQuestsMod`
Main mod entrypoint.

`McaCompat`
A strict adapter layer for all MCA Reborn-specific calls.

`QuestDefinition`
Immutable data-loaded quest template.

`QuestObjective`
Registry-driven objective interface.

`QuestReward`
Registry-driven reward interface.

`QuestCondition`
Registry-driven condition interface.

`QuestManager`
Server-side controller for offering, accepting, completing, and abandoning quests.

`QuestDataLoader`
Datapack reload listener for loading quest JSON.

`QuestStateStorage`
Persistent player/villager quest state.

`QuestNetwork`
Forge networking for client-server packets.

`QuestMenuScreen`
Client screen shown after selecting the MCA interaction menu’s Quests button.

`QuestConversationScreen`
Client conversation screen for accepting, declining, and turning in a quest.

`QuestEvents`
Forge event listeners for kill, block break, crafting, travel, item checks, and other objective progress.

`QuestCommands`
Admin/debug commands.

## 6. MCA Integration Strategy

The preferred integration is to add a **Quests** button directly to MCA’s existing interaction menu.

MCA Reborn’s interaction screen is dynamic, so the agent should first inspect MCA’s screen layout resources and `MCAScreens` implementation to determine whether the button can be added through a resource override, datapack-like screen definition, or asset injection.

Preferred order of integration:

First, use MCA’s existing dynamic screen layout system if it supports external resource override or extension. Add a button with a translatable label such as `gui.button.mcaquests.quests`.

Second, if the layout cannot be extended cleanly, use a small, targeted client-side Mixin into MCA’s interaction screen setup to add the button after MCA’s normal buttons are created.

Third, intercept MCA’s generic interaction button command path if possible. If MCA sends button commands such as `gui.button.<name>` to the server, add a new command key for `mcaquests.quests` and handle it on the MCA: Quests side.

Fourth, if no clean command interception exists, add a dedicated packet from the client to the server when the Quests button is pressed.

A fallback debug-only interaction may be provided through sneak-right-clicking an MCA villager, but this must not replace the actual MCA menu option. The user-facing feature must be the **Quests** option in MCA’s own villager interaction menu.

The button should only appear when the entity is an MCA villager-like NPC and the player is allowed to interact with that NPC. It should be hidden or disabled for invalid entities, dead villagers, babies unless configured, and any state where MCA itself prevents normal interaction.

## 7. MCA Compatibility Layer

All direct MCA calls must be isolated in `McaCompat`.

This layer should expose methods like:

```java
boolean isMcaVillager(Entity entity);

UUID getVillagerUuid(Entity entity);

Component getVillagerDisplayName(Entity entity);

ResourceLocation getProfessionId(Entity entity);

boolean isAdult(Entity entity);

int getFavor(ServerPlayer player, Entity villager);

void addFavor(ServerPlayer player, Entity villager, int amount);

boolean canPlayerInteract(ServerPlayer player, Entity villager);

Optional<BlockPos> getHomeOrVillagePos(Entity villager);

Optional<String> getMcaPersonality(Entity villager);

Optional<String> getMcaGender(Entity villager);
```

The first implementation can be minimal. The critical methods for version 1 are villager detection, UUID lookup, profession lookup, favor lookup, and favor modification.

The agent should inspect MCA’s actual 1.20.1 classes before implementing this layer. Likely relevant classes include MCA villager entities, villager brain classes, memory classes, profession classes, and interaction classes.

Do not scatter MCA imports throughout the quest codebase. If MCA changes a method name or field later, only `McaCompat` should need major edits.

## 8. Quest Menu Flow

When the player selects **Quests**, the server should inspect that villager and player, then send the client one of these responses:

No quests available.

Available quest offers.

Active quest with this villager.

Completed quest ready for turn-in.

Quest in progress.

Quest blocked by relationship, cooldown, profession, time, or other condition.

The UI should feel like a conversation, not a generic list. The first version can still use a simple screen, but the presentation should be NPC-centered.

Example flow:

Player right-clicks MCA villager.

MCA interaction menu opens.

Player clicks **Quests**.

Villager says: “I could use some help. Interested?”

Quest card displays title, objective summary, reward summary, and favor gain.

Player clicks **Accept** or **Decline**.

If accepted, quest is stored server-side and a short confirmation line appears.

Player completes the objective.

Player returns to the same villager, opens **Quests**, and the villager recognizes the completed task.

Player clicks **Complete**.

Server consumes required items if applicable, grants rewards, increases favor, marks completion/cooldown, and syncs the result.

## 9. Quest Conversation Requirements

Every quest should support these dialogue states:

`offer`
What the villager says when offering the quest.

`accept`
What the villager says when the player accepts.

`decline`
What the villager says when the player declines.

`in_progress`
What the villager says when the quest is active but incomplete.

`ready`
What the villager says when the quest objective is complete and ready to turn in.

`complete`
What the villager says after reward delivery.

`cooldown`
Optional text shown when the villager has no new quest because of cooldown.

`locked`
Optional text shown when the player does not meet requirements.

Dialogue should support both literal text and translation keys.

Datapack authors should be able to write simple literal strings, but the default quest pack should use translation keys so it can be localized.

## 10. Quest Data Location

Quest JSON files should load from datapacks at:

```text
data/<namespace>/mcaquests/quests/*.json
```

Examples:

```text
data/mcaquests/mcaquests/quests/farmer/wheat_request.json
data/mcaquests/mcaquests/quests/librarian/missing_pages.json
data/mcaquests/mcaquests/quests/guard/clear_the_night.json
data/runecraft/mcaquests/quests/orc_camp_warning.json
```

Use a reload listener so quests reload with `/reload`.

Invalid quest files should log clear errors and be skipped. One bad quest must not crash the server unless a strict debug config is enabled.

## 11. Quest Definition Schema

Each quest file should define a single quest.

Suggested version 1 schema:

```json
{
  "format_version": 1,
  "id": "mcaquests:farmer_wheat_request",
  "enabled": true,
  "weight": 20,
  "category": "delivery",
  "repeat": {
    "type": "cooldown",
    "cooldown_ticks": 24000
  },
  "giver": {
    "professions": ["minecraft:farmer"],
    "adult_only": true,
    "min_favor": -100,
    "max_favor": 1000
  },
  "availability": {
    "dimensions": ["minecraft:overworld"],
    "time": "any",
    "weather": "any"
  },
  "dialogue": {
    "offer": {
      "translate": "mcaquests.quest.farmer_wheat_request.offer"
    },
    "accept": {
      "translate": "mcaquests.quest.farmer_wheat_request.accept"
    },
    "decline": {
      "translate": "mcaquests.quest.farmer_wheat_request.decline"
    },
    "in_progress": {
      "translate": "mcaquests.quest.farmer_wheat_request.in_progress"
    },
    "ready": {
      "translate": "mcaquests.quest.farmer_wheat_request.ready"
    },
    "complete": {
      "translate": "mcaquests.quest.farmer_wheat_request.complete"
    }
  },
  "objectives": [
    {
      "type": "mcaquests:item_delivery",
      "item": "minecraft:wheat",
      "count": 24,
      "consume": true
    }
  ],
  "rewards": [
    {
      "type": "mcaquests:item",
      "item": "minecraft:emerald",
      "count": 3
    },
    {
      "type": "mcaquests:xp",
      "amount": 25
    },
    {
      "type": "mcaquests:favor",
      "amount": 25
    }
  ]
}
```

The quest loader must validate:

The ID is a valid resource location.

The ID in the file matches the file-derived ID or is otherwise unique.

The quest is enabled.

Weight is positive if the quest can be randomly offered.

All professions are valid resource locations.

All objective types exist.

All reward types exist.

Items, blocks, entities, effects, and tags resolve.

Counts are positive.

Favor amount is within configured min/max limits.

Command rewards are rejected unless enabled in config.

Unknown fields should either warn or be allowed based on a config option. During development, strict validation is preferable.

## 12. Profession-Specific Quests

Profession filtering is a core requirement.

A quest may specify one or more valid giver professions:

```json
"giver": {
  "professions": [
    "minecraft:farmer",
    "minecraft:fisherman",
    "mca:farmer"
  ]
}
```

The agent must inspect how MCA Reborn represents professions in 1.20.1. Some villagers may use vanilla `VillagerProfession`, while MCA may add or wrap professions through its own classes. The compatibility layer must normalize the villager’s profession into a `ResourceLocation`.

If a quest does not specify professions, it is considered generic and may be offered by any valid MCA villager.

The config should include:

```toml
professionMatchingMode = "NORMALIZED"
```

Suggested modes:

`STRICT`
Only exact profession IDs match.

`NORMALIZED`
Equivalent vanilla/MCA professions may match.

`LOOSE`
Profession aliases may match through a configurable alias table.

Example aliases:

```json
{
  "mca:guard": ["mca:guard", "guard", "minecraft:none"],
  "minecraft:farmer": ["minecraft:farmer", "mca:farmer", "farmer"]
}
```

## 13. Quest Conditions

The quest system should support condition objects. Conditions should be registry-driven so more can be added later.

Recommended built-in conditions:

`mcaquests:favor`
Requires minimum or maximum favor with the NPC.

`mcaquests:profession`
Requires the NPC to have a matching profession.

`mcaquests:biome`
Requires the villager or player to be in a biome or biome tag.

`mcaquests:dimension`
Requires a dimension.

`mcaquests:time`
Requires day, night, morning, evening, or tick range.

`mcaquests:weather`
Requires clear, rain, or thunder.

`mcaquests:item_held`
Requires the player to hold an item.

`mcaquests:advancement`
Requires an advancement.

`mcaquests:player_level`
Requires XP level.

`mcaquests:relationship_state`
Optional MCA-specific relationship gating.

`mcaquests:random_chance`
Allows rare quests.

`mcaquests:quest_completed`
Requires another quest to have been completed.

`mcaquests:quest_not_completed`
Prevents repeats or branches.

Conditions should support `all_of`, `any_of`, and `not`.

Example:

```json
"conditions": {
  "all_of": [
    {
      "type": "mcaquests:favor",
      "min": 50
    },
    {
      "type": "mcaquests:time",
      "period": "night"
    }
  ]
}
```

## 14. Objective Types

The first release should implement a stable objective registry.

Minimum version 1 objective types:

`mcaquests:item_delivery`
Player must bring items back to the quest giver.

`mcaquests:kill_entity`
Player must kill a specified entity type or entity tag.

`mcaquests:break_block`
Player must break a specified block or block tag.

`mcaquests:visit_biome`
Player must enter a biome or biome tag.

`mcaquests:visit_dimension`
Player must enter a dimension.

`mcaquests:craft_item`
Player must craft a specified item.

`mcaquests:obtain_item`
Player must obtain or possess a specified item.

`mcaquests:talk_to_profession`
Player must talk to another MCA villager with a specified profession.

`mcaquests:place_block`
Player must place a specified block.

`mcaquests:fish_item`
Player must fish up a specified item or item tag.

Recommended later objective types:

Escort NPC.

Defend village.

Cure zombie villager.

Breed animals.

Tame animal.

Harvest crop.

Deliver item to another specific villager.

Explore structure.

Reach coordinate radius.

Use item on block.

Use item on entity.

Trade with villager.

Sleep in village.

Every objective should have:

A unique type ID.

A Codec or JSON parser.

A progress state.

A display summary.

A server-side validation method.

An event hook or polling strategy.

## 15. Reward Types

Reward types should also be registry-driven.

Minimum version 1 reward types:

`mcaquests:item`
Gives an item stack.

`mcaquests:xp`
Gives raw experience points.

`mcaquests:xp_levels`
Gives XP levels.

`mcaquests:favor`
Adds MCA relationship hearts/favor with the quest giver.

`mcaquests:effect`
Applies a status effect.

`mcaquests:command`
Runs a server command if enabled in config.

`mcaquests:loot_table`
Rolls a loot table and gives/drops the results.

Recommended later reward types:

Village reputation.

MCA family relationship impact.

Currency integration.

Curios items.

FTB Money or Lightman’s Currency integration.

Skill XP for external RPG mods.

Advancement grant.

All rewards must be applied server-side only.

Item rewards should first attempt to insert into the player inventory. If the inventory is full, the default behavior should be to drop the item at the player’s position. A config option may instead require free inventory space before turn-in.

Favor rewards must apply only after successful completion and reward delivery.

## 16. Quest State and Persistence

Quest state must be stored server-side.

A player may have multiple active quests, but the default config should limit this to a reasonable number, such as 10 total active MCA quests.

Each active quest should store:

Quest ID.

Assigned villager UUID.

Assigned villager display name at acceptance time.

Assigned villager profession at acceptance time.

Dimension where the quest was accepted.

Villager position if available.

Start game time.

Last progress update time.

Objective progress data.

Generated random values.

Completion-ready flag.

Reward-claimed flag.

Expiration time if applicable.

The system also needs completed/cooldown history:

Per player.

Per quest.

Per villager UUID where relevant.

Last completed time.

Number of completions.

Last declined time if decline cooldowns are enabled.

Recommended storage:

Use a player capability or Forge-compatible attachment-style abstraction for active player quest data.

Use `SavedData` for global indexes if needed.

Ensure data is serialized to NBT and survives player logout, server restart, and dimension changes.

Do not rely on client state for anything important.

## 17. Villager Identity Handling

Default behavior should require turning in the quest to the same villager who gave it.

The quest state should track the villager by UUID.

If the villager is unloaded, the quest should remain active.

If the villager dies, default behavior should mark the quest as failed or blocked, depending on config.

Suggested config:

```toml
requireOriginalVillagerForTurnIn = true
allowTurnInToSameProfessionIfOriginalMissing = false
failQuestIfGiverDies = false
```

Each quest may override turn-in behavior:

```json
"turn_in": {
  "mode": "original_giver"
}
```

Supported turn-in modes:

`original_giver`
Must return to the same villager.

`same_profession`
Any MCA villager with the same profession can complete it.

`any_villager`
Any MCA villager can complete it.

`self_complete`
Completes immediately when objectives are done.

`specified_profession`
Turn in to a listed profession.

## 18. Quest Selection Logic

When the player opens the Quests menu, the server should:

Verify the entity is a valid MCA villager.

Load the player’s active quests involving that villager.

Check for completed quests ready for turn-in.

Build a pool of available quest definitions.

Filter by enabled status.

Filter by profession.

Filter by villager/player conditions.

Filter by repeat/cooldown rules.

Filter by active quest conflicts.

Filter by prerequisites.

Apply weights.

Generate up to `offersPerVillager` quest offers.

Cache offers for that player/villager pair until the next in-game day or until config-defined refresh time.

Default:

```toml
offersPerVillager = 1
offerRefreshTicks = 24000
```

The first version may offer a single quest at a time. Later versions may present multiple quest cards.

Quest offers should be deterministic enough that reopening the menu does not constantly reroll the quest. Use a seed based on player UUID, villager UUID, world day, and quest pool version.

## 19. Progress Tracking

Progress should be event-driven where possible.

Use Forge events for:

Entity kills.

Block breaks.

Block placement.

Item crafting.

Item pickup.

Item smelting where available.

Fishing results.

Player ticks for location-based checks.

Entity interaction for talk-to-NPC objectives.

Avoid scanning every player inventory every tick. For item possession objectives, scan when the quest menu opens, when inventory changes if available, or at a low interval such as once per second for players with active quests.

Kill objective rules:

Credit direct player kills.

Optionally credit tamed animals owned by the player.

Optionally credit projectiles shot by the player.

Do not credit unrelated mob deaths nearby by default.

Block objective rules:

Only count blocks broken by the player.

Respect tags.

Optionally restrict to naturally generated blocks later, but do not attempt this in version 1 unless necessary.

Item delivery rules:

Do not consume required items until turn-in.

On turn-in, validate the player still has the required items.

Consume only after all validations pass.

If reward delivery fails and items were consumed, rollback or drop rewards safely.

## 20. Networking

Use Forge SimpleChannel networking.

Suggested packets:

Client to server:

`OpenQuestMenuC2SPacket`
Sent when the Quests button is clicked.

`QuestOfferDecisionC2SPacket`
Accept or decline a quest offer.

`QuestTurnInC2SPacket`
Attempt to complete an active quest.

`QuestAbandonC2SPacket`
Abandon an active quest.

`QuestRequestSyncC2SPacket`
Request updated quest log data.

Server to client:

`QuestMenuDataS2CPacket`
Contains current villager quest state and offers.

`QuestConversationS2CPacket`
Displays a specific conversation state.

`QuestProgressSyncS2CPacket`
Syncs active quest progress.

`QuestCompletedToastS2CPacket`
Triggers a toast/sound when objective requirements are complete.

`QuestErrorS2CPacket`
Displays a friendly error.

All packets must validate:

Player is present.

World is server-side.

Villager UUID exists or is safely unresolved.

Player is close enough to the villager for menu actions.

Quest IDs exist.

Quest is still valid.

No reward or completion packet should trust the client.

## 21. Client UI

Add a clean RPG-style UI consistent with MCA’s existing interface.

Required screens:

`QuestMenuScreen`
Shows the villager name, profession, relationship/favor if available, active quest status, and available quest offer.

`QuestConversationScreen`
Shows dialogue text, objective summary, reward summary, and response buttons.

Optional but recommended:

`QuestLogScreen`
A keybind-accessible list of active MCA quests.

`QuestTrackerOverlay`
Small HUD tracker for pinned active quest.

`QuestCompletedToast`
Toast notification when an objective is ready to turn in.

The Quests screen should include:

Villager name.

Villager profession.

Quest title.

Quest description.

Objective summary.

Reward summary.

Favor reward indicator.

Accept button.

Decline button.

Complete button when ready.

Back button returning to MCA interaction menu or closing gracefully.

Use translation keys for all built-in text.

## 22. Default Quest Pack

Ship with 30–50 default quests.

Keep the default pack vanilla-safe. Do not require external mods beyond MCA Reborn.

Suggested default categories:

Farmer quests:
Bring wheat, carrots, potatoes, beetroot, seeds, or hay bales.

Fisherman quests:
Bring cod, salmon, tropical fish, string, or a fishing rod.

Librarian quests:
Bring paper, books, ink sacs, feathers, or amethyst shards.

Cleric quests:
Bring rotten flesh, spider eyes, glowstone dust, or bottles.

Armorer/toolsmith/weaponsmith quests:
Bring coal, iron ingots, copper ingots, leather, or defeat nearby monsters.

Mason quests:
Bring clay, bricks, stone, granite, andesite, or diorite.

Shepherd quests:
Bring wool, dyes, shears, or string.

Butcher quests:
Bring cooked meat, leather, or defeat wolves/foxes only if thematically acceptable. Avoid cruel-feeling quests by default.

Cartographer quests:
Visit a biome, bring paper, bring compass, or explore nearby terrain.

Guard quests:
Kill zombies, skeletons, spiders, pillagers, or other hostile mobs.

Unemployed/nitwit quests:
Simple errands, flowers, apples, sticks, bread, or local exploration.

Child villager quests, if enabled:
Find cookies, flowers, toys, paper, or harmless items. Disabled by default if child interactions are sensitive or awkward in MCA’s design.

Each profession should have at least three quests.

Each default quest should grant:

A modest item reward.

A small or medium XP reward.

A meaningful favor increase.

Favor rewards should be large enough to make questing feel worthwhile, but not so large that one quest trivializes relationships.

Suggested default favor reward values:

Small errand: 10–15

Normal quest: 20–30

Dangerous quest: 35–50

Rare quest: 60+

## 23. Example Default Quest

```json
{
  "format_version": 1,
  "id": "mcaquests:farmer_wheat_request",
  "enabled": true,
  "weight": 20,
  "category": "delivery",
  "repeat": {
    "type": "cooldown",
    "cooldown_ticks": 24000
  },
  "giver": {
    "professions": ["minecraft:farmer"],
    "adult_only": true,
    "min_favor": -100
  },
  "dialogue": {
    "offer": {
      "text": "The fields have been kind, but I am short on wheat for the next batch of bread. Could you bring me 24 wheat?"
    },
    "accept": {
      "text": "Thank you. Bring it back when you can, and I will make it worth your time."
    },
    "decline": {
      "text": "Another time, then."
    },
    "in_progress": {
      "text": "Still looking for that wheat?"
    },
    "ready": {
      "text": "That wheat will help more than you know. Shall we settle up?"
    },
    "complete": {
      "text": "You kept your word. I will remember that."
    }
  },
  "objectives": [
    {
      "type": "mcaquests:item_delivery",
      "item": "minecraft:wheat",
      "count": 24,
      "consume": true
    }
  ],
  "rewards": [
    {
      "type": "mcaquests:item",
      "item": "minecraft:emerald",
      "count": 3
    },
    {
      "type": "mcaquests:xp",
      "amount": 25
    },
    {
      "type": "mcaquests:favor",
      "amount": 25
    }
  ]
}
```

## 24. Commands

Add admin/debug commands under `/mcaquests`.

Required commands:

`/mcaquests reload`
Reload and validate quest data.

`/mcaquests validate`
Validate loaded quests and print errors.

`/mcaquests list`
List loaded quest IDs.

`/mcaquests offers <player>`
Show current quest offers for the player’s targeted villager or nearest villager.

`/mcaquests active <player>`
Show active quests.

`/mcaquests complete <player> <quest_id>`
Force-complete a quest for testing.

`/mcaquests reset <player> [quest_id]`
Reset quest progress or completion history.

`/mcaquests debug villager`
Print targeted villager MCA data: UUID, name, profession, favor, relationship info if available.

`/mcaquests export-schema`
Export example JSON schema to config folder.

Permission levels:

Normal players: no admin commands.

Server operators level 2: list, active, debug.

Server operators level 3 or 4: reset, complete, reload, validate, export-schema.

## 25. Config

Use Forge common and client configs.

Common config:

```toml
enableDefaultQuestPack = true
maxActiveQuestsPerPlayer = 10
maxActiveQuestsPerVillager = 1
offersPerVillager = 1
offerRefreshTicks = 24000
defaultQuestCooldownTicks = 24000
requireOriginalVillagerForTurnIn = true
allowTurnInToSameProfessionIfOriginalMissing = false
failQuestIfGiverDies = false
allowCommandRewards = false
allowLootTableRewards = true
favorRewardMultiplier = 1.0
minFavorReward = 0
maxFavorReward = 100
professionMatchingMode = "NORMALIZED"
strictJsonValidation = false
debugLogging = false
```

Client config:

```toml
showQuestButtonInMcaMenu = true
showQuestToasts = true
showQuestTrackerHud = true
playQuestSounds = true
questTrackerMaxEntries = 3
```

## 26. Safety and Balance Rules

All rewards are server-side.

All completion validation is server-side.

All player-submitted quest IDs must be checked against server-loaded definitions.

All item rewards must handle full inventory safely.

Command rewards are disabled by default.

Malformed datapack quests should be skipped with useful logs.

Favor rewards should be clamped by config.

Quest completion should be atomic: either the player gets the reward and the quest completes, or neither happens.

Do not consume delivery items until the reward can be granted.

Do not allow the same quest to be turned in twice through packet spam.

Use transaction-style state updates around completion.

## 27. Datapack Authoring Features

Datapack authors should be able to:

Add new quests.

Override default quests.

Disable default quests.

Create profession-specific quests.

Create generic quests.

Use item, block, entity, and biome tags.

Use translated dialogue or literal dialogue.

Create repeatable quests.

Create one-time quests.

Create cooldown quests.

Create relationship-gated quests.

Create prerequisite quest chains.

Create custom rewards using built-in reward types.

Use command rewards only if the server config allows them.

Add-ons should be able to register new objective, reward, and condition types in Java.

## 28. Java Extension API

Expose a small API package for other mods:

```java
public interface QuestObjectiveType<T extends QuestObjective> {}

public interface QuestRewardType<T extends QuestReward> {}

public interface QuestConditionType<T extends QuestCondition> {}

public class QuestAcceptedEvent extends Event {}

public class QuestCompletedEvent extends Event {}

public class QuestRewardedEvent extends Event {}

public class QuestFailedEvent extends Event {}
```

Post Forge events when:

A quest is offered.

A quest is accepted.

A quest is declined.

A quest progresses.

A quest becomes ready to complete.

A quest is completed.

Rewards are granted.

A quest is abandoned.

A quest fails.

Do not expose mutable internal state directly. Use copies or controlled accessors.

## 29. Suggested File Structure

```text
src/main/java/com/mcaquests/
  McaQuests.java
  McaQuestsConfig.java
  compat/
    McaCompat.java
    McaVillagerRef.java
  quest/
    QuestDefinition.java
    QuestId.java
    QuestManager.java
    QuestOffer.java
    QuestSelectionContext.java
    QuestTurnInMode.java
  quest/condition/
    QuestCondition.java
    ConditionRegistry.java
    FavorCondition.java
    ProfessionCondition.java
    TimeCondition.java
    DimensionCondition.java
  quest/objective/
    QuestObjective.java
    ObjectiveRegistry.java
    ItemDeliveryObjective.java
    KillEntityObjective.java
    BreakBlockObjective.java
    VisitBiomeObjective.java
    CraftItemObjective.java
  quest/reward/
    QuestReward.java
    RewardRegistry.java
    ItemReward.java
    XpReward.java
    FavorReward.java
    EffectReward.java
    CommandReward.java
  data/
    QuestDataLoader.java
    QuestValidationException.java
    QuestJsonCodecs.java
  state/
    PlayerQuestData.java
    ActiveQuest.java
    QuestProgress.java
    QuestHistory.java
    QuestStorage.java
  network/
    QuestNetwork.java
    OpenQuestMenuC2SPacket.java
    QuestDecisionC2SPacket.java
    QuestTurnInC2SPacket.java
    QuestMenuDataS2CPacket.java
    QuestProgressSyncS2CPacket.java
  client/
    QuestMenuScreen.java
    QuestConversationScreen.java
    QuestLogScreen.java
    QuestToast.java
    QuestHudOverlay.java
  command/
    McaQuestsCommand.java
  event/
    QuestEventHandlers.java
  mixin/
    InteractScreenMixin.java
    OptionalMcaCommandMixin.java

src/main/resources/
  META-INF/mods.toml
  pack.mcmeta
  mcaquests.mixins.json
  assets/mcaquests/lang/en_us.json
  assets/mcaquests/textures/gui/
  data/mcaquests/mcaquests/quests/
```

## 30. `mods.toml` Requirements

The mod metadata should declare:

Mod id: `mcaquests`

Display name: `MCA: Quests`

Minecraft version: 1.20.1

Forge dependency.

MCA Reborn dependency.

Architectury dependency if required by the runtime.

The dependency on MCA should be mandatory. This mod has no purpose without MCA Reborn.

## 31. Mixins

Use Mixins only where necessary.

Acceptable Mixin targets:

MCA interaction screen button setup.

MCA interaction screen button press handling.

Possibly MCA screen layout registration if no public method exists.

Mixin rules:

Keep Mixins tiny.

Do not duplicate MCA UI logic.

Do not overwrite whole methods unless there is no alternative.

Prefer inject-at-tail or targeted redirects.

Log a clear error if the expected MCA screen target changes.

Put all Mixin integration behind config where possible.

The agent should first try resource/layout extension. Use Mixins as the fallback, not the first assumption.

## 32. Localization

Default language file:

```text
assets/mcaquests/lang/en_us.json
```

Required keys:

```json
{
  "gui.button.mcaquests.quests": "Quests",
  "mcaquests.screen.quests.title": "Quests",
  "mcaquests.button.accept": "Accept",
  "mcaquests.button.decline": "Decline",
  "mcaquests.button.complete": "Complete",
  "mcaquests.button.abandon": "Abandon",
  "mcaquests.status.no_quests": "I do not need anything right now.",
  "mcaquests.status.in_progress": "Quest in progress",
  "mcaquests.status.ready": "Ready to complete",
  "mcaquests.reward.favor": "+%s favor",
  "mcaquests.quest.farmer_wheat_request.title": "A Farmer's Shortage"
}
```

Default quest dialogue should use translation keys rather than hardcoded English where practical.

Datapack quests may use either:

```json
{ "text": "Literal dialogue." }
```

or:

```json
{ "translate": "custom.quest.dialogue.key" }
```

## 33. Testing Requirements

The agent must not consider the mod complete until these tests pass.

Unit tests:

Quest JSON parses correctly.

Invalid quest JSON fails gracefully.

Profession filters work.

Condition evaluation works.

Weighted quest selection works.

Cooldown logic works.

Reward parsing works.

Objective progress serialization works.

Game/manual tests:

Right-clicking an MCA villager shows the MCA interaction menu.

The **Quests** button appears.

Clicking **Quests** opens a quest conversation.

Accepting a quest creates server-side state.

Declining a quest does not create active state.

Item delivery quest validates inventory correctly.

Item delivery consumes the correct items on completion.

Kill quest progresses only when the player earns credit.

Block break quest progresses correctly.

Profession-specific quests only appear for matching professions.

Completion grants item rewards.

Completion grants XP rewards.

Completion increases MCA favor with that NPC.

Quest progress survives logout/login.

Quest progress survives server restart.

Quest progress survives villager unload/reload.

Invalid datapack quests do not crash the server.

Dedicated server works with no client-only class loading crash.

Multiplayer packet spam cannot duplicate rewards.

Regression tests:

Repeatedly clicking Complete does not duplicate rewards.

Reopening the Quests menu does not reroll offers every time.

A full inventory handles rewards correctly.

A dead or missing villager does not crash quest turn-in.

Reloading datapacks while players have active quests does not corrupt state.

## 34. Implementation Milestones

Milestone 0: Project setup.

Create Forge 1.20.1 project.

Add MCA Reborn dependency.

Add Architectury dependency if required.

Confirm the mod launches with MCA installed.

Confirm dedicated server launch.

Milestone 1: MCA compatibility.

Implement `McaCompat`.

Detect MCA villagers.

Read villager UUID, display name, and profession.

Read and modify favor.

Add `/mcaquests debug villager`.

Milestone 2: Menu integration.

Add the **Quests** button to MCA’s interaction menu.

Clicking the button sends a server packet.

Server verifies targeted villager and returns placeholder menu data.

Milestone 3: Quest data loading.

Implement datapack quest loader.

Implement schema validation.

Load default quest JSON.

Add `/mcaquests reload`, `/mcaquests list`, and `/mcaquests validate`.

Milestone 4: Quest state.

Implement active quest storage.

Implement accept, decline, abandon, and sync.

Persist state through logout and server restart.

Milestone 5: Objectives.

Implement item delivery.

Implement kill entity.

Implement break block.

Implement visit biome or dimension.

Milestone 6: Rewards.

Implement item reward.

Implement XP reward.

Implement favor reward.

Implement effect reward.

Implement command reward behind config.

Milestone 7: UI polish.

Build quest conversation screen.

Add reward icons/text.

Add completion state.

Add toasts.

Add optional quest log keybind.

Milestone 8: Default content.

Create 30–50 default quests.

Balance rewards.

Add translation keys.

Validate all default JSON.

Milestone 9: Hardening.

Test multiplayer.

Test dedicated server.

Test reload behavior.

Test malformed datapacks.

Fix reward duplication risks.

Document datapack format.

## 35. Acceptance Criteria

The mod is complete when all of the following are true:

With MCA Reborn installed, right-clicking an MCA villager opens the normal MCA interaction menu and includes a **Quests** option.

Selecting **Quests** opens an NPC conversation-style quest screen.

The villager can explain the task and reward.

The player can accept or decline.

Accepted quests persist server-side.

Quest objectives progress correctly.

Completing the quest grants configured rewards.

Completing the quest increases MCA favor with the quest giver.

Profession-specific quests only appear for valid professions.

Custom datapack quests can be added without Java changes.

Default quests are included and enabled by default.

Invalid datapack quests are reported but do not crash the server.

The mod works in single-player and on dedicated servers.

The mod does not duplicate rewards under packet spam or repeated clicks.

The codebase isolates MCA internals behind `McaCompat`.

## 36. Key Engineering Risks

The MCA interaction menu may not expose a clean external extension point. If so, a small Mixin will be needed.

MCA Reborn may use mappings or abstractions that differ from a normal Forge-only mod. The agent must resolve dependency/mapping setup carefully.

MCA internal relationship APIs may change between builds. Keep all favor logic inside `McaCompat`.

Profession IDs may not be purely vanilla. Normalize profession handling.

Quest state can become stale if villagers die, move, or unload. Store UUIDs and handle missing villagers gracefully.

Datapack reloads can invalidate active quest definitions. Active quests should either keep a minimal snapshot or fail gracefully with a clear message if their definition disappears.

Reward duplication is the most important exploit to prevent. Completion must be server-side, atomic, and idempotent.

## 37. Final Instruction to the Coding Agent

Build **MCA: Quests** as a robust Forge 1.20.1 MCA Reborn add-on with a server-authoritative, datapack-driven quest system. Prioritize clean MCA menu integration, safe persistence, profession-specific quest filtering, and reliable favor rewards. Keep the first version focused, stable, and extensible: item delivery, kill, block break, and exploration quests are enough for the initial implementation as long as the data format and registries make future objective types easy to add.
