import request from '@/utils/request'

/**
 * 课件 CQRS Query API 客户端.
 *
 * 后端 controller: CoursewareQueryController @ /api/courses/{courseId}/courseware/...
 * 单一职责: 一次 GET 拿全课件树 (PPT pages + scripts + audios + flow / HTML unit + segments).
 * 性能预算: p99 < 200ms (15 页 + 15 音频 + flow).
 */

// === 读侧统一入口 ===

/**
 * 取课件完整树 (取代多次拼装).
 * 返回 CoursewareTreeDTO: { type, sectionId, courseId, pages[], flow[], htmlUnit, narrationStatus, audioReadyCount }
 * type: "PPT" | "HTML" | "EMPTY"
 */
export function getCoursewareTree(courseId, sectionId) {
  return request({ method: 'GET', url: `/api/courses/${courseId}/courseware/${sectionId}` })
}

/**
 * 音频流式 GET (7-19 P1-C 修复兼容).
 * 不依赖 pageNumber, 仅用 audio_token (UK 校验).
 * 返回音频字节流 (浏览器直接 <audio src=...>)
 */
export function getAudioStreamUrl(courseId, token) {
  return `/api/courses/${courseId}/courseware/audio/${token}`
}

/**
 * 按 token 解析元数据 (非流式, 用于 UI 显示音频状态).
 */
export function resolveAudioToken(courseId, token) {
  return request({ method: 'GET', url: `/api/courses/${courseId}/courseware/audio/${token}` })
}