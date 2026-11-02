import { useEffect, useRef, useState } from 'react'
import chatApi, { errorMessage } from '../api/chatApi.js'
import { streamTarget } from '../api/chatConfig.js'
import { streamAnswer } from '../api/streamClient.js'
import './Chat.css'

let localCounter = 0
const nextKey = () => `local-${(localCounter += 1)}`

const formatDate = (value) => {
  const date = value ? new Date(value) : null
  return date && !Number.isNaN(date.getTime()) ? date.toLocaleString() : '-'
}

const toItem = (message) => ({
  key: message.id ?? nextKey(),
  role: message.role,
  content: message.content,
  citations: [],
})

export default function Chat() {
  const [sessions, setSessions] = useState([])
  const [activeId, setActiveId] = useState(null)
  const [items, setItems] = useState([])
  const [input, setInput] = useState('')
  const [loadingSessions, setLoadingSessions] = useState(true)
  const [loadingConversation, setLoadingConversation] = useState(false)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const endRef = useRef(null)
  
  // Race condition guard
  const activeSessionReqRef = useRef(null)

  useEffect(() => {
    let cancelled = false
    chatApi
      .listSessions()
      .then((list) => {
        if (!cancelled) setSessions(list)
      })
      .catch((e) => {
        if (!cancelled) setError(errorMessage(e))
      })
      .finally(() => {
        if (!cancelled) setLoadingSessions(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [items, sending])

  async function openSession(id) {
    setActiveId(id)
    setError('')
    setLoadingConversation(true)
    activeSessionReqRef.current = id; 
    try {
      const conversation = await chatApi.getConversation(id);
      if (activeSessionReqRef.current === id) { 
        setItems(conversation.messages.map(toItem));
      }
    } catch (e) {
      if (activeSessionReqRef.current === id){
        setError(errorMessage(e));
        setItems([]);
      }
    } finally {
      if (activeSessionReqRef.current === id){
        setLoadingConversation(false);
      }
    }
  }

  async function newChat() {
    setError('')
    try {
      const session = await chatApi.createSession()
      setSessions((current) => [session, ...current])
      setActiveId(session.id)
      setItems([])
      return session.id
    } catch (e) {
      setError(errorMessage(e))
      return null
    }
  }

  async function send(event) {
    event.preventDefault()
    const question = input.trim()
    if (!question || sending || loadingConversation) return

    const userKey = nextKey()
    const assistantKey = nextKey()
    
    // Helper to update the streaming assistant message
    const patchAssistant = (change) =>
      setItems((current) => current.map((item) => (item.key === assistantKey ? change(item) : item)))

    let failure = null
    let received = false
    setError('')
    setSending(true)

    try {
      const sessionId = activeId ?? (await newChat())
      if (!sessionId) return
      setInput('')

      // Add User Message and Empty Assistant Message (which will be filled by the stream)
      setItems((current) => [
        ...current,
        { key: userKey, role: 'USER', content: question, citations: [] },
        { key: assistantKey, role: 'ASSISTANT', content: '', citations: [] },
      ])

      // Call the Streaming API
      await streamAnswer({
        question,
        token: localStorage.getItem('token'),
        ...streamTarget(sessionId),
        onToken: (text) => {
          received = true
          patchAssistant((item) => ({ ...item, content: item.content + text }))
        },
        onCitations: (citations) => patchAssistant((item) => ({ ...item, citations })),
        onError: (e) => { failure = e },
      })
    } catch (e) {
      failure = e
    } finally {
      setSending(false)
    }

    if (failure) {
      if (failure.status === 401) {
        localStorage.removeItem('token')
        window.location.href = '/login'
        return
      }
      if (!received) {
        // If nothing arrived, remove the messages so they can try again
        setItems((current) => current.filter((item) => item.key !== userKey && item.key !== assistantKey))
        setInput(question)
      }
      setError(errorMessage(failure))
    }
  }

  return (
    <div className="chat">
      <aside className="chat-sidebar">
        <button type="button" onClick={newChat} disabled={sending}>
          New chat
        </button>
        {loadingSessions && <p className="chat-muted">Loading chats…</p>}
        {!loadingSessions && sessions.length === 0 && <p className="chat-muted">No chats yet.</p>}
        <ul className="chat-sessions">
          {sessions.map((session) => (
            <li key={session.id}>
              <button
                type="button"
                className={session.id === activeId ? 'chat-session active' : 'chat-session'}
                onClick={() => openSession(session.id)}
                disabled={sending}
              >
                {formatDate(session.createdAt)}
              </button>
            </li>
          ))}
        </ul>
      </aside>

      <main className="chat-main">
        <div className="chat-messages" aria-live="polite">
          {loadingConversation && <p className="chat-muted">Loading conversation…</p>}
          {!loadingConversation && items.length === 0 && (
            <p className="chat-muted">Ask a question about your documents.</p>
          )}
          {items.map((item) => (
            <div key={item.key} className={item.role === 'USER' ? 'chat-bubble user' : 'chat-bubble assistant'}>
              <p>{item.content}</p>
              {item.citations.length > 0 && (
                <ul className="chat-citations">
                  {item.citations.map((citation, index) => (
                    <li key={`${citation.documentName}-${citation.page}-${index}`}>
                      <strong>{citation.documentName}</strong>
                      {citation.page != null ? `, page ${citation.page}` : ''}
                      <span className="chat-snippet">{citation.snippet}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ))}
          {sending && <p className="chat-muted">Thinking…</p>}
          <div ref={endRef} />
        </div>

        {error && (
          <p role="alert" className="chat-error">
            {error}
          </p>
        )}

        <form className="chat-form" onSubmit={send}>
          <input
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="Ask about your documents"
            aria-label="Your question"
            disabled={sending || loadingConversation} 
          />
          <button 
            type="submit" 
            disabled={sending || loadingConversation || input.trim() === ''}
          >
            Send
          </button>
        </form>
      </main>
    </div>
  )
}
