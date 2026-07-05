import request from '../utils/request'

export function getLearningProgress(params) {
  return request({ method: 'GET', url: '/learning-progress/progress', params })
}

export function updateLearningProgress(id, data) {
  return request({ method: 'PUT', url: `/learning-progress/progress/${id}`, data })
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