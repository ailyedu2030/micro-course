import { computed, ref } from 'vue'

function createQueueItem(file, index) {
  return {
    id: `${Date.now()}-${index}-${file.name}`,
    file,
    name: file.name,
    progress: 0,
    status: 'pending',
    errorMessage: '',
  }
}

function resolveProgress(progressEvent) {
  const total = progressEvent?.total
  const loaded = progressEvent?.loaded

  if (!total || !loaded) {
    return null
  }

  return Math.min(100, Math.round((loaded * 100) / total))
}

function resolveErrorMessage(error) {
  return error?.response?.data?.message || error?.message || '上传失败'
}

export function useVideoUploadQueue({ uploader }) {
  const queue = ref([])

  const successCount = computed(() => queue.value.filter((item) => item.status === 'success').length)
  const failureCount = computed(() => queue.value.filter((item) => item.status === 'error').length)
  const uploadingCount = computed(() => queue.value.filter((item) => item.status === 'uploading').length)
  const isBatchMode = computed(() => queue.value.length > 1)
  const hasQueue = computed(() => queue.value.length > 0)
  const summaryText = computed(() => {
    const total = queue.value.length
    if (total === 0) return ''

    if (uploadingCount.value > 0) {
      return `共 ${total} 个文件，正在上传 ${uploadingCount.value} 个`
    }

    if (failureCount.value > 0) {
      return `共 ${total} 个文件，成功 ${successCount.value} 个，失败 ${failureCount.value} 个`
    }

    if (successCount.value === total) {
      return `共 ${total} 个文件，全部上传完成`
    }

    return `共 ${total} 个文件待上传`
  })

  function replaceQueue(files = []) {
    queue.value = files
      .filter(Boolean)
      .map((file, index) => createQueueItem(file, index))
  }

  function clearQueue() {
    queue.value = []
  }

  async function uploadAll({ courseId, chapterId }) {
    if (queue.value.length === 0) {
      return { successCount: 0, failureCount: 0, results: [] }
    }

    const results = []

    for (const item of queue.value) {
      item.status = 'uploading'
      item.progress = 0
      item.errorMessage = ''

      try {
        const response = await uploader({
          file: item.file,
          courseId,
          chapterId,
          onProgress: (progressEvent) => {
            const progress = resolveProgress(progressEvent)
            if (progress !== null) {
              item.progress = progress
            }
          },
        })

        item.progress = 100
        item.status = 'success'
        results.push({ success: true, response, item })
      } catch (error) {
        item.status = 'error'
        item.errorMessage = resolveErrorMessage(error)
        results.push({ success: false, error, item })
      }
    }

    return {
      successCount: results.filter((entry) => entry.success).length,
      failureCount: results.filter((entry) => !entry.success).length,
      results,
    }
  }

  return {
    queue,
    hasQueue,
    isBatchMode,
    summaryText,
    successCount,
    failureCount,
    replaceQueue,
    clearQueue,
    uploadAll,
  }
}
