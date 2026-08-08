import request from '../utils/request'

export function getLearningProgress(params) {
  return request({ method: 'GET', url: '/learning-progress/progress', params })
}

export function updateLearningProgress(id, data) {
  return request({ method: 'PUT', url: `/learning-progress/progress/${id}`, data })
}

// G3-P0-5: 播放器翻页/音频结束上报本课时播放进度（SKIP_IF_KNOWN 服务端读取）。
// 服务端计算 video_progress = played/total（0-100），仅 STUDENT 可用。
export function reportVideoProgress(courseId, sectionId, playedSeconds, totalSeconds) {
  return request({
    method: 'PUT',
    url: `/learning-progress/${courseId}/sections/${sectionId}/video-progress`,
    data: { playedSeconds, totalSeconds }
  })
}

export function createLearningProgress(data) {
  return request({ method: 'POST', url: '/learning-progress/progress', data })
}

export function getCompletion(params) {
  return request({ method: 'GET', url: '/learning-progress/progress/completion', params })
}

// R8 P0-3: 批量获取学习进度（解决 MyCourses N+1，替代 per-course getLearningProgress）
export function batchGetLearningProgress(courseIds) {
  return request({ method: 'GET', url: '/learning-progress/progress/batch', params: { courseIds: courseIds.join(',') } })
}

export function getStudyDays() {
  return request({ method: 'GET', url: '/learning-progress/study-days' })
}

export function getTotalTime() {
  return request({ method: 'GET', url: '/learning-progress/total-time' })
}

// P1C-031: 获取服务端时间，避免前后端"今天"定义不一致
export function getServerTime() {
  return request({ method: 'GET', url: '/server-time' })
}