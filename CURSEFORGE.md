# MCA: Quests

**Turn your MCA villages into places where people actually need your help.**

Credit to [TheWiggleDuck](https://www.curseforge.com/members/thewiggleduck/projects) for the awesome Curseforge project icon!

MCA: Quests adds a full RPG-style quest system to **Minecraft Comes Alive: Reborn**. Villagers can offer jobs based on their profession, relationships, circumstances, surroundings, and the state of their village.

Right-click an MCA villager, open the integrated **Quests** menu, choose an offer, complete its objectives out in the world, then return for your reward. Helping villagers can earn items, experience, currency, status effects, village reputation, titles, and most importantly, **MCA relationship hearts with the villagers you help**.

With more than **260 built-in quests**, long-form quest chains, village-wide projects, dynamic situations, extensive quest tracking, and optional integrations with other major mods, MCA: Quests is designed to make MCA villages feel less like collections of NPCs and more like communities you can become part of.

> **This NeoForge port requires Minecraft Comes Alive: Reborn for Minecraft 1.21.1.**<br>
> It is an add-on and does not function without MCA Reborn.

## Features

### More Than 260 Built-In Quests

Villagers across the entire village can have something for you to do.

Quest content covers:

*   Farmers
*   Fishermen
*   Shepherds
*   Librarians
*   Clerics
*   Armorers
*   Weaponsmiths
*   Toolsmiths
*   Masons
*   Butchers
*   Fletchers
*   Leatherworkers
*   Cartographers
*   Guards
*   Jobless villagers
*   Nitwits
*   Additional roles such as archers, adventurers, and mercenaries

The built-in quests range from everyday errands to dangerous expeditions and community problems.

You might gather supplies for a farmer, hunt monsters for the guard, deliver something to a villager's relative, repair part of the village, cure an infected resident, explore a distant structure, escort someone home, or help an entire settlement recover from a crisis.

### A Wide Variety of Objectives

Quests are not limited to basic item collection.

Objectives can involve:

*   Gathering and delivering items
*   Crafting
*   Fishing
*   Killing enemies
*   Breaking or placing blocks
*   Visiting biomes
*   Entering dimensions
*   Finding structures
*   Traveling to specific locations
*   Escorting villagers
*   Protecting villagers
*   Defending locations
*   Breeding and taming animals
*   Curing infected villagers
*   Finding missing relatives
*   Talking to particular villagers or professions
*   Completing multi-step journeys

Optional integrations can add even more objective types.

## Quest Chains and Ongoing Stories

Individual quests can connect into multi-stage **relationship quest chains**.

A villager might begin by asking for a simple favor, then eventually involve you in larger parts of their life or community. Chains remember what happened during earlier stages and can branch depending on whether you completed, failed, or abandoned previous quests.

Progress is associated with the villager involved, allowing different villagers to have their own ongoing stories with the player.

The interface clearly identifies the current chapter, such as **Part 2 of 4**, so longer quest lines remain easy to follow.

## Quests That Understand MCA Villagers

MCA: Quests can use information from MCA Reborn when deciding which quests make sense.

Quest offers can account for things such as:

*   Relationships
*   Spouses
*   Family members
*   Age
*   Personality
*   Mood
*   Profession
*   Health
*   Infection
*   Village or home
*   Missing relatives
*   Deceased relatives
*   Relationship hearts

This allows quests to be about the actual villagers in your world instead of anonymous placeholders.

When a quest refers to a particular family member or villager, that specific person is bound to the quest. Their name, location, objective tracking, and turn-in behavior all continue referring to the same NPC.

## Living Village Situations

Villages can react to events happening around them through **situations**.

Situations are temporary circumstances that can generate appropriate quests when something goes wrong, such as:

*   Raids
*   Villager deaths
*   Zombie infections
*   Missing residents
*   Food shortages
*   Nightfall
*   Villagers stranded outside after dark
*   Hostile mobs threatening homes

Instead of every quest existing forever as a static job board entry, some opportunities appear because something actually happened.

Situations are saved with the world, throttled to avoid spam, and fully configurable through datapacks.

## Village Projects

Some problems are bigger than a single villager.

**Village Projects** are shared, multi-stage community goals that players can work toward together.

Projects can involve tasks such as:

*   Repairing village infrastructure
*   Restocking a guardhouse
*   Restoring a library
*   Supplying resources
*   Defending an area
*   Constructing or improving village facilities

On multiplayer servers, contributions from different players can all advance the same project.

Projects can reward:

*   Everyone who contributed
*   The highest contributor
*   Individual contributors
*   The village itself

Progress is stored in the world save, making village projects suitable for long-running multiplayer worlds and modpacks.

## Village Reputation and Titles

Your actions can affect your standing with an entire village.

MCA: Quests includes its own configurable reputation progression:

**Stranger -> Acquaintance -> Friend -> Honored -> Revered**

Higher reputation can unlock new quests, and reaching milestones can award **titles**.

The built-in **Journal** lets you review:

*   Reputation with villages
*   Reputation tiers
*   Earned titles
*   Completed quests
*   Previous accomplishments

Both reputation tiers and titles are datapack-driven, so modpack authors can replace or expand the progression system.

## Quest Tracking and Navigation

MCA: Quests includes several ways to keep track of what you are doing.

### Quest Log

A dedicated Quest Log displays your active quests and their progress.

You can also abandon quests directly from the log, even if the original quest giver is no longer nearby.

### HUD Tracker

Follow a quest to place its current objective on the HUD.

The tracker can display:

*   Objective progress
*   Quest giver
*   Destination
*   Distance
*   Direction
*   Current target

Its position can be configured by the player.

### In-World Quest Markers

Objectives with a location can display a compact marker directly in the world.

The marker follows the objective you currently need to complete. As a quest progresses, the destination automatically changes with it.

For example, a quest might point toward a Nether portal first, then toward the actual destination once you reach the Nether, and finally back toward the villager when the quest is ready to turn in.

An optional screen-edge indicator can also show the direction of objectives that are behind you or outside the current view.

Villagers who are currently relevant to a tracked objective can be outlined for that player, making it much easier to find the correct person in a crowded village.

## Rewards

Quests support multiple reward types, including:

*   Items
*   Currency
*   Experience points
*   Experience levels
*   Status effects
*   Loot tables
*   Commands
*   MCA relationship hearts

Command rewards are disabled by default for safety.

### Configurable Currency

Quest rewards do not have to be hard-coded to emeralds.

MCA: Quests supports a semantic currency system where pack authors can decide what "money" means for their pack.

Currency can use:

*   Emeralds
*   Create: Numismatics coins
*   Any configured item

Quests can also use difficulty levels such as **easy**, **medium**, and **hard** to determine appropriate payout ranges.

The reward amount is rolled when the quest is accepted and does not change afterward, so the reward shown to the player is the reward they actually receive.

## Optional Mod Integrations

MCA: Quests is designed to work as part of larger modpacks. Integrations are optional and disable themselves cleanly when the related mod is not installed.

### Townstead

With **Townstead** installed, quests can interact with much deeper village simulation.

Quest content can use:

*   Hunger
*   Thirst
*   Energy
*   Work schedules
*   Profession progression
*   Learned skills
*   Calendar progression
*   Seasons
*   Buildings
*   Village development
*   Village character

Townstead support includes a large additional collection of quests, village projects, and dynamic situations.

Long-term objectives can follow actual Townstead simulation instead of using arbitrary timers. A quest asking a villager to work for a week, for example, can track completed work shifts rather than requiring the player to stand nearby watching a countdown.

If Townstead is removed while one of its quests is active, the quest can **suspend instead of failing**. Its progress is preserved until the dependency becomes available again.

### MCA: Conversations

With **MCA: Conversations** installed, quest dialogue can take place through the conversation system instead of relying only on static quest text.

Conversation-driven objectives can also require the player to actually speak with the appropriate villager.

Without MCA: Conversations, normal quest dialogue continues to work.

### MCA: Reputation

When **MCA: Reputation** is installed, the Journal can link directly to the corresponding village standing and deed information.

Without it, MCA: Quests continues using its own built-in village reputation system.

### FTB Quests

Optional **FTB Quests** integration allows quest books to interact with MCA: Quests progression.

FTB Quests content can read or reward things such as:

*   MCA villager quest progress
*   Relationship quest chains
*   Village reputation
*   Titles
*   Village projects
*   Situations
*   MCA hearts
*   Marriage-related state

MCA: Quests datapacks can also interact with FTB Quests progression.

FTB Quests is not required.

### JourneyMap

With **JourneyMap 6.0.0+**, active quest destinations can automatically appear as map waypoints.

Quest waypoints move as objectives change and are removed when they are no longer needed.

Players can also create persistent JourneyMap waypoints from quest destinations.

### Xaero's Minimap

**Xaero's Minimap 26.0.0+** is also supported for quest destinations.

Automatic waypoints follow quest progress in the same way as the built-in tracker.

On Xaero-only installations, manually pinned quest destinations are session waypoints and do not persist after closing the game.

### Map Atlases

With **Map Atlases** installed, active quest destinations automatically appear on its atlas fullscreen screen, its native minimap, and the map shown by a held atlas, plus a small rim arrow when the followed quest is off-screen.

A **Quest destinations** list covers every eligible target and can focus the followed quest, and explicit **Show in atlas** and **Save pin** actions are offered alongside the JourneyMap and Xaero options.

This integration is client-side only and checks the installed Map Atlases build against one exact, verified release; on any other build it disables itself and quest guidance continues to work as before.

Nothing is required from pack authors — quest destinations appear automatically wherever Map Atlases is installed.

### Create: Numismatics

Install **Create: Numismatics** and configure MCA: Quests to use its coins as the global quest currency.

This allows existing and custom quests to use a modpack's economy without rewriting every quest.

## Built for Modpacks

Almost the entire quest system is **datapack-driven**.

Pack authors can add their own quests without writing Java code.

Custom quests are loaded from:

```
data/<namespace>/mcaquests/quests/
```

Village projects can be added through:

```
data/<namespace>/mcaquests/projects/
```

Situations can be added through:

```
data/<namespace>/mcaquests/situations/
```

Quest authors have access to a large collection of objectives, conditions, rewards, relationships, progression rules, quest history checks, composite conditions, dialogue pools, and integrations.

Useful commands include:

```
/mcaquests list
/mcaquests validate
/mcaquests reload
/mcaquests export-schema
```

`/mcaquests export-schema` generates a working quest example that can be used as a starting point for custom content.

Full datapack and configuration documentation is available in the project's GitHub repository.

## Extensive Configuration

Separate server/common and client configuration files are generated automatically.

Pack authors and server owners can configure gameplay behavior such as:

*   Number of quest offers
*   Quest cooldowns
*   Repeat behavior
*   Relationship heart rewards
*   Reward scaling
*   Currency
*   Turn-in rules
*   Integration features
*   Villager behavior
*   Project and situation systems

Players can separately configure visual features such as:

*   HUD placement
*   Quest markers
*   Marker visibility
*   Marker labels
*   Screen-edge indicators
*   High contrast
*   Reduced motion
*   Map waypoints
*   Sounds
*   Toast notifications

## Add-On API

For developers, MCA: Quests exposes a Java API for registering additional:

*   Objective types
*   Reward types
*   Condition types
*   Quest guidance behavior

NeoForge events are also available for major quest lifecycle events, including:

*   Quest accepted
*   Quest declined
*   Quest ready
*   Quest completed
*   Quest abandoned
*   Quest failed

This makes MCA: Quests suitable as a foundation for additional MCA-focused mods and modpack-specific integrations.

## Multiplayer Friendly

Quest validation, progress, and rewards are handled server-side.

Reward delivery is designed to be atomic and resistant to packet spam or duplicate turn-ins, while shared Village Projects provide server-wide cooperative objectives.

Player-specific tracking and villager highlighting remain personal to each player.

## Languages

MCA: Quests currently includes full translations for:

*   English
*   Português do Brasil

All built-in interface text, quest dialogue, objectives, rewards, projects, situations, and other content use translation keys, making additional community translations possible.

## Requirements

| Requirement                   |Version                                      |
| ----------------------------- |-------------------------------------------- |
| <strong>Minecraft</strong>    |1.21.1                                       |
| <strong>Mod Loader</strong>   |NeoForge 21.1.x                       |
| <strong>Minecraft Comes Alive: Reborn</strong> |Required, 7.7.x NeoForge builds for 1.21.1 |
| <strong>Architectury API</strong> |Only when another installed mod requires it                                     |

### Optional Integrations

| Mod                     |Purpose                                                                                                                    |
| ----------------------- |-------------------------------------------------------------------------------------------------------------------------- |
| <strong>Townstead</strong> |Expanded village simulation, quests, projects, situations, needs, professions, skills, buildings, and calendar integration |
| <strong>MCA: Conversations</strong> |Conversation-based quest dialogue and objectives                                                                           |
| <strong>MCA: Reputation</strong> |Reputation and deed screen integration                                                                                     |
| <strong>FTB Quests</strong> |Two-way quest book integration                                                                                             |
| <strong>Create: Numismatics</strong> |Alternative quest currency                                                                                                 |
| <strong>JourneyMap 6.0.0+</strong> |Quest map waypoints                                                                                                        |
| <strong>Xaero's Minimap 26.0.0+</strong> |Quest map waypoints                                                                                                        |
| <strong>Map Atlases</strong> |Quest destinations on the atlas screen, minimap and held-atlas view (client-side only)                                     |

## Getting Started

1.  Install **NeoForge 21.1.x**, **Minecraft Comes Alive: Reborn for 1.21.1**, and this **MCA: Quests NeoForge port**.

2.  Launch your world.

3.  Right-click an adult MCA villager.

4.  Select **Quests** from their interaction menu.

5.  Choose an available quest and accept it.

6.  Follow the Quest Log, HUD tracker, or world marker to complete the objectives.

7.  Return to the appropriate villager and complete the quest to receive your rewards.


MCA: Quests is built to make helping villagers feel like part of living in their world rather than a separate quest system placed on top of it.

Whether you want a few extra reasons to interact with your neighbors, long-running character stories, cooperative village projects, or a foundation for a heavily customized RPG modpack, the entire system is designed to grow with the world around it.

**Source, documentation, configuration reference, and datapack guide:**<br>
[https://github.com/otectus/MCAQuests](https://github.com/otectus/MCAQuests)

_MCA: Quests is an independent add-on for Minecraft Comes Alive: Reborn._
