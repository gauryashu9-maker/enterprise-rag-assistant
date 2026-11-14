import { Navigate, Outlet } from 'react-router-dom'

export default function RequireAuth() {
  const token = localStorage.getItem('token')
  if (!token) {
    // Redirect to the Home page instead of /login
    return <Navigate to="/" replace />
  }
  return <Outlet />
}