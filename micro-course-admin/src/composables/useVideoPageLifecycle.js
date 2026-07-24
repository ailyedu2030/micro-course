import { nextTick, onBeforeUnmount, onMounted, watch } from 'vue'

function noop() {}

export function useVideoPageLifecycle(options = {}) {
  const {
    isPlayingRef,
    componentUnmountedRef,
    isPipSupportedRef,
    syncViewportMode = noop,
    pictureInPictureEnabled = () => document.pictureInPictureEnabled,
    canRequestPictureInPicture = () => typeof HTMLVideoElement !== 'undefined' && typeof HTMLVideoElement.prototype.requestPictureInPicture === 'function',
    resetVideoProgressReporter = noop,
    nextTickFn = nextTick,
    loadVideo = noop,
    handleKeydown = noop,
    handleFullscreenChange = noop,
    handleResize = noop,
    scrollToActiveChapter = noop,
    startVideoProgressHeartbeat = noop,
    stopVideoProgressHeartbeat = noop,
    destroyPlayer = noop,
    stopBufferingWatchdog = noop,
    addDocumentListener = (event, handler) => document.addEventListener(event, handler),
    removeDocumentListener = (event, handler) => document.removeEventListener(event, handler),
    addWindowListener = (event, handler) => window.addEventListener(event, handler),
    removeWindowListener = (event, handler) => window.removeEventListener(event, handler)
  } = options

  onMounted(async () => {
    syncViewportMode()

    if (isPipSupportedRef) {
      isPipSupportedRef.value = Boolean(pictureInPictureEnabled() && canRequestPictureInPicture())
    }

    resetVideoProgressReporter()
    await nextTickFn()
    loadVideo()

    addDocumentListener('keydown', handleKeydown)
    addDocumentListener('fullscreenchange', handleFullscreenChange)
    addWindowListener('resize', handleResize)
    scrollToActiveChapter()
  })

  watch(isPlayingRef, (playing) => {
    if (playing) {
      startVideoProgressHeartbeat()
      return
    }
    stopVideoProgressHeartbeat()
  })

  onBeforeUnmount(() => {
    if (componentUnmountedRef) {
      componentUnmountedRef.value = true
    }

    destroyPlayer()
    stopBufferingWatchdog()
    removeDocumentListener('keydown', handleKeydown)
    removeDocumentListener('fullscreenchange', handleFullscreenChange)
    removeWindowListener('resize', handleResize)
  })

  return {}
}
