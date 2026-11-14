import { useEffect, useState, useRef } from 'react'
import { listDocuments, uploadDocument } from '../api/documentsApi.js'

const STATUS_COLORS = {
  UPLOADED: '#94a3b8',
  PROCESSING: '#f59e0b',
  READY: '#10b981',
  FAILED: '#f43f5e',
}

function StatusBadge({ status }) {
  return (
    <span
      style={{
        background: STATUS_COLORS[status] || '#94a3b8',
        color: status === 'READY' ? '#022c22' : 'white',
        padding: '4px 10px',
        borderRadius: '12px',
        fontSize: '0.75rem',
        fontWeight: 'bold',
      }}
    >
      {status}
    </span>
  )
}

function formatSize(bytes) {
  if (bytes == null) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default function Documents() {
  const [documents, setDocuments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [uploading, setUploading] = useState(false)
  
  const fileInputRef = useRef(null)

  async function loadDocuments() {
    try {
      setLoading(true)
      const data = await listDocuments()
      if (!Array.isArray(data)) {
        throw new Error('Invalid response while loading documents')
      }
      setDocuments(data)
    } catch (err) {
      setError(err.response?.data?.detail || err.message || 'Failed to load documents')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadDocuments()
  }, [])

  async function handleFileChange(event) {
    const file = event.target.files[0]
    if (!file) return

    setUploading(true)
    setError('')
    try {
      await uploadDocument(file)
      event.target.value = null // Reset input
      await loadDocuments() // Reload to show PROCESSING/READY status
    } catch (err) {
      setError(err.response?.data?.detail || err.message || 'Upload failed')
    } finally {
      setUploading(false)
    }
  }

  return (
    <div style={{ maxWidth: 1000, margin: '32px auto', padding: '0 24px', color: '#e2e8f0' }}>
      
      {/* Header with Upload Button */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ color: '#a78bfa', margin: 0, fontSize: '2rem' }}>Documents</h1>
        
        <input 
          type="file" 
          ref={fileInputRef} 
          onChange={handleFileChange} 
          style={{ display: 'none' }} 
          accept=".pdf,.txt"
        />
        
        <button 
          onClick={() => fileInputRef.current.click()} 
          disabled={uploading}
          style={{
            background: 'linear-gradient(90deg, #a78bfa, #67e8f9)',
            color: '#1a1a2e',
            border: 'none',
            padding: '10px 20px',
            borderRadius: '8px',
            fontWeight: 'bold',
            fontSize: '1rem',
            cursor: uploading ? 'not-allowed' : 'pointer',
            opacity: uploading ? 0.7 : 1
          }}
        >
          {uploading ? 'Uploading...' : '+ Upload Document'}
        </button>
      </div>

      {loading && documents.length === 0 && <p style={{ color: '#94a3b8' }}>Loading documents...</p>}
      {error && <p style={{ color: '#f43f5e' }}>{error}</p>}
      {!loading && !error && documents.length === 0 && <p style={{ color: '#94a3b8' }}>No documents yet. Upload one to get started!</p>}

      {documents.length > 0 && (
        <div style={{ backgroundColor: '#23233a', borderRadius: '12px', border: '1px solid #3d3d5c', overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
            <thead style={{ backgroundColor: '#1a1a2e', borderBottom: '1px solid #3d3d5c' }}>
              <tr>
                <th style={{ padding: '16px' }}>Filename</th>
                <th style={{ padding: '16px' }}>Status</th>
                <th style={{ padding: '16px' }}>Uploaded</th>
                <th style={{ padding: '16px' }}>Size</th>
              </tr>
            </thead>
            <tbody>
              {documents.map((doc) => (
                <tr key={doc.id} style={{ borderBottom: '1px solid #3d3d5c' }}>
                  <td style={{ padding: '16px', fontWeight: '500' }}>{doc.filename}</td>
                  <td style={{ padding: '16px' }}>
                    <StatusBadge status={doc.status} />
                    {doc.errorMessage && (
                      <div style={{ color: '#f43f5e', fontSize: '0.8rem', marginTop: '4px' }}>{doc.errorMessage}</div>
                    )}
                  </td>
                  <td style={{ padding: '16px', color: '#94a3b8' }}>
                    {doc.uploadedAt ? new Date(doc.uploadedAt).toLocaleString() : '-'}
                  </td>
                  <td style={{ padding: '16px', color: '#94a3b8' }}>{formatSize(doc.sizeBytes)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}