/**
 * useCoursewareUpload.js · PPT/HTML 课件上传统一逻辑（F-2026-08-07-13）
 *
 * PPT 与 HTML 模块共用：文件校验（大小/类型/MIME/PPTX 魔数）+ uploadSlide +
 * PPT 渲染轮询（树从 EMPTY → PPT 完成）。上传成功通过 onSuccess 回调通知宿主刷新树。
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadSlide } from '../api/slide'
import { getCoursewareTree } from '../api/queryCourseware'

export function useCoursewareUpload({ courseId, chapterId, sectionId, onSuccess }) {
  const uploading = ref(false)
  const uploadProgress = ref(0)
  const renderPending = ref(false)
  let renderPollTimer = null

  async function validate(file) {
    if (file.size > 50 * 1024 * 1024) {
      ElMessage.warning('文件超过 50MB 限制')
      return false
    }
    const lowerName = file.name.toLowerCase()
    const isHtml = lowerName.endsWith('.html') || lowerName.endsWith('.htm')
    const isPptx = lowerName.endsWith('.pptx')
    if (!isHtml && !isPptx) {
      ElMessage.warning('仅支持 .pptx / .html / .htm 格式')
      return false
    }
    if (isHtml && file.size > 5 * 1024 * 1024) {
      ElMessage.warning('HTML 文件不能超过 5MB')
      return false
    }
    if (isPptx) {
      const validMime = file.type === '' || file.type === 'application/vnd.openxmlformats-officedocument.presentationml.presentation'
      if (file.type && !validMime) {
        ElMessage.warning('PPTX 文件 MIME 类型不匹配，请检查文件格式')
        return false
      }
      try {
        const slice = file.slice(0, 4)
        const buf = await slice.arrayBuffer()
        const header = new Uint8Array(buf)
        if (header[0] !== 0x50 || header[1] !== 0x4B || header[2] !== 0x03 || header[3] !== 0x04) {
          ElMessage.warning('PPTX 文件头校验失败：文件可能已损坏或不是有效的 PPTX 格式')
          return false
        }
      } catch {
        ElMessage.warning('PPTX 文件校验失败，请重试')
        return false
      }
    }
    return { isHtml, isPptx }
  }

  async function handleUpload(file, expected) {
    if (uploading.value) {
      ElMessage.warning('已有课件正在上传，请稍候')
      return false
    }
    const v = await validate(file)
    if (!v) return false
    if (expected === 'PPT' && !v.isPptx) {
      ElMessage.warning('PPT 课件仅支持 .pptx 文件')
      return false
    }
    if (expected === 'HTML' && !v.isHtml) {
      ElMessage.warning('HTML 课件仅支持 .html / .htm 文件')
      return false
    }
    uploading.value = true
    uploadProgress.value = 0
    try {
      await uploadSlide(courseId.value, file, (e) => {
        uploadProgress.value = Math.round((e.loaded / e.total) * 100)
      }, chapterId.value ? Number(chapterId.value) : null, sectionId.value ? Number(sectionId.value) : null)
      if (v.isHtml) {
        // P0-3 诚实提示：上传只保存到服务器，编辑器需重载后才显示新内容
        ElMessage.success('HTML 已保存到服务器，编辑器将自动重载新内容')
      } else {
        ElMessage.success('上传成功，正在后台渲染...')
      }
      onSuccess?.()
      return true
    } catch (e) {
      ElMessage.error(e?.response?.data?.message || e?.message || '上传失败')
      return false
    } finally {
      uploading.value = false
    }
  }

  /** PPT 上传后轮询树直至 type !== EMPTY（渲染完成）；HTML 由宿主直接刷新。 */
  function startRenderPolling(done, maxTries = 30) {
    stopRenderPolling()
    renderPending.value = true
    let count = 0
    renderPollTimer = setInterval(async () => {
      count++
      try {
        const res = await getCoursewareTree(courseId.value, sectionId.value, chapterId.value)
        const t = res.data || res
        if (t.type !== 'EMPTY') {
          stopRenderPolling()
          renderPending.value = false
          ElMessage.success('课件处理完成')
          done?.()
          return
        }
      } catch (e) {
        // 轮询失败下一轮重试
      }
      if (count > maxTries) {
        stopRenderPolling()
        renderPending.value = false
        // G3-P1-C-1: 轮询超时后调 getCoursewareTree 透传真实渲染错误（如 PPT 文件损坏），
        // 而非一律提示泛化的"超时"（L0：错误消息诚实，用户知道该怎么办）。
        try {
          const res = await getCoursewareTree(courseId.value, sectionId.value, chapterId.value)
          const t = res.data || res
          const renderErr = t?.renderErrorMessage
          if (t?.renderStatus === 'FAILED' && renderErr) {
            ElMessage.error(`课件处理失败：${renderErr}，请重新上传`)
          } else if (t?.renderStatus === 'FAILED') {
            ElMessage.error('课件处理失败（文件可能损坏），请重新上传')
          } else {
            ElMessage.error('课件处理超时，请稍后刷新查看')
          }
        } catch {
          ElMessage.error('课件处理超时，请稍后刷新查看')
        }
      }
    }, 3000)
  }

  function stopRenderPolling() {
    if (renderPollTimer) {
      clearInterval(renderPollTimer)
      renderPollTimer = null
    }
  }

  return {
    uploading,
    uploadProgress,
    renderPending,
    handleUpload,
    startRenderPolling,
    stopRenderPolling
  }
}
