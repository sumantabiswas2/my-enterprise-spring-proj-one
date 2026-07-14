# AGENTS.md

# Enterprise Microservice Platform

**Version:** 1.0

---

# Purpose

This document provides instructions for AI coding agents (ChatGPT, Codex, GitHub Copilot, Claude Code, Gemini CLI, Cursor, Windsurf, etc.) working on this repository.

Before generating or modifying code, always understand the existing architecture and follow the project standards.

This repository prioritizes:

- Production-ready code
- Clean Architecture
- Domain-Driven Design (DDD)
- SOLID principles
- Cloud-native design
- Event-driven communication
- Security by default
- High observability
- Testability
- Maintainability

---

# Read These Documents First

Before writing any code, review these documents in order:

1. docs/requirements.md
2. docs/architecture.md
3. docs/api-guidelines.md
4. docs/openapi-style-guide.md
5. docs/events.md
6. docs/asyncapi-style-guide.md
7. docs/database.md
8. docs/security.md
9. docs/observability.md
10. docs/deployment.md

`docs/requirements.md` is authoritative for platform behavior. If documents conflict, reconcile the documents before implementing the affected behavior; do not guess. Update code only after the contract is coherent or the user explicitly approves an architectural change.

---

# Technology Stack

Java 21

Spring Boot 4.1.0

Spring Cloud

Spring Cloud 2025.1.2 or a later compatible 2025.1.x service release

Spring Cloud Gateway

Spring Cloud Discovery

Spring Security

OAuth2

Keycloak

JWT

Spring Data JPA

PostgreSQL

Kafka

Redis

Docker

Kubernetes

Helm

ArgoCD

GitHub Actions

Micrometer

OpenTelemetry

Prometheus

Grafana

Loki

Tempo

JUnit 5

Mockito

Testcontainers

---

# Microservices

Current services

- gateway-service
- discovery-service
- customer-service
- product-service
- order-service
- inventory-service
- payment-service
- shipping-service
- notification-service
- analytics-service

Each service is independently deployable.

Business services own their local database, business logic, REST API where required, and domain events. Gateway and Discovery are stateless infrastructure services; Keycloak owns identity data and OIDC endpoints.

Never violate service boundaries.

---

# Architecture Principles

Always follow

- Domain Driven Design
- Database per Service
- API First
- Event Driven Architecture
- Stateless Services
- Twelve-Factor App principles where applicable

Never

- Access another service's database
- Introduce distributed database transactions
- Share entity classes between services
- Introduce tight coupling between services

---

# Package Structure

Every business service should use Clean Architecture boundaries while retaining familiar Spring adapters.

```
com.company.orderservice

config/

domain/
  model/
  event/
  repository/

application/
  service/
  port/in/
  port/out/

adapter/in/rest/
  controller/
  dto/
  validation/

adapter/in/kafka/

adapter/out/persistence/
  entity/
  repository/
  mapper/

adapter/out/client/

security/
exception/
```

The domain and application layers must not depend on Spring MVC, Kafka, JPA entities, or external client implementations. Infrastructure adapters implement application ports. Do not create arbitrary package structures outside these boundaries.

---

# REST API Standards

Always

- Prefix endpoints with `/api/v1`
- Use JSON
- Validate request bodies
- Return proper HTTP status codes
- Use DTOs
- Generate OpenAPI documentation

Never

- Return Entity classes
- Return stack traces
- Expose internal exceptions

---

# Controller Rules

REST controllers should

- Validate requests
- Delegate to services
- Return DTOs

REST controllers must NOT

- Access repositories
- Contain business logic
- Publish Kafka events directly

---

# Service Rules

Business use cases belong in application services. Domain invariants and state transitions belong in domain models or domain services.

Services may

- Call repositories
- Publish Kafka events
- Create Micrometer metrics
- Create OpenTelemetry spans
- Handle transactions

---

# Repository Rules

Repositories

- Extend Spring Data JPA
- Contain persistence logic only

Never place business logic in repositories.

---

# DTO Rules

Always create

- Request DTO
- Response DTO

