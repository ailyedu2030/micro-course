import request from '../utils/request'

/**
 * 公开 Banner API - 学生端首页轮播图
 * P0 闭环修复 Round 4: 后端 BannerPublicController @ /api/banners
 */
export function getActiveBanners() {
  return request({ method: 'GET', url: '/banners' })
}
