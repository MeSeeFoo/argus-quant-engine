# 0013. Trunk-based development without a `develop` branch

- **Status:** Accepted
- **Date:** 2026-10-16
- **Issues:** #10, #11

## Context

The repository needs a branching model that works with its automation: release-please, Dependabot, the required checks of the ruleset that protects `main`, and the nightly pipeline. The long-term goal is continuous deployment to AWS. Progress should stay visible in `main` commit by commit.

## Decision

**Trunk-based development** (GitHub Flow): `main` is the only long-lived branch. Every change starts from an issue, lives on a short branch `<type>/<issue>-<slug>`, and reaches `main` through a pull request merged with a **merge commit** (no squash, no rebase) once the required checks are green. Branches are brought up to date by merging `main` into them; pushed commits are never rewritten.

The details are in [CONTRIBUTING.md](../../CONTRIBUTING.md#branching-model).

## Consequences

- release-please computes releases from `main`; bots and CI target a single branch.
- `main` must always be releasable, so unfinished work stays behind its pull request.
- Every commit of a branch is visible in `main`, which makes the history longer but shows the progress.

## When to revisit

If Argus had to maintain several versions in production at the same time, or if a staging environment had to validate a batch of changes before release, a `develop` branch or release branches would make sense. That change would be a new ADR superseding this one.

## Alternatives considered

- **GitFlow (`develop`, `release/*`, `hotfix/*`).** Designed for scheduled releases; here it would add a second branch to protect and keep in sync, and an extra merge for every release.
- **Squash merges.** A cleaner history, but loses the per-commit progress the author wants to see.
