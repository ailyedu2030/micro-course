import request from '../utils/request'

// 1. 初始化空草稿
export function initStorageDraft() {
  return request({ method: 'POST', url: '/storage-applications/init' })
}

// 2. 我的申请列表
export function getMyStorageDrafts() {
  return request({ method: 'GET', url: '/storage-applications/my-drafts' })
}

// 3. 获取详情
export function getStorageDetail(id) {
  return request({ method: 'GET', url: `/storage-applications/${id}` })
}

// 4. 全量保存
export function saveStorageApplication(id, data) {
  return request({ method: 'PUT', url: `/storage-applications/${id}`, data })
}

// 5. 自动保存
export function autoSaveStorageApplication(id, data) {
  return request({ method: 'PATCH', url: `/storage-applications/${id}/auto-save`, data })
}

// 6. 上传图片（签名/公章）
export function uploadStorageImage(id, file, type) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('type', type)
  return request({
    method: 'POST',
    url: `/storage-applications/${id}/upload-image`,
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 7. 预览数据
export function getStoragePreview(id) {
  return request({ method: 'GET', url: `/storage-applications/${id}/preview` })
}

// 8. 提交审核
export function submitStorageApplication(id) {
  return request({ method: 'POST', url: `/storage-applications/${id}/submit` })
}

// 9. 重置模块
export function resetStorageModule(id, module) {
  return request({ method: 'POST', url: `/storage-applications/${id}/reset-module`, params: { module } })
}

// 10. 重置全部
export function resetStorageAll(id) {
  return request({ method: 'POST', url: `/storage-applications/${id}/reset-all` })
}

// 11. 下载Word
export function exportStorageWord(id) {
  return request({
    method: 'GET',
    url: `/storage-applications/${id}/export-word`,
    responseType: 'blob'
  })
}

// 12. 下载PDF
export function exportStoragePdf(id) {
  return request({
    method: 'GET',
    url: `/storage-applications/${id}/export-pdf`,
    responseType: 'blob'
  })
}
