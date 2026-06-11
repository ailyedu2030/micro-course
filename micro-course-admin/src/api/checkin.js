import request from '../utils/request'

export function getMyCheckIns(params) {
  return request({ method: 'GET', url: '/check-ins/my', params })
}

export function getCheckInStreak() {
  return request({ method: 'GET', url: '/check-ins/streak' })
}

export function createCheckIn(data) {
  return request({ method: 'POST', url: '/check-ins', data })
}