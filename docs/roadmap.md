# Roadmap

The platform is built in layers. Each phase ends with something that works end to end, instead of half-building every service in parallel. Phases 0–2 set up the foundations and validate the architecture early. Phases 3–6 add functionality on a stable base: Python models, the quant lab, the UI and the benchmarks. AWS and high availability come once there is something worth deploying. The reasons behind each design choice are in the [Architecture Decision Records](adr/README.md).

Some work can be pulled forward when it makes sense. For example, basic metrics from Phase 8 are worth adding from Phase 2 onwards.

---

## Phase 0 · Foundations

**Goal:** prepare the ground before writing any business logic.

- **Maven multi-module build:** a parent `pom.xml` that centralises versions (Spring Boot, Aeron, SBE…) and enforces Java 25 and Maven 3.9+.
- **`argus-contracts`:** message schemas. SBE for the fast path (`Trade`, `BestBidAsk`, `IndicatorValue`, `Alert`, `Heartbeat`) and Protobuf for Kafka (one message per topic). The Java code is generated at build time.
- **Docker Compose:** Kafka, QuestDB, PostgreSQL, Apicurio (schema registry) and Grafana, started locally with a single command.
- **ArchUnit rules:** tests that check the hexagonal architecture automatically. For example, `domain` must not depend on Spring or Kafka.
- **GitHub Actions CI:** every push builds the project and runs the tests.
- **Architecture Decision Records:** the decisions taken so far, and the architecture review that closes the design ([`docs/adr`](adr/README.md)).

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

- **`argus-feed-gateway`:** connects to Binance and publishes trades and top-of-book updates over Aeron UDP, and to Kafka without blocking the fast path: every trade, and the top of book conflated at a configurable interval. Counts the ones it has to drop ([ADR 0027](adr/0027-the-feed-gateway-publishes-market-data-on-both-paths.md)). A single shard and a single task; the instrument catalogue starts with one shard.
- **`argus-model-engine`:** a single instance receives the ticks, keeps in-memory windows (1s/1m) on event time and runs built-in Java indicators (SMA/EMA) from `argus-builtin-models`. Publishes every output to Kafka without blocking the fast path, and counts the ones it has to drop.
- **`argus-data-archiver`:** consumes from Kafka and stores the ticks and indicator values in QuestDB.

**Done when:** real BTCUSDT trades and top-of-book updates are being stored in the database, and a QuestDB query (`SAMPLE BY`) returns 1-minute candles built from them. This is the most important phase, because it validates the backbone of the system.

## Phase 3 · Python models & alerts

**Goal:** the engine starts making decisions, driven by models that users write in Python.

- **Proof of concept first:** confirm which `SandboxPolicy` GraalPy supports (`ISOLATED` or `UNTRUSTED`), and measure the cost of calling a sandboxed Python model from the engine ([ADR 0011](adr/0011-user-models-in-python-on-graalpy.md)).
- **`argus-feed-player`:** replays recorded sessions, for backtests and for instruments Binance does not list ([ADR 0024](adr/0024-a-feed-player-for-replay-and-backtesting.md)).
- **`argus-model-runtime`:** runs Python models with GraalPy inside the engine process, with isolation, CPU and memory limits, and warm-up on recent data before a deployment emits.
- **`argus` Python SDK:** `ModelConfiguration`, the `Indicator` and `AlertRule` interfaces, read-only access to the built-in Java indicators, and `argus.math` for numerical functions that NumPy would otherwise provide.
- **Models and deployments** distributed through the compacted topics `argus.models.v1` and `argus.model-deployments.v1`. The engine starts new deployments at runtime, without restarting. Models can use several instruments and other indicators' outputs ([ADR 0017](adr/0017-multi-instrument-models-and-event-time.md)).
- If a model fails or runs too long, only that deployment is disabled and the engine keeps running.
- **PANIC button:** stops every model; persistent and audited ([ADR 0019](adr/0019-a-global-panic-button-for-models.md)).
- **`argus-notification-dispatcher`:** turns alerts into notifications (webhook, email), with deduplication, cooldown, Resilience4j retries, a circuit breaker per destination and a dead-letter topic ([ADR 0020](adr/0020-the-notification-dispatcher-only-notifies.md)).

