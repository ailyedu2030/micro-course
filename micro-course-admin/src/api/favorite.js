import request from '../utils/request'
export function getFavorites(params) { return request({ method:'GET', url:'/favorites', params }) }
export function addFavorite(data) { return request({ method:'POST', url:'/favorites', data }) }
export function removeFavorite(id) { return request({ method:'DELETE', url:`/favorites/${id}` }) }
export function cancelFavorite(id) { return removeFavorite(id) }
export function getMyFavorites() { return request({ method:'GET', url:'/favorites/my' }) }