import { onBeforeUnmount, ref, unref } from 'vue'

export function useVideoTouchGestures(options = {}) {
  const {
    isMobile,
    videoRef,
    changeVolume,
    skipBackward,
    skipForward,
    now = () => Date.now(),
    indicatorHideDelayMs = 500,
    seekIndicatorHideDelayMs = 600,
    doubleTapWindowMs = 300
  } = options

  const volumeIndicatorVisible = ref(false)
  const brightnessIndicatorVisible = ref(false)
  const volumeIndicatorValue = ref(100)
  const brightnessIndicatorValue = ref(100)
  const gestureIndicatorX = ref(0)
  const gestureIndicatorY = ref(0)
  const showSeekIndicator = ref(false)
  const seekIndicatorDir = ref('')
  const seekIndicatorSeconds = ref(10)

  let touchStartX = 0
  let touchStartY = 0
  let touchStartTime = 0
  let touchStartVolume = 100
  let touchStartBrightness = 100
  let tapCount = 0
  let tapTimer = null
  let indicatorTimer = null
  let seekIndicatorTimer = null
  let isSwiping = false
  let swipeType = null

  function clearTapTimer() {
    if (tapTimer) {
      clearTimeout(tapTimer)
      tapTimer = null
    }
  }

  function clearIndicatorTimer() {
    if (indicatorTimer) {
      clearTimeout(indicatorTimer)
      indicatorTimer = null
    }
  }

  function clearSeekIndicatorTimer() {
    if (seekIndicatorTimer) {
      clearTimeout(seekIndicatorTimer)
      seekIndicatorTimer = null
    }
  }

  function hideGestureIndicatorsDelayed() {
    if (!volumeIndicatorVisible.value && !brightnessIndicatorVisible.value) {
      return
    }
    clearIndicatorTimer()
    indicatorTimer = setTimeout(() => {
      volumeIndicatorVisible.value = false
      brightnessIndicatorVisible.value = false
      indicatorTimer = null
    }, indicatorHideDelayMs)
  }

  function showSeekIndicatorHelper(direction, seconds = 10) {
    seekIndicatorDir.value = direction
    seekIndicatorSeconds.value = seconds
    showSeekIndicator.value = true
    clearSeekIndicatorTimer()
    seekIndicatorTimer = setTimeout(() => {
      showSeekIndicator.value = false
      seekIndicatorTimer = null
    }, seekIndicatorHideDelayMs)
  }

  function handleTouchStart(event) {
    if (!unref(isMobile)) return

    const touch = event?.touches?.[0]
    if (!touch) return

    touchStartX = touch.clientX
    touchStartY = touch.clientY
    touchStartTime = now()

    const video = videoRef?.value
    if (video) {
      touchStartVolume = video.volume * 100
      touchStartBrightness = brightnessIndicatorValue.value
    }

    isSwiping = false
    swipeType = null
  }

  function handleTouchMove(event) {
    if (!unref(isMobile)) return

    const touch = event?.touches?.[0]
    const video = videoRef?.value
    if (!touch || !video) return

    const deltaX = touch.clientX - touchStartX
    const deltaY = touch.clientY - touchStartY
    const rect = event?.target?.closest?.('.video-container')?.getBoundingClientRect?.()
    if (!rect) return

    if (!isSwiping && (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10)) {
      const relativeX = touchStartX - rect.left
      const isRightSide = relativeX > rect.width / 2

      if (Math.abs(deltaX) > Math.abs(deltaY)) {
        swipeType = 'seek'
      } else {
        swipeType = isRightSide ? 'volume' : 'brightness'
        isSwiping = true
        gestureIndicatorX.value = touch.clientX
        gestureIndicatorY.value = touch.clientY
      }
    }

    if (swipeType === 'volume') {
      const newVolume = Math.min(100, Math.max(0, touchStartVolume + (-deltaY * 0.4)))
      changeVolume?.(newVolume)
      volumeIndicatorValue.value = Math.round(newVolume)
      volumeIndicatorVisible.value = true
    } else if (swipeType === 'brightness') {
      const newBrightness = Math.min(100, Math.max(20, touchStartBrightness + (-deltaY * 0.4)))
      brightnessIndicatorValue.value = Math.round(newBrightness)
      brightnessIndicatorVisible.value = true
      video.style.filter = `brightness(${newBrightness / 100})`
    }
  }

  function handleTouchEnd(event) {
    if (!unref(isMobile)) return

    const touch = event?.changedTouches?.[0]
    if (!touch) return

    const elapsed = now() - touchStartTime
    const deltaX = Math.abs(touch.clientX - touchStartX)
    const deltaY = Math.abs(touch.clientY - touchStartY)
    const wasSwiping = isSwiping

    hideGestureIndicatorsDelayed()
    swipeType = null
    isSwiping = false

    if (elapsed >= doubleTapWindowMs || deltaX >= 30 || deltaY >= 30 || wasSwiping) {
      return
    }

    const rect = event?.target?.closest?.('.video-container')?.getBoundingClientRect?.()
    if (!rect) return

    const tapRegion = (touch.clientX - rect.left) / rect.width
    tapCount += 1
    clearTapTimer()

    if (tapCount === 2) {
      tapCount = 0
      if (tapRegion < 1 / 3) {
        skipBackward?.()
        showSeekIndicatorHelper('backward', 10)
      } else if (tapRegion > 2 / 3) {
        skipForward?.()
        showSeekIndicatorHelper('forward', 10)
      }
      return
    }

    tapTimer = setTimeout(() => {
      tapCount = 0
      tapTimer = null
    }, doubleTapWindowMs)
  }

  onBeforeUnmount(() => {
    clearTapTimer()
    clearIndicatorTimer()
    clearSeekIndicatorTimer()
  })

  return {
    volumeIndicatorVisible,
    brightnessIndicatorVisible,
    volumeIndicatorValue,
    brightnessIndicatorValue,
    gestureIndicatorX,
    gestureIndicatorY,
    showSeekIndicator,
    seekIndicatorDir,
    seekIndicatorSeconds,
    handleTouchStart,
    handleTouchMove,
    handleTouchEnd
  }
}
