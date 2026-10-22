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

## Chat conversation data

The backend data layer provides `ChatConversationResponse` for an authenticated user's conversation. It contains the existing `ChatSessionResponse` under `session` and an ordered `messages` array of existing `ChatMessageResponse` values. The same owner-only rule applies to USER and ADMIN; a conversation owned by another account or a missing conversation is not found. No HTTP route is defined here.

```json
{
  "session": {
    "id": "12345678-1234-5678-1234-567812345678",
    "userId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
    "createdAt": "2026-10-20T10:00:00Z"
  },
  "messages": [
    {
      "id": "11111111-2222-3333-4444-555555555555",
      "sessionId": "12345678-1234-5678-1234-567812345678",
      "role": "USER",
      "content": "How many days off?",
      "createdAt": "2026-10-20T10:00:01Z"
    },
    {
      "id": "66666666-7777-8888-9999-aaaaaaaaaaaa",
      "sessionId": "12345678-1234-5678-1234-567812345678",
      "role": "ASSISTANT",
      "content": "You get 24 days.",
      "createdAt": "2026-10-20T10:00:02Z"
    }
  ]
}
```

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

500 `ProblemDetail` with a generic message when the model or vector store fails (a 503 mapping is planned). Protected endpoints return 401 when the bearer token is missing, invalid, or expired.

Answers can take several seconds, so show a loading state. A streaming endpoint is planned and its contract is **TBD**.

### POST /api/rag/stream

Same question as `/api/rag/ask`, but the answer arrives as server-sent events while it is being generated.

Status: **implemented**. Requires `Authorization: Bearer <token>`. Send `Accept: text/event-stream`.

Request body: `{ "question": "..." }` (required, not blank), same as `/api/rag/ask`.

Events, in this order:

| `event` | `data` (JSON) | When |
| --- | --- | --- |
| `token` | `{ "text": " days" }` | Many times. Append `text` to the answer as it arrives and keep spaces as they are |
| `citations` | `{ "citations": [ { "documentName": "hr-policy.pdf", "page": 1, "snippet": "..." } ] }` | Once, after the last token. Empty when there was no answer |
| `done` | `{}` | Last event of a normal answer |
| `error` | `{ "detail": "The answer could not be generated." }` | Instead of citations and done, if generation fails midway |

Notes for the frontend:

- Use `fetch` with a streaming reader, not `EventSource`: `EventSource` cannot send a POST body or an `Authorization` header.
- When nothing relevant is found, the stream is one `token` event with the fallback sentence, an empty `citations` event, then `done`.
- A missing or invalid token returns a normal 401 `ProblemDetail` before any event is sent.

## Authentication

**Authentication endpoints are planned.** JWT authentication for protected endpoints is implemented; until login and registration exist, the frontend uses mock auth.

| Item | Status |
|---|---|
| `POST /api/auth/login` | Planned |
| `POST /api/auth/register` | Planned |
| JWT authentication on protected endpoints | Implemented |
| Owner-scoped document access, ADMIN can manage all documents | Planned |
| 401 response for missing, invalid, or expired bearer tokens | Implemented |
| `ProblemDetail` format for validation and server errors | Implemented (`GlobalExceptionHandler`) |

Protected endpoints require a bearer JWT. The verified JWT subject is the user's UUID, with `email` and `role` claims. Tokens expire after 15 minutes. Configure `JWT_SECRET` as a Base64-encoded value of at least 32 random bytes; the application fails to start if it is missing, malformed, or too short. Missing, invalid, or expired tokens return a sanitized 401 `ProblemDetail`. Authenticated principals use the shared `AuthenticatedUser(UUID id, String email, Role role)` contract.

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

Send `Authorization: Bearer <jwt>` on every protected request except `/api/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`, and `/actuator/health`.
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

Missing, invalid, malformed, expired, or tampered bearer tokens return a sanitized `401 Unauthorized` `ProblemDetail`. An authenticated user who lacks the required authority receives a sanitized `403 Forbidden` `ProblemDetail`. Both responses include the request path in `instance` and omit token contents and internal exception details. These responses are produced by the security handlers, not `GlobalExceptionHandler`.
