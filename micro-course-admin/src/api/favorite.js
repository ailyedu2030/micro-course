import request from '../utils/request'
export function getFavorites(params) { return request({ method:'GET', url:'/favorites', params }) }
export function addFavorite(courseId) { return request({ method:'POST', url:'/favorites', params:{courseId} }) }
export function removeFavorite(id) { return request({ method:'DELETE', url:`/favorites/${id}` }) }