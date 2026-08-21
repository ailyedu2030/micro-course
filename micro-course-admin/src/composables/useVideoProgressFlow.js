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
    createPayload: (ctx) => {
      const snapshot = getCurrentProgressSnapshot()
      const payload = {
        userId: unref(userId),
        courseId: unref(courseId),
        chapterId: unref(chapterId),
        // P1-2026-08-21: 以 videoId 作为 sectionId 键控——多视频章节各视频独立进度，
        // 与学习页 LearningView 按 sectionId(=视频id) 精确读写口径一致
        sectionId: unref(videoId) != null ? Number(unref(videoId)) : undefined,
        videoPosition: Math.floor(snapshot?.current || 0),
        videoProgress: Math.round(snapshot?.progressPercentVal || 0)
      }
      if (ctx?.completed) payload.completed = true
      return payload
    },
    updatePayload: (ctx) => {
      const snapshot = getCurrentProgressSnapshot()
      const payload = {
        videoPosition: Math.floor(snapshot?.current || 0),
        videoProgress: Math.round(snapshot?.progressPercentVal || 0)
      }
      if (ctx?.completed) payload.completed = true
      return payload
    },
    createProgress: createLearningProgress,
    updateProgress: updateLearningProgress,
    findExistingProgress: async () => {
      // P1-2026-08-21: 按 sectionId(=videoId) 精确匹配(多视频章节不串档)，无则按 chapterId 兜底
      const res = await getLearningProgress({ courseId: unref(courseId) })
      const rawData = res.data || []
      const vid = unref(videoId)
      if (Array.isArray(rawData)) {
        if (vid != null) {
          const bySection = rawData.find(p => p.sectionId != null && Number(p.sectionId) === Number(vid))
          if (bySection) return bySection
        }
        return rawData.find(p => p.chapterId != null && Number(p.chapterId) === Number(unref(chapterId)))
      }
      if (rawData && typeof rawData === 'object' && rawData.id) {
        if (vid != null && rawData.sectionId != null && Number(rawData.sectionId) === Number(vid)) return rawData
        if (Number(rawData.chapterId) === Number(unref(chapterId))) return rawData
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

  async function reportProgress(force = false, completed = false) {
    const snapshot = getCurrentProgressSnapshot()
    if (!snapshot) return

    const { progressPercentVal } = snapshot
    if (!force && Math.abs(progressPercentVal - lastReportedProgress) < 1 && lastFailedProgress === null) return

    // P1-2026-08-21: 视频看完(@ended)时 force+completed 落库，此前终态不持久化致章节完成/证书判定失效
    await persistProgress({ force, completed })
  }

  return {
    progressId,
    getCurrentProgressSnapshot,
    reportProgress,
    resetProgressReporter
  }
}
