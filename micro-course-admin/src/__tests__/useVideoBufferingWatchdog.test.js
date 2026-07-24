import { describe, expect, it, vi, afterEach } from 'vitest'

import { useVideoBufferingWatchdog } from '@/composables/useVideoBufferingWatchdog'

describe('useVideoBufferingWatchdog', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows a warning after prolonged buffering and cancels it when playback resumes', async () => {
    vi.useFakeTimers()
    const showWarning = vi.fn()
    const watchdog = useVideoBufferingWatchdog({
      showWarning,
      showRetryConfirm: vi.fn(),
      onRetry: vi.fn(),
      warningDelayMs: 15000,
      retryDelayMs: 30000
    })

    watchdog.onBufferingStart()
    await vi.advanceTimersByTimeAsync(14999)
    expect(showWarning).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)
    expect(watchdog.isBuffering.value).toBe(true)
    expect(showWarning).toHaveBeenCalledWith({
      message: '视频缓冲中,网络可能较慢,请稍候...',
      duration: 5000
    })

    watchdog.onBufferingEnd()
    expect(watchdog.isBuffering.value).toBe(false)

    showWarning.mockClear()
    watchdog.onBufferingStart()
    watchdog.onBufferingEnd()
    await vi.advanceTimersByTimeAsync(30000)
    expect(showWarning).not.toHaveBeenCalled()
  })

  it('confirms retry after timeout and only retries when the user accepts', async () => {
    vi.useFakeTimers()
    const onRetry = vi.fn()
    const showRetryConfirm = vi.fn()
      .mockResolvedValueOnce(undefined)
      .mockRejectedValueOnce(new Error('cancel'))
    const watchdog = useVideoBufferingWatchdog({
      showWarning: vi.fn(),
      showRetryConfirm,
      onRetry,
      warningDelayMs: 15000,
      retryDelayMs: 30000
    })

    watchdog.onBufferingStart()
    await vi.advanceTimersByTimeAsync(30000)
    await Promise.resolve()
    expect(showRetryConfirm).toHaveBeenCalledWith({
      message: '视频缓冲超过 30 秒,可能是网络问题。是否重试?',
      title: '缓冲超时',
      options: {
        confirmButtonText: '重试',
        cancelButtonText: '继续等待',
        type: 'warning'
      }
    })
    expect(onRetry).toHaveBeenCalledTimes(1)

    watchdog.onBufferingStart()
    await vi.advanceTimersByTimeAsync(30000)
    await Promise.resolve()
    expect(onRetry).toHaveBeenCalledTimes(1)
  })
})
