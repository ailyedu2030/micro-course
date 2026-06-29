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

export function getQuestionById(id) {
  return request({ method: 'GET', url: `/questions/${id}` })
}

export function batchImportQuestion(file, courseId) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('courseId', courseId)
  return request({ method: 'POST', url: '/questions/batch/import', data: formData })
}