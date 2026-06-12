import request from '../utils/request'

export function getMyBadges() {
  return request({ method: 'GET', url: '/badges/my' })
}

export function getBadgeDefinitions() {
  return request({ method: 'GET', url: '/badges/definitions' })
}

export function getMyAchievements() {
  return request({ method: 'GET', url: '/badges/achievements' })
}
