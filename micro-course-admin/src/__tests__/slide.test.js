import { describe, it, expect, vi } from 'vitest'

// Mock the request utility
vi.mock('@/utils/request', () => ({
  default: vi.fn(),
}))

// Import after mocking
import request from '@/utils/request'
import { uploadHtml, uploadSlide } from '@/plugins/interactive/api/slide'

describe('slide.js API layer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 3.4.1 (tasks.md 3.3.1): 新增 uploadHtml() 函数 — 60s timeout
  it('uploadHtml() sends POST to the upload endpoint with 60s timeout', () => {
    request.mockResolvedValue({ data: { id: 1, totalPages: 1 } })
    const file = new File(['<html></html>'], 'lesson.html', { type: 'text/html' })
    const onProgress = vi.fn()
    const chapterId = 42

    uploadHtml(100, file, onProgress, chapterId)

    expect(request).toHaveBeenCalledOnce()
    const callArgs = request.mock.calls[0][0]
    expect(callArgs.method).toBe('POST')
    expect(callArgs.url).toBe('/courses/100/slides/upload')
    expect(callArgs.timeout).toBe(60000)
    expect(callArgs.data).toBeInstanceOf(FormData)
    expect(callArgs.onUploadProgress).toBe(onProgress)
  })

  it('uploadSlide() sets 300s timeout for PPTX uploads', () => {
    request.mockResolvedValue({ data: { id: 1 } })
    const file = new File(['test'], 'test.pptx', {
      type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    })

    uploadSlide(100, file)

    expect(request).toHaveBeenCalledOnce()
    expect(request.mock.calls[0][0].timeout).toBe(300000)
  })

  it('uploadSlide() sets 60s timeout for HTML uploads via file extension', () => {
    request.mockResolvedValue({ data: { id: 1 } })
    const file = new File(['test'], 'lesson.html', { type: 'text/html' })

    uploadSlide(100, file)

    expect(request).toHaveBeenCalledOnce()
    expect(request.mock.calls[0][0].timeout).toBe(60000)
  })

  it('uploadSlide() includes chapterId when provided', () => {
    request.mockResolvedValue({ data: { id: 1 } })
    const file = new File(['test'], 'test.pptx', {
      type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    })
    const onProgress = vi.fn()

    uploadSlide(100, file, onProgress, 42)

    const formData = request.mock.calls[0][0].data
    expect(formData.get('chapterId')).toBe('42')
    expect(request.mock.calls[0][0].onUploadProgress).toBe(onProgress)
  })
})
