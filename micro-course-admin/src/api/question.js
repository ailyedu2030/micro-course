import request from '../utils/request'

export function getQuestions(params) {
  return request({ method: 'GET', url: '/questions', params })
}

export function createQuestion(data) {
  return request({ method: 'POST', url: '/questions', data })
}

export function updateQuestion(id, data) {
  return request({ method: 'PUT', url: `/questions/${id}`, data })
}

export function deleteQuestion(id) {
  return request({ method: 'DELETE', url: `/questions/${id}` })
}