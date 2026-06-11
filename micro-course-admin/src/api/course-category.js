import request from '../utils/request'
export function getCategories(params) { return request({ method:'GET', url:'/course-categories', params }) }
export function getCategoryById(id) { return request({ method:'GET', url:`/course-categories/${id}` }) }
export function createCategory(data) { return request({ method:'POST', url:'/course-categories', data }) }
export function updateCategory(id, data) { return request({ method:'PUT', url:`/course-categories/${id}`, data }) }
export function deleteCategory(id) { return request({ method:'DELETE', url:`/course-categories/${id}` }) }