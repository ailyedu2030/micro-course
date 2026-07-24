import { unref } from 'vue'

export function useVideoKeyboardShortcuts(options = {}) {
  const {
    videoRef,
    volumePercent,
    togglePlay,
    skipBackward,
    skipForward,
    changeVolume,
    toggleFullscreen,
    toggleMute,
    showControls
  } = options

  function handleKeydown(event) {
    const tagName = event?.target?.tagName
    if (tagName === 'INPUT' || tagName === 'TEXTAREA') {
      return
    }

    const video = videoRef?.value

    switch (event?.code) {
      case 'Space':
        event.preventDefault()
        togglePlay?.()
        showControls?.()
        break
      case 'ArrowLeft':
        event.preventDefault()
        skipBackward?.()
        showControls?.()
        break
      case 'ArrowRight':
        event.preventDefault()
        skipForward?.()
        showControls?.()
        break
      case 'ArrowUp':
        event.preventDefault()
        if (video) {
          changeVolume?.(Math.min(100, unref(volumePercent) + 10))
          showControls?.()
        }
        break
      case 'ArrowDown':
        event.preventDefault()
        if (video) {
          changeVolume?.(Math.max(0, unref(volumePercent) - 10))
          showControls?.()
        }
        break
      case 'KeyF':
        event.preventDefault()
        toggleFullscreen?.()
        break
      case 'KeyM':
        event.preventDefault()
        toggleMute?.()
        showControls?.()
        break
    }
  }

  return {
    handleKeydown
  }
}
