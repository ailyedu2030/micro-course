import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { useVideoLearningData } from '@/composables/useVideoLearningData'

describe('useVideoLearningData', () => {
  it('filters non-video chapters and syncs the active chapter index', async () => {
    const onActiveChapterChange = vi.fn()
    const learningData = useVideoLearningData({
      courseId: computed(() => 8),
      chapterId: computed(() => 22),
      userId: computed(() => 3),
      getChaptersApi: vi.fn().mockResolvedValue({
        data: {
          items: [
            { id: 11, title: '导学', sectionType: 'TEXT' },
            { id: 22, title: '第一节', sectionType: 'VIDEO' },
            { id: 33, title: '第二节', sectionType: 'VIDEO' }
          ]
        }
      }),
      onActiveChapterChange
    })

    await learningData.loadChapters()

    expect(learningData.chapters.value).toEqual([
      { id: 22, title: '第一节', sectionType: 'VIDEO', isCompleted: false },
      { id: 33, title: '第二节', sectionType: 'VIDEO', isCompleted: false }
    ])
    expect(learningData.currentChapterIndex.value).toBe(0)
    expect(learningData.currentChapter.value?.id).toBe(22)
    expect(onActiveChapterChange).toHaveBeenCalledWith(0)
  })

  it('restores matching progress from array responses', async () => {
    const learningData = useVideoLearningData({
      courseId: computed(() => 8),
      chapterId: computed(() => 22),
      userId: computed(() => 3),
      getLearningProgressApi: vi.fn().mockResolvedValue({
        data: [
          { id: 1, chapterId: 11, videoPosition: 9 },
          { id: 2, chapterId: 22, videoPosition: 88 }
        ]
      })
    })

    await learningData.loadProgress()

    expect(learningData.progressId.value).toBe(2)
    expect(learningData.lastPosition.value).toBe(88)
  })

  it('supports single-object progress responses and ignores mismatched chapters', async () => {
    const learningData = useVideoLearningData({
      courseId: computed(() => 8),
      chapterId: computed(() => 22),
      userId: computed(() => 3),
      getLearningProgressApi: vi.fn()
        .mockResolvedValueOnce({
          data: { id: 6, chapterId: 22, videoPosition: 51 }
        })
        .mockResolvedValueOnce({
          data: { id: 7, chapterId: 99, videoPosition: 120 }
        })
    })

    await learningData.loadProgress()
    expect(learningData.progressId.value).toBe(6)
    expect(learningData.lastPosition.value).toBe(51)

    learningData.progressId.value = null
    learningData.lastPosition.value = 0
    await learningData.loadProgress()
    expect(learningData.progressId.value).toBe(null)
    expect(learningData.lastPosition.value).toBe(0)
  })

  it('loads discussions and orchestrates chapter switching', async () => {
    const router = { push: vi.fn().mockResolvedValue(undefined) }
    const route = { query: { chapterId: 11, courseId: 8, foo: 'bar' } }
    const reportProgress = vi.fn().mockResolvedValue(undefined)
    const reloadVideo = vi.fn().mockResolvedValue(undefined)
    const learningData = useVideoLearningData({
      courseId: computed(() => 8),
      chapterId: ref(11),
      userId: computed(() => 3),
      route,
      router,
      reportProgress,
      reloadVideo,
      getPostsApi: vi.fn().mockResolvedValue({
        data: {
          items: [
            { id: 101, title: '问题1' },
            { id: 102, title: '问题2' }
          ]
        }
      })
    })
    learningData.chapters.value = [
      { id: 11, title: '第一节', isCompleted: true },
      { id: 22, title: '第二节', isCompleted: true }
    ]
    learningData.currentChapterIndex.value = 0
    learningData.progressId.value = 9
    learningData.lastPosition.value = 64

    await learningData.loadDiscussions()
    await learningData.switchChapter(22)

    expect(learningData.discussions.value).toEqual([
      { id: 101, title: '问题1' },
      { id: 102, title: '问题2' }
    ])
    expect(reportProgress).toHaveBeenCalledTimes(1)
    expect(router.push).toHaveBeenCalledWith({
      query: {
        chapterId: 22,
        courseId: 8,
        foo: 'bar'
      }
    })
    expect(learningData.currentChapterIndex.value).toBe(1)
    expect(learningData.chapters.value[1].isCompleted).toBe(false)
    expect(learningData.progressId.value).toBe(null)
    expect(learningData.lastPosition.value).toBe(0)
    expect(reloadVideo).toHaveBeenCalledTimes(1)
  })
})
