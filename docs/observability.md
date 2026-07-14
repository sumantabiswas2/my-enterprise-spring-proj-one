# Observability Guide

**Project:** Enterprise Microservice Platform

**Version:** 1.0

---

# 1. Purpose

This document defines the observability standards for all microservices within the Enterprise Microservice Platform.

Observability enables engineers to understand the internal state of a distributed system using:

- Metrics
- Distributed Tracing
- Logging

Every service in this platform must implement all three.

---

# 2. Observability Stack

| Capability | Technology |
|------------|------------|
| Metrics | Micrometer |
| Tracing | OpenTelemetry |
| Metrics Storage | Prometheus |
| Dashboards | Grafana |
| Log Aggregation | Loki |
| Distributed Traces | Tempo |
| Logging Framework | SLF4J + Logback |

---

# 3. High Level Architecture

```
                Spring Boot Services
                        │
        ┌───────────────┼────────────────┐
        │               │                │
        ▼               ▼                ▼
     Metrics         Traces            Logs
        │               │                │
        ▼               ▼                ▼
   Micrometer     OpenTelemetry      Logback
        │               │                │
        ▼               ▼                ▼
   Prometheus        Tempo             Loki
          └────────────┼──────────────┘
                       ▼
                   Grafana
```

---

# 4. Three Pillars

## Metrics

Answer

> "What is happening?"

Examples

- CPU usage
- Memory usage
- Request count
- Error rate

---

## Logs

Answer

> "What exactly happened?"

Examples

- Order Created
- Payment Failed
- Customer Login

---

## Traces

Answer

> "Where did the request spend time?"

Example

Gateway

↓

Order Service

↓

Inventory

↓

Payment

↓

Shipping

Each service contributes one or more spans.

---

# 5. Metrics

Every microservice must expose

```
/actuator/prometheus
```

Example

```
http_server_requests_seconds_count

jvm_memory_used_bytes

process_cpu_usage

hikaricp_connections_active
```

---

# 6. Required Metrics

Every service should publish

## HTTP

- Request Count
- Response Time
- Error Count
- Active Requests

---

## JVM

- Heap Usage
- Non Heap Usage
- GC Count
- GC Time
- Thread Count
- Loaded Classes

---

## Database

- Active Connections
- Idle Connections
- Query Time
- Failed Queries

---

## Kafka

- Messages Published
- Messages Consumed
- Consumer Lag
- Failed Messages

---

## Cache

Redis

- Hits
- Misses
- Evictions

---

# 7. Custom Business Metrics

Business metrics should be created using Micrometer.

Examples

```
orders.created

orders.cancelled

payments.success

payments.failed

inventory.reserved

shipment.created
```

These are more valuable than technical metrics.

---

# 8. Metric Naming

Good

```
orders.created

orders.cancelled

payment.success

inventory.available
```

Bad

```
counter1

metric2

myCounter
```

Metric names should describe business activity.

---

# 9. Metric Tags

Always tag metrics.

Example

```
service=order-service

status=SUCCESS

paymentType=CARD

country=IN
```

Avoid high-cardinality tags such as

```
customerId

email

phoneNumber

traceId
```

---

# 10. Distributed Tracing

Every incoming request starts a Trace.

Example

```
Gateway

↓

Order Service

↓

Inventory

↓

Payment

↓

Shipping
```

Every service contributes spans.

---

# 11. Trace Context

Every request must propagate

```
traceparent

tracestate
```

Never remove these headers.

---

# 12. Trace IDs

Every request has

```
Trace ID
```

Example

```
4bf92f3577b34da6a3ce929d0e0e4736
```

This Trace ID links

- Metrics
- Logs
- Traces

---

# 13. Span Naming

Good

```
GET /products

POST /orders

Reserve Inventory

Charge Payment

Publish Kafka Event
```

Bad

```
method1

controller

execute
```

---

# 14. Custom Spans

Create spans for important business operations.

Examples

```
Validate Inventory

Reserve Stock

Charge Card

Create Shipment

Send Notification
```

