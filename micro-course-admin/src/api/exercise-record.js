import request from '../utils/request'

export function getRecordsByExercise(exerciseId) {
  return request({ method: 'GET', url: `/exercise-records/exercise/${exerciseId}` })
}

export function getMyRecords(exerciseId) {
  return request({ method: 'GET', url: `/exercise-records/my/${exerciseId}` })
}

export function getRecordById(id) {
  return request({ method: 'GET', url: `/exercise-records/${id}` })
}