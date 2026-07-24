import { computed } from 'vue'

export function useVideoRouteContext(options = {}) {
  const {
    route,
    userStore
  } = options

  const videoId = computed(() => route.params.videoId || route.query.videoId)
  const courseId = computed(() => route.params.id || route.query.courseId)
  const chapterId = computed(() => route.query.chapterId)
  const userId = computed(() => userStore.userInfo?.id)

  return {
    videoId,
    courseId,
    chapterId,
    userId
  }
}
