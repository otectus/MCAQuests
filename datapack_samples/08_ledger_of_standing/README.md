# 08 — The Ledger of Standing (reputation, tiers, titles and incidents)

A merchant guild that keeps a written record of you. This sample covers the mod's own **village
reputation** and the progression built on top of it — **tier ladders** and **titles** — and then the
optional **MCA: Reputation** layer, where deeds are individual events with a status you can resolve.

Three separate things are called "reputation" in this ecosystem. Keeping them apart is most of the job:

| | What it is | Where it lives |
|---|---|---|
| **MCA hearts** | How one villager feels about you personally | MCA Reborn; `mcaquests:hearts` reward, `mcaquests:hearts` condition |
| **Village reputation** | A number per village, owned by this mod | `mcaquests:village_reputation` reward/condition, tiers, titles |
| **Incidents** | Individual recorded deeds with a status and a visibility | The optional MCA: Reputation mod |

## Files

| File | Shows |
|---|---|
| `reputation_tiers/guild.json` | A **custom ladder**, `ledger:guild`, with `grants_title` on three of its tiers. |
| `titles/*.json` | Three title definitions, one `global` and two `village`. |
| `quests/guild/the_first_ledger_line.json` | Earning reputation and a title from an ordinary quest. |
| `quests/guild/writ_of_trust.json` | Gating on `reputation_tier` **on a named ladder** plus a raw `village_reputation` floor. |
| `quests/guild/making_it_right.json` | The quest-level `reputation` block, `has_incident`, `resolve_incident`, `record_incident`. |
| `quests/guild/a_good_word.json` | `villager_opinion` — what the giver personally thinks, as opposed to what the village recorded. |

## Village reputation

Every village carries an independent, mod-side reputation value. Raise it with a reward:

```json
{ "type": "mcaquests:village_reputation", "amount": 25 }
```

and read it in any condition tree — quest **or** project:

```json
{ "type": "mcaquests:village_reputation", "min": 60 }
```

Projects move it with their `reputation` block (sample 06) and situations with their `outcomes` (sample
07). Test it with `/mcaquests reputation get|set|add|tiers`.

## Tier ladders

```
data/<namespace>/mcaquests/reputation_tiers/<name>.json
```

- `threshold` is the **inclusive minimum** for the tier.
- Tiers must **strictly ascend**, and the lowest threshold must be `<= 0` so every value maps to a tier.
  An invalid ladder is skipped entirely, so check `/mcaquests validate` after editing one.
- `grants_title` auto-awards a village-scoped title the first time a village reaches that tier — which
  is why `the_first_ledger_line` pays 25 reputation *and* grants the apprentice title: one is the
  ladder's own doing, the other is the quest being explicit. Either alone is fine; both is belt and
  braces on a first-run experience.

The id **`mcaquests:default`** is special: it is the ladder the Journal UI shows and the one any
`reputation_tier` condition uses when it omits `ladder`. This pack deliberately does **not** touch it —
it ships `ledger:guild` alongside and names it explicitly:

```json
{ "type": "mcaquests:reputation_tier", "min_tier": "sworn", "ladder": "ledger:guild" }
```

Shipping your own `default.json` to *replace* the built-in ladder is a different, heavier decision —
sample 09 does exactly that and explains what it changes.

Gated by `enableReputationTiers` (on by default). With it off, ladders and titles stop loading entirely.

## Titles

A title is just a resource location; awarding one works even with no definition (the id is displayed).
A definition supplies the display name and scope:

```json
{ "name": "Hand of the Guild", "scope": "village" }
```

`name` is a plain string, not a text object — it is not translatable through a lang key today, so write
it in the language your pack is written in.

```json
{ "type": "mcaquests:grant_title", "title": "ledger:hand_of_the_guild", "scope": "global" }
```

`scope` on the **reward** decides where it is attached: `village` (the giver's village; a no-op if no
village resolves) or `global`. Players see reputation, tiers and titles in the **Journal**. Test with
`/mcaquests title grant|list|clear`. `/mcaquests validate` warns about a `grant_title` naming a title
nothing defines and a `reputation_tier` naming a tier its ladder does not have.

