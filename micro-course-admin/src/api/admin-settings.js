import request from '../utils/request'

/**
 * 获取所有系统配置
 * GET /api/admin/settings
 */
export function getSettings() {
  return request({ method: 'GET', url: '/admin/settings' })
}

/**
 * 批量更新系统配置
 * PUT /api/admin/settings
 * @param {Object} settings - key-value map
 */
export function updateSettings(settings) {
  return request({ method: 'PUT', url: '/admin/settings', data: settings })
}

/**
 * 切换注册开关
 * PUT /api/admin/settings/register
 * @param {boolean} enabled
 */
export function toggleRegister(enabled) {
  return request({ method: 'PUT', url: '/admin/settings/register', data: { enabled } })
}

/**
 * 设置上传限制
 * PUT /api/admin/settings/upload
 * @param {Object} data - { maxVideoSizeMB, maxFileSizeMB }
 */
export function updateUploadLimit(data) {
  return request({ method: 'PUT', url: '/admin/settings/upload', data })
}

/**
 * 保存 CAS 配置
 * PUT /api/admin/settings/cas
 * @param {Object} data - CAS config fields
 */
export function updateCasConfig(data) {
  return request({ method: 'PUT', url: '/admin/settings/cas', data })
}