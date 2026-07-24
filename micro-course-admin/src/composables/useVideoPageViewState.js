import { computed, ref } from 'vue'

export function useVideoPageViewState(options = {}) {
  const {
    chaptersRef,
    currentChapterIndexRef,
    volumePercentRef
  } = options

  const activeTab = ref('chapters')
  const showChapterList = ref(true)
  const currentSubtitle = ref('')

  const currentChapter = computed(() => chaptersRef.value[currentChapterIndexRef.value])
  const volume = computed(() => volumePercentRef.value / 100)

  function toggleChapterList() {
    showChapterList.value = !showChapterList.value
  }

  return {
    activeTab,
    showChapterList,
    currentSubtitle,
    currentChapter,
    volume,
    toggleChapterList
  }
}
