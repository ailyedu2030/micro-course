<template>
  <div class="slide-manage fade-in">
    <!-- Page Header -->
    <header class="manage-header">
      <button class="back-btn" @click="$router.back()" aria-label="返回上一页">
        <el-icon :size="20"><ArrowLeft /></el-icon>
      </button>
      <div class="header-info">
        <h1 class="page-title">幻灯片管理</h1>
        <span class="page-subtitle" v-if="slide">{{ pages.length }} 页 · {{ slide.fileName }}</span>
        <el-tag v-if="statusTag" :type="statusTag.type" size="small" effect="plain" class="status-chip">
          {{ statusTag.text }}
        </el-tag>
      </div>
      <div class="header-actions" v-if="slide?.status === 2">
        <el-button :loading="aiGenerating" @click="handleGenerateAllAI" :icon="MagicStick">
          批量 AI 生成
        </el-button>
        <el-button type="success" :loading="ttsGenerating" @click="handleGenerateAllTTS" :icon="Headset">
          批量生成音频
        </el-button>
        <el-button @click="showPreview = true" :icon="View">
          预览
        </el-button>
      </div>
    </header>

    <!-- Upload Zone -->
    <section v-if="!slide" class="upload-hero">
      <div class="upload-card">
        <div class="upload-icon-wrapper">
          <el-icon :size="40"><UploadFilled /></el-icon>
        </div>
        <h2 class="upload-title">上传 PPT 课件</h2>
        <p class="upload-desc">支持 .pptx 格式，最大 50MB。上传后将自动渲染为高清幻灯片</p>
        <el-upload drag :show-file-list="false" :before-upload="handleUpload" accept=".pptx" :disabled="uploading"
          class="upload-dragger">
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
        </el-upload>
      </div>
    </section>

    <!-- Processing -->
    <section v-else-if="slide.status === 0 || slide.status === 1" class="processing-card">
      <div class="processing-content">
        <div class="pulse-ring" />
        <h3>正在渲染幻灯片</h3>
        <el-progress :percentage="renderProgress" :stroke-width="8" :text-inside="false" color="#6366f1" />
        <p class="processing-hint">请稍候，系统正在逐页渲染高清图片...</p>
      </div>
    </section>

    <!-- Error -->
    <section v-else-if="slide.status === 3" class="error-card">
      <el-result icon="error" title="渲染失败" :sub-title="slide.errorMessage || '未知错误'">
        <template #extra>
          <el-upload :show-file-list="false" :before-upload="handleUpload" accept=".pptx">
            <el-button type="primary" :icon="Refresh">重新上传</el-button>
          </el-upload>
        </template>
      </el-result>
    </section>

    <!-- Main Workspace -->
    <section v-else-if="slide.status === 2" class="workspace">
      <!-- Thumbnail Grid -->
      <div class="thumb-grid">
        <div v-for="page in pages" :key="page.pageNumber"
          class="thumb-card" :class="{ active: selectedPage?.pageNumber === page.pageNumber }"
          tabindex="0" role="button"
          :aria-label="'第' + page.pageNumber + '页'"
          @click="selectPage(page)"
          @keydown.enter="selectPage(page)"
          @keydown.space.prevent="selectPage(page)">
          <div class="thumb-img-wrap">
            <img v-if="thumbUrls[page.pageNumber]" :src="thumbUrls[page.pageNumber]"
              :alt="'第' + page.pageNumber + '页'" class="thumb-img" loading="lazy" />
            <div v-else class="thumb-skeleton" />
            <div class="thumb-overlay">
              <span class="thumb-num">{{ page.pageNumber }}</span>
              <span v-if="page.hasAnimation || page.hasEmbeddedMedia"
                class="compat-warn" title="包含无法在播放器中展示的内容">⚠</span>
            </div>
          </div>
          <div class="thumb-status">
            <span class="status-dot" :class="statusDotClass(page.narrationStatus)" />
            <span class="status-label">{{ page.narrationStatusText }}</span>
          </div>
        </div>
      </div>

      <!-- Editor Panel -->
      <aside class="editor-panel" v-if="selectedPage">
        <div class="editor-header">
          <h3>第 {{ selectedPage.pageNumber }} 页</h3>
          <button class="close-btn" @click="selectedPage = null"><el-icon :size="16"><Close /></el-icon></button>
        </div>

        <!-- Compatibility Warning -->
        <div v-if="selectedPage.hasAnimation || selectedPage.hasEmbeddedMedia" class="compat-alert">
          <el-icon :size="16"><WarningFilled /></el-icon>
          <span>{{
            [selectedPage.hasAnimation ? '包含动画效果，播放时将展示最终静态状态' : '',
             selectedPage.hasEmbeddedMedia ? '包含嵌入视频/音频，播放器无法展示' : '']
            .filter(Boolean).join('；')
          }}</span>
        </div>

        <!-- Image Preview -->
        <div class="preview-box" v-if="previewUrl">
          <img :src="previewUrl" class="preview-img" alt="预览" />
        </div>

        <!-- Extracted Text -->
        <div v-if="selectedPage.extractedText" class="text-section">
          <span class="section-label">提取文本</span>
          <p class="extracted-text">{{ selectedPage.extractedText }}</p>
        </div>

        <!-- Narration Editor -->
        <div class="narration-section">
          <div class="section-header">
            <span class="section-label">讲述稿</span>
            <div class="section-actions">
              <el-button size="small" :loading="aiLoading" @click="handleGenerateAI" :icon="MagicStick">
                AI 生成
              </el-button>
              <el-button v-if="selectedPage.narrationScript" size="small" type="success"
                :loading="ttsLoading" @click="handleGenerateTTS" :icon="Headset">
                生成音频
              </el-button>
            </div>
          </div>
          <el-input v-model="editingScript" type="textarea" :rows="10"
            placeholder="点击「AI 生成」自动生成讲述稿，或手动输入..."
            @blur="handleSaveScript" resize="none" />
          <div v-if="audioInfo" class="audio-meta">
            <el-icon :size="14"><Headset /></el-icon>
            <span>{{ audioInfo }}</span>
          </div>
        </div>
      </aside>

      <aside v-else class="editor-panel editor-empty">
        <el-empty description="点击缩略图开始编辑" :image-size="80" />
      </aside>
    </section>

    <!-- Preview Dialog -->
    <el-dialog v-model="showPreview" title="学生视角预览" fullscreen :destroy-on-close="true">
      <SlidePreview :course-id="courseId" v-if="showPreview" @close="showPreview = false" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, UploadFilled, MagicStick, Headset, View, Close, WarningFilled, Refresh } from '@element-plus/icons-vue'
