import { Link, useNavigate } from 'react-router-dom'

export default function NavBar() {
  const navigate = useNavigate()
  const email = localStorage.getItem('email')
  const role = localStorage.getItem('role')

  function handleLogout() {
    localStorage.removeItem('token')
    localStorage.removeItem('email')
    localStorage.removeItem('role')
    navigate('/login', { replace: true })
  }

  return (
    <nav style={{ display: 'flex', gap: 16, alignItems: 'center', padding: '12px 16px', borderBottom: '1px solid #444' }}>
      <Link to="/documents">Documents</Link>
      <Link to="/chat">Chat</Link>
      <span style={{ marginLeft: 'auto' }}>{email}{role ? ` (${role})` : ''}</span>
      <button onClick={handleLogout}>Log out</button>
    </nav>
  )
}