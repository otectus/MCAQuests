# Claude Code Task: MCA-Aware Quest Conditions for MCA: Quests

## Objective

Extend **MCA: Quests** so quest availability can depend on MCA Reborn villager state, relationships, family structure, and life-sim context — implemented as new **optional, datapack-driven condition types**, never as hardcoded quest logic.

Preserve the existing datapack-driven condition architecture. Every new condition must:

- be usable standalone, and
- compose inside the existing `all_of`, `any_of`, and `not` composites, and
- combine freely with existing gates: **hearts, profession, biome, time, weather, advancement, level, random chance, quest history**.

---

## Non-Negotiables (read before writing any code)

- **MCA isolation.** Every MCA Reborn import and internal access lives *only* inside `McaCompat`. Condition classes call `McaCompat`; they never import MCA internals. Zero exceptions, no scattered imports.
- **Fail safe by default.** Any condition whose backing MCA data is missing, unstable, or throws must evaluate to a safe result (default: *condition not met*) and emit debug logging. It must never crash the server — **unless strict validation is explicitly enabled**.
- **Server-authoritative.** All evaluation happens server-side. No client trust.
- **Datapack-driven.** No hardcoded quest logic. Conditions are data; behavior is configured in JSON.
- **Additive / non-destructive.** Do not alter or regress existing condition behavior. New types are purely additive.
- **Mirror, don't invent (syntax).** New condition JSON must match the *existing* condition syntax exactly. The source of truth is the current codebase, not imagination — you extract and document the canonical syntax in Phase 0 *before* designing anything new.
- **No per-tick graph polling.** Relationship / family / village lookups must not run every server tick. The evaluation strategy is decided in Phase 1 from Phase 0 evidence (see Performance).
- **Honesty rule for "supported."** A condition is documented as *supported* only if (a) Phase 0 confirms its MCA data is reliably available, **and** (b) it ships with working validation and a demonstrated safe-fail path. Otherwise mark it *experimental* or omit it. Nothing is labeled supported on faith.

---

## Evidence-Gated Phases

Work strictly in order. **Each phase ends in a hard STOP.** Do not begin the next phase until I approve the deliverable. Every claim about the codebase or the MCA API must cite real evidence (file path + symbol/line, or an actual API signature you located) — not assumptions.

### Phase 0 — Discovery (STOP)

Produce a written findings document. **Do not write feature code in this phase.**

1. **Condition architecture map.** How conditions are registered, parsed, validated, and *evaluated* — and critically, **when and how often** evaluation runs (quest-board refresh? quest UI open? on server tick? on an event?). Cite the exact call sites.
2. **Canonical syntax reference.** Extract the real JSON shape of 2–3 existing conditions verbatim and distill the house style: key naming, type tags, how composites nest. This becomes the template Phase 1 mirrors.
3. **`McaCompat` inventory.** What the layer already exposes, which MCA internals it touches today, and the seam where new methods attach.
4. **MCA data probe + availability matrix.** For each target data point below, locate the actual MCA Reborn API and classify it as **Reliable / Fragile / Unavailable**, citing the class/method evidence for each:
   - married-to-player / is-player's-spouse *(confirm whether these are one concept or two)*
   - is-family-member
   - age group: child / adult / elder
   - personality / mood
   - village membership / home presence
   - health (threshold)
   - infection / zombification state
   - relationship to another (named) villager
   - another related villager's status: alive / missing / dead / nearby / same-village
5. **Performance path.** Given (1), identify the hot path and state the cost of querying MCA state during a full eligibility pass.
6. **Go/no-go matrix.** Which proposed conditions are implementable now (Reliable), which are experimental (Fragile), which are deferred (Unavailable).

**Deliverable:** findings doc containing the syntax reference, availability matrix, and go/no-go matrix. **STOP.**

### Phase 1 — Design (STOP)

