# Enterprise Microservice Platform — Requirements Specification

**Version:** 1.0
**Project Type:** Cloud-Native Microservices Platform (Learning)
**Status:** Draft — reference document for implementation

---

# 0. How to Use This Document

This is the **functional and non-functional requirements** reference for the platform. It answers *what* the system must do and *how well* it must do it. It complements the existing technical docs, which answer *how* to build it:

- Architecture, patterns, tech stack → `docs/architecture.md`, `Agents.md`
- REST contracts → `docs/api-guidelines.md`, `docs/openapi-style-guide.md`
- Events → `docs/events.md`
- Persistence → `docs/database.md`
- Security → `docs/security.md`
- Observability → `docs/observability.md`
- Deployment → `docs/deployment.md`

Each requirement has a stable ID (e.g. `FR-ORD-03`). Reference these IDs in commits, PRs, tests, and issues. **When implementing a feature, satisfy the requirement AND its acceptance criteria — do not consider a task done until the criteria pass** (see `Agents.md` → Definition of Done).

**Requirement keywords** follow RFC 2119: **MUST** (mandatory), **SHOULD** (recommended), **MAY** (optional).

---

# 1. Scope & Goals

## 1.1 In Scope
- A distributed e-commerce backend demonstrating enterprise patterns end to end.
- Ten independently deployable services (see §3).
- Synchronous REST for queries/commands and asynchronous Kafka for domain events.
- Full cross-cutting concerns: security, observability, resilience, CI/CD.

## 1.2 Out of Scope
- A production storefront UI (a thin web/mobile client MAY be added for demos only).
- Real money movement — the Payment Service integrates with a **sandbox/mock** provider only.
- Multi-region / multi-tenant deployment.
- Advanced ML/predictive analytics (Analytics Service produces aggregates only; downstream Databricks integration is a stretch goal).

## 1.3 Learning Objectives (Project Goals)
- **G1** Practice Domain-Driven Design and database-per-service isolation.
- **G2** Implement reliable event-driven workflows (Saga / outbox / idempotency).
- **G3** Achieve production-grade observability (metrics, traces, structured logs).
- **G4** Enforce security by default (OAuth2/OIDC, JWT, RBAC).
- **G5** Deliver a full cloud-native delivery pipeline (Docker → Helm → ArgoCD).

---

# 2. Actors & Personas

| ID | Actor | Description |
|----|-------|-------------|
| A1 | Customer | End user who browses products and places orders. |
| A2 | Guest | Unauthenticated visitor; may browse the catalog only. |
| A3 | Admin / Ops | Manages catalog, inventory, and reviews operational dashboards. |
| A4 | System (Service) | A service acting on behalf of a workflow (e.g. Order Service calling Payment). |
| A5 | External Payment Provider | Sandbox gateway that authorizes/captures payments. |
| A6 | External Courier | Sandbox shipping/tracking provider. |

---

# 3. Service Inventory & Ownership

Each service is independently deployable, owns exactly one database, and never reads another service's data store (`Agents.md` → Database Rules).

| Service | Bounded Context | Data Store | Primary Sync API | Publishes | Consumes |
|---------|-----------------|-----------|------------------|-----------|----------|
| gateway | Edge routing | — | all `/api/v1/**` | — | — |
| discovery | Service registry | — | — | — | — |
| auth (Keycloak) | Identity | Keycloak DB | OIDC endpoints | — | — |
| customer | Customer profile | PostgreSQL | `/api/v1/customers` | `customer.*` | — |
| product | Catalog | PostgreSQL + Redis | `/api/v1/products` | `product.*` | — |
| order | Order lifecycle | PostgreSQL | `/api/v1/orders` | `order.*` | `payment.*`, `inventory.*`, `shipment.*` |
| inventory | Stock | PostgreSQL | `/api/v1/inventory` | `inventory.*` | `order.*` |
| payment | Payments | PostgreSQL | `/api/v1/payments` | `payment.*` | `order.*` |
| shipping | Fulfillment | PostgreSQL | `/api/v1/shipments` | `shipment.*` | `payment.*` |
| notification | Messaging | PostgreSQL | `/api/v1/notifications` | — | `order.*`, `payment.*`, `shipment.*` |
| analytics | BI aggregation | PostgreSQL (OLAP-style) | `/api/v1/analytics` | — | all `*.*` |

---

# 4. Functional Requirements

