/**
 * 教务处统计 API
 * /academic/stats/* 接口封装
 */
import request from '../utils/request'

/**
 * 获取教务处概览数据
 * GET /academic/stats/overview
 * 响应: { totalCourses, totalEnrollments, avgCompletionRate, avgAccuracyRate }
 */
export function getAcademicOverview() {
  return request({ method: 'GET', url: '/academic/stats/overview' })
}

/**
 * 获取院系列表统计
 * GET /academic/stats/departments
 * 响应: [{ id, name, avgCompletionRate, avgAccuracyRate, totalEnrollments }]
 */
export function getDepartmentStats() {
  return request({ method: 'GET', url: '/academic/stats/departments' })
}

/**
 * 获取院系详情
 * GET /academic/stats/department/:id
 * 响应: { id, name, totalCourses, totalEnrollments, avgCompletionRate, avgAccuracyRate }
 */
export function getDepartmentDetail(id) {
  return request({ method: 'GET', url: `/academic/stats/department/${id}` })
}

/**
 * 获取完成率预警列表
 * GET /academic/stats/warnings
 * 响应: { items: [{ id, name, completionRate, enrollmentCount }], totalElements }
 */
export function getCompletionWarnings() {
  return request({ method: 'GET', url: '/academic/stats/warnings' })
}

/**
 * 获取参与率趋势
 * GET /academic/stats/participation-trend?days=30
 * 响应: [{ date, participationRate }]
 */
export function getParticipationTrend(params) {
  return request({ method: 'GET', url: '/academic/stats/participation-trend', params })
}

/**
 * 获取完成率趋势
 * GET /academic/stats/completion-trend?days=30
 * 响应: [{ date, completionRate }]
 */
export function getCompletionTrend(params) {
  return request({ method: 'GET', url: '/academic/stats/completion-trend', params })
}