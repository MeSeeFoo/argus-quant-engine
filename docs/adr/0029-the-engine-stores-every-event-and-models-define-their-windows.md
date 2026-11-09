# 0029. The engine stores every event, and models define their windows

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issues:** #111, #112
- **Amends:** [0017](0017-multi-instrument-models-and-event-time.md)

## Context

A model declares its own windows: by time or by number of ticks, sliding or tumbling ([0014](0014-one-model-engine-for-alert-rules-and-indicators.md)). One model wants a 1-minute candle, another a mid price computed on every top-of-book update. The engine therefore cannot summarise events into fixed bars when they arrive: what a bar throws away, a model may need.

Models receive market data as objects, never as a ring buffer or bytes, and several models read the same events. The engine must still allocate nothing in steady state, and a slow model must not stop the engine from reading the feed.

## Decision

### Two agents joined by a Disruptor

```text
reader agent                     LMAX Disruptor            evaluator agent
Aeron poll ─▶ decode ─▶ checks ─▶ ring of reused slots ─▶ store ─▶ windows ─▶ models
```

- The **reader agent** polls Aeron, decodes with flyweights, drops old trades and counts `sequence` gaps ([0022](0022-feed-gateway-shards-with-an-active-and-a-passive.md)). It publishes trades and top-of-book updates into one Disruptor ring, so the evaluator sees them in arrival order across instruments.
- The **evaluator agent** drains the ring through an `EventPoller`, which keeps it an Agrona agent that Spring starts like any other ([0010](0010-spring-confined-to-the-edges-of-the-engine.md)). The stores, the windows and the models are used only on this thread, so they need no locks or memory barriers.
- **The reader never waits for the evaluator.** Each poll asks Aeron for no more messages than the ring has free slots. What was not read stays in Aeron's buffers; if the evaluator falls behind for longer than those can hold, the engine loses data on its own side (`fc=max`) and sees it as a `sequence` gap.
- The ring is only a hand-off. A slot is free again as soon as the evaluator has passed it, so nothing keeps a reference to one: the evaluator copies each event into a store.

### Stores: every event, as objects

- One store per instrument and kind of event (trades, top of book). It holds every event, in arrival order, in pre-allocated objects.
- Two limits per kind are configured: the **initial** and the **maximum** number of objects. The store overwrites its oldest object when no window needs it any more. When the oldest is still needed it grows, up to the maximum. Growing allocates, but it only happens until the store reaches the size its windows need, and then steady state allocates nothing.
- At the maximum the oldest object is overwritten anyway, and the affected windows report that they are incomplete. A model is never silently given less than it asked for.
- **Objects are read-only to models, not immutable.** Each stored object is a mutable class that implements a read-only interface (`Trade`, `BestBidAsk`), and models only ever receive the interface. It is one object with two faces, so reading it costs no extra allocation or copy. The evaluator overwrites the object when it is reused, so a reference is valid only during the evaluation it was received in. A model that needs a value later copies the value.

### Windows: positions over a store

- A window is declared by a model and copies no events: it is a range of positions over one store. Any number of models share the same objects.
- By time (on `exchangeTimestamp`) or by number of events, sliding or tumbling. Bars are one thing a model may build from a window, only if it wants them.
- **Late events are not reordered.** Events stay in arrival order, which keeps a replay identical to the original run. A window's "now" is the latest `exchangeTimestamp` of its input and never goes backwards.

### When a model is evaluated

This amends [0017](0017-multi-instrument-models-and-event-time.md), which evaluates a deployment on every update of any of its inputs. That stays the default, and a deployment may instead be evaluated **every N events** or **every T of event time**. A time-based evaluation only fires when an event arrives: the system clock is never involved, so replays give the same results.

## Consequences

- Models can compute anything from raw events, including a mid price on every update.
- Memory is bounded by configuration and is the cost of keeping raw events: about 85 MB for a million top-of-book objects. History longer than the store does not exist in memory; an indicator that needs more keeps its own state.
- Two threads plus the driver's, none pinned on Fargate ([0021](0021-run-everything-on-fargate-with-aeron-over-udp.md)).
- A slow model delays the other models, as they share the evaluator thread, but not the reading of the feed until the ring is full.
- The Disruptor is a second ring buffer library next to Agrona's, used only here. Part of the reason is a learning goal, stated as such, like Spring in [0010](0010-spring-confined-to-the-edges-of-the-engine.md).
- The gateway keeps Agrona's ring, for the reasons in `MarketDataQueue`: it has several producers, and its events never leave the service as objects.

## Alternatives considered

- **Fixed 1-second and 1-minute bars.** Small and bounded memory, but a model could not build its own window from them.
- **Immutable objects, one allocated per event.** Safest to share, but it allocates on every event, against the hot-path rule.
- **A pool with reference counts per object.** Would let objects outlive an evaluation, but costs a count per reader per event. A store whose size follows its windows makes it unnecessary.
- **Primitive columns behind the same API.** Better cache locality and nothing for the GC to trace, but the model-facing objects would have to be flyweights over them, for no gain that has been measured yet.
- **The evaluator on the reader's thread.** One thread and no hand-off, but a slow Python model would delay reading the feed.
- **Keeping history in the Disruptor's ring.** One structure instead of two, but the producer overwrites slots the evaluator has passed, so windows would race with it.
- **Claiming with `tryNext()` and dropping when full.** Simple, but reports a full ring with an exception, which the hot path does not use for control flow. Limiting the poll avoids a full ring altogether.
