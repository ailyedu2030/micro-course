import request from '../utils/request'
export function getFavorites(params) { return request({ method:'GET', url:'/favorites', params }) }
export function addFavorite(data) { return request({ method:'POST', url:'/favorites', data }) }
export function removeFavorite(id) { return request({ method:'DELETE', url:`/favorites/${id}` }) }
// P1-2026-08-21: 按收藏记录 id 取消（管理端/教师端，后端 /favorites/record/{id}）
export function removeFavoriteRecord(id) { return request({ method:'DELETE', url:`/favorites/record/${id}` }) }
export function cancelFavorite(id) { return removeFavorite(id) }
export function getMyFavorites() { return request({ method:'GET', url:'/favorites/my' }) }