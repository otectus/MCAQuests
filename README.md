# MCA: Quests

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/Loader-NeoForge%2021.1-1f425f)](https://neoforged.net/)
[![Requires](https://img.shields.io/badge/Requires-MCA%20Reborn%207.7-orange)](https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue)](LICENSE.md)

An RPG-style, **datapack-driven quest system** for **[Minecraft Comes Alive: Reborn](https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn)** villagers. Right-click a villager, open the new **Quests** menu, accept a job, complete it out in the world, and turn it in for items, XP, status effects — and a meaningful boost to your **MCA hearts** with that specific villager.

> Server-authoritative, dup-proof, and fully data-driven: every quest is a JSON file, so modpacks and players can add their own without writing code.

---

## Features

- 🗨️ **Integrated menu** — a **Quests** button is injected directly into MCA's villager interaction screen (no extra steps, no separate UI to learn).
- 📜 **260+ built-in quests** spanning **every villager profession** — farmer, fisherman, shepherd, librarian, cleric, armorer, weaponsmith, toolsmith, mason, butcher, fletcher, leatherworker, cartographer — plus **jobless/nitwit** villagers and MCA's **guard** (with bonus content for archers, adventurers, and mercenaries).
- 🎭 **Multi-offer conversation UI** — villagers present several quests at once, each with flavor dialogue, objectives, and a reward summary; Accept / Decline / Complete / Abandon inline. The villager themselves stands in the header, turning to follow your cursor.
- 🖼️ **A real interface, in vanilla's own language** — nine-sliced panels, framed cards, textured buttons and scrollbars you can actually drag, reward **item icons** with vanilla tooltips, per-objective progress bars, difficulty badges, and heart icons. The quest log and journal share one tabbed window. Every screen adapts from a 320×240-equivalent display up to 4K, at any GUI scale, and is reachable by keyboard and narrator.
- 🔗 **Relationship quest chains** — link quests into multi-stage arcs that remember what you did: a farmer asks for wheat, then to expand the farm, then to help an apprentice, then invites you to a feast. Chains branch on whether you completed, failed, or abandoned earlier steps, and the UI shows the arc name and "Part 2 of 4". All datapack-driven — no code required (4 sample arcs included).
- 🌾 **Living Village situations** — villages **react to what happens in the world**. Raids, deaths, zombie infections, missing kin, famine, nightfall, a resident stranded outside the walls after dark, mobs gathering at somebody's door — and, with Townstead, a season turning, a villager coming of age or retiring, and a village losing its routine — open transient **situations** that surface dynamic, **time-limited** quest offers on nearby villagers — "drive back the raid", "cure the infected", "find the missing child", "fill the empty granary" — and resolve into village reputation when you help (or fail to). Server-authoritative, throttled so they never spam, and persisted in the world save. Fully datapack-driven (`situations/`) and backward compatible; 6 example situations included.
- 🏘️ **Village projects** — shared, multi-stage **community goals** that the whole server works on together. A sponsor villager rallies the town — repair the well, restock the guardhouse, restore the library — and **any player can contribute**: progress is shared and lives in the world save, so donations, kills, and builds all bank into one common pool. Pick a **scope** (player, villager, family, profession, or village), and phases hand out **shared rewards** (to contributors, the top contributor, everyone who helped, or the village) plus mod-side village reputation. Fully datapack-driven and backward compatible; 6 example projects included.
- 🏅 **Progression: reputation tiers & titles** — village reputation now climbs a named ladder (Stranger → Acquaintance → Friend → Honored → Revered), with a tier-up toast and auto-earned **titles**. Gate quests on a player's standing with the `reputation_tier` condition, award titles with the `grant_title` reward, and track it all in a new **Journal** screen (village reputation, tiers, titles, and a completed-quest archive). Fully datapack-driven (`reputation_tiers/`, `titles/`) and backward compatible.
- 🎯 **Objective types for what a quest is actually about** — deliver, gather, craft, fish, kill, break and place blocks, visit biomes, dimensions and structures, reach and defend places, escort and protect villagers, breed and tame animals, cure the infected, find missing kin, talk to professions — and, with Townstead, hold a state, move a value, work whole shifts, raise a building, grow a village's character and keep its people well.
- 🎁 **8 reward types** — items, **currency**, XP, XP levels, status effects, loot tables, commands (off by default), and **MCA hearts**.
- 💰 **Semantic currency & difficulty bands** — a quest asks for *money*, not for emeralds. Tag a quest `easy` / `medium` / `hard` and the server decides the payout range and the coin: vanilla emeralds by default, **Create: Numismatics** coins, or any item you like — one config line, no datapack rewritten. Numismatics is resolved by registry id and never linked against, so it stays a genuine optional. Amounts are rolled **once at accept time** and frozen, so the number on the card is the number you're paid and reopening the menu can't reroll it.
- 🌍 **Fully localizable, fully translated** — every one of the 2,891 built-in strings is a translation key, and **Brazilian Portuguese** ships complete alongside English: interface, objectives, rewards, quest dialogue, relationship arcs, projects, and situations. Automated parity checks keep every locale honest.
- 🔒 **25 condition types** + `all_of` / `any_of` / `not` composites — gate offers by hearts, profession, biome, dimension, time, weather, held item, advancement, level, random chance, and quest history (completed / not-completed / failed / abandoned).
- 💞 **MCA-aware conditions** — gate quests on the villager's life-sim state: whether they're your **spouse** or **family**, their **relationship status**, **age**, **personality**, **mood**, **village/home**, **health**, or **infection**, and even whether a **relative is missing or has died**. All optional, datapack-driven, and fail-safe; 6 sample quests included (spouse errand, child's request, sick-villager remedy, guard patrol, missing-child search, memorial).
- 🗣️ **Villagers who explain themselves** — a villager with nothing to offer tells you *why*: you did that yesterday, or you have not earned it yet. Greetings and refusals vary with their **personality**, and with anything else the condition language can see — mood, time of day, weather, how well they know you. Shipped as datapack `dialogue/` pools, so a pack can add a voice, shadow the built-in one, or gate a line on whatever it likes.
- 🧭 **Quest tracking** — a keybind-toggled **Quest Log** (with an **Abandon** button per quest, so you can always drop one even if its giver is gone), a fully repositionable **HUD tracker** that names the giver, and a **toast + sound** when a quest is ready to turn in.
- 🔦 **Never lose your quest target** — follow a quest from the log and the mod answers *where next*: a **compact marker in the world** (24 px diamond frame with glyph and ring), visible through walls, standing on the place the objective you are actually on is about, and a tracker line naming it with a live distance and bearing ("Anna's home — 84 blocks ahead-right"). It moves by itself as each step lands — a portal until you reach the Nether, a fortress once you are there, the villager to hand it back to when you are done — and fades out as you arrive. An optional on-screen **edge indicator** (18 px diamond with distance/angle) shows when the quest is off-screen or behind the camera. Behind terrain the marker either dims to a faint outline or vanishes (configurable). The villager a quest wants you to reach is **outlined**, for you alone, and only while reaching them is what you are doing. Family quests bind one *specific* relative when you accept, so the name on the card, the villager that glows, and the one who takes the parcel are always the same person — and a relative who has genuinely gone **missing** is found out in the world, materialised with their real name and every family link intact.
- 🛡️ **Server-authoritative & exploit-resistant** — all selection, validation, and reward granting happen on the server; turn-in is atomic and idempotent, so packet-spam can't duplicate rewards.
- 🧩 **Extensible** — a public Java API lets add-ons register their own objective/reward/condition types (including where their objective sends the player, via `QuestObjective.guidance`), and six Forge events (`QuestAccepted/Declined/Ready/Completed/Abandoned/Failed`) let other mods react. A reward may also override `grant(player, villager, RewardContext)` to deliver itself when the quest giver is not loaded, using the giver identity and village the quest froze when it was accepted.
- 💬 **MCA: Conversations integration** (optional) — with the **MCA: Conversations** add-on installed, villagers **speak** a quest's offer / progress / completion / failure line in their own personality instead of the static text, and "talk to this villager" objectives progress from an **actual conversation**. MCA: Quests ships only the hooks and safe fallbacks — without the add-on, dialogue stays as written and nothing else changes.
- 📖 **FTB Quests integration** (optional) — ten task types and three reward types let an FTB Quests book read and grant real MCA: Quests progress (villager quests, relationship arcs, reputation, titles, projects, situations, hearts, marriage), and three conditions plus an objective and a reward let MCA: Quests datapacks read and write FTB book progress right back. Fully optional in both directions — see **[FTBQUESTS.md](FTBQUESTS.md)**.
- 🏡 **Townstead integration** (optional) — with **[Townstead](https://www.curseforge.com/minecraft/mc-mods/townstead)** installed, a villager's **hunger, thirst, energy, shift schedule, profession track, learned skills** and their village's **calendar, buildings and character** all become things a quest can be about. **Seventy-three quests, eleven community projects and fourteen situations** ship with it. Spend a whole year with one farmer, season by season, on the calendar your server actually loaded. Take a dock from wet stones to deep water and light the way back in. Stay through every dull year of somebody's apprenticeship. Grow a nameless settlement into a place people describe without being prompted. Bread you hand over goes **into the villager's own inventory**, where Townstead lets them actually eat it. Remove Townstead later and an in-progress quest **suspends with its progress intact** rather than failing — see **[TOWNSTEAD.md](TOWNSTEAD.md)**.

- 🕰️ **Quests that take real time, honestly** — "work a week at the forge" counts *shifts the villager completed*, across your logouts and server restarts, rather than asking you to stand and watch a timer. A shift you were not there for ends **unknown** rather than missed, so walking away for a night never silently breaks a streak. "Once a season" means once a season **on your calendar**, whether a Townstead season is three days or thirty.

## Requirements

| | |
|---|---|
| **Minecraft** | 1.21.1 |
| **Mod loader** | NeoForge 21.1.x |
| **Java** | JDK 21 (Gradle provisions it via foojay if JAVA_HOME is 17) |
| **[MCA Reborn](https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn)** | **Required** — 7.7.x (no longer requires Architectury; MCA 1.21.1 dropped it) |
| **MCA: Conversations** | *Optional* — enables voiced quest dialogue & conversation-driven objectives |
| **[FTB Quests](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge)** | *Optional* — 2101.1.x; enables the FTB task/reward integration ([FTBQUESTS.md](FTBQUESTS.md)) |
| **[Townstead](https://www.curseforge.com/minecraft/mc-mods/townstead)** | *Optional* — `[0.7.5,0.8)`, verified against **0.7.6**; adds villager needs, professions, skills and village character as quest state ([TOWNSTEAD.md](TOWNSTEAD.md)) |
| **[Ice & Fire: Community Edition](https://www.curseforge.com/minecraft/mc-mods/ice-and-fire-dragons)** | *Optional* — 2.0 and later; quest targets for dragons, hippogryphes and creatures; auto-detects original or Community Edition build ([ICEANDFIRE.md](ICEANDFIRE.md)) |
| **[Bountiful](https://www.curseforge.com/minecraft/mc-mods/bountiful)** | *Optional* — 8.0.x; bounty boards and completion as quest objectives ([BOUNTIFUL.md](BOUNTIFUL.md)) |
| **Create: Numismatics** | *Optional* — set `currencyProvider = NUMISMATICS` to pay quest rewards in coins ([CONFIG.md](CONFIG.md#rewardscurrency)) |

MCA: Quests does nothing on its own — it is an add-on for MCA Reborn.

## Installation

1. Install **NeoForge** for Minecraft 1.21.1.
2. Drop **MCA Reborn** and **MCA: Quests** into your `mods/` folder (Architectury is no longer required).
3. Launch. Right-click an adult MCA villager and click **Quests**.

## How it works

Right-click a villager → **Quests** → pick an offer → **Accept**.

> **Note for players upgrading from 1.0.0:** sneak-right-clicking a villager no longer opens the quest menu. That was a leftover debug shortcut, and because it cancelled the interaction it also swallowed MCA's own sneak actions (the villager editor book, inventory, trading) and broke other mods that use sneak-right-click. The injected **Quests** button is now the only entry point, and MCA: Quests never cancels an entity interaction. Track it via the HUD or the Quest Log keybind (bind "Open Quest Log" in Controls). When the objective is done you'll get a toast; return to an eligible villager and **Complete** it to claim your rewards and earn hearts. Changed your mind? **Abandon** it from the villager's menu or straight from the Quest Log.

## Configuration

Two files are generated in `config/` on first run:

- `mcaquests-common.toml` — gameplay rules (offer counts, cooldowns, hearts scaling, turn-in rules, the villager-follow toggle, …).
- `mcaquests-client.toml` — visuals (HUD position, toasts, sounds, the menu button).

See **[CONFIG.md](CONFIG.md)** for every option and its default.

## Add your own quests

Quests load from any datapack at `data/<namespace>/mcaquests/quests/**.json`. Run `/mcaquests export-schema` in-game for a working example, and see **[DATAPACK.md](DATAPACK.md)** for the full field reference and a step-by-step datapack walkthrough. Useful commands: `/mcaquests list`, `/mcaquests validate`, `/mcaquests reload`.

Shared **village projects** load alongside them from `data/<namespace>/mcaquests/projects/**.json` — see the [Village projects](DATAPACK.md#village-projects) section of DATAPACK.md and the `/mcaquests project list|info|validate` commands.

Emergent **situations** load from `data/<namespace>/mcaquests/situations/**.json` — pair a world-event trigger (raid, death, infection, missing kin, famine, night) with a time-limited offer. See the [Situations](DATAPACK.md#situations-the-living-village) section of DATAPACK.md and the `/mcaquests situation list|info|validate|debug` commands.

## Building from source

Requires **JDK 21** (or JDK 17 with `JAVA_HOME` set; Gradle provisions JDK 21 via foojay toolchain resolver).

```bash
./gradlew build
```

The jar lands in `build/libs/`. MCA Reborn is pulled automatically via the Modrinth Maven. `gradle.properties` leaves `org.gradle.java.home` unset, so Gradle uses `JAVA_HOME` or provisions JDK 21 from foojay.

To put the result in a mods folder, use:

```bash
./gradlew installMod -PmodsDir="<path to your instance's mods folder>"
```

## Compatibility note

MCA Reborn exposes no public API, so MCA: Quests reaches into its internal classes. It supports the **7.7.x line** from one jar: rather than linking MCA at compile time, every MCA class and member is resolved **by name at runtime**, against whichever package layout is installed. The mod probes for MCA's package root at startup.

If an MCA build ever ships a layout this version does not recognise, MCA-backed features disable themselves with a single log line and **the server keeps running**; run `/mcaquests debug mca` to see which package root matched and whether anything is missing. All MCA access stays isolated behind the binding layer (`compat/mca`), and a build-time check fails the build if any class ever references an MCA type directly.

The **MCA: Conversations** integration is a soft dependency: MCA: Quests exposes the dialogue and objective hooks (`QuestDialogueHooks`, `ExternalSignalObjective`) and the add-on registers itself against them. When it isn't installed the hooks simply no-op — quest dialogue falls back to the static datapack text and objectives progress through their normal detectors.

The **FTB Quests** integration is likewise a soft, optional dependency (`mandatory=false`, tested against **2101.1.x**): every FTB-facing task/reward routes through an internal bridge that becomes an inert no-op if FTB Quests is absent, disabled, or throws, so nothing about MCA: Quests' own datapack format or world save depends on it being installed. The integration compiles against FTB's publicly published maven artifacts and ships none of them — the jar contains zero FTB code. See **[FTBQUESTS.md](FTBQUESTS.md)** for the full task/reward/condition reference.

The **Townstead** integration is optional in the same way, and is reached even more carefully. Townstead is itself built against MCA, so its own method signatures name MCA classes — binding any of them directly would tie this mod to one MCA package layout and undo the runtime resolution described above. So nothing is bound by parameter type: Townstead members are matched by name and arity and invoked through handles whose arguments are all `Object`, and a build-time check fails the build if any compiled class so much as mentions a Townstead type. The jar contains zero Townstead code and is not compiled against it.

Binding reports **capabilities** rather than a single yes or no, so a Townstead point release that moves one internal method disables exactly the feature that needed it and nothing else. `/mcaquests compat townstead status` says what bound; `probe` checks each capability by actually using it. **Removing Townstead from an existing world is safe**: quests that depend on it suspend with their progress and frozen baselines intact, stay abandonable, and resume where they left off if it comes back. See **[TOWNSTEAD.md](TOWNSTEAD.md)**.


The **JourneyMap** integration (optional, client-side) now uses JourneyMap's documented **plugin API 2.0** (`@JourneyMapPlugin`, `IClientPlugin`), discovered and initialized by JourneyMap's own classloader. The automatic quest waypoints are created with the in-world icon and beacon hidden so MCA: Quests' own marker is the only in-world visual. Waypoints are one per active quest, moved as the quest advances, and taken away when it ends. They are never written into JourneyMap's player waypoint list — they belong to the quest — so uninstalling this mod leaves nothing behind. The **pin waypoint** button in the quest log uses persistent waypoints that survive uninstall.

The **Xaero's Minimap** integration (optional, client-side) works similarly: one waypoint per active quest, moved and cleared with quest state. On Xaero-alone installs (no JourneyMap), pinned waypoints are explicitly labelled as **session waypoints** that exist only while the instance is running, with a distinct tooltip and success message. With JourneyMap installed, pins go there instead (persistent). Both backends use a per-waypoint recovery mechanism with exponential backoff, so failures are retried rather than silently skipped.

`/mcaquestsclient waypoints status` and `probe` report what bound and test the backends. `mapWaypoints` turns the whole thing off; `journeyMapWaypoints` and `xaeroWaypoints` toggle each backend independently.
## License & credits

Licensed under **[GPL-3.0](LICENSE.md)**, matching MCA Reborn (whose internals this mod links against).

- **Minecraft Comes Alive: Reborn** by Luke100000 and contributors — the mod this builds on.
- Created by **otectus**.
