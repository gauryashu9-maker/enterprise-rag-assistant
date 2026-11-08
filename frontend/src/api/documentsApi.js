import api from './axiosClient.js'

// Mock mode stays ON until the document endpoints exist
// (planned in docs/api-contract.md, not implemented yet).
// Set VITE_USE_MOCK_API=false in your local .env to try the real call.
const USE_MOCK = Boolean(import.meta?.env?.VITE_USE_MOCK_API === 'true')

// Same shape as DocumentResponse in docs/api-contract.md
const MOCK_DOCUMENTS = [
  {
    id: '8ca57f15-4358-4bc6-a8af-614eab659cf4',
    filename: 'employee-handbook.pdf',
    status: 'READY',
    uploadedAt: '2026-10-15T12:30:00Z',
    sizeBytes: 2048000,
  },
  {
    id: '3b1f0c9e-6a52-4a4f-9d55-1e7c2f6d7a10',
    filename: 'leave-policy.pdf',
    status: 'PROCESSING',
    uploadedAt: '2026-10-16T09:10:00Z',
    sizeBytes: 512000,
  },
  {
    id: '5d7e2a41-0c88-4b13-8f0a-9a3c64b2e5f7',
    filename: 'onboarding-notes.txt',
    status: 'UPLOADED',
    uploadedAt: '2026-10-16T10:45:00Z',
    sizeBytes: 4096,
  },
  {
    id: '9a6c1d33-72ef-4e0b-b1c8-0d4f8a2b6e19',
    filename: 'scan-2024.pdf',
    status: 'FAILED',
    uploadedAt: '2026-10-14T16:20:00Z',
    sizeBytes: 8192000,
    errorMessage: 'Unsupported document format',
  },
]

/**
 * Extracts human-readable error text from an API error response,
 * giving priority to RFC 7807 ProblemDetail 'detail', then error.message,
 * with a fallback default.
 */
export function getUploadErrorMessage(error) {
  const detail = error?.response?.data?.detail
  if (typeof detail === 'string' && detail.trim().length > 0) {
    return detail.trim()
  }
  const message = error?.message
  if (typeof message === 'string' && message.trim().length > 0) {
    return message.trim()
  }
  return 'Document upload failed. Please try again.'
}

export async function listDocuments(client = api) {
  if (USE_MOCK) {
    await new Promise((resolve) => setTimeout(resolve, 400))
    return MOCK_DOCUMENTS
  }
  const { data } = await client.get('/documents')
  return data
}

export async function uploadDocument(file, client = api) {
  if (USE_MOCK) {
    await new Promise((resolve) => setTimeout(resolve, 400))
    return {
      id: 'mock-' + Date.now(),
      filename: file?.name || 'uploaded-document.pdf',
      status: 'READY',
      uploadedAt: new Date().toISOString(),
      sizeBytes: file?.size || 1024,
    }
  }

  const formData = new FormData()
  formData.append('file', file)
  const { data } = await client.post('/documents/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
  return data
}
