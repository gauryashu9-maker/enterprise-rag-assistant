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

The document endpoints are implemented and security-enforced by the stateless JWT filter chain. The frontend must send `Authorization: Bearer <jwt>` on every document request; the authenticated principal is extracted from the token — the frontend must not supply a user ID to select an owner.

| Method and path | Status | Behavior | Success response |
| --- | --- | --- | --- |
| `GET /api/documents` | Implemented | Return documents, newest first: a USER gets their own documents; an ADMIN gets all documents. | `200 OK`, JSON array of `DocumentResponse` objects. |
| `GET /api/documents/{id}` | Implemented | Retrieve the owner's document, or any document for ADMIN. A missing document or another user's document returns 404 for a USER. | `200 OK`, one `DocumentResponse`. |
| `DELETE /api/documents/{id}` | Implemented | Delete a document and its vector chunks. Authorizes first: a USER can only delete owned documents (cross-owner returns 404); an ADMIN can delete any document. Deletes vector chunks by `documentId` filter first, then removes the database record. If vector deletion fails, returns 503 and preserves the database record. | `204 No Content`. |

All document endpoints require a valid bearer JWT. A missing, invalid, expired, or tampered token returns `401 Unauthorized` with a `ProblemDetail` whose `instance` field contains the exact request path. A not-found response (including cross-owner access) uses a `404 ProblemDetail`. Security enforcement is verified by `DocumentSecurityIntegrationTest`.

### Chat Session and Conversation Endpoints

The chat session and conversation endpoints are implemented and security-enforced by the stateless JWT filter chain. Every request requires `Authorization: Bearer <jwt>`. The authenticated caller's identity is extracted from the token. Strict owner isolation is enforced for all roles (including `ADMIN`): a user can only access their own sessions and conversations; accessing a missing session or another user's session returns `404 Not Found`.

| Method and path | Status | Behavior | Success response |
| --- | --- | --- | --- |
| `POST /api/chat/sessions` | Implemented | Create a new chat session for the authenticated user. Returns a `Location` header pointing to the created session resource. | `201 Created`, one `ChatSessionResponse`. |
| `GET /api/chat/sessions` | Implemented | List all chat sessions belonging to the authenticated user, newest first (`createdAt` descending). | `200 OK`, JSON array of `ChatSessionResponse` objects. |
| `GET /api/chat/sessions/{id}` | Implemented | Retrieve the session and its chronological message history (`createdAt ASC, id ASC`). Cross-owner or missing sessions return 404 for all roles. | `200 OK`, one `ChatConversationResponse`. |
| `POST /api/chat/sessions/{id}/messages` | Implemented | Send a user message within an owned chat session. Generates an AI answer via RAG and persists both the USER question and ASSISTANT answer with resilient retry. If persistence fails after retries, the generated answer is still returned successfully. Cross-owner or missing sessions return 404 for all roles. | `200 OK`, one `SendChatMessageResponse`. |

All chat endpoints require a valid bearer JWT. A missing, invalid, or expired token returns `401 Unauthorized` with a `ProblemDetail`. A missing or cross-owner conversation returns a `404 ProblemDetail`. Blank or missing message payloads return a `400 Validation ProblemDetail`.

### Chat DTO JSON shapes

`SendChatMessageRequest`:

```json
{
  "message": "How many days of paid leave do I get?"
}
```

`SendChatMessageResponse`:

```json
{
  "userMessage": {
    "id": "11111111-2222-3333-4444-555555555555",
    "sessionId": "22222222-2222-4222-8222-222222222222",
    "role": "USER",
    "content": "How many days of paid leave do I get?",
    "createdAt": "2026-10-24T10:00:00Z"
  },
  "assistantMessage": {
    "id": "66666666-7777-8888-9999-aaaaaaaaaaaa",
    "sessionId": "22222222-2222-4222-8222-222222222222",
    "role": "ASSISTANT",
    "content": "Employees get 24 days of paid annual leave.",
    "createdAt": "2026-10-24T10:00:01Z"
  },
  "citations": [
    {
      "documentName": "hr-policy.pdf",
      "page": 1,
      "snippet": "Employees get 24 days of paid annual leave per calendar year."
    }
  ]
}
```

