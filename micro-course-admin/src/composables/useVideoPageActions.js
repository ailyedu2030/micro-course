import { ref } from 'vue'

export function useVideoPageActions(options = {}) {
  const {
    router,
    errorMsgRef
  } = options

  const highlightedNoteTime = ref(null)

  function highlightTime(time) {
    highlightedNoteTime.value = time ?? null
  }

  function onVideoError() {
    errorMsgRef.value = '视频播放出错，请尝试刷新页面'
  }

  function goBack() {
    router.back()
  }

  return {
    errorMsgRef,
    highlightedNoteTime,
    highlightTime,
    onVideoError,
    goBack
  }
}
