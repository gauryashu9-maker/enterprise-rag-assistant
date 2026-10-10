# [Week 1] Ingestion: document extraction and upload stub
**Branch:** `feature/ingestion-extraction`

**Goal:** turn an uploaded PDF/TXT into Spring AI `Document` objects with useful metadata. (Chunking, embeddings and storage are Week 2.)

## Tasks
- [ ] Read the Spring AI Document API / ETL pipeline docs. Note what Reader, Transformer and Writer mean.
- [ ] `DocumentExtractionService`: PDFs via `PagePdfDocumentReader`, text files via `TextReader`. Metadata must include filename and page number (citations need it later).
- [ ] `FileValidator`: allowed types (pdf, txt), max 25 MB, reject empty files.
- [ ] Put 2 small sample files in `src/test/resources` and write unit tests.
- [ ] `DocumentUploadController`: `POST /api/documents` (multipart) returning an `UploadResponse` Record. It can just return extraction stats for now.

## Done when
Uploading a sample PDF returns the page count and extracted text length, and tests pass.


---
**Reminder:** commit every working day (small, Conventional Commits), open a PR early, merge with *Rebase and merge*. Never commit secrets.
