# cloud-itonami-isic-0126

Open Occupation Blueprint for **ISIC Rev. 4 0126**: Growing of oleaginous
fruits.

This repository implements a forkable OSS **oleaginous-fruit plantation
operations coordinator**: a facility-management robot manages plantation-
block record logging, field-operation (pruning/spraying/harvest)
scheduling, and supply procurement under a governor-gated actor, so an
oleaginous-fruit-growing operation (oil palm, olive, coconut, candlenut
plantations and groves) keeps its own operational records and maintains
full transparency over decisions.

**Maturity: `:implemented`.** `src/oleaginousops/` implements the
`OleaginousOpsAdvisor` (`oleaginousops.advisor`) and the independent
`OleaginousOperationsGovernor` (`oleaginousops.governor`), composed by
`oleaginousops.operation` following the itonami actor pattern
(ADR-2607011000): `advise -> govern -> phase-gate -> commit | escalate |
hold`. See [Testing](#testing) below for the current green test count
(`clojure -M:test`).

`oleaginousops.operation` is a synchronous stub of this flow (see its
docstring) — production wiring into a `langgraph-clj` StateGraph with
`interrupt-before`/checkpoint-based human-in-the-loop resume for escalated
operations is deferred, mirroring `cloud-itonami-isic-0125`'s own
`berrynutops.operation`.

## What this does NOT do

This actor coordinates **back-office logistics only**. It explicitly does **NOT**:

- **Direct field-equipment operation** — remains the grower's exclusive authority
- **Spray-application decisions** — remains the agronomist/grower authority
- **Harvest-timing / economic decisions** — economic authority remains human
- **Direct execution of any kind** — any proposal for direct actuation is a hard block

## HARD invariants (always hold, never overridable)

1. **plantation-not-registered** — the request's `plantation-id` must resolve to a
   registered plantation block in the Store before any proposal can proceed
2. **no-execution** — every proposal's `:effect` must be `:propose` (the governor
   never directly operates field equipment, never finalizes a spray application)
3. **field-equipment-or-spray-blocked** — `:operate-field-equipment` and
   `:finalize-spray-application` proposals are unconditionally, permanently blocked
4. **op-not-allowed** — any op outside the closed allowlist below is rejected
5. **plantation-count-invalid** — `:log-plantation-record` with a non-positive
   logged quantity (trees counted / harvest weight / oil-content-test yield /
   bunch-fruit count) is rejected

## Always-escalate operations (human sign-off, regardless of confidence)

- `:flag-crop-health-concern` — any pest/disease (e.g. bud rot/Ganoderma)/
  drought-stress concern → automatic escalation
- `:order-supplies` over its category cost threshold (default 500 currency
  units; see `oleaginousops.facts/supply-categories`)
- Any proposal with confidence below the Governor's floor (0.7)

## Operational requests (closed allowlist, all `:effect :propose`)

```text
:log-plantation-record
  — record planting/harvest-yield/oil-content-test data
  — requires a registered plantation block; non-positive quantities are rejected

:schedule-field-operation
  — propose pruning/spraying/harvest scheduling
  — does NOT make or finalize a spray-application decision

:flag-crop-health-concern
  — surface a pest/disease (e.g. bud rot/Ganoderma) or drought-stress concern
  — ALWAYS escalates for human review

:order-supplies
  — procurement for seedlings, fertilizer, equipment
  — escalates if cost exceeds its category threshold
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs the
physical domain work**. Here a facility-management robot handles:

- Plantation-block record logging and entry
- Field-operation scheduling and reminders
- Supply inventory and ordering
- Audit ledger maintenance

The **OleaginousOperationsGovernor** is the independent safety layer that gates all
proposals before a robot action is executed. The governor never dispatches hardware
directly; `:high`/`:safety-critical` actions (such as escalated crop-health concerns
or high-cost supply orders) require human sign-off.

## Core Contract

```text
operational request (log, schedule, concern, order)
        |
        v
OleaginousOpsAdvisor -> OleaginousOperationsGovernor -> phase gate -> commit, or escalate for human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated operation can dispatch a robot action the governor refuses, suppress an
operating record, or hide a crop-health concern without governor approval and audit
evidence.

## Module structure

Mirrors `cloud-itonami-isic-0125` (`berrynutops.*`) module-for-module:

- `oleaginousops.facts` — reference data: supply-category cost thresholds, fruit classes
- `oleaginousops.registry` — pure independent verification functions (cost/count/confidence)
- `oleaginousops.store` — `Store` protocol + in-memory `MemStore` (plantation-block registration lookup)
- `oleaginousops.advisor` — `Advisor` protocol + `MockAdvisor` (the sealed LLM/decision node)
- `oleaginousops.governor` — `OleaginousOperationsGovernor`: hard invariants + escalation gates
- `oleaginousops.phase` — 0→3 rollout phase gate
- `oleaginousops.operation` — composes advisor → governor → phase into one operation run
- `oleaginousops.sim` — demo runner (`clojure -M:run`)

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISIC Rev. 4 `0126`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Testing

```bash
clojure -M:test   # run the suite (see raw output for tests/assertions)
clojure -M:lint   # clj-kondo, 0 errors / 0 warnings
clojure -M:run    # demo runner
```

## License

AGPL-3.0-or-later.
