import request from '../utils/request'
export function enroll(data) { return request({ method:'POST', url:'/enrollments', data }) }
export function getMyEnrollments(params) { return request({ method:'GET', url:'/enrollments/my', params: typeof params === 'object' ? params : {} }) }
/** P1-2: 课程学员分页查询（传 courseId + page/size） */
export function getCourseEnrollments(params) { return request({ method:'GET', url:`/enrollments/course/${params.courseId}`, params: { page: params.page, size: params.size } }) }

export function getEnrollments(params) { return request({ method:'GET', url:'/enrollments', params }) }
export function getCourseRanking(courseId, params) { return request({ method:'GET', url:`/enrollments/course/${courseId}/ranking`, params }) }
export function updateEnrollment(id, data) { return request({ method:'PUT', url:`/enrollments/${id}`, data }) }
export function cancelEnrollment(id) { return request({ method:'DELETE', url:`/enrollments/${id}` }) }

export function exportEnrollments(courseId) { return request({ method:'GET', url:`/enrollments/export`, params:{ courseId }, responseType:'blob' }) }

/** P0-2: 获取学员详情 */
export function getStudentDetail(userId) { return request({ method:'GET', url:`/enrollments/student-detail/${userId}` }) }