Legend for tables: **Pri** = priority (H/M/L).

## 4.1 Gateway Service (`FR-GW`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-GW-01 | The gateway MUST be the single external entry point; internal services MUST NOT be directly reachable from outside the cluster. | H |
| FR-GW-02 | The gateway MUST route requests to services by path prefix using discovery (no hardcoded hosts). | H |
| FR-GW-03 | The gateway MUST validate the JWT (signature, issuer, expiry) before routing protected requests and reject invalid tokens with `401`. | H |
| FR-GW-04 | The gateway MUST propagate/originate a correlation ID (`traceId`) on every request. | H |
| FR-GW-05 | The gateway MUST apply per-client rate limiting and return `429` when exceeded. | M |
| FR-GW-06 | The gateway SHOULD apply a circuit breaker / timeout per downstream route and return `503` on open circuit. | M |

## 4.2 Discovery Service (`FR-DISC`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-DISC-01 | Every service MUST self-register on startup and send heartbeats. | H |
| FR-DISC-02 | The registry MUST deregister instances that stop heartbeating within the configured lease window. | H |
| FR-DISC-03 | Consumers MUST resolve service instances via discovery, never via static IPs. | H |

## 4.3 Auth / Identity (`FR-AUTH`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-AUTH-01 | The platform MUST use Keycloak as the OAuth2/OIDC authorization server. | H |
| FR-AUTH-02 | Clients MUST obtain a JWT via the OIDC flow; services MUST validate it as a resource server. | H |
| FR-AUTH-03 | The system MUST define roles at minimum: `CUSTOMER`, `ADMIN`, `SERVICE`. | H |
| FR-AUTH-04 | Tokens MUST carry the subject (customer id) and roles used for RBAC. | H |
| FR-AUTH-05 | Service-to-service calls MUST use a service account / client-credentials token, not a user token. | M |

## 4.4 Customer Service (`FR-CUST`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-CUST-01 | The system MUST register a new customer profile (name, email, phone). Email MUST be unique. | H |
| FR-CUST-02 | A customer MUST be able to retrieve and update their own profile. | H |
| FR-CUST-03 | A customer MUST be able to manage multiple delivery addresses (add, update, delete, set default). | H |
| FR-CUST-04 | A customer MUST only access their own data; `ADMIN` MAY access any (RBAC enforced). | H |
| FR-CUST-05 | On successful registration the service MUST publish `customer.registered`. | M |
| FR-CUST-06 | The service SHOULD store loyalty/preference data as an extensible attribute set. | L |

## 4.5 Product Service (`FR-PROD`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-PROD-01 | The system MUST list products with pagination, filtering (category, price range), and sorting. | H |
| FR-PROD-02 | The system MUST return full product detail (description, price, category, specs, images). | H |
| FR-PROD-03 | `ADMIN` MUST be able to create, update, and deactivate products. | H |
| FR-PROD-04 | Product reads SHOULD be served from a Redis cache with a defined TTL and cache-invalidation on update. | M |
| FR-PROD-05 | The catalog MUST expose availability status but MUST NOT own stock counts (inventory is authoritative). | H |
| FR-PROD-06 | Product changes MUST publish `product.created` / `product.updated`. | M |

## 4.6 Order Service (`FR-ORD`) — Core Orchestrator

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-ORD-01 | A customer MUST place an order containing one or more line items (product + quantity). | H |
| FR-ORD-02 | The service MUST validate that each product exists and the requested quantity is positive. | H |
| FR-ORD-03 | On order placement the service MUST publish `order.created` and set status `PENDING`. | H |
| FR-ORD-04 | The service MUST orchestrate the order Saga across inventory → payment → shipping (see §6). | H |
| FR-ORD-05 | The service MUST maintain an order state machine: `PENDING → CONFIRMED → PAID → SHIPPED → DELIVERED`, plus `CANCELLED` and `FAILED`. Illegal transitions MUST be rejected. | H |
| FR-ORD-06 | The service MUST compensate (cancel + release reservations + refund) when any Saga step fails. | H |
| FR-ORD-07 | A customer MUST retrieve their orders and a single order's status/history. | H |
| FR-ORD-08 | A customer MAY cancel an order only while it is in `PENDING` or `CONFIRMED`. | M |
| FR-ORD-09 | The service MUST use the transactional outbox pattern so state changes and event publication are atomic. | H |
| FR-ORD-10 | Order totals MUST be computed server-side from product prices at order time (never trust client-supplied prices). | H |

