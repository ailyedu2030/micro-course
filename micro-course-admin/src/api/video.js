import request from '../utils/request'

// ==================== 视频 CRUD ====================
export function getVideos(params) { return request({ method: 'GET', url: '/videos', params }) }
export function getVideoById(id) { return request({ method: 'GET', url: `/videos/${id}` }) }
export function createVideo(data) { return request({ method: 'POST', url: '/videos', data }) }
export function updateVideo(id, data) { return request({ method: 'PUT', url: `/videos/${id}`, data }) }
export function deleteVideo(id) { return request({ method: 'DELETE', url: `/videos/${id}` }) }

// ==================== 视频上传 ====================
export function uploadVideo(formData) {
  return request({
    method: 'POST',
    url: '/videos/upload',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000 // 10 分钟超时（大文件上传）
  })
}

// ==================== 封面上传 ====================
export function uploadVideoCover(id, file) {
  const fd = new FormData()
  fd.append('file', file)
  return request({
    method: 'POST',
    url: `/videos/${id}/cover`,
    data: fd,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ==================== 播放签名 ====================
export function getVideoSign(id) {
  return request({ method: 'GET', url: `/videos/${id}/sign` })
}

export function getVideoPlayUrl(id, sign) {
  return request({ method: 'GET', url: `/videos/${id}/play`, params: { sign } })
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
