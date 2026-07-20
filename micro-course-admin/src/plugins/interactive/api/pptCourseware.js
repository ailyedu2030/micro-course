import request from '@/utils/request'

/**
 * PPT 课件 REST API 客户端 (V300-V302 + V306 后端).
 *
 * 后端 controller: PptCoursewareController @ /api/courses/{courseId}/ppt/...
 * 文档: docs/superpowers/specs/2026-07-19-courseware-architecture-design.md §4.3
 *
 * 7-19 P1-C 兼容:
 * <ul>
 *   <li>audio_token 是 UK, 流式 GET 不依赖 pageNumber</li>
 *   <li>saveScript 自动降级旧 active + insert 新 active (partial unique 兼容)</li>
 * </ul>
 */

// === PPT 页面 CRUD ===

export function listPptPages(courseId, sectionId) {
  return request({ method: 'GET', url: `/api/courses/${courseId}/ppt/sections/${sectionId}/pages` })
}

export function createPptPage(courseId, sectionId, dto) {
  return request({ method: 'POST', url: `/api/courses/${courseId}/ppt/sections/${sectionId}/pages`, data: dto })
}

export function getPptPage(courseId, pageId) {
  return request({ method: 'GET', url: `/api/courses/${courseId}/ppt/pages/${pageId}` })
}

export function updatePptPage(courseId, pageId, dto) {
  return request({ method: 'PUT', url: `/api/courses/${courseId}/ppt/pages/${pageId}`, data: dto })
}

export function deletePptPage(courseId, pageId) {
  return request({ method: 'DELETE', url: `/api/courses/${courseId}/ppt/pages/${pageId}` })
}

// === PPT 讲述稿 ===

export function getActivePptScript(courseId, pageId) {
  return request({ method: 'GET', url: `/api/courses/${courseId}/ppt/pages/${pageId}/scripts/active` })
}

export function listPptScriptHistory(courseId, pageId) {
  return request({ method: 'GET', url: `/api/courses/${courseId}/ppt/pages/${pageId}/scripts` })
}

export function savePptScript(courseId, pageId, { scriptText, voice, ttsModel, createdBy }) {
  return request({ method: 'PUT', url: `/api/courses/${courseId}/ppt/pages/${pageId}/scripts`, data: { scriptText, voice, ttsModel, createdBy } })
}

// === PPT 音频 ===

export function listPptAudios(courseId, scriptId) {
  return request({ method: 'GET', url: `/api/courses/${courseId}/ppt/scripts/${scriptId}/audios` })
}

export function generatePptAudio(courseId, scriptId, { voice, model, ttsParams }) {
  return request({ method: 'POST', url: `/api/courses/${courseId}/ppt/scripts/${scriptId}/audios`, data: { voice, model, ttsParams } })
}

export function getPptAudio(courseId, audioId) {
  return request({ method: 'GET', url: `/api/courses/${courseId}/ppt/audios/${audioId}` })
}

// === PPT 页间跳转 (NEXT/BRANCH/SKIP) ===

export function listPptFlows(courseId, sectionId) {
  return request({ method: 'GET', url: `/api/courses/${courseId}/ppt/sections/${sectionId}/flows` })
}

export function createPptFlow(courseId, sectionId, dto) {
  return request({ method: 'POST', url: `/api/courses/${courseId}/ppt/sections/${sectionId}/flows`, data: dto })
}