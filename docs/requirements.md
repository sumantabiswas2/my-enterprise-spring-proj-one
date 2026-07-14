# Enterprise Microservice Platform — Requirements Specification

**Version:** 1.0
**Project Type:** Cloud-Native Microservices Platform (Learning)
**Status:** Draft — reference document for implementation

---

# 0. How to Use This Document

This is the **functional and non-functional requirements** reference for the platform. It answers *what* the system must do and *how well* it must do it. It complements the existing technical docs, which answer *how* to build it:

- Architecture, patterns, tech stack → `docs/architecture.md`, `AGENTS.md`
- REST contracts → `docs/api-guidelines.md`, `docs/openapi-style-guide.md`
- Events → `docs/events.md`, `docs/asyncapi-style-guide.md`
- Persistence → `docs/database.md`, `AGENTS.md` Database Rules
- Security → `docs/security.md`
- Observability → `docs/observability.md`
- Deployment → `docs/deployment.md`, `AGENTS.md` Docker/Kubernetes/Helm/CI-CD rules

Each requirement has a stable ID (e.g. `FR-ORD-03`). Reference these IDs in commits, tests, and issues. The normative requirement statement, the cross-cutting rules that apply to it, and the acceptance scenarios in §6.6 together form its acceptance criteria. **Do not consider a task done until the applicable criteria pass** (see `AGENTS.md` → Definition of Done).

**Requirement keywords** follow RFC 2119: **MUST** (mandatory), **SHOULD** (recommended), **MAY** (optional).

---

# 1. Scope & Goals

## 1.1 In Scope
- A distributed e-commerce backend demonstrating enterprise patterns end to end.
- Ten independently deployable Spring services plus a separately deployed Keycloak identity provider (see §3).
- Synchronous REST for queries/commands and asynchronous Kafka for domain events.
- Full cross-cutting concerns: security, observability, resilience, CI/CD.
- A fully local reference environment; no paid cloud account or externally hosted runtime is required.

## 1.2 Out of Scope
- A production storefront UI (a thin web/mobile client MAY be added for demos only).
- Real money movement — the Payment Service integrates with a **sandbox/mock** provider only.
- Multi-region / multi-tenant deployment.
- Production cloud deployment or real external infrastructure; Kubernetes, GitOps, payment, courier, email, and CI/CD integrations are demonstrated locally or with mocks.
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

Each Spring service is independently deployable and never reads another service's data store (`AGENTS.md` → Database Rules). Business services own one PostgreSQL database each. Gateway and Discovery are stateless infrastructure services; Keycloak owns its own identity database.

| Service | Bounded Context | Data Store | Primary Sync API | Publishes | Consumes |
|---------|-----------------|-----------|------------------|-----------|----------|
| gateway | Edge routing | — | all `/api/v1/**` | — | — |
| discovery | Service registry | — | — | — | — |
| auth (Keycloak, platform component) | Identity | Keycloak DB | OIDC endpoints | — | — |
| customer | Customer profile | PostgreSQL | `/api/v1/customers` | `customer.*` | — |
| product | Catalog | PostgreSQL + Redis | `/api/v1/products` | `product.*` | `inventory.availability-changed` |
| order | Order lifecycle / Saga coordination | PostgreSQL | `/api/v1/orders` | `order.*`, `payment.refund-requested` | `inventory.*`, `payment.*`, `shipment.*` |
| inventory | Stock | PostgreSQL | `/api/v1/inventory` | `inventory.*` | `order.*` |
| payment | Payments | PostgreSQL | `/api/v1/payments` | `payment.*` | `order.confirmed`, `payment.refund-requested` |
| shipping | Fulfillment | PostgreSQL | `/api/v1/shipments` | `shipment.*` | `order.paid` |
| notification | Messaging | PostgreSQL | `/api/v1/notifications` | — | `order.*`, `payment.*`, `shipment.*` |
| analytics | BI aggregation | PostgreSQL (OLAP-style) | `/api/v1/analytics` | — | Explicit allow-list of domain topics in §5 |

---

# 4. Functional Requirements

Legend for tables: **Pri** = priority (H/M/L).

## 4.1 Gateway Service (`FR-GW`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-GW-01 | The gateway MUST be the single external entry point; internal services MUST NOT be directly reachable from outside the cluster. | H |
| FR-GW-02 | The gateway MUST route requests to services by path prefix using discovery (no hardcoded hosts). | H |
| FR-GW-03 | The gateway MUST validate the JWT (signature, issuer, expiry) before routing protected requests and reject invalid tokens with `401`. | H |
| FR-GW-04 | The gateway MUST propagate or originate `X-Correlation-Id` and W3C trace context on every request. Correlation ID and trace ID are distinct values and both MUST be available to downstream logs. | H |
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
| FR-AUTH-04 | Tokens MUST carry an immutable identity subject (`sub`) and roles used for RBAC. Customer Service owns the mapping from that subject to its customer profile ID. | H |
| FR-AUTH-05 | Service-to-service calls MUST use a service account / client-credentials token, not a user token. | M |
| FR-AUTH-06 | Keycloak MUST own account registration, credentials, and account-email uniqueness. Customer Service MUST provision or update a profile only for an authenticated Keycloak subject and MUST compensate or surface a recoverable provisioning failure. | H |

