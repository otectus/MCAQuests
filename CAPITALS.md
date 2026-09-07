# MCA: Quests × MCA Capitals

**[MCA Capitals](https://www.curseforge.com/minecraft/mc-mods/mca-capitals)** makes one MCA villager the sovereign of a capital, assigns offices in the hierarchy below them, manages diplomatic relations between capitals, and handles succession when a throne becomes vacant. This integration makes courts a subject for quests: escort the sovereign, petition the Hand, raise a villager to nobility, or keep the realm in order when the crown changes hands.

MCA: Quests does not compile against Capitals and reaches it through reflection only. Everything it learns about a capital is reported as **capabilities** rather than as a single yes-or-no. Missing or failing methods disable the features that depend on them. Quests requiring an unavailable Capitals capability are withheld from offers, and accepted quests pause until it returns, including quests that need Capitals only for a reward.

Everything here is **optional in both directions**. Without Capitals the types still register, so your datapacks parse identically, the bundled content simply never becomes eligible, and nothing else about MCA: Quests changes.

---

## Install

1. Install **MCA Reborn**, **MCA: Quests**, and **MCA Capitals** (1.3.5+, which itself requires MCA Reborn 7.7.1-alpha.2+). Capitals has its own dependencies; ensure they are present too.
2. Start the server. That is all — no configuration is needed unless you want to disable built-in content or adjust the poller interval.

Confirm it took with `/mcaquests compat capitals status`. You want to see status FULL or PARTIAL, and at least the `capitals.registry` capability marked ok.

---

## How it works

MCA: Quests binds Capitals methods by name and arity at runtime, without linking its classes. Role holders are looked up on demand; the poller samples diplomacy and succession state at intervals. A change that remains observable is normally detected within `pollIntervalTicks`; a vacancy that begins and ends between polls can be missed. The first successful sample establishes a baseline rather than announcing every existing vacancy or war. Failed samples preserve that baseline, and distinct deceased sovereigns can identify successive vacancies even without an observed occupied interval.

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

Capitals capability names are case-insensitive. An unknown provider or capability is treated as unavailable, not as a parse error; this allows add-ons to register their own providers. Use the exact provider id `mcacapitals` and check spelling against the table above.

---

## What happens when Capitals is removed

This is the case worth understanding before you build a pack around it.

A quest you accepted while Capitals was installed **does not fail**. It:

- keeps its progress, exactly as it was;
- stops polling, so nothing advances and nothing regresses;
- never reads as complete, so it cannot be turned in;
- shows a **"Quest paused"** line naming MCA Capitals in the quest log;
- stays abandonable, from both the log and the villager menu;
- and picks up exactly where it left off if Capitals returns.

Availability is checked again each pass, so capabilities recover after a successful probe without discarding progress. Disabling the integration has the same effect. Turning off only `enableBuiltinContent` hides new bundled offers; accepted quests can continue while their definitions and required capabilities remain available. Reloading without the pack pauses its existing quests.

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
5. **coronation_gift** — present a golden helmet to the sovereign while the throne is occupied; rewards a chronicle entry (this quest is not tied to a coronation event)
6. **the_chroniclers_request** — bring a writable book to the herald; rewards hearts and a chronicle entry
7. **envoy_of_the_alliance** — deliver letters to the giver's capital's ambassador while that capital has an alliance; rewards hearts and village reputation
8. **a_lords_due** — give emeralds to a villager seeking a lordship; rewards that villager the lord title and the player hearts

**Situations:**

- **the_empty_throne** — triggered when a capital's throne becomes vacant; deliver papers to the Hand; rewards currency, XP, hearts and village reputation
- **drums_of_war** — triggered when a capital declares war on another; defend the village center; rewards currency, XP, hearts and village reputation

To control whether the built-in content appears, see `[compat.capitals]` in the config.

---

## How role gating works

Every quest that names an office asks for a **condition**, not a config switch. A capital can have many quests, and which ones are offered to a player depends on which offices are held and which the quest author wrote. The `capital_role` condition names the office and checks both villagers and the player.

`capital_role subject=giver role=sovereign` requires the giver themselves to hold the sovereign office. Living in the capital does not make every villager an officeholder. With `subject=player`, the condition checks the player and the giver remains an ordinary MCA villager. A player-held throne is separate from the villager hierarchy.

An objective with a `capital_role` villager target requires a villager officeholder. A player-only or vacant office cannot supply one, so that offer is withheld. Once accepted, the target UUID stays bound to that villager through later appointments and chunk unloading; it does not switch to a successor. If several villagers hold a role, selection is deterministic.

Capital rewards use the original giver's village and identity saved at acceptance, including when turning in elsewhere or while the giver is unloaded. Chronicle rewards render the bundled English text on dedicated servers. Custom datapacks can supply `fallback` text with `%s` placeholders for the player and capital when their translation key is unavailable on the server.

**Appointing sovereigns and changing allegiances is deliberately out of scope.** Those are Capitals' own ceremonies and are meant for Capitals' own commands and game mechanics to run. MCA: Quests observes and reacts to them (via situations and conditions) but never initiates them.

---

## Configuration

See `[compat.capitals]` in [CONFIG.md](CONFIG.md#compatcapitals) for every `compat.capitals.*` option: `enabled`, `enableBuiltinContent`, and `pollIntervalTicks` (ceiling on how long an interregnum or war can go unnoticed).

---

## Development

To run the game with Capitals installed and the built-in content packed:

```bash
./gradlew runClient -PmcaDevVersion=7.7.1-alpha.2+1.20.1 -PenableCapitalsInDev=true
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

**What offices can I gate on?** Sovereign, consort, dowager, heir, royal_child, hand, commander, herald, grand_maester, master_of_laws, ambassador, duke, lord, knight, royal_guard, and member (court membership). Archduke is available as a player title reward, but is not a `capital_role` selector.

**Why don't my throne quests appear?** Run `/mcaquests compat capitals status` to see which capabilities bound. If it is ABSENT or DISABLED, check that Capitals is installed and the `compat.capitals.enabled` config is `true`. If a capability is marked unavailable, the quest is gated on something that is not present (e.g., a missing optional MCA feature).

**Can a player become a quest giver if they hold the sovereign office?** No. Quest givers are always villagers. Villagers can offer quests gated on the player holding an office with `capital_role subject=player`.

**Why do the court quests never appear?** Check that the pack is enabled, the giver's profession is eligible, the giver belongs to an active capital, and any target office has a villager holder. Individual quests add further conditions. Use `/mcaquests debug quest <quest-id>` for offer filtering and `/mcaquests compat capitals status` for capability diagnostics.

---

See [DATAPACK.md](DATAPACK.md) for conditions, rewards, villager targeting, and situation triggers, and [CONFIG.md](CONFIG.md#compatcapitals) for every config option and its clamp range.
