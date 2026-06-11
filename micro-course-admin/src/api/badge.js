import request from '../utils/request'

export function getMyBadges() {
  return request({ method: 'GET', url: '/badges/my' })
}
