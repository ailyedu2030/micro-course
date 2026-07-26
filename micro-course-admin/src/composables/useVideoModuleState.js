import { ref } from 'vue'

export function useVideoModuleState() {
  const loading = ref(true)
  const errorMsg = ref('')
  const videoData = ref({})
  const chapters = ref([])
  const discussions = ref([])
  const isPipSupported = ref(false)
  const currentChapterIndex = ref(0)
  const isComponentUnmounted = ref(false)

  function setErrorMessage(message) {
    errorMsg.value = message
  }

  function clearErrorMessage() {
    errorMsg.value = ''
  }

  function markComponentUnmounted() {
    isComponentUnmounted.value = true
  }

  return {
    loading,
    errorMsg,
    videoData,
    chapters,
    discussions,
    isPipSupported,
    currentChapterIndex,
    isComponentUnmounted,
    setErrorMessage,
    clearErrorMessage,
    markComponentUnmounted
  }
}
