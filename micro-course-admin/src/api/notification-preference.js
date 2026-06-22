import request from '../utils/request'

export function getMyPreferences() {
  return request({ method: 'GET', url: '/notification-preferences/my' })
}

export function updateMyPreferences(data) {
  return request({ method: 'PUT', url: '/notification-preferences/my', data })
}