## 4.4 Customer Service (`FR-CUST`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-CUST-01 | The system MUST create a customer profile for an authenticated Keycloak subject (name, email, phone). The Keycloak subject is the identity key; Customer Service enforces one profile per subject and maintains its local email uniqueness constraint. | H |
| FR-CUST-02 | A customer MUST be able to retrieve and update their own profile. | H |
| FR-CUST-03 | A customer MUST be able to manage multiple delivery addresses (add, update, delete, set default). | H |
| FR-CUST-04 | A customer MUST only access their own data; `ADMIN` MAY access any (RBAC enforced). | H |
| FR-CUST-05 | On successful registration the service MUST publish `customer.registered`. | M |
| FR-CUST-06 | The service SHOULD store loyalty/preference data as an extensible attribute set. | L |
| FR-CUST-07 | The service MUST map the immutable Keycloak subject (`sub`) to exactly one customer profile; it MUST NOT assume the Keycloak subject equals the customer database primary key. | H |
| FR-CUST-08 | On notification-preference changes, Customer Service MUST publish `customer.notification-preferences-updated`. Notification Service MAY retrieve the minimal current notification profile through an authorized Customer API when it processes an event. | M |

## 4.5 Product Service (`FR-PROD`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-PROD-01 | The system MUST list products with pagination, filtering (category, price range), and sorting. | H |
| FR-PROD-02 | The system MUST return full product detail (description, price, category, specs, images). | H |
| FR-PROD-03 | `ADMIN` MUST be able to create, update, and deactivate products. | H |
| FR-PROD-04 | Product reads SHOULD be served from a Redis cache with a defined TTL and cache-invalidation on update. | M |
| FR-PROD-05 | The catalog MUST expose availability status but MUST NOT own stock counts (inventory is authoritative). | H |
| FR-PROD-06 | Product changes MUST publish `product.created` / `product.updated`. | M |
| FR-PROD-07 | Availability displayed by the catalog MUST come from an event-maintained local read model populated from `inventory.availability-changed`; Product MUST NOT read the Inventory database. | H |

## 4.6 Order Service (`FR-ORD`) — Core Orchestrator

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-ORD-01 | A customer MUST place an order containing one or more line items (product + quantity). | H |
| FR-ORD-02 | The service MUST validate that each product exists and the requested quantity is positive. | H |
| FR-ORD-03 | On order placement the service MUST publish `order.created` and set status `PENDING`. | H |
| FR-ORD-04 | The service MUST coordinate the event-driven Order Saga across inventory → payment → shipping by consuming outcome events and publishing the next order lifecycle fact (see §6). | H |
| FR-ORD-05 | The service MUST maintain an order state machine: `PENDING → CONFIRMED → PAID → SHIPPED → DELIVERED`, plus terminal `CANCELLED` and `FAILED`. `inventory.reserved` causes `CONFIRMED`; `payment.completed` causes `PAID`; `shipment.created` causes `SHIPPED`; `shipment.delivered` causes `DELIVERED`; a non-recoverable Saga or delivery failure causes `FAILED`. Illegal transitions MUST be rejected. | H |
| FR-ORD-06 | The service MUST initiate the applicable compensations when a Saga step fails: release an active reservation and, if payment was captured, request an idempotent refund. A system failure MUST publish `order.failed`; `order.cancelled` is reserved for an accepted cancellation request. | H |
| FR-ORD-07 | A customer MUST retrieve their orders and a single order's status/history. | H |
| FR-ORD-08 | A customer MUST be able to request cancellation while an order is `PENDING` or `CONFIRMED`; cancellation in any later or terminal state MUST be rejected with a business-conflict response. | M |
| FR-ORD-09 | The service MUST use the transactional outbox pattern so state changes and event publication are atomic. | H |
| FR-ORD-10 | Order totals MUST be computed server-side from product prices at order time (never trust client-supplied prices). | H |
| FR-ORD-11 | On transition to `DELIVERED`, the service MUST publish `order.completed` exactly once. | H |

