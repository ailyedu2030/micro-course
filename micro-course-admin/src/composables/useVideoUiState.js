import { onBeforeUnmount, ref } from 'vue'

export function useVideoUiState(options = {}) {
  const {
    getViewportWidth = () => (typeof window !== 'undefined' ? window.innerWidth : 1024),
    mobileBreakpoint = 768,
    resizeDelayMs = 200,
    objectivesHideDelayMs = 3000
  } = options

  const isMobile = ref(getViewportWidth() <= mobileBreakpoint)
  const showObjectives = ref(false)

  let resizeTimer = null
  let objectivesTimer = null

  function clearResizeTimer() {
    if (resizeTimer) {
      clearTimeout(resizeTimer)
      resizeTimer = null
    }
  }

  function clearObjectivesTimer() {
    if (objectivesTimer) {
      clearTimeout(objectivesTimer)
      objectivesTimer = null
    }
  }

  function syncViewportMode() {
    isMobile.value = getViewportWidth() <= mobileBreakpoint
  }

  function handleResize() {
    clearResizeTimer()
    resizeTimer = setTimeout(() => {
      syncViewportMode()
      resizeTimer = null
    }, resizeDelayMs)
  }

  function showObjectivesOverlay() {
    showObjectives.value = true
    clearObjectivesTimer()
    objectivesTimer = setTimeout(() => {
      showObjectives.value = false
      objectivesTimer = null
    }, objectivesHideDelayMs)
  }

  onBeforeUnmount(() => {
    clearResizeTimer()
    clearObjectivesTimer()
  })

  return {
    isMobile,
    showObjectives,
    syncViewportMode,
    handleResize,
    showObjectivesOverlay
  }
}
