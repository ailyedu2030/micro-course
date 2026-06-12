import request from '../utils/request'

export function getExercises(params) {
  return request({ method: 'GET', url: '/exercises', params })
}

export function createExercise(data) {
  return request({ method: 'POST', url: '/exercises', data })
}

export function updateExercise(id, data) {
  return request({ method: 'PUT', url: `/exercises/${id}`, data })
}

export function deleteExercise(id) {
  return request({ method: 'DELETE', url: `/exercises/${id}` })
}

export function getExerciseById(id) {
  return request({ method: 'GET', url: `/exercises/${id}` })
}

export function addQuestionsToExercise(id, data) {
  return request({ method: 'POST', url: `/exercises/${id}/questions`, data })
}

export function removeQuestionFromExercise(exerciseId, questionId) {
  return request({ method: 'DELETE', url: `/exercises/${exerciseId}/questions/${questionId}` })
}

export function submitExerciseRecord(data) {
  return request({ method: 'POST', url: '/exercise-records/submit', data })
}