/**
 * 管理端 API
 * /admin/* 接口封装
 */
import request from '../utils/request'

/**
 * 获取管理端概览统计
 * GET /admin/stats
 * 响应: { totalUsers, totalCourses, totalStudents, activeUsers,
 *         pendingCourses, pendingReviews, totalStudyMinutes, certificatesIssued }
 */
export function getStats() {
  return request({ method: 'GET', url: '/admin/stats' })
}

/**
 * 获取核心指标趋势（用户/课程/学员 3 条线）
 * GET /admin/stats/trends?days=30
 * 响应: { users: [{date,count}], courses: [{date,count}], students: [{date,count}] }
 */
export function getTrends(days = 30) {
  return request({ method: 'GET', url: '/admin/stats/trends', params: { days } })
}

/**
 * 获取课程分类分布
 * GET /admin/stats/categories
 * 响应: [{ name, value }]
 */
export function getCategoryStats() {
  return request({ method: 'GET', url: '/admin/stats/categories' })
}

/**
 * 获取最近活跃数据
 * GET /admin/stats/activity?days=30
 * 响应: [{ date, activeUsers }]
 */
export function getActivity(days = 30) {
  return request({ method: 'GET', url: '/admin/stats/activity', params: { days } })
}

/**
 * 获取最新操作日志
 * GET /admin/operation-logs?size=5
 * 响应: { items: [{ id, operator, action, target, createdAt }] }
 */
export function getLogs(params) {
  return request({ method: 'GET', url: '/admin/operation-logs', params })
}

/**
 * 获取系统健康状态（如有 API）
 * GET /admin/health
 * 响应: { db, redis, disk, memory }
 */
export function getHealth() {
  return request({ method: 'GET', url: '/admin/health' })
}