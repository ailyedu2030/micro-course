import request from '../utils/request'
export function getPosts(params) { return request({ method:'GET', url:'/discussions/posts', params }) }
export function getPostById(id) { return request({ method:'GET', url:`/discussions/posts/${id}` }) }
export function createPost(data) { return request({ method:'POST', url:'/discussions/posts', data }) }
export function deletePost(id) { return request({ method:'DELETE', url:`/discussions/posts/${id}` }) }
export function updatePostPin(id, pinned) { return request({ method:'PUT', url:`/discussions/posts/${id}/pin`, params: { pinned } }) }
export function updatePostEssence(id, essence) { return request({ method:'PUT', url:`/discussions/posts/${id}/essence`, params: { essence } }) }
export function getComments(postId) { return request({ method:'GET', url:'/discussions/comments', params:{postId} }) }
export function createComment(data) { return request({ method:'POST', url:'/discussions/comments', data }) }
export function deleteComment(id) { return request({ method:'DELETE', url:`/discussions/comments/${id}` }) }
export function likeComment(id) { return request({ method:'POST', url:`/discussions/comments/${id}/like` }) }