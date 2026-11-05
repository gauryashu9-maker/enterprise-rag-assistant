import { Navigate, Route, Routes } from 'react-router-dom'
import RequireAuth from './components/RequireAuth.jsx'
import Header from './components/Header.jsx'
import Chat from './pages/Chat.jsx'
import Documents from './pages/Documents.jsx'
import Login from './pages/Login.jsx'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<Login />} />
      
      {/* Protected Routes with the Header included */}
      <Route element={<RequireAuth />}>
        <Route path="/documents" element={<><Header /><Documents /></>} />
        <Route path="/chat" element={<><Header /><Chat /></>} />
      </Route>
      
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}