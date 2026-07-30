import { onBeforeUnmount, onMounted } from 'vue'

export function useLearningProgressHeartbeat(options = {}) {
  const {
    intervalMs = 10000,
    onInterval,
    onBeforeUnmountPersist
  } = options

  let heartbeatTimer = null
  /** @type {Set<{type:string, handler:Function}>} */
  let visibilityListeners = null

  /**
   * 构造页面可见性变化时刷新的监听器（pagehide / visibilitychange）。
   * 当用户切换标签页、关闭页面或从后台恢复时，保证最后的心跳数据成功提交。
   * 只在 startHeartbeat 时注册一次，stopHeartbeat 时自动拆除。
   */
  function setupVisibilityListeners() {
    if (visibilityListeners) return // 已经注册过

    visibilityListeners = new Set()

    const pageHideHandler = async () => {
      await onBeforeUnmountPersist?.()
    }
    addEventListener('pagehide', pageHideHandler)
    visibilityListeners.add({ type: 'pagehide', handler: pageHideHandler })

    const visibilityHandler = async () => {
      if (document.visibilityState === 'hidden') {
        await onBeforeUnmountPersist?.()
      }
    }
    addEventListener('visibilitychange', visibilityHandler)
    visibilityListeners.add({ type: 'visibilitychange', handler: visibilityHandler })
  }

  function teardownVisibilityListeners() {
    if (!visibilityListeners) return
    for (const { type, handler } of visibilityListeners) {
      removeEventListener(type, handler)
    }
    visibilityListeners = null
  }

  function startHeartbeat() {
    if (heartbeatTimer) {
      return
    }
    heartbeatTimer = setInterval(() => {
      onInterval?.()
    }, intervalMs)
    setupVisibilityListeners()
  }

  function stopHeartbeat() {
    if (!heartbeatTimer) {
      return
    }
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
    teardownVisibilityListeners()
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
