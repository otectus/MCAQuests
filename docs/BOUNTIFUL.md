# MCA: Quests × Bountiful

**[Bountiful](https://www.curseforge.com/minecraft/mc-mods/bountiful)** manages a contract board where players can pick up jobs — hunt a mob, harvest resources, or catch fish — and turn them in for reward. This integration makes the board something a quest can link to: discover it, take contracts from it, and complete them to advance.

MCA: Quests does not compile against Bountiful and reaches it through reflection only. Three integration modes exist and exactly one is in use; which one is chosen depends on whether Bountiful is installed and how deep the integration can reach:

- **AUTO** (default): use the completion hook when Bountiful's `tryCashIn` method is available with the expected shape; degrade to data-only when it is not (Bountiful too old, or renamed internals)
- **DATA_ONLY**: mount our bounty pools and read rarity, but never observe a cash-in (bounty-completion objectives don't appear, but board discovery and rarity-gated quests do)
- **OFF**: as if Bountiful were not installed

Everything here is **optional in both directions**. Without Bountiful the types still register, so your datapacks parse identically, the bundled content simply never becomes eligible, and nothing else about MCA: Quests changes.

---

## Install

1. Install **MCA Reborn**, **MCA: Quests**, and **Bountiful** (any recent version). Bountiful has its own dependencies (Kambrik and Kotlin for Forge); ensure they are present too.
2. Start the server. That is all — no configuration is needed unless you want to change the mode or disable built-in content.

Confirm it took with `/mcaquests compat bountiful status`. You want to see status FULL or PARTIAL, effective mode HOOKED or DATA_ONLY, and at least the `BOARD_REGISTRY` and `DATA_PACK` capabilities marked ok.

---

## How it works

Bountiful has a public API for everything except bounty completion. MCA: Quests reads bounty properties and mounts conditional pools and decrees through the normal datapack loader. Completion is the one thing the API does not expose: when a player cashes in a bounty at the board, nothing tells MCA: Quests about it — so the integration includes an optional Mixin observer (`BountyStackCashInMixin`, a single method hook on Bountiful's `BountyStack.tryCashIn`) that is applied only when Bountiful is installed and the method has the expected shape.

That hook is applied once per game start via a config plugin that checks Bountiful's bytes beforehand; if anything is wrong — Bountiful is absent, an internal rename has changed the method signature, or something else has broken it — the integration degrades to data-only with a diagnostic. Nothing is silently broken.

| Capability | What it unlocks |
|---|---|
| `bountiful.data_pack` | Our conditional bounty pools and decrees can be mounted (nearly always true) |
| `bountiful.board_registry` | `bountiful:bountyboard` is registered, so a board discovery objective can complete |
| `bountiful.cash_in_hook` | A successful bounty cash-in is observable, so bounty-completion objectives can advance |
| `bountiful.read_rarity` | A bounty's rarity can be read, so `min_rarity` on an objective means something (requires hook) |
| `bountiful.read_objectives` | A bounty's objective list can be read (requires hook) |

---

## Gating content — `mcaquests:compat_capability`

Open every Bountiful definition with this. It is true only when the capability is available.

```json
{
  "type": "mcaquests:compat_capability",
  "provider": "bountiful",
  "capability": "bountiful.cash_in_hook"
}
```

Capability names are case-insensitive, and a name that is not a real capability **fails the datapack reload** rather than silently gating on nothing.

---

## Objectives

### `mcaquests:bountiful_bounties` — cash in a number of bounties

| Field | Type | Default | Meaning |
|---|---|---|---|
| `count` | int | 1 | How many bounties must be cashed in |
| `min_rarity` | string | (none) | Only bounties of this rarity or higher count (Bountiful's names: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY) |
| `source` | object | (none) | Optional hint for the quest tracker — where bounties are found |

This objective is unavailable — and the quest is hidden from offers — when:

- The mod is absent or the integration is off
- `min_rarity` is set but the rarity reader is not available (requires the hook)
- The cash-in hook is not available (no hook, mode is DATA_ONLY, or disabled)

When unavailable, the reason is one of:

- `mcaquests.objective.unavailable.bountiful_hook` — the cash-in observer is not available
- `mcaquests.objective.unavailable.bountiful_rarity` — rarity cannot be read

Naming a minimum as unavailable rather than quietly ignoring it is deliberate: a quest that can never actually complete due to an unmet requirement should hide rather than sit at zero forever.

---

## What happens when Bountiful is removed

This is the case worth understanding before you build a pack around it.

A quest you accepted while Bountiful was installed **does not fail**. It:

- keeps its progress, exactly as it was;
- stops polling, so nothing advances and nothing regresses;
- never reads as complete, so it cannot be turned in;
- shows an amber **"On hold — waiting on a mod that is not installed"** line in the quest log;
- stays abandonable, from both the log and the villager menu;
- and picks up exactly where it left off if Bountiful returns.

Suspension is decided fresh every pass rather than written into the save, so recovery needs no migration and nothing can go stale. Offers simply stop appearing, because every bundled definition gates on `bountiful.cash_in_hook` or another capability that is absent when the mod is gone.

---

## Commands

All under `/mcaquests compat bountiful`:

| Command | Permission | What it reports |
|---|---|---|
| `bountiful status` | 2 | Whether Bountiful is installed, which capabilities are present (board, data-pack, rarity), the effective mode in use (HOOKED/DATA_ONLY/NOOP), and whether the hook was successfully applied |
| `bountiful probe` | 2 | Re-runs the capability check (hook presence, board registry, etc.) without restarting. Use this after changing `compat.bountiful.mode` to see the new mode take effect |

---

## Built-in content

The mod ships two conditional bounty packs, both optional:

### `bountiful_core` — board discovery and contracts

Three quests:

- **board_discovery** (librarian/cartographer) — find and interact with a bounty board; gated on `bountiful.board_registry`
- **contractor** (weaponsmith, toolsmith) — complete two bounties from the board; gated on `bountiful.cash_in_hook`
- **specialist** (weaponsmith) — complete one high-rarity bounty; gated on both `bountiful.cash_in_hook` and `bountiful.read_rarity`

The spec requires the board to exist and the completions to be observable.

### `bountiful_iafce` — pooled contracts with Ice & Fire themes (when Ice & Fire is also installed)

Two bounty pools and one decree:

**Bounty pools:**

- `mcaquests_iafce_hunts` — hunt dragon-type mobs for rare bounties:
  - `mcaquests_iafce_fire_dragon`: 1 fire dragon, 12,000 worth, RARE
  - `mcaquests_iafce_ice_dragon`: 1 ice dragon, 12,000 worth, RARE
  - `mcaquests_iafce_lightning_dragon`: 1 lightning dragon, 14,000 worth, RARE

- `mcaquests_iafce_materials` — harvest from dragons for uncommon/rare bounties:
  - `mcaquests_iafce_fire_dragon_blood`: 1-2 fire dragon blood, 4,000 worth, RARE
  - `mcaquests_iafce_ice_dragon_blood`: 1-2 ice dragon blood, 4,000 worth, RARE
  - `mcaquests_iafce_lightning_dragon_blood`: 1-2 lightning dragon blood, 4,500 worth, RARE
  - `mcaquests_iafce_dragonscales_red`: 4-12 red dragon scales, 900 worth, UNCOMMON

**Bounty decree:**

- `mcaquests_dragons` — a predefined contract collection that draws objectives from the hunts pool and rewards from the materials pool

The `unitWorth` values are the release design specification's defaults and have not yet been tuned against real Bountiful pool statistics. Pack authors may override these values by editing the pool file. The decree currently has to be obtained or placed by a server admin or through Bountiful's own mechanisms (crafting, command, loot table) — this release does not add it to any board or loot table.

Bountiful resolves pools and decrees by bare file name, so the shipped names carry the `mcaquests_` prefix to avoid collisions.

---

## What happens when things aren't installed, or come and go

| Situation | Result |
|---|---|
| Bountiful absent | Types register, packs parse, `compat_capability` for any Bountiful id is false, no bundled content is offered, no Bountiful class is loaded. Nothing is logged — this is the normal state |
| Bountiful present, mode AUTO, hook applied | Everything in this document works |
| Bountiful present, mode AUTO, hook unavailable | Degrades to DATA_ONLY; one WARN in the log explaining what happened and how to check (`/mcaquests compat bountiful status`). Board discovery works, but bounty-completion objectives don't appear |
| Bountiful present, mode DATA_ONLY | The integration works but never observes a cash-in; bounty-completion objectives are ineligible, but board discovery and rarity-gated quests (if any) still work |
| Bountiful present, mode OFF | As "absent", except one INFO at startup saying the integration is disabled |
| Bountiful removed from an existing world | Active quests suspend as described above. The world loads normally |
| Bountiful restored | Suspended quests resume. Nothing is duplicated and nothing is re-announced |

---

## FAQ

**Do I need Bountiful?** No. Without it MCA: Quests behaves exactly as it did in 1.5.3.

**Will my existing quests break?** No. Every new field is optional and every new type is additive. A pack written for 1.5.3 loads unchanged.

**Can I write Bountiful quests without Bountiful installed?** Yes — the types register regardless, so your pack parses and validates. It just will not be offered until Bountiful is there.

**Why don't my bounty-completion quests appear?** One of the reasons above. Run `/mcaquests compat bountiful status` to see which mode is in use and which capabilities bound. If the mode is DATA_ONLY or OFF, and you want the full integration, try `/mcaquests compat bountiful probe` to re-check (and then restart if nothing changed). If the mode is AUTO but the hook did not apply, check the startup log for a message naming the problem.

**Why can't I find the decree in a board?** The decree is not wired into any board or loot table by MCA: Quests, so a server admin must provide it through Bountiful's own means (its commands or a datapack). See Bountiful's documentation for the exact method.

**Does the client need Bountiful?** The mixin is applied wherever Bountiful's `BountyStack` class loads (both sides when Bountiful is installed), and the handler only acts for server-side players. A client without Bountiful sees no behaviour change; a client with Bountiful sees no change either because bounty-completion is a server-side observable.

---

See [DATAPACK.md](DATAPACK.md) for the rest of MCA: Quests' own datapack format, and [CONFIG.md](CONFIG.md#compatbountiful) for every `[compat.bountiful]` config option and its clamp range.
