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

// 正确率趋势（后端 Agent 1 在实现中，stub 保证前端编译通过）
export function getAccuracyTrend(params) {
  return request({ method: 'GET', url: '/exercise-records/my/accuracy-trend', params })
}

export function getMyAttemptCount(exerciseId) {
  return request({ method: 'GET', url: `/exercise-records/my/${exerciseId}/attempt-count` })
}