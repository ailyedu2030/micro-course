/**
 * 课程审核 API
 */
import request from '../utils/request'

/**
 * 管理后台：获取所有评价列表（支持按课程筛选）
 * GET /api/reviews?page=&size=&courseId=
 */
export function getReviews(params) { return request({ method:'GET', url:'/reviews', params }) }

/**
 * 获取指定课程的评价列表
 * GET /api/courses/{courseId}/reviews
 */
export function getCourseReviews(courseId, params) { return request({ method:'GET', url:`/courses/${courseId}/reviews`, params }) }

/**
 * 创建课程评价
 * POST /api/courses/{courseId}/reviews
 */
export function createReview(courseId, data) { return request({ method:'POST', url:`/courses/${courseId}/reviews`, data }) }
export function getMyReviews(params) { return request({ method:'GET', url:'/reviews/my', params }) }
export function approveReview(id) { return request({ method:'PUT', url:`/reviews/${id}/approve` }) }
export function rejectReview(id) { return request({ method:'PUT', url:`/reviews/${id}/reject` }) }
export function deleteReview(id) { return request({ method:'DELETE', url:`/reviews/${id}` }) }