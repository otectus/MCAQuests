# MCA: Quests × MCA Capitals

**[MCA Capitals](https://www.curseforge.com/minecraft/mc-mods/mca-capitals)** makes one MCA villager the sovereign of a capital, assigns offices in the hierarchy below them, manages diplomatic relations between capitals, and handles succession when a throne becomes vacant. This integration makes courts a subject for quests: escort the sovereign, petition the Hand, raise a villager to nobility, or keep the realm in order when the crown changes hands.

MCA: Quests does not compile against Capitals and reaches it through reflection only. Everything it learns about a capital is reported as **capabilities** rather than as a single yes-or-no, so one method rename in a Capitals point release disables exactly the feature that read it — the title rewards stop being granted, and the villager offices simply go unmatched rather than breaking any quest.

Everything here is **optional in both directions**. Without Capitals the types still register, so your datapacks parse identically, the bundled content simply never becomes eligible, and nothing else about MCA: Quests changes.

---

## Install

1. Install **MCA Reborn**, **MCA: Quests**, and **MCA Capitals** (1.3.5+, which itself requires MCA Reborn 7.7.35-beta.3+). Capitals has its own dependencies; ensure they are present too.
2. Start the server. That is all — no configuration is needed unless you want to disable built-in content or adjust the poller interval.

Confirm it took with `/mcaquests compat capitals status`. You want to see status FULL or PARTIAL, and at least the `capitals.registry` capability marked ok.

---

## How it works

Capitals has a public API for reading capital state, roles and diplomacy. MCA: Quests binds those methods by name and arity at runtime (never by parameter type), so nothing in this codebase is ever linked to Capitals and the integration can degrade gracefully if an update shifts a method. Role holders are looked up on demand; the poller samples diplomacy and succession state at intervals, so a war or interregnum are subject to the `pollIntervalTicks` ceiling before a situation can fire.

| Capability | What it unlocks |
|---|---|
| `capitals.registry` | Capitals exist: the record registry, village-to-capital lookup, display name, and state |
| `capitals.roles` | Who holds which villager office, from the sovereign down to a knight |
| `capitals.player_titles` | What a *player* holds: a throne, a consort seat, an office, a granted noble title |
| `capitals.title_grants` | Granting a player a noble title (the only player-side mutation this mod performs) |
| `capitals.allegiance` | The capital a player has declared allegiance to |
| `capitals.diplomacy` | The diplomatic state between two capitals |
| `capitals.interregnum` | Whether a capital's throne is currently vacant, and who vacated it |
| `capitals.chronicle` | Writing a line into a capital's chronicle, with or without the herald announcing it |
| `capitals.villager_titles` | Raising a villager to knight, lord or duke of a capital |

---

## Gating content — `mcaquests:compat_capability`

Open every Capitals definition with this. It is true only when the capability is available.

```json
{
  "type": "mcaquests:compat_capability",
  "provider": "mcacapitals",
  "capability": "capitals.registry"
}
```

Capability names are case-insensitive, and a name that is not a real capability **fails the datapack reload** rather than silently gating on nothing.

---

## What happens when Capitals is removed

This is the case worth understanding before you build a pack around it.

A quest you accepted while Capitals was installed **does not fail**. It:

- keeps its progress, exactly as it was;
- stops polling, so nothing advances and nothing regresses;
- never reads as complete, so it cannot be turned in;
- shows an amber **"On hold — waiting on a mod that is not installed"** line in the quest log;
- stays abandonable, from both the log and the villager menu;
- and picks up exactly where it left off if Capitals returns.

Suspension is decided fresh every pass rather than written into the save, so recovery needs no migration and nothing can go stale. Offers simply stop appearing, because every bundled definition gates on a Capitals capability that is absent when the mod is gone.

---

## Commands

All under `/mcaquests compat capitals`:

| Command | Permission | What it reports |
|---|---|---|
| `capitals status` | 2 | Whether Capitals is installed, which capabilities are present, and which binding members are unresolved |
| `capitals probe` | 2 | Re-runs the capability check without restarting. Use this after changing `compat.capitals.enabled` to see the new state take effect |

---

## Built-in content

The mod ships one conditional quest pack, optional:

### `capitals_court` — court duties and succession

Eight quests and two situations, all gated on `capitals.registry` and the `enableBuiltinContent` setting:

**Quests:**

1. **royal_escort** — escort the sovereign home safely; rewards hearts, village reputation, and a chronicle entry
2. **petition_to_the_crown** — deliver petitions to the Hand; rewards XP and hearts
3. **the_accolade** — clear hostiles from the roads and present an iron sword to the sovereign to earn a knighthood; rewards the player the knight title and a chronicle entry
4. **guard_the_heir** — stand near the heir for three minutes; rewards hearts and XP
5. **coronation_gift** — present a golden helmet to the sovereign on their coronation; rewards a chronicle entry
6. **the_chroniclers_request** — bring a writable book to the herald; rewards hearts and a chronicle entry
7. **envoy_of_the_alliance** — deliver letters to an allied capital's ambassador; rewards hearts and village reputation
8. **a_lords_due** — give emeralds to a villager seeking a lordship; rewards that villager the lord title and the player hearts

**Situations:**

- **the_empty_throne** — triggered when a capital's throne becomes vacant; offer duties to the Hand (role-gated); rewards currency, XP, hearts and village reputation
- **drums_of_war** — triggered when a capital declares war on another; defend the village center; rewards currency, XP, hearts and village reputation

To control whether the built-in content appears, see `[compat.capitals]` in the config.

---

## How role gating works

Every quest that names an office asks for a **condition**, not a config switch. A capital can have many quests, and which ones are offered to a player depends on which offices are held and which the quest author wrote. The `capital_role` condition names the office and checks both villagers and the player.

A giver villager is automatically part of their own capital, so `capital_role subject=giver role=sovereign` will never be offered by a villager from a non-sovereign capital. A player-held throne is separate from the villager hierarchy, so the same condition on the player will offer the quest only when you hold that office.

If no villager holds an office (the hand has been dismissed, for example), or if the office is held only by a player and the giver is not that player, the quest is not offered. The condition does not block the offer with an "unavailable" message — it makes the condition false, so the quest simply does not appear.

**Appointing sovereigns and changing allegiances is deliberately out of scope.** Those are Capitals' own ceremonies and are meant for Capitals' own commands and game mechanics to run. MCA: Quests observes and reacts to them (via situations and conditions) but never initiates them.

---

## Configuration

See `[compat.capitals]` in [CONFIG.md](CONFIG.md#compatcapitals) for every `compat.capitals.*` option: `enabled`, `enableBuiltinContent`, and `pollIntervalTicks` (ceiling on how long an interregnum or war can go unnoticed).

---

## Development

To run the game with Capitals installed and the built-in content packed:

```bash
./gradlew runClient -PenableCapitalsInDev=true
```

To verify the binding against a real Capitals jar:

```bash
./gradlew capitalsProbeTest -PcapitalsJar=/path/to/capitals-*.jar
```

---

## FAQ

**Do I need Capitals?** No. Without it MCA: Quests behaves exactly as it does without the integration.

**Will my existing quests break?** No. Every new field is optional and every new type is additive. An existing quest pack loads unchanged.

**Can I write Capitals quests without Capitals installed?** Yes — the types register regardless, so your pack parses and validates. It just will not be offered until Capitals is there.

**What offices can I gate on?** The roles that apply to villagers: sovereign, consort, dowager, heir, royal_child, hand, commander, herald, grand maester, master of laws, ambassador, duke, lord, knight, royal guard, and member (anyone in the court). Archduke is player-only and never a quest requirement.

**Why don't my throne quests appear?** Run `/mcaquests compat capitals status` to see which capabilities bound. If it is ABSENT or DISABLED, check that Capitals is installed and the `compat.capitals.enabled` config is `true`. If a capability is marked unavailable, the quest is gated on something that is not present (e.g., a missing optional MCA feature).

**Can a player become a quest giver if they hold the sovereign office?** No. A player throne exists separately from the villager hierarchy, and quest givers are always villagers. A quest gated on the player holding an office will not be offered by anyone.

**Why do the court quests never appear?** Either Capitals is not installed, or one of the conditions it gates on is false. Most quests require an active capital and an active interregnum/peace for one of the conditions. Run `/mcaquests debug` to see the current capital's state, and `/mcaquests compat capitals status` to see what capabilities bound.

---

See [DATAPACK.md](DATAPACK.md) for conditions, rewards, villager targeting, and situation triggers, and [CONFIG.md](CONFIG.md#compatcapitals) for every config option and its clamp range.
