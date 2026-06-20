import request from '../utils/request'

export function getMyCertificates() {
  return request({ method: 'GET', url: '/certificates/my' })
}

export function downloadCertificate(certificateId) {
  return request({ method: 'GET', url: `/certificates/${certificateId}/download`, responseType: 'blob' })
}