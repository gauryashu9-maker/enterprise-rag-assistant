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

### Document endpoints

The list and get endpoints are implemented. They use the authenticated principal populated by the JWT filter; the frontend must not supply a user ID to select an owner. Authentication enforcement depends on Security PR #39 being merged.

| Method and path | Behavior | Success response |
| --- | --- | --- |
| `GET /api/documents` | Return documents, newest first: a USER gets their own documents; an ADMIN gets all documents. | `200 OK`, JSON array of `DocumentResponse` objects. |
| `GET /api/documents/{id}` | Retrieve the owner's document, or any document for ADMIN. A missing document or another user's document returns 404 for a USER. | `200 OK`, one `DocumentResponse`. |
| `DELETE /api/documents/{id}` | Not implemented: safe deletion is blocked until ingestion provides document-scoped vector deletion. | TBD |

Not-found responses use a ProblemDetail. Document endpoints require Security PR #39's authenticated principal integration before they can be safely exposed.

### Ingestion status

The intended lifecycle is `UPLOADED` → `PROCESSING` → `READY` or `FAILED`. Status changes are owned by the backend ingestion service; the service integration is pending. The frontend can poll `GET /api/documents/{id}` to observe status changes. The current development seed chunks and retrieval pipeline do not carry documentId/ownerId metadata, and no vector deletion operation is available. Do not delete a document record until ingestion supports deleting its associated vectors.

## Upload

**TBD — owned by the upload/ingestion module.** The upload route, multipart field names, accepted file types and limits, response shape, and whether upload returns immediately or waits for ingestion have not been confirmed. Do not infer them from the `DocumentResponse` DTO.

## RAG / Ask

### POST /api/rag/ask

Ask a question about the uploaded documents. The answer is generated only from the retrieved document chunks.

Status: **implemented**. Authentication and per-user filtering are planned (see Authentication).

Request:

```json
{ "question": "How many days of annual leave do I get?" }
```

`question` is required and must not be blank.

200 OK:

```json
{
  "answer": "Employees get 24 days of paid annual leave per calendar year.",
  "citations": [
    {
      "documentName": "hr-policy.pdf",
      "page": 1,
      "snippet": "Employees get 24 days of paid annual leave per calendar year. Up to 5 unused days can carry over."
    }
  ]
}
```

- `citations` lists the retrieved chunks the answer was built from, best match first. The number varies (up to 5).
- `page` can be `null`. `snippet` is at most about 200 characters and ends with `...` when cut. `documentName` is `unknown` when a chunk has no filename.

When no relevant chunk is found, or the model has no answer, the response is still 200 OK:

```json
{ "answer": "I don't have that information in the uploaded documents.", "citations": [] }
```

Show this as a normal message without sources, not as an error.

400 Bad Request (blank or missing `question`), verified against the running app:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/rag/ask",
  "errors": { "question": "must not be blank" }
}
```

500 `ProblemDetail` with a generic message when the model or vector store fails (a 503 mapping is planned). 401 applies once authentication is enabled (planned).

Answers can take several seconds, so show a loading state. A streaming endpoint is planned and its contract is **TBD**.

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

**400 — Malformed request.** Missing or unreadable JSON request bodies receive a sanitized ProblemDetail response; parser details are not exposed.

```json
{
  "type": "about:blank",
  "title": "Malformed request",
  "status": 400,
  "detail": "Request body is missing or invalid"
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
