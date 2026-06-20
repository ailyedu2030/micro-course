import request from '@/utils/request'

export function createOrder(data) {
  return request({ method: 'POST', url: '/orders', data })
}

export function getOrder(id) {
  return request({ method: 'GET', url: `/orders/${id}` })
}

export function getMyOrders(params) {
  return request({ method: 'GET', url: '/orders/my', params })
}

export function payOrder(id, paymentMethod) {
  return request({ method: 'POST', url: `/orders/${id}/pay`, data: { paymentMethod } })
}
