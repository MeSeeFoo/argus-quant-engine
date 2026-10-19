# 0019. A global PANIC button for models

- **Status:** Accepted
- **Date:** 2026-10-19
- **Issues:** #11, #42

## Context

If models start producing harmful output (a bug, bad parameters, abnormal market conditions), operators need one action that stops all of them immediately. Regulation of algorithmic trading requires such a kill functionality (MiFID II, RTS 6, article 12; see [0026](0026-regulatory-archive-for-mifid-ii.md)). The stop must be global, including instances that are restarting or being added, and auditable.

## Decision

- **Scope: models only.** Every deployment on every engine instance stops evaluating and emitting. The feed gateway, the archiver and the notification dispatcher keep running, so what happens after the PANIC is still recorded. Alerts already published before the PANIC are still notified.
- **Persistent state.** The BFF writes the PANIC to the compacted topic `argus.control.v1`. Every instance reads it at startup and keeps listening: an instance that starts, restarts or receives deployments in a rebalance **starts halted**. An instance that cannot read the control state starts halted as well (fail-safe).
- **No re-arm from the product.** Models cannot be restarted from the UI. Re-arming is an operational procedure: an administration command, restricted to an operations role and audited, that clears the state, followed by a rolling restart of the engine.
- **Two-step audit**, in `argus.audit.v1`:
  1. `PANIC_REQUESTED`, with the user who pressed it (OIDC identity, restricted role).
  2. `HALTED` from every instance, with the number of deployments it stopped. The BFF compares the acknowledgements with the members of the engine's consumer group ([0016](0016-distribute-deployments-with-a-kafka-consumer-group.md)) and records `PANIC_COMPLETED`, or `PANIC_INCOMPLETE` naming the instances that did not answer in time.
- An evaluation in progress is cancelled (the GraalPy context is closed).

## Consequences

- The PANIC is truly global: scaling out, restarts and rebalances cannot bypass it.
- Restarting models after a PANIC is deliberately slow and requires operations.
- The audit trail proves both who pressed it and that it took effect.

## Alternatives considered

- **In-memory latch, cleared by a restart.** An instance that was starting, or a new one, would come up running models: not global.
- **Re-arm from the UI.** Too easy to undo a safety action by mistake.
- **PANIC also stops notifications.** Rejected: alerts emitted before the PANIC are still useful information.
- **PANIC per model or per deployment.** Already covered by stopping a deployment; the PANIC is the last-resort global action.
