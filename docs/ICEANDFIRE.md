# MCA: Quests × Ice & Fire

**[Ice & Fire](https://www.curseforge.com/minecraft/mc-mods/iceandfire)** (both the original and Community Edition) adds dragons, rare creatures, and untamed structures to the world. This integration makes those a subject for quests: hunt dragons, clear the Dread army, find seekers, or recover from the wild.

There are two builds of this mod sharing the same mod id `iceandfire`. MCA: Quests detects which one is installed and adapts: the original has the Myrmex hive and no Dragon Seekers; Community Edition dropped the hive and added Seekers, netherite armors, and two new mechanics (brush scales and dragon forge blood). Everything else is decided by asking what the registries contain, so a fork can restore dropped content or remove new content and the same save will follow it.

Everything here is **optional in both directions**. Without Ice & Fire the types still register, so your datapacks parse identically, the bundled content simply never becomes eligible, and nothing else about MCA: Quests changes.

---

## Install

1. Install **MCA Reborn**, **MCA: Quests**, and **Ice & Fire** (original or Community Edition, the mod id is shared and either works). Any recent version of either build.
2. Start the server. That is all — no configuration is needed.

Confirm it took with `/mcaquests compat iceandfire status`. You want to see at least the three dragons and the core capability.

---

## How it works

MCA: Quests never compiles against Ice & Fire. Every reference is resolved at runtime by asking the game's registries what exists, and what bound is reported as **capabilities** rather than as a single yes-or-no. That matters in practice: if Ice & Fire adds new content, it appears immediately; if it removes something, only the quests that declared it suspend.

| Capability | What it unlocks |
|---|---|
| `iceandfire.core` | The mod is loaded and at least one dragon exists (usually all three, always at least one) |
| `iceandfire.fire_dragon` | The fire dragon is registered |
| `iceandfire.ice_dragon` | The ice dragon is registered |
| `iceandfire.lightning_dragon` | The lightning dragon is registered |
| `iceandfire.myrmex` | The Myrmex hive (worker or queen; absent from Community Edition by design) |
| `iceandfire.dread_mobs` | All seven of the Dread army — fire, ghoul, beast, scuttler, lich, knight, horse — present and counted |
| `iceandfire.dragon_seekers` | The three obtainable seeker rarities (Godly Seeker is treated as a creative/endgame item and no shipped quest requires it) |
| `iceandfire.netherite_dragon_armor` | All four Netherite dragon armor pieces (head, neck, body, tail; Community Edition only) |
| `iceandfire.netherite_hippogryph_armor` | The Netherite hippogryph armor (Community Edition only) |
| `iceandfire.structures` | The three named structures — Gorgon Temple, Graveyard, Mausoleum — are registered in the world |
| `iceandfire.brush_scales` | Brushing scales off a tamed dragon (Community Edition mechanic; reported as flavor-declared, never registry-based) |
| `iceandfire.dragon_forge_blood` | Blood in the dragon forge (Community Edition mechanic; reported as flavor-declared, never registry-based) |

---

## Gating content — `mcaquests:compat_capability`

Open every Ice & Fire definition with this. It is true only when the capability is available.

```json
{
  "type": "mcaquests:compat_capability",
  "provider": "iceandfire",
  "capability": "iceandfire.fire_dragon"
}
```

Capability names are case-insensitive, and a name that is not a real capability **fails the datapack reload** rather than silently gating on nothing.

Each bundled quest gates on a specific capability matching what it requires:

| Quest | Gating capability |
|---|---|
| `hydra_hunt` | `iceandfire.core` |
| `fire_dragon_hunt` | `iceandfire.fire_dragon` |
| `ice_dragon_hunt` | `iceandfire.ice_dragon` |
| `lightning_dragon_hunt` | `iceandfire.lightning_dragon` |
| `dread_purge` | `iceandfire.dread_mobs` |
| `dragon_seeker_trial` | `iceandfire.dragon_seekers` |
| `netherite_dragon_armor` | `iceandfire.netherite_dragon_armor` |
| `netherite_hippogryph_armor` | `iceandfire.netherite_hippogryph_armor` |

The entire pack mounts only when **both** `iceandfire.core` is present **and** `compat.iceandfire.enableBuiltinContent` is true.

---

## What happens when Ice & Fire is removed

This is the case worth understanding before you build a pack around it.

A quest you accepted while Ice & Fire was installed **does not fail**. It:

- keeps its progress, exactly as it was;
- stops polling, so nothing advances and nothing regresses;
- never reads as complete, so it cannot be turned in;
- shows an amber **"On hold — waiting on a mod that is not installed"** line in the quest log;
- stays abandonable, from both the log and the villager menu;
- and picks up exactly where it left off if Ice & Fire returns.

Suspension is decided fresh every pass rather than written into the save, so recovery needs no migration and nothing can go stale. Offers simply stop appearing because the bundled pack mounts only when the two conditions above are met.

---

## Commands

All at permission level 2, all read-only.

| Command | What it tells you |
|---|---|
| `/mcaquests compat iceandfire status` | Which build is installed (original, Community Edition, or ambiguous), which entry-point class answered, which capabilities bound, which did not, and the mode |
| `/mcaquests compat iceandfire probe` | Checks each capability by asking the current world's registries — "bound" and "present in the loaded world" are not the same thing, so this is the right check once a world is loaded |

---

## What happens when things aren't installed, or come and go

| Situation | Result |
|---|---|
| Ice & Fire absent | Types register, packs parse, `compat_capability` for any Ice & Fire id is false, no bundled content is offered, no Ice & Fire class is loaded. Nothing is logged — this is the normal state |
| Ice & Fire present, all capabilities bound | Everything in this document works |
| Ice & Fire present, some capabilities missing | Depends on which: if a structure is missing at load, it is reported missing; if a mob is present later, it is picked up at the next probe or `/reload`. No errors are logged when a quest simply does not appear — every bundled definition gates on what it needs |
| Ice & Fire removed from an existing world | Active quests suspend as described above. The world loads normally |
| Ice & Fire restored | Suspended quests resume. Nothing is duplicated and nothing is re-announced |
| `enabled = false` | As "absent", except one INFO at startup saying so |

---

## FAQ

**Do I need Ice & Fire?** No. Without it MCA: Quests behaves exactly as it did in 1.5.3.

**Will my existing quests break?** No. Every new field is optional and every new type is additive. A pack written for 1.5.3 loads unchanged.

**Can I write Ice & Fire quests without Ice & Fire installed?** Yes — the types register regardless, so your pack parses and validates. It just will not be offered until Ice & Fire is there.

**Why does my Ice & Fire quest say "On hold"?** Ice & Fire is not installed, or a specific capability that quest needs did not bind. Run `/mcaquests compat iceandfire status`.

**Which build is right for me?** Both work; the original predates Community Edition and is maintained by Alex's Mobs' author, while Community Edition is community-maintained and has dropped the Myrmex and added Dragon Seekers. Either works with this integration — the bundled content uses the Dread army and dragon hunts that both have.

---

See [DATAPACK.md](DATAPACK.md) for the rest of MCA: Quests' own datapack format, and [CONFIG.md](CONFIG.md#compaticeandfire) for every `[compat.iceandfire]` config option and its clamp range.
