import { useEffect, useState } from 'react'
import { getUploadErrorMessage, listDocuments, uploadDocument } from '../api/documentsApi.js'

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
  const [uploadError, setUploadError] = useState('')
  const [uploadSuccess, setUploadSuccess] = useState('')

  useEffect(() => {
    async function load() {
      try {
        const data = await listDocuments()
        if (!Array.isArray(data)) {
          throw new Error('Invalid response while loading documents')
        }
        setDocuments(data)
      } catch (err) {
        setError(getUploadErrorMessage(err) || 'Failed to load documents')
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  async function handleFileUpload(e) {
    const file = e.target.files?.[0]
    if (!file) return

    setUploading(true)
    setUploadError('')
    setUploadSuccess('')

    try {
      const created = await uploadDocument(file)
      setUploadSuccess(`"${file.name}" uploaded successfully.`)
      setDocuments((prev) => [created, ...prev.filter((d) => d.id !== created.id)])
      e.target.value = ''
    } catch (err) {
      setUploadError(getUploadErrorMessage(err))
    } finally {
      setUploading(false)
    }
  }

  return (
    <div style={{ maxWidth: 1000, margin: '32px auto', padding: '0 24px', color: '#e2e8f0' }}>
      <h1 style={{ color: '#a78bfa', marginBottom: '1.5rem', fontSize: '2rem' }}>Documents</h1>

      {/* Upload UI section */}
      <div
        style={{
          backgroundColor: '#23233a',
          borderRadius: '12px',
          border: '1px solid #3d3d5c',
          padding: '20px',
          marginBottom: '24px',
        }}
      >
        <h2 style={{ fontSize: '1.1rem', marginBottom: '8px', color: '#f8fafc' }}>Upload Document</h2>
        <p style={{ fontSize: '0.875rem', color: '#94a3b8', marginBottom: '16px' }}>
          Select a PDF or TXT file to upload and ingest into the knowledge base.
        </p>

        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
          <label
            style={{
              display: 'inline-block',
              backgroundColor: uploading ? '#4b5563' : '#6366f1',
              color: 'white',
              padding: '8px 16px',
              borderRadius: '8px',
              cursor: uploading ? 'not-allowed' : 'pointer',
              fontSize: '0.875rem',
              fontWeight: '500',
            }}
          >
            {uploading ? 'Uploading...' : 'Choose File'}
            <input
              type="file"
              accept=".pdf,.txt"
              onChange={handleFileUpload}
              disabled={uploading}
              style={{ display: 'none' }}
            />
          </label>
          <span style={{ fontSize: '0.875rem', color: '#94a3b8' }}>
            Supported formats: .pdf, .txt (Max 25 MB)
          </span>
        </div>

        {uploadError && (
          <div
            style={{
              marginTop: '12px',
              padding: '10px 14px',
              backgroundColor: '#451a1a',
              border: '1px solid #f43f5e',
              borderRadius: '8px',
              color: '#fca5a5',
              fontSize: '0.875rem',
            }}
          >
            {uploadError}
          </div>
        )}

        {uploadSuccess && (
          <div
            style={{
              marginTop: '12px',
              padding: '10px 14px',
              backgroundColor: '#064e3b',
              border: '1px solid #10b981',
              borderRadius: '8px',
              color: '#6ee7b7',
              fontSize: '0.875rem',
            }}
          >
            {uploadSuccess}
          </div>
        )}
      </div>

      {loading && <p style={{ color: '#94a3b8' }}>Loading documents...</p>}
      {error && <p style={{ color: '#f43f5e' }}>{error}</p>}
      {!loading && !error && documents.length === 0 && <p style={{ color: '#94a3b8' }}>No documents yet.</p>}

      {!loading && !error && documents.length > 0 && (
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
