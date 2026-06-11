import request from '../utils/request'
export function getUsers(params) { return request({ method: 'GET', url: '/users', params }) }
export function getUserById(id) { return request({ method: 'GET', url: `/users/${id}` }) }
export function createUser(data) { return request({ method: 'POST', url: '/users', data }) }
export function updateUser(id, data) { return request({ method: 'PUT', url: `/users/${id}`, data }) }
export function updateUserStatus(id, data) { return request({ method: 'PUT', url: `/users/${id}/status`, data }) }