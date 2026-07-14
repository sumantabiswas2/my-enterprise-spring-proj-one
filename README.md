# Enterprise Microservice Platform

A local-first e-commerce platform for learning Java, Spring Boot, distributed systems, event-driven design, security, observability, and delivery automation.

The project deliberately uses independently deployable Spring services. It is a learning reference, not a production storefront or a real-money payment system.

## Start Here

Read these documents before implementing a service:

1. [Requirements specification](docs/requirements.md)
2. [Architecture](docs/architecture.md)
3. [Agent instructions](AGENTS.md)
4. The relevant API, event, database, security, observability, and deployment guides in [`docs/`](docs/)

`docs/requirements.md` is the source of truth for platform behavior. The other guides describe how to implement that behavior.

## Local Reference Environment

Everything runs locally:

- Docker Compose first.
- PostgreSQL with one database per business service.
- Kafka, Redis, and Keycloak.
- MailHog/Mailpit plus deterministic payment and courier adapters.
- Prometheus, Grafana, Loki, Tempo, and an OpenTelemetry Collector.
- Later: kind or Minikube, a local registry, Helm, and ArgoCD.

No cloud account, real payment provider, real courier, or externally hosted runtime is required.

## Services

| Component | Responsibility |
|---|---|
| Gateway | External API entry point, JWT validation, routing, rate limiting, and trace propagation. |
| Discovery | Local service registry used by Spring services. |
| Keycloak | OAuth2/OIDC identity provider; owns login credentials and tokens. |
| Customer | Customer profiles, addresses, and preferences linked to Keycloak subjects. |
| Product | Product catalogue, pricing, categories, and an event-maintained availability read model. |
| Order | Order lifecycle and event-driven Saga coordinator. |
| Inventory | Warehouse stock and reservations. |
| Payment | Sandbox payment capture and refunds. |
| Shipping | Sandbox courier assignment, tracking, and delivery lifecycle. |
| Notification | Event-driven email delivery and notification history. |
| Analytics | Event-fed operational aggregates. |

## Order Flow

```text
POST /api/v1/orders
  -> order.created
  -> inventory.reserved
  -> order.confirmed
  -> payment.completed
  -> order.paid
  -> shipment.created
  -> shipment.delivered
  -> order.completed
```

Order Service coordinates the lifecycle. Every event is delivered at least once, so consumers are idempotent and tolerate out-of-order delivery. See [requirements.md](docs/requirements.md) for compensation and cancellation flows.

## Suggested Learning Path

1. Platform skeleton: Discovery, Gateway, Keycloak, and one service through the gateway.
2. Catalog and customer profile management.
3. Order and inventory reservation with outbox and idempotency.
4. Payment, shipping, compensation, notification, and analytics.
5. Observability, resilience, DLQs, failure injection, and load tests.
6. Local Kubernetes, Helm, and ArgoCD.

See the milestone table in [requirements.md](docs/requirements.md).

## Building

The platform is a Maven multi-module build (Java 21, Spring Boot 4.1.0, Spring Cloud 2025.1.x). From the repository root:

```bash
./mvnw clean package          # build all modules
./mvnw -pl discovery-server spring-boot:run   # run a single service
```

## Running M0 Locally (Platform Skeleton)

M0 delivers Discovery, the Gateway, Keycloak identity, and one service (Customer) reachable end to end through the gateway with JWT validation and RBAC.

1. Provide local secrets: `cp .env.example .env` and edit the placeholder values.
2. Start local infrastructure (Keycloak, Redis, Customer DB):
   ```bash
   docker compose --env-file .env -f docker/docker-compose.yml up -d
   ```
3. Start the services (each in its own shell):
   ```bash
   ./mvnw -pl discovery-server spring-boot:run
   ./mvnw -pl gateway spring-boot:run
   ./mvnw -pl auth-service spring-boot:run
   ./mvnw -pl customer-service spring-boot:run
   ```
4. Obtain a token from Keycloak (`web-app` client, `demo-customer` user) and call through the gateway:
   ```bash
   curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/customers/hello
   ```

Health and metrics are exposed on every service at `/actuator/health` and `/actuator/prometheus`.

## Status

The repository contains the platform contracts, implementation standards, a Maven multi-module build for all services, and the **M0 platform skeleton** (Discovery, Gateway with JWT/rate-limiting/circuit-breaking, Keycloak realm, and the Customer hello path). Remaining business logic (M1–M6) is added incrementally per the milestone table in [requirements.md](docs/requirements.md).
