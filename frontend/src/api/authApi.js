import api from './axiosClient'

// Mock mode stays ON until the Security module confirms the login endpoint
// (see docs/api-contract.md, Authentication: TBD).
// Set VITE_USE_MOCK_AUTH=false in your local .env to try the real call.
const USE_MOCK = import.meta.env.VITE_USE_MOCK_AUTH !== 'false'

export async function login(email, password) {
  if (USE_MOCK) {
    await new Promise((resolve) => setTimeout(resolve, 400))
    if (!email || !password) {
      throw new Error('Email and password are required')
    }
    return { token: 'mock-jwt-token' }
  }
  // TODO: confirm the path, field names, and response shape with Security
  const { data } = await api.post('/auth/login', { email, password })
  return data
}