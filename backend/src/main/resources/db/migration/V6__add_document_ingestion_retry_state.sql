
ALTER TABLE public.document_records
    ADD COLUMN IF NOT EXISTS retry_count integer NOT NULL DEFAULT 0;

ALTER TABLE public.document_records
    ADD COLUMN IF NOT EXISTS next_retry_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_document_records_recovery
    ON public.document_records (status, next_retry_at);
