// Which streaming endpoint the chat page uses.
// true:  /api/chat/sessions/{id}/messages/stream (streams AND saves the exchange to the history)
// false: /api/rag/stream (streams only, nothing is saved)
export const STREAM_AND_SAVE = false;

export function streamTarget(sessionId, saveHistory = STREAM_AND_SAVE) {
  return saveHistory
    ? { path: `/chat/sessions/${encodeURIComponent(sessionId)}/messages/stream`, bodyField: 'message' }
    : { path: '/rag/stream', bodyField: 'question' }
}