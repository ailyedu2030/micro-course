import { reactive } from 'vue'
import { describe, expect, it } from 'vitest'

import { useVideoRouteContext } from '@/composables/useVideoRouteContext'

describe('useVideoRouteContext', () => {
  it('derives route ids and user id from the current route and store', () => {
    const route = reactive({
      params: {
        id: 'course-from-param',
        videoId: 'video-from-param'
      },
      query: {
        courseId: 'course-from-query',
        chapterId: 'chapter-from-query',
        videoId: 'video-from-query'
      }
    })

    const userStore = reactive({
      userInfo: {
        id: 42
      }
    })

    const context = useVideoRouteContext({
      route,
      userStore
    })

    expect(context.videoId.value).toBe('video-from-param')
    expect(context.courseId.value).toBe('course-from-param')
    expect(context.chapterId.value).toBe('chapter-from-query')
    expect(context.userId.value).toBe(42)

    route.params.videoId = ''
    route.query.videoId = 'video-from-query-updated'
    userStore.userInfo.id = 84

    expect(context.videoId.value).toBe('video-from-query-updated')
    expect(context.userId.value).toBe(84)
  })
})
