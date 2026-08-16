# MCA: Quests

**Give your MCA villagers something to ask of you.**

MCA: Quests adds an RPG-style quest system to **Minecraft Comes Alive: Reborn**. Right-click any villager, open the brand-new **Quests** menu woven right into MCA's interaction screen, and take on jobs for the people of your world — fetch a farmer's harvest, clear a guard's patrol of the dead, chart the mountains for a cartographer, or simply pick flowers for a lonely soul who has no trade at all.

Complete the work, return, and turn it in for loot, XP, and — most importantly — a real boost to your **hearts** with that villager. Quests are the fastest, most natural way to win a village over.

> ⚠️ **Requires [MCA Reborn](https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn) (NeoForge build).** This is an add-on — it does nothing without MCA Reborn installed.

## ✨ What you get

- **A Quests button right in the villager menu** — no commands, no clunky extra screens. If you can talk to a villager, you can quest for them.
- **150+ hand-written quests across every profession** — farmers, fishers, shepherds, librarians, clerics, armorers, weaponsmiths, toolsmiths, masons, butchers, fletchers, leatherworkers, cartographers, the town guard, and even **jobless villagers and nitwits** with simple, charming errands.
- **Relationship quest chains** — quests remember what you've done and grow into ongoing stories, tracked **per villager** so each one's arc is your own with them. A farmer asks for wheat, then to expand the farm, then to help an apprentice, then invites you to a village feast. Chains even branch on whether you came through or let someone down — and the menu shows "Part 2 of 4" so you always know where the story stands. Eight sample arcs are built in, including a courtship, a frantic search for a lost child, and caring for an aging parent.
- **Village projects you build together** — sometimes a whole town needs a hand. A sponsor villager rallies the village toward a shared, multi-stage goal — repair the well, restock the guardhouse, restore the library, recover after a raid — and **everyone pitches in**. Progress is shared and saved with the world, so on a server your friends' donations, kills, and builds all count toward the same prize. Finish a stage and rewards go out to the people who helped — the top contributor, every participant, or the village itself — along with a lift to your standing in town. Six community projects ship in the box, and pack makers can write their own. *(Great fun on multiplayer; works solo too.)*
- **Earn your place in town** — every village remembers what you've done. Reputation now climbs a named ladder — Stranger, Acquaintance, Friend, Honored, Revered — with a toast when you rise and **titles** you earn along the way. Some quests only open up once a village trusts you. Flip open the new **Journal** to see your standing with each village, the titles you've collected, and a log of every quest you've completed. Pack makers can rewrite the whole ladder.
- **Quests that know your villagers** — offers can react to MCA life-sim state: a spouse asks for a small favor, your child begs for cookies, a sick villager needs a golden apple, the town guard calls for a patrol, a grieving parent pleads for help finding a missing child. All optional and datapack-driven, with six sample quests built in.
- **Quests about people, homes, and places** — walk a villager home before nightfall, protect a child while they gather flowers, defend the guard captain from the undead, cure an infected neighbor, carry a letter to someone's brother, repair the village well, or trade with the local blacksmith. Twelve new objective types bring escorting, protecting, defending, trading, healing, curing, breeding, taming, resting, building near a place, and entering structures — all saved with the world, so they survive logouts, deaths, and server restarts.
- **Villagers who actually talk to you** *(with MCA: Conversations)* — install the optional **MCA: Conversations** add-on and villagers speak their quest lines in their own voice and personality, and "go talk to so-and-so" quests progress from a real conversation instead of a checkbox. Not installed? Everything still works — dialogue just reads as written.
- **FTB Quests integration** *(optional)* — build FTB Quests books that read your villager quests, reputation, titles, and hearts, and grant those same rewards straight from the book. Not installed? Nothing changes.
- **Real variety** — deliver and gather, craft and fish, hunt monsters, mine and build, and explore distant biomes and dimensions. Some quests only appear at night, or in the rain.
- **Earn hearts that matter** — rewards feed directly into MCA's relationship system, alongside emeralds, XP, and more. Courting a villager is a real investment now, not something you can rush in an afternoon of repeating one errand.
- **An economy that fits your pack** — quests pay in *currency*, not hard-coded emeralds. Tag a quest easy, medium, or hard and the server picks the payout range and the coin: emeralds by default, **Create: Numismatics** coins, or any item you choose — one config line, and every installed quest pack follows. Payouts are locked in the moment you accept, so what the card promises is what you get.
- **Agora em Português (Brasil)** — the whole mod, fully translated: menus, objectives, rewards, and every line of villager dialogue across all 150+ quests, the relationship arcs, the village projects, and the emergencies. Every built-in line is now a translation key, so more languages can follow.
- **Track everything** — a toggleable Quest Log, an on-screen tracker you can place in any corner, and a satisfying chime when a quest is ready to hand in. Changed your mind? Abandon any quest right from the log, even if its villager is long gone.
- **Built for modpacks** — every quest is a simple JSON file. Pack makers can add, remove, or rebalance quests with a datapack, no code required. A full config lets you tune offer counts, cooldowns, heart rewards, and more.

## 🎮 How to play

1. Install **MCA Reborn** and **MCA: Quests**.
2. Right-click an adult villager and click **Quests**. *(Sneak-right-click is no longer a shortcut — it was interfering with MCA's own villager menus and with other mods.)*
3. Pick an offer, **Accept**, and head out. Watch the tracker fill up.
4. Come back and **Complete** it to claim your rewards and win their heart.

## 🛠️ For pack makers

- Add your own quests via datapack: `data/<namespace>/mcaquests/quests/**.json`. Run `/mcaquests export-schema` for a ready-to-edit example.
- Author shared **village projects** the same way, from `data/<namespace>/mcaquests/projects/**.json` — pick a scope, lay out the phases, and reward your contributors.
- Full schema reference and config docs are on the [GitHub repository](https://github.com/otectus/MCAQuests).
- A Java API + NeoForge events let other mods register custom objective/reward/condition types and react to quest progress.

## 📋 Details

- **Minecraft:** 1.21.1 · **NeoForge** 21.1.0+
- **Requires:** MCA Reborn 7.7.x (NeoForge build)
- **Optional:** MCA: Conversations — voiced quest dialogue & conversation-driven objectives
- **Optional:** FTB Quests — two-way book integration · **Create: Numismatics** — pay quest rewards in coins
- **Languages:** English, Português (Brasil)
- Server-authoritative and dup-proof — safe for multiplayer.
- Source & docs: **https://github.com/otectus/MCAQuests**
- Licensed under **GPL-3.0**, matching MCA Reborn.

*Minecraft Comes Alive: Reborn is by Luke100000 and contributors. MCA: Quests is an independent add-on.*
