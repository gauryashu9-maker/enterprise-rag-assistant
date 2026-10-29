CREATE TABLE public.document_ingestion_chunks (
    document_id uuid NOT NULL
        REFERENCES public.document_records(id)
        ON DELETE CASCADE,
    chunk_index integer NOT NULL,
    content text NOT NULL,
    PRIMARY KEY (document_id, chunk_index),
    CONSTRAINT chk_document_ingestion_chunk_index
        CHECK (chunk_index >= 0)
);