## 4.7 Inventory Service (`FR-INV`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-INV-01 | The service MUST track available and reserved stock per product per warehouse. | H |
| FR-INV-02 | On `order.created` the service MUST attempt to reserve stock and publish `inventory.reserved` or `inventory.reservation-failed`. | H |
| FR-INV-03 | Stock reservation MUST be atomic and MUST NOT allow available stock to go negative (handle concurrent orders). | H |
| FR-INV-04 | On order cancellation/failure the service MUST release the reservation (`inventory.released`). | H |
| FR-INV-05 | On `shipment.created` the service MUST convert the reservation to a permanent stock decrement. If shipment creation fails first, it MUST release the reservation. | H |
| FR-INV-06 | `ADMIN` MUST be able to adjust stock levels (restock, correction) with an audit trail. | M |
| FR-INV-07 | Event consumption MUST be idempotent (duplicate `order.created` MUST NOT double-reserve). | H |
| FR-INV-08 | Inventory MUST tolerate out-of-order order lifecycle events. Order lifecycle payloads MUST carry a monotonically increasing `orderVersion`; Inventory MUST persist terminal cancellation/failure state and MUST ignore a later `order.created` with an equal or lower version. | H |

## 4.8 Payment Service (`FR-PAY`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-PAY-01 | On `order.confirmed` the service MUST initiate payment against the sandbox provider. | H |
| FR-PAY-02 | The service MUST record transaction metadata and status (`INITIATED`, `AUTHORIZED`, `CAPTURED`, `FAILED`, `REFUNDED`). | H |
| FR-PAY-03 | The service MUST publish `payment.completed` or `payment.failed`. | H |
| FR-PAY-04 | The service MUST NOT persist raw card / sensitive payment data (store provider token/reference only). | H |
| FR-PAY-05 | The service MUST support a refund on compensation (`payment.refunded`). | H |
| FR-PAY-06 | Payment initiation MUST be idempotent per order (retries MUST NOT double-charge). | H |
| FR-PAY-07 | The service SHOULD reconcile provider webhooks/callbacks against local transaction state. | M |
| FR-PAY-08 | On `payment.refund-requested` the service MUST perform or resume one idempotent refund and publish `payment.refunded` or `payment.refund-failed`. | H |

## 4.9 Shipping Service (`FR-SHIP`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-SHIP-01 | On `order.paid` the service MUST create a shipment and assign a courier. | H |
| FR-SHIP-02 | The service MUST publish `shipment.created` and subsequent `shipment.status-changed` events. | H |
| FR-SHIP-03 | The service MUST expose tracking information (tracking number, current status, ETA). | H |
| FR-SHIP-04 | The service MUST maintain a shipment lifecycle: `CREATED → DISPATCHED → IN_TRANSIT → DELIVERED` (+ `FAILED_DELIVERY`). | H |
| FR-SHIP-05 | On delivery the service MUST publish `shipment.delivered` (drives order → `DELIVERED`). | H |
| FR-SHIP-06 | If shipment creation fails, the service MUST publish `shipment.creation-failed`; delivery failure after dispatch MUST publish `shipment.delivery-failed` for operational resolution. | H |

## 4.10 Notification Service (`FR-NOTIF`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-NOTIF-01 | The service MUST subscribe to the notification-relevant customer, order, payment, and shipment topics explicitly listed in §5 and send the matching notification. | H |
| FR-NOTIF-02 | The service MUST support at least email; SMS/push MAY be added behind a channel abstraction. | H |
| FR-NOTIF-03 | The service MUST be fully decoupled — business services MUST NOT call it synchronously. | H |
| FR-NOTIF-04 | Delivery MUST be idempotent (a duplicate event MUST NOT send a duplicate notification). | H |
| FR-NOTIF-05 | The service SHOULD record notification history/status and support retry on transient failure. | M |
| FR-NOTIF-06 | The service SHOULD support user notification preferences/opt-out. | L |

## 4.11 Analytics Service (`FR-ANLY`)

| ID | Requirement | Pri |
|----|-------------|-----|
| FR-ANLY-01 | The service MUST consume the explicit allow-list of domain events in §5 and persist them for aggregation without affecting transactional workloads. | H |
| FR-ANLY-02 | The service MUST compute operational aggregates (orders/day, revenue, payment success rate, and top products). Conversion analytics is out of scope until browse/session events are introduced. | H |
| FR-ANLY-03 | The service MUST expose read-only aggregate/report endpoints for `ADMIN`. | H |
| FR-ANLY-04 | Consumption MUST be idempotent and MUST tolerate out-of-order events. | H |
| FR-ANLY-05 | The service MAY export processed data to an external platform (Databricks) — stretch goal. | L |

---

# 5. Domain Events (Contract Summary)

Every event MUST carry the standard envelope (`AGENTS.md` → Event Rules): `eventId`, `eventType`, `eventVersion`, `occurredAt`, `source`, `traceId`, `payload`. Topic naming is `domain.event`. `docs/events.md` MUST define the full schema and operational policy for every topic below before that event is implemented.

