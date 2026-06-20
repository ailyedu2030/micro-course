import request from '@/utils/request'

export function getLessons(params) {
  return request({ method: 'GET', url: '/lessons', params })
}

export function getLessonByChapter(chapterId) {
  return request({ method: 'GET', url: `/lessons/chapter/${chapterId}` })
}

export function getLessonById(id) {
  return request({ method: 'GET', url: `/lessons/${id}` })
}

export function createLesson(data) {
  return request({ method: 'POST', url: '/lessons', data })
}

export function updateLesson(id, data) {
  return request({ method: 'PUT', url: `/lessons/${id}`, data })
}

export function deleteLesson(id) {
  return request({ method: 'DELETE', url: `/lessons/${id}` })
}

export function sortLessons(items) {
  return request({ method: 'PUT', url: '/lessons/sort', data: items })
}
