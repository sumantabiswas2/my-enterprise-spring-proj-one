# Event Architecture and Operations

## Purpose

This document defines the business meaning and operational rules for Kafka events. The requirements specification is authoritative for the event inventory; publisher-owned AsyncAPI contracts define the machine-readable payload schemas.

## Non-negotiable rules

- Topic names use `domain.event`; producers own their topics and schemas.
- An event is written to an outbox in the same local transaction as the state it describes. A relay publishes it and marks it delivered only after Kafka acknowledges it.
- Consumers deduplicate by `eventId` in their own durable `processed_event` store before applying side effects.
- Kafka preserves order only within one topic partition. It provides no ordering across topics. Consumers must therefore be correct when events from different topics arrive in either order.
- Stateful order-lifecycle messages carry `orderId` and monotonically increasing `orderVersion`. Inventory persists terminal cancellation/failure state and ignores a late `order.created` whose version is not newer.
- Partition by the owning aggregate ID (`orderId` for order workflow events, `productId` for catalog/inventory events, and `customerId` for customer events).
- No consumer uses a wildcard subscription. Analytics and Notification use the explicit allow-lists below.

## Common envelope

Every event has this envelope; domain data goes in `payload`.

```json
{
  "eventId": "2b1f6b3a-2da0-4c8d-a2de-3fc61bb75f11",
  "eventType": "order.created",
  "eventVersion": "1.0",
  "occurredAt": "2026-07-14T10:15:30Z",
  "source": "order-service",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "payload": {}
}
```

`eventId` is immutable and globally unique. `occurredAt` is UTC ISO-8601. `source` is the producing service. `traceId` supports search only; W3C `traceparent` and optional `tracestate` are also propagated in Kafka headers. Versions use `MAJOR.MINOR`: a compatible additive change increments MINOR; a breaking change requires a new MAJOR contract and migration plan.

## Event registry

| Topic | Producer | Required consumers | Meaning / key |
|---|---|---|---|
| `customer.registered` | customer | notification, analytics | Customer profile created; `customerId` |
| `customer.notification-preferences-updated` | customer | notification, analytics | Notification preferences changed; `customerId` |
| `product.created`, `product.updated` | product | analytics | Catalog changed; `productId` |
| `order.created` | order | inventory, notification, analytics | Pending order placed; `orderId`, `orderVersion` |
| `order.confirmed` | order | payment, notification, analytics | Reservation accepted; `orderId`, `orderVersion` |
| `order.paid` | order | shipping, notification, analytics | Payment captured; `orderId`, `orderVersion` |
| `order.cancelled`, `order.failed`, `order.completed` | order | inventory/notification/analytics as applicable | Terminal lifecycle fact; `orderId`, `orderVersion` |
| `inventory.reserved`, `inventory.reservation-failed`, `inventory.released` | inventory | order, analytics | Reservation outcome; `orderId` |
| `inventory.availability-changed` | inventory | product, analytics | Product availability read-model update; `productId` |
| `payment.completed`, `payment.failed`, `payment.refunded`, `payment.refund-failed` | payment | order, notification, analytics | Payment outcome; `orderId` |
| `payment.refund-requested` | order | payment, analytics | Idempotent Saga compensation request; `orderId` |
| `shipment.created`, `shipment.creation-failed`, `shipment.delivered`, `shipment.delivery-failed` | shipping | order, notification, analytics | Shipment lifecycle outcome; `orderId` |
| `shipment.status-changed` | shipping | notification, analytics | Tracking status update; `shipmentId` |

The exact payload, headers, examples, and bindings live in the publisher's AsyncAPI document and follow `docs/asyncapi-style-guide.md`.

## Order Saga

1. Order persists `PENDING` and publishes `order.created`.
2. Inventory reserves or rejects stock. On `inventory.reserved`, Order publishes `order.confirmed`; on failure it transitions to `FAILED` and publishes `order.failed`.
3. Payment consumes `order.confirmed`. On `payment.completed`, Order publishes `order.paid`; on failure it requests reservation release, transitions to `FAILED`, and publishes `order.failed`.
4. Shipping consumes `order.paid`. On `shipment.created`, Order enters `SHIPPED`; on `shipment.delivered`, it enters `DELIVERED` and publishes `order.completed` once.
5. A cancellation or later unrecoverable failure publishes the appropriate terminal order event. Order requests release of any active reservation and publishes `payment.refund-requested` if payment was captured. Payment publishes the refund result.

Order Service is the workflow coordinator. Inventory, Payment, and Shipping publish their own facts; they do not infer the next cross-service workflow step. A terminal order state wins over stale earlier lifecycle messages.

## Delivery failure handling

For each consumer, the first processing attempt is followed by at most three retries for transient failures, using backoff of 1 s, 5 s, and 30 s (with bounded jitter). Validation, authorization, and known non-retryable business failures are not retried.

After retries are exhausted, publish the original record plus failure metadata to:

```
<source-topic>.<consumer-service>.dlq
```

For example, Inventory's failed consumption of `order.created` goes to `order.created.inventory-service.dlq`. The DLQ record includes original topic/partition/offset, consumer service, failure class, failure time, retry count, and a redacted failure reason. DLQ replay is an explicit, audited operation after the cause is fixed; it must preserve the original `eventId` so normal idempotency still applies.

## Contract governance

- A producer owns its topic and publishes its AsyncAPI contract before implementation.
- Consumers reference the publisher-owned schema; they do not copy and independently alter it.
- Additive optional fields are compatible. Removing, renaming, or changing semantics/types is breaking.
- Contract tests validate examples and consumer compatibility in CI/local verification.
- Never place secrets, passwords, raw payment data, JWTs, complete addresses, or unnecessary personal data in events.

## Operational signals

Every producer exposes outbox backlog and publish-failure signals. Every consumer exposes processing duration, success/failure, duplicate suppression, lag, retry backlog, and DLQ growth. Trace headers are continued into consumer spans and structured logs. Exact service-level signals and alert acceptance are in `docs/requirements.md` §7.4.1.
