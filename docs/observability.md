# Observability Guide

## Baseline

Observability is required from the first implementation of each behavior. The exact service-level metrics, logs, traces, dashboards, alerts, and acceptance scenarios are normative in `docs/requirements.md` §7.4. This guide defines their common implementation.

## Local stack

The local reference environment runs Prometheus, Grafana, Loki, Tempo, and an OpenTelemetry Collector with configuration stored in the repository. Applications export telemetry through the Collector where configured, but they must keep running safely if a telemetry backend is temporarily unavailable. The Collector is not a future enhancement.

Every Spring service exposes `/actuator/health` and `/actuator/prometheus` on the local/internal management network. Keycloak exposes its supported health and metrics endpoints. Readiness represents whether the component can safely serve its main responsibility; liveness represents whether it needs restart. A database-owning service becomes unready when its PostgreSQL database is unavailable. Cache/provider degradation follows the service-specific rules in the requirements.

## Logs

Use structured JSON logs with at least timestamp, level, service, environment, message, trace ID, span ID, correlation ID, and bounded operation/outcome fields. Log business events, state transitions, external calls, failures, retries, and DLQ routing. Never log passwords, JWTs, cookies, secrets, authorization headers, card data, provider tokens, complete addresses, phone numbers, or message bodies.

## Tracing

Use OpenTelemetry and W3C context propagation. Gateway starts or continues a server span and downstream REST client spans. REST calls propagate `traceparent` and `tracestate`; Kafka producers copy them to record headers and consumers continue the context before creating processing spans. The envelope `traceId` enables search but does not replace W3C headers.

Use order/customer/payment IDs only as trace or carefully redacted log context, never as metric labels. Record exception details safely and attach aggregate transition/span events rather than high-cardinality metric tags.

## Metrics

Use Micrometer standard HTTP, JVM, process, pool, Kafka-client, and cache metrics, plus the canonical business metrics in the requirements. Names such as `orders.created`, `payments.completed`, and `shipments.created` are canonical Micrometer names; exporters may translate dots to underscores. Tag only bounded dimensions such as service, route, operation, outcome, status, event type, channel, and provider.

All event producers measure outbox backlog and publication failures. Consumers measure processing duration, outcome, duplicate suppression, retry backlog, consumer lag, and DLQ growth. Gateway measures rate limiting/authentication/downstream failures; Discovery measures registry activity; Keycloak dashboards surface safe authentication/token outcomes.

## Dashboards and alerts

Each service dashboard covers traffic, errors, latency, saturation, dependency health, and its required business signals. Locally testable alerts cover at least: no healthy required discovery instance, unresolved payment refund, outbox backlog/publication failure, consumer lag, DLQ growth, stale analytics aggregation, and gateway rate-limit/downstream failure. Alerts must clear after recovery.

## Verification

Run the acceptance scenarios in `docs/requirements.md` §7.4.2: normal and failing operation per service, PostgreSQL outage, cache/provider outage, Kafka outage and recovery, invalid JWT/redaction checks, and alert-trigger scenarios. A complete order flow must be traceable through gateway, REST, Kafka, logs, metrics, and spans with no sensitive data exposed.
