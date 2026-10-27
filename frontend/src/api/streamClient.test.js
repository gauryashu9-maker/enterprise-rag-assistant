import assert from 'node:assert/strict'
import { test } from 'node:test'
import { streamAnswer } from './streamClient.js'

const encoder = new TextEncoder()

function streamingResponse(chunks) {
  const body = new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)))
      controller.close()
    },
  })
  return { ok: true, status: 200, body }
}

function recorder() {
  const log = []
  return {
    log,
    handlers: {
      onToken: (text) => log.push(['token', text]),
      onCitations: (citations) => log.push(['citations', citations]),
      onDone: () => log.push(['done']),
      onError: (error) => log.push(['error', error.message, error.status]),
    },
  }
}

test('streams tokens, then citations, then done, and sends the bearer token', async () => {
  const calls = []
  const fetchImpl = async (url, options) => {
    calls.push({ url, options })
    return streamingResponse([
      'event:token\ndata:{"text":"24"}\n\nevent:token\ndata:{"text":" days"}\n\n',
      'event:citations\ndata:{"citations":[{"documentName":"hr.pdf","page":1,"snippet":"s"}]}\n\nevent:done\ndata:{}\n\n',
    ])
  }
  const { log, handlers } = recorder()

  await streamAnswer({ question: 'leave?', token: 'abc', fetchImpl, ...handlers })

  assert.deepEqual(log.map((entry) => entry[0]), ['token', 'token', 'citations', 'done'])
  assert.equal(log[1][1], ' days')
  assert.equal(calls[0].url, '/api/rag/stream')
  assert.equal(calls[0].options.headers.Authorization, 'Bearer abc')
  assert.deepEqual(JSON.parse(calls[0].options.body), { question: 'leave?' })
})

test('reports the ProblemDetail text and status for a failed request', async () => {
  const fetchImpl = async () => ({
    ok: false,
    status: 401,
    json: async () => ({ detail: 'Authentication is required or the bearer token is invalid' }),
  })
  const { log, handlers } = recorder()

  await streamAnswer({ question: 'q', token: 'bad', fetchImpl, ...handlers })

  assert.deepEqual(log, [['error', 'Authentication is required or the bearer token is invalid', 401]])
})

test('reports an error event from the server once and ignores what follows', async () => {
  const fetchImpl = async () =>
    streamingResponse([
      'event:token\ndata:{"text":"par"}\n\nevent:error\ndata:{"detail":"The answer could not be generated."}\n\nevent:done\ndata:{}\n\n',
    ])
  const { log, handlers } = recorder()

  await streamAnswer({ question: 'q', token: 't', fetchImpl, ...handlers })

  assert.deepEqual(log, [
    ['token', 'par'],
    ['error', 'The answer could not be generated.', undefined],
  ])
})

test('reports a closed connection when the stream ends before done', async () => {
  const fetchImpl = async () => streamingResponse(['event:token\ndata:{"text":"x"}\n\n'])
  const { log, handlers } = recorder()

  await streamAnswer({ question: 'q', token: 't', fetchImpl, ...handlers })

  assert.deepEqual(log, [
    ['token', 'x'],
    ['error', 'The connection closed before the answer finished.', undefined],
  ])
})

test('reports a network failure when the request itself cannot be sent', async () => {
  const fetchImpl = async () => {
    throw new Error('offline')
  }
  const { log, handlers } = recorder()

  await streamAnswer({ question: 'q', token: 't', fetchImpl, ...handlers })

  assert.deepEqual(log, [['error', 'offline', undefined]])
})
