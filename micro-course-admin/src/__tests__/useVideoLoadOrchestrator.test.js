import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { useVideoLoadOrchestrator } from '@/composables/useVideoLoadOrchestrator'

describe('useVideoLoadOrchestrator', () => {
  it('loads video data and orchestrates player initialization with follow-up tasks', async () => {
    const loading = ref(false)
    const errorMsg = ref('旧错误')
    const videoData = ref({})
    const callSequence = []

    const orchestrator = useVideoLoadOrchestrator({
      loadingRef: loading,
      errorMsgRef: errorMsg,
      videoDataRef: videoData,
      videoId: computed(() => 42),
      getVideoApi: vi.fn().mockResolvedValue({
        data: { id: 42, title: '第一课', hlsUrl: 'https://cdn.example.com/42.m3u8' }
      }),
      nextTickFn: vi.fn().mockImplementation(async () => {
        callSequence.push('nextTick')
      }),
      initPlayer: vi.fn().mockImplementation(() => {
        callSequence.push('initPlayer')
      }),
      loadChapters: vi.fn().mockImplementation(async () => {
        callSequence.push('loadChapters')
      }),
      loadProgress: vi.fn().mockImplementation(async () => {
        callSequence.push('loadProgress')
      }),
      loadDiscussions: vi.fn().mockImplementation(async () => {
        callSequence.push('loadDiscussions')
      }),
      loadLocalPosition: vi.fn().mockImplementation(() => {
        callSequence.push('loadLocalPosition')
      }),
      loadNotesFromStorage: vi.fn().mockImplementation(() => {
        callSequence.push('loadNotesFromStorage')
      }),
      showObjectivesOverlay: vi.fn().mockImplementation(() => {
        callSequence.push('showObjectivesOverlay')
      })
    })

    const loaded = await orchestrator.loadVideo()

    expect(loaded).toBe(true)
    expect(loading.value).toBe(false)
    expect(errorMsg.value).toBe('')
    expect(videoData.value).toEqual({
      id: 42,
      title: '第一课',
      hlsUrl: 'https://cdn.example.com/42.m3u8'
    })
    expect(callSequence).toEqual([
      'nextTick',
      'initPlayer',
      'loadChapters',
      'loadProgress',
      'loadDiscussions',
      'loadLocalPosition',
      'loadNotesFromStorage',
      'showObjectivesOverlay'
    ])
  })

  it('sets a generic error message when the video request fails', async () => {
    const loading = ref(false)
    const errorMsg = ref('')
    const videoData = ref({})
    const onLoadError = vi.fn()

    const orchestrator = useVideoLoadOrchestrator({
      loadingRef: loading,
      errorMsgRef: errorMsg,
      videoDataRef: videoData,
      videoId: computed(() => 42),
      getVideoApi: vi.fn().mockRejectedValue(new Error('network down')),
      onLoadError
    })

    const loaded = await orchestrator.loadVideo()

    expect(loaded).toBe(false)
    expect(loading.value).toBe(false)
    expect(errorMsg.value).toBe('无法加载视频，请检查网络连接')
    expect(onLoadError).toHaveBeenCalledTimes(1)
  })

  it('avoids post-request state updates when the component unmounts mid-load', async () => {
    const loading = ref(false)
    const errorMsg = ref('旧错误')
    const videoData = ref({ stale: true })
    let unmounted = false
    const initPlayer = vi.fn()
    const loadChapters = vi.fn()

    const orchestrator = useVideoLoadOrchestrator({
      loadingRef: loading,
      errorMsgRef: errorMsg,
      videoDataRef: videoData,
      videoId: computed(() => 42),
      isComponentUnmounted: () => unmounted,
      getVideoApi: vi.fn().mockImplementation(async () => {
        unmounted = true
        return {
          data: { id: 42, title: '第一课' }
        }
      }),
      initPlayer,
      loadChapters
    })

    const loaded = await orchestrator.loadVideo()

    expect(loaded).toBe(false)
    expect(videoData.value).toEqual({ stale: true })
    expect(errorMsg.value).toBe('')
    expect(loading.value).toBe(true)
    expect(initPlayer).not.toHaveBeenCalled()
    expect(loadChapters).not.toHaveBeenCalled()
  })
})