---

# 15. Kafka Tracing

Trace should continue through Kafka.

```
Order Service

↓

Kafka

↓

Inventory Service

↓

Kafka

↓

Payment Service
```

Trace ID must remain unchanged.

---

# 16. Logging

Logging framework

```
SLF4J

Logback
```

Log format

```
Timestamp

Level

Trace ID

Span ID

Service

Thread

Message
```

---

# 17. Log Levels

| Level | Usage |
|---------|--------|
|TRACE|Detailed debugging|
|DEBUG|Developer debugging|
|INFO|Business events|
|WARN|Recoverable issues|
|ERROR|Failures|

---

# 18. Log Structure

Prefer structured JSON logs.

Example

```json
{
  "timestamp":"2026-07-14T12:00:00Z",
  "level":"INFO",
  "service":"order-service",
  "traceId":"abc123",
  "spanId":"xyz789",
  "message":"Order Created"
}
```

---

# 19. Logging Rules

Log

- Incoming request
- Outgoing response
- External API calls
- Kafka events
- Exceptions

Do NOT log

- Password
- JWT
- Credit Card
- CVV
- OTP
- API Keys

---

# 20. Prometheus

Prometheus scrapes

```
/actuator/prometheus
```

Example

```
scrape_interval

15 seconds
```

---

# 21. Grafana Dashboards

Every service should have dashboards for

- HTTP Requests
- Error Rate
- JVM
- Kafka
- Database
- Redis
- Business Metrics

---

# 22. Loki

Loki stores logs.

Developers should search by

- Trace ID
- Order ID
- Customer ID
- Service Name

---

# 23. Tempo

Tempo stores distributed traces.

Typical troubleshooting

```
Customer reports issue

↓

Find Trace ID

↓

Open Tempo

↓

View complete request journey
```

---

# 24. Health Checks

Every service exposes

```
/actuator/health
```

Readiness

```
/actuator/health/readiness
```

Liveness

```
/actuator/health/liveness
```

---

# 25. Kubernetes Monitoring

Monitor

- CPU
- Memory
- Restart Count
- OOM Kills
- Pod Status
- Replica Count

---

# 26. Gateway Monitoring

Monitor

- Request Rate
- Route Latency
- Authentication Failures
- Rate Limiting
- Downstream Errors

---

# 27. Kafka Monitoring

Monitor

- Consumer Lag
- Topic Throughput
- Publish Rate
- Failed Consumers
- DLQ Messages

---

# 28. Alerting

Critical alerts

- Service Down
- High Error Rate
- High Response Time
- Kafka Consumer Lag
- JVM Memory > 90%
- Database Connection Pool Exhausted

---

# 29. SLOs

Example

Availability

```
99.9%
```

API Response

```
95%

< 500 ms
```

Error Rate

```
< 1%
```

---

# 30. Troubleshooting Workflow

User reports issue

↓

Find Trace ID

↓

Open Grafana

↓

Check Metrics

↓

Open Tempo

↓

Find Slow Span

↓

Open Loki

↓

Inspect Logs

↓

Identify Root Cause

---

# 31. AI Coding Rules

When generating Spring Boot code, AI agents should:

- Enable Micrometer for every service.
- Configure OpenTelemetry tracing.
- Expose `/actuator/prometheus`.
- Use structured JSON logging.
- Propagate Trace ID across REST and Kafka.
- Create custom business metrics for important operations.
- Add custom spans around business logic.
- Avoid logging sensitive information.
- Instrument external REST calls and Kafka producers/consumers.
- Ensure every exception is logged with Trace ID and Span ID.

---

# 32. Future Enhancements

The observability platform may later include

- Jaeger support
- OpenTelemetry Collector
- Distributed Profiling
- eBPF Monitoring
- Kubernetes Event Monitoring
- SLO Dashboards
- AI-assisted anomaly detection
- Automatic Root Cause Analysis
- Synthetic Monitoring
- Chaos Engineering Metrics