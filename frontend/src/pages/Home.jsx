import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { login } from '../api/authApi.js' // Adjust path if needed

export default function Home() {
  const navigate = useNavigate()
  const [showLogin, setShowLogin] = useState(false)
  
  // Login Form State
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleLogin(e) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const data = await login(email, password)
      localStorage.setItem('token', data.token)
      localStorage.setItem('email', data.email)
      localStorage.setItem('role', data.role)
      navigate('/chat')
    } catch (err) {
      setError(err.response?.data?.detail || err.message || 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    // Changed background to a deeper shade of black/blue
    <div style={{ minHeight: '100vh', backgroundColor: '#0B0C1A', color: '#ffffff', fontFamily: 'system-ui, sans-serif', overflowX: 'hidden', position: 'relative' }}>
      
      {/* CSS for the glowing animation */}
      <style>
        {`
          @keyframes glow {
            0% { box-shadow: 0 0 10px rgba(167, 139, 250, 0.2), 0 0 20px rgba(76, 201, 240, 0.2); }
            50% { box-shadow: 0 0 20px rgba(167, 139, 250, 0.6), 0 0 40px rgba(76, 201, 240, 0.6); }
            100% { box-shadow: 0 0 10px rgba(167, 139, 250, 0.2), 0 0 20px rgba(76, 201, 240, 0.2); }
          }
          .modal-glow {
            animation: glow 3s infinite alternate;
          }
        `}
      </style>

      {/* NAVBAR */}
      <nav style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '1.5rem 4rem', borderBottom: '1px solid rgba(255,255,255,0.1)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <div style={{ width: '32px', height: '32px', background: 'linear-gradient(135deg, #a78bfa, #4cc9f0)', borderRadius: '8px' }}></div>
          <h1 style={{ margin: 0, fontSize: '1.5rem', fontWeight: '800', letterSpacing: '1px' }}>AXLERO <span style={{ color: '#94a3b8', fontSize: '1rem', fontWeight: 'normal' }}>| RAG Assistant</span></h1>
        </div>
        <button 
          onClick={() => setShowLogin(true)}
          style={{ background: '#ffffff', color: '#000', border: 'none', padding: '10px 24px', borderRadius: '24px', fontWeight: 'bold', cursor: 'pointer', transition: 'transform 0.2s' }}
        >
          LOG IN
        </button>
      </nav>

      {/* HERO SECTION */}
      <main style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center', padding: '8rem 2rem', position: 'relative' }}>
        {/* Glowing Orb Background */}
        <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%, -50%)', width: '700px', height: '700px', background: 'radial-gradient(circle, rgba(167,139,250,0.15) 0%, rgba(76,201,240,0.05) 40%, rgba(11,12,26,0) 70%)', zIndex: 0, pointerEvents: 'none' }}></div>
        
        <div style={{ position: 'relative', zIndex: 1, maxWidth: '900px' }}>
          <div style={{ display: 'inline-block', border: '1px solid rgba(255,255,255,0.2)', padding: '6px 16px', borderRadius: '20px', fontSize: '0.9rem', marginBottom: '2rem', background: 'rgba(255,255,255,0.05)' }}>
            Powered by Spring AI & pgvector <span style={{ color: '#4cc9f0' }}>➔</span>
          </div>
          <h2 style={{ fontSize: '4rem', fontWeight: '800', lineHeight: '1.1', marginBottom: '1.5rem' }}>
            Chat Directly With<br/>Your Enterprise Documents.
          </h2>
          <p style={{ fontSize: '1.2rem', color: '#94a3b8', maxWidth: '700px', margin: '0 auto 3rem auto', lineHeight: '1.6' }}>
            Stop searching through thousands of PDFs. Our Retrieval-Augmented Generation AI securely answers your questions with verifiable citations, keeping your data 100% private.
          </p>
          <button 
            onClick={() => setShowLogin(true)}
            style={{ background: 'linear-gradient(90deg, #a78bfa, #4cc9f0)', color: '#000', border: 'none', padding: '16px 40px', borderRadius: '30px', fontSize: '1.1rem', fontWeight: 'bold', cursor: 'pointer', boxShadow: '0 10px 30px rgba(167,139,250,0.3)' }}
          >
            ACCESS THE ASSISTANT
          </button>
        </div>
      </main>

      {/* FEATURES SECTION (Kept the same) */}
      <section style={{ padding: '4rem 4rem', backgroundColor: '#070814' }}>
        <div style={{ textAlign: 'center', marginBottom: '3rem' }}>
          <p style={{ color: '#a78bfa', fontWeight: 'bold', fontSize: '0.9rem', marginBottom: '0.5rem' }}>WHY USE OUR RAG ENGINE</p>
          <h3 style={{ fontSize: '2rem', margin: 0 }}>Intelligent, Secure, and Instant.</h3>
        </div>
        <div style={{ display: 'flex', gap: '2rem', maxWidth: '1000px', margin: '0 auto', justifyContent: 'center' }}>
          <div style={{ flex: 1, background: '#11122b', borderRadius: '16px', padding: '2rem', border: '1px solid #2c2f5c' }}>
            <h4 style={{ fontSize: '1.3rem', marginBottom: '1rem', color: '#4cc9f0' }}>Semantic Search</h4>
            <p style={{ color: '#94a3b8', lineHeight: '1.6', fontSize: '0.95rem' }}>We don't just match keywords. We convert your text into vectors to find the exact passage based on meaning and context.</p>
          </div>
          <div style={{ flex: 1, background: '#11122b', borderRadius: '16px', padding: '2rem', border: '1px solid #2c2f5c' }}>
            <h4 style={{ fontSize: '1.3rem', marginBottom: '1rem', color: '#a78bfa' }}>Verified Citations</h4>
            <p style={{ color: '#94a3b8', lineHeight: '1.6', fontSize: '0.95rem' }}>The AI is forced to return chunk numbers. Our backend builds the citations, meaning the AI cannot invent fake document names.</p>
          </div>
          <div style={{ flex: 1, background: '#11122b', borderRadius: '16px', padding: '2rem', border: '1px solid #2c2f5c' }}>
            <h4 style={{ fontSize: '1.3rem', marginBottom: '1rem', color: '#4cc9f0' }}>Strict Isolation</h4>
            <p style={{ color: '#94a3b8', lineHeight: '1.6', fontSize: '0.95rem' }}>Your documents are tied to your secure JWT token. The vector search is mathematically restricted to only your own files.</p>
          </div>
        </div>
      </section>

      {/* FOOTER */}
      <footer style={{ textAlign: 'center', padding: '2rem', color: '#64748b', fontSize: '0.9rem', borderTop: '1px solid rgba(255,255,255,0.05)' }}>
        © 2026 Axlero Solutions. Built by the Project 2 RAG Team.
      </footer>

      {/* --- LOGIN MODAL --- */}
      {showLogin && (
        <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(5, 5, 10, 0.85)', backdropFilter: 'blur(8px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          
          <div className="modal-glow" style={{ width: '100%', maxWidth: '400px', backgroundColor: '#121420', padding: '2.5rem', borderRadius: '16px', border: '1px solid #3d3d5c', position: 'relative' }}>
            
            {/* Close Button */}
            <button 
              onClick={() => setShowLogin(false)}
              style={{ position: 'absolute', top: '15px', right: '15px', background: 'transparent', border: 'none', color: '#94a3b8', fontSize: '1.5rem', cursor: 'pointer', hover: {color: '#fff'} }}
            >
              ×
            </button>

            <h2 style={{ textAlign: 'center', margin: '0 0 0.5rem 0', fontSize: '1.8rem', background: 'linear-gradient(90deg, #a78bfa, #4cc9f0)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
              Welcome Back
            </h2>
            <p style={{ textAlign: 'center', color: '#94a3b8', marginBottom: '2rem', fontSize: '0.9rem' }}>
              Sign in to access your documents
            </p>

            <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
              <input
                type="email"
                placeholder="Email address"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                style={{ width: '100%', padding: '12px 16px', backgroundColor: '#0B0C1A', border: '1px solid #3d3d5c', borderRadius: '8px', color: '#fff', outline: 'none' }}
              />
              <input
                type="password"
                placeholder="Password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                style={{ width: '100%', padding: '12px 16px', backgroundColor: '#0B0C1A', border: '1px solid #3d3d5c', borderRadius: '8px', color: '#fff', outline: 'none' }}
              />
              
              {error && <p style={{ color: '#f43f5e', fontSize: '0.85rem', margin: 0 }}>{error}</p>}

              <button
                type="submit"
                disabled={loading}
                style={{ marginTop: '0.5rem', width: '100%', background: 'linear-gradient(90deg, #a78bfa, #4cc9f0)', color: '#000', border: 'none', padding: '14px', borderRadius: '8px', fontSize: '1rem', fontWeight: 'bold', cursor: loading ? 'not-allowed' : 'pointer', opacity: loading ? 0.7 : 1 }}
              >
                {loading ? 'Authenticating...' : 'Log In'}
              </button>
            </form>

          </div>
        </div>
      )}
    </div>
  )
}