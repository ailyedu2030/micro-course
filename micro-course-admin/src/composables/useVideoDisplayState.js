import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

function formatWatermarkTimestamp(date) {
  return `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, '0')}${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

export function useVideoDisplayState(options = {}) {
  const {
    currentTimeRef,
    durationRef,
    userIdRef,
    nowFn = () => new Date(),
    refreshIntervalMs = 60 * 1000,
    setIntervalFn = (handler, delay) => window.setInterval(handler, delay),
    clearIntervalFn = (timerId) => window.clearInterval(timerId)
  } = options

  const watermarkTime = ref(nowFn())
  let watermarkTimerId = null

  function refreshWatermarkTime() {
    watermarkTime.value = nowFn()
  }

  const progressPercent = computed(() => {
    const duration = Number(durationRef?.value)

    if (!duration || Number.isNaN(duration) || duration <= 0) {
      return 0
    }

    const currentTime = Number(currentTimeRef?.value)

    if (Number.isNaN(currentTime)) {
      return 0
    }

    return Math.min(100, Math.max(0, (currentTime / duration) * 100))
  })

  const watermarkText = computed(() => {
    const userId = userIdRef?.value ?? 'unknown'
    return `用户 ${userId} · ${formatWatermarkTimestamp(watermarkTime.value)}`
  })

  onMounted(() => {
    refreshWatermarkTime()
    watermarkTimerId = setIntervalFn(refreshWatermarkTime, refreshIntervalMs)
  })

  onBeforeUnmount(() => {
    if (watermarkTimerId !== null) {
      clearIntervalFn(watermarkTimerId)
      watermarkTimerId = null
    }
  })

  return {
    progressPercent,
    watermarkText,
    refreshWatermarkTime
  }
}
