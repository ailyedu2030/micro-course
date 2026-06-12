/**
 * 成绩管理 API
 * /grades 接口封装
 */
import request from '../utils/request'

/**
 * 提交/更新成绩
 * POST /grades
 * @param {Object} data - { enrollmentId, score, comment }
 */
export function submitGrade(data) {
  return request({ method: 'POST', url: '/grades', data })
}

/**
 * 获取成绩列表
 * GET /grades?enrollmentId=&courseId=&page=&size=
 */
export function getGrades(params) {
  return request({ method: 'GET', url: '/grades', params })
}