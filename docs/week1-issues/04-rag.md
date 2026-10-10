# [Week 1] RAG Engine: prompt template and retrieval skeleton
**Branch:** `feature/rag-retrieval`

**Goal:** lay the groundwork for the Week 3 RAG service.

## Tasks
- [ ] `src/main/resources/prompts/rag-prompt.st` with `{context}` and `{question}`. Instruct the model to answer ONLY from the context and say it does not know otherwise.
- [ ] `RetrievalService` wrapping `VectorStore.similaritySearch(SearchRequest...)` with configurable topK and similarity threshold.
- [ ] `RagService` interface (`answer(question)`) so others can code against it. A stub implementation is fine.
- [ ] Dev-only seed (`@Profile("dev")`) that loads a few sample `Document`s into the vector store so retrieval can be tried before ingestion is done.
- [ ] Write down in the PR what topK / threshold values give good results on the seed data.

## Done when
A question against the seeded data returns the relevant chunks, and the prompt renders correctly with a `PromptTemplate` test.


---
**Reminder:** commit every working day (small, Conventional Commits), open a PR early, merge with *Rebase and merge*. Never commit secrets.
