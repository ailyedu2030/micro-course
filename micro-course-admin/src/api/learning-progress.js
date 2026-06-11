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