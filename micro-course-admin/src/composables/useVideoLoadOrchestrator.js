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
    // P1-C 修复 (2026-08-04): VideoPlayer 传入 startVideoProgressHeartbeat 但本函数
    // 从未解构/调用 → 心跳从未启动 → 视频播放进度不上报（学习进度/断点续播失效）。
    startVideoProgressHeartbeat = () => {},
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

      // P1-C 修复(2026-08-03): 必须先释放 loading 骨架，<video> 才会挂载。
      // 旧顺序在 loading=true 时调用 initPlayer → videoRef 为空 → 播放器永不初始化
      // （页面 UI 正常但视频黑屏，VideoPlayer 真实链路复现）。
      loading.value = false
      await nextTickFn()
      if (getIsUnmounted()) return false

      initPlayer()
      // 播放器初始化完成后启动进度心跳（每 10s 上报 + 页面隐藏时冲刷）
      startVideoProgressHeartbeat()
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
