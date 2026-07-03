/**
 * 管理端统计 API
 * /admin/stats/* 接口封装
 */
import request from '../utils/request'

/**
 * 获取管理端概览数据
 * GET /admin/stats/overview
 * 响应: { userTotal, activeUsers7d, courseTotal, enrollmentTotal }
 */
export function getOverview() {
  return request({ method: 'GET', url: '/admin/stats/overview' })
}

/**
 * 获取用户增长趋势
 * GET /admin/stats/users?days=30
 * 响应: [{ date, count }]
 */
export function getUserTrend(days = 30) {
  return request({ method: 'GET', url: '/admin/stats/users', params: { days } })
}

/**
 * 获取课程增长趋势
 * GET /admin/stats/courses?days=30
 * 响应: [{ date, count }]
 */
export function getCourseTrend(days = 30) {
  return request({ method: 'GET', url: '/admin/stats/courses', params: { days } })
}

/**
 * 获取课程状态分布
 * GET /admin/stats/course-distribution
 * 响应: { draft, underReview, published, offline }
 */
export function getCourseDistribution() {
  return request({ method: 'GET', url: '/admin/stats/course-distribution' })
}

/**
 * 获取学习行为数据
 * GET /admin/stats/learning-behavior
 * 响应: [{ type, count }]
 */
export function getLearningBehavior() {
  return request({ method: 'GET', url: '/admin/stats/learning-behavior' })
}

/**
 * 获取每日活跃用户数
 * GET /admin/stats/daily-activity?days=30
 * 响应: [{ date, activeUsers }]
 */
export function getDailyActivity(days = 30) {
  return request({ method: 'GET', url: '/admin/stats/daily-activity', params: { days } })
}

/**
 * 获取系统健康状态
 * GET /admin/stats/health
 * 响应: { db, redis, disk, memory }
 */
export function getHealth() {
  return request({ method: 'GET', url: '/admin/stats/health' })
}

/**
 * 获取平台营收数据
 * GET /admin/stats/revenue
 */
export function getRevenueStats() {
  return request({ method: 'GET', url: '/admin/stats/revenue' })
}