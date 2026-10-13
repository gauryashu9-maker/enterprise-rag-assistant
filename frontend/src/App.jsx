import { Navigate, Route, Routes } from 'react-router-dom'
import Chat from './pages/Chat.jsx'
import Documents from './pages/Documents.jsx'
import Login from './pages/Login.jsx'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<Login />} />
      <Route path="/documents" element={<Documents />} />
      <Route path="/chat" element={<Chat />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}