| Topic | Producer | Key Consumers | Trigger |
|-------|----------|---------------|---------|
| `customer.registered` | customer | notification, analytics | New customer profile |
| `customer.notification-preferences-updated` | customer | notification, analytics | Notification preference changed |
| `product.created` / `product.updated` | product | analytics | Catalog change |
| `order.created` | order | inventory, notification, analytics | Order placed |
| `order.confirmed` | order | payment, notification, analytics | Stock reservation accepted |
| `order.paid` | order | shipping, notification, analytics | Payment captured |
| `order.cancelled` | order | inventory, notification, analytics | Customer/admin cancellation accepted |
| `order.failed` | order | inventory, notification, analytics | Saga failed |
| `order.completed` | order | notification, analytics | Order delivered and closed |
| `inventory.reserved` | inventory | order, analytics | Stock reserved |
| `inventory.reservation-failed` | inventory | order, analytics | Insufficient stock |
| `inventory.released` | inventory | order, analytics | Compensation |
| `inventory.availability-changed` | inventory | product, analytics | Availability read model changed |
| `payment.completed` | payment | order, notification, analytics | Payment captured |
| `payment.failed` | payment | order, notification, analytics | Payment declined |
| `payment.refunded` | payment | order, notification, analytics | Compensation |
| `payment.refund-failed` | payment | order, notification, analytics | Refund requires retry or intervention |
| `payment.refund-requested` | order | payment, analytics | Compensation command requested by Saga coordinator |
| `shipment.created` | shipping | order, notification, analytics | Shipment created |
| `shipment.creation-failed` | shipping | order, notification, analytics | Shipment could not be created |
| `shipment.status-changed` | shipping | notification, analytics | Tracking update |
| `shipment.delivered` | shipping | order, notification, analytics | Delivered |
| `shipment.delivery-failed` | shipping | order, notification, analytics | Delivery requires operational resolution |

**Cross-cutting event rules:** consumers MUST be idempotent (dedupe on `eventId`); producers MUST use the outbox pattern; schema changes MUST be backward-compatible or bump `eventVersion`; each topic MUST specify a business partition key, retry limit, backoff, and consumer-specific DLQ. Kafka does not order records across topics: stateful consumers MUST use the aggregate version and terminal-state rules defined by the owning workflow. Analytics consumes only the explicit topics in this table, not a wildcard subscription. `docs/asyncapi-style-guide.md` defines the full machine-readable schemas; `docs/events.md` defines business semantics and operations.

---

# 6. Key End-to-End Flows

## 6.1 Happy Path — Place Order (Saga)
1. Customer `POST /api/v1/orders` → Order Service validates, persists `PENDING`, publishes `order.created` (via outbox).
2. Inventory reserves stock → `inventory.reserved`.
3. Order consumes `inventory.reserved`, transitions to `CONFIRMED`, and publishes `order.confirmed`.
4. Payment consumes `order.confirmed`, charges the sandbox provider, and publishes `payment.completed`.
5. Order consumes `payment.completed`, transitions to `PAID`, and publishes `order.paid`.
6. Shipping consumes `order.paid`, creates a shipment, and publishes `shipment.created`; Order transitions to `SHIPPED`, and Inventory commits the reservation.
7. Notification emails the customer at applicable milestones; Analytics records every allow-listed event.
8. `shipment.delivered` causes Order to transition to `DELIVERED` and publish `order.completed`.

## 6.2 Compensation — Payment Fails
1. `order.created` → `inventory.reserved`.
2. Order publishes `order.confirmed`; Payment declines → `payment.failed`.
3. Order Service transitions order → `FAILED`, publishes `order.failed`.
4. Inventory releases reservation (`inventory.released`).
5. Notification informs the customer of failure.

## 6.3 Compensation — Out of Stock
1. `order.created` → `inventory.reservation-failed`.
2. Order Service transitions order → `FAILED`, publishes `order.failed`; no payment is attempted.
3. Notification informs the customer.

## 6.4 Compensation — Shipment Creation Fails
1. Payment completes and Order reaches `PAID`.
2. Shipping publishes `shipment.creation-failed` before inventory is committed.
3. Order transitions to `FAILED`, publishes `order.failed` and `payment.refund-requested`.
4. Inventory releases the reservation; Payment publishes `payment.refunded` after the sandbox refund succeeds.
5. A failed refund is retried and then routed to a DLQ with an operational alert; it MUST remain visible as unresolved compensation.

