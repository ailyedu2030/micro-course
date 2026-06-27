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
  return request({ method: 'PUT', url: `/courses/${id}/status`, params: { status } })
}

export function submitCourseForReview(id) {
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

export function updateCourseCover(id, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({ method: 'POST', url: `/courses/${id}/cover`, data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
}
export function getCourseStudents(id) { return request({ method: 'GET', url: `/courses/${id}/students` }) }
export function getCourseStats(id) { return request({ method: 'GET', url: `/courses/${id}/stats` }) }
export function unpublishCourse(id) { return request({ method: 'POST', url: `/courses/${id}/unpublish` }) }
export function getPendingReviewCourses(params) { return request({ method: 'GET', url: '/courses/pending-review', params }) }