import { ref, unref } from 'vue'
import Hls from 'hls.js'

export function useVideoSourceLifecycle(options = {}) {
  const {
    videoRef,
    isPipSupported,
    handlePipEnter,
    handlePipLeave,
    getVideoUrl = () => '',
    loadVideo = async () => {},
    getAuthToken = () => '',
    setErrorMessage = () => {},
    HlsLib = Hls,
    scheduleRetryInit = () => Promise.resolve()
  } = options

  const hlsInstance = ref(null)
  const hlsFatal = ref(false)

  function isHlsUrl(url) {
    return Boolean(url) && (url.endsWith('.m3u8') || url.includes('.m3u8'))
  }

  function syncPipListeners(video) {
    if (!video || !unref(isPipSupported)) return
    video.removeEventListener('enterpictureinpicture', handlePipEnter)
    video.removeEventListener('leavepictureinpicture', handlePipLeave)
    video.addEventListener('enterpictureinpicture', handlePipEnter)
    video.addEventListener('leavepictureinpicture', handlePipLeave)
  }

  function clearPipListeners(video) {
    if (!video) return
    video.removeEventListener('enterpictureinpicture', handlePipEnter)
    video.removeEventListener('leavepictureinpicture', handlePipLeave)
  }

  function initPlayer() {
    const video = unref(videoRef)
    const url = getVideoUrl()

    if (!video) return false
    if (!url) {
      setErrorMessage('视频地址无效')
      return false
    }

    syncPipListeners(video)

    if (isHlsUrl(url)) {
      if (HlsLib.isSupported()) {
        const token = getAuthToken()
        hlsInstance.value = new HlsLib({
          xhrSetup: (xhr) => {
            if (token) {
              xhr.setRequestHeader('Authorization', 'Bearer ' + token)
            }
          }
        })
        hlsInstance.value.loadSource(url)
        hlsInstance.value.attachMedia(video)
        hlsInstance.value.on(HlsLib.Events.MANIFEST_PARSED, () => {
          video.play().catch(() => {})
        })
        hlsInstance.value.on(HlsLib.Events.ERROR, (event, data) => {
          if (data.fatal) {
            hlsFatal.value = true
            setErrorMessage('视频播放出错')
          }
        })
        return true
      }

      if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = url
        video.play().catch(() => {})
        return true
      }
    }

    video.src = url
    video.load()
    return true
  }

  function destroyPlayer() {
    const video = unref(videoRef)
    clearPipListeners(video)
    if (hlsInstance.value) {
      hlsInstance.value.destroy()
      hlsInstance.value = null
    }
  }

  function retryLoad() {
    hlsFatal.value = false
    loadVideo()
  }

  async function retryHls() {
    hlsFatal.value = false
    setErrorMessage('')
    destroyPlayer()

    const video = unref(videoRef)
    if (video) {
      video.pause()
      video.removeAttribute('src')
      video.load()
    }

    await scheduleRetryInit()
    return initPlayer()
  }

  return {
    hlsInstance,
    hlsFatal,
    isHlsUrl,
    initPlayer,
    retryLoad,
    retryHls,
    destroyPlayer
  }
}