> **Acceptance for §6:** after all messages for a test scenario have been processed, the order MUST reach a consistent terminal state (`DELIVERED`, `CANCELLED`, or `FAILED`). There MUST be no orphaned reservations or untracked charges. Duplicate delivery of any event MUST produce no additional reservation, charge, refund, shipment, or notification. A permanent technical failure MAY require operator action, but it MUST be represented by a DLQ record, alert, and queryable unresolved-compensation status rather than being silently treated as complete.

## 6.5 Customer Cancellation and Payment Race
1. Order atomically transitions an eligible `PENDING` or `CONFIRMED` order to `CANCELLED` and writes `order.cancelled` to its outbox.
2. Inventory records the terminal cancellation state and releases any active reservation. A later or duplicate `order.created` with an equal or lower `orderVersion` is ignored.
3. If a concurrent payment completes after cancellation, Order MUST NOT publish `order.paid`; it publishes `payment.refund-requested` exactly once instead.
4. Shipping is never started for the cancelled order, and Notification reports the final cancellation outcome.

## 6.6 Acceptance and Traceability Rules

Every implemented requirement MUST be linked to one or more automated tests using its requirement ID in the test name, display name, tag, or test documentation. A requirement is accepted only when:

1. Its normative statement and all referenced cross-cutting requirements pass.
2. Its success, validation, authorization, duplicate/retry, and relevant failure paths are tested.
3. Public REST and event contracts match their OpenAPI or AsyncAPI/schema documentation.
4. Observable evidence exists where applicable: structured log fields, metric increments, propagated trace context, and health behavior.
5. The test runs in the local reference environment without a paid or externally hosted dependency.

Minimum end-to-end acceptance scenarios:

| ID | Scenario | Expected Result |
|----|----------|-----------------|
| AC-ORD-01 | Place a valid order with available stock and a successful sandbox payment. | One order reaches `DELIVERED`; stock is committed once; one payment and shipment exist; expected notifications and analytics records exist. |
| AC-ORD-02 | Deliver `order.created` and every downstream event twice. | The outcome matches single delivery; no duplicate reservation, charge, refund, shipment, notification, or aggregate contribution occurs. |
| AC-ORD-03 | Place two concurrent orders for the last unit of stock. | Exactly one reservation succeeds; available stock never becomes negative; the other order reaches `FAILED`. |
| AC-ORD-04 | Make payment fail after inventory reservation. | Order reaches `FAILED`; reservation is released; no shipment is created; the failure notification is recorded. |
| AC-ORD-05 | Make shipment creation fail after payment capture. | Order reaches `FAILED`; inventory is released; exactly one refund completes or an unresolved compensation is visible and alerted. |
| AC-ORD-06 | Cancel a `CONFIRMED` order while a payment result is racing with the cancellation. | Order remains `CANCELLED`; inventory is released; no shipment is created; any captured payment is refunded exactly once. |
| AC-SEC-01 | Call every protected endpoint without a token, with the wrong role, and as another customer. | Responses are respectively `401`/`403`; no cross-customer data is disclosed; no token or secret appears in logs. |
| AC-REL-01 | Force a transient consumer failure and then a permanent failure. | Transient processing succeeds within configured retries; permanent failure reaches the correct DLQ and raises an observable alert. |
| AC-OBS-01 | Execute one complete order flow. | The same trace context can be followed through Gateway, REST calls, Kafka processing, logs, metrics, and spans. |

---

# 7. Non-Functional Requirements (`NFR`)

## 7.1 Performance & Scalability
| ID | Requirement |
|----|-------------|
| NFR-PERF-01 | Read APIs (catalog, order status) SHOULD respond in < 300 ms p95 under the local nominal-load profile defined in §9; cold starts are excluded and results MUST record the test hardware. |
| NFR-PERF-02 | Services MUST be horizontally scalable and stateless (support HPA). |
| NFR-PERF-03 | List endpoints MUST paginate; N+1 queries and `SELECT *` are prohibited (`AGENTS.md` → Performance). |
| NFR-PERF-04 | Product reads SHOULD use caching to reduce DB load. |

