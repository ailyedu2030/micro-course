import request from '../utils/request'

export function getSettings() {
  return request({ method: 'GET', url: '/admin/settings' })
}

export function updateSettings(data) {
  return request({ method: 'PUT', url: '/admin/settings', data })
}