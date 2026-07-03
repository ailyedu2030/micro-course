import request from '@/utils/request'

export function getOfflineSessions(chapterId, params) {
  return request({ url: `/api/chapters/${chapterId}/offline-sessions`, method: 'get', params })
}

export function createOfflineSession(chapterId, data) {
  return request({ url: `/api/chapters/${chapterId}/offline-sessions`, method: 'post', data })
}

export function updateOfflineSession(id, data) {
  return request({ url: `/api/offline-sessions/${id}`, method: 'put', data })
}

export function deleteOfflineSession(id) {
  return request({ url: `/api/offline-sessions/${id}`, method: 'delete' })
}

export function checkin(sessionId) {
  return request({ url: `/api/offline-sessions/${sessionId}/checkin`, method: 'post' })
}

export function getMyAttendance(chapterId) {
  return request({ url: `/api/chapters/${chapterId}/my-attendance`, method: 'get' })
}

export function getAttendance(sessionId, params) {
  return request({ url: `/api/offline-sessions/${sessionId}/attendance`, method: 'get', params })
}

export function updateAttendance(sessionId, recordId, data) {
  return request({ url: `/api/offline-sessions/${sessionId}/attendance/${recordId}`, method: 'put', data })
}