## 4.7 Inventory Service (`FR-INV`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-INV-01 | The service MUST track available and reserved stock per product per warehouse. | H |
| FR-INV-02 | On `order.created` the service MUST attempt to reserve stock and publish `inventory.reserved` or `inventory.reservation-failed`. | H |
| FR-INV-03 | Stock reservation MUST be atomic and MUST NOT allow available stock to go negative (handle concurrent orders). | H |
| FR-INV-04 | On order cancellation/failure the service MUST release the reservation (`inventory.released`). | H |
| FR-INV-05 | On successful order completion the service MUST convert reservation to a permanent decrement. | H |
| FR-INV-06 | `ADMIN` MUST be able to adjust stock levels (restock, correction) with an audit trail. | M |
| FR-INV-07 | Event consumption MUST be idempotent (duplicate `order.created` MUST NOT double-reserve). | H |

## 4.8 Payment Service (`FR-PAY`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-PAY-01 | On `inventory.reserved` (or order confirmation) the service MUST initiate payment against the sandbox provider. | H |
| FR-PAY-02 | The service MUST record transaction metadata and status (`INITIATED`, `AUTHORIZED`, `CAPTURED`, `FAILED`, `REFUNDED`). | H |
| FR-PAY-03 | The service MUST publish `payment.completed` or `payment.failed`. | H |
| FR-PAY-04 | The service MUST NOT persist raw card / sensitive payment data (store provider token/reference only). | H |
| FR-PAY-05 | The service MUST support a refund on compensation (`payment.refunded`). | H |
| FR-PAY-06 | Payment initiation MUST be idempotent per order (retries MUST NOT double-charge). | H |
| FR-PAY-07 | The service SHOULD reconcile provider webhooks/callbacks against local transaction state. | M |

## 4.9 Shipping Service (`FR-SHIP`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-SHIP-01 | On `payment.completed` the service MUST create a shipment and assign a courier. | H |
| FR-SHIP-02 | The service MUST publish `shipment.created` and subsequent `shipment.status-changed` events. | H |
| FR-SHIP-03 | The service MUST expose tracking information (tracking number, current status, ETA). | H |
| FR-SHIP-04 | The service MUST maintain a shipment lifecycle: `CREATED → DISPATCHED → IN_TRANSIT → DELIVERED` (+ `FAILED_DELIVERY`). | H |
| FR-SHIP-05 | On delivery the service MUST publish `shipment.delivered` (drives order → `DELIVERED`). | H |

## 4.10 Notification Service (`FR-NOTIF`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-NOTIF-01 | The service MUST subscribe to `order.*`, `payment.*`, `shipment.*` and send the matching notification. | H |
| FR-NOTIF-02 | The service MUST support at least email; SMS/push MAY be added behind a channel abstraction. | H |
| FR-NOTIF-03 | The service MUST be fully decoupled — business services MUST NOT call it synchronously. | H |
| FR-NOTIF-04 | Delivery MUST be idempotent (a duplicate event MUST NOT send a duplicate notification). | H |
| FR-NOTIF-05 | The service SHOULD record notification history/status and support retry on transient failure. | M |
| FR-NOTIF-06 | The service SHOULD support user notification preferences/opt-out. | L |

## 4.11 Analytics Service (`FR-ANLY`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-ANLY-01 | The service MUST consume all domain events and persist them for aggregation without affecting transactional workloads. | H |
| FR-ANLY-02 | The service MUST compute operational aggregates (orders/day, revenue, top products, conversion). | H |
| FR-ANLY-03 | The service MUST expose read-only aggregate/report endpoints for `ADMIN`. | H |
| FR-ANLY-04 | Consumption MUST be idempotent and MUST tolerate out-of-order events. | H |
| FR-ANLY-05 | The service MAY export processed data to an external platform (Databricks) — stretch goal. | L |

---

# 5. Domain Events (Contract Summary)

Every event MUST carry the standard envelope (`Agents.md` → Event Rules): `eventId`, `eventType`, `eventVersion`, `occurredAt`, `source`, `traceId`, `payload`. Topic naming is `domain.event`. See `docs/events.md` for full schemas.

