# Argus Quant Engine

Event-driven quant engine on Java 25, Maven and Spring Boot 4: market data in over Aeron + SBE (fast path), results out over Aeron and over Kafka + Protobuf (durable path), user models in sandboxed Python (GraalPy). The [README](README.md) describes the target architecture; [`docs/roadmap.md`](docs/roadmap.md) says what is built in each phase.

This file is the entry point. Details live in the documents it links to: read them instead of guessing, and do not copy them here. Everything in the repository (code, comments, docs, commits, pull requests) is written in English.

## Build and test

```bash
./mvnw verify            # build, tests, formatting, Error Prone + NullAway, JaCoCo thresholds
./mvnw spotless:apply    # format Java code (google-java-format); verify fails on unformatted code
```

- Use `mvnw.cmd` on Windows. JDK 25 is required; Maven comes from the wrapper.
- `verify` is the Definition of Done locally and gives the same result as CI. Every warning fails the build; nullable values need JSpecify's `@Nullable`.
- Every JVM that loads Agrona (SBE codecs, Aeron) needs `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED`. The build passes it to tests through the `agrona.jvm.args` property; services must pass it at runtime.
- Never edit or commit generated code (`target/generated-sources`): change the schemas in `contracts/` instead.

Local infrastructure (Kafka, QuestDB, PostgreSQL, Apicurio, Grafana) runs with Docker Compose, from `infra/docker`:

```bash
cp .env.example .env          # first time only; .env is git-ignored
docker compose up -d --wait   # returns once every container is healthy
docker compose down -v        # full reset, data included
```

Kafka topics are declared in `infra/docker/kafka/topics.conf`; topics are never created on first use. More in [Local environment](README.md#-local-environment). Every CI check and how to run it locally: [`docs/ci.md`](docs/ci.md#running-the-checks-locally).

## Repository layout

| Path | Module | What it is |
| :--- | :--- | :--- |
| `contracts/` | `argus-contracts` | SBE and Protobuf schemas, the single source of truth for messages ([`contracts/README.md`](contracts/README.md)) |
| `libs/architecture-rules/` | `argus-architecture-rules` | ArchUnit rules every service applies with one test class |
| `libs/model-runtime/` | `argus-model-runtime` | GraalPy sandbox and the `argus` Python SDK |
| `libs/builtin-models/` | `argus-builtin-models` | Java indicators (SMA, EMA, VWAP…), zero-allocation |
| `services/<name>/` | `argus-<name>` | `feed-gateway`, `feed-player`, `model-engine`, `strategy-sim`, `notification-dispatcher`, `data-archiver`, `bff` |
| `infra/docker/` | | Docker Compose for local development |
| `docs/adr/` | | Architecture Decision Records |

Only `contracts` and `libs/architecture-rules` exist so far; the rest arrive with their phase. The full planned layout is in the [README](README.md#-repository-layout-planned). Every service uses the hexagonal packages `com.argus.<service>.{domain, application, adapter.in, adapter.out, config}`.

## Vocabulary

- **Model:** versioned code plus its `ModelConfiguration`. Two kinds: `Indicator` and `AlertRule`.
- **Deployment:** a model version bound to instruments and parameters, running in `argus-model-engine`.
- **Output:** what a deployment emits: an `IndicatorValue` (from an `Indicator`) or an `Alert` (from an `AlertRule`).

There is no "signal", "signal engine" or "rule runtime" any more: [ADR 0014](docs/adr/0014-one-model-engine-for-alert-rules-and-indicators.md) replaced them with the model engine. Use these terms in code and docs.

## Rules that must never be broken

1. **The engine never blocks on Kafka.** The fast path writes to an Agrona ring buffer drained by a separate publisher agent; when the buffer is full, messages are dropped and counted ([ADR 0005](docs/adr/0005-the-engine-never-blocks-on-kafka.md)).
2. **No allocation on the hot path** in steady state: pre-allocated primitive ring buffers, SBE flyweights, no boxing, no locks ([Hot path](README.md#hot-path)). Spring only wires and starts the agents: no beans or proxies on the hot path ([ADR 0010](docs/adr/0010-spring-confined-to-the-edges-of-the-engine.md)).
3. **No Spring, Kafka or Aeron in `domain`.** The hexagonal rules are ArchUnit tests, not conventions ([ADR 0009](docs/adr/0009-hexagonal-architecture-enforced-with-archunit.md)).
4. **Event time everywhere.** Windows and a model's "now" use the events' `exchangeTimestamp`, never the system clock; wall-clock time only measures latency ([ADR 0017](docs/adr/0017-multi-instrument-models-and-event-time.md)).
5. **Contracts evolve compatibly.** Follow the versioning rules in [`contracts/README.md`](contracts/README.md); a breaking change means a new schema id or topic.

## Design decisions

The [ADRs](docs/adr/README.md) are the source of truth for design. Read the relevant ones before changing a design, and do not contradict them silently. An accepted ADR is **immutable**: to change a decision, write a new ADR that supersedes or amends it, and only update the old one's status line and links.

## Claude Code settings and hooks

[`.claude/settings.json`](.claude/settings.json) is shared by every session and agent:

- **Permissions.** Read-only `git` and `gh` commands and `./mvnw` run without asking. Force-pushes, pushes to `main` and `git rebase` are denied.
- **Hooks**, in [`.claude/hooks`](.claude/hooks), are bash scripts with no dependency beyond git and the Maven Wrapper (no `jq`). Claude Code runs them with bash on Linux and macOS, and with Git Bash on Windows, also in sessions that use the PowerShell tool:

| Hook | When | What it does |
| :--- | :--- | :--- |
| `format-java.sh` | When Claude finishes a turn | Runs `spotless:apply` once on the Java files changed in the working tree, so `verify` never fails on formatting. Formatting once per turn avoids starting Maven after every edit. A file that does not parse is reported back to Claude to fix. |
| `protect-generated-code.sh` | Before a file is written or edited | Blocks edits under `target/` and `generated-sources/`, and points to the SBE or Protobuf schema to change instead. |
| `guard-git-push.sh` | Before a `git` command in Bash or PowerShell | Blocks force-pushes (`--force`, `-f`, `+refspec`, `--mirror`), pushes to `main` and a bare `git push` while `main` is checked out. |

`bash .claude/hooks/test-hooks.sh` checks every hook, and CI runs it on Linux and Windows. These guards stop mistakes, not attacks: the ruleset that protects `main` on GitHub is the real boundary. Personal overrides go in `.claude/settings.local.json`, which is git-ignored; a deny rule cannot be overridden there.

**Reviewer agent.** [`.claude/agents/reviewer.md`](.claude/agents/reviewer.md) is a read-only subagent that checks a diff against the ADRs, the hot-path rules, the contracts and `CONTRIBUTING.md`, and reports findings by severity. Ask "use the reviewer agent on this branch" (or on pull request `#n`) before opening or merging a pull request. Its checklist is the delimited *Checklist* section of that file.

## Workflow

[`CONTRIBUTING.md`](CONTRIBUTING.md) defines issues, branch names, Conventional Commits, pull requests, merging and the Definition of Done, and CI enforces most of it. Follow it as written. In short: every change comes from an issue and reaches `main` through a pull request; existing commits are never rewritten.
