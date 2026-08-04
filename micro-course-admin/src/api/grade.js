/**
 * 成绩管理 API
 * /grades 接口封装
 */
import request from '../utils/request'

/**
 * 教师批改/提交成绩
 * POST /grades/teacher-grade
 * @param {Object} data - { enrollmentId, score, comment }
 */
export function submitGrade(data) {
  return request({ method: 'POST', url: '/grades/teacher-grade', data })
}

// P1-C 修复 (2026-08-04): 主观题人工批改（逐题给分+评语）
export function manualGrade(recordId, data) {
  return request({ method: 'POST', url: `/grades/${recordId}/manual-grade`, data })
}

/**
 * 获取成绩列表
 * GET /grades?enrollmentId=&courseId=&page=&size=
 */
export function getGrades(params) {
  return request({ method: 'GET', url: '/grades', params })
}
