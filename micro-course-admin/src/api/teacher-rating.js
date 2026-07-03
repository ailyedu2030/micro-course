import request from '../utils/request'

/**
 * 教师自查评级
 * GET /api/teacher-ratings/my
 */
export function getMyRating() {
  return request({ method: 'GET', url: '/teacher-ratings/my' })
}

/**
 * 管理员：获取所有教师评级列表
 * GET /api/teacher-ratings
 */
export function getAllRatings() {
  return request({ method: 'GET', url: '/teacher-ratings' })
}

/**
 * 管理员：按等级筛选
 * GET /api/teacher-ratings/by-tier?tier=GOLD
 */
export function getRatingsByTier(tier) {
  return request({ method: 'GET', url: '/teacher-ratings/by-tier', params: { tier } })
}

/**
 * 管理员：手动调整教师等级
 * PUT /api/teacher-ratings/{teacherId}/tier
 */
export function adjustTeacherTier(teacherId, newTier, reason) {
  return request({ method: 'PUT', url: `/teacher-ratings/${teacherId}/tier`, params: { newTier, reason } })
}

/**
 * 管理员：手动重新计算教师评级
 * POST /api/teacher-ratings/{teacherId}/recalculate
 */
export function recalculateTeacherRating(teacherId) {
  return request({ method: 'POST', url: `/teacher-ratings/${teacherId}/recalculate` })
}

/**
 * P1-I 修复: 批量重算所有教师评级
 * POST /api/teacher-ratings/recalculate-all
 */
export function recalculateAllTeacherRatings() {
  return request({ method: 'POST', url: '/teacher-ratings/recalculate-all' })
}

/**
 * 教师查看自己的等级变更历史
 * GET /api/teacher-ratings/my/history
 */
export function getMyTierHistory() {
  return request({ method: 'GET', url: '/teacher-ratings/my/history' })
}
