# 0001. Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-10-14
- **Issues:** #11

## Context

Argus is a learning project built in phases, partly with AI agents that start every task without the history of earlier conversations. Many decisions are deliberate trade-offs (Spring on a low-latency path, Python rules, dropping messages instead of blocking). Without a record, the next contributor, human or agent, sees the result and not the reasoning, and may "fix" something that was done on purpose.

## Decision

We record every significant design decision as an Architecture Decision Record in `docs/adr`, following [`template.md`](template.md): context, decision, consequences and alternatives considered, with a number, a status and a date. A decision is significant when it is hard to reverse, affects more than one module, or a reasonable engineer could have chosen differently.

The [Definition of Done](../../CONTRIBUTING.md#definition-of-done) requires an ADR for such decisions.

## Consequences

- The README describes *what* the system is; the ADRs explain *why*. The README links to them instead of repeating the reasoning.
- Changing a decision means writing a new ADR that supersedes or amends the old one, so the history stays readable.
- Writing an ADR costs a little time on each significant decision.

## Alternatives considered

- **Reasoning in the README.** It was the situation before this ADR: the README grew long, and it mixes the current design with the reasons behind it.
- **Reasoning in pull request descriptions.** They are hard to find later and are not versioned with the code.
