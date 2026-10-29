import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
})

// Runs before every request: attach the JWT if we have one
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  const isAuthCall = config.url?.includes('/auth/')
  if (token && !isAuthCall) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Runs after every response: on 401, clear the token and go to /login
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const isLoginCall = error.config?.url?.includes('/auth/login')
    if (error.response?.status === 401 && !isLoginCall) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export default api