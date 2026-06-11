import request from '../utils/request'
export function getMajors(params) { return request({ method: 'GET', url: '/majors', params }) }
export function getMajorById(id) { return request({ method: 'GET', url: `/majors/${id}` }) }
export function createMajor(data) { return request({ method: 'POST', url: '/majors', data }) }
export function updateMajor(id, data) { return request({ method: 'PUT', url: `/majors/${id}`, data }) }
export function deleteMajor(id) { return request({ method: 'DELETE', url: `/majors/${id}` }) }