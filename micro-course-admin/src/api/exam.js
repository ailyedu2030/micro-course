import request from '@/utils/request'

// NOTE: exam APIs use mixed paths — list uses /exercises with isExam param, while /exams/* endpoints are for exam-specific operations

/**
 * 获取试卷列表（通过 exercises 接口筛选 isExam=true）
 * 后端 /api/exercises 已支持 isExam 参数
 */
export function getExamList(params) {
  return request({ method: 'GET', url: '/exercises', params })
}

/** 学生端：获取我的考试列表 */
export function getMyExams() {
  return request({ method: 'GET', url: '/exams/my' })
}

/** 获取单张试卷详情 */
export function getExamById(id) {
  return request({ method: 'GET', url: `/exercises/${id}` })
}

/** 智能组卷 */
export function generateExam(data) {
  return request({ method: 'POST', url: '/exams/generate', data })
}

/** 删除试卷 */
export function deleteExam(id) {
  return request({ method: 'DELETE', url: `/exercises/${id}` })
}
