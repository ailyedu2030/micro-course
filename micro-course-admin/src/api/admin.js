/**
 * 管理端 API
 * /admin/* 接口封装
 */
import request from '../utils/request'

/**
 * 获取管理端概览统计
 * GET /admin/stats/overview
 * 响应: { totalUsers, totalCourses, totalStudents, activeUsers,
 *         pendingCourses, pendingReviews, totalStudyMinutes, certificatesIssued }
 */
export function getStats() {
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
 * GET /admin/stats/learning-behavior?days=30
 * 响应: { videoPlayCount, exerciseSubmitCount }
 */
export function getLearningBehavior(days = 30) {
  return request({ method: 'GET', url: '/admin/stats/learning-behavior', params: { days } })
}

/**
 * 获取最新操作日志
 * GET /operation-logs?size=5
 * 响应: { items: [{ id, operator, action, target, createdAt }] }
 */
export function getLogs(params) {
  return request({ method: 'GET', url: '/operation-logs', params })
}