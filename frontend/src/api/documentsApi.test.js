import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { getUploadErrorMessage, uploadDocument } from './documentsApi.js'

describe('getUploadErrorMessage', () => {
  it('extracts detail from RFC 7807 ProblemDetail response', () => {
    const problemDetailError = {
      response: {
        status: 400,
        data: {
          type: 'about:blank',
          title: 'Bad Request',
          status: 400,
          detail: 'Only PDF and TXT files are supported',
          instance: '/api/documents/upload',
        },
      },
    }

    const message = getUploadErrorMessage(problemDetailError)
    assert.equal(message, 'Only PDF and TXT files are supported')
  })

  it('handles network error without a response body by falling back to error.message', () => {
    const networkError = new Error('Network Error: Failed to fetch')
    const message = getUploadErrorMessage(networkError)
    assert.equal(message, 'Network Error: Failed to fetch')
  })

  it('falls back to default message when detail field is missing or empty', () => {
    // Missing detail in response body
    const missingDetailError = {
      response: {
        status: 500,
        data: {
          status: 500,
          title: 'Internal Server Error',
        },
      },
    }
    assert.equal(
      getUploadErrorMessage(missingDetailError),
      'Document upload failed. Please try again.',
    )

    // Empty/blank string detail
    const blankDetailError = {
      response: {
        status: 400,
        data: {
          detail: '   ',
        },
      },
    }
    assert.equal(
      getUploadErrorMessage(blankDetailError),
      'Document upload failed. Please try again.',
    )

    // Non-string detail
    const nonStringDetailError = {
      response: {
        status: 400,
        data: {
          detail: 12345,
        },
      },
    }
    assert.equal(
      getUploadErrorMessage(nonStringDetailError),
      'Document upload failed. Please try again.',
    )
  })

  it('falls back to default message when error object is null, undefined, or unexpected shape', () => {
    assert.equal(getUploadErrorMessage(undefined), 'Document upload failed. Please try again.')
    assert.equal(getUploadErrorMessage(null), 'Document upload failed. Please try again.')
    assert.equal(getUploadErrorMessage({}), 'Document upload failed. Please try again.')
    assert.equal(getUploadErrorMessage('raw error string'), 'Document upload failed. Please try again.')
  })
})

describe('uploadDocument API client', () => {
  it('posts document file as multipart/form-data to /documents/upload', async () => {
    const mockHttp = {
      calls: [],
      async post(url, body, config) {
        this.calls.push({ url, body, config })
        return {
          data: {
            id: 'doc-uuid-1',
            filename: 'policy.pdf',
            status: 'UPLOADED',
            uploadedAt: '2026-11-08T10:00:00Z',
            sizeBytes: 2048,
          },
        }
      },
    }

    const testFile = { name: 'policy.pdf', size: 2048 }
    const response = await uploadDocument(testFile, mockHttp)

    assert.equal(mockHttp.calls.length, 1)
    assert.equal(mockHttp.calls[0].url, '/documents/upload')
    assert.equal(mockHttp.calls[0].config.headers['Content-Type'], 'multipart/form-data')
    assert.equal(response.id, 'doc-uuid-1')
    assert.equal(response.filename, 'policy.pdf')
    assert.equal(response.status, 'UPLOADED')
  })
})
