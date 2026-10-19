# 0025. Model promotion across environments

- **Status:** Accepted, documented only (not implemented)
- **Date:** 2026-10-19
- **Issues:** #11, #44

## Context

Users write models in the UI and the engine loads them at runtime ([0014](0014-one-model-engine-for-alert-rules-and-indicators.md)). That is the right workflow for research and development, but in production a model that can be edited in place is an uncontrolled change. Regulation of algorithmic trading expects algorithms to be tested before deployment, changes to be controlled, and their activation to be approved (MiFID II, RTS 6).

## Decision

This describes how Argus would be run in a regulated organisation. It is not implemented.

- **Models live in their own repository and pipeline**, separate from the Argus project. Argus is the platform; models are artifacts that run on it.
- **Pipeline of a model version:**
  1. Lint and unit tests with the `argus` SDK, **run on GraalPy**, not CPython, so they behave as in production.
  2. A **backtest with the feed player** ([0024](0024-a-feed-player-for-replay-and-backtesting.md)) as a quality gate.
  3. An **immutable artifact**, identified by its hash, promoted from development to pre-production to production without being rebuilt.
- **In production, models cannot be edited**: the model registry is read-only, and the UI's editor is a development tool.
- **Activation in production is done by operations** (the team that runs the platform), when the business asks for it: four eyes, both steps audited.
- **Parameters** (for example calibrations from the quant lab) are data, not code: they can change in production without the full pipeline, but **only within the ranges the model declares** in its `ModelConfiguration`, and every change is versioned and audited.

## Consequences

- What runs in production is exactly what was tested.
- Agility for parameters, control for code.
- The dynamic loading of models stays a feature of development environments.

## Alternatives considered

- **Edit models directly in production.** Fast, but uncontrolled.
- **Models inside the Argus repository.** Couples the release of models to the release of the platform.
