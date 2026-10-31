import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createSseParser } from './sseParser.js'

function collect(chunks) {
  const events = []
  const parser = createSseParser((event) => events.push(event))
  chunks.forEach((chunk) => parser.push(chunk))
  parser.end()
  return events
}

test('parses the events the backend sends and keeps spaces inside token text', () => {
  const events = collect([
    'event:token\ndata:{"text":"Employees"}\n\nevent:token\ndata:{"text":" get"}\n\n',
    'event:citations\ndata:{"citations":[]}\n\nevent:done\ndata:{}\n\n',
  ])

  assert.deepEqual(events.map((e) => e.event), ['token', 'token', 'citations', 'done'])
  assert.equal(events[1].data.text, ' get')
})

test('handles events split across chunks and CRLF line endings', () => {
  const events = collect([
    'event:tok',
    'en\r\ndata:{"text":"hi"}\r\n',
    '\r\nevent:done\r\ndata:{}\r\n\r\n',
  ])

  assert.deepEqual(events, [
    { event: 'token', data: { text: 'hi' } },
    { event: 'done', data: {} },
  ])
})

test('ignores comments and blocks without data, and keeps non-JSON data as text', () => {
  const events = collect([': keep-alive\n\nevent:note\ndata:plain words\n\n'])

  assert.deepEqual(events, [{ event: 'note', data: 'plain words' }])
})

test('flushes a final event that has no trailing blank line', () => {
  const events = collect(['event:done\ndata:{}'])

  assert.deepEqual(events, [{ event: 'done', data: {} }])
})