## 7.2 Reliability & Resilience
| ID | Requirement |
|----|-------------|
| NFR-REL-01 | No distributed DB transactions; consistency MUST be achieved via Saga + compensation. |
| NFR-REL-02 | All event consumers MUST be idempotent. |
| NFR-REL-03 | Inter-service synchronous calls MUST use timeouts and circuit breakers. Retries with backoff MUST be limited to transient failures on safe or explicitly idempotent operations; authentication, validation, and non-idempotent failures MUST NOT be retried automatically. |
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
| NFR-OBS-01 | Every Spring service MUST expose `/actuator/health` and `/actuator/prometheus`; Keycloak MUST expose its supported health and metrics endpoints. |
| NFR-OBS-02 | Every request/event MUST be traceable end-to-end via a propagated `traceId`. |
| NFR-OBS-03 | Logs MUST be structured and include trace/span/correlation IDs. |
| NFR-OBS-04 | Business and technical metrics MUST be emitted (order rate, payment success rate, consumer lag). |
| NFR-OBS-05 | Every Spring service and Keycloak MUST have readiness and liveness probes configured. See `docs/observability.md`. |
| NFR-OBS-06 | REST calls MUST propagate W3C `traceparent` and `tracestate`; Kafka producers and consumers MUST propagate the same context in record headers. An event-envelope `traceId` is required for search but MUST NOT replace W3C propagation. |
| NFR-OBS-07 | Metric tags MUST use bounded values such as service, operation, outcome, status, event type, and notification channel. Customer IDs, order IDs, product IDs, email addresses, trace IDs, and other unbounded or sensitive values MUST NOT be metric tags. |
| NFR-OBS-08 | Actuator and Prometheus endpoints MUST be reachable only from the local/internal management network. Public health responses MUST NOT expose dependency details, credentials, hostnames, or stack traces. |
| NFR-OBS-09 | The local reference environment MUST run Prometheus, Grafana, Loki, Tempo, and an OpenTelemetry Collector, with reproducible configuration stored in the repository. Application startup MUST NOT fail solely because an observability backend is temporarily unavailable. |
| NFR-OBS-10 | Every service MUST provide a dashboard covering traffic, errors, latency, saturation, dependency health, and its service-specific business signals. Required alert rules MUST be testable locally. |

### 7.4.1 Service-Level Observability Requirements

Metric names below are canonical Micrometer names. Exporters MAY translate dots to backend-specific notation such as Prometheus underscores. Counters measure totals, timers measure count and duration, and gauges represent current state. All services also retain Spring/Micrometer standard HTTP, JVM, process, database-pool, Kafka-client, and cache metrics applicable to their dependencies.

