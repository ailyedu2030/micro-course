// P2-16: 购物车服务端同步 API
import request from '../utils/request'

export function getCart() {
  return request({ method: 'GET', url: '/cart' })
}

export function addCartItem(courseId, quantity = 1) {
  return request({ method: 'POST', url: '/cart', data: { courseId, quantity } })
}

export function updateCartItem(itemId, quantity) {
  return request({ method: 'PUT', url: `/cart/${itemId}`, data: { quantity } })
}

export function removeCartItem(itemId) {
  return request({ method: 'DELETE', url: `/cart/${itemId}` })
}

export function clearCart() {
  return request({ method: 'DELETE', url: '/cart' })
}
