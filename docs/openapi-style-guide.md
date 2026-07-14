# OpenAPI Style Guide

## Purpose

Each Spring service owns an OpenAPI 3 contract for its REST API. OpenAPI describes synchronous APIs; Kafka contracts are documented separately in publisher-owned AsyncAPI files using `docs/asyncapi-style-guide.md`.

## Document layout

Place the contract/configuration in the owning service and expose it through the gateway only as appropriate. Keep reusable schemas under `components/schemas`, reusable responses under `components/responses`, and security schemes under `components/securitySchemes`. The generated document must include title, version, server information for the local profile, tags, descriptions, examples, and operation IDs.

Use a stable operation ID such as `listProducts`, `getOrderById`, or `cancelOrder`; do not derive it from a Java method name that may change.

## Paths and methods

- All public paths begin `/api/v1` and use plural resource names.
- Path parameters are lower camel case (`{orderId}`); query parameters are lower camel case (`minPrice`).
- Document authentication for every protected operation and `CUSTOMER`/`ADMIN` behavior in its description.
- Document every response that can actually occur. Do not add a fixed response-code checklist to unrelated operations.

## Schema rules

Use request and response DTO schemas, never persistence entities. Mark required fields, formats, constraints, default values, and examples. The common success and error envelopes match `docs/api-guidelines.md`.

Canonical order states are:

```
PENDING, CONFIRMED, PAID, SHIPPED, DELIVERED, CANCELLED, FAILED
```

`traceId` is returned for support/searching but is not an authorization credential. Product schemas show `availabilityStatus`, not another service's stock count.

## Example shape

```yaml
paths:
  /api/v1/products/{productId}:
    get:
      operationId: getProductById
      tags: [Products]
      security: [{ bearerAuth: [] }]
      parameters:
        - $ref: '#/components/parameters/ProductId'
      responses:
        '200':
          description: Product found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ProductResponseEnvelope'
        '401': { $ref: '#/components/responses/Unauthorized' }
        '404': { $ref: '#/components/responses/NotFound' }
```

```yaml
components:
  schemas:
    ProductResponseEnvelope:
      type: object
      required: [data, timestamp]
      properties:
        data: { $ref: '#/components/schemas/ProductResponse' }
        timestamp: { type: string, format: date-time }
        traceId: { type: string }
```

The corresponding controller return type is `ApiResponse<ProductResponse>` (or equivalent), not a bare `ProductResponse`.

## Security schemes

Define `bearerAuth` as HTTP bearer JWT and set global security only if nearly all operations require it. Override it with `security: []` for truly public catalog reads. Keycloak OIDC discovery details belong in environment configuration, not copied as credentials into the contract.

## Contract quality gate

Before merge/local completion, validate the OpenAPI document, verify examples against DTO serialization, and add contract/integration tests for success, validation failure, authorization failure, not found, and applicable conflict responses. A public API change requires an updated OpenAPI diff and review.
