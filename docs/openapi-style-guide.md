# OpenAPI Style Guide

**Project:** Enterprise Microservice Platform  
**Version:** 1.0

---

# 1. Purpose

This document defines the standards for documenting REST APIs using the OpenAPI Specification (OAS 3.x).

Every REST endpoint must be fully documented so that developers, testers, API consumers, and AI coding agents can understand the API without reading the implementation.

---

# 2. OpenAPI Version

Use

```
OpenAPI 3.1
```

Documentation UI

```
Swagger UI
```

---

# 3. API Information

Every service should expose API metadata.

Example

```yaml
openapi: 3.1.0

info:
  title: Product Service API
  description: Product Catalog APIs
  version: 1.0.0
```

---



# 4. API Tags

Group APIs by business capability.

Good

```
Customer

Product

Order

Inventory

Payment

Shipping
```

Bad

```
API

Service

Controller
```

Example

```java
@Tag(
    name = "Products",
    description = "Product management APIs"
)
```

---



# 5. Operation Summary

Every endpoint must have

- Summary
- Description

Example

```java
@Operation(
    summary = "Create Product",
    description = "Creates a new product in the catalog."
)
```

Good summaries are short.

Descriptions explain business behavior.

---



# 6. Operation ID

Every endpoint should define a unique operationId.

Good

```
createProduct

updateProduct

deleteProduct

findProductById
```

Avoid

```
method1

save

controllerMethod
```

---



# 7. Parameters

Every path parameter must be documented.

Example

```java
@Parameter(
    description = "Unique Product ID",
    example = "1001"
)
```

Example endpoint

```
GET /api/v1/products/{id}
```

---



# 8. Query Parameters

Document

- meaning
- default value
- examples

Example

```java
@Parameter(
    description = "Page number",
    example = "0"
)
```

```
GET /products?page=0&size=20
```

---



# 9. Request Body

Always describe request payload.

Example

```java
@RequestBody(
    description = "Product information",
    required = true
)
```

Include examples.

```json
{
  "name": "Laptop",
  "category": "Electronics",
  "price": 80000
}
```

---



# 10. Response Documentation

Every endpoint should document all response codes.

Minimum

```
200

201

400

401

403

404

409

500
```

Example

```java
@ApiResponses({

@ApiResponse(responseCode="201",
description="Product created"),

@ApiResponse(responseCode="400",
description="Validation failed"),

@ApiResponse(responseCode="404",
description="Product not found")

})
```

---



# 11. Response Schema

Always define response DTOs.

Example

```java
class ProductResponse {

Long id;

String name;

BigDecimal price;

}
```

Avoid exposing Entity classes.

---



# 12. Error Responses

Every API must document error responses.

Example

```json
{
  "timestamp": "2026-07-14T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Product not found",
  "traceId": "3fd9ab34"
}
```

---



# 13. Examples

Every endpoint should include request and response examples.

Good

```json
{
  "customerName": "John Smith",
  "email": "john@email.com"
}
```

Avoid empty examples.

---



# 14. Schemas

Document every DTO.

Good

```java
@Schema(
description="Product Name",
example="Laptop"
)
```

Example

```java
@Schema(
description="Product price",
example="79999.99"
)
```

---



# 15. Validation Documentation

Validation annotations should be reflected.

Example

```java
@NotBlank

@Email

@NotNull

@Positive
```

Swagger should clearly indicate

- required fields
- constraints

---



# 16. Security Documentation

Document authentication.

Example

```yaml
security:
  - bearerAuth: []
```

Security Scheme

```
JWT Bearer Token
```

---



# 17. Authorization Header

Document

```
Authorization

Bearer eyJhbGc...
```

---



# 18. Pagination

Document pagination parameters.

```
page

size

sort
```

Example

```
GET /products?page=0&size=20&sort=name,asc
```

---



# 19. Filtering

Document optional filters.

Example

```
GET /products?category=Electronics
```

```
GET /orders?status=SHIPPED
```

---



# 20. Sorting

Document sortable fields.

Example

```
sort=name

sort=price

sort=createdDate
```

---



# 21. Enum Documentation

Every enum should be documented.

Example

```java
enum OrderStatus {

NEW

PAID

SHIPPED

DELIVERED

CANCELLED

}
```

---



# 22. Deprecation

Deprecated endpoints must be marked.

Example

```java
@Operation(
deprecated = true
)
```

---



# 23. Common Response Wrapper

Responses should use standard wrapper.

Example

```json
{
  "data": {
    "id": 10,
    "name": "Laptop"
  },
  "timestamp": "2026-07-14T10:10:00Z"
}
```

---



# 24. Error Schema

All services should use the same error schema.

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "...",
  "message": "...",
  "path": "...",
  "traceId": "..."
}
```

---



# 25. Naming Standards

DTOs

```
CreateProductRequest

UpdateProductRequest

ProductResponse

ProductSummaryResponse
```

Avoid

```
ProductDTO

ResponseDTO

DataDTO
```

---



# 26. Media Types

Request

```
application/json
```

Response

```
application/json
```

File Upload

```
multipart/form-data
```

---



# 27. OpenAPI Package Structure

Recommended

```
controller/

dto/

mapper/

config/

exception/

model/
```

OpenAPI configuration

```
config/OpenApiConfig.java
```

---



# 28. Swagger UI

Expose

```
/swagger-ui.html
```

OpenAPI JSON

```
/v3/api-docs
```

---



# 29. API Documentation Checklist

Every endpoint should document

- Summary
- Description
- Tag
- Request Body
- Path Parameters
- Query Parameters
- Success Responses
- Error Responses
- Security
- Examples
- Validation
- Response Schema

---



# 30. Example Endpoint

```java
@Tag(name = "Products")

@RestController

@RequestMapping("/api/v1/products")
public class ProductController {

    @Operation(
        summary = "Create Product",
        description = "Creates a new product."
    )

    @ApiResponses({

        @ApiResponse(
            responseCode = "201",
            description = "Product Created"
        ),

        @ApiResponse(
            responseCode = "400",
            description = "Validation Failed"
        )

    })

    @PostMapping

    public ProductResponse createProduct(

        @Valid

        @RequestBody

        CreateProductRequest request

    ) {

        ...
    }

}
```

---



# 31. AI Coding Rules

When generating APIs, AI agents should:

- Add `@Tag` to every controller.
- Add `@Operation` to every endpoint.
- Add `@ApiResponses` for all expected HTTP responses.
- Document request bodies, parameters, and response schemas.
- Include realistic request and response examples.
- Define JWT bearer authentication where required.
- Use DTOs instead of JPA entities in API contracts.
- Reflect validation constraints in the generated OpenAPI documentation.
- Keep OpenAPI documentation synchronized with implementation whenever endpoints change.

---



# 32. Future Enhancements

As the platform evolves, OpenAPI documentation may include:

- API version-specific documentation
- Multiple server environments (Local, QA, UAT, Production)
- Reusable components for common headers and error responses
- Links between related operations
- Webhook documentation for Kafka event consumers
- Generated SDKs for Java, TypeScript, and Python
- API linting and validation in the CI/CD pipeline

