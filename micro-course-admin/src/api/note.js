/**
 * 学习笔记 API（P1-C 补全 2026-08-04）
 */
import request from '../utils/request'

export function getCourseNotes(params) {
  return request({ method: 'GET', url: '/course-notes', params })
}

export function createCourseNote(data) {
  return request({ method: 'POST', url: '/course-notes', data })
}

export function deleteCourseNote(id) {
  return request({ method: 'DELETE', url: `/course-notes/${id}` })
}
