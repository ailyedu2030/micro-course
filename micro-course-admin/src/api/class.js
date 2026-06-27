import request from '../utils/request'
export function getClasses(params) { return request({ method: 'GET', url: '/classes', params }) }
export function getClassById(id) { return request({ method: 'GET', url: `/classes/${id}` }) }
export function createClass(data) { return request({ method: 'POST', url: '/classes', data }) }
export function updateClass(id, data) { return request({ method: 'PUT', url: `/classes/${id}`, data }) }
export function deleteClass(id) { return request({ method: 'DELETE', url: `/classes/${id}` }) }
export function getClassStudents(id) { return request({ method: 'GET', url: `/classes/${id}/students` }) }