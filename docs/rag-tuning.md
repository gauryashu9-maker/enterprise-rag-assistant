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

## Live check at threshold 0.55
  - Q2, Q3, Q4 answered correctly. Q5 (cricket), Q6 (dress code), Q7 (joke) fell back correctly with no citations.
  - Q1 answered WRONGLY ("156 days") because 3 chunks passed, 2 of them unrelated, and the 0.5B chat model mixed them up and invented arithmetic.
  - Conclusion: the threshold stops off-topic questions, but noisy context still hurts weak models.
  - Next: keep only chunks within ~85% of the best score, and re-test with a larger chat model.

## Next idea

Keep only chunks scoring within about 85% of the best chunk. On this data that gives exactly one citation for each real question.
## Live results with the relative cutoff (0.85 of best score)

- Date: 2026-10-17
- Same setup: nomic-embed-text, qwen2.5:0.5b, topK 5, similarity-threshold 0.55
- Change: chunks scoring below 85% of the best retrieved chunk are dropped

| Question | Before cutoff | After cutoff |
|---|---|---|
| Q1 annual leave | WRONG answer (156 days), 3 citations (2 unrelated) | Correct (24 days), 1 citation (page 1) |
| Q2 work from home | Correct, 1 citation | Correct, 1 citation (page 2) |
| Q3 expense deadline | Correct, 2 citations (1 unrelated) | Correct, 1 citation (page 3) |
| Q4 laptop return | Correct, 1 citation | Correct, 1 citation (page 4) |
| Q5 cricket (off-topic) | Fallback | Fallback |
| Q6 dress code (off-topic) | Fallback | Fallback |
| Q7 joke (off-topic) | Fallback | Fallback |

Why Q1 was wrong before: unrelated chunks (remote work, laptop) reached the 0.5B model, which mixed them and invented arithmetic. Removing weak chunks fixed the wrong answer, not just the citations.

Limits:
- 0.85 was chosen from only 4 chunks and 7 questions. Re-check with real uploaded documents.
- The ratio is a constant in RetrievalService. Make it a config property if it needs tuning per environment.
- Scores are specific to nomic-embed-text. Re-run this experiment if the team standardizes on another embedding model.
- Answer wording still comes from a tiny model and can be sloppy.
