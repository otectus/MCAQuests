# MCA: Quests

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/Loader-Forge%2047.4.10%2B-1f425f)](https://files.minecraftforge.net/)
[![Requires](https://img.shields.io/badge/Requires-MCA%20Reborn%207.6.x-orange)](https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue)](LICENSE.md)

An RPG-style, **datapack-driven quest system** for **[Minecraft Comes Alive: Reborn](https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn)** villagers. Right-click a villager, open the new **Quests** menu, accept a job, complete it out in the world, and turn it in for items, XP, status effects — and a meaningful boost to your **MCA hearts** with that specific villager.

> Server-authoritative, dup-proof, and fully data-driven: every quest is a JSON file, so modpacks and players can add their own without writing code.

---

## Features

- 🗨️ **Integrated menu** — a **Quests** button is injected directly into MCA's villager interaction screen (no extra steps, no separate UI to learn).
- 📜 **150+ built-in quests** spanning **every villager profession** — farmer, fisherman, shepherd, librarian, cleric, armorer, weaponsmith, toolsmith, mason, butcher, fletcher, leatherworker, cartographer — plus **jobless/nitwit** villagers and MCA's **guard** (with bonus content for archers, adventurers, and mercenaries).
- 🎭 **Multi-offer conversation UI** — villagers present several quests at once, each with flavor dialogue, objectives, and a reward summary; Accept / Decline / Complete / Abandon inline.
- 🔗 **Relationship quest chains** — link quests into multi-stage arcs that remember what you did: a farmer asks for wheat, then to expand the farm, then to help an apprentice, then invites you to a feast. Chains branch on whether you completed, failed, or abandoned earlier steps, and the UI shows the arc name and "Part 2 of 4". All datapack-driven — no code required (4 sample arcs included).
- 🌾 **Living Village situations** — villages **react to what happens in the world**. Raids, deaths, zombie infections, missing kin, famine, and nightfall open transient **situations** that surface dynamic, **time-limited** quest offers on nearby villagers — "drive back the raid", "cure the infected", "find the missing child", "fill the empty granary" — and resolve into village reputation when you help (or fail to). Server-authoritative, throttled so they never spam, and persisted in the world save. Fully datapack-driven (`situations/`) and backward compatible; 6 example situations included.
- 🏘️ **Village projects** — shared, multi-stage **community goals** that the whole server works on together. A sponsor villager rallies the town — repair the well, restock the guardhouse, restore the library — and **any player can contribute**: progress is shared and lives in the world save, so donations, kills, and builds all bank into one common pool. Pick a **scope** (player, villager, family, profession, or village), and phases hand out **shared rewards** (to contributors, the top contributor, everyone who helped, or the village) plus mod-side village reputation. Fully datapack-driven and backward compatible; 6 example projects included.
- 🏅 **Progression: reputation tiers & titles** — village reputation now climbs a named ladder (Stranger → Acquaintance → Friend → Honored → Revered), with a tier-up toast and auto-earned **titles**. Gate quests on a player's standing with the `reputation_tier` condition, award titles with the `grant_title` reward, and track it all in a new **Journal** screen (village reputation, tiers, titles, and a completed-quest archive). Fully datapack-driven (`reputation_tiers/`, `titles/`) and backward compatible.
- 🎯 **10 objective types** — deliver items, gather, craft, fish, kill mobs, break/place blocks, visit biomes or dimensions, and talk to professions.
- 🎁 **8 reward types** — items, **currency**, XP, XP levels, status effects, loot tables, commands (off by default), and **MCA hearts**.
- 💰 **Semantic currency & difficulty bands** — a quest asks for *money*, not for emeralds. Tag a quest `easy` / `medium` / `hard` and the server decides the payout range and the coin: vanilla emeralds by default, **Create: Numismatics** coins, or any item you like — one config line, no datapack rewritten. Numismatics is resolved by registry id and never linked against, so it stays a genuine optional. Amounts are rolled **once at accept time** and frozen, so the number on the card is the number you're paid and reopening the menu can't reroll it.
- 🌍 **Fully localizable, fully translated** — every one of the 1,582 built-in strings is a translation key, and **Brazilian Portuguese** ships complete alongside English: interface, objectives, rewards, quest dialogue, relationship arcs, projects, and situations. Automated parity checks keep every locale honest.
- 🔒 **25 condition types** + `all_of` / `any_of` / `not` composites — gate offers by hearts, profession, biome, dimension, time, weather, held item, advancement, level, random chance, and quest history (completed / not-completed / failed / abandoned).
- 💞 **MCA-aware conditions** — gate quests on the villager's life-sim state: whether they're your **spouse** or **family**, their **relationship status**, **age**, **personality**, **mood**, **village/home**, **health**, or **infection**, and even whether a **relative is missing or has died**. All optional, datapack-driven, and fail-safe; 6 sample quests included (spouse errand, child's request, sick-villager remedy, guard patrol, missing-child search, memorial).
- 🧭 **Quest tracking** — a keybind-toggled **Quest Log** (with an **Abandon** button per quest, so you can always drop one even if its giver is gone), a fully repositionable **HUD tracker** that names the giver, and a **toast + sound** when a quest is ready to turn in.
- 🔦 **Never lose your quest target** — the villager a quest wants you to find is **outlined through walls**, for you alone, and the tracker names them with a live distance and compass bearing ("Hans — 84 blocks to your right"). Out of render distance, it points at their last known home. Family quests bind one *specific* relative when you accept, so the name on the card, the villager that glows, and the one who takes the parcel are always the same person — and a relative who has genuinely gone **missing** is found out in the world, materialised with their real name and every family link intact.
- 🛡️ **Server-authoritative & exploit-resistant** — all selection, validation, and reward granting happen on the server; turn-in is atomic and idempotent, so packet-spam can't duplicate rewards.
- 🧩 **Extensible** — a public Java API lets add-ons register their own objective/reward/condition types, and five Forge events (`QuestAccepted/Ready/Completed/Abandoned/Failed`) let other mods react.
- 💬 **MCA: Conversations integration** (optional) — with the **MCA: Conversations** add-on installed, villagers **speak** a quest's offer / progress / completion / failure line in their own personality instead of the static text, and "talk to this villager" objectives progress from an **actual conversation**. MCA: Quests ships only the hooks and safe fallbacks — without the add-on, dialogue stays as written and nothing else changes.
- 📖 **FTB Quests integration** (optional) — ten task types and three reward types let an FTB Quests book read and grant real MCA: Quests progress (villager quests, relationship arcs, reputation, titles, projects, situations, hearts, marriage), and three conditions plus an objective and a reward let MCA: Quests datapacks read and write FTB book progress right back. Fully optional in both directions — see **[FTBQUESTS.md](FTBQUESTS.md)**.

## Requirements

| | |
|---|---|
| **Minecraft** | 1.20.1 |
| **Mod loader** | Forge 47.4.10 or newer |
| **[MCA Reborn](https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn)** | **Required** — 7.6.x |
| **[Architectury API](https://www.curseforge.com/minecraft/mc-mods/architectury-api)** | **Required** (Forge) — MCA Reborn depends on it |
| **MCA: Conversations** | *Optional* — enables voiced quest dialogue & conversation-driven objectives |
| **[FTB Quests](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge)** | *Optional* — 2001.4.x tested; enables the FTB task/reward integration ([FTBQUESTS.md](FTBQUESTS.md)) |
| **Create: Numismatics** | *Optional* — set `currencyProvider = NUMISMATICS` to pay quest rewards in coins ([CONFIG.md](CONFIG.md#rewardscurrency)) |

MCA: Quests does nothing on its own — it is an add-on for MCA Reborn.

## Installation

1. Install **Forge** for Minecraft 1.20.1.
2. Drop **MCA Reborn**, **Architectury API**, and **MCA: Quests** into your `mods/` folder.
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

Requires **JDK 17**.

```bash
./gradlew build
```

The jar lands in `build/libs/`. MCA Reborn and Architectury are pulled automatically (MCA via the Modrinth Maven). Note: `gradle.properties` pins `org.gradle.java.home` to a local JDK 17 path — adjust it to your own JDK 17 install, or remove it and run Gradle with `JAVA_HOME` pointed at JDK 17.

## Compatibility note

MCA Reborn exposes no public API, so MCA: Quests links against its internal classes and is therefore pinned to the **7.6.x** line. A future MCA major version may require an update here. All MCA access is isolated behind a single `McaCompat` adapter to make that easy.

The **MCA: Conversations** integration is a soft dependency: MCA: Quests exposes the dialogue and objective hooks (`QuestDialogueHooks`, `ExternalSignalObjective`) and the add-on registers itself against them. When it isn't installed the hooks simply no-op — quest dialogue falls back to the static datapack text and objectives progress through their normal detectors.

The **FTB Quests** integration is likewise a soft, optional dependency (`mandatory=false`, tested against **2001.4.x**): every FTB-facing task/reward routes through an internal bridge that becomes an inert no-op if FTB Quests is absent, disabled, or throws, so nothing about MCA: Quests' own datapack format or world save depends on it being installed. The integration compiles against FTB's publicly published maven artifacts and ships none of them — the jar contains zero FTB code. See **[FTBQUESTS.md](FTBQUESTS.md)** for the full task/reward/condition reference.

## License & credits

Licensed under **[GPL-3.0](LICENSE.md)**, matching MCA Reborn (whose internals this mod links against).

- **Minecraft Comes Alive: Reborn** by Luke100000 and contributors — the mod this builds on.
- Created by **otectus**.
