# REST API Guidelines

## Scope

These rules apply to Spring service APIs behind the gateway. Public contracts are versioned under `/api/v1`; document every endpoint in OpenAPI before or with implementation.

## Resource conventions

- Use plural nouns: `/api/v1/products`, `/api/v1/orders/{orderId}`.
- Use HTTP semantics: `GET` reads, `POST` creates/commands, `PUT` replaces, `PATCH` partially changes, and `DELETE` removes where the domain permits it.
- Commands that are not resource replacement may use an explicit action, such as `POST /api/v1/orders/{orderId}/cancel`.
- Use ISO-8601 UTC timestamps, JSON, and opaque IDs in public contracts.
- Do not expose JPA entities or another service's internal identifiers unnecessarily.

## Responses

Single-resource successes use one wrapper:

```json
{
  "data": { "id": "..." },
  "timestamp": "2026-07-14T10:15:30Z",
  "traceId": "..."
}
```

Collection successes use the same wrapper plus pagination:

```json
{
  "data": [],
  "page": { "number": 0, "size": 20, "totalElements": 0, "totalPages": 0 },
  "timestamp": "2026-07-14T10:15:30Z",
  "traceId": "..."
}
```

Use `201` with `Location` for creation, `202` only for genuinely asynchronous acceptance, and `204` only when no response body is returned. Document only status codes that are applicable to the endpoint.

## Errors

All errors use:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "One or more fields are invalid.",
  "timestamp": "2026-07-14T10:15:30Z",
  "traceId": "...",
  "fieldErrors": [{ "field": "quantity", "message": "must be positive" }]
}
```

Use `400` for malformed JSON and Bean Validation failures, `401` for authentication failure, `403` for authorization failure, `404` for an inaccessible/nonexistent resource where disclosure is safe, `409` for a domain conflict (including invalid order transition or late cancellation), `429` for rate limiting, and `5xx` for unexpected/dependency failures. Do not use `422` in this API convention. Never return a stack trace.

## Querying and pagination

Use `page` (zero-based) and `size`; default and maximum size are documented per endpoint. Allow-list `sort` fields and directions. Filters have typed, documented query parameters, for example `category`, `minPrice`, `maxPrice`, and `availabilityStatus`. Product APIs expose availability status, not inventory stock counts.

## Security and context propagation

The gateway and every resource service validate JWTs. Services derive identity from the validated principal, enforce RBAC and record ownership, and never trust user identity headers. The gateway creates or propagates `X-Correlation-Id`; REST calls propagate W3C `traceparent`/`tracestate`. Correlation ID and trace ID are distinct and both appear in structured logs.

## Idempotency and concurrency

For retryable client-created commands such as order placement, support an `Idempotency-Key` scoped to the authenticated caller and endpoint; return the original result for a replay. Use ETags/`If-Match` or a documented version field where concurrent updates can overwrite changes. Event-level idempotency is handled separately with `eventId`.

## Deprecation

Avoid breaking `/v1`. Mark deprecated operations in OpenAPI, add a `Deprecation: true` response header and a documented `Sunset` date for each specific operation, and offer a migration path. Do not publish a generic fixed sunset date.

## Implementation boundary

Controllers validate DTOs, delegate to application services, and return DTOs/wrappers. They do not query repositories, contain business rules, or publish Kafka messages directly. A centralized exception handler creates the standard error envelope.
