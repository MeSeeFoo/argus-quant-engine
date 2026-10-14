# Roadmap

The platform is built in layers. Each phase ends with something that works end to end, instead of half-building every service in parallel. Phases 0–2 set up the foundations and validate the architecture early. Phases 3–6 add functionality on a stable base: Python rules, the quant lab, the UI and the benchmarks. AWS comes once there is something worth deploying.

Some work can be pulled forward when it makes sense. For example, basic metrics from Phase 8 are worth adding from Phase 2 onwards.

---

## Phase 0 · Foundations

**Goal:** prepare the ground before writing any business logic.

- **Maven multi-module build:** a parent `pom.xml` that centralises versions (Spring Boot, Aeron, SBE…) and enforces Java 25 and Maven 3.9+.
- **`argus-contracts`:** message schemas. SBE for the fast path (`Trade`, `Signal`) and Protobuf for Kafka. The Java code is generated at build time.
- **Docker Compose:** Kafka, QuestDB, PostgreSQL, Apicurio (schema registry) and Grafana, started locally with a single command.
- **ArchUnit rules:** tests that check the hexagonal architecture automatically. For example, `domain` must not depend on Spring or Kafka.
- **GitHub Actions CI:** every push builds the project and runs the tests.

**Done when:** `mvn verify` passes locally and on GitHub, and `docker compose up` starts the infrastructure.

## Phase 1 · AI-assisted development workflow

**Goal:** set up a team of specialised AI agents (Claude Code) to help with the rest of the development.

- **Project instructions (`CLAUDE.md`):** conventions, build and test commands, architecture rules and how the repository is organised.
- **Specialised subagents (`.claude/agents/`):**
  - **Backend:** Java, Spring Boot, hexagonal architecture.
  - **Frontend:** Angular.
  - **QA automation:** JUnit, Testcontainers, ArchUnit, end-to-end UI tests.
  - **DevOps:** Docker, Terraform, GitHub Actions.
  - **Reviewer:** architecture rules and low-latency constraints on the hot path.
- **Skills for repeated tasks:** for example, scaffolding a new hexagonal service, adding a Kafka topic with its Protobuf contract, or writing an ADR.
- **Hooks and shared permissions (`.claude/settings.json`):** automatic checks after edits (for example compiling or formatting), and protection for generated code.
- **Collaboration workflow:** how the agents split and hand over work, git worktrees for parallel tasks, and review before merging.

Agents can be refined or added as later phases arrive. The frontend agent, for instance, only becomes relevant in Phase 5.

**Done when:** the agents are defined and versioned in the repository, and a small trial task has been completed through them end to end, including code and tests.

## Phase 2 · Vertical slice

**Goal:** a real piece of data flows through the whole system, even with minimal logic.

- **`argus-feed-gateway`:** connects to Binance and publishes trades over Aeron.
- **`argus-signal-engine`:** receives the trades, builds candles (1s/1m) and computes simple indicators (SMA/EMA). Publishes to Kafka without blocking the fast path.
- **`argus-market-archiver`:** consumes from Kafka and stores the data in QuestDB.

**Done when:** real BTCUSDT trades and candles are being stored in the database. This is the most important phase, because it validates the backbone of the system.

## Phase 3 · Python rules & alerts

**Goal:** the engine starts making decisions, driven by rules that users write in Python.

- **Proof of concept first:** confirm which `SandboxPolicy` GraalPy supports (`ISOLATED` or `UNTRUSTED`), and measure the cost of calling a sandboxed Python rule from the hot path.
- **`argus-rule-runtime`:** runs Python rules with GraalPy inside the engine process, with isolation, CPU and memory limits, and warm-up on historical data before a rule goes live.
- **`argus` Python SDK:** the `@rule` decorator, read-only access to the built-in Java indicators, and `argus.math` for numerical functions that NumPy would otherwise provide.
- Rule distribution through the compacted topic `argus.rules.v1`. The engine reloads rules at runtime, without restarting.
- If a rule fails or runs too long, only that rule is disabled and the engine keeps running.
- **`argus-action-dispatcher`:** receives alerts and runs their actions (webhook, email), with Resilience4j retries, a circuit breaker and a dead-letter topic for failures.

**Done when:** a Python rule fires on real data and a webhook is delivered, and a malicious rule or an infinite loop does not affect the engine.

## Phase 4 · Quant lab

**Goal:** a research environment for the analysis that does not belong in the live engine.

- **`argus-quant-lab`:** JupyterLab with pandas, NumPy and SciPy, reading the market data stored in QuestDB.
- Example notebooks: exploratory analysis, backtesting a rule on historical data, and calibrating a model (for example a GARCH).
- Calibrated parameters are published to the compacted topic `argus.rule-params.v1`, and live rules pick them up without being redeployed.

**Done when:** a parameter calibrated in a notebook changes the behaviour of a live rule.

## Phase 5 · API & UI

**Goal:** give the system a user interface.

- **`argus-api`:** REST for managing Python rules (validation, versioning, dry runs), historical queries on QuestDB, and WebSocket for live data. OIDC authentication with Keycloak.
- **`argus-ui`:** Angular dashboard with live charts, signals and alerts, plus a Python rule editor.

**Done when:** a rule can be created from the browser and seen firing on the chart.

## Phase 6 · Fast-path consumer & benchmarking

**Goal:** back the low-latency claims with numbers.

- **`argus-strategy-sim`:** receives signals over Aeron and simulates trades (paper trading).
- Benchmarks with JMH (microbenchmarks) and HdrHistogram (end-to-end latency percentiles).
- The latency targets in the README are replaced with measured results.

**Done when:** the README shows real p50/p99/p99.9 latencies.

## Phase 7 · AWS

**Goal:** the end-to-end deployment in the cloud.

- Infrastructure with Terraform: VPC, ECS (EC2 for the fast path, Fargate for everything else), MSK, QuestDB on EC2, RDS for PostgreSQL, Apicurio on Fargate, Cognito, S3 + CloudFront and ALB.
- CD pipeline: GitHub Actions builds the images, pushes them to ECR and deploys to ECS.

**Done when:** `terraform apply` brings everything up, the system works on AWS, and `terraform destroy` removes it without leftovers (or costs).

## Phase 8 · Observability

**Goal:** understand what happens inside the system.

- Distributed tracing with OpenTelemetry, for example following a trade from Binance to the alert it triggers.
- Dashboards in Grafana (local) and CloudWatch (AWS).
- SLO-based alerting, for example "p99 latency above X" or "Kafka consumer lag".

**Done when:** the cause of a problem can be found using only metrics, logs and traces.
