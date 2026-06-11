import request from '../utils/request'
export function getTags(params) { return request({ method:'GET', url:'/tags', params }) }
export function createTag(data) { return request({ method:'POST', url:'/tags', data }) }
export function updateTag(id, data) { return request({ method:'PUT', url:`/tags/${id}`, data }) }
export function deleteTag(id) { return request({ method:'DELETE', url:`/tags/${id}` }) }