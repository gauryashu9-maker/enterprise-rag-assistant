import api from './axiosClient'

// Real login is the default now that POST /api/auth/login exists.
// Set VITE_USE_MOCK_AUTH=true in your local .env to work offline.
const USE_MOCK = import.meta.env.VITE_USE_MOCK_AUTH === 'true'

const MOCK_USERS = [
  { email: 'user@example.com', password: 'secret123', role: 'USER' },
  { email: 'admin@example.com', password: 'admin12345', role: 'ADMIN' },
]

function problem(status, detail) {
  const err = new Error(detail)
  err.response = { status, data: { status, detail } }
  return err
}

// POST /api/auth/login -> { token, email, role }
export async function login(email, password) {
  if (USE_MOCK) {
    await new Promise((resolve) => setTimeout(resolve, 400))
    const user = MOCK_USERS.find((u) => u.email === email && u.password === password)
    if (!user) {
      throw problem(401, 'Invalid email or password')
    }
    return { token: 'mock-jwt-token', email: user.email, role: user.role }
  }
  const { data } = await api.post('/auth/login', { email, password })
  return data
}