# Shared API Contract

This document is the frontend integration reference for confirmed backend behavior and planned endpoints. Unconfirmed module-owned contracts are marked **TBD**. Examples are illustrative JSON; timestamps use ISO-8601 UTC.

## Documents

### Document response

The confirmed response DTO is `DocumentResponse`. UUIDs are serialized as strings, status values use the enum names below, and `uploadedAt` is an ISO-8601 timestamp.

```json
{
  "id": "8ca57f15-4358-4bc6-a8af-614eab659cf4",
  "filename": "employee-handbook.pdf",
  "status": "READY",
  "uploadedAt": "2026-10-15T12:30:00Z",
  "sizeBytes": 2048
}
```

`sizeBytes` is an optional number in the agreed contract. The current persistence entity stores it as a non-null primitive `long`, so `DocumentResponse.fromEntity` always supplies it. `errorMessage` is an optional string; null response properties are omitted by Jackson (`@JsonInclude(NON_NULL)`). For a failed document, the response can include:

```json
{
  "id": "8ca57f15-4358-4bc6-a8af-614eab659cf4",
  "filename": "employee-handbook.pdf",
  "status": "FAILED",
  "uploadedAt": "2026-10-15T12:30:00Z",
  "sizeBytes": 2048,
  "errorMessage": "Unsupported document format"
}
```

Status values: `UPLOADED`, `PROCESSING`, `READY`, `FAILED`.

### Planned document endpoints

**These HTTP endpoints are PLANNED and are not implemented yet.** The backend currently has owner-scoped repository queries, but no document controller. The response examples above describe the DTO, not a live HTTP response.

| Method and path | Planned behavior | Planned success response |
| --- | --- | --- |
| `GET /api/documents` | Return the authenticated user's documents, newest first. | `200 OK`, JSON array of `DocumentResponse` objects. |
| `GET /api/documents/{id}` | Retrieve a document owned by the authenticated user. | `200 OK`, one `DocumentResponse`. |
| `DELETE /api/documents/{id}` | Delete an owned document. | `204 No Content`. |

Authentication and exact not-found behavior for these endpoints depend on the pending Security/API integration.

### Ingestion status

The intended lifecycle is `UPLOADED` → `PROCESSING` → `READY` or `FAILED`. Status changes are owned by the backend ingestion service; the service integration is pending. Once document endpoints are implemented, the frontend will poll `GET /api/documents/{id}` to observe status changes. Deletion of associated vector chunks is also pending.

## Upload

**TBD — owned by the upload/ingestion module.** The upload route, multipart field names, accepted file types and limits, response shape, and whether upload returns immediately or waits for ingestion have not been confirmed. Do not infer them from the `DocumentResponse` DTO.

## RAG / Ask

**TBD — owned by the RAG module.** The backend currently exposes `POST /api/rag/ask` and its request DTO requires a non-blank `question`. Confirm the frontend request/response contract, citations, error behavior, and authentication requirements with the RAG owner before relying on this route.

## Authentication

Status: agreed contract. The Security role implements it; until then the frontend uses mock auth.

### POST /api/auth/login

Request:
```json
{ "email": "user@example.com", "password": "secret123" }
```

200 OK:
```json
{ "token": "<jwt>", "email": "user@example.com", "role": "USER" }
```

401 (wrong email or password), `ProblemDetail`:
```json
{ "type": "about:blank", "title": "Unauthorized", "status": 401, "detail": "Invalid email or password", "instance": "/api/auth/login" }
```

400: blank or invalid fields, `ProblemDetail`.

### POST /api/auth/register

Request: `{ "email": "...", "password": "..." }` (password at least 8 characters)

201 Created: `{ "id": "<uuid>", "email": "...", "role": "USER" }`

409: email already registered, `ProblemDetail`. 400: invalid fields, `ProblemDetail`.

### Using the token

Send `Authorization: Bearer <token>` on every request except `/api/auth/**`.
A missing, invalid or expired token returns 401 with a `ProblemDetail`.

Roles: `USER` can chat. `ADMIN` can also upload and delete documents.
Dev-only test accounts will be seeded by the Security role and listed here.

## Errors

The backend `GlobalExceptionHandler` returns Spring `ProblemDetail` responses with `type`, `title`, `status`, and `detail`; validation responses also include an `errors` extension mapping field names to validation messages. The `type` is `about:blank` unless set otherwise. The handler does not set `instance`, and null `instance` is omitted from the JSON.

### Confirmed handler responses

**400 — Validation failed.** The `errors` extension contains field-level messages collected from Spring validation errors.

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request validation failed",
  "errors": {
    "question": "must not be blank"
  }
}
```

**404 — Resource not found.** `detail` contains the `ResourceNotFoundException` message.

```json
{
  "type": "about:blank",
  "title": "Resource not found",
  "status": 404,
  "detail": "Document not found"
}
```

**500 — Unexpected error.** Internal exception details are not returned.

```json
{
  "type": "about:blank",
  "title": "Internal Server Error",
  "status": 500,
  "detail": "An unexpected error occurred"
}
```

**503 — Required service unavailable or database access failure.** Internal exception details are not returned.

```json
{
  "type": "about:blank",
  "title": "Service unavailable",
  "status": 503,
  "detail": "A required service is temporarily unavailable"
}
```

### Authentication-related statuses

`401 Unauthorized` and `403 Forbidden` may apply once authentication and authorization are enabled. Their response bodies and ProblemDetail behavior are **TBD with the Security module**; they are not produced by the current `GlobalExceptionHandler`. Do not assume the examples above describe those responses.
