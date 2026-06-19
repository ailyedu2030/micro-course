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
  // P0-2: 后端用 @RequestBody，前端必须发 data 而非 params
  return request({ method: 'PUT', url: '/admin/settings/register', data: { enabled } })
}

/**
 * 设置上传限制
 * PUT /api/admin/settings/upload
 * @param {Object} data - { maxVideoSizeMb }
 */
export function updateUploadLimit(data) {
  // P0-3: 后端用 @RequestBody，前端必须发 data 而非 params
  // P2: 统一使用 maxVideoSizeMb（小写 b）
  return request({ method: 'PUT', url: '/admin/settings/upload', data: { maxVideoSizeMb: data.maxVideoSizeMb } })
}

/**
 * 保存 CAS 配置
 * PUT /api/admin/settings/cas
 * @param {Object} data - { casServerUrl, casServiceUrl }
 */
export function updateCasConfig(data) {
  // P0-4: 后端用 @RequestBody，前端必须发 data 而非 params
  return request({ method: 'PUT', url: '/admin/settings/cas', data: { casServerUrl: data.casServerUrl, casServiceUrl: data.casServiceUrl } })
}