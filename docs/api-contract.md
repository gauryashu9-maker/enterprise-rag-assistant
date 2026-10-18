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
| `GET /api/documents` | Return documents, newest first: a USER gets their own documents, an ADMIN gets all documents. | `200 OK`, JSON array of `DocumentResponse` objects. |
| `GET /api/documents/{id}` | Retrieve a document: the owner, or any document for ADMIN. A document of another user returns 404 for a USER. | `200 OK`, one `DocumentResponse`. |
| `DELETE /api/documents/{id}` | Delete a document: the owner, or any document for ADMIN. A document of another user returns 404 for a USER. | `204 No Content`. |

Authentication is planned (see the Authentication section). Not-found behavior is decided: a missing document, or a document of another user (for a USER), returns 404 with a ProblemDetail.

### Ingestion status

The intended lifecycle is `UPLOADED` → `PROCESSING` → `READY` or `FAILED`. Status changes are owned by the backend ingestion service; the service integration is pending. Once document endpoints are implemented, the frontend will poll `GET /api/documents/{id}` to observe status changes. Deletion of associated vector chunks is also pending. Planned design: every chunk carries documentId and ownerId metadata, and DELETE removes the document chunks first, then the record, in one transactional service method (retrying a failed delete is safe).

## Upload

**TBD — owned by the upload/ingestion module.** The upload route, multipart field names, accepted file types and limits, response shape, and whether upload returns immediately or waits for ingestion have not been confirmed. Do not infer them from the `DocumentResponse` DTO.

## RAG / Ask

**TBD — owned by the RAG module.** The backend currently exposes `POST /api/rag/ask` and its request DTO requires a non-blank `question`. Confirm the frontend request/response contract, citations, error behavior, and authentication requirements with the RAG owner before relying on this route.

## Authentication

**PLANNED, not implemented yet.** The authentication endpoints below are the agreed contract for the Security module. Until they exist, the frontend uses mock auth.

| Item | Status |
|---|---|
| `POST /api/auth/login` | Planned |
| `POST /api/auth/register` | Planned |
| Bearer token on protected endpoints | Planned |
| Owner-scoped document access, ADMIN can manage all documents | Planned |
| 401 response from the Security module (example below) | Planned; the shape is a target, not live behavior |
| `ProblemDetail` format for validation and server errors | Implemented (`GlobalExceptionHandler`) |

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

Roles: `USER` can upload, list, delete and chat over their own documents. `ADMIN` can additionally list, read and delete any user's documents (chat stays owner-scoped for everyone). `register` always creates a `USER`; `ADMIN` accounts are seeded for dev. The JWT subject (`sub`) is the user's UUID, with `email` and `role` claims. Another user's document returns 404, not 403.
Dev-only test accounts will be seeded by the Security role and listed here.

## Errors

The backend `GlobalExceptionHandler` returns Spring `ProblemDetail` responses with `type`, `title`, `status`, `detail`, and `instance`; validation responses also include an `errors` extension mapping field names to validation messages. The `type` is `about:blank` unless set otherwise. `instance` contains the request path (for example `/api/rag/ask`), verified against the running app.

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

`401 Unauthorized` and `403 Forbidden` apply once authentication and authorization are enabled. The planned `401` response for login is described in the Authentication section above (planned, not implemented). The `403` response body is **TBD with the Security module**. Neither is produced by the current `GlobalExceptionHandler`, so do not assume the validation and not-found examples describe them.
