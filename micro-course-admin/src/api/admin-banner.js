/**
 * 管理端 Banner API
 * /admin/banners 接口封装
 */
import request from '../utils/request'

/**
 * 获取 Banner 列表
 * GET /api/admin/banners
 */
export function getBanners() {
  return request({ method: 'GET', url: '/admin/banners' })
}

/**
 * 创建 Banner
 * POST /api/admin/banners
 * @param {FormData} formData - image file + link + sort order
 */
export function createBanner(formData) {
  return request({
    method: 'POST',
    url: '/admin/banners',
    data: formData
  })
}

/**
 * 更新 Banner
 * PUT /api/admin/banners/{id}
 * @param {number} id
 * @param {FormData} formData
 */
export function updateBanner(id, formData) {
  return request({
    method: 'PUT',
    url: `/admin/banners/${id}`,
    data: formData
  })
}

/**
 * 删除 Banner
 * DELETE /api/admin/banners/{id}
 * @param {number} id
 */
export function deleteBanner(id) {
  return request({ method: 'DELETE', url: `/admin/banners/${id}` })
}

/**
 * 切换 Banner 状态（启用/禁用）
 * PUT /api/admin/banners/{id}/status
 * @param {number} id
 * @param {boolean} enabled
 */
export function toggleBannerStatus(id, enabled) {
  return request({
    method: 'PUT',
    url: `/admin/banners/${id}/status`,
    data: { enabled }
  })
}