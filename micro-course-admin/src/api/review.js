/**
 * 课程审核 API
 */
import request from '../utils/request'

export function createReview(courseId, data) { return request({ method:'POST', url:'/reviews', data: { courseId, ...data } }) }
export function getReviews(courseId, params = {}) {
  const queryParams = { ...params }
  if (courseId != null) queryParams.courseId = courseId
  return request({ method:'GET', url:'/reviews', params: queryParams })
}
export function getMyReviews(params) { return request({ method:'GET', url:'/reviews/my', params }) }
export function approveReview(id) { return request({ method:'PUT', url:`/reviews/${id}/approve` }) }
export function rejectReview(id) { return request({ method:'PUT', url:`/reviews/${id}/reject` }) }
export function deleteReview(id) { return request({ method:'DELETE', url:`/reviews/${id}` }) }