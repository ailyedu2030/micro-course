import request from '@/utils/request'

export function listSections(courseId, chapterId, params) {
  return request({ method: 'GET', url: `/courses/${courseId}/chapters/${chapterId}/sections`, params })
}

export function getSection(courseId, chapterId, sectionId) {
  return request({ method: 'GET', url: `/courses/${courseId}/chapters/${chapterId}/sections/${sectionId}` })
}

export function createSection(courseId, chapterId, data) {
  return request({ method: 'POST', url: `/courses/${courseId}/chapters/${chapterId}/sections`, data })
}

export function updateSection(courseId, chapterId, sectionId, data) {
  return request({ method: 'PUT', url: `/courses/${courseId}/chapters/${chapterId}/sections/${sectionId}`, data })
}

export function deleteSection(courseId, chapterId, sectionId, force = false) {
  return request({
    method: 'DELETE',
    url: `/courses/${courseId}/chapters/${chapterId}/sections/${sectionId}`,
    params: force ? { force: true } : {}
  })
}
