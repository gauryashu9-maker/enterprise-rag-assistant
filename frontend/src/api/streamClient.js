import { createSseParser } from './sseParser.js'

/**
 * Sends a question and streams the answer from the backend (server-sent events over fetch).
 * Calls onToken(text) for every piece, onCitations(list) once, then onDone() or onError(error).
 * Errors carry .status when the server answered with an error response (for example 401).
 */
export async function streamAnswer({
  question,
  token,
  path = '/rag/stream',
  baseUrl = '/api',
  signal,
  fetchImpl = (...args) => fetch(...args),
  onToken = () => {},
  onCitations = () => {},
  onDone = () => {},
  onError = () => {},
}) {
  let finished = false
  const finish = (callback, value) => {
    if (finished) return
    finished = true
    callback(value)
  }

  let response
  try {
    response = await fetchImpl(`${baseUrl}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ question }),
      signal,
    })
  } catch (error) {
    if (error.name === 'AbortError') return
    finish(onError, error)
    return
  }

  if (!response.ok) {
    let detail = `Request failed (${response.status})`
    try {
      const problem = await response.json()
      if (problem?.detail) detail = problem.detail
    } catch {
      // the error body was not JSON: keep the generic message
    }
    const error = new Error(detail)
    error.status = response.status
    finish(onError, error)
    return
  }

  const parser = createSseParser(({ event, data }) => {
    if (finished) return
    if (event === 'token') onToken(data?.text ?? '')
    else if (event === 'citations') onCitations(data?.citations ?? [])
    else if (event === 'done') finish(onDone)
    else if (event === 'error') {
      finish(onError, new Error(data?.detail || 'The answer could not be generated.'))
    }
  })

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      parser.push(decoder.decode(value, { stream: true }))
    }
    parser.end()
  } catch (error) {
    if (error.name === 'AbortError' || finished) return
    finish(onError, error)
    return
  }

  if (!finished) {
    finish(onError, new Error('The connection closed before the answer finished.'))
  }
}
