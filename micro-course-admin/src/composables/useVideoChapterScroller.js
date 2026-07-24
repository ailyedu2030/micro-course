import { nextTick, ref } from 'vue'

export function useVideoChapterScroller(options = {}) {
  const {
    currentChapterIndexRef,
    nextTickFn = nextTick
  } = options

  const chapterItemRefs = ref({})

  function setChapterItemRef(el, index) {
    if (!el) {
      return
    }
    chapterItemRefs.value[index] = el
  }

  function scrollToActiveChapter() {
    nextTickFn(() => {
      const activeIndex = currentChapterIndexRef.value
      const el = chapterItemRefs.value[activeIndex]
      if (!el) {
        return
      }
      el.scrollIntoView({
        behavior: 'smooth',
        block: 'nearest'
      })
    })
  }

  return {
    chapterItemRefs,
    setChapterItemRef,
    scrollToActiveChapter
  }
}
