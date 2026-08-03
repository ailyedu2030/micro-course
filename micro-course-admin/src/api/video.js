import request from '../utils/request'

// ==================== 视频 CRUD ====================
export function getVideos(params) { return request({ method: 'GET', url: '/videos', params }) }
export function getVideoById(id) { return request({ method: 'GET', url: `/videos/${id}` }) }
export function createVideo(data) { return request({ method: 'POST', url: '/videos', data }) }
export function updateVideo(id, data) { return request({ method: 'PUT', url: `/videos/${id}`, data }) }
export function deleteVideo(id) { return request({ method: 'DELETE', url: `/videos/${id}` }) }

// ==================== 视频上传 ====================
export function uploadVideo(formData, onUploadProgress) {
  // P1-C 修复: 不要显式设置 Content-Type — 浏览器自动加 boundary
  // _timeout 被 request.js 的 pickTimeout 识别
  return request({
    method: 'POST',
    url: '/videos/upload',
    data: formData,
    _timeout: 600000, // 10 分钟（覆盖全局 FormData 默认 300s）
    onUploadProgress
  })
}

// ==================== 封面上传 ====================
export function uploadVideoCover(id, file) {
  const fd = new FormData()
  fd.append('file', file)
  return request({
    method: 'POST',
    url: `/videos/${id}/cover`,
    data: fd
  })
}

// ==================== 播放签名 ====================
export function getVideoSign(id) {
  return request({ method: 'GET', url: `/videos/${id}/sign` })
}

export function getVideoPlayUrl(id, sign) {
  return request({ method: 'GET', url: `/videos/${id}/play`, params: { sign } })
}

// P1-C 修复(2026-08-03): HLS 播放签名双通道。
// 流端点强制校验签名（P1I-014），而 hls.js 加载 m3u8 时相对分片 URL
// （如 index0.ts）无法继承 manifest 的 query → 仅给 manifest 加 ?sign= 不够，
// 分片请求仍需 X-Video-Sign 请求头（见 useVideoSourceLifecycle / CourseDetail）。
// 本函数给 manifest URL 附加 sign query，覆盖原生 <video src>（Safari HLS）场景；
// 已含 sign 参数或非 m3u8 时原样返回。
export async function buildSignedHlsUrl(id, url) {
  if (!url || !/\.m3u8/i.test(url) || /\bsign=/.test(url)) return url
  try {
    const res = await getVideoSign(id)
    const sign = res?.data
    if (!sign) return url
    const sep = url.includes('?') ? '&' : '?'
    return `${url}${sep}sign=${encodeURIComponent(sign)}`
  } catch {
    // 签名获取失败：保持原样，交由播放器错误态提示，不吞掉可观测性
    return url
  }
}

// ==================== P0-5: 视频书签 ====================
export function getVideoBookmarks(videoId) {
  return request({ method: 'GET', url: `/videos/${videoId}/bookmarks` })
}

export function createVideoBookmark(videoId, data) {
  return request({ method: 'POST', url: `/videos/${videoId}/bookmarks`, data })
}

export function deleteVideoBookmark(videoId, bookmarkId) {
  return request({ method: 'DELETE', url: `/videos/${videoId}/bookmarks/${bookmarkId}` })
}

export function retryVideoTranscode(videoId) {
  return request({ method: 'POST', url: `/videos/${videoId}/retry` })
}

export function getVideoStatus(videoId) {
  return request({ method: 'GET', url: `/videos/${videoId}/status` })
}

export function getVideoStatusBatch(ids) {
  return request({ method: 'GET', url: '/videos/status/batch', params: { ids: ids.join(',') } })
}