import { uploadSlide, getSlides, getSlidePages, getSlidePage, generateNarration, updateNarration, generateAllNarrations, generateAudio, generateAllAudio } from '@/plugins/interactive/api/slide'
import SlidePreview from '@/plugins/interactive/components/SlidePreview.vue'
import { loadAuthImage, clearImageCache } from '@/utils/authImage'

const route = useRoute()
const courseId = computed(() => route.params.id)

const slide = ref(null)
const pages = ref([])
const selectedPage = ref(null)
const editingScript = ref('')
const uploading = ref(false)
const aiLoading = ref(false)
const aiGenerating = ref(false)
const ttsLoading = ref(false)
const ttsGenerating = ref(false)
const showPreview = ref(false)
const thumbUrls = ref({})
const previewUrl = ref('')
let pollTimer = null
let progressSim = null

const statusTag = computed(() => {
  if (!slide.value) return null
  const map = [{ type: 'info', text: '上传中' }, { type: 'warning', text: '渲染中' }, { type: 'success', text: '就绪' }, { type: 'danger', text: '失败' }]
  return map[slide.value.status] || map[0]
})

const audioInfo = computed(() => {
  if (!selectedPage.value?.audioDuration) return ''
  const m = Math.floor(selectedPage.value.audioDuration / 60)
  const s = String(selectedPage.value.audioDuration % 60).padStart(2, '0')
  return `音频时长 ${m}:${s}`
})

function statusDotClass(s) {
  if (s === 'AUDIO_READY') return 'dot-ready'
  if (s === 'AI_GENERATED' || s === 'TEACHER_EDITED') return 'dot-script'
  return 'dot-pending'
}

async function handleUpload(file) {
  // 前端校验：文件大小和类型
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.warning('文件超过 50MB 限制')
    return false
  }
  if (!file.name.endsWith('.pptx')) {
    ElMessage.warning('仅支持 .pptx 格式')
    return false
  }
  uploading.value = true
  try {
    const res = await uploadSlide(courseId.value, file)
    ElMessage.success('上传成功，正在后台渲染...')
    await loadData()
    startPolling()
  } catch (e) {
    ElMessage.error('上传失败：' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    uploading.value = false
  }
  return false // 阻止默认上传行为
}

