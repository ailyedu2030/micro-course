import request from '../utils/request'

export function getMyCertificates(params) {
  return request({ method: 'GET', url: '/certificates/my', params })
}

export function downloadCertificate(certificateId) {
  return request({ method: 'GET', url: `/certificates/${certificateId}/download`, responseType: 'blob' })
}