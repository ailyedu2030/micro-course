import request from '../utils/request'
export function getMyWrongQuestions(params) { return request({ method: 'GET', url: '/wrong-questions/my', params }) }