async function loadData() {
  try {
    const s = await getSlides(courseId.value)
    slide.value = s.data
    if (s.data?.status === 2) {
      const p = await getSlidePages(courseId.value)
      pages.value = p.data || []
      loadThumbnails()
    }
  } catch {}
}

async function loadThumbnails() {
  const cid = courseId.value
  for (const page of pages.value) {
    const blobUrl = await loadAuthImage(`/courses/${cid}/slides/pages/${page.pageNumber}/thumbnail`)
    if (blobUrl) thumbUrls.value[page.pageNumber] = blobUrl
  }
}

async function loadPreviewImage(page) {
  previewUrl.value = await loadAuthImage(`/courses/${courseId.value}/slides/pages/${page.pageNumber}/image`)
}

function selectPage(page) {
  selectedPage.value = page
  editingScript.value = page.narrationScript || ''
  loadPreviewImage(page)
}

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    await loadData()
    if (slide.value?.status !== 0 && slide.value?.status !== 1) { stopPolling(); stopProgressSim() }
  }, 3000)
  startProgressSim()
}
function stopPolling() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null } }
function startProgressSim() {
  stopProgressSim(); renderProgress.value = 0
  progressSim = setInterval(() => { renderProgress.value = Math.min(renderProgress.value + 5, 95) }, 1000)
}
function stopProgressSim() { if (progressSim) { clearInterval(progressSim); progressSim = null; renderProgress.value = 100 } }

async function handleGenerateAllAI() {
  aiGenerating.value = true
  try { await generateAllNarrations(courseId.value); ElMessage.success('批量 AI 生成已启动'); setTimeout(() => loadData(), 5000) }
  catch { ElMessage.error('操作失败') }
  finally { aiGenerating.value = false }
}

async function handleGenerateAllTTS() {
  ttsGenerating.value = true
  try { await generateAllAudio(courseId.value); ElMessage.success('批量 TTS 生成已启动'); setTimeout(() => loadData(), 5000) }
  catch { ElMessage.error('操作失败') }
  finally { ttsGenerating.value = false }
}

onMounted(() => loadData())
onUnmounted(() => { stopPolling(); stopProgressSim(); clearImageCache() })
</script>

