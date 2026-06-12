/**
 * 课程评价 API - Phase 9.1 CourseReview
 * @author Claude Code Agent
 */
import request from '../utils/request'

/**
 * 创建课程评价
 * @param {number} courseId - 课程ID
 * @param {object} data - 评价数据 { rating, content }
 * @returns {Promise} created review
 */
export function createReview(courseId, data) {
  return request({ method: 'POST', url: `/courses/${courseId}/reviews`, data })
}

/**
 * 获取课程评价列表
 * @param {number} courseId - 课程ID
 * @param {object} params - 分页参数 { page, size }
 * @returns {Promise} reviews list
 */
export function getReviews(courseId, params) {
  return request({ method: 'GET', url: `/courses/${courseId}/reviews`, params })
}

/**
 * 获取当前用户的所有评价
 * @param {object} params - 分页参数 { page, size }
 * @returns {Promise} my reviews list
 */
export function getMyReviews(params) {
  return request({ method: 'GET', url: '/reviews/my', params })
}