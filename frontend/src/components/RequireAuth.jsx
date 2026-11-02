import { Navigate, Outlet } from 'react-router-dom'
import NavBar from './NavBar.jsx'

export default function RequireAuth() {
  const token = localStorage.getItem('token')
  if (!token) {
    return <Navigate to="/login" replace />
  }
  return (
    <>
      <NavBar />
      <Outlet />
    </>
  )
}