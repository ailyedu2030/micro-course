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

// createReview, getMyReviews 由 course-review.js 统一管理，此处重新导出
export { createReview, getMyReviews } from './course-review'

// 管理后台：通过评价ID直接操作（MyReviewController 提供 /api/reviews/{id}/... 路径）
export function approveReview(id) { return request({ method:'PUT', url:`/reviews/${id}/approve` }) }
export function rejectReview(id) { return request({ method:'PUT', url:`/reviews/${id}/reject` }) }
export function deleteReview(id) { return request({ method:'DELETE', url:`/reviews/${id}` }) }

// 课程嵌套路径（CourseReviewController 提供 POST /api/courses/{courseId}/reviews/{id}/... 路径）
export function approveCourseReview(courseId, id) { return request({ method:'POST', url:`/courses/${courseId}/reviews/${id}/approve` }) }
export function rejectCourseReview(courseId, id) { return request({ method:'POST', url:`/courses/${courseId}/reviews/${id}/reject` }) }
export function deleteCourseReview(courseId, id) { return request({ method:'DELETE', url:`/courses/${courseId}/reviews/${id}` }) }

/**
 * 举报处理 API
 */

/** 提交举报 */
export function createReport(data) { return request({ method:'POST', url:'/reports', data }) }

/** 管理员查看举报列表 */
export function getAdminReports(params) { return request({ method:'GET', url:'/reports/admin', params }) }

/** 管理员审核举报 */
export function reviewReport(id, data) { return request({ method:'POST', url:`/reports/${id}/review`, data }) }