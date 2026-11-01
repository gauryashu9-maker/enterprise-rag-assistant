// Chat endpoints on top of an axios-like client ({ get, post }), so they can be tested without a browser.

export function createChatApi(http) {
  return {
    async createSession() {
      const { data } = await http.post('/chat/sessions')
      return data
    },

    async listSessions() {
      const { data } = await http.get('/chat/sessions')
      return Array.isArray(data) ? data : []
    },

    async getConversation(sessionId) {
      const { data } = await http.get(`/chat/sessions/${encodeURIComponent(sessionId)}`)
      return {
        session: data?.session ?? null,
        messages: Array.isArray(data?.messages) ? data.messages : [],
      }
    },

    async sendMessage(sessionId, message) {
      const { data } = await http.post(`/chat/sessions/${encodeURIComponent(sessionId)}/messages`, { message })
      return { ...data, citations: Array.isArray(data?.citations) ? data.citations : [] }
    },
  }
}

/** The best human-readable text for a failed request: the server's ProblemDetail, then the error, then a default. */
export function errorMessage(error) {
  return error?.response?.data?.detail || error?.message || 'Something went wrong. Please try again.'
}
