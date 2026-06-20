import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getSlides, getSlidePages, getSlidePage, uploadSlide, generateNarration, updateNarration, generateAllNarrations, generateAudio, generateAllAudio } from '@/plugins/interactive/api/slide'

export function useSlideManager(courseId) {
  const slide = ref(null)
  const pages = ref([])
  const selectedPage = ref(null)
  const editingScript = ref('')
  const uploading = ref(false)
  const aiLoading = ref(false)
  const ttsLoading = ref(false)
  const showPreview = ref(false)

  async function loadData() {
    try {
      const s = await getSlides(courseId.value)
      slide.value = s.data
      if (s.data?.status === 2) {
        const p = await getSlidePages(courseId.value)
        pages.value = p.data || []
      }
    } catch { /* no slides */ }
  }

  function selectPage(page) {
    selectedPage.value = page
    editingScript.value = page.narrationScript || ''
  }

  async function handleUpload(file) {
    uploading.value = true
    try {
      await uploadSlide(courseId.value, file)
      ElMessage.success('上传成功，正在后台渲染...')
      // Poll for status
      startPolling()
    } catch { ElMessage.error('上传失败') }
    finally { uploading.value = false }
    return false
  }

  async function handleGenerateAI() {
    if (!selectedPage.value) return
    aiLoading.value = true
    try {
      const res = await generateNarration(courseId.value, selectedPage.value.pageNumber)
      const u = res.data
      selectedPage.value.narrationScript = u.narrationScript
      selectedPage.value.narrationStatus = u.narrationStatus
      selectedPage.value.narrationStatusText = u.narrationStatusText
      editingScript.value = u.narrationScript
      ElMessage.success('讲述稿已生成')
    } catch { ElMessage.error('AI 生成失败') }
    finally { aiLoading.value = false }
  }

  async function handleGenerateTTS() {
    if (!selectedPage.value) return
    ttsLoading.value = true
    try {
      const res = await generateAudio(courseId.value, selectedPage.value.pageNumber)
      Object.assign(selectedPage.value, res.data)
      ElMessage.success('音频已生成')
    } catch { ElMessage.error('TTS 生成失败') }
    finally { ttsLoading.value = false }
  }

  async function handleSaveScript() {
    if (!selectedPage.value || editingScript.value === selectedPage.value.narrationScript) return
    try {
      await updateNarration(courseId.value, selectedPage.value.pageNumber, editingScript.value)
      selectedPage.value.narrationStatus = 'TEACHER_EDITED'
      selectedPage.value.narrationStatusText = '教师已编辑'
    } catch {}
  }

  let pollTimer = null
  function startPolling() {
    if (pollTimer) return
    pollTimer = setInterval(async () => {
      await loadData()
      if (slide.value?.status !== 0 && slide.value?.status !== 1) {
        if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
      }
    }, 3000)
  }

  return {
    slide, pages, selectedPage, editingScript,
    uploading, aiLoading, ttsLoading, showPreview,
    loadData, selectPage, handleUpload,
    handleGenerateAI, handleGenerateTTS, handleSaveScript,
  }
}
