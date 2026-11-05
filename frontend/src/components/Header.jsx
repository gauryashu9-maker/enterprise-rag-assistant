import { useNavigate } from 'react-router-dom'
import './Header.css'

export default function Header() {
  const navigate = useNavigate()
  const token = localStorage.getItem('token')

  const handleLogout = () => {
    localStorage.removeItem('token')
    navigate('/login')
  }

  return (
    <header className="app-header">
      <div className="header-title" onClick={() => navigate('/chat')}>
        Enterprise RAG Assistant
      </div>
      <div className="header-nav">
        <button onClick={() => navigate('/documents')}>Documents</button>
        <button onClick={() => navigate('/chat')}>Chat</button>
        {token && <button className="logout-btn" onClick={handleLogout}>Log Out</button>}
      </div>
    </header>
  )
}