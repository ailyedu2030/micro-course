import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { useVideoSourceLifecycle } from '@/composables/useVideoSourceLifecycle'

function createVideoElement(overrides = {}) {
  return {
    src: '',
    play: vi.fn().mockResolvedValue(undefined),
    pause: vi.fn(),
    load: vi.fn(),
    canPlayType: vi.fn().mockReturnValue(''),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    removeAttribute: vi.fn(),
    ...overrides
  }
}

function createHlsMock({ supported = true } = {}) {
  const instances = []

  class MockHls {
    static isSupported() {
      return supported
    }

    constructor(config) {
      this.config = config
      this.handlers = new Map()
      this.loadSource = vi.fn()
      this.attachMedia = vi.fn()
      this.on = vi.fn((event, handler) => {
        this.handlers.set(event, handler)
      })
      this.destroy = vi.fn()
      instances.push(this)
    }
  }

  MockHls.Events = {
    MANIFEST_PARSED: 'manifestParsed',
    ERROR: 'error'
  }

  return { MockHls, instances }
}

describe('useVideoSourceLifecycle', () => {
  it('initializes hls.js playback, registers PiP listeners, and reports fatal errors', async () => {
    const { MockHls, instances } = createHlsMock({ supported: true })
    const video = createVideoElement()
    const videoRef = ref(video)
    const isPipSupported = ref(true)
    const handlePipEnter = vi.fn()
    const handlePipLeave = vi.fn()
    const setErrorMessage = vi.fn()

    const sourceLifecycle = useVideoSourceLifecycle({
      videoRef,
      isPipSupported,
      handlePipEnter,
      handlePipLeave,
      getVideoUrl: () => 'https://cdn.example.com/video.m3u8',
      getAuthToken: () => 'token-123',
      setErrorMessage,
      HlsLib: MockHls
    })

    sourceLifecycle.initPlayer()

    expect(instances).toHaveLength(1)
    expect(instances[0].config.xhrSetup).toBeTypeOf('function')
    const xhr = { setRequestHeader: vi.fn() }
    instances[0].config.xhrSetup(xhr)
    expect(xhr.setRequestHeader).toHaveBeenCalledWith('Authorization', 'Bearer token-123')
    expect(instances[0].loadSource).toHaveBeenCalledWith('https://cdn.example.com/video.m3u8')
    expect(instances[0].attachMedia).toHaveBeenCalledWith(video)
    expect(video.removeEventListener).toHaveBeenCalledWith('enterpictureinpicture', handlePipEnter)
    expect(video.addEventListener).toHaveBeenCalledWith('leavepictureinpicture', handlePipLeave)

    await instances[0].handlers.get(MockHls.Events.MANIFEST_PARSED)?.()
    expect(video.play).toHaveBeenCalledTimes(1)

    instances[0].handlers.get(MockHls.Events.ERROR)?.({}, { fatal: true })
    expect(sourceLifecycle.hlsFatal.value).toBe(true)
    expect(setErrorMessage).toHaveBeenLastCalledWith('视频播放出错')
  })

  it('falls back to native HLS playback when hls.js is unavailable', async () => {
    const { MockHls } = createHlsMock({ supported: false })
    const video = createVideoElement({
      canPlayType: vi.fn().mockReturnValue('probably')
    })
    const videoRef = ref(video)

    const sourceLifecycle = useVideoSourceLifecycle({
      videoRef,
      isPipSupported: ref(false),
      handlePipEnter: vi.fn(),
      handlePipLeave: vi.fn(),
      getVideoUrl: () => 'https://cdn.example.com/native.m3u8',
      setErrorMessage: vi.fn(),
      HlsLib: MockHls
    })

    sourceLifecycle.initPlayer()

    expect(video.src).toBe('https://cdn.example.com/native.m3u8')
    expect(video.play).toHaveBeenCalledTimes(1)
  })

  it('retries playback by clearing previous player state before reinitializing', async () => {
    const { MockHls, instances } = createHlsMock({ supported: true })
    const video = createVideoElement()
    const videoRef = ref(video)
    const setErrorMessage = vi.fn()

    const sourceLifecycle = useVideoSourceLifecycle({
      videoRef,
      isPipSupported: ref(false),
      handlePipEnter: vi.fn(),
      handlePipLeave: vi.fn(),
      getVideoUrl: () => 'https://cdn.example.com/retry.m3u8',
      setErrorMessage,
      HlsLib: MockHls,
      scheduleRetryInit: () => Promise.resolve()
    })

    sourceLifecycle.initPlayer()
    sourceLifecycle.hlsFatal.value = true

    await sourceLifecycle.retryHls()

    expect(instances[0].destroy).toHaveBeenCalledTimes(1)
    expect(video.pause).toHaveBeenCalledTimes(1)
    expect(video.removeAttribute).toHaveBeenCalledWith('src')
    expect(video.load).toHaveBeenCalledTimes(1)
    expect(sourceLifecycle.hlsFatal.value).toBe(false)
    expect(setErrorMessage).toHaveBeenCalledWith('')
    expect(instances).toHaveLength(2)
    expect(instances[1].loadSource).toHaveBeenCalledWith('https://cdn.example.com/retry.m3u8')
  })
})
