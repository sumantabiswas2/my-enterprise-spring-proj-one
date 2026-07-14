# AsyncAPI Style Guide

**Project:** Enterprise Microservice Platform  
**Version:** 1.0  
**AsyncAPI Version:** 3.1.0  
**Kafka Binding Version:** 0.5.0

---

# 1. Purpose

This document defines how the platform documents Kafka-based event APIs with AsyncAPI.

An AsyncAPI document is a contract between event producers and consumers. It must explain what an application sends and receives without requiring readers to inspect Java code, Kafka configuration, or database entities.

This guide complements:

- `AGENTS.md` for repository-wide architecture and coding rules.
- `docs/requirements.md` for authoritative functional and non-functional requirements.
- `docs/events.md` for event architecture, ownership, retries, DLQs, and business semantics.
- `docs/observability.md` for trace propagation, metrics, and logging.
- `docs/security.md` for authentication, authorization, and sensitive-data rules.

When documents conflict, first preserve the business intent in `docs/requirements.md`, then update all affected contracts and implementation together.

---

# 2. Required Specification

All new AsyncAPI documents MUST use:

```yaml
asyncapi: 3.1.0
```

Kafka bindings MUST explicitly declare:

```yaml
bindingVersion: '0.5.0'
```

Do not use AsyncAPI 2.x `publish` and `subscribe` Operation Objects. AsyncAPI 3.x operations are defined at the document root and use:

- `action: send` when the documented application sends a message.
- `action: receive` when the documented application receives a message.

The action is always from the perspective of the application named in `info.title`.

---

# 3. Contract Ownership and Location

Every service that publishes or consumes Kafka messages MUST own an `asyncapi.yaml` file at that service module's root.

Examples:

```text
order-service/asyncapi.yaml
inventory-service/asyncapi.yaml
payment-service/asyncapi.yaml
```

Rules:

- A service document MUST describe every topic that service sends to or receives from.
- The event publisher owns the message payload schema.
- Consumers MUST reference the publisher-owned schema. They MUST NOT copy it into a separately maintained contract.
- A generated platform-wide document MAY aggregate service contracts, but it MUST NOT become a competing source of truth.
- Shared schemas MAY be stored in a repository-level contract module when reuse is introduced deliberately.
- Java classes, database entities, and generated schemas are not authoritative unless the checked-in AsyncAPI contract is updated in the same change.

---

# 4. Required Document Structure

Every service contract MUST contain:

```yaml
asyncapi:
info:
defaultContentType:
servers:
channels:
operations:
components:
```

Use `components` for reusable messages, schemas, headers, correlation IDs, and security schemes. Avoid copying the same envelope or header schema into multiple messages.

---

# 5. API Information

The `info` object MUST include:

- A service-specific title.
- A concise description of the service's event responsibilities.
- A contract version.

Example:

```yaml
info:
  title: Order Service Event API
  version: 1.0.0
  description: >-
    Events sent and received by Order Service while coordinating the order Saga.
```

The `info.version` identifies the overall service contract release. It is separate from the `eventVersion` carried by each message.

---

# 6. Servers and Environments

The local Kafka broker MUST be documented. Non-local servers MAY be added later without embedding credentials.

Example:

```yaml
servers:
  localKafka:
    host: localhost:9092
    protocol: kafka
    description: Kafka broker in the local reference environment.
```

Rules:

- Never include usernames, passwords, API keys, tokens, truststore passwords, or private certificates.
- Environment-specific addresses SHOULD use server variables or deployment configuration.
- Non-local Kafka connections MUST document the required TLS/SASL mechanism without documenting secret values.
- A contract MUST NOT assume that `localhost` is valid inside Docker or Kubernetes; the example above describes developer-host access only.

---

# 7. Channel and Topic Naming

Kafka topic addresses MUST follow the repository convention:

```text
<domain>.<event>
```

Examples:

```text
order.created
inventory.reserved
payment.completed
shipment.delivered
```

Channel keys are AsyncAPI identifiers and SHOULD use lower camel case:

```yaml
channels:
  orderCreated:
    address: order.created
```

Rules:

