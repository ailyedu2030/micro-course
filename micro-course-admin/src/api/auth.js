import request from '../utils/request'
export function login(data) { return request({ method: 'POST', url: '/auth/login', data }) }
export function register(data) { return request({ method: 'POST', url: '/auth/register', data }) }
export function refreshToken(data) { return request({ method: 'POST', url: '/auth/refresh', data }) }
export function logout() { return request({ method: 'POST', url: '/auth/logout' }) }
export function getCurrentUser() { return request({ method: 'GET', url: '/auth/me' }) }
export function updateProfile(data) { return request({ method: 'PUT', url: '/auth/me', data }) }
export function changePassword(data) { return request({ method: 'PUT', url: '/auth/me/password', data }) }
export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({ method: 'POST', url: '/auth/me/avatar', data: formData })
}
export function casLogin(ticket, state) { return request({ method: 'GET', url: `/auth/cas?ticket=${encodeURIComponent(ticket)}&state=${encodeURIComponent(state || '')}` }) }
export function getRegistrationStatus() { return request({ method: 'GET', url: '/auth/registration-status' }) }