# Security Guide

## Scope and authority

The requirements specification defines mandatory security behavior. This guide explains the local-first implementation rules for Spring services and Keycloak.

## Identity model

Keycloak is the OAuth2/OIDC authorization server. It owns account registration, passwords, credential recovery, and account-email uniqueness. Application services never store or hash an end-user password.

Tokens contain an immutable `sub` and roles. Customer Service maps `sub` to one local customer profile; it must not use `sub` as its database primary key. The minimum roles are:

| Role | Use |
|---|---|
| `CUSTOMER` | Own profile, addresses, orders, and customer-safe catalog actions |
| `ADMIN` | Catalog, stock, operational reports, and authorized cross-customer support |
| `SERVICE` | Machine identity for a specific service account |

Use realm/client role mapping consistently and convert them to Spring authorities (`ROLE_CUSTOMER`, `ROLE_ADMIN`, `ROLE_SERVICE`) at the resource-server boundary.

## Authentication and authorization

- The gateway validates JWT signature, issuer, expiry, and intended audience before routing protected requests.
- Each resource service also validates those JWT properties. Gateway validation is a useful edge control, not a reason to trust unvalidated headers.
- Browser/mobile clients use an OIDC authorization-code flow with PKCE. Service-to-service calls use a least-privileged client-credentials token, never a forwarded user token.
- All `/api/v1/**` endpoints require authentication unless explicitly documented public. Public catalog reads and health endpoints are the limited exceptions.
- Enforce authorization both at endpoint and ownership level. A customer can only access records associated with their mapped customer ID; `ADMIN` access is explicit and auditable.
- Return `401` for missing/invalid authentication and `403` for authenticated but unauthorized access. Do not reveal resource existence through error detail.

## Gateway and service boundaries

External traffic reaches Spring services only through the gateway. Internal reachability is restricted by local Kubernetes networking when used, but services still protect their own APIs. Do not accept caller identity from `X-User-*` headers. Derive identity only from a validated token.

## Token validation baseline

Every Spring resource server configures issuer/JWK discovery from Keycloak, validates expected issuer and audience, applies a short clock-skew tolerance, and maps roles deliberately. Cache JWKs according to the library defaults; do not hardcode signing keys. Rotate keys through Keycloak and test a rotation locally.

## Secrets and configuration

- Keep client secrets, database passwords, Kafka credentials, and provider keys outside source control.
- Use environment variables or local secret mounts for the learning environment; Kubernetes `Secret` resources must be templates without real values in Git.
- Redact authorization headers, cookies, passwords, card data, API keys, JWTs, and provider tokens in logs, traces, errors, and event payloads.
- Provide `.env.example` files with variable names only; never commit a populated `.env` file.

## API and input safety

- Use DTOs and Jakarta Bean Validation at every input boundary.
- Allow-list sort fields and page sizes; never pass request values directly into dynamic queries.
- Use parameterized queries/JPA binding. Escape output according to the consuming UI if a UI is added.
- Standardized error responses contain a safe code, message, timestamp, trace ID, and field errors where appropriate—never stack traces or internal configuration.

## Payments and personal data

Payment stores provider references and transaction metadata only. It never persists PAN, CVV, raw card payloads, or full provider payloads that contain them. Events and ordinary logs use minimal identifiers; notification adapters must not log message body, destination address, or credentials.

## Transport and local profile

TLS is mandatory outside the local profile. Local HTTP is permitted only for the reproducible developer environment and must not change authentication, authorization, secret handling, or redaction requirements. Actuator and Prometheus endpoints are restricted to the internal/local management network; public health output is minimal.

## Verification checklist

- Invalid, expired, wrong-issuer/audience, and insufficient-role tokens are rejected with the correct status.
- A customer cannot retrieve another customer's data.
- Service account permissions are limited to their required APIs.
- Keycloak registration/profile-provisioning failure is recoverable and visible without duplicate profiles.
- Logs, metrics, traces, DLQs, and errors contain no secret or sensitive payment data.
