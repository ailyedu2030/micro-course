import { nextTick, ref, unref } from 'vue'

import { getVideoById } from '@/api/video'

export function useVideoLoadOrchestrator(options = {}) {
  const {
    loadingRef,
    errorMsgRef,
    videoDataRef,
    videoId,
    getVideoApi = getVideoById,
    nextTickFn = nextTick,
    initPlayer = () => {},
    loadChapters = async () => {},
    loadProgress = async () => {},
    loadDiscussions = async () => {},
    loadLocalPosition = () => {},
    loadNotesFromStorage = () => {},
    showObjectivesOverlay = () => {},
    isComponentUnmounted = false,
    onLoadError
  } = options

  const loading = loadingRef ?? ref(false)
  const errorMsg = errorMsgRef ?? ref('')
  const videoData = videoDataRef ?? ref({})

  function getIsUnmounted() {
    return typeof isComponentUnmounted === 'function'
      ? isComponentUnmounted()
      : Boolean(unref(isComponentUnmounted))
  }

  async function loadVideo() {
    if (getIsUnmounted()) return false

    loading.value = true
    errorMsg.value = ''

    try {
      const res = await getVideoApi(unref(videoId))
      if (getIsUnmounted()) return false

      videoData.value = res?.data || res || {}

      await nextTickFn()
      if (getIsUnmounted()) return false

      initPlayer()
      await Promise.all([loadChapters(), loadProgress(), loadDiscussions()])
      if (getIsUnmounted()) return false

      loadLocalPosition()
      loadNotesFromStorage()
      showObjectivesOverlay()
      return true
    } catch (error) {
      if (getIsUnmounted()) return false

      onLoadError?.(error)
      errorMsg.value = '无法加载视频，请检查网络连接'
      return false
    } finally {
      if (!getIsUnmounted()) {
        loading.value = false
      }
    }
  }

  return {
    loading,
    errorMsg,
    videoData,
    loadVideo
  }
}
