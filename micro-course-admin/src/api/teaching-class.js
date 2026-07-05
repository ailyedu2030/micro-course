import request from '../utils/request'

// 分页
export function getTeachingClasses(params) {
  return request({ method: 'GET', url: '/teaching-classes', params })
}

// 详情
export function getTeachingClassById(id) {
  return request({ method: 'GET', url: `/teaching-classes/${id}` })
}

// 创建
export function createTeachingClass(data) {
  return request({ method: 'POST', url: '/teaching-classes', data })
}

// 更新
export function updateTeachingClass(id, data) {
  return request({ method: 'PUT', url: `/teaching-classes/${id}`, data })
}

// 删除
export function deleteTeachingClass(id) {
  return request({ method: 'DELETE', url: `/teaching-classes/${id}` })
}

// 班级学生
export function getTeachingClassStudents(classId) {
  return request({ method: 'GET', url: `/teaching-classes/${classId}/students` })
}

// 添加学生
export function addStudentToClass(classId, userId) {
  return request({ method: 'POST', url: `/teaching-classes/${classId}/students`, data: { userId } })
}

// 移除学生
export function removeStudentFromClass(classId, userId) {
  return request({ method: 'DELETE', url: `/teaching-classes/${classId}/students/${userId}` })
}

// 更新学生状态
export function updateStudentStatus(classId, userId, status) {
  return request({ method: 'PUT', url: `/teaching-classes/${classId}/students/${userId}`, data: { status } })
}

// 结课
export function completeTeachingClass(id) {
  return request({ method: 'POST', url: `/teaching-classes/${id}/complete` })
}

// 停开
export function cancelTeachingClass(id, reason) {
  return request({ method: 'POST', url: `/teaching-classes/${id}/cancel`, data: { reason } })
}