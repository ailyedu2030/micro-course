import request from '../utils/request'
export function enroll(data) { return request({ method:'POST', url:'/enrollments', data }) }
export function getMyEnrollments(userId) { return request({ method:'GET', url:'/enrollments/my', params:{userId} }) }
export function getCourseEnrollments(courseId) { return request({ method:'GET', url:`/enrollments/course/${courseId}` }) }
export function updateEnrollment(id, data) { return request({ method:'PUT', url:`/enrollments/${id}`, data }) }
export function cancelEnrollment(id) { return request({ method:'DELETE', url:`/enrollments/${id}` }) }