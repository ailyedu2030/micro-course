import { computed, ref, unref } from 'vue'

import { getChapters } from '@/api/chapter'
import { getLearningProgress } from '@/api/learning-progress'
import { getPosts } from '@/api/discussion'

export function useVideoLearningData(options = {}) {
  const {
    courseId,
    chapterId,
    userId,
    chaptersRef,
    discussionsRef,
    currentChapterIndexRef,
    progressIdRef,
    lastPositionRef,
    route,
    router,
    reportProgress = async () => {},
    reloadVideo = async () => {},
    isComponentUnmounted = false,
    getChaptersApi = getChapters,
    getLearningProgressApi = getLearningProgress,
    getPostsApi = getPosts,
    onActiveChapterChange,
    onChaptersError,
    onProgressError,
    onDiscussionsError
  } = options

  const chapters = chaptersRef ?? ref([])
  const discussions = discussionsRef ?? ref([])
  const currentChapterIndex = currentChapterIndexRef ?? ref(0)
  const progressId = progressIdRef ?? ref(null)
  const lastPosition = lastPositionRef ?? ref(0)
  const currentChapter = computed(() => chapters.value[currentChapterIndex.value] || null)

  function getIsUnmounted() {
    return typeof isComponentUnmounted === 'function'
      ? isComponentUnmounted()
      : Boolean(unref(isComponentUnmounted))
  }

  function syncCurrentChapterIndex(targetChapterId = unref(chapterId)) {
    const idx = chapters.value.findIndex((chapter) => Number(chapter.id) === Number(targetChapterId))
    if (idx >= 0) {
      currentChapterIndex.value = idx
      onActiveChapterChange?.(idx)
      return idx
    }
    return -1
  }

  async function loadChapters() {
    if (getIsUnmounted()) return []
    if (!unref(courseId)) return []

    try {
      const res = await getChaptersApi({ courseId: unref(courseId) })
      if (getIsUnmounted()) return []

      const list = res?.data?.items || res?.data || []
      chapters.value = list
        .map((chapter) => ({
          ...chapter,
          isCompleted: false
        }))
        .filter((chapter) => chapter.sectionType === 'VIDEO')

      syncCurrentChapterIndex()
      return chapters.value
    } catch (error) {
      chapters.value = []
      onChaptersError?.(error)
      return []
    }
  }

  async function loadProgress() {
    if (getIsUnmounted()) return null
    if (!unref(userId) || !unref(courseId)) return null

    try {
      const res = await getLearningProgressApi({
        userId: unref(userId),
        courseId: unref(courseId)
      })
      if (getIsUnmounted()) return null

      const rawData = res?.data || []
      let progressData = null

      if (Array.isArray(rawData)) {
        progressData = rawData.find((item) => Number(item.chapterId) === Number(unref(chapterId))) || null
      } else if (rawData && typeof rawData === 'object' && rawData.id &&
        Number(rawData.chapterId) === Number(unref(chapterId))) {
        progressData = rawData
      }

      progressId.value = progressData?.id ?? null
      lastPosition.value = progressData?.videoPosition > 0 ? progressData.videoPosition : 0
      return progressData
    } catch (error) {
      onProgressError?.(error)
      return null
    }
  }

  async function loadDiscussions() {
    if (getIsUnmounted()) return []
    if (!unref(chapterId)) return []

    try {
      const res = await getPostsApi({ chapterId: unref(chapterId), page: 0, size: 20 })
      if (getIsUnmounted()) return []

      discussions.value = res?.data?.items || res?.data || []
      return discussions.value
    } catch (error) {
      discussions.value = []
      onDiscussionsError?.(error)
      return []
    }
  }

  async function switchChapter(id) {
    await reportProgress()
    await router?.push?.({
      query: {
        ...route?.query,
        chapterId: id
      }
    })

    const idx = syncCurrentChapterIndex(id)
    if (idx >= 0) {
      chapters.value = chapters.value.map((chapter, index) => (
        index === idx
          ? { ...chapter, isCompleted: false }
          : chapter
      ))
    }

    lastPosition.value = 0
    progressId.value = null
    await reloadVideo()
  }

  return {
    chapters,
    discussions,
    currentChapterIndex,
    currentChapter,
    progressId,
    lastPosition,
    syncCurrentChapterIndex,
    loadChapters,
    loadProgress,
    loadDiscussions,
    switchChapter
  }
}