1. **JSON schema per viable condition**, mirroring the Phase 0 syntax reference exactly. (Illustrative shape appears below — *confirm or replace against the real syntax*.)
2. **`McaCompat` method contracts** — one method per data point, each specifying: signature, return type, the safe default it returns when data is unavailable, and what it logs.
3. **Evaluation & caching strategy**, decided from Phase 0 evidence:
   - **Default lean:** evaluate MCA conditions only at eligibility-check time (not per tick), and **memoize per evaluation pass** so a villager's state is fetched once per pass, not once per condition.
   - Escalate to a persistent / event-invalidated cache **only** if Phase 0 shows a hot path that requires it — and justify the tradeoff explicitly if you do.
4. **Validation rules** per field: valid relationship targets, allowed age groups, known personality values, villager-reference format. Define the **strict-vs-lenient** behavior toggle.

**Deliverable:** design doc (schemas, compat contracts, eval strategy, validation rules). **STOP.**

### Phase 2 — McaCompat (STOP)

Implement the compat methods only — the isolation boundary, **no condition logic yet**. Each method must fail safe and log when MCA data is absent. Demonstrate (test or harness) that every method returns its safe default when the backing data is missing.

**Deliverable:** compat methods + safe-fail demonstration. **STOP.**

### Phase 3 — Conditions + Validation (STOP)

Implement each viable condition type, calling **only** through `McaCompat`. Wire into `all_of` / `any_of` / `not`. Implement validation with the strict/lenient toggle:

- **Lenient mode:** bad data → clear validation/debug output and safe-fail.
- **Strict mode:** surfaced error.

**Deliverable:** working conditions + validation. **STOP.**

### Phase 4 — Example Quest Pack (STOP)

Add a small set of example quests to the built-in pack, each gated on Phase 0 availability. Include at least one of each (skip the memorial/missing one *only* if its data is Unavailable):

- spouse / marriage quest
- child or family quest
- health / sickness quest
- guard or village-presence quest
- memorial or missing-villager quest

These should exercise the broader content surface where data allows: spouse errands, family disputes, child requests, guard patrol requests, sick-villager assistance, rescue missions, inheritance/memorial quests after deaths, village-home tasks.

**Deliverable:** example datapack quests. **STOP.**

### Phase 5 — Documentation

Update the datapack docs with **every** new condition type: its fields, a working example, failure behavior, and compatibility limitations. Mark each condition *supported* or *experimental* per the honesty rule, and list any deferred (Unavailable) concepts explicitly.

**Deliverable:** updated docs.

---

## Performance (expanded)

- Do **not** poll the relationship/family/village graph on the server tick.
- Resolve the actual evaluation cadence in Phase 0; design to it in Phase 1.
- Within any single eligibility pass, **snapshot/memoize each villager's MCA state once** and reuse it across all conditions in that pass.
- Prefer on-demand queries at eligibility-check time. Introduce a persistent cache (with event-driven invalidation) **only** if the hot-path analysis demands it, and document the tradeoff.

---

## Illustrative JSON shape (provisional — replace with Phase 0 syntax)

> Strawman to anchor intent only. The real key names, type tags, and nesting come from the existing conditions you extract in Phase 0. If your repo's style differs, the discovery step overrides this.

```json
{
  "type": "all_of",
  "conditions": [
    { "type": "mca:is_player_spouse" },
    { "type": "mca:age_group", "group": "adult" },
    { "type": "mca:health_below", "threshold": 0.5 },
    { "type": "hearts", "min": 50 }
  ]
}
```

```json
{
  "type": "mca:related_villager_status",
  "relation": "child",
  "status": "missing"
}
```

---

## What NOT to do

- Don't scatter MCA imports outside `McaCompat`.
- Don't invent JSON syntax; mirror the existing style discovered in Phase 0.
- Don't poll the relationship/family/village graph per tick.
- Don't hardcode quest logic — everything is datapack conditions.
- Don't mark a condition supported without confirmed MCA data **and** working validation **and** a safe-fail path.
- Don't crash the server on bad data outside strict mode.
- Don't change or regress existing condition behavior.
- Don't cross a STOP without my approval.

---

## Definition of Done

- New conditions are optional, datapack-driven, and compose with all existing gates and composites.
- All MCA access is behind `McaCompat`; conditions fail safe with debug logging.
- Validation covers every new field, with strict and lenient modes.
- Example quests demonstrate each viable category.
- Docs cover every condition with fields, examples, failure behavior, limitations, and honest support status.
