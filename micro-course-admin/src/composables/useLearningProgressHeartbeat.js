import { onBeforeUnmount } from 'vue'

export function useLearningProgressHeartbeat(options = {}) {
  const {
    intervalMs = 10000,
    onInterval,
    onBeforeUnmountPersist
  } = options

  let heartbeatTimer = null

  function startHeartbeat() {
    if (heartbeatTimer) {
      return
    }
    heartbeatTimer = setInterval(() => {
      onInterval?.()
    }, intervalMs)
  }

  function stopHeartbeat() {
    if (!heartbeatTimer) {
      return
    }
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }

  function restartHeartbeat() {
    stopHeartbeat()
    startHeartbeat()
  }

  async function flushHeartbeat() {
    stopHeartbeat()
    await onBeforeUnmountPersist?.()
  }

  onBeforeUnmount(async () => {
    await flushHeartbeat()
  })

  return {
    startHeartbeat,
    stopHeartbeat,
    restartHeartbeat,
    flushHeartbeat
  }
}
