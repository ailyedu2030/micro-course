import request from '@/utils/request'

export function getBundles(params) {
  return request({ method: 'GET', url: '/course-bundles', params })
}

export function getBundleById(id) {
  return request({ method: 'GET', url: `/course-bundles/${id}` })
}

export function createBundle(data) {
  return request({ method: 'POST', url: '/course-bundles', data })
}

export function updateBundle(id, data) {
  return request({ method: 'PUT', url: `/course-bundles/${id}`, data })
}

export function publishBundle(id) {
  return request({ method: 'PATCH', url: `/course-bundles/${id}/publish` })
}

export function unpublishBundle(id) {
  return request({ method: 'PATCH', url: `/course-bundles/${id}/unpublish` })
}

export function addBundleCourse(bundleId, data) {
  return request({ method: 'POST', url: `/course-bundles/${bundleId}/items`, data })
}

export function removeBundleCourse(bundleId, itemId) {
  return request({ method: 'DELETE', url: `/course-bundles/${bundleId}/items/${itemId}` })
}

export function deleteBundle(id) {
  return request({ method: 'DELETE', url: `/course-bundles/${id}` })
}

export function getBundleEnrollmentStatus(bundleId) {
  return request({ method: 'GET', url: `/course-bundles/${bundleId}/my-enrollment` })
}
