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
- 📜 **69 built-in quests** spanning **every villager profession** — farmer, fisherman, shepherd, librarian, cleric, armorer, weaponsmith, toolsmith, mason, butcher, fletcher, leatherworker, cartographer — plus **jobless/nitwit** villagers and MCA's **guard** (with bonus content for archers, adventurers, and mercenaries).
- 🎭 **Multi-offer conversation UI** — villagers present several quests at once, each with flavor dialogue, objectives, and a reward summary; Accept / Decline / Complete / Abandon inline.
- 🔗 **Relationship quest chains** — link quests into multi-stage arcs that remember what you did: a farmer asks for wheat, then to expand the farm, then to help an apprentice, then invites you to a feast. Chains branch on whether you completed, failed, or abandoned earlier steps, and the UI shows the arc name and "Part 2 of 4". All datapack-driven — no code required (4 sample arcs included).
- 🎯 **10 objective types** — deliver items, gather, craft, fish, kill mobs, break/place blocks, visit biomes or dimensions, and talk to professions.
- 🎁 **7 reward types** — items, XP, XP levels, status effects, loot tables, commands (off by default), and **MCA hearts**.
- 🔒 **14 condition types** + `all_of` / `any_of` / `not` composites — gate offers by hearts, profession, biome, dimension, time, weather, held item, advancement, level, random chance, and quest history (completed / not-completed / failed / abandoned).
- 🧭 **Quest tracking** — a keybind-toggled **Quest Log**, a fully repositionable **HUD tracker** that names the giver, and a **toast + sound** when a quest is ready to turn in.
- 🛡️ **Server-authoritative & exploit-resistant** — all selection, validation, and reward granting happen on the server; turn-in is atomic and idempotent, so packet-spam can't duplicate rewards.
- 🧩 **Extensible** — a public Java API lets add-ons register their own objective/reward/condition types, and five Forge events (`QuestAccepted/Ready/Completed/Abandoned/Failed`) let other mods react.

## Requirements

| | |
|---|---|
| **Minecraft** | 1.20.1 |
| **Mod loader** | Forge 47.4.10 or newer |
| **[MCA Reborn](https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn)** | **Required** — 7.6.x |
| **[Architectury API](https://www.curseforge.com/minecraft/mc-mods/architectury-api)** | **Required** (Forge) — MCA Reborn depends on it |

MCA: Quests does nothing on its own — it is an add-on for MCA Reborn.

## Installation

1. Install **Forge** for Minecraft 1.20.1.
2. Drop **MCA Reborn**, **Architectury API**, and **MCA: Quests** into your `mods/` folder.
3. Launch. Right-click an adult MCA villager and click **Quests**.

## How it works

Right-click a villager → **Quests** → pick an offer → **Accept**. Track it via the HUD or the Quest Log keybind (bind "Open Quest Log" in Controls). When the objective is done you'll get a toast; return to an eligible villager and **Complete** it to claim your rewards and earn hearts.

## Configuration

Two files are generated in `config/` on first run:

- `mcaquests-common.toml` — gameplay rules (offer counts, cooldowns, hearts scaling, turn-in rules, the villager-follow toggle, …).
- `mcaquests-client.toml` — visuals (HUD position, toasts, sounds, the menu button).

See **[CONFIG.md](CONFIG.md)** for every option and its default.

## Add your own quests

Quests load from any datapack at `data/<namespace>/mcaquests/quests/**.json`. Run `/mcaquests export-schema` in-game for a working example, and see **[DATAPACK.md](DATAPACK.md)** for the full field reference and a step-by-step datapack walkthrough. Useful commands: `/mcaquests list`, `/mcaquests validate`, `/mcaquests reload`.

## Building from source

Requires **JDK 17**.

```bash
./gradlew build
```

The jar lands in `build/libs/`. MCA Reborn and Architectury are pulled automatically (MCA via the Modrinth Maven). Note: `gradle.properties` pins `org.gradle.java.home` to a local JDK 17 path — adjust it to your own JDK 17 install, or remove it and run Gradle with `JAVA_HOME` pointed at JDK 17.

## Compatibility note

MCA Reborn exposes no public API, so MCA: Quests links against its internal classes and is therefore pinned to the **7.6.x** line. A future MCA major version may require an update here. All MCA access is isolated behind a single `McaCompat` adapter to make that easy.

## License & credits

Licensed under **[GPL-3.0](LICENSE.md)**, matching MCA Reborn (whose internals this mod links against).

- **Minecraft Comes Alive: Reborn** by Luke100000 and contributors — the mod this builds on.
- Created by **otectus**.
