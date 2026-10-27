# RAG engine: how it works and how to defend it

## Request flow (POST /api/rag/ask)

1. The controller validates the body (a non-blank question) and takes the user id from the JWT principal, never from the request.
2. `RagServiceImpl` asks `RetrievalService` for chunks: pgvector similarity search, top 5, similarity threshold 0.55, filtered to the caller's `ownerId`. Only chunks within 85% of the best score are kept.
3. No chunk found: the model is not called and the fallback sentence is returned (cheaper, and it cannot hallucinate).
4. Otherwise the chunks are numbered and injected into a prompt template that tells the model to answer only from the context and to reply as JSON (the answer plus the numbers of the chunks it used). `BeanOutputConverter` maps that into the `LlmAnswer` record.
5. Citations are built from our own chunk metadata for those numbers, so a document name or page cannot be invented. If the reply is not valid JSON, the raw text is used and all retrieved chunks are cited. A no-answer reply carries no citations.
6. The controller returns the `RagAnswer` DTO record.

Streaming (`POST /api/rag/stream`) uses the same retrieval and prompt, but streams tokens as server-sent events, then one citations event and done. Structured output cannot stream, so the stream path cites all retrieved chunks unless the model gave the fallback answer.

## Questions to be ready for

**How do IoC and dependency injection work here?** Spring creates `RagServiceImpl` and injects its collaborators through the constructor: `RetrievalService`, the `ChatClient` bean from `AiConfig`, and the prompt resource. Nothing is created with `new`, so tests pass mocks to the same constructor. Constructor injection keeps dependencies explicit and the fields final.

**Why DTOs and not entities?** Controllers return records (`RagAnswer`, `Citation`, `DocumentResponse` and others), so the JSON contract is explicit and stable, entities never leak internals, and lazy loading cannot fail during serialization.

**How is one user's data isolated?** The owner id comes from the verified token. Every chunk carries an `ownerId`, retrieval always adds the filter `ownerId == caller`, and there is no unfiltered search method. An integration test on a real pgvector database proves that two users with equally relevant chunks each get only their own, and that chunks without an owner are never returned.

**How were the retrieval settings chosen?** I logged every chunk's similarity score for relevant and off-topic questions (see `docs/rag-tuning.md`). A 0.55 threshold separated them with a thin margin, and a relative cutoff removed unrelated chunks that had pushed the small model into a wrong answer.

**What happens when the model misbehaves?** Not JSON: fall back to the raw text. Empty reply: the fallback sentence. Stream failure: one error event. The model is never called when nothing relevant is retrieved.

**Where are the transactions?** The model call is never inside a transaction because it can take seconds. Chat history is saved afterwards in a short transactional method (check this against the chat service before presenting).

## Known limits (say them before they are asked)

- Answer quality depends on the model. The local 0.5B model sometimes ignores the JSON format, and the fallback catches it. A larger model follows it.
- The thresholds were tuned on 4 sample chunks and 7 questions. Re-tune with real documents.
- Streamed answers cite all retrieved chunks, while the non-streaming answer cites only the chunks the model reports using.
- Scores belong to the embedding model. Changing it means re-embedding and re-tuning.
