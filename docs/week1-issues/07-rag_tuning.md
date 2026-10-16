# RAG retrieval tuning (live test)

- Date: 2026-10-15
- Embedding model: nomic-embed-text (Ollama), 768 dimensions
- Chat model: qwen2.5:0.5b (Ollama)
- Data: 4 sample chunks (hr-policy.pdf, pages 1-4) loaded by DevDataSeeder
- topK: 5 (only 4 chunks exist). Threshold set to 0.0 to log every score
- Score = cosine similarity of each retrieved chunk (higher = closer)

## Scores per question (all 4 chunks, best first)

| Question | Right chunk | Scores |
|---|---|---|
| Q1 How many days of annual leave do I get? | page 1 | 0.799, 0.579, 0.565, 0.548 |
| Q2 Can I work from home? | page 2 | 0.573, 0.467, 0.414, 0.370 |
| Q3 What is the deadline for expense claims? | page 3 | 0.743, 0.574, 0.547, 0.498 |
| Q4 When do I give back my laptop? | page 4 | 0.658, 0.507, 0.463, 0.459 |
| Q5 Who won the cricket world cup? (off-topic) | none | 0.352, 0.335, 0.322, 0.282 |
| Q6 What is the company dress code? (off-topic) | none | 0.524, 0.522, 0.498, 0.492 |
| Q7 Tell me a joke (off-topic) | none | 0.317, 0.316, 0.311, 0.298 |

## Chunks passing each threshold

| Threshold | Q1 | Q2 | Q3 | Q4 | Q5 | Q6 | Q7 | Verdict |
|---|---|---|---|---|---|---|---|---|
| 0.40 | 4 | 3 | 4 | 4 | 0 | 4 | 0 | Dress code passes 4 irrelevant chunks |
| 0.50 | 4 | 1 | 3 | 2 | 0 | 2 | 0 | Dress code still passes |
| 0.55 | 3 | 1 | 2 | 1 | 0 | 0 | 0 | Best: all real questions answer, all off-topic fall back |
| 0.60 | 1 | 0 | 1 | 1 | 0 | 0 | 0 | Q2 (remote work) wrongly falls back |

## Decision

similarity-threshold = 0.55, topK = 5.

Known limits:
- Margin is thin (lowest correct 0.573 vs highest off-topic 0.524).
- Q1 and Q3 still cite 2 and 1 unrelated chunks at 0.55.
- Tiny sample (4 chunks, 7 questions). Re-tune with real uploaded documents.
- Scores are specific to nomic-embed-text. Re-run this experiment if the team changes embedding model.

## Next idea

Keep only chunks scoring within about 85% of the best chunk. On this data that gives exactly one citation for each real question.