<style scoped>
.slide-manage { min-height: 100vh; background: #f8f9fb; padding-bottom: 48px; }

/* === HEADER === */
.manage-header {
  display: flex; align-items: center; gap: 16px; padding: 16px 24px;
  background: #fff; border-bottom: 1px solid #f0f0f0; position: sticky; top: 0; z-index: 50;
  box-shadow: 0 1px 4px rgba(0,0,0,.03);
}
.back-btn { width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; cursor: pointer; color: #6b7280; transition: all .2s; flex-shrink: 0; }
.back-btn:hover { border-color: #6366f1; color: #6366f1; background: #eef2ff; }
.header-info { flex: 1; display: flex; align-items: center; gap: 12px; min-width: 0; }
.page-title { font-size: 18px; font-weight: 700; color: #1f2937; margin: 0; }
.page-subtitle { font-size: 13px; color: #9ca3af; }
.status-chip { flex-shrink: 0; }
.header-actions { display: flex; gap: 8px; flex-shrink: 0; }

/* === UPLOAD HERO === */
.upload-hero { display: flex; align-items: center; justify-content: center; min-height: 500px; padding: 40px; }
.upload-card { text-align: center; max-width: 480px; }
.upload-icon-wrapper { display: inline-flex; align-items: center; justify-content: center; width: 80px; height: 80px; border-radius: 20px; background: linear-gradient(135deg, #eef2ff 0%, #e0e7ff 100%); color: #6366f1; margin-bottom: 20px; }
.upload-title { font-size: 22px; font-weight: 700; color: #1f2937; margin: 0 0 8px; }
.upload-desc { font-size: 14px; color: #9ca3af; margin: 0 0 24px; line-height: 1.6; }
.upload-dragger { width: 100%; }
.upload-dragger :deep(.el-upload-dragger) { border: 2px dashed #d1d5db; border-radius: 16px; padding: 40px; background: #fff; transition: all .3s; }
.upload-dragger :deep(.el-upload-dragger:hover) { border-color: #6366f1; background: #fafafe; }

/* === PROCESSING === */
.processing-card { display: flex; align-items: center; justify-content: center; min-height: 400px; }
.processing-content { text-align: center; max-width: 360px; }
.pulse-ring { width: 64px; height: 64px; margin: 0 auto 24px; border-radius: 50%; border: 3px solid #e0e7ff; border-top-color: #6366f1; animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.processing-content h3 { font-size: 16px; color: #374151; margin: 0 0 16px; }
.processing-hint { font-size: 13px; color: #9ca3af; margin-top: 12px; }

/* === ERROR === */
.error-card { display: flex; align-items: center; justify-content: center; min-height: 400px; }

/* === WORKSPACE === */
.workspace { display: flex; gap: 0; padding: 0; height: calc(100vh - 73px); }

/* --- Thumbnails --- */
.thumb-grid {
  flex: 1; display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px; padding: 20px; overflow-y: auto; align-content: start;
}
.thumb-card {
  background: #fff; border-radius: 12px; border: 2px solid transparent;
  cursor: pointer; transition: all .2s; overflow: hidden; position: relative;
}
.thumb-card:hover { border-color: #c7d2fe; transform: translateY(-2px); box-shadow: 0 4px 16px rgba(0,0,0,.06); }
.thumb-card.active { border-color: #6366f1; box-shadow: 0 0 0 3px rgba(99,102,241,.12); }
.thumb-img-wrap { position: relative; aspect-ratio: 4/3; background: #f5f5f5; overflow: hidden; }
.thumb-img { width: 100%; height: 100%; object-fit: contain; display: block; }
.thumb-skeleton { width: 100%; height: 100%; background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
@keyframes shimmer { 0% { background-position: -200% 0; } 100% { background-position: 200% 0; } }
.thumb-overlay { position: absolute; inset: 0; display: flex; align-items: flex-end; justify-content: space-between; padding: 8px; pointer-events: none; }
.thumb-num { background: rgba(0,0,0,.6); color: #fff; padding: 2px 8px; border-radius: 6px; font-size: 12px; font-weight: 600; }
.compat-warn { background: rgba(245,158,11,.9); color: #fff; padding: 2px 6px; border-radius: 6px; font-size: 11px; }
.thumb-status { padding: 8px 10px; display: flex; align-items: center; gap: 6px; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot-pending { background: #d1d5db; }
.dot-script { background: #f59e0b; }
.dot-ready { background: #22c55e; }
.status-label { font-size: 12px; color: #6b7280; }

/* --- Editor --- */
.editor-panel {
  width: 380px; background: #fff; border-left: 1px solid #f0f0f0;
  display: flex; flex-direction: column; overflow-y: auto; flex-shrink: 0;
}
.editor-empty { align-items: center; justify-content: center; }
.editor-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid #f5f5f5; }
.editor-header h3 { font-size: 15px; font-weight: 600; color: #1f2937; margin: 0; }
.close-btn { width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; border: none; background: transparent; border-radius: 6px; cursor: pointer; color: #9ca3af; }
.close-btn:hover { background: #f3f4f6; color: #374151; }

.compat-alert { display: flex; align-items: flex-start; gap: 8px; padding: 10px 14px; margin: 10px 14px 0; background: #fffbeb; border: 1px solid #fde68a; border-radius: 8px; font-size: 12px; color: #92400e; line-height: 1.5; }
.compat-alert .el-icon { flex-shrink: 0; margin-top: 1px; color: #f59e0b; }

.preview-box { margin: 10px 14px; border: 1px solid #f0f0f0; border-radius: 8px; overflow: hidden; }
.preview-img { width: 100%; display: block; }

.text-section { padding: 4px 16px; }
.section-label { font-size: 11px; font-weight: 600; color: #9ca3af; text-transform: uppercase; letter-spacing: 1px; }
.extracted-text { font-size: 13px; color: #4b5563; line-height: 1.6; max-height: 100px; overflow-y: auto; padding: 8px; background: #f9fafb; border-radius: 6px; margin: 6px 0 0; }

.narration-section { padding: 12px 16px; flex: 1; display: flex; flex-direction: column; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.section-actions { display: flex; gap: 6px; }
.audio-meta { display: flex; align-items: center; gap: 4px; margin-top: 8px; font-size: 12px; color: #22c55e; }

/* === RESPONSIVE === */
@media (max-width: 1024px) {
  .workspace { flex-direction: column; height: auto; }
  .editor-panel { width: 100%; max-height: 50vh; border-left: none; border-top: 1px solid #f0f0f0; }
  .thumb-grid { grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 8px; padding: 12px; }
}
@media (max-width: 640px) {
  .manage-header { padding: 12px; flex-wrap: wrap; }
  .header-actions { width: 100%; }
  .header-actions .el-button { flex: 1; }
}
</style>
