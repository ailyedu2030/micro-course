import request from '../utils/request'
export function getNotifications(params) { return request({ method:'GET', url:'/notifications', params }) }
export function markAsRead(id) { return request({ method:'PUT', url:`/notifications/${id}/read` }) }
export function markAllAsRead() { return request({ method:'PUT', url:'/notifications/read-all' }) }
export function getUnreadCount() { return request({ method:'GET', url:'/notifications/unread-count' }) }
export function sendNotification(data) { return request({ method:'POST', url:'/notifications', data }) }
