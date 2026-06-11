import request from '../utils/request'
export function getDepartments(params) { return request({ method: 'GET', url: '/departments', params }) }
export function getDepartmentById(id) { return request({ method: 'GET', url: `/departments/${id}` }) }
export function createDepartment(data) { return request({ method: 'POST', url: '/departments', data }) }
export function updateDepartment(id, data) { return request({ method: 'PUT', url: `/departments/${id}`, data }) }
export function deleteDepartment(id) { return request({ method: 'DELETE', url: `/departments/${id}` }) }