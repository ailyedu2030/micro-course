import { onBeforeUnmount, ref, watch } from 'vue'

function toArray(value) {
  if (!value) {
    return []
  }

  return Array.from(value)
}

function getCueText(track) {
  const activeCue = toArray(track?.activeCues)[0]
  return activeCue?.text || ''
}

export function useVideoSubtitles(options = {}) {
  const {
    videoRef,
    subtitleUrlRef
  } = options

  const subtitlesEnabled = ref(false)
  const currentSubtitle = ref('')

  let boundTrack = null
  let boundCueChangeHandler = null

  function detachCueChangeListener() {
    if (boundTrack && boundCueChangeHandler) {
      boundTrack.removeEventListener?.('cuechange', boundCueChangeHandler)
    }

    boundTrack = null
    boundCueChangeHandler = null
  }

  function updateCurrentSubtitle() {
    if (!subtitlesEnabled.value || !boundTrack) {
      currentSubtitle.value = ''
      return
    }

    currentSubtitle.value = getCueText(boundTrack)
  }

  function getTracks() {
    return toArray(videoRef?.value?.textTracks)
  }

  function applyTrackMode() {
    const tracks = getTracks()

    tracks.forEach((track) => {
      track.mode = subtitlesEnabled.value ? 'showing' : 'disabled'
    })

    if (!subtitlesEnabled.value) {
      currentSubtitle.value = ''
    }
  }

  function bindCueChangeListener(track) {
    if (boundTrack === track) {
      return
    }

    detachCueChangeListener()

    if (!track) {
      currentSubtitle.value = ''
      return
    }

    boundCueChangeHandler = () => {
      updateCurrentSubtitle()
    }
    boundTrack = track
    boundTrack.addEventListener?.('cuechange', boundCueChangeHandler)
    updateCurrentSubtitle()
  }

  function syncSubtitleTrack() {
    const subtitleUrl = subtitleUrlRef?.value

    if (!subtitleUrl) {
      subtitlesEnabled.value = false
      applyTrackMode()
      detachCueChangeListener()
      currentSubtitle.value = ''
      return
    }

    const [firstTrack] = getTracks()

    bindCueChangeListener(firstTrack)
    applyTrackMode()
  }

  function toggleSubtitles() {
    if (!subtitleUrlRef?.value) {
      return
    }

    subtitlesEnabled.value = !subtitlesEnabled.value
    syncSubtitleTrack()
  }

  watch(
    () => subtitleUrlRef?.value,
    () => {
      syncSubtitleTrack()
    },
    { immediate: true }
  )

  onBeforeUnmount(() => {
    subtitlesEnabled.value = false
    applyTrackMode()
    detachCueChangeListener()
    currentSubtitle.value = ''
  })

  return {
    subtitlesEnabled,
    currentSubtitle,
    toggleSubtitles,
    syncSubtitleTrack
  }
}
