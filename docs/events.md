# Event Architecture Guide

**Project:** Enterprise Microservice Platform  
**Version:** 1.0

---

# 1. Purpose

This document defines the event-driven architecture used by the Enterprise Microservice Platform.

It describes:

- Kafka topics
- Event publishers
- Event consumers
- Event payloads
- Event naming conventions
- Versioning strategy
- Retry handling
- Dead Letter Queue (DLQ)
- Best practices

All microservices must follow these standards.

---

# 2. Event Driven Architecture

The platform follows an Event-Driven Architecture (EDA).

Services communicate using Kafka whenever an immediate synchronous response is **not required**.

Benefits

- Loose coupling
- Better scalability
- Higher resiliency
- Independent deployments
- Easier integration

---



# 3. High-Level Event Flow

```

Customer

↓

Order Service

↓

OrderCreated Event

↓

Kafka

↓

Inventory Service

↓

InventoryReserved Event

↓

Kafka

↓

Payment Service

↓

PaymentCompleted Event

↓

Kafka

↓

Shipping Service

↓

ShipmentCreated Event

↓

Kafka

↓

Notification Service

Analytics Service

```

---



# 4. Kafka Topics


| Topic              | Publisher            | Consumers                  |
| ------------------ | -------------------- | -------------------------- |
| order.created      | Order Service        | Inventory, Analytics       |
| order.cancelled    | Order Service        | Inventory, Notification    |
| inventory.reserved | Inventory Service    | Order Service              |
| inventory.released | Inventory Service    | Order Service              |
| payment.completed  | Payment Service      | Order, Shipping, Analytics |
| payment.failed     | Payment Service      | Order, Notification        |
| shipment.created   | Shipping Service     | Notification, Analytics    |
| shipment.delivered | Shipping Service     | Notification, Analytics    |
| notification.sent  | Notification Service | Analytics                  |


---



# 5. Event Naming Convention

Topics should follow

```
<domain>.<event>
```

Examples

```
order.created

order.cancelled

payment.completed

inventory.reserved

shipment.created
```

Avoid

```
topic1

orderTopic

paymentEvent
```

---



# 6. Event Naming Rules

Event names should describe something that **already happened**.

Good

```
OrderCreated

PaymentCompleted

InventoryReserved

ShipmentDelivered
```

Bad

```
CreateOrder

ReserveInventory

ShipOrder
```

Events represent facts.

---



# 7. Standard Event Envelope

Every Kafka message should use the following envelope.

```json
{
  "eventId": "6e8452a5-79d8-44db-b98c-8a0e49f0f357",
  "eventType": "OrderCreated",
  "eventVersion": "1.0",
  "occurredAt": "2026-07-14T10:30:00Z",
  "source": "order-service",
  "traceId": "2d8d79fa34",
  "payload": {
  }
}
```

---



# 8. Event Metadata

Every event must include


| Field        | Required |
| ------------ | -------- |
| eventId      | Yes      |
| eventType    | Yes      |
| eventVersion | Yes      |
| occurredAt   | Yes      |
| source       | Yes      |
| traceId      | Yes      |
| payload      | Yes      |


---



# 9. OrderCreated Event

Publisher

```
Order Service
```

Topic

```
order.created
```

Consumers

- Inventory Service
- Analytics Service

Example

```json
{
  "eventId": "1",
  "eventType": "OrderCreated",
  "eventVersion": "1.0",
  "occurredAt": "2026-07-14T11:00:00Z",
  "source": "order-service",
  "traceId": "abc123",
  "payload": {
    "orderId": 101,
    "customerId": 20,
    "totalAmount": 1500.00,
    "items": [
      {
        "productId": 1001,
        "quantity": 2
      }
    ]
  }
}
```

---



# 10. InventoryReserved Event

Publisher

Inventory Service

Consumers

Order Service

Topic

```
inventory.reserved
```

Payload

```json
{
  "orderId":101,
  "status":"RESERVED"
}
```

---



# 11. PaymentCompleted Event

Publisher

Payment Service

Consumers

- Order Service
- Shipping Service
- Analytics Service

Payload

```json
{
  "orderId":101,
  "paymentId":9001,
  "amount":1500.00,
  "status":"SUCCESS"
}
```

