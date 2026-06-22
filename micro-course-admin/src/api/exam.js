import request from '../utils/request'

/** J3-01: 获取当前学生的考试列表 */
export function getMyExams() {
  return request({ method: 'GET', url: '/exams/my' })
}