## The quest-level `reputation` block

Beyond the simple `village_reputation` reward, a quest can declare what kind of *story* its outcome is:

```json
"reputation": {
  "complete": { "delta": 10, "incident": "mcareputation:quest_completed",
                "visibility": "village", "tags": ["guild"], "recipients": "resolving_player" },
  "fail":     { "delta": -4, "visibility": "witnessed", "recipients": "resolving_player" },
  "abandon":  { "delta": -2, "visibility": "witnessed", "recipients": "resolving_player" }
}
```

- Each outcome accepts the object above **or a bare integer** (`"complete": 10`), which is what every
  older pack effectively wrote.
- **Failure and abandonment default to nothing.** That is a content decision, not an oversight:
  abandoning has always been free from the villager menu, and quietly attaching a penalty would change
  the behaviour of every existing pack. If you want a cost, write it — `making_it_right` does.
- `recipients` is `nobody`, `resolving_player`, `phase_contributors`, `all_participants` or
  `accepted_participants` (the last two matter for projects and situations).
- **Do not use this block *and* a `village_reputation` reward on the same quest.** When both are present
  the block wins and the reward becomes display-only; validation warns about the ambiguity. Note that
  `making_it_right` therefore has no `village_reputation` reward, while the other three quests here have
  no `reputation` block.
- `visibility` and `incident` are MCA: Reputation's vocabulary. Without that mod they are inert strings
  and the delta behaves like the plain reward.

## MCA: Reputation (optional)

Two conditions and two rewards are registered **whether or not** MCA: Reputation is installed, so a pack
using them parses and validates identically either way. Without the mod, the conditions never match and
the rewards are silent no-ops — `making_it_right` and `a_good_word` simply never appear.

**`mcareputation:has_incident`** — a deed on the player's record with the giver's *village*:

```json
{ "type": "mcareputation:has_incident",
  "incident": "mcareputation:villager_assaulted",
  "status": ["active", "apologized"],
  "known_to_giver": true }
```

`status` values are `active`, `apologized`, `atoned`, `forgiven`, `disproven`, `expired`.
`known_to_giver` limits it to incidents this villager actually knows about, and `negate` inverts the
whole selector.

**`mcareputation:villager_opinion`** — what the giver *personally* thinks, which is not the same thing:

```json
{ "type": "mcareputation:villager_opinion", "min_tier": "friend", "basis": ["involved", "witnessed"] }
```

`basis` is how they came by the opinion: `involved`, `witnessed`, `hearsay`, `none`. Somebody who was
there is less swayed by village gossip than somebody who heard about it — which is the entire premise of
`a_good_word`, where the giver says outright that they will not vouch on hearsay.

> The tiers this condition names (`stranger`, `acquaintance`, `friend`, `honored`, `revered`) are the
> **default Quests ladder**, not your custom one. `a_good_word` therefore uses `villager_opinion` with
> `friend` and a separate `reputation_tier` with `trusted` on `ledger:guild`; they are two different
> scales and mixing up their vocabularies is the easy mistake here.

**`mcareputation:resolve_incident`** marks a past deed apologised for, atoned for, forgiven or disproven
— the payoff of a restitution quest. It reduces the standing penalty of the thing you did without
erasing the record that you did it. Two safety properties are worth knowing:

- **The selector must narrow something.** A reward naming no incident, status or tag is refused with a
  warning rather than picking one arbitrarily — "atone for the assault" and "atone for whatever" are not
  the same offer.
- **Resolving is idempotent.** The backend refuses a status that is not strictly stronger than the
  current one, so a repeatable restitution quest cannot ratchet the same incident through the same
  reduction twice.

**`mcareputation:record_incident`** writes a *new* deed, for the case the top-level block cannot express:
one quest producing two ledger lines. `making_it_right` does both — it resolves the assault and records
the restitution, because those are two facts.
