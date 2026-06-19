/**
 * 教师端 API
 * /teacher/* 接口封装
 */
import request from '../utils/request'

/**
 * 获取教师工作台统计数据
 * GET /teacher/stats
 * 响应: { courseCount, studentCount, pendingHomework, pendingQuestions }
 */
export function getStats() {
  return request({ method: 'GET', url: '/teacher/stats' })
}

/**
 * 获取最近 7 天学情数据（双 Y 轴：学习时长 + 完成率）
 * GET /teacher/student-activity?days=7
 * 响应: [{ date, studyMinutes, completionRate }]
 */
export function getStudentActivity(days = 7) {
  return request({ method: 'GET', url: '/teacher/student-activity', params: { days } })
}

/**
 * 获取待办任务列表
 * GET /teacher/pending-tasks?size=5
 * 响应: [{ id, type, title, createdAt }]
 */
export function getPendingTasks(size = 5) {
  return request({ method: 'GET', url: '/teacher/pending-tasks', params: { size } })
}

/**
 * 获取最新通知列表
 * GET /teacher/notifications?size=5
 * 响应: [{ id, title, content, createdAt }]
 */
export function getNotifications(size = 5) {
  return request({ method: 'GET', url: '/teacher/notifications', params: { size } })
}

/**
 * 获取我教的课程列表
 * GET /teacher/courses
 * 响应: { items: [{ id, title, cover, studentCount, rating, status }] }
 */
export function getMyCourses() {
  return request({ method: 'GET', url: '/teacher/courses', params: { page: 0, size: 999 } })
}