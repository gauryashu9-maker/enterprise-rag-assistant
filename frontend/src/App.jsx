import { Navigate, Route, Routes } from 'react-router-dom'
import RequireAuth from './components/RequireAuth.jsx'
import Header from './components/Header.jsx'
import Home from './pages/Home.jsx' // <-- Import the new page
import Chat from './pages/Chat.jsx'
import Documents from './pages/Documents.jsx'
import Login from './pages/Login.jsx'

export default function App() {
  return (
    <Routes>
      {/* Set the Home page as the default route */}
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<Login />} />
      
      {/* Protected Routes */}
      <Route element={<RequireAuth />}>
        <Route path="/documents" element={<><Header /><Documents /></>} />
        <Route path="/chat" element={<><Header /><Chat /></>} />
      </Route>
      
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}