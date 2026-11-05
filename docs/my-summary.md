# Module Summary: RAG Engine & Chat UI

1. **RAG Engine Architecture:** I built the `RetrievalService` to handle the filtered vector search (cosine similarity, topK 5, threshold 0.55). I built `RagServiceImpl` to orchestrate the prompt and the AI model call.
2. **Hallucination Prevention:** I implemented a relative cutoff (85%) to drop irrelevant chunks. If the search yields nothing, we don't call the model at all, returning a safe fallback.
3. **Citation Accuracy:** The AI returns `LlmAnswer` (text + chunk numbers). Our backend builds the citations from pgvector metadata, so the AI can never invent a fake document name.
4. **Real-Time Streaming:** Built the backend to return `Flux<ServerSentEvent>` (token, citations, done events). Used a custom `fetch` stream reader on the frontend to handle the POST body and JWT headers.
5. **Team Leadership:** Fixed the UUID ingestion bug, wrote scripts to unblock the team during auth loops, and reassigned tasks when members went inactive to ensure we hit the 4-week deadline.