| ID | Service | Requirement |
|----|---------|-------------|
| NFR-OBS-GW-01 | Gateway | The Gateway MUST expose request rate, error rate, and latency by bounded route ID using standard HTTP metrics, and MUST emit counters `gateway.rate.limit.rejected`, `gateway.authentication.failed`, and `gateway.downstream.failed` plus circuit-breaker state/transition metrics. |
| NFR-OBS-GW-02 | Gateway | Each routed request MUST create or continue a server span and a downstream client span. Structured access logs MUST include route ID, method, normalized route template, status, duration, correlation ID, trace ID, and span ID, but MUST NOT contain JWTs or raw query-string secrets. |
| NFR-OBS-GW-03 | Gateway | Readiness MUST fail when the Gateway cannot load its required route configuration; an individual unavailable downstream service MUST be represented through route/circuit metrics and MUST NOT make the entire Gateway unready. |
| NFR-OBS-DISC-01 | Discovery | Discovery MUST expose gauges for registered applications and instances, and counters for registrations, renewals, cancellations, and evictions. It MUST alert locally when required services have no healthy instance beyond the configured lease/grace period. |
| NFR-OBS-DISC-02 | Discovery | Readiness MUST fail when the registry cannot serve discovery requests. Registration and eviction logs MUST include service name and instance ID, but MUST NOT include registration credentials. |
| NFR-OBS-AUTH-01 | Keycloak | Keycloak MUST expose supported built-in metrics and structured events for login success/failure, token issuance failure, and administrative changes. Dashboards MUST show authentication failure rate and token-endpoint latency without recording passwords, authorization codes, access tokens, refresh tokens, or session cookies. |
| NFR-OBS-AUTH-02 | Keycloak | Gateway and resource-service dashboards MUST distinguish invalid signature, expired token, wrong issuer/audience, and insufficient-role outcomes using bounded categories; raw token contents MUST NOT be logged or tagged. |
| NFR-OBS-CUST-01 | Customer | Customer Service MUST emit counters `customers.registered`, `customers.profile.updated`, `customers.address.changed`, and `customers.access.denied`, tagged only by bounded outcome/operation values. Registration and profile operations MUST create business spans. |
| NFR-OBS-CUST-02 | Customer | Customer Service readiness MUST fail when its PostgreSQL database is unavailable. Logs MAY include the internal customer ID for operational lookup but MUST NOT contain phone numbers, complete addresses, tokens, or unnecessary email addresses. |
| NFR-OBS-PROD-01 | Product | Product Service MUST emit counters `products.created`, `products.updated`, and `products.deactivated`, a timer `products.search.duration`, and Redis cache hit, miss, eviction, and load metrics. No search term or product ID may be used as a metric tag. |
| NFR-OBS-PROD-02 | Product | Product Service readiness MUST fail when PostgreSQL is unavailable. Redis unavailability MUST be visible through health, error, and fallback metrics but SHOULD NOT make the service unready while authoritative database reads remain functional. |
| NFR-OBS-ORD-01 | Order | Order Service MUST emit counters `orders.created`, `orders.cancelled`, `orders.failed`, and `orders.completed`; a counter `orders.state.transitions` tagged by bounded from/to state; a timer `orders.saga.duration`; and a gauge `orders.compensations.unresolved`. |
| NFR-OBS-ORD-02 | Order | Order creation, each Saga transition, outbox relay, and compensation MUST create spans or span events containing order ID as trace/log context, never as a metric tag. Illegal transitions and unresolved compensation MUST produce structured warning/error logs and locally testable alerts. |
| NFR-OBS-ORD-03 | Order | Order Service readiness MUST fail when PostgreSQL is unavailable. Kafka unavailability MUST be reflected in producer/outbox backlog metrics and alerts; it MUST NOT discard committed outbox records or falsely report a published event. |
| NFR-OBS-INV-01 | Inventory | Inventory Service MUST emit counters `inventory.reservations` tagged by bounded outcome, `inventory.releases`, `inventory.commits`, `inventory.adjustments`, and `inventory.duplicate.events`; reservation processing MUST expose a duration timer and concurrency-conflict counter. |
| NFR-OBS-INV-02 | Inventory | Reservation, release, commit, and adjustment operations MUST be traceable by order ID in spans/logs. Readiness MUST fail when PostgreSQL is unavailable; Kafka failure MUST be visible through consumer/producer and DLQ metrics without losing idempotency records. |
| NFR-OBS-PAY-01 | Payment | Payment Service MUST emit counters `payments.attempted`, `payments.completed`, `payments.failed`, `payments.refunds.requested`, `payments.refunded`, and `payments.refunds.failed`, plus timers for provider authorization/capture/refund latency. Tags MUST be limited to bounded provider, operation, and outcome values. |
| NFR-OBS-PAY-02 | Payment | Provider calls, webhook reconciliation, and refunds MUST create spans. Logs and telemetry MUST use internal payment/order references only and MUST NOT contain PAN, CVV, provider tokens, authorization headers, or raw provider payloads containing sensitive data. |
| NFR-OBS-PAY-03 | Payment | Readiness MUST fail when PostgreSQL is unavailable. Sandbox-provider failure MUST affect dependency health/metrics and circuit state but MUST NOT make the service unready to receive and safely persist/retry work. Any unresolved refund MUST alert locally. |
| NFR-OBS-SHIP-01 | Shipping | Shipping Service MUST emit counters `shipments.created`, `shipments.creation.failed`, `shipments.status.changed`, `shipments.delivered`, and `shipments.delivery.failed`, plus courier-call and shipment-creation duration timers tagged only by bounded courier/operation/outcome. |
| NFR-OBS-SHIP-02 | Shipping | Courier calls and lifecycle changes MUST create spans or span events. Readiness MUST fail when PostgreSQL is unavailable; courier failure MUST be shown as dependency/circuit degradation without exposing customer address data in metrics or ordinary logs. |
| NFR-OBS-NOTIF-01 | Notification | Notification Service MUST emit counters `notifications.attempted`, `notifications.sent`, `notifications.failed`, `notifications.retried`, and `notifications.duplicate.suppressed`, tagged only by bounded channel, template, event type, and outcome, plus a delivery-duration timer. |
| NFR-OBS-NOTIF-02 | Notification | Readiness MUST fail when PostgreSQL is unavailable. Mail/SMS/push adapter failure MUST be visible as dependency degradation, retry backlog, and DLQ metrics without logging message bodies, email addresses, phone numbers, or provider credentials. |
| NFR-OBS-ANLY-01 | Analytics | Analytics Service MUST emit counters `analytics.events.consumed`, `analytics.events.duplicate`, `analytics.events.out.of.order`, and `analytics.events.failed`; timers for event processing and aggregate refresh; and gauges for consumer lag and last-successful aggregate refresh age. |
| NFR-OBS-ANLY-02 | Analytics | Readiness MUST fail when PostgreSQL is unavailable. Kafka lag, stale aggregates, and DLQ growth MUST have locally testable alerts. Event IDs and business IDs MAY appear in trace/log context for diagnosis but MUST NOT be metric tags. |

### 7.4.2 Service-Level Observability Acceptance

