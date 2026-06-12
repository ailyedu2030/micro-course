import request from '../utils/request'
export function getVideos(params) { return request({ method:'GET', url:'/videos', params }) }
export function getVideoById(id) { return request({ method:'GET', url:`/videos/${id}` }) }
export function createVideo(data) { return request({ method:'POST', url:'/videos', data }) }
export function updateVideo(id, data) { return request({ method:'PUT', url:`/videos/${id}`, data }) }
export function deleteVideo(id) { return request({ method:'DELETE', url:`/videos/${id}` }) }
export function uploadVideo(formData) { return request({ method:'POST', url:'/videos/upload', data: formData, headers: { 'Content-Type': 'multipart/form-data' } }) }
export function uploadVideoCover(id, file) { const fd = new FormData(); fd.append('file', file); return request({ method:'POST', url:`/videos/${id}/cover`, data: fd, headers: {'Content-Type':'multipart/form-data'} }) }
export function getVideoPlayUrl(id, sign) { return request({ method:'GET', url:`/videos/${id}/play`, params:{ sign } }) }