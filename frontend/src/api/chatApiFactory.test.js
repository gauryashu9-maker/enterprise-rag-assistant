import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createChatApi, errorMessage } from './chatApiFactory.js'

function fakeHttp(responses) {
  const calls = []
  const handler = (method) => async (url, body) => {
    calls.push({ method, url, body })
    return { data: responses[`${method} ${url}`] }
  }
  return { calls, get: handler('GET'), post: handler('POST') }
}

test('creates a session and lists sessions', async () => {
  const api = createChatApi(
    fakeHttp({
      'POST /chat/sessions': { id: 's1', userId: 'u1', createdAt: '2026-10-20T10:30:00Z' },
      'GET /chat/sessions': [{ id: 's1' }, { id: 's0' }],
    }),
  )

  assert.equal((await api.createSession()).id, 's1')
  assert.deepEqual((await api.listSessions()).map((s) => s.id), ['s1', 's0'])
})

test('listSessions returns an empty list when the server sends something else', async () => {
  const api = createChatApi(fakeHttp({ 'GET /chat/sessions': { unexpected: true } }))

  assert.deepEqual(await api.listSessions(), [])
})

test('loads a conversation with its messages in order', async () => {
  const http = fakeHttp({
    'GET /chat/sessions/s1': { session: { id: 's1' }, messages: [{ id: 'm1' }, { id: 'm2' }] },
  })

  const conversation = await createChatApi(http).getConversation('s1')

  assert.equal(conversation.session.id, 's1')
  assert.deepEqual(conversation.messages.map((m) => m.id), ['m1', 'm2'])
})

test('a conversation without messages becomes an empty list', async () => {
  const http = fakeHttp({ 'GET /chat/sessions/s1': { session: { id: 's1' } } })

  assert.deepEqual((await createChatApi(http).getConversation('s1')).messages, [])
})

test('sends a message with the message field and keeps the citations list safe', async () => {
  const http = fakeHttp({
    'POST /chat/sessions/s1/messages': { assistantMessage: { content: '24 days' }, citations: null },
  })

  const result = await createChatApi(http).sendMessage('s1', 'How many days?')

  assert.deepEqual(http.calls[0].body, { message: 'How many days?' })
  assert.deepEqual(result.citations, [])
  assert.equal(result.assistantMessage.content, '24 days')
})

test('encodes the session id in the URL', async () => {
  const http = fakeHttp({ 'GET /chat/sessions/a%2Fb': { session: {}, messages: [] } })

  await createChatApi(http).getConversation('a/b')

  assert.equal(http.calls[0].url, '/chat/sessions/a%2Fb')
})

test('errorMessage prefers the ProblemDetail text, then the error message, then a default', () => {
  assert.equal(errorMessage({ response: { data: { detail: 'Not found' } } }), 'Not found')
  assert.equal(errorMessage(new Error('offline')), 'offline')
  assert.equal(errorMessage(undefined), 'Something went wrong. Please try again.')
})