- The channel `address` MUST exactly match the Kafka topic.
- Topic names describe facts that have already occurred.
- Do not use generic names such as `events`, `topic1`, or `orderTopic`.
- DLQ topics use `<original-topic>.<consumer-service>.dlq`, for example `order.created.inventory-service.dlq`.
- Retry topics, if introduced, MUST use a documented deterministic suffix.
- Each business topic MUST have exactly one owning publisher.
- Wildcard channels such as `*.*` MUST NOT be used as contract definitions.

Each business channel MUST document these extensions:

```yaml
x-event-owner: order-service
x-consumers:
  - inventory-service
  - notification-service
  - analytics-service
x-partition-key: orderId
x-dlq-topics:
  inventory-service: order.created.inventory-service.dlq
  notification-service: order.created.notification-service.dlq
  analytics-service: order.created.analytics-service.dlq
```

The values MUST match the event catalogue in `docs/requirements.md` and `docs/events.md`.

---

# 8. Operations

Every send and receive behavior MUST have an explicit root-level operation.

Operation IDs use a verb followed by the event name:

```text
sendOrderCreated
receiveInventoryReserved
sendPaymentCompleted
receiveOrderPaid
```

Example:

```yaml
operations:
  sendOrderCreated:
    action: send
    summary: Publish a newly accepted order.
    channel:
      $ref: '#/channels/orderCreated'
    messages:
      - $ref: '#/channels/orderCreated/messages/orderCreated'
```

Rules:

- Operations MUST include `action`, `summary`, and `channel`.
- Operations SHOULD include a description of the business trigger and expected outcome.
- When a channel declares multiple messages, the operation MUST list the exact messages it handles.
- Operation message references MUST point to messages under the referenced channel, not directly to `components.messages`.
- A receive operation MUST document its Kafka consumer group with an operation binding.
- Consumer group IDs MUST be stable, service-specific, and identical to runtime configuration.

Example receive binding:

```yaml
bindings:
  kafka:
    groupId:
      type: string
      enum:
        - order-service
    bindingVersion: '0.5.0'
```

---

# 9. Message Naming

Message component keys and message names use PascalCase and past tense:

```text
OrderCreated
InventoryReserved
PaymentCompleted
ShipmentDelivered
```

Each message MUST define:

- `name`.
- `title` or `summary`.
- `contentType: application/json`.
- Common headers.
- Correlation ID.
- Payload schema.
- Kafka key binding.
- At least one realistic example.

Do not name event messages after commands such as `CreateOrder` or after persistence types such as `OrderEntity`.

---

# 10. Standard Event Envelope

Every event payload MUST use the repository envelope:

```json
{
  "eventId": "6e8452a5-79d8-44db-b98c-8a0e49f0f357",
  "eventType": "OrderCreated",
  "eventVersion": "1.0",
  "occurredAt": "2026-07-14T10:30:00Z",
  "source": "order-service",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "payload": {}
}
```

Required envelope fields:

| Field | Schema | Rule |
|-------|--------|------|
| `eventId` | string, UUID | Globally unique; used for idempotent processing. |
| `eventType` | string | Exact PascalCase event name. |
| `eventVersion` | string | Contract version for this event type. |
| `occurredAt` | string, `date-time` | UTC ISO-8601 timestamp. |
| `source` | string | Owning service name, such as `order-service`. |
| `traceId` | string | Searchable trace identifier; not a replacement for W3C headers. |
| `payload` | object | Event-specific immutable business fact. |

The envelope MUST NOT contain retry counters, consumer state, database metadata, or framework-specific serialization fields. Retry metadata belongs in Kafka headers or consumer infrastructure.

---

# 11. Headers, Correlation, and Tracing

Every message MUST document these Kafka headers:

| Header | Required | Purpose |
|--------|----------|---------|
| `traceparent` | Yes | W3C trace context. |
| `tracestate` | No | Optional W3C vendor trace state. |
| `X-Correlation-Id` | Yes | Request/workflow correlation identifier. |
| `content-type` | Yes | Must be `application/json`. |

Use a reusable header schema:

```yaml
components:
  schemas:
    EventHeaders:
      type: object
      required:
        - traceparent
        - X-Correlation-Id
        - content-type
      properties:
        traceparent:
          type: string
        tracestate:
          type: string
        X-Correlation-Id:
          type: string
        content-type:
          type: string
          const: application/json
```

Use the correlation ID component:

