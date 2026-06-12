import request from '../utils/request'
export function enroll(data) { return request({ method:'POST', url:'/enrollments', data }) }
export function getMyEnrollments(params) { return request({ method:'GET', url:'/enrollments/my', params: typeof params === 'object' ? params : {} }) }
export function getCourseEnrollments(courseId) { return request({ method:'GET', url:`/enrollments/course/${courseId}` }) }

export function getEnrollments(params) { return request({ method:'GET', url:'/enrollments', params }) }
export function getCourseRanking(courseId, params) { return request({ method:'GET', url:`/enrollments/course/${courseId}/ranking`, params }) }
export function updateEnrollment(id, data) { return request({ method:'PUT', url:`/enrollments/${id}`, data }) }
export function cancelEnrollment(id) { return request({ method:'DELETE', url:`/enrollments/${id}` }) }