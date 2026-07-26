import { describe, expect, it, vi } from 'vitest'

import { useVideoUploadQueue } from '@/composables/useVideoUploadQueue'

describe('useVideoUploadQueue', () => {
  it('builds a visible queue from selected files', () => {
    const uploadQueue = useVideoUploadQueue({
      uploader: vi.fn(),
    })

    const files = [
      { name: '第一讲.mp4' },
      { name: '第二讲.mp4' },
    ]

    uploadQueue.replaceQueue(files)

    expect(uploadQueue.queue.value).toHaveLength(2)
    expect(uploadQueue.queue.value.map((item) => item.name)).toEqual([
      '第一讲.mp4',
      '第二讲.mp4',
    ])
    expect(uploadQueue.isBatchMode.value).toBe(true)
    expect(uploadQueue.summaryText.value).toContain('2')
  })

  it('uploads queued files sequentially and tracks per-item progress', async () => {
    const uploader = vi.fn(async ({ file, onProgress }) => {
      onProgress({ loaded: 50, total: 100 })
      onProgress({ loaded: 100, total: 100 })

      return { data: { id: `${file.name}-id` } }
    })

    const uploadQueue = useVideoUploadQueue({ uploader })
    uploadQueue.replaceQueue([
      { name: '第一讲.mp4' },
      { name: '第二讲.mp4' },
    ])

    const result = await uploadQueue.uploadAll({
      courseId: 12,
      chapterId: 8,
    })

    expect(uploader).toHaveBeenCalledTimes(2)
    expect(uploader).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        file: expect.objectContaining({ name: '第一讲.mp4' }),
        courseId: 12,
        chapterId: 8,
        onProgress: expect.any(Function),
      })
    )
    expect(uploader).toHaveBeenNthCalledWith(
      2,
      expect.objectContaining({
        file: expect.objectContaining({ name: '第二讲.mp4' }),
        courseId: 12,
        chapterId: 8,
        onProgress: expect.any(Function),
      })
    )
    expect(uploadQueue.queue.value.map((item) => item.status)).toEqual([
      'success',
      'success',
    ])
    expect(uploadQueue.queue.value.map((item) => item.progress)).toEqual([100, 100])
    expect(result.successCount).toBe(2)
    expect(result.failureCount).toBe(0)
  })
})