```yaml
components:
  correlationIds:
    workflowCorrelationId:
      description: Correlates messages belonging to one business workflow.
      location: $message.header#/X-Correlation-Id
```

Rules:

- `traceparent` and `tracestate` MUST be propagated in Kafka record headers.
- The envelope `traceId` exists for search and audit correlation only.
- `X-Correlation-Id`, `traceId`, and `eventId` are distinct identifiers.
- Consumers MUST continue or link trace context according to the tracing library's Kafka instrumentation.
- Tokens, credentials, and personal data MUST NOT be placed in headers.

---

# 12. Payload Schema Rules

Every payload field MUST define:

- Type.
- Description.
- Required/optional status.
- Format or constraint where applicable.
- A realistic example.

Rules:

- Use JSON field names in lower camel case.
- Use `format: uuid` for UUID values.
- Use `format: date-time` for UTC timestamps.
- Use explicit enums for bounded states.
- Monetary values MUST document currency and decimal precision. Java implementations MUST use `BigDecimal`, never `double` or `float`.
- Quantities MUST define minimum values.
- Arrays MUST define item schemas and relevant size constraints.
- Identifiers MUST retain the type defined by the owning domain; do not silently change numeric IDs to UUIDs or vice versa.
- Payloads MUST model business facts, not JPA entities.
- Consumers MUST tolerate unknown optional fields for backward compatibility.
- Required fields MUST be limited to information necessary to interpret the event safely.
- Sensitive fields prohibited by `docs/security.md` MUST NOT appear in a schema or example.

---

# 13. Kafka Bindings

Kafka-specific information MUST use AsyncAPI Kafka bindings version `0.5.0`.

Channel bindings SHOULD document the deployed topic characteristics when they are controlled by the project:

```yaml
bindings:
  kafka:
    partitions: 3
    replicas: 1
    topicConfiguration:
      cleanup.policy:
        - delete
      max.message.bytes: 1048576
    bindingVersion: '0.5.0'
```

Local replica counts MAY differ from non-local profiles. Do not claim a value that deployment configuration does not enforce.

Message bindings MUST describe the Kafka record key:

```yaml
bindings:
  kafka:
    key:
      type: string
      description: Stable order identifier used to preserve per-order ordering.
    bindingVersion: '0.5.0'
```

Rules:

- Use a stable business identifier such as `orderId`, `customerId`, or `shipmentId` as the key.
- The documented key type MUST match the producer serializer.
- Do not use `eventId` as the partition key when business ordering matters.
- Partition and replica values MUST match Helm/Docker/local topic configuration.
- Maximum message size MUST remain within the event limit in `docs/events.md`.

---

# 14. Publisher and Consumer Semantics

Each channel or operation description MUST state:

- The business action that causes publication.
- The state transaction associated with publication.
- The owning publisher.
- All known consumers.
- The partition key.
- Expected ordering.
- Delivery guarantee.
- Idempotency key.
- Retry and DLQ behavior.

Platform defaults:

- Delivery is at least once.
- Producers use the transactional outbox pattern.
- Consumers deduplicate using `eventId` and persist idempotency state transactionally with their business effect.
- Duplicate delivery MUST not duplicate reservations, charges, refunds, shipments, notifications, or analytics contributions.
- Consumers MUST reject unsupported major event versions rather than guessing how to process them.

---

# 15. Retry and Dead-Letter Documentation

Every receive operation MUST document its retry policy and DLQ.

Use consistent extensions:

```yaml
x-retry-policy:
  maxRetries: 3
  backoff:
    - PT1S
    - PT5S
    - PT30S
x-dlq-topic: order.created.inventory-service.dlq
```

Rules:

- `maxRetries` excludes the initial processing attempt; this policy therefore permits one initial attempt and up to three retries.
- Retry only transient failures.
- Validation errors, unsupported versions, and permanent business failures MUST NOT loop indefinitely.
- A DLQ contract MUST preserve the original event and relevant non-sensitive failure metadata.
- DLQ messages MUST remain traceable to the original `eventId` and correlation context.
- DLQ topic access and retention MUST be documented in deployment configuration.
- Retry counts and exception details MUST NOT be added to the immutable business payload.

---

# 16. Event Versioning and Compatibility

The `eventVersion` field versions one event type.

