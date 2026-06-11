import request from '../utils/request'
export function getTags(params) { return request({ method:'GET', url:'/tags', params }) }
export function createTag(data) { return request({ method:'POST', url:'/tags', data }) }