import request from '../utils/request'
export function getNotifications(params) { return request({ method:'GET', url:'/notifications', params }) }
export function markAsRead(id) { return request({ method:'PUT', url:`/notifications/${id}/read` }) }
export function markAllAsRead() { return request({ method:'PUT', url:'/notifications/read-all' }) }
export function getUnreadCount() { return request({ method:'GET', url:'/notifications/unread-count' }) }
export function getPreferences() { return request({ method:'GET', url:'/notification-preferences/my' }) }
export function updatePreferences(data) { return request({ method:'PUT', url:'/notification-preferences/my', data }) }