> **Best-Effort History Persistence**: RAG generation executes outside database transactions to avoid holding HikariCP connections during model inference. Persistence of user and assistant messages is attempted with up to 3 total attempts for transient database failures (`TransientDataAccessException`). If database persistence fails or retries are exhausted, the endpoint still returns `200 OK` with the generated answer and citations so users never lose a completed response. In that unpersisted scenario, `userMessage.id` and `assistantMessage.id` are returned as `null` to explicitly indicate that the messages were not persisted to database history.

`ChatSessionResponse`:

```json
{
  "id": "22222222-2222-4222-8222-222222222222",
  "userId": "11111111-1111-4111-8111-111111111111",
  "createdAt": "2026-10-20T10:30:00Z"
}
```

`ChatMessageResponse`:

```json
{
  "id": "33333333-3333-4333-8333-333333333333",
  "sessionId": "22222222-2222-4222-8222-222222222222",
  "role": "ASSISTANT",
  "content": "The policy is documented.",
  "createdAt": "2026-10-20T10:31:00Z"
}
```

### Ingestion status

The intended lifecycle is `UPLOADED` → `PROCESSING` → `READY` or `FAILED`. Status changes are owned by the backend ingestion service; the service integration is pending. The frontend can poll `GET /api/documents/{id}` to observe status changes. The current development seed chunks and retrieval pipeline do not carry documentId/ownerId metadata, and no vector deletion operation is available. Do not delete a document record until ingestion supports deleting its associated vectors.

## Upload

**TBD — owned by the upload/ingestion module.** The upload route, multipart field names, accepted file types and limits, response shape, and whether upload returns immediately or waits for ingestion have not been confirmed. Do not infer them from the `DocumentResponse` DTO.

## Chat conversation data

The backend provides `ChatConversationResponse` via `GET /api/chat/sessions/{id}` for an authenticated user's conversation. It contains `ChatSessionResponse` under `session` and an ordered `messages` array of `ChatMessageResponse` values. The same owner-only rule applies to USER and ADMIN; a conversation owned by another account or a missing conversation is not found (404 ProblemDetail).

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
- How citations are chosen: the model is asked for structured JSON (the answer plus the numbers of the context chunks it used). The citations are then built from our own chunk metadata for those numbers, so a source name or page can never be invented. If the model does not return valid JSON, its raw text is used as the answer and all retrieved chunks are cited.
- Scope: answers use only the documents owned by the authenticated user. The user id comes from the token, never from the request, so another user's documents are never retrieved.

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
  "detail": "Request body is missing or invalid",
  "instance": "/api/rag/ask"
}
```

**400 — Type mismatch.** Malformed path variables or request parameters (such as an unparseable UUID) receive a sanitized ProblemDetail response with the parameter name; internal converter details, stack traces, and submitted values are not exposed.

```json
{
  "type": "about:blank",
  "title": "Type mismatch",
  "status": 400,
  "detail": "Failed to convert parameter 'id'",
  "instance": "/api/documents/invalid-uuid"
}
```

**404 — Resource not found.** `detail` contains the `ResourceNotFoundException` message.

```json
{
  "type": "about:blank",
  "title": "Resource not found",
  "status": 404,
  "detail": "Document not found",
  "instance": "/api/documents/8ca57f15-4358-4bc6-a8af-614eab659cf4"
}
```

**500 — Unexpected error.** Internal exception details are not returned.

```json
{
  "type": "about:blank",
  "title": "Internal Server Error",
  "status": 500,
  "detail": "An unexpected error occurred",
  "instance": "/api/rag/ask"
}
```

**503 — Required service unavailable or database access failure.** Internal exception details are not returned.

```json
{
  "type": "about:blank",
  "title": "Service unavailable",
  "status": 503,
  "detail": "A required service is temporarily unavailable",
  "instance": "/api/rag/ask"
}
```

### Authentication-related statuses

Missing, invalid, malformed, expired, or tampered bearer tokens return a sanitized `401 Unauthorized` `ProblemDetail`. An authenticated user who lacks the required authority receives a sanitized `403 Forbidden` `ProblemDetail`. Both responses include the request path in `instance` and omit token contents and internal exception details. These responses are produced by the security handlers, not `GlobalExceptionHandler`.
