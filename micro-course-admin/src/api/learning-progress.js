import request from '../utils/request'

export function getLearningProgress(params) {
  return request({ method: 'GET', url: '/learning-progress/progress', params })
}

export function updateLearningProgress(id, data) {
  return request({ method: 'PUT', url: `/learning-progress/progress/${id}`, data })
}