| Topic | Producer | Key Consumers | Trigger |
|-------|----------|---------------|---------|
| `customer.registered` | customer | notification, analytics | New customer |
| `product.created` / `product.updated` | product | analytics | Catalog change |
| `order.created` | order | inventory, notification, analytics | Order placed |
| `order.cancelled` / `order.completed` | order | inventory, payment, notification, analytics | State change |
| `inventory.reserved` | inventory | payment, order, analytics | Stock reserved |
| `inventory.reservation-failed` | inventory | order, analytics | Insufficient stock |
| `inventory.released` | inventory | order, analytics | Compensation |
| `payment.completed` | payment | shipping, order, notification, analytics | Payment captured |
| `payment.failed` | payment | order, notification, analytics | Payment declined |
| `payment.refunded` | payment | order, notification, analytics | Compensation |
| `shipment.created` | shipping | order, notification, analytics | Shipment created |
| `shipment.status-changed` | shipping | notification, analytics | Tracking update |
| `shipment.delivered` | shipping | order, notification, analytics | Delivered |

**Cross-cutting event rules:** consumers MUST be idempotent (dedupe on `eventId`); producers MUST use the outbox pattern; schema changes MUST be backward-compatible or bump `eventVersion`.

---

# 6. Key End-to-End Flows

## 6.1 Happy Path — Place Order (Saga)
1. Customer `POST /api/v1/orders` → Order Service validates, persists `PENDING`, publishes `order.created` (via outbox).
2. Inventory reserves stock → `inventory.reserved`.
3. Payment charges sandbox → `payment.completed`; Order → `PAID`.
4. Shipping creates shipment → `shipment.created`; Order → `SHIPPED`.
5. Notification emails the customer at each milestone.
6. Analytics records every event.
7. `shipment.delivered` → Order → `DELIVERED`.

## 6.2 Compensation — Payment Fails
1. `order.created` → `inventory.reserved`.
2. Payment declines → `payment.failed`.
3. Order Service transitions order → `FAILED`, publishes `order.cancelled`.
4. Inventory releases reservation (`inventory.released`).
5. Notification informs the customer of failure.

## 6.3 Compensation — Out of Stock
1. `order.created` → `inventory.reservation-failed`.
2. Order Service transitions order → `FAILED`; no payment is attempted.
3. Notification informs the customer.

> **Acceptance for §6:** the system MUST reach a consistent terminal state (`DELIVERED`, `CANCELLED`, or `FAILED`) for every order, with no orphaned reservations or charges, even when a step is retried or a duplicate event is delivered.

---

# 7. Non-Functional Requirements (`NFR`)

## 7.1 Performance & Scalability
| ID | Requirement |
|----|-------------|
| NFR-PERF-01 | Read APIs (catalog, order status) SHOULD respond < 300 ms p95 under nominal load. |
| NFR-PERF-02 | Services MUST be horizontally scalable and stateless (support HPA). |
| NFR-PERF-03 | List endpoints MUST paginate; N+1 queries and `SELECT *` are prohibited (`Agents.md` → Performance). |
| NFR-PERF-04 | Product reads SHOULD use caching to reduce DB load. |

## 7.2 Reliability & Resilience
| ID | Requirement |
|----|-------------|
| NFR-REL-01 | No distributed DB transactions; consistency MUST be achieved via Saga + compensation. |
| NFR-REL-02 | All event consumers MUST be idempotent. |
| NFR-REL-03 | Inter-service sync calls MUST use timeouts, retries (with backoff), and circuit breakers. |
| NFR-REL-04 | Failed event processing MUST route to a dead-letter topic after max retries. |
| NFR-REL-05 | Services MUST start and recover without manual intervention (self-registration, health probes). |

## 7.3 Security
| ID | Requirement |
|----|-------------|
| NFR-SEC-01 | All external endpoints except health and explicitly public catalog reads MUST require authentication. |
| NFR-SEC-02 | Authorization MUST be enforced via RBAC on every protected endpoint. |
| NFR-SEC-03 | Secrets MUST come from config/secret stores — never hardcoded or committed. |
| NFR-SEC-04 | Logs MUST NOT contain passwords, JWTs, card data, or secrets. |
| NFR-SEC-05 | All input MUST be validated (Jakarta Bean Validation); errors MUST NOT leak stack traces. |
| NFR-SEC-06 | Transport MUST be TLS in non-local environments. See `docs/security.md`. |