**Done when:** a Python model fires on real data and a webhook is delivered, the same model gives the same results in a replay, a malicious model or an infinite loop does not affect the engine, and the PANIC button stops every model.

## Phase 4 · Quant lab

**Goal:** a research environment for the analysis that does not belong in the live engine.

- **`argus-quant-lab`:** JupyterLab with pandas, NumPy and SciPy, reading the market data stored in QuestDB.
- Example notebooks: exploratory analysis, backtesting a model on historical data, and calibrating a model (for example a GARCH).
- Calibrated parameters update a deployment through the BFF, within the ranges the model declares, and live deployments pick them up without being redeployed.
- **Demo model: a US stock valued in euros.** Combines a US stock (replayed by the feed player) with EUR/USD (Binance's EURUSDT as a proxy) to show the stock's value for a trader in Madrid, both at mid and at executable prices. It adds the theoretical EUR/USD forward by covered interest parity, with interest rates calibrated in the lab, compared with a quoted future.

**Done when:** a parameter calibrated in a notebook changes the behaviour of a live deployment.

## Phase 5 · API & UI

**Goal:** give the system a user interface.

- **`argus-bff`:** REST for managing models, deployments and the instrument catalogue (validation, versioning, dry runs), historical queries on QuestDB, WebSocket for live data, the PANIC button and the status of the feed gateways. OIDC authentication with Keycloak.
- **`argus-ui`:** Angular dashboard with live charts, indicators and alerts, a Python model editor, deployments, gateway status and the PANIC button.

**Done when:** a model can be written and deployed from the browser and seen firing on the chart.

## Phase 6 · Fast-path consumer & benchmarking

**Goal:** back the low-latency claims with numbers.

- **`argus-strategy-sim`:** receives model outputs over Aeron and simulates trades (paper trading).
- Benchmarks with JMH (microbenchmarks) and HdrHistogram (end-to-end latency percentiles).
- The latency targets in the README are replaced with measured results.

**Done when:** the README shows real p50/p99/p99.9 latencies.

## Phase 7 · AWS & high availability

**Goal:** the end-to-end deployment in the cloud, with no single point of failure on the market data path.

- Infrastructure with Terraform: VPC, ECS on Fargate for every service, Cloud Map for Aeron discovery, MSK, QuestDB on EC2, RDS for PostgreSQL, Apicurio on Fargate, Cognito, S3 + CloudFront and ALB ([ADR 0021](adr/0021-run-everything-on-fargate-with-aeron-over-udp.md)).
- **Feed gateway shards** with an active and a passive per shard, elected through Kafka; the passive resumes from the last trade the active published ([ADR 0022](adr/0022-feed-gateway-shards-with-an-active-and-a-passive.md), [0023](adr/0023-instrument-catalogue.md)).
- **Model engine scale-out:** deployments shared across instances through a Kafka consumer group ([ADR 0016](adr/0016-distribute-deployments-with-a-kafka-consumer-group.md)).
- CD pipeline: GitHub Actions builds the images, pushes them to ECR and deploys to ECS.

**Done when:** `terraform apply` brings everything up, the system works on AWS, stopping the active task of a shard or a model engine instance loses no data, and `terraform destroy` removes it without leftovers (or costs).

## Phase 8 · Observability

**Goal:** understand what happens inside the system.

- Distributed tracing with OpenTelemetry, for example following a trade from Binance to the alert it triggers.
- Dashboards in Grafana (local) and CloudWatch (AWS).
- SLO-based alerting, for example "p99 latency above X" or "Kafka consumer lag".

**Done when:** the cause of a problem can be found using only metrics, logs and traces.

## Documented, not planned

Two topics are designed but belong to no phase: the regulatory archive for MiFID II ([ADR 0026](adr/0026-regulatory-archive-for-mifid-ii.md)) and the promotion of models across environments ([ADR 0025](adr/0025-model-promotion-across-environments.md)).