Separate internal entities from external contracts.

---

# Event Rules

Kafka topic naming

```
domain.event
```

Examples

```
order.created

payment.completed

shipment.created
```

Every event must include

- eventId
- eventType
- eventVersion
- occurredAt
- source
- traceId
- payload

Consumers must be idempotent.

Event producers must use the transactional outbox pattern. Stateful consumers must handle duplicate and out-of-order delivery; Kafka ordering applies only within one topic partition.

---

# Database Rules

Each business service owns one PostgreSQL database. Gateway and Discovery are stateless; Keycloak owns its identity database.

Use

- Flyway
- Spring Data JPA
- HikariCP

Never

- Join across services
- Share schemas
- Read another service's tables

Cross-service communication must use REST or Kafka.

---

# Security Rules

Authentication

OAuth2

OpenID Connect

JWT

Keycloak

Never

- Hardcode secrets
- Log JWTs
- Log passwords
- Commit credentials

Use RBAC for authorization.

---

# Observability Rules

Every Spring service must expose

```
/actuator/health

/actuator/prometheus
```

Every service should include

- Micrometer metrics
- OpenTelemetry traces
- Structured logs

Log entries should contain

- Trace ID
- Span ID
- Correlation ID

---

# Logging Rules

Use

SLF4J

Logback

Log

- Business events
- Exceptions
- External API calls
- Kafka publish/consume

Never log

- Passwords
- Credit card data
- API keys
- JWTs
- Secrets

---

# Exception Handling

Use

@ControllerAdvice

Return standardized error responses.

Never expose stack traces.

---

# Validation

Use Jakarta Bean Validation.

Examples

@NotNull

@NotBlank

@Email

@Positive

---

# Testing

Every feature should include

- Unit Tests
- Integration Tests

Use

JUnit 5

Mockito

Testcontainers

When fixing bugs, add regression tests where appropriate.

---

# Docker

Each service requires

- Dockerfile

Run containers as non-root.

Keep images lightweight.

---

# Kubernetes

Each service should provide

- Deployment
- Service
- ConfigMap
- Secret
- HorizontalPodAutoscaler

Configure

- Readiness Probe
- Liveness Probe

---

# Helm

Each service should have

Chart.yaml

values.yaml

templates/

Avoid duplicated Kubernetes YAML.

---

# CI/CD

GitHub Actions pipeline should

- Build
- Test
- Package
- Build Docker image
- Push image
- Update Helm values
- Deploy through ArgoCD

---

# Performance

Prefer

- Constructor Injection
- Pagination
- Batch operations
- DTO projections

Avoid

- N+1 queries
- SELECT *
- Large transactions
- Blocking calls in reactive components

---

# Code Style

Prefer

- Small classes
- Small methods
- Clear naming
- Immutable DTOs
- Composition over inheritance

Avoid

- God classes
- Deep inheritance
- Static mutable state
- Magic numbers

---

# Definition of Done

A task is complete only when

- Code compiles
- Unit tests pass
- Integration tests pass
- APIs are documented
- Validation is implemented
- Logging is added
- Metrics are added
- Tracing is added
- Security is enforced
- Documentation is updated if behavior changes

---

# AI Agent Workflow

For every implementation:

1. Understand the business requirement.
2. Identify affected services.
3. Review related documentation.
4. Design before coding.
5. Keep changes isolated to the owning service.
6. Add or update tests.
7. Verify observability (logs, metrics, traces).
8. Verify security.
9. Verify API contracts.
10. Ensure no architectural rules are violated.

---

# When Unsure

If implementation details are ambiguous:

- Prefer consistency with the existing codebase.
- Do not invent new architectural patterns.
- Ask for clarification when a decision would affect public APIs, event contracts, or database schemas.

---

# Guiding Principles

When multiple solutions are possible, prefer:

- Simplicity over cleverness
- Consistency over novelty
- Readability over brevity
- Explicitness over hidden behavior
- Composition over inheritance
- Local transactions over distributed transactions
- Events over tight coupling
- Maintainability over premature optimization
- Platform standards over personal preference
