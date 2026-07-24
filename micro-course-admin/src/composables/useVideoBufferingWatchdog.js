import { ref } from 'vue'

export function useVideoBufferingWatchdog(options = {}) {
  const {
    showWarning = () => {},
    showRetryConfirm = async () => {},
    onRetry = async () => {},
    warningDelayMs = 15000,
    retryDelayMs = 30000
  } = options

  const isBuffering = ref(false)
  let warningTimer = null
  let retryTimer = null

  function clearTimers() {
    if (warningTimer) {
      clearTimeout(warningTimer)
      warningTimer = null
    }
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
  }

  function startWatchdog() {
    clearTimers()

    warningTimer = setTimeout(() => {
      if (isBuffering.value) {
        showWarning({
          message: '视频缓冲中,网络可能较慢,请稍候...',
          duration: 5000
        })
      }
    }, warningDelayMs)

    retryTimer = setTimeout(() => {
      if (!isBuffering.value) return
      showRetryConfirm({
        message: '视频缓冲超过 30 秒,可能是网络问题。是否重试?',
        title: '缓冲超时',
        options: {
          confirmButtonText: '重试',
          cancelButtonText: '继续等待',
          type: 'warning'
        }
      }).then(() => {
        onRetry()
      }).catch(() => {
        // 用户选择继续等待,不做处理
      })
    }, retryDelayMs)
  }

  function stopWatchdog() {
    clearTimers()
  }

  function onBufferingStart() {
    isBuffering.value = true
    startWatchdog()
  }

  function onBufferingEnd() {
    isBuffering.value = false
    stopWatchdog()
  }

  return {
    isBuffering,
    startWatchdog,
    stopWatchdog,
    onBufferingStart,
    onBufferingEnd
  }
}
