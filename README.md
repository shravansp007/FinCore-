# FinCore - Intelligent Banking Platform

FinCore is a production-style digital banking backend built to model real-world financial system behavior under strict security, consistency, and observability requirements. The platform goes beyond CRUD banking flows by implementing refresh-token rotation, account lockout, idempotent transfers, distributed event processing, fraud risk scoring, scheduled batch operations, and standards-based API governance.

The system is designed as a portfolio-grade engineering project with explicit trade-offs for reliability, auditability, and scale. It combines synchronous APIs for transactional guarantees with asynchronous Kafka pipelines for decoupled side effects, uses Redis-backed controls for race-safe operations, and exposes deep telemetry (metrics, tracing, structured logs) for incident analysis and performance tuning.

## Architecture Overview

```text
+------------------+       HTTPS/JWT        +------------------------------+
| Angular Frontend | <--------------------> | Spring Boot API (FinCore)    |
+------------------+                        | - Auth/RBAC                  |
                                            | - Transfers/Idempotency      |
                                            | - Fraud Engine (CoR)         |
                                            | - Batch Jobs                 |
                                            | - OpenAPI + RFC7807          |
                                            +----+-----------+-------------+
                                                 |           |
                           JPA/SQL               |           | Redis Cache + Locks
                                                 |           |
                                    +------------v---+   +---v-----------------+
                                    | PostgreSQL      |   | Redis + Redisson    |
                                    | - users/accounts|   | - refresh sessions  |
                                    | - transactions  |   | - lockout counters  |
                                    | - audit/fraud   |   | - idempotency store |
                                    +-----------------+   +---------------------+
                                                 |
                                                 | TransactionEvent / AccountEvent
                                                 v
                                      +----------------------+
                                      | Kafka Cluster        |
                                      | topics + DLT         |
                                      +----+--------+--------+
                                           |        |
                     +---------------------+        +----------------------+
                     |                                               |
             +-------v--------+                             +--------v--------+
             | Consumers      |                             | DeadLetter       |
             | Audit/Notify/  |                             | Consumer         |
             | Fraud          |                             | failed_events DB |
             +----------------+                             +------------------+

Observability Plane:
Prometheus <--- /actuator/prometheus --- FinCore --- tracing ---> Zipkin
Grafana ---- dashboards ---- Prometheus
Structured JSON logs (traceId/spanId/userId/transactionId) ---> log pipeline
```

## Feature List

### Security and Identity
- JWT access tokens with refresh token rotation (HttpOnly cookie + Redis session state)
- Token reuse detection with immediate all-session revocation
- Redis-based account lockout (5 failed attempts, 15-minute TTL reset)
- Device fingerprinting (User-Agent + IP hash) with suspicious login auditing
- Role-based access control for USER and ADMIN domains

### Transaction Integrity
- Atomic fund transfer flows with transactional boundaries
- Idempotency keys (`X-Idempotency-Key`) with Redis `SETNX` race protection
- Duplicate request replay using cached response payloads
- Standardized RFC 7807 Problem Details for API failures

### Event-Driven Processing
- Kafka producer publishes transfer domain events
- Consumers for audit logging, notification dispatch, and fraud pipeline triggers
- Dead Letter Topic and failed-event persistence for operational replay/review

### Fraud Detection
- Chain of Responsibility fraud engine with 6 composable rules
- Weighted risk aggregation with threshold-based alerting and account lock actions
- Configurable rule threshold persistence for runtime tuning

### Batch and Offline Workloads
- Monthly statement generation job with streaming PDF writing
- Dormant account detection and status transitions
- Failed pending transaction cleanup with compensating actions

### Caching and Concurrency
- Two-level cache strategy (Caffeine L1 + Redis L2)
- Redisson distributed locking for cross-instance safety
- Redis-backed TTL caches for high-churn data paths

### Observability
- Custom Micrometer metrics for transfers, fraud, cache behavior, and account activity
- Prometheus scraping + Grafana dashboarding
- End-to-end Zipkin tracing with context propagation through Kafka
- Structured JSON logging with MDC enrichment (`traceId`, `spanId`, `userId`, `transactionId`)

### API and Delivery
- API versioning (`/api/v1`, `/api/v2`) and media-type version negotiation via `Accept`
- OpenAPI docs with Swagger UI and JWT-protected interactive testing
- GitHub Actions CI/CD with unit/integration tests, JaCoCo gate, Docker image publishing

## Tech Stack

| Layer | Technologies |
|---|---|
| Language/Runtime | Java 17 |
| Framework | Spring Boot 3.x |
| Security | Spring Security, JWT, RBAC |
| Data | PostgreSQL, Spring Data JPA/Hibernate |
| Caching/Locks | Redis, Caffeine, Redisson |
| Messaging | Apache Kafka, Spring Kafka |
| Batch | Spring Batch |
| Documents | iText7 |
| API Docs | Springdoc OpenAPI, Swagger UI |
| Observability | Micrometer, Prometheus, Grafana, Zipkin |
| Logging | Logback JSON, MDC |
| Testing | JUnit 5, Mockito, Testcontainers, Gatling, JaCoCo |
| CI/CD | GitHub Actions, Docker, Docker Hub |

## Run Locally (Docker Compose)

### 1. Prerequisites
- Docker Desktop (with Docker Compose v2)
- Java 17+ and Maven 3.9+ (if running app outside container)

### 2. Start infrastructure
```bash
docker compose up -d postgres redis zookeeper kafka zipkin prometheus grafana
```

### 3. Verify infrastructure
```bash
docker compose ps
```

