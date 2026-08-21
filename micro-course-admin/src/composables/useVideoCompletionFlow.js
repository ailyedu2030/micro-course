import { ref, unref } from 'vue'

export function useVideoCompletionFlow(options = {}) {
  const {
    isPlayingRef,
    chaptersRef,
    currentChapterIndexRef,
    reportProgress = async () => {},
    confirmExerciseStart = async () => {},
    navigateToExercise = () => {},
    showSuccessMessage = () => {}
  } = options

  const isPlaying = isPlayingRef ?? ref(false)
  const chapters = chaptersRef ?? ref([])
  const currentChapterIndex = currentChapterIndexRef ?? ref(0)

  async function handleEnded() {
    isPlaying.value = false

    const targetIndex = currentChapterIndex.value
    const chapter = chapters.value[targetIndex]
    if (chapter) {
      chapters.value = chapters.value.map((item, index) => (
        index === targetIndex
          ? { ...item, isCompleted: true }
          : item
      ))
    }

    try {
      // P1-2026-08-21: 完成态必须 force+completed 落库(原 reportProgress() 无 force → paused=true 不持久化)
      await reportProgress(true, true)
    } catch {
      // 进度上报失败不阻塞完成流程
    }

    if (chapter?.exerciseCount > 0) {
      try {
        await confirmExerciseStart({
          message: `「${chapter.title}」的视频已看完，是否开始本节练习？`,
          title: '视频播放完成',
          options: {
            confirmButtonText: '开始练习',
            cancelButtonText: '继续看下一节',
            type: 'success'
          }
        })
        const chapterId = chapter?.id
        if (chapterId) {
          navigateToExercise(`/student/chapters/${chapterId}/exercises`)
        }
      } catch {
        // 用户选择继续留在当前播放器，不做额外处理
      }
      return
    }

    showSuccessMessage('视频播放完成')
  }

  return {
    handleEnded
  }
}
