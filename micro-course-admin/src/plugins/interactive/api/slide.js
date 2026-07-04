import request from '@/utils/request'

export function uploadSlide(courseId, file, onProgress, chapterId) {
  const fd = new FormData()
  fd.append('file', file)
  if (chapterId) fd.append('chapterId', chapterId)
  return request({
    method: 'POST',
    url: `/courses/${courseId}/slides/upload`,
    data: fd,
    timeout: 300000,
    onUploadProgress: onProgress
  })
}

export function getSlides(courseId) {
  return request({ method: 'GET', url: `/courses/${courseId}/slides` })
}

export function getSlidePages(courseId, chapterId) {
  const params = chapterId ? { chapterId } : {}
  return request({ method: 'GET', url: `/courses/${courseId}/slides/pages`, params })
}

export function getSlidePage(courseId, pageNumber) {
  return request({ method: 'GET', url: `/courses/${courseId}/slides/pages/${pageNumber}` })
}

export function generateNarration(courseId, pageNumber) {
  return request({ method: 'POST', url: `/courses/${courseId}/slides/pages/${pageNumber}/narration/generate` })
}

export function updateNarration(courseId, pageNumber, narrationScript) {
  return request({
    method: 'PUT',
    url: `/courses/${courseId}/slides/pages/${pageNumber}/narration`,
    data: { narrationScript }
  })
}

export function generateAllNarrations(courseId) {
  return request({ method: 'POST', url: `/courses/${courseId}/slides/narrations/generate` })
}

export function generateAudio(courseId, pageNumber) {
  return request({ method: 'POST', url: `/courses/${courseId}/slides/pages/${pageNumber}/audio/generate` })
}

export function generateAllAudio(courseId) {
  return request({ method: 'POST', url: `/courses/${courseId}/slides/audio/generate` })
}

export function updateSlidePage(courseId, pageNumber, data) {
  return request({
    method: 'PUT',
    url: `/courses/${courseId}/slides/pages/${pageNumber}`,
    data
  })
}

export function getNarrationSettings(courseId) {
  return request({ method: 'GET', url: `/courses/${courseId}/narration-settings` })
}

export function updateNarrationSettings(courseId, data) {
  return request({ method: 'PUT', url: `/courses/${courseId}/narration-settings`, data })
}

export function deleteSlide(courseId) {
  return request({ method: 'DELETE', url: `/courses/${courseId}/slides` })
}

export function deleteSlidePage(courseId, pageNumber) {
  return request({ method: 'DELETE', url: `/courses/${courseId}/slides/pages/${pageNumber}` })
}

export function reorderSlidePages(courseId, order) {
  return request({ method: 'PUT', url: `/courses/${courseId}/slides/pages/reorder`, data: order })
}

export function downloadOriginalSlide(courseId) {
  return request({ method: 'GET', url: `/courses/${courseId}/slides/download`, responseType: 'blob' })
}
