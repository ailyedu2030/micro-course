import request from '../utils/request'
export function getUsers(params) { return request({ method: 'GET', url: '/users', params }) }
export function getUserById(id) { return request({ method: 'GET', url: `/users/${id}` }) }
/** P0-3: 教师公开信息（仅 name+avatar+bio），任何登录用户可用 */
export function getPublicProfile(id) { return request({ method: 'GET', url: `/users/${id}/public-profile` }) }
export function createUser(data) { return request({ method: 'POST', url: '/users', data }) }
export function updateUser(id, data) { return request({ method: 'PUT', url: `/users/${id}`, data }) }
export function updateUserStatus(id, data) { return request({ method: 'PUT', url: `/users/${id}/status`, data }) }
export function updateTeacherStatus(id, data) { return request({ method: 'PUT', url: `/users/${id}/teacher-status`, data }) }

/**
 * 批量导入用户
 * POST /users/batch
 * @param {FormData} formData - xlsx file
 */
export function batchImportUsers(formData) {
  return request({ method: 'POST', url: '/users/batch', data: formData })
}

/**
 * 上传用户头像
 * POST /users/{userId}/avatar
 * @param {number} userId
 * @param {File} file
 */
export function uploadAvatar(userId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    method: 'POST',
    url: `/users/${userId}/avatar`,
    data: formData
  })
}

// ====== API Key 管理（每教师独立密钥，供 Hermes / 第三方系统调用 webhook） ======

/**
 * 查看当前用户的 API Key（脱敏）
 * GET /users/me/api-key
 */
export function getMyApiKey() {
  return request({ method: 'GET', url: '/users/me/api-key' })
}

/**
 * 生成 / 重新生成当前用户的 API Key（返回明文，仅此一次）
 * POST /users/me/api-key
 */
export function generateMyApiKey() {
  return request({ method: 'POST', url: '/users/me/api-key' })
}

/**
 * 撤销当前用户的 API Key
 * DELETE /users/me/api-key
 */
export function revokeMyApiKey() {
  return request({ method: 'DELETE', url: '/users/me/api-key' })
}