### 4. Build and run backend
```bash
mvn clean package -DskipTests
mvn spring-boot:run
```

### 5. Access services
- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Zipkin: `http://localhost:9411`

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | No | Register a user |
| POST | `/api/v1/auth/login` | No | Login and issue access + refresh token cookie |
| POST | `/api/v1/auth/refresh` | No (refresh cookie) | Rotate refresh token and return new access token |
| POST | `/api/v1/auth/logout` | No (refresh cookie) | Logout and clear refresh session |
| POST | `/api/v1/transactions` | Yes | Create transaction (deposit/withdraw/transfer) |
| POST | `/api/v1/transactions/transfer` | Yes | Idempotent transfer with `X-Idempotency-Key` |
| POST | `/api/v2/transfers` | Yes | v2 transfer contract |
| POST | `/api/transfers` + `Accept: application/vnd.fincore.transfer-v1+json` | Yes | Media-type negotiated v1 transfer response |
| POST | `/api/transfers` + `Accept: application/vnd.fincore.transfer-v2+json` | Yes | Media-type negotiated v2 transfer response |
| GET | `/api/v1/transactions` | Yes | List user transactions |
| GET | `/api/v1/transactions/account/{accountId}` | Yes | Account transaction history |
| GET | `/api/v1/transactions/account/{accountId}/mini-statement` | Yes | Last N account transactions |
| GET | `/api/v1/transactions/reference/{reference}` | Yes | Lookup transaction by reference |
| GET | `/api/v1/admin/users` | Admin | List users |
| GET | `/api/v1/admin/accounts` | Admin | List accounts |
| POST | `/api/v1/admin/accounts/{id}/unlock` | Admin | Manually clear account lockout |
| GET | `/api/v1/admin/fraud/alerts` | Admin | Review fraud alerts |
| PUT | `/api/v1/admin/fraud/alerts/{id}` | Admin | Mark alert as CLEARED/CONFIRMED |

## Performance Benchmarks (Gatling)

> Note: values below are representative placeholders; replace with your latest report snapshot.

- Scenario: 500 concurrent users, ramp-up 30s, each performs login + 5 transfers
- Throughput: `~1,250 req/s`
- P95 latency: `~380 ms`
- P99 latency: `~610 ms`
- Error rate: `~0.45%`
- SLA status: `PASS` (P95 < 500ms, errors < 1%)

## Key Design Decisions

### Why Kafka
Kafka decouples core transfer commit from side effects (audit, notifications, fraud checks), improving resilience and horizontal scalability. It also gives replayability and DLT-based failure isolation, which are critical for financial audit workflows.

### Why Chain of Responsibility for Fraud
Fraud rules evolve frequently and should be independently testable and reorderable. Chain of Responsibility enables modular rule classes, composable scoring, and low-risk extension without modifying existing rule logic.

### Why Two-Level Cache (Caffeine L1 + Redis L2)
L1 in-memory cache reduces latency and network overhead for hot keys; L2 Redis provides shared cache coherence across instances. This hybrid model balances speed and consistency under scale-out deployments.

### Why Idempotency Keys
Payment clients retry on timeouts and flaky networks; without idempotency, retries can double-charge users. Redis-backed idempotency with atomic lock acquisition ensures exactly-once processing semantics at API boundaries.

## Testing Strategy

### Layer 1: Unit Tests
- JUnit 5 + Mockito
- Validates service logic deterministically (auth flows, transfer guards, fraud scoring)

### Layer 2: Integration Tests
- Testcontainers for PostgreSQL, Redis, Kafka
- Verifies real DB queries, race conditions, and message flow against actual infrastructure

### Layer 3: Performance and Quality Gates
- Gatling load tests for concurrency and SLA behavior
- JaCoCo enforces minimum 75% coverage in CI
- GitHub Actions blocks merge on failing tests/coverage gates and publishes Docker image on `main`

## Resume Highlights (FinCore)

- Engineered JWT session security with refresh-token rotation in HttpOnly cookies and Redis-backed session indexing; detected token replay and auto-revoked all active sessions, eliminating stolen-refresh-token persistence risk in 7-day windows.
- Built race-safe transfer idempotency using Redis `SETNX` locks plus 24-hour response caching (`idempotency:{userId}:{uuid}`), preventing duplicate debit execution under client retries and concurrent request collisions.
- Implemented event-driven transaction processing on Kafka with dedicated Audit, Notification, and Fraud consumers plus a Dead Letter Topic workflow; isolated downstream failures without blocking primary transfer commits.
- Developed a Chain-of-Responsibility fraud engine with 6 weighted rules (velocity, threshold, beneficiary novelty, time anomaly, round amount, rapid sequential), capping risk at 100 and auto-locking accounts at >=80 risk.
- Delivered Spring Batch operations for monthly statement generation, dormant account detection, and stale pending transaction cleanup; used chunk-oriented processing and streaming PDF output to control memory on high-volume account sets.
- Designed observability end-to-end with custom Micrometer metrics, Prometheus/Grafana dashboards, Zipkin tracing, and MDC-enriched JSON logs; cut root-cause triage time via trace-correlated transaction logging.
- Established a multi-layer quality pipeline using Mockito unit tests, Testcontainers integration tests (PostgreSQL/Redis/Kafka), Gatling performance tests, and JaCoCo gates; enforced a 75% coverage quality threshold in CI.
- Standardized external API contracts with `/api/v1` + `/api/v2` versioning, media-type negotiation via `Accept`, OpenAPI/Swagger documentation, and RFC 7807 Problem Details, improving client compatibility and error diagnosability.