Compatibility rules:

- Adding an optional field is normally backward compatible.
- Removing or renaming a field is breaking.
- Changing a field type, meaning, unit, or enum interpretation is breaking.
- Making an optional field required is breaking.
- Changing the Kafka record key or its meaning is breaking.
- Breaking changes require a new major `eventVersion` and a documented migration plan.
- Old and new versions MAY coexist during migration.
- Consumers MUST be upgraded or proven compatible before an old version is retired.

Examples and tests MUST exist for every supported event version.

---

# 17. Security and Privacy

AsyncAPI schemas and examples MUST NOT contain:

- Passwords.
- JWTs, refresh tokens, or authorization codes.
- Card PAN, CVV, or raw payment-provider tokens.
- OTPs.
- API keys or credentials.
- Secrets or private certificate material.
- Unnecessary personal data.

Use opaque references when a downstream service needs to retrieve protected information through an authorized API.

Kafka server security SHOULD document the mechanism, such as TLS and SASL, but MUST keep secret values externalized.

---

# 18. Examples

Every message MUST provide at least one valid example.

Examples MUST:

- Include all required envelope and payload fields.
- Match the schema exactly.
- Use realistic but fictional values.
- Use UTC timestamps.
- Avoid real customer, payment, or credential data.
- Demonstrate enum values and nested collections where relevant.

Failure events MUST include examples that show safe business failure information without stack traces or internal exception details.

---

# 19. Contract Validation and Testing

The build MUST validate every `asyncapi.yaml` file.

Validation MUST check:

- AsyncAPI syntax and references.
- Kafka binding syntax.
- Unique channel and operation identifiers.
- Required event envelope fields.
- Message examples against schemas.
- Topic names against the naming convention.
- Send/receive operations against the service inventory.
- Publisher ownership and consumer lists against `docs/requirements.md`.
- Retry and DLQ metadata for receive operations.
- Prohibited sensitive fields and example values.

Contract tests MUST verify that:

- Producers emit messages accepted by the documented schema.
- Consumers accept every supported documented version.
- Consumers reject malformed or unsupported messages safely.
- Duplicate messages are idempotent.
- W3C trace and correlation headers are propagated.

AsyncAPI validation and contract tests MUST run in the normal local Maven verification workflow and in CI.

---

# 20. Complete Example

The following abbreviated Order Service contract demonstrates the required structure. Real service contracts must include all of their send and receive operations.

