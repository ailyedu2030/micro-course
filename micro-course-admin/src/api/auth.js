import request from '../utils/request'
export function login(data) { return request({ method: 'POST', url: '/auth/login', data }) }
export function refreshToken(data) { return request({ method: 'POST', url: '/auth/refresh', data }) }
export function logout() { return request({ method: 'POST', url: '/auth/logout' }) }
export function getCurrentUser() { return request({ method: 'GET', url: '/auth/me' }) }