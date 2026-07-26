import { describe, expect, it } from 'vitest'

import { useVideoModuleState } from '@/composables/useVideoModuleState'

describe('useVideoModuleState', () => {
  it('provides the default VideoPlayer shell state and helper actions', () => {
    const state = useVideoModuleState()

    expect(state.loading.value).toBe(true)
    expect(state.errorMsg.value).toBe('')
    expect(state.videoData.value).toEqual({})
    expect(state.chapters.value).toEqual([])
    expect(state.discussions.value).toEqual([])
    expect(state.isPipSupported.value).toBe(false)
    expect(state.currentChapterIndex.value).toBe(0)
    expect(state.isComponentUnmounted.value).toBe(false)

    state.setErrorMessage('播放失败')
    expect(state.errorMsg.value).toBe('播放失败')

    state.clearErrorMessage()
    expect(state.errorMsg.value).toBe('')

    state.markComponentUnmounted()
    expect(state.isComponentUnmounted.value).toBe(true)
  })
})
