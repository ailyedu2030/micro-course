import request from '@/utils/request'

/**
 * HTML 课件 REST API 客户端 (V303-V305 后端).
 *
 * 后端 controller: HtmlCoursewareController @ /api/courses/{courseId}/html/...
 * 7-19 P0 防御: 后端强制 HtmlSanitizer, 即便前端漏调也安全
 */

// === HTML Unit CRUD ===

export function getHtmlUnitBySection(courseId, sectionId) {
  return request({ method: 'GET', url: `/api/courses/${courseId}/html/sections/${sectionId}/unit` })
}

export function createHtmlUnit(courseId, sectionId, dto) {
  return request({ method: 'POST', url: `/api/courses/${courseId}/html/sections/${sectionId}/unit`, data: dto })
}

export function getHtmlUnit(courseId, unitId) {
  return request({ method: 'GET', url: `/api/courses/${courseId}/html/units/${unitId}` })
}

export function updateHtmlUnit(courseId, unitId, dto) {
  return request({ method: 'PUT', url: `/api/courses/${courseId}/html/units/${unitId}`, data: dto })
}

export function deleteHtmlUnit(courseId, unitId) {
  return request({ method: 'DELETE', url: `/api/courses/${courseId}/html/units/${unitId}` })
}

// === HTML 分段脚本 ===

export function listActiveHtmlSegments(courseId, unitId) {
  return request({ method: 'GET', url: `/api/courses/${courseId}/html/units/${unitId}/segments` })
}

export function getActiveHtmlSegment(courseId, unitId, idx) {
  return request({ method: 'GET', url: `/api/courses/${courseId}/html/units/${unitId}/segments/${idx}` })
}

export function saveHtmlSegmentScript(courseId, unitId, idx, { scriptText, voice, ttsModel, segmentMarker, createdBy }) {
  return request({ method: 'PUT', url: `/api/courses/${courseId}/html/units/${unitId}/segments/${idx}`, data: { scriptText, voice, ttsModel, segmentMarker, createdBy } })
}

// === HTML 分段音频 ===

export function listHtmlSegmentAudios(courseId, segmentScriptId) {
  return request({ method: 'GET', url: `/api/courses/${courseId}/html/segments/${segmentScriptId}/audios` })
}

export function generateHtmlSegmentAudio(courseId, segmentScriptId, { voice, model, ttsParams }) {
  return request({ method: 'POST', url: `/api/courses/${courseId}/html/segments/${segmentScriptId}/audios`, data: { voice, model, ttsParams } })
}