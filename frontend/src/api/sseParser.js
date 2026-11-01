// Parses a server-sent-events text stream into { event, data } objects.
// The backend sends events like:  event:token\ndata:{"text":" days"}\n\n

function parseBlock(block) {
  let event = 'message'
  const dataLines = []
  for (const line of block.split('\n')) {
    if (line === '' || line.startsWith(':')) continue // blank line or comment
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1) // the spec removes one leading space
    if (field === 'event') event = value
    else if (field === 'data') dataLines.push(value)
  }
  if (dataLines.length === 0) return null
  const raw = dataLines.join('\n')
  let data = raw
  try {
    data = JSON.parse(raw)
  } catch {
    // not JSON: keep the text as it is
  }
  return { event, data }
}

/** Returns { push(chunk), end() }. onEvent is called once per complete event, in order. */
export function createSseParser(onEvent) {
  let buffer = ''

  const emit = (block) => {
    const parsed = parseBlock(block)
    if (parsed) onEvent(parsed)
  }

  return {
    push(chunk) {
      buffer = (buffer + chunk).replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary !== -1) {
        emit(buffer.slice(0, boundary))
        buffer = buffer.slice(boundary + 2)
        boundary = buffer.indexOf('\n\n')
      }
    },
    end() {
      if (buffer.trim() !== '') emit(buffer)
      buffer = ''
    },
  }
}
