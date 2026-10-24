ALTER TABLE public.chat_messages
    ADD COLUMN sequence_number integer;

WITH ordered_messages AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY session_id
            ORDER BY created_at ASC, id ASC
        ) - 1 AS message_sequence
    FROM public.chat_messages
)
UPDATE public.chat_messages AS message
SET sequence_number = ordered_messages.message_sequence
FROM ordered_messages
WHERE message.id = ordered_messages.id;

ALTER TABLE public.chat_messages
    ALTER COLUMN sequence_number SET NOT NULL;

CREATE UNIQUE INDEX uq_chat_messages_session_sequence
    ON public.chat_messages (session_id, sequence_number);