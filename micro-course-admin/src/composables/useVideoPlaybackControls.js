import { onBeforeUnmount, ref } from 'vue'

export function useVideoPlaybackControls(options = {}) {
  const {
    videoRef,
    videoContainerRef,
    progressTrackRef,
    getLastPosition = () => 0,
    documentRef = typeof document !== 'undefined' ? document : null,
    hideControlsDelayMs = 3000,
    speedToastDurationMs = 1500
  } = options

  const isPlaying = ref(false)
  const isMuted = ref(false)
  const isFullscreen = ref(false)
  const isPip = ref(false)
  const subtitlesEnabled = ref(false)
  const playbackRate = ref(1)
  const volumePercent = ref(100)
  const currentTime = ref(0)
  const duration = ref(0)
  const bufferedPercent = ref(0)
  const controlsVisible = ref(true)
  const speedToastVisible = ref(false)

  let hideControlsTimer = null
  let speedToastTimer = null

  function clearHideControlsTimer() {
    if (hideControlsTimer) {
      clearTimeout(hideControlsTimer)
      hideControlsTimer = null
    }
  }

  function clearSpeedToastTimer() {
    if (speedToastTimer) {
      clearTimeout(speedToastTimer)
      speedToastTimer = null
    }
  }

  function togglePlay() {
    const video = videoRef?.value
    if (!video) return
    if (video.paused) {
      video.play().catch(() => {})
      isPlaying.value = true
    } else {
      video.pause()
      isPlaying.value = false
    }
  }

  function skipBackward() {
    const video = videoRef?.value
    if (video) {
      video.currentTime = Math.max(video.currentTime - 10, 0)
    }
  }

  function skipForward() {
    const video = videoRef?.value
    if (video) {
      video.currentTime = Math.min(video.currentTime + 10, video.duration)
    }
  }

  function seekRelative(delta) {
    const video = videoRef?.value
    if (video) {
      video.currentTime = Math.max(0, Math.min(video.duration || 0, video.currentTime + delta))
    }
  }

  function toggleMute() {
    const video = videoRef?.value
    if (!video) return
    video.muted = !video.muted
    isMuted.value = video.muted
  }

  function changeVolume(value) {
    const video = videoRef?.value
    if (video) {
      video.volume = value / 100
      volumePercent.value = value
      isMuted.value = value === 0
    }
  }

  function changeSpeed(speed) {
    playbackRate.value = speed
    const video = videoRef?.value
    if (video) {
      video.playbackRate = speed
    }
    speedToastVisible.value = true
    clearSpeedToastTimer()
    speedToastTimer = setTimeout(() => {
      speedToastVisible.value = false
    }, speedToastDurationMs)
  }

  function toggleSubtitles() {
    subtitlesEnabled.value = !subtitlesEnabled.value
  }

  async function toggleFullscreen() {
    const container = videoContainerRef?.value
    if (!container || !documentRef) return
    try {
      if (!documentRef.fullscreenElement) {
        await container.requestFullscreen?.()
        isFullscreen.value = true
      } else {
        await documentRef.exitFullscreen?.()
        isFullscreen.value = false
      }
    } catch (error) {
      console.warn('[VideoPlayer] toggleFullscreen 全屏切换失败', error)
      isFullscreen.value = false
    }
  }

  async function togglePictureInPicture() {
    const video = videoRef?.value
    if (!video || !documentRef) return
    try {
      if (documentRef.pictureInPictureElement) {
        await documentRef.exitPictureInPicture()
      } else {
        await video.requestPictureInPicture()
      }
    } catch (error) {
      console.warn('[VideoPlayer] togglePictureInPicture 画中画切换失败', error)
    }
  }

  function handlePipEnter() {
    isPip.value = true
  }

  function handlePipLeave() {
    isPip.value = false
  }

  function seekVideo(event) {
    const video = videoRef?.value
    const track = progressTrackRef?.value
    if (!video || !track) return
    const rect = track.getBoundingClientRect()
    const percent = (event.clientX - rect.left) / rect.width
    video.currentTime = percent * video.duration
  }

  function showControls() {
    controlsVisible.value = true
    clearHideControlsTimer()
  }

  function hideControlsDelayed() {
    clearHideControlsTimer()
    hideControlsTimer = setTimeout(() => {
      if (isPlaying.value) {
        controlsVisible.value = false
      }
    }, hideControlsDelayMs)
  }

  function onCanPlay() {
    const video = videoRef?.value
    if (!video) return
    duration.value = video.duration
    video.playbackRate = playbackRate.value
    video.volume = volumePercent.value / 100
    const lastPosition = getLastPosition()
    if (lastPosition > 0 && lastPosition < video.duration - 10) {
      video.currentTime = lastPosition
    }
  }

  function onTimeUpdate() {
    const video = videoRef?.value
    if (video) {
      currentTime.value = video.currentTime
    }
  }

  function onProgress() {
    const video = videoRef?.value
    if (video && video.buffered.length > 0) {
      bufferedPercent.value = (video.buffered.end(video.buffered.length - 1) / video.duration) * 100
    }
  }

  function handleFullscreenChange() {
    isFullscreen.value = !!documentRef?.fullscreenElement
  }

  onBeforeUnmount(() => {
    clearHideControlsTimer()
    clearSpeedToastTimer()
  })

  return {
    isPlaying,
    isMuted,
    isFullscreen,
    isPip,
    subtitlesEnabled,
    playbackRate,
    volumePercent,
    currentTime,
    duration,
    bufferedPercent,
    controlsVisible,
    speedToastVisible,
    togglePlay,
    skipBackward,
    skipForward,
    seekRelative,
    toggleMute,
    changeVolume,
    changeSpeed,
    toggleSubtitles,
    toggleFullscreen,
    togglePictureInPicture,
    handlePipEnter,
    handlePipLeave,
    seekVideo,
    showControls,
    hideControlsDelayed,
    onCanPlay,
    onTimeUpdate,
    onProgress,
    handleFullscreenChange
  }
}
