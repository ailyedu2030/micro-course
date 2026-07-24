import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { useVideoCompletionFlow } from '@/composables/useVideoCompletionFlow'

describe('useVideoCompletionFlow', () => {
  it('marks the current chapter completed, reports progress, and navigates to exercises after confirmation', async () => {
    const isPlaying = ref(true)
    const chapters = ref([
      { id: 11, title: '第一节', exerciseCount: 2, isCompleted: false }
    ])
    const currentChapterIndex = ref(0)
    const reportProgress = vi.fn().mockResolvedValue(undefined)
    const confirmExerciseStart = vi.fn().mockResolvedValue(undefined)
    const navigateToExercise = vi.fn()

    const completionFlow = useVideoCompletionFlow({
      isPlayingRef: isPlaying,
      chaptersRef: chapters,
      currentChapterIndexRef: currentChapterIndex,
      reportProgress,
      confirmExerciseStart,
      navigateToExercise
    })

    await completionFlow.handleEnded()

    expect(isPlaying.value).toBe(false)
    expect(chapters.value[0].isCompleted).toBe(true)
    expect(reportProgress).toHaveBeenCalledTimes(1)
    expect(confirmExerciseStart).toHaveBeenCalledWith({
      message: '「第一节」的视频已看完，是否开始本节练习？',
      title: '视频播放完成',
      options: {
        confirmButtonText: '开始练习',
        cancelButtonText: '继续看下一节',
        type: 'success'
      }
    })
    expect(navigateToExercise).toHaveBeenCalledWith('/student/chapters/11/exercises')
  })

  it('shows a completion message when the chapter has no exercises', async () => {
    const showSuccessMessage = vi.fn()
    const completionFlow = useVideoCompletionFlow({
      isPlayingRef: ref(true),
      chaptersRef: ref([
        { id: 11, title: '第一节', exerciseCount: 0, isCompleted: false }
      ]),
      currentChapterIndexRef: ref(0),
      reportProgress: vi.fn().mockResolvedValue(undefined),
      showSuccessMessage
    })

    await completionFlow.handleEnded()

    expect(showSuccessMessage).toHaveBeenCalledWith('视频播放完成')
  })

  it('keeps the user on the player when the exercise prompt is dismissed', async () => {
    const navigateToExercise = vi.fn()
    const completionFlow = useVideoCompletionFlow({
      isPlayingRef: ref(true),
      chaptersRef: ref([
        { id: 11, title: '第一节', exerciseCount: 1, isCompleted: false }
      ]),
      currentChapterIndexRef: ref(0),
      reportProgress: vi.fn().mockResolvedValue(undefined),
      confirmExerciseStart: vi.fn().mockRejectedValue(new Error('cancel')),
      navigateToExercise
    })

    await completionFlow.handleEnded()

    expect(navigateToExercise).not.toHaveBeenCalled()
  })
})
