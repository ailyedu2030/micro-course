import request from '@/utils/request'

export function getOfflineSessions(chapterId, params) {
  return request({ url: `/offline-sessions/${chapterId}/chapters`, method: 'get', params })
}

export function createOfflineSession(chapterId, data) {
  return request({ url: `/offline-sessions/${chapterId}/chapters`, method: 'post', data })
}

export function updateOfflineSession(id, data) {
  return request({ url: `/offline-sessions/${id}`, method: 'put', data })
}

export function deleteOfflineSession(id) {
  return request({ url: `/offline-sessions/${id}`, method: 'delete' })
}

export function checkin(sessionId) {
  return request({ url: `/offline-sessions/${sessionId}/checkin`, method: 'post' })
}

export function getMyAttendance(chapterId) {
  return request({ url: `/offline-sessions/${chapterId}/my-attendance`, method: 'get' })
}

export function getAttendance(sessionId, params) {
  return request({ url: `/offline-sessions/${sessionId}/attendance`, method: 'get', params })
}

export function updateAttendance(sessionId, recordId, data) {
  return request({ url: `/offline-sessions/${sessionId}/attendance/${recordId}`, method: 'put', data })
}
