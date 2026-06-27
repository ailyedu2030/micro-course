import request from '@/utils/request'

// ========== 微专业主表 ==========
export function getMicroSpecialtyList(params) {
  return request({ method: 'GET', url: '/micro-specialties', params })
}

export function getSquareData() {
  return request({ method: 'GET', url: '/micro-specialties/square' })
}

export function getMicroSpecialtyDetail(id) {
  return request({ method: 'GET', url: `/micro-specialties/${id}` })
}

export function createMicroSpecialty(data) {
  return request({ method: 'POST', url: '/micro-specialties', data })
}

export function updateMicroSpecialty(id, data) {
  return request({ method: 'PUT', url: `/micro-specialties/${id}`, data })
}

export function deleteMicroSpecialty(id) {
  return request({ method: 'DELETE', url: `/micro-specialties/${id}` })
}

// ========== 状态流转 ==========
export function submitMicroSpecialty(id) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/submit` })
}

export function approveMicroSpecialty(id) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/approve` })
}

export function rejectMicroSpecialty(id, data) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/reject`, data })
}

export function openMicroSpecialty(id) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/open` })
}

export function closeMicroSpecialty(id) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/close` })
}

export function cancelMicroSpecialty(id) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/cancel` })
}

export function archiveMicroSpecialty(id) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/archive` })
}

export function transferLeadership(id, data) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/transfer-leadership`, data })
}

// ========== 课程编排 ==========
/** 获取微专业课程列表（注意与 course.js getCourses 区分：此函数传 msId，course.js 传搜索参数） */
export function getCourses(id) {
  return request({ method: 'GET', url: `/micro-specialties/${id}/courses` })
}

export function addCourse(id, data) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/courses`, data })
}

export function updateCourseItem(id, itemId, data) {
  return request({ method: 'PUT', url: `/micro-specialties/${id}/courses/${itemId}`, data })
}

export function removeCourse(id, itemId) {
  return request({ method: 'DELETE', url: `/micro-specialties/${id}/courses/${itemId}` })
}

// ========== 教师团队 ==========
export function getTeachers(id) {
  return request({ method: 'GET', url: `/micro-specialties/${id}/teachers` })
}

export function inviteTeacher(id, data) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/teachers`, data })
}

export function removeTeacher(id, teacherId) {
  return request({ method: 'DELETE', url: `/micro-specialties/${id}/teachers/${teacherId}` })
}

// ========== 修读 ==========
export function applyEnrollment(data) {
  return request({ method: 'POST', url: '/micro-specialty-enrollments/apply', data })
}

export function approveEnrollment(id) {
  return request({ method: 'POST', url: `/micro-specialty-enrollments/${id}/approve` })
}

export function rejectEnrollment(id, data) {
  return request({ method: 'POST', url: `/micro-specialty-enrollments/${id}/reject`, data })
}

export function classImport(data) {
  return request({ method: 'POST', url: '/micro-specialty-enrollments/class-import', data })
}

export function getMyEnrollments() {
  return request({ method: 'GET', url: '/micro-specialty-enrollments/my' })
}

export function dropEnrollment(id, data) {
  return request({ method: 'POST', url: `/micro-specialty-enrollments/${id}/drop`, data })
}

export function reapplyEnrollment(id) {
  return request({ method: 'POST', url: `/micro-specialty-enrollments/${id}/reapply` })
}

export function issueCertificate(id) {
  return request({ method: 'POST', url: `/micro-specialty-enrollments/${id}/issue-certificate` })
}

export function getEnrollmentList(msId, params) {
  return request({ method: 'GET', url: `/micro-specialties/${msId}/enrollments`, params })
}

// ========== 申报 ==========
export function submitProposal(data) {
  return request({ method: 'POST', url: '/micro-specialty-proposals', data })
}

export function getMyProposals(params) {
  return request({ method: 'GET', url: '/micro-specialty-proposals/my', params })
}

export function getAllProposals(params) {
  return request({ method: 'GET', url: '/micro-specialty-proposals', params })
}

export function approveProposal(id) {
  return request({ method: 'POST', url: `/micro-specialty-proposals/${id}/approve` })
}

export function rejectProposal(id, data) {
  return request({ method: 'POST', url: `/micro-specialty-proposals/${id}/reject`, data })
}

export function withdrawProposal(id) {
  return request({ method: 'POST', url: `/micro-specialty-proposals/${id}/withdraw` })
}

export function resubmitProposal(id, data) {
  return request({ method: 'POST', url: `/micro-specialty-proposals/${id}/resubmit`, data })
}

// ========== 教师邀请 ==========
export function getPendingInvites(params) {
  return request({ method: 'GET', url: '/micro-specialty-teachers/pending-invites', params })
}

export function getPendingCrossDeptInvites(params) {
  return request({ method: 'GET', url: '/micro-specialty-teachers/pending-cross-dept-invites', params })
}

export function acceptInvite(inviteId) {
  return request({ method: 'POST', url: `/micro-specialty-teachers/${inviteId}/accept` })
}

export function declineInvite(inviteId) {
  return request({ method: 'POST', url: `/micro-specialty-teachers/${inviteId}/decline` })
}

export function reinviteTeacher(inviteId, data) {
  return request({ method: 'POST', url: `/micro-specialty-teachers/${inviteId}/reinvite`, data: data || {} })
}

export function leaveTeam(inviteId) {
  return request({ method: 'POST', url: `/micro-specialty-teachers/${inviteId}/leave` })
}

export function reviewCrossDept(inviteId, data) {
  return request({ method: 'POST', url: `/micro-specialty-teachers/${inviteId}/review-cross-dept`, data })
}

// ========== 置顶 ==========
export function applyFeatured(id, data) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/apply-featured`, data })
}

export function approveFeatured(id) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/approve-featured` })
}

export function rejectFeatured(id, reason) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/reject-featured`, data: { reason: reason || '' } })
}

export function setGoldFeatured(id) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/set-gold-featured` })
}

export function unsetGoldFeatured(id) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/unset-gold-featured` })
}

export function unsetFeatured(id) {
  return request({ method: 'POST', url: `/micro-specialties/${id}/unset-featured` })
}

// ========== 统计 ==========
export function getStats(id) {
  return request({ method: 'GET', url: `/micro-specialties/${id}/stats` })
}

/**
 * 获取当前用户在该微专业中的角色（用于前端路由守卫 requiresLead 校验）
 * @returns {Promise<{code: number, data: {role: string|null}}>} role = "LEAD" | "MEMBER" | "ASSISTANT" | null
 */
export function getMyRole(id) {
  return request({ method: 'GET', url: `/micro-specialties/${id}/my-role` })
}