| ID | Scenario | Expected Result |
|----|----------|-----------------|
| AC-OBS-02 | Execute one representative success and failure operation for every service. | The service-specific counters/timers change as specified, logs contain required correlation fields, and the operation is visible in a trace without sensitive data. |
| AC-OBS-03 | Stop each service's PostgreSQL container individually. | The affected database-owning service becomes unready, remains live, emits a dependency error, and recovers without restart after PostgreSQL returns. |
| AC-OBS-04 | Stop Redis, a sandbox provider, or a notification adapter. | The owning service reports dependency degradation and exercises the documented fallback/retry/circuit behavior without incorrectly becoming unready where the table says it should continue. |
| AC-OBS-05 | Stop Kafka during an order flow and restore it. | Committed outbox records remain pending, backlog/producer metrics and alerts become visible, and publication resumes without event loss after Kafka recovers. |
| AC-OBS-06 | Send an invalid JWT and execute operations containing representative customer/payment data. | Authentication metrics use bounded failure categories and exported logs, metrics, and traces contain no JWT, password, card data, provider token, email address, phone number, or complete address. |
| AC-OBS-07 | Trigger gateway rate limiting, an unresolved refund, Kafka consumer lag, DLQ growth, and stale analytics aggregates. | Each condition appears on its owning dashboard and activates its locally testable alert rule; the alert clears after recovery. |

## 7.5 Maintainability & Portability
| ID | Requirement |
|----|-------------|
| NFR-MNT-01 | Every service MUST follow the standard package layout (`AGENTS.md` → Package Structure). |
| NFR-MNT-02 | Services MUST NOT share entity classes or databases. |
| NFR-MNT-03 | Schema changes MUST be applied via Flyway migrations. |
| NFR-MNT-04 | Each service MUST ship a Dockerfile (non-root) and Helm chart. |
| NFR-MNT-05 | The platform MUST follow Twelve-Factor config (externalized config, env parity). |

## 7.6 Testability & Quality
| ID | Requirement |
|----|-------------|
| NFR-TST-01 | Every unit of business behavior MUST have focused unit tests using JUnit 5 and Mockito where isolation is useful; configuration-only behavior MAY be covered by integration tests instead. |
| NFR-TST-02 | Every service MUST have integration tests against the real infrastructure types it uses through Testcontainers or an equivalent disposable local runtime (for example Postgres, Kafka, Redis, or Keycloak). Gateway and Discovery are not required to start unused Postgres or Kafka containers. |
| NFR-TST-03 | Saga flows and compensation paths MUST have integration coverage. |
| NFR-TST-04 | Bug fixes MUST add a regression test. |

---

# 8. Data Ownership Rules (Summary)

- Each business service owns one PostgreSQL database. Gateway and Discovery are stateless; Keycloak owns its identity database.
- No cross-service joins, shared schemas, or foreign keys across service boundaries.
- References across contexts are by **ID only** (e.g. Order stores `customerId`, `productId`, not the entities).
- Cross-service data needs are met by REST query or by consuming events into a local read model.

---

# 9. Assumptions & Constraints

- **AS-1** Payment and courier providers are sandbox/mock; no real funds or logistics.
- **AS-2** A single Kafka cluster and single Postgres server (one DB per service) are sufficient for the learning environment.
- **AS-3** The entire reference platform runs locally. Docker Compose is used first; a local Kubernetes cluster (kind or Minikube), local container registry, Helm, and ArgoCD are introduced later. GitHub Actions workflows MAY be exercised locally with a compatible runner, while ordinary Maven integration tests remain the required local verification path.
- **AS-4** Email uses a local capture server such as MailHog/Mailpit; payment and courier use deterministic local adapters. No paid SaaS dependency is required.
- **AS-5** Tech stack is fixed per `AGENTS.md` (Java 21, Spring Boot 4.1.0, Spring Cloud, Kafka, Redis, Keycloak). If the specified versions are incompatible or unavailable when M0 begins, record and approve one architecture decision, then update all documents together before coding.
- **AS-6** The nominal local load profile is 25 concurrent virtual users for 5 minutes against at least 10,000 products, 1,000 customers, and 5,000 historical orders after warm-up. Results record CPU, memory, JVM settings, container limits, and host hardware; this is a repeatable learning baseline, not a production capacity claim.
- **CN-1** No architectural pattern outside those in `AGENTS.md` may be introduced without an explicit decision.
- **CN-2** Local-only execution does not weaken production-oriented design rules: secrets remain externalized, containers run non-root, TLS is required for the non-local profile, and services remain independently deployable.

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
| M6 | Local delivery pipeline | Docker → Helm → GitHub Actions workflow/local runner → ArgoCD on local Kubernetes | NFR-MNT-04, `docs/deployment.md` |

Every milestone MUST include the baseline cross-cutting work for the behavior it introduces: validation, standardized errors, Flyway where applicable, authorization, health checks, structured logging, trace propagation, metrics, OpenAPI/event documentation, and automated tests. M5 hardens and validates these capabilities with dashboards, alerts, DLQs, failure injection, and load tests; it does not defer their initial implementation.

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