```yaml
asyncapi: 3.1.0

info:
  title: Order Service Event API
  version: 1.0.0
  description: Events sent by Order Service while coordinating the order Saga.

defaultContentType: application/json

servers:
  localKafka:
    host: localhost:9092
    protocol: kafka
    description: Kafka broker accessed from the developer host.

channels:
  orderCreated:
    address: order.created
    description: Newly accepted orders awaiting inventory reservation.
    messages:
      orderCreated:
        $ref: '#/components/messages/OrderCreated'
    bindings:
      kafka:
        partitions: 3
        replicas: 1
        bindingVersion: '0.5.0'
    x-event-owner: order-service
    x-consumers:
      - inventory-service
      - notification-service
      - analytics-service
    x-partition-key: orderId
    x-dlq-topic: order.created.inventory-service.dlq

operations:
  sendOrderCreated:
    action: send
    summary: Publish a newly accepted order.
    description: Sent after the order and outbox record commit atomically.
    channel:
      $ref: '#/channels/orderCreated'
    messages:
      - $ref: '#/channels/orderCreated/messages/orderCreated'

components:
  messages:
    OrderCreated:
      name: OrderCreated
      title: Order created event
      summary: Describes an order accepted for Saga processing.
      contentType: application/json
      headers:
        $ref: '#/components/schemas/EventHeaders'
      correlationId:
        $ref: '#/components/correlationIds/workflowCorrelationId'
      payload:
        $ref: '#/components/schemas/OrderCreatedEvent'
      bindings:
        kafka:
          key:
            type: integer
            format: int64
            description: Order identifier.
          bindingVersion: '0.5.0'
      examples:
        - name: validOrderCreated
          headers:
            traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
            X-Correlation-Id: 3f1c07b0-6492-4b7d-8c28-9ca68089d32f
            content-type: application/json
          payload:
            eventId: 6e8452a5-79d8-44db-b98c-8a0e49f0f357
            eventType: OrderCreated
            eventVersion: '1.0'
            occurredAt: '2026-07-14T10:30:00Z'
            source: order-service
            traceId: 4bf92f3577b34da6a3ce929d0e0e4736
            payload:
              orderId: 101
              customerId: 20
              totalAmount: 1500.00
              currency: INR
              items:
                - productId: 1001
                  quantity: 2

  correlationIds:
    workflowCorrelationId:
      description: Correlates messages in one business workflow.
      location: $message.header#/X-Correlation-Id

  schemas:
    EventHeaders:
      type: object
      required:
        - traceparent
        - X-Correlation-Id
        - content-type
      properties:
        traceparent:
          type: string
        tracestate:
          type: string
        X-Correlation-Id:
          type: string
        content-type:
          type: string
          const: application/json

    EventMetadata:
      type: object
      required:
        - eventId
        - eventType
        - eventVersion
        - occurredAt
        - source
        - traceId
      properties:
        eventId:
          type: string
          format: uuid
        eventType:
          type: string
        eventVersion:
          type: string
          pattern: '^\\d+\\.\\d+$'
        occurredAt:
          type: string
          format: date-time
        source:
          type: string
        traceId:
          type: string

    OrderCreatedEvent:
      allOf:
        - $ref: '#/components/schemas/EventMetadata'
        - type: object
          required:
            - payload
          properties:
            eventType:
              const: OrderCreated
            source:
              const: order-service
            payload:
              $ref: '#/components/schemas/OrderCreatedPayload'

    OrderCreatedPayload:
      type: object
      required:
        - orderId
        - customerId
        - totalAmount
        - currency
        - items
      properties:
        orderId:
          type: integer
          format: int64
        customerId:
          type: integer
          format: int64
        totalAmount:
          type: number
          minimum: 0
        currency:
          type: string
          pattern: '^[A-Z]{3}$'
        items:
          type: array
          minItems: 1
          items:
            type: object
            required:
              - productId
              - quantity
            properties:
              productId:
                type: integer
                format: int64
              quantity:
                type: integer
                minimum: 1
```

---

# 21. Review Checklist

Before merging an event contract, verify:

- [ ] AsyncAPI version is `3.1.0`.
- [ ] Kafka binding version is `0.5.0` everywhere a binding is used.
- [ ] Service perspective makes every `send` and `receive` action unambiguous.
- [ ] Topic address follows `<domain>.<event>`.
- [ ] Publisher and consumers match `docs/requirements.md`.
- [ ] Message uses the standard event envelope.
- [ ] Payload is a business contract, not an entity serialization.
- [ ] Kafka key and ordering expectation are documented.
- [ ] W3C trace and correlation headers are documented.
- [ ] Retry, idempotency, and DLQ behavior are documented.
- [ ] Versioning and compatibility impact were reviewed.
- [ ] Examples validate and contain no sensitive information.
- [ ] Producer and consumer contract tests were added or updated.
- [ ] Implementation, `docs/events.md`, and AsyncAPI changed together.

---

# 22. AI Coding Rules

When generating or modifying Kafka contracts, AI coding agents MUST:

- Read `docs/requirements.md`, `docs/events.md`, and this guide first.
- Use AsyncAPI 3.1 root-level operations with `send` and `receive` actions.
- Keep publisher ownership and consumer lists explicit.
- Reuse the standard envelope, headers, and correlation definitions.
- Add realistic, schema-valid examples.
- Document Kafka keys, consumer groups, retry policy, and DLQ topics.
- Preserve backward compatibility unless a major event version is intentionally introduced.
- Add or update producer and consumer contract tests.
- Never infer an event schema from a JPA entity.
- Never include credentials or sensitive customer/payment data.

---

# 23. References

- AsyncAPI document structure: <https://www.asyncapi.com/docs/concepts/asyncapi-document/structure>
- AsyncAPI messages: <https://www.asyncapi.com/docs/concepts/asyncapi-document/adding-messages>
- AsyncAPI operations: <https://www.asyncapi.com/docs/concepts/asyncapi-document/adding-operations>
- Kafka bindings: <https://www.asyncapi.com/docs/reference/bindings/kafka>
