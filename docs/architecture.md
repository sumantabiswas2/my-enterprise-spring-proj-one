# Enterprise Microservice Platform Architecture

**Version:** 1.0  
**Project Type:** Local-First Cloud-Native Microservices Learning Platform
**Language:** Java 21  
**Framework:** Spring Boot 4.1.0
---

# 1. Purpose and Source of Truth

This document explains the platform structure and implementation boundaries. `docs/requirements.md` is authoritative for business behavior, event ownership, and acceptance criteria. `AGENTS.md` is authoritative for repository workflow and coding rules.

The project demonstrates independently deployable services, local transactions, event-driven workflows, security, observability, and local delivery automation. It does not require a cloud account, real payment provider, or real courier.

---

# 2. Platform Components

| Component | Type | Responsibility | Data ownership |
|---|---|---|---|
| Gateway Service | Infrastructure | External entry point, routing, JWT validation, rate limiting, trace propagation. | Stateless. |
| Discovery Service | Infrastructure | Eureka registration and discovery for Spring services. | Stateless registry state. |
| Keycloak | Platform component | OAuth2/OIDC authorization server, login, credentials, and token issuance. | Keycloak identity database. |
| Customer Service | Business service | Customer profiles, addresses, and notification preferences. | `customer_db`. |
| Product Service | Business service | Catalogue, price, categories, and local availability read model. | `product_db`, Redis cache. |
| Order Service | Business service | Order lifecycle and event-driven Saga coordination. | `order_db`. |
| Inventory Service | Business service | Warehouse stock, reservations, releases, and availability events. | `inventory_db`. |
| Payment Service | Business service | Sandbox payment capture, reconciliation, and refunds. | `payment_db`. |
| Shipping Service | Business service | Sandbox courier assignment, tracking, and delivery events. | `shipping_db`. |
| Notification Service | Business service | Event-driven email delivery and notification history. | `notification_db`. |
| Analytics Service | Business service | Event-fed operational aggregates and admin reports. | `analytics_db`. |

Gateway and Discovery do not own PostgreSQL business databases. Every business service owns one PostgreSQL database and never reads another service's database.

---

# 3. High-Level Topology

```text
Client
  |
  v
Gateway -----> Keycloak (OIDC/JWT validation metadata)
  |
  +----> Customer / Product / Order REST APIs
                           |
                           v
                         Kafka
       +-------------------+--------------------+
       v                   v                    v
  Inventory            Payment              Shipping
       |                   |                    |
       +-------------------+--------------------+
                           |
                  Notification / Analytics / Product availability read model
```

All Spring services register with Discovery. In a later Kubernetes phase, Eureka remains a deliberate learning component rather than an assumed production requirement.

---

# 4. Architectural Boundaries

The platform uses:

- Database per service.
- Clean Architecture boundaries inside business services.
- DDD bounded contexts at service boundaries.
- REST for immediate queries or commands requiring an immediate answer.
- Kafka domain events for workflow progression and independent side effects.
- Local transactions, transactional outbox, idempotent consumers, and compensations instead of distributed transactions.

Business services use domain, application, and adapter boundaries defined in `AGENTS.md`. Domain and application code must not depend on Spring MVC, Kafka, JPA entities, or external HTTP clients.

---

# 5. Identity and Security Architecture

Keycloak owns account registration, login credentials, and OIDC token issuance.

```text
Client -- Authorization Code + PKCE --> Keycloak
Client -- Bearer JWT --> Gateway --> Resource Services
```

Gateway and every protected resource service validate JWT issuer, signature, audience, and expiry. Resource services enforce role-based authorization themselves; network location is not an authorization boundary.

The minimum roles are:

- `CUSTOMER`
- `ADMIN`
- `SERVICE`

Customer Service maps the immutable Keycloak `sub` claim to its own customer-profile ID. It never owns passwords or performs login. Service-to-service calls use client-credentials tokens.

---

# 6. REST Communication

All public APIs are versioned under `/api/v1` and are routed through Gateway.

Use synchronous REST only when the caller needs an immediate response. Initial examples include:

- Order validates product existence and obtains the price snapshot from Product.
- Notification retrieves a minimal notification profile from Customer with a service token when needed to deliver an event-triggered notification.

All synchronous calls use a timeout, circuit breaker, W3C trace propagation, and retries only when the operation is safe or explicitly idempotent.

---

# 7. Event-Driven Order Saga

Order Service coordinates the Saga by consuming outcomes and publishing the next lifecycle fact:

```text
order.created
  -> inventory.reserved | inventory.reservation-failed
  -> order.confirmed
  -> payment.completed | payment.failed
  -> order.paid
  -> shipment.created | shipment.creation-failed
  -> shipment.delivered
  -> order.completed
```

The detailed event catalogue, compensation paths, consumer lists, and acceptance criteria are in `docs/requirements.md`. `docs/events.md` explains the operational semantics and `docs/asyncapi-style-guide.md` defines contract documentation rules.

Kafka guarantees ordering only inside a single topic partition. Order lifecycle payloads therefore carry a monotonic `orderVersion`; stateful consumers persist terminal cancellation/failure state and ignore stale messages.

---

# 8. Data and Caching

Each business service owns its schema, tables, migrations, outbox, and consumer idempotency records.

Redis is a cache, never a system of record. Product Service caches catalogue reads and maintains an availability read model from `inventory.availability-changed`; it does not read Inventory's database or expose raw stock counts as product-owned data.

---

# 9. Observability

Every Spring service exposes health and Prometheus metrics. Keycloak exposes its supported health and metrics endpoints.

The local reference environment contains Prometheus, Grafana, Loki, Tempo, and an OpenTelemetry Collector. REST and Kafka propagate `traceparent`, optional `tracestate`, and `X-Correlation-Id`; event envelopes retain a searchable `traceId`.

Service-specific metric, dashboard, readiness, and alert requirements are defined in `docs/requirements.md`.

---

# 10. Technology Baseline

| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.0 |
| Cloud release train | Spring Cloud 2025.1.2 or later compatible 2025.1.x |
| Gateway | Spring Cloud Gateway |
| Discovery | Spring Cloud Netflix Eureka |
| Persistence | PostgreSQL, Spring Data JPA, Hibernate, Flyway, HikariCP |
| Messaging | Apache Kafka |
| Cache | Redis |
| Security | Spring Security, OAuth2 Resource Server, Keycloak |
| Resilience | Resilience4j |
| Observability | Micrometer, OpenTelemetry, Prometheus, Grafana, Loki, Tempo, OpenTelemetry Collector |
| Testing | JUnit 5, Mockito, Testcontainers |
| Local delivery | Docker Compose, kind or Minikube, local registry, Helm, ArgoCD |

---

# 11. Deployment Model

The mandatory initial environment is local:

```text
Docker Compose -> local tests -> local observability stack
                         |
                         v
              kind/Minikube -> Helm -> local ArgoCD
```

GitHub Actions, external registries, multi-environment promotion, and production SLOs are future extensions. CI should build, test, scan, and publish only when a remote environment is intentionally introduced.

---

# 12. Design Rules

- Never access another service's database or share entity classes.
- Never use XA/2PC or distributed database transactions.
- Never put business logic in REST controllers, Kafka listeners, or repositories.
- Never publish JPA entities as events or API responses.
- Never log credentials, JWTs, payment data, or secrets.
- Never treat Kafka topic ordering as cross-topic ordering.
- Prefer explicit, documented contracts over implied service behavior.
