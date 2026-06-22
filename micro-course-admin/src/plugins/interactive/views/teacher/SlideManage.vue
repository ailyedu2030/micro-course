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
        <el-upload
drag :show-file-list="false" :before-upload="handleUpload" accept=".pptx" :disabled="uploading"
          class="upload-dragger"
>
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
          <el-progress :percentage="renderProgress" :stroke-width="8" :text-inside="false" />
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
        <div
v-for="page in pages" :key="page.pageNumber"
          class="thumb-card" :class="{ active: selectedPage?.pageNumber === page.pageNumber }"
          tabindex="0" role="button"
          :aria-label="'第' + page.pageNumber + '页'"
          @click="selectPage(page)"
          @keydown.enter="selectPage(page)"
          @keydown.space.prevent="selectPage(page)"
>
          <div class="thumb-img-wrap">
            <img
v-if="thumbUrls[page.pageNumber]" :src="thumbUrls[page.pageNumber]"
              :alt="'第' + page.pageNumber + '页'" class="thumb-img" loading="lazy"
/>
            <div v-else class="thumb-skeleton" />
            <div class="thumb-overlay">
              <span class="thumb-num">{{ page.pageNumber }}</span>
              <span
v-if="page.hasAnimation || page.hasEmbeddedMedia"
                class="compat-warn" title="包含无法在播放器中展示的内容"
>⚠</span>
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
              <el-button
v-if="selectedPage.narrationScript" size="small" type="success"
                :loading="ttsLoading" @click="handleGenerateTTS" :icon="Headset"
>
                生成音频
              </el-button>
            </div>
          </div>
          <el-input
v-model="editingScript" type="textarea" :rows="10"
            placeholder="点击「AI 生成」自动生成讲述稿，或手动输入..."
            @blur="handleSaveScript" resize="none"
/>
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
const uploadProgress = ref(0)
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
  uploadProgress.value = 0
  try {
    await uploadSlide(courseId.value, file, (e) => {
      uploadProgress.value = Math.round((e.loaded / e.total) * 100)
    })
    ElMessage.success('上传成功，正在后台渲染...')
    await loadData()
    startPolling()
  } catch (e) {
    ElMessage.error('上传失败')
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
.slide-manage { min-height: 100dvh; background: var(--bg-page); padding-bottom: var(--space-12); }

/* === HEADER === */
.manage-header {
  display: flex; align-items: center; gap: var(--space-4); padding: var(--space-4) var(--space-6);
  background: var(--surface-card); border-bottom: 1px solid var(--border-subtle);
  position: sticky; top: 0; z-index: 50;
  box-shadow: var(--shadow-xs);
}
.back-btn {
  width: 36px; height: 36px; display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--border-default); border-radius: var(--radius-md);
  background: var(--surface-card); cursor: pointer; color: var(--text-secondary);
  transition: all var(--duration-fast) var(--ease-out); flex-shrink: 0;
}
.back-btn:hover { border-color: var(--role-primary); color: var(--role-primary); background: var(--role-primary-tint); }
.header-info { flex: 1; display: flex; align-items: center; gap: var(--space-3); min-width: 0; }
.page-title { font-size: var(--text-lg); font-weight: var(--weight-semibold); color: var(--text-primary); margin: 0; }
.page-subtitle { font-size: var(--text-xs); color: var(--text-tertiary); }
.status-chip { flex-shrink: 0; }
.header-actions { display: flex; gap: var(--space-2); flex-shrink: 0; }

/* === UPLOAD HERO === */
.upload-hero { display: flex; align-items: center; justify-content: center; min-height: 500px; padding: var(--space-10); }
.upload-card { text-align: center; max-width: 480px; }
.upload-icon-wrapper {
  display: inline-flex; align-items: center; justify-content: center;
  width: 80px; height: 80px; border-radius: var(--radius-xl);
  background: var(--role-primary-tint); color: var(--role-primary); margin-bottom: var(--space-5);
}
.upload-title { font-size: var(--text-2xl); font-weight: var(--weight-bold); color: var(--text-primary); margin: 0 0 var(--space-2); }
.upload-desc { font-size: var(--text-sm); color: var(--text-tertiary); margin: 0 0 var(--space-6); line-height: 1.6; }
.upload-dragger { width: 100%; }
.upload-dragger :deep(.el-upload-dragger) {
  border: 2px dashed var(--border-default); border-radius: var(--radius-xl);
  padding: var(--space-10); background: var(--surface-card); transition: all var(--duration-normal) var(--ease-out);
}
.upload-dragger :deep(.el-upload-dragger:hover) {
  border-color: var(--role-primary); background: var(--role-primary-tint);
}

