import { ref, onScopeDispose } from 'vue'
import { ElMessage } from 'element-plus'
import { getSlides, getSlidePages, getSlidePage, uploadSlide, generateNarration, updateNarration, generateAllNarrations, generateAudio, generateAllAudio } from '@/plugins/interactive/api/slide'

export function useSlideManager(courseId, chapterId) {
  const slide = ref(null)
  const pages = ref([])
  const selectedPage = ref(null)
  const editingScript = ref('')
  const uploading = ref(false)
  const uploadingProgress = ref(0)
  const aiLoading = ref(false)
  const ttsLoading = ref(false)
  const showPreview = ref(false)

  async function loadData() {
    try {
      const s = await getSlides(courseId.value)
      slide.value = s.data
      if (s.data?.status === 2) {
        const p = await getSlidePages(courseId.value, chapterId?.value)
        pages.value = p.data || []
      }
    } catch (e) {
      if (e?.response?.status !== 404) {
        console.warn('[SlideManager] loadData error', e)
      }
    }
  }

  function selectPage(page) {
    selectedPage.value = page
    editingScript.value = page.narrationScript || ''
  }

  async function handleUpload(file) {
    uploading.value = true
    try {
      await uploadSlide(courseId.value, file, (e) => {
        uploadingProgress.value = Math.round((e.loaded / e.total) * 100)
      }, chapterId?.value)
      ElMessage.success('上传成功，正在后台渲染...')
      startPolling()
    } catch (e) { ElMessage.error(e?.response?.data?.message || '上传失败') }
    finally { uploading.value = false }
    return false
  }

  async function handleGenerateAI() {
    if (!selectedPage.value) return
    aiLoading.value = true
    try {
      const res = await generateNarration(courseId.value, selectedPage.value.pageNumber, selectedPage.value.sectionId || null)
      const u = res.data
      selectedPage.value.narrationScript = u.narrationScript
      selectedPage.value.narrationStatus = u.narrationStatus
      selectedPage.value.narrationStatusText = u.narrationStatusText
      editingScript.value = u.narrationScript
      ElMessage.success('讲述稿已生成')
    } catch (e) { ElMessage.error(e?.response?.data?.message || 'AI 生成失败') }
    finally { aiLoading.value = false }
  }

  async function handleGenerateTTS() {
    if (!selectedPage.value) return
    ttsLoading.value = true
    try {
      const res = await generateAudio(courseId.value, selectedPage.value.pageNumber)
      Object.assign(selectedPage.value, res.data)
      ElMessage.success('音频已生成')
    } catch (e) { ElMessage.error(e?.response?.data?.message || 'TTS 生成失败') }
    finally { ttsLoading.value = false }
  }

  async function handleSaveScript() {
    if (!selectedPage.value || editingScript.value === selectedPage.value.narrationScript) return
    try {
      await updateNarration(courseId.value, selectedPage.value.pageNumber, editingScript.value, selectedPage.value.sectionId || null)
      selectedPage.value.narrationStatus = 'TEACHER_EDITED'
      selectedPage.value.narrationStatusText = '教师已编辑'
    } catch (e) {
      ElMessage.error('保存讲述稿失败')
      console.warn('[SlideManager] saveNarration failed', e)
    }
  }

  let pollTimer = null
  let pollCount = 0
  const MAX_POLLS = 100
  function startPolling() {
    if (pollTimer) return
    pollTimer = setInterval(async () => {
      pollCount++
      if (pollCount > MAX_POLLS) {
        clearInterval(pollTimer); pollTimer = null
        ElMessage.warning('渲染耗时较长，请刷新页面检查状态')
        return
      }
      await loadData()
      if (slide.value?.status === 3) {
        clearInterval(pollTimer); pollTimer = null
        ElMessage.error(slide.value?.errorMessage || '课件渲染失败')
      } else if (slide.value?.status === 2) {
        clearInterval(pollTimer); pollTimer = null
        ElMessage.success('课件渲染完成')
      }
    }, 3000)
  }

  onScopeDispose(() => {
    if (pollTimer) clearInterval(pollTimer)
  })

  return {
    slide, pages, selectedPage, editingScript,
    uploading, uploadingProgress, aiLoading, ttsLoading, showPreview,
    loadData, selectPage, handleUpload,
    handleGenerateAI, handleGenerateTTS, handleSaveScript,
  }
}
