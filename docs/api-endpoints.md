# API Endpoints Reference

This document provides a quick reference for the core REST endpoints exposed by the Spring Boot backend.

## Authentication
*   **`POST /api/auth/register`** - Creates a new user account.
*   **`POST /api/auth/login`** - Authenticates a user and returns a JWT token.

## Document Management (Requires JWT)
*   **`POST /api/documents/upload`** - Uploads a PDF or TXT file, triggers the ETL pipeline (chunking & embedding).
*   **`GET /api/documents`** - Lists all documents belonging to the authenticated user.
*   **`DELETE /api/documents/{id}`** - Deletes a document and its associated vectors from pgvector.

## RAG & Chat (Requires JWT)
*   **`POST /api/rag/ask`** - Submits a question and returns a synchronous JSON response with the answer and citations.
*   **`POST /api/rag/stream`** - Submits a question and returns a Server-Sent Event (SSE) stream for real-time typing.
*   **`GET /api/chat/sessions`** - Lists all previous chat sessions for the user.
*   **`GET /api/chat/sessions/{id}`** - Retrieves the message history for a specific session.

## System
*   **`GET /actuator/health`** - Health check endpoint for the backend.
*   **`GET /swagger-ui/index.html`** - Auto-generated OpenAPI/Swagger UI documentation.