/* === PROCESSING === */
.processing-card { display: flex; align-items: center; justify-content: center; min-height: 400px; }
.processing-content { text-align: center; max-width: 360px; }
.pulse-ring {
  width: 64px; height: 64px; margin: 0 auto var(--space-6);
  border-radius: 50%; border: 3px solid var(--role-primary-tint);
  border-top-color: var(--role-primary); animation: spin 1s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.processing-content h3 { font-size: var(--text-base); color: var(--text-primary); margin: 0 0 var(--space-4); }
.processing-hint { font-size: var(--text-xs); color: var(--text-tertiary); margin-top: var(--space-3); }

/* === ERROR === */
.error-card { display: flex; align-items: center; justify-content: center; min-height: 400px; }

/* === WORKSPACE === */
.workspace { display: flex; gap: 0; padding: 0; height: calc(100dvh - 73px); }

/* --- Thumbnails --- */
.thumb-grid {
  flex: 1; display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--space-3); padding: var(--space-5); overflow-y: auto; align-content: start;
}
.thumb-card {
  background: var(--surface-card); border-radius: var(--radius-lg); border: 2px solid transparent;
  cursor: pointer; transition: all var(--duration-fast) var(--ease-out);
  overflow: hidden; position: relative;
}
.thumb-card:hover {
  border-color: var(--role-primary-light-5); transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}
.thumb-card.active {
  border-color: var(--role-primary); box-shadow: 0 0 0 3px var(--role-primary-light-8);
}
.thumb-img-wrap { position: relative; aspect-ratio: 4/3; background: var(--bg-page); overflow: hidden; }
.thumb-img { width: 100%; height: 100%; object-fit: contain; display: block; }
.thumb-skeleton {
  width: 100%; height: 100%;
  background: linear-gradient(90deg, var(--bg-page) 25%, var(--border-subtle) 50%, var(--bg-page) 75%);
  background-size: 200% 100%; animation: shimmer 1.5s infinite;
}
@keyframes shimmer { 0% { background-position: -200% 0; } 100% { background-position: 200% 0; } }
.thumb-overlay { position: absolute; inset: 0; display: flex; align-items: flex-end; justify-content: space-between; padding: var(--space-2); pointer-events: none; }
.thumb-num { background: var(--overlay-dark); color: #fff; padding: var(--space-0) var(--space-2); border-radius: var(--radius-sm); font-size: var(--text-xs); font-weight: var(--weight-semibold); }
.compat-warn { background: var(--color-warning); color: #fff; padding: var(--space-0) var(--space-2); border-radius: var(--radius-sm); font-size: var(--text-xs); }
.thumb-status { padding: var(--space-2) var(--space-2-5); display: flex; align-items: center; gap: var(--space-1-5); }
.status-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.dot-pending { background: var(--color-info); }
.dot-script { background: var(--color-warning); }
.dot-ready { background: var(--color-success); }
.status-label { font-size: var(--text-xs); color: var(--text-secondary); }

/* --- Editor --- */
.editor-panel {
  width: 380px; background: var(--surface-card); border-left: 1px solid var(--border-subtle);
  display: flex; flex-direction: column; overflow-y: auto; flex-shrink: 0;
}
.editor-empty { align-items: center; justify-content: center; }
.editor-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--space-3-5) var(--space-4); border-bottom: 1px solid var(--border-faint);
}
.editor-header h3 { font-size: var(--text-sm); font-weight: var(--weight-semibold); color: var(--text-primary); margin: 0; }
.close-btn {
  width: 28px; height: 28px; display: flex; align-items: center; justify-content: center;
  border: none; background: transparent; border-radius: var(--radius-sm);
  cursor: pointer; color: var(--text-tertiary);
}
.close-btn:hover { background: var(--bg-page); color: var(--text-primary); }

.compat-alert {
  display: flex; align-items: flex-start; gap: var(--space-2); padding: var(--space-2-5) var(--space-3-5);
  margin: var(--space-2-5) var(--space-3-5) 0;
  background: var(--color-warning-light-9); border: 1px solid var(--color-warning-light-6);
  border-radius: var(--radius-md); font-size: var(--text-xs); color: var(--color-warning-dark); line-height: 1.5;
}
.compat-alert .el-icon { flex-shrink: 0; margin-top: 1px; color: var(--color-warning); }

.preview-box { margin: var(--space-2-5) var(--space-3-5); border: 1px solid var(--border-subtle); border-radius: var(--radius-md); overflow: hidden; }
.preview-img { width: 100%; display: block; }

.text-section { padding: var(--space-1) var(--space-4); }
.section-label { font-size: var(--text-xs); font-weight: var(--weight-semibold); color: var(--text-tertiary); text-transform: uppercase; letter-spacing: var(--tracking-wide); }
.extracted-text {
  font-size: var(--text-xs); color: var(--text-secondary); line-height: 1.6;
  max-height: 100px; overflow-y: auto; padding: var(--space-2);
  background: var(--bg-page); border-radius: var(--radius-sm); margin: var(--space-1-5) 0 0;
}

.narration-section { padding: var(--space-3) var(--space-4); flex: 1; display: flex; flex-direction: column; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-2); }
.section-actions { display: flex; gap: var(--space-1-5); }
.audio-meta { display: flex; align-items: center; gap: var(--space-1); margin-top: var(--space-2); font-size: var(--text-xs); color: var(--color-success); }

/* === RESPONSIVE === */
@media (max-width: 1024px) {
  .workspace { flex-direction: column; height: auto; }
  .editor-panel { width: 100%; max-height: 50vh; border-left: none; border-top: 1px solid var(--border-subtle); }
  .thumb-grid { grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: var(--space-2); padding: var(--space-3); }
}
@media (max-width: 640px) {
  .manage-header { padding: var(--space-3); flex-wrap: wrap; }
  .header-actions { width: 100%; }
  .header-actions .el-button { flex: 1; }
}
</style>
