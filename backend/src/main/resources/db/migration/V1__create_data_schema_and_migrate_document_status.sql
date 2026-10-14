CREATE TABLE IF NOT EXISTS public.chat_sessions (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL
);

CREATE TABLE IF NOT EXISTS public.chat_messages (
    id uuid PRIMARY KEY,
    role varchar(255) NOT NULL,
    content text NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    session_id uuid NOT NULL REFERENCES public.chat_sessions (id)
);

CREATE TABLE IF NOT EXISTS public.document_records (
    id uuid PRIMARY KEY,
    filename varchar(255) NOT NULL,
    content_type varchar(255) NOT NULL,
    size_bytes bigint NOT NULL,
    status varchar(255) NOT NULL,
    chunk_count integer NOT NULL,
    uploaded_by uuid NOT NULL,
    uploaded_at timestamp(6) with time zone NOT NULL
);

ALTER TABLE public.document_records
    ADD COLUMN IF NOT EXISTS error_message text;

UPDATE public.document_records
SET status = 'UPLOADED'
WHERE status = 'UPLOADING';
