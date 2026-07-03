import request from '../utils/request'

/**
 * 获取所有平台分账配置
 * GET /api/admin/platform-share-config
 */
export function getPlatformShareConfigList() {
  return request({ method: 'GET', url: '/admin/platform-share-config' })
}

/**
 * 按 key 获取单个配置
 * GET /api/admin/platform-share-config/{key}
 */
export function getPlatformShareConfig(key) {
  return request({ method: 'GET', url: `/admin/platform-share-config/${key}` })
}

/**
 * 创建或更新平台分账配置
 * PUT /api/admin/platform-share-config/{key}
 * @param {string} key - config key
 * @param {Object} data - { configValue, description, active }
 */
export function upsertPlatformShareConfig(key, data) {
  return request({ method: 'PUT', url: `/admin/platform-share-config/${key}`, data })
}
