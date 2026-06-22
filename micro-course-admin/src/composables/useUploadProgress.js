/**
 * D2: 上传进度 composable
 * 为需要自定义上传进度展示的组件提供精细化控制
 * 全局进度由 request.js 中的 globalUploadState 自动驱动
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

export function useUploadProgress() {
  const uploading = ref(false)
  const percent = ref(0)

  /**
   * 创建一个上传进度回调，绑定到 axios config.onUploadProgress
   * @returns {{ onUploadProgress: Function }} 可直接展开到 axios config 中
   */
  function createProgressTracker() {
    uploading.value = true
    percent.value = 0
    return {
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          percent.value = Math.round((progressEvent.loaded * 100) / progressEvent.total)
        }
      }
    }
  }

  /** 标记上传完成 */
  function finishProgress() {
    uploading.value = false
    percent.value = 100
  }

  /** 重置上传状态 */
  function resetProgress() {
    uploading.value = false
    percent.value = 0
  }

  /** 上传失败处理 */
  function failProgress(err) {
    uploading.value = false
    percent.value = 0
    ElMessage.error(err?.message || '上传失败，请重试')
  }

  return {
    uploading,
    percent,
    createProgressTracker,
    finishProgress,
    resetProgress,
    failProgress
  }
}