---



# 12. ShipmentCreated Event

Publisher

Shipping Service

Consumers

- Notification Service
- Analytics Service

Payload

```json
{
  "shipmentId":501,
  "orderId":101,
  "trackingNumber":"TRK123456"
}
```

---



# 13. Event Ownership

Each event has exactly one publisher.

Good

```
OrderCreated

Publisher

Order Service
```

Bad

```
OrderCreated

Publisher

Order Service

Inventory Service
```

Never allow multiple publishers for the same business event.

---



# 14. Event Versioning

Use semantic versions.

```
1.0

1.1

2.0
```

Breaking changes require

```
2.0
```

---



# 15. Backward Compatibility

Prefer adding optional fields.

Good

```json
{
  "orderId":101,
  "customerId":20,
  "couponCode":"SAVE10"
}
```

Avoid renaming or removing existing fields.

---



# 16. Partition Key

Use a stable business identifier.

Examples

```
orderId

customerId

shipmentId
```

Never use random UUIDs as partition keys unless ordering is unimportant.

---



# 17. Ordering

Messages with the same partition key must be processed in order.

Example

```
OrderCreated

↓

PaymentCompleted

↓

ShipmentCreated
```

---



# 18. Delivery Guarantee

Kafka provides

```
At Least Once
```

Consumers must therefore be idempotent.

---



# 19. Idempotency

Duplicate messages may occur.

Consumers should detect duplicate events using

```
eventId
```

or

```
businessId
```

---



# 20. Retry Strategy

Retry only transient failures.

Recommended


| Attempt | Delay      |
| ------- | ---------- |
| 1       | 1 second   |
| 2       | 5 seconds  |
| 3       | 30 seconds |


After retries, send to DLQ.

---



# 21. Dead Letter Queue

Every topic should have a DLQ.

Example

```
order.created

↓

order.created.dlq
```

Messages in DLQ require investigation.

---



# 22. Poison Messages

A poison message repeatedly fails processing.

Rules

- Stop retrying after configured attempts
- Move to DLQ
- Generate an alert
- Preserve original payload

---



# 23. Event Schema Validation

Every consumer must validate

- Required fields
- Event version
- Payload format

Reject invalid messages.

---



# 24. Event Size

Recommended maximum

```
< 1 MB
```

Large documents should be stored externally with only a reference included in the event.

---



# 25. Sensitive Data

Never publish

- Passwords
- JWT tokens
- Credit card numbers
- CVV
- OTP
- API keys

Use references instead of sensitive values.

---



# 26. Observability

Every event should include

- traceId
- eventId
- timestamp
- publisher

Every consumer should log

- processing time
- success/failure
- retry count

---



# 27. Monitoring

Monitor

- Publish rate
- Consumer lag
- Retry count
- DLQ size
- Processing latency
- Failed messages

---



# 28. Testing

Each event should have

- Unit tests
- Integration tests
- Contract tests
- End-to-end tests

---



# 29. Event Contract Ownership

Publishers own the schema.

Consumers must not change publisher contracts.

Breaking changes require a new event version.

---



# 30. Event Documentation

Every event should document

- Purpose
- Publisher
- Consumers
- Payload
- Sample JSON
- Version
- Topic
- Retry policy
- DLQ policy

---



# 31. AI Coding Rules

When generating Kafka code, AI agents should:

- Publish immutable event objects.
- Include the standard event envelope.
- Add `eventId`, `traceId`, and `occurredAt` to every event.
- Use meaningful topic names (`order.created`, `payment.completed`).
- Use business identifiers as partition keys.
- Make consumers idempotent.
- Configure retry and Dead Letter Queue handling.
- Log event publication and consumption with trace information.
- Avoid publishing sensitive information.
- Keep event payloads focused on business facts, not database entities.

---



# 32. Future Enhancements

The platform may later adopt:

- AsyncAPI documentation
- Schema Registry (Apache Avro / Protobuf)
- Event version compatibility validation
- Outbox Pattern
- Change Data Capture (CDC)
- Saga Pattern
- Event Replay
- Event Archival
- Event Encryption
- Cross-region Kafka replication

