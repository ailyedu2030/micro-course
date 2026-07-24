import { ref, unref } from 'vue'

import { createLearningProgress, getLearningProgress, updateLearningProgress } from '@/api/learning-progress'
import { useLearningProgressReporter } from '@/composables/useLearningProgressReporter'

export function useVideoProgressFlow(options = {}) {
  const {
    reporterFactory = useLearningProgressReporter,
    videoRef,
    videoId,
    courseId,
    chapterId,
    userId,
    progressIdRef,
    isComponentUnmounted = false,
    saveLocalPosition = () => {},
    showWarning = () => {},
    storage = typeof sessionStorage !== 'undefined' ? sessionStorage : null
  } = options

  const progressId = progressIdRef ?? ref(null)
  let lastReportedProgress = 0
  let lastFailedProgress = null

  function getIsUnmounted() {
    return typeof isComponentUnmounted === 'function'
      ? isComponentUnmounted()
      : Boolean(unref(isComponentUnmounted))
  }

  function getCurrentProgressSnapshot() {
    const video = unref(videoRef)
    if (!video || !video.duration) {
      return null
    }

    const current = video.currentTime
    return {
      current,
      progressPercentVal: (current / video.duration) * 100
    }
  }

  const {
    persistProgress,
    resetProgressReporter
  } = reporterFactory({
    getDedupKey: () => unref(videoId) ? `progress_dedup_video_${unref(videoId)}` : '',
    shouldPersist: ({ force }) => {
      if (!force && getIsUnmounted()) return false
      const video = unref(videoRef)
      if (!video || !video.duration) return false
      if (!force && video.paused) return false
      return true
    },
    getProgressRecord: () => progressId.value ? { id: progressId.value } : null,
    setProgressRecord: (record) => {
      if (record?.id) {
        progressId.value = record.id
      }
    },
    createPayload: () => {
      const snapshot = getCurrentProgressSnapshot()
      return {
        userId: unref(userId),
        courseId: unref(courseId),
        chapterId: unref(chapterId),
        videoPosition: Math.floor(snapshot?.current || 0),
        videoProgress: Math.round(snapshot?.progressPercentVal || 0)
      }
    },
    updatePayload: () => {
      const snapshot = getCurrentProgressSnapshot()
      return {
        videoPosition: Math.floor(snapshot?.current || 0),
        videoProgress: Math.round(snapshot?.progressPercentVal || 0)
      }
    },
    createProgress: createLearningProgress,
    updateProgress: updateLearningProgress,
    findExistingProgress: async () => {
      const res = await getLearningProgress({
        courseId: unref(courseId),
        chapterId: unref(chapterId)
      })
      const rawData = res.data || []
      if (Array.isArray(rawData)) {
        return rawData.find(p => Number(p.chapterId) === Number(unref(chapterId)))
      }
      if (rawData && typeof rawData === 'object' && rawData.id &&
        Number(rawData.chapterId) === Number(unref(chapterId))) {
        return rawData
      }
      return null
    },
    onPersisted: () => {
      const snapshot = getCurrentProgressSnapshot()
      lastReportedProgress = snapshot?.progressPercentVal || 0
      lastFailedProgress = null
      if (snapshot) {
        saveLocalPosition(snapshot.current)
      }
    },
    onError: ({ error }) => {
      const snapshot = getCurrentProgressSnapshot()
      lastFailedProgress = snapshot?.progressPercentVal ?? lastFailedProgress
      const warningKey = `progress_error_${unref(videoId)}`
      if (storage && !storage.getItem(warningKey)) {
        storage.setItem(warningKey, '1')
        showWarning('进度上报失败,请检查网络')
      }
      console.warn('[进度上报]', error)
    }
  })

  async function reportProgress(force = false) {
    const snapshot = getCurrentProgressSnapshot()
    if (!snapshot) return

    const { progressPercentVal } = snapshot
    if (!force && Math.abs(progressPercentVal - lastReportedProgress) < 1 && lastFailedProgress === null) return

    await persistProgress({ force })
  }

  return {
    progressId,
    getCurrentProgressSnapshot,
    reportProgress,
    resetProgressReporter
  }
}
