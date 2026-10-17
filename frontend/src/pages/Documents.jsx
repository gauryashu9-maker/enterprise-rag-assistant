import { useEffect, useState } from 'react'
import { listDocuments } from '../api/documentsApi.js'

const STATUS_COLORS = {
  UPLOADED: '#6b7280',
  PROCESSING: '#d97706',
  READY: '#16a34a',
  FAILED: '#dc2626',
}

function StatusBadge({ status }) {
  return (
    <span
      style={{
        background: STATUS_COLORS[status] || '#6b7280',
        color: 'white',
        padding: '2px 8px',
        borderRadius: 12,
        fontSize: 12,
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

  useEffect(() => {
    async function load() {
      try {
        const data = await listDocuments()
        setDocuments(data)
      } catch (err) {
        setError(err.response?.data?.detail || err.message || 'Failed to load documents')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  return (
    <div style={{ maxWidth: 800, margin: '32px auto', padding: '0 16px' }}>
      <h1>Documents</h1>

      {loading && <p>Loading documents...</p>}
      {error && <p style={{ color: 'crimson' }}>{error}</p>}
      {!loading && !error && documents.length === 0 && <p>No documents yet.</p>}

      {!loading && !error && documents.length > 0 && (
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
          <thead>
            <tr>
              <th>Filename</th>
              <th>Status</th>
              <th>Uploaded</th>
              <th>Size</th>
            </tr>
          </thead>
          <tbody>
            {documents.map((doc) => (
              <tr key={doc.id}>
                <td>{doc.filename}</td>
                <td>
                  <StatusBadge status={doc.status} />
                  {doc.errorMessage && (
                    <div style={{ color: 'crimson', fontSize: 12 }}>{doc.errorMessage}</div>
                  )}
                </td>
                <td>{new Date(doc.uploadedAt).toLocaleString()}</td>
                <td>{formatSize(doc.sizeBytes)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}