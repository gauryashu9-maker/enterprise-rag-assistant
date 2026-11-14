import { Navigate, Route, Routes } from 'react-router-dom'
import RequireAuth from './components/RequireAuth.jsx'
import Header from './components/Header.jsx'
import Home from './pages/Home.jsx'
import Chat from './pages/Chat.jsx'
import Documents from './pages/Documents.jsx'

export default function App() {
  return (
    <Routes>
      {/* Everything starts here */}
      <Route path="/" element={<Home />} />
      
      {/* If anyone tries to go to /login, just send them to the Home page */}
      <Route path="/login" element={<Navigate to="/" replace />} />
      
      {/* Protected Routes */}
      <Route element={<RequireAuth />}>
        <Route path="/documents" element={<><Header /><Documents /></>} />
        <Route path="/chat" element={<><Header /><Chat /></>} />
      </Route>
      
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}