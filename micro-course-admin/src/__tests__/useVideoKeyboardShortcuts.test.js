import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { useVideoKeyboardShortcuts } from '@/composables/useVideoKeyboardShortcuts'

function createKeyEvent(code, tagName = 'DIV') {
  return {
    code,
    target: { tagName },
    preventDefault: vi.fn()
  }
}

describe('useVideoKeyboardShortcuts', () => {
  it('routes playback shortcuts to the provided handlers', () => {
    const videoRef = ref({})
    const volumePercent = ref(50)
    const togglePlay = vi.fn()
    const skipBackward = vi.fn()
    const skipForward = vi.fn()
    const changeVolume = vi.fn()
    const toggleFullscreen = vi.fn()
    const toggleMute = vi.fn()
    const showControls = vi.fn()

    const { handleKeydown } = useVideoKeyboardShortcuts({
      videoRef,
      volumePercent,
      togglePlay,
      skipBackward,
      skipForward,
      changeVolume,
      toggleFullscreen,
      toggleMute,
      showControls
    })

    handleKeydown(createKeyEvent('Space'))
    expect(togglePlay).toHaveBeenCalledTimes(1)
    expect(showControls).toHaveBeenCalledTimes(1)

    handleKeydown(createKeyEvent('ArrowLeft'))
    expect(skipBackward).toHaveBeenCalledTimes(1)

    handleKeydown(createKeyEvent('ArrowRight'))
    expect(skipForward).toHaveBeenCalledTimes(1)

    handleKeydown(createKeyEvent('ArrowUp'))
    expect(changeVolume).toHaveBeenLastCalledWith(60)

    handleKeydown(createKeyEvent('ArrowDown'))
    expect(changeVolume).toHaveBeenLastCalledWith(40)

    handleKeydown(createKeyEvent('KeyF'))
    expect(toggleFullscreen).toHaveBeenCalledTimes(1)

    handleKeydown(createKeyEvent('KeyM'))
    expect(toggleMute).toHaveBeenCalledTimes(1)
  })

  it('ignores text inputs and clamps volume changes within bounds', () => {
    const videoRef = ref({})
    const volumePercent = ref(95)
    const togglePlay = vi.fn()
    const skipBackward = vi.fn()
    const skipForward = vi.fn()
    const changeVolume = vi.fn()
    const toggleFullscreen = vi.fn()
    const toggleMute = vi.fn()
    const showControls = vi.fn()

    const { handleKeydown } = useVideoKeyboardShortcuts({
      videoRef,
      volumePercent,
      togglePlay,
      skipBackward,
      skipForward,
      changeVolume,
      toggleFullscreen,
      toggleMute,
      showControls
    })

    handleKeydown(createKeyEvent('Space', 'INPUT'))
    expect(togglePlay).not.toHaveBeenCalled()
    expect(showControls).not.toHaveBeenCalled()

    handleKeydown(createKeyEvent('ArrowUp'))
    expect(changeVolume).toHaveBeenLastCalledWith(100)

    volumePercent.value = 5
    handleKeydown(createKeyEvent('ArrowDown'))
    expect(changeVolume).toHaveBeenLastCalledWith(0)
  })
})
