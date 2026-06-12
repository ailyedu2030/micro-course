import request from '../utils/request'

export function getCourses(params) {
  return request({ method: 'GET', url: '/courses', params })
}

export function getCourseById(id) {
  return request({ method: 'GET', url: `/courses/${id}` })
}

export function createCourse(data) {
  return request({ method: 'POST', url: '/courses', data })
}

export function updateCourse(id, data) {
  return request({ method: 'PUT', url: `/courses/${id}`, data })
}

export function updateCourseStatus(id, status) {
  return request({ method: 'PUT', url: `/courses/${id}/status`, data: { status } })
}

export function submitCourseForReview(id) {
  return request({ method: 'POST', url: `/courses/${id}/submit` })
}

export function submitForReview(id) {
  return request({ method: 'POST', url: `/courses/${id}/submit` })
}

export function approveCourse(id) {
  return request({ method: 'POST', url: `/courses/${id}/approve` })
}

export function rejectCourse(id, reason) {
  return request({ method: 'POST', url: `/courses/${id}/reject`, data: { reason } })
}

export function publishCourse(id) {
  return request({ method: 'POST', url: `/courses/${id}/publish` })
}

export function deleteCourse(id) {
  return request({ method: 'DELETE', url: `/courses/${id}` })
}

export function copyCourse(id) {
  return request({ method: 'POST', url: `/courses/${id}/copy` })
}