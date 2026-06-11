import request from '../utils/request'
export function getUsers(params) { return request({ method: 'GET', url: '/users', params }) }
export function getUserById(id) { return request({ method: 'GET', url: `/users/${id}` }) }
export function createUser(data) { return request({ method: 'POST', url: '/users', data }) }
export function updateUser(id, data) { return request({ method: 'PUT', url: `/users/${id}`, data }) }
export function updateUserStatus(id, data) { return request({ method: 'PUT', url: `/users/${id}/status`, data }) }

/**
 * 批量导入用户
 * POST /users/batch
 * @param {FormData} formData - xlsx file
 */
export function batchImportUsers(formData) {
  return request({ method: 'POST', url: '/users/batch', data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
}