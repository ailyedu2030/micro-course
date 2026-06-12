import request from '../utils/request'
export function getChapters(params) { return request({ method:'GET', url:'/chapters', params }) }
export function getChapterById(id) { return request({ method:'GET', url:`/chapters/${id}` }) }
export function createChapter(data) { return request({ method:'POST', url:'/chapters', data }) }
export function updateChapter(id, data) { return request({ method:'PUT', url:`/chapters/${id}`, data }) }
export function deleteChapter(id) { return request({ method:'DELETE', url:`/chapters/${id}` }) }
export function sortChapters(chapterSorts) { return request({ method:'PUT', url:'/chapters/sort', data: chapterSorts }) }