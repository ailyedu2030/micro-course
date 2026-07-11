import request from '@/utils/request'

/**
 * 上传课件（统一接口，后端按扩展名自动分支）
 * - .pptx → 走 SlideRenderService 异步渲染 PNG（existing）
 * - .html/.htm → 走 HtmlSanitizer 消毒后入库（新增）
 *
 * 修复 Hermes P0#3 #4 #5：原 uploadHtml() 路由到错的端点 +
 * SlideManage.vue 硬编码 .pptx 过滤导致 HTML 上传链路完全断裂。
 * 现统一走 /upload 多部分上传，contentType 参数保留向后兼容。
 */
export function uploadSlide(courseId, file, onProgress, chapterId, contentType = 'auto') {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('contentType', contentType)
  if (chapterId) fd.append('chapterId', chapterId)
  const isHtml = file.name && /\.(html?|HTML?)$/.test(file.name)
  return request({
    method: 'POST',
    url: `/courses/${courseId}/slides/upload`,
    data: fd,
    timeout: isHtml ? 60000 : 300000,
    onUploadProgress: onProgress
  })
}

// 兼容层：保留 uploadHtml 作为 uploadSlide 的别名
// 注意：移除 Hermes 的错误独立端点（/upload-html 用 @RequestBody String，前后端不匹配）
export const uploadHtml = uploadSlide

export function getSlides(courseId, chapterId) {
  const params = chapterId ? { chapterId } : {}
  return request({ method: 'GET', url: `/courses/${courseId}/slides`, params })
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
