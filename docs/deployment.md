# Local Deployment Guide

## Goal

This project is a fully local reference environment. It demonstrates containerization, Helm, GitOps, and Kubernetes without requiring a paid cloud account or external registry.

## Environments

`local` is the required, reproducible environment. Docker Compose is the first runtime; kind or Minikube is introduced after the services work in Compose. A non-local profile may be added later to demonstrate TLS and production-like settings, but `dev`, `qa`, `uat`, and production clusters are not prerequisites for this project.

## Local components

Compose runs PostgreSQL databases (one per business service), Kafka, Redis, Keycloak, Mailpit/MailHog, mock payment/courier adapters, Prometheus, Grafana, Loki, Tempo, and an OpenTelemetry Collector. All configuration needed to recreate this environment is committed, except secret values.

Use a local registry (for example `localhost:5001`) when deploying to kind/Minikube. Do not require Docker Hub or GHCR for normal development.

## Build and run flow

1. Run unit and integration tests locally; integration tests use disposable infrastructure where practical.
2. Build each service artifact and a non-root container image.
3. Run the Compose smoke flow before Kubernetes work.
4. Push/load the image into the local registry/cluster.
5. Install/update the Helm chart in the local cluster.
6. Optionally let locally installed Argo CD reconcile the Git-tracked Helm values to demonstrate GitOps.

CI may build, test, and update a GitOps values repository/branch. Argo CD, not CI, applies desired state to a Kubernetes cluster. A local-compatible runner can demonstrate the workflow; it is not a requirement to contact GitHub-hosted infrastructure.

## Helm and Kubernetes rules

Each deployable Spring service has a chart with `Chart.yaml`, `values.yaml`, and templates. Keycloak and shared observability components may use pinned upstream charts or locally maintained charts with reproducible values.

Render only resources that the component needs:

- `Deployment` and `Service` for networked workloads;
- `ConfigMap` for non-secret configuration;
- `Secret` references/templates without real values committed;
- readiness and liveness probes for every Spring service and Keycloak;
- `HorizontalPodAutoscaler` when the target profile needs it (local defaults can use one replica without an HPA).

Containers run as non-root, define resource requests/limits appropriate for the local machine, and expose management ports only on the local/internal network. Gateway is the only public entry point. Do not add PostgreSQL, Kafka, or Redis to services that do not use them merely for structural uniformity.

## Configuration

Use Spring profiles and environment variables. Keep a checked-in `.env.example` and Helm value examples with names/placeholders only. Never commit credentials, JWT signing material, provider tokens, or populated kubeconfig files. Local HTTP is acceptable; a non-local profile requires TLS.

## Delivery acceptance

The delivery pipeline is complete when a new developer can clone the repository, supply local secret values, run the documented commands, execute an authenticated order flow through the gateway, inspect traces/logs/metrics, and tear the environment down cleanly. Zero-downtime rollout, multi-region recovery, formal RTO/RPO targets, and hosted production operations are future learning extensions—not current acceptance criteria.