## 7.4 Observability
| ID | Requirement |
|----|-------------|
| NFR-OBS-01 | Every service MUST expose `/actuator/health` and `/actuator/prometheus`. |
| NFR-OBS-02 | Every request/event MUST be traceable end-to-end via a propagated `traceId`. |
| NFR-OBS-03 | Logs MUST be structured and include trace/span/correlation IDs. |
| NFR-OBS-04 | Business and technical metrics MUST be emitted (order rate, payment success rate, consumer lag). |
| NFR-OBS-05 | Readiness and liveness probes MUST be configured. See `docs/observability.md`. |

## 7.5 Maintainability & Portability
| ID | Requirement |
|----|-------------|
| NFR-MNT-01 | Every service MUST follow the standard package layout (`Agents.md` → Package Structure). |
| NFR-MNT-02 | Services MUST NOT share entity classes or databases. |
| NFR-MNT-03 | Schema changes MUST be applied via Flyway migrations. |
| NFR-MNT-04 | Each service MUST ship a Dockerfile (non-root) and Helm chart. |
| NFR-MNT-05 | The platform MUST follow Twelve-Factor config (externalized config, env parity). |

## 7.6 Testability & Quality
| ID | Requirement |
|----|-------------|
| NFR-TST-01 | Every feature MUST have unit tests (JUnit 5 + Mockito). |
| NFR-TST-02 | Every service MUST have integration tests using Testcontainers (real Postgres/Kafka). |
| NFR-TST-03 | Saga flows and compensation paths MUST have integration coverage. |
| NFR-TST-04 | Bug fixes MUST add a regression test. |

---

# 8. Data Ownership Rules (Summary)

- Each service owns exactly one PostgreSQL database (`docs/database.md`).
- No cross-service joins, shared schemas, or foreign keys across service boundaries.
- References across contexts are by **ID only** (e.g. Order stores `customerId`, `productId`, not the entities).
- Cross-service data needs are met by REST query or by consuming events into a local read model.

---

# 9. Assumptions & Constraints

- **AS-1** Payment and courier providers are sandbox/mock; no real funds or logistics.
- **AS-2** A single Kafka cluster and single Postgres server (one DB per service) are sufficient for the learning environment.
- **AS-3** Local development runs via Docker Compose; target deployment is Kubernetes + Helm + ArgoCD.
- **AS-4** Tech stack is fixed per `Agents.md` (Java 21, Spring Boot 3.5, Spring Cloud, Kafka, Redis, Keycloak).
- **CN-1** No architectural pattern outside those in `Agents.md` may be introduced without an explicit decision.

---

# 10. Milestones (Suggested Build Order)

Ordered to keep each step independently verifiable.

| # | Milestone | Delivers | Key Requirements |
|---|-----------|----------|------------------|
| M0 | Platform skeleton | discovery, gateway, Keycloak, one hello service through the gateway | FR-DISC-*, FR-GW-*, FR-AUTH-* |
| M1 | Catalog & customers | product + customer CRUD, caching, auth/RBAC | FR-PROD-*, FR-CUST-* |
| M2 | Order + inventory happy path | place order → reserve stock via events + outbox + idempotency | FR-ORD-01..05,09,10, FR-INV-* |
| M3 | Payment + shipping | complete the Saga incl. compensation | FR-PAY-*, FR-SHIP-*, FR-ORD-06 |
| M4 | Notification & analytics | event-driven side effects and reporting | FR-NOTIF-*, FR-ANLY-* |
| M5 | Cross-cutting hardening | full observability, resilience, DLQ, tracing | all NFRs |
| M6 | Delivery pipeline | Docker → Helm → GitHub Actions → ArgoCD | NFR-MNT-04, deployment.md |

---

# 11. Glossary

| Term | Meaning |
|------|---------|
| Saga | Sequence of local transactions coordinated via events, with compensating actions on failure. |
| Outbox | Pattern where events are written to a DB table in the same transaction as state, then relayed to Kafka. |
| Idempotency | Processing the same message more than once yields the same result. |
| Compensation | An action that undoes a prior successful step when a later step fails (release stock, refund). |
| RBAC | Role-Based Access Control. |
| DLQ | Dead-Letter Queue/Topic for messages that fail processing repeatedly. |
| Bounded Context | A DDD boundary within which a domain model and its terms are consistent. |

---

*This document is the authoritative source for **what** the platform must do. When implementation and this document conflict, update whichever is wrong per the change's intent — and keep requirement IDs stable.*
