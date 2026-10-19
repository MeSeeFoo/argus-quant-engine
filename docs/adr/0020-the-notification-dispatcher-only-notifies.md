# 0020. The notification dispatcher only notifies

- **Status:** Accepted
- **Date:** 2026-10-19
- **Issues:** #11, #42

## Context

The original `argus-action-dispatcher` "ran actions" for alerts, which left open whether an action could change the system (stop a model, press the PANIC). Alerts are delivered at least once by Kafka, and a rule hovering around a threshold can fire many times a minute.

## Decision

The service is renamed **`argus-notification-dispatcher`**. It **notifies people and external systems; it never acts on Argus.** For each alert in `argus.alerts.v1`, it:

1. **Routes** it: which channels the owner of the deployment configured (stored in PostgreSQL by the BFF and distributed through a compacted topic).
2. **Deduplicates** it by alert id, so at-least-once delivery does not notify twice.
3. **Applies a cooldown** per deployment and instrument, so a rule that flaps does not flood the user.
4. **Renders** it with a template per channel.
5. **Delivers** it (webhook, email; chat tools later) with retries and backoff, and a **circuit breaker per destination**, so a webhook that is down does not affect the others (Resilience4j).
6. Sends what still fails to the dead-letter topic `argus.alerts.dlq.v1`.
7. Publishes the result (delivered, failed, suppressed) to `argus.notification-deliveries.v1`, for the UI and as an audit trail.

## Consequences

- A clear boundary: anything that changes Argus goes through the BFF, with its permissions and audit.
- Users get one notification per real event, not per evaluation.

## Alternatives considered

- **Actions that act on Argus (stop a model, PANIC).** Mixes notification with control and bypasses the BFF's permissions.
- **Notifications sent by the engine.** Puts slow I/O next to the hot path.
