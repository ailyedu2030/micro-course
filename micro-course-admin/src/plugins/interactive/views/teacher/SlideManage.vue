<template>
  <div class="slide-manage fade-in">
    <!-- Breadcrumb -->
    <div class="breadcrumb-bar">
      <el-breadcrumb separator="→">
        <el-breadcrumb-item :to="{ path: userRole === 'TEACHER' ? '/teacher/courses' : '/courses' }">课程管理</el-breadcrumb-item>
        <el-breadcrumb-item v-if="courseTitle" :to="{ path: `/courses/${courseId}` }">{{ courseTitle }}</el-breadcrumb-item>
        <el-breadcrumb-item>幻灯片管理</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- Page Header -->
    <header class="manage-header">
      <div class="header-info">
        <h1 class="page-title">幻灯片管理</h1>
        <div v-if="courseTitle || chapterTitle" class="context-tags">
          <el-tag v-if="courseTitle" type="primary" size="small" effect="plain">{{ courseTitle }}</el-tag>
          <el-tag v-if="chapterTitle" type="success" size="small" effect="plain">{{ chapterTitle }}</el-tag>
        </div>
        <span class="page-subtitle" v-if="slide">{{ pages.length }} 页 · {{ slide.fileName }}</span>
        <el-tag v-if="statusTag" :type="statusTag.type" size="small" effect="plain" class="status-chip">
          {{ statusTag.text }}
        </el-tag>
      </div>
      <div class="header-actions" v-if="slide?.status === 2 && !batchMode">
        <el-button type="primary" plain @click="showPreview = true" :icon="View">预览</el-button>
        <el-upload :show-file-list="false" :before-upload="handleUpload" accept=".pptx,.html,.htm" class="replace-upload">
          <el-button :icon="UploadFilled">替换</el-button>
        </el-upload>
        <el-dropdown trigger="click" class="header-dropdown">
          <el-button :icon="MoreFilled">更多</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleDownloadSlide" :icon="Download">下载 PPT</el-dropdown-item>
              <el-dropdown-item @click="toggleBatchMode" :icon="Select">批量操作</el-dropdown-item>
              <el-dropdown-item divided :icon="MagicStick" :disabled="aiGenerating" @click="handleGenerateAllAI">批量 AI 生成</el-dropdown-item>
              <el-dropdown-item :icon="Headset" :disabled="ttsGenerating" @click="handleGenerateAllTTS">批量 TTS 配音</el-dropdown-item>
              <el-dropdown-item divided :icon="Delete" @click="handleDeleteSlide" style="color:var(--el-color-danger)">删除课件</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <div class="header-actions" v-if="batchMode">
        <el-button :type="batchMode ? 'primary' : 'default'" @click="toggleBatchMode">退出批量</el-button>
      </div>
      <div v-if="batchMode && selectedBatch.length > 0" class="batch-bar">
        <span class="batch-count">已选 {{ selectedBatch.length }} 页</span>
        <el-button size="small" :loading="aiLoading" @click="handleBatchAI" :icon="MagicStick">
          批量 AI 生成
        </el-button>
        <el-button size="small" type="success" :loading="ttsLoading" @click="handleBatchTTS" :icon="Headset">
          批量生成音频
        </el-button>
        <el-button size="small" type="danger" plain @click="handleBatchDelete" :icon="Delete">
          批量删除
        </el-button>
        <el-button size="small" @click="selectedBatch = []">取消选择</el-button>
      </div>
    </header>

    <!-- Upload Zone -->
    <section v-if="!slide" class="upload-hero">
      <div class="create-guide">
        <div class="guide-step active">
          <span class="step-num">1</span>
          <span class="step-text">上传课件（.pptx / .html）</span>
        </div>
        <div class="guide-step">
          <span class="step-num">2</span>
          <span class="step-text">等待自动渲染</span>
        </div>
        <div class="guide-step">
          <span class="step-num">3</span>
          <span class="step-text">AI 生成讲述稿</span>
        </div>
        <div class="guide-step">
          <span class="step-num">4</span>
          <span class="step-text">TTS 配音</span>
        </div>
        <div class="guide-step">
          <span class="step-num">5</span>
          <span class="step-text">预览发布</span>
        </div>
      </div>
      <div class="upload-card">
        <div class="upload-icon-wrapper">
          <el-icon :size="40"><UploadFilled /></el-icon>
        </div>
        <h2 class="upload-title">上传课件</h2>
        <p class="upload-desc">支持 .pptx (最大 50MB) 和 .html (最大 5MB) 格式。PPT 将自动渲染为高清幻灯片，HTML 可在线直接播放</p>
        <el-upload
drag :show-file-list="false" :before-upload="handleUpload" accept=".pptx,.html,.htm" :disabled="uploading"
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
          <el-upload :show-file-list="false" :before-upload="handleUpload" accept=".pptx,.html,.htm">
            <el-button type="primary" :icon="Refresh">重新上传</el-button>
          </el-upload>
        </template>
      </el-result>
    </section>

    <!-- Main Workspace -->
    <section v-else-if="slide.status === 2" class="workspace">
      <!-- Progress Guide -->
      <div class="workspace-guide">
        <div class="guide-step done">
          <span class="step-num">✓</span>
          <span class="step-text">已上传</span>
        </div>
        <div class="guide-step done">
          <span class="step-num">✓</span>
          <span class="step-text">已渲染</span>
        </div>
        <div class="guide-step" :class="progressStep === 3 ? 'active' : ''">
          <span class="step-num">{{ narrationDoneCount }}/{{ pages.length }}</span>
          <span class="step-text">讲述稿</span>
        </div>
        <div class="guide-step" :class="progressStep === 4 ? 'active' : ''">
          <span class="step-num">{{ audioDoneCount }}/{{ pages.length }}</span>
          <span class="step-text">音频</span>
        </div>
        <div class="guide-step" :class="progressStep === 5 ? 'active done' : ''">
          <span class="step-num">✓</span>
          <span class="step-text">发布</span>
        </div>
      </div>
      <div class="workspace-body">
        <!-- Thumbnail Grid -->
        <div class="thumb-grid">
        <div
v-for="page in pages" :key="page.pageNumber"
          class="thumb-card" :class="{
            active: selectedPage?.pageNumber === page.pageNumber,
            'batch-selected': batchSelected.has(page.pageNumber)
          }"
          :data-page="page.pageNumber"
          tabindex="0" role="button"
          :aria-label="'第' + page.pageNumber + '页'"
          @click="batchMode ? toggleBatchSelect(page) : selectPage(page)"
          @keydown.enter="batchMode ? toggleBatchSelect(page) : selectPage(page)"
          @keydown.space.prevent="batchMode ? toggleBatchSelect(page) : selectPage(page)"
>
          <div class="batch-checkbox" v-if="batchMode" @click.stop="toggleBatchSelect(page)">
            <el-checkbox :model-value="batchSelected.has(page.pageNumber)" />
          </div>
          <div class="thumb-img-wrap">
            <img
v-if="thumbUrls[page.pageNumber]" :src="thumbUrls[page.pageNumber]"
              :alt="'第' + page.pageNumber + '页'" class="thumb-img" loading="lazy"
/>
            <div v-else class="thumb-skeleton" />
            <div class="thumb-overlay">
              <span class="thumb-num">{{ page.pageNumber }}</span>
              <span v-if="page.contentType === 'HTML_DIRECT'" class="html-badge" title="HTML 课时">HTML</span>
              <button class="thumb-del-btn" @click.stop="handleDeletePage(page)" title="删除此页" aria-label="删除此页">
                <el-icon :size="12"><Delete /></el-icon>
              </button>
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
              <el-button size="small" @click="showScriptDialog = true" :icon="Edit">编辑</el-button>
            </div>
          </div>
          <div class="script-preview" @click="showScriptDialog = true">
            <p v-if="editingScript" class="script-text">{{ editingScript }}</p>
            <p v-else class="script-placeholder">点击「AI 生成」自动生成讲述稿，或点击编辑手动输入...</p>
          </div>
          <div v-if="audioInfo" class="audio-meta">
            <el-icon :size="14"><Headset /></el-icon>
            <span>{{ audioInfo }}</span>
          </div>
        </div>
      </aside>

      <aside v-else class="editor-panel editor-empty">
        <el-empty description="点击缩略图开始编辑" :image-size="80" />
      </aside>
      </div><!-- end workspace-body -->
    </section>

    <!-- Preview Dialog -->
    <el-dialog v-model="showPreview" title="学生视角预览" fullscreen :destroy-on-close="true">
      <SlidePreview :course-id="courseId" :section-id="sectionId" v-if="showPreview" @close="showPreview = false" />
    </el-dialog>

    <!-- Narration Script Dialog -->
    <el-dialog v-model="showScriptDialog" title="编辑讲述稿" width="70%" :close-on-click-modal="false" destroy-on-close>
      <div class="script-dialog-body">
        <div v-if="selectedPage?.extractedText" class="extracted-ref">
          <span class="ref-label">页面提取文本参考</span>
          <p class="ref-text">{{ selectedPage.extractedText }}</p>
        </div>
        <el-input
          v-model="editingScript" type="textarea" :rows="16"
          placeholder="点击「AI 生成」自动生成讲述稿，或手动输入..."
          resize="vertical"
        />
      </div>
      <template #footer>
        <div class="script-dialog-footer">
          <div class="footer-left">
            <el-button size="small" :loading="aiLoading" @click="handleGenerateAI" :icon="MagicStick">AI 生成</el-button>
            <el-button v-if="editingScript" size="small" type="success" :loading="ttsLoading" @click="handleGenerateTTS" :icon="Headset">生成音频</el-button>
          </div>
          <div class="footer-right">
            <el-button @click="showScriptDialog = false">取消</el-button>
            <el-button type="primary" @click="handleSaveScript(); showScriptDialog = false">保存</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCourseById } from '@/api/course'
import { getChapterById } from '@/api/chapter'
import { useUserStore } from '@/store/user'
import { UploadFilled, MagicStick, Headset, View, Close, WarningFilled, Refresh, Delete, Download, MoreFilled, Select, Edit } from '@element-plus/icons-vue'
import { uploadSlide, getSlides, getSlidePages, getSlidePage, generateNarration, updateNarration, generateAllNarrations, generateAudio, generateAllAudio, deleteSlide, deleteSlidePage, reorderSlidePages, downloadOriginalSlide } from '@/plugins/interactive/api/slide'
import SlidePreview from '@/plugins/interactive/components/SlidePreview.vue'
import { loadAuthResource, clearImageCache } from '@/utils/authImage'
import Sortable from 'sortablejs'

const route = useRoute()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)
const courseId = computed(() => route.params.courseId)
const chapterId = computed(() => route.params.chapterId || null)
const sectionId = computed(() => route.query.sectionId || null)
const courseTitle = ref('')
const chapterTitle = ref('')
const slide = ref(null)
const pages = ref([])
const selectedPage = ref(null)
const editingScript = ref('')
const uploading = ref(false)
const sorting = ref(false)
const uploadProgress = ref(0)
const aiLoading = ref(false)
const aiGenerating = ref(false)
const ttsLoading = ref(false)
const ttsGenerating = ref(false)
const showPreview = ref(false)
const showScriptDialog = ref(false)
const thumbUrls = ref({})
const previewUrl = ref('')
const renderProgress = ref(0)
const renderProgressLabel = ref('')
let pollTimer = null
const progressSim = null
let sortableInstance = null

const narrationDoneCount = computed(() => pages.value.filter(p => p.narrationStatus && p.narrationStatus !== 'PENDING').length)
const audioDoneCount = computed(() => pages.value.filter(p => p.narrationStatus === 'AUDIO_READY').length)
const progressStep = computed(() => {
  if (!slide.value || slide.value.status !== 2) return 1
  const nDone = narrationDoneCount.value
  const aDone = audioDoneCount.value
  const total = pages.value.length
  if (total === 0) return 2
  if (aDone === total) return 5
  if (nDone === total) return 4
  if (nDone > 0) return 3
  return 2
})

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

const batchMode = ref(false)
const selectedBatch = ref([])
const batchSelected = computed(() => new Set(selectedBatch.value.map(p => p.pageNumber)))

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) selectedBatch.value = []
}

function toggleBatchSelect(page) {
  const idx = selectedBatch.value.findIndex(p => p.pageNumber === page.pageNumber)
  if (idx >= 0) {
    selectedBatch.value.splice(idx, 1)
  } else {
    selectedBatch.value.push(page)
  }
}

async function handleBatchAI() {
  const failedPages = []
  let successCount = 0
  for (const page of selectedBatch.value) {
    if (page.narrationStatus !== 'AI_GENERATED' && page.narrationStatus !== 'AUDIO_READY') {
      try {
        await generateNarration(courseId.value, page.pageNumber, page.sectionId || null)
        page.narrationStatus = 'AI_GENERATED'
        successCount++
      } catch (e) {
        failedPages.push({ page: page.pageNumber, error: e?.response?.data?.message || e?.message || '未知错误' })
      }
    }
  }
  if (failedPages.length > 0) {
    ElMessage.warning(`AI 生成完成：成功 ${successCount} 页，失败 ${failedPages.length} 页（${failedPages.map(p => `第${p.page}页: ${p.error}`).join('；')}）`)
  } else {
    ElMessage.success(`批量 AI 生成完成，成功处理 ${successCount} 页`)
  }
}

async function handleBatchTTS() {
  const failedPages = []
  let successCount = 0
  for (const page of selectedBatch.value) {
    if (page.narrationScript && page.narrationStatus !== 'AUDIO_READY') {
      try {
        const res = await generateAudio(courseId.value, page.pageNumber)
        Object.assign(page, res.data)
        successCount++
      } catch (e) {
        failedPages.push({ page: page.pageNumber, error: e?.response?.data?.message || e?.message || '未知错误' })
      }
    }
  }
  if (failedPages.length > 0) {
    ElMessage.warning(`TTS 生成完成：成功 ${successCount} 页，失败 ${failedPages.length} 页（${failedPages.map(p => `第${p.page}页: ${p.error}`).join('；')}）`)
  } else {
    ElMessage.success(`批量 TTS 生成完成，成功处理 ${successCount} 页`)
  }
}

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedBatch.value.length} 页吗？`, '批量删除', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
    for (const page of selectedBatch.value) {
      try {
        await deleteSlidePage(courseId.value, page.pageNumber)
        const idx = pages.value.findIndex(p => p.pageNumber === page.pageNumber)
        if (idx >= 0) pages.value.splice(idx, 1)
      } catch { /* skip failed pages */ }
    }
    selectedBatch.value = []
    ElMessage.success('批量删除完成')
  } catch { /* cancelled */ }
}

function statusDotClass(s) {
  if (s === 'AUDIO_READY') return 'dot-ready'
  if (s === 'AI_GENERATED' || s === 'TEACHER_EDITED') return 'dot-script'
  return 'dot-pending'
}

async function handleSaveScript() {
  if (!selectedPage.value) return
  try {
    await updateNarration(courseId.value, selectedPage.value.pageNumber, editingScript.value, selectedPage.value.sectionId || null)
    selectedPage.value.narrationScript = editingScript.value
    selectedPage.value.narrationStatus = 'TEACHER_EDITED'
    ElMessage.success('讲述稿已保存')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存讲述稿失败')
  }
}

async function handleGenerateAI() {
  if (!selectedPage.value) return
  aiLoading.value = true
  try {
    await generateNarration(courseId.value, selectedPage.value.pageNumber, selectedPage.value.sectionId || null)
    ElMessage.success('AI 生成完成')
    await loadData()
    selectPage(pages.value.find(p => p.pageNumber === selectedPage.value.pageNumber) || selectedPage.value)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || 'AI 生成失败')
  } finally {
    aiLoading.value = false
  }
}

async function handleGenerateTTS() {
  if (!selectedPage.value || !selectedPage.value.narrationScript) {
    ElMessage.warning('请先生成或输入讲述稿')
    return
  }
  ttsLoading.value = true
  try {
    await generateAudio(courseId.value, selectedPage.value.pageNumber)
    ElMessage.success('音频生成已启动')
    await loadData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '音频生成失败')
  } finally {
    ttsLoading.value = false
  }
}

async function handleUpload(file) {
  // 防并发：uploadProgress 仅单值，并发会互相覆盖
  if (uploading.value) {
    ElMessage.warning('已有课件正在上传，请稍候')
    return false
  }
  // 前端校验：文件大小和类型
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.warning('文件超过 50MB 限制')
    return false
  }
  // 修复 Hermes P0#4：接受 .pptx 和 .html/.htm 两种格式
  // 后端 /upload 端点会自动按扩展名分支（PPTX 走渲染，HTML 走消毒入库）
  const lowerName = file.name.toLowerCase()
  const isValidType = lowerName.endsWith('.pptx')
    || lowerName.endsWith('.html')
    || lowerName.endsWith('.htm')
  if (!isValidType) {
    ElMessage.warning('仅支持 .pptx / .html / .htm 格式')
    return false
  }
  // HTML 文件单独校验 5MB（PPTX 上限 50MB）
  if ((lowerName.endsWith('.html') || lowerName.endsWith('.htm'))
      && file.size > 5 * 1024 * 1024) {
    ElMessage.warning('HTML 文件不能超过 5MB')
    return false
  }
  uploading.value = true
  uploadProgress.value = 0
  try {
    await uploadSlide(courseId.value, file, (e) => {
      uploadProgress.value = Math.round((e.loaded / e.total) * 100)
    }, Number(chapterId.value) || null)
    ElMessage.success('上传成功，正在后台渲染...')
    await loadData()
    startPolling()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
  return false // 阻止默认上传行为
}

async function loadData() {
  try {
    const s = await getSlides(courseId.value, chapterId.value)
    slide.value = s.data
    if (s.data?.status === 2) {
      const p = await getSlidePages(courseId.value, chapterId.value, sectionId.value)
      pages.value = p.data || []
      await loadThumbnails()
    }
    } catch (e) {
      console.warn('[SlideManage] loadData failed', e?.message)
      ElMessage.warning('课件数据加载失败')
    }
}

async function loadThumbnails() {
  const cid = courseId.value
  const CONCURRENCY = 6
  const results = {}
  for (let i = 0; i < pages.value.length; i += CONCURRENCY) {
    const batch = pages.value.slice(i, i + CONCURRENCY)
    const urls = await Promise.all(batch.map(p =>
      loadAuthResource(`/courses/${cid}/slides/pages/${p.pageNumber}/thumbnail`)
    ))
    batch.forEach((p, j) => { if (urls[j]) results[p.pageNumber] = urls[j] })
  }
  Object.assign(thumbUrls.value, results)
  await nextTick()
  initPageSort()
}

function initPageSort() {
  const el = document.querySelector('.thumb-grid')
  if (!el || sortableInstance) return
  sortableInstance = Sortable.create(el, {
    animation: 200,
    onStart: () => { sorting.value = true },
    onEnd: async (evt) => {
      const items = Array.from(el.children).map((child, idx) => {
        const num = parseInt(child.getAttribute('data-page') || '0', 10)
        return num
      })
      // 旧顺序 -> 新顺序: 只更新被拖动的元素
      const oldNum = parseInt(evt.item.getAttribute('data-page') || '0', 10)
      const newPos = evt.newIndex + 1
      if (oldNum === newPos) { sorting.value = false; return }
      // 批量更新排序
      const order = items.map((pageNum, idx) => ({
        pageNumber: pageNum,
        newPageNumber: idx + 1
      }))
      try {
        await reorderSlidePages(courseId.value, order)
        await loadData()
      } catch {
        ElMessage.error('排序保存失败')
      } finally {
        sorting.value = false
      }
    }
  })
}

async function loadPreviewImage(page) {
  previewUrl.value = await loadAuthResource(`/courses/${courseId.value}/slides/pages/${page.pageNumber}/image`)
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
  startProgressTracking()
}
function stopPolling() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null } }
function startProgressTracking() {
  stopProgressTracking()
  // 将模拟进度条换为基于实际状态轮询的真实进度指示
  // PPT 渲染/转码是异步操作，服务端不返回百分比，而是多阶段状态
  // 这里显示"处理中…"等待实际状态更新
  renderProgress.value = 0
  renderProgressLabel.value = '正在处理…'
}
function stopProgressTracking() {
  stopProgressSim()
  renderProgress.value = 100
  renderProgressLabel.value = ''
}
// 保留旧函数兼容调用，但不再产生虚假百分比
function startProgressSim() { renderProgress.value = 0 }
function stopProgressSim() { renderProgress.value = 100; renderProgressLabel.value = '' }

async function handleGenerateAllAI() {
  aiGenerating.value = true
  try { await generateAllNarrations(courseId.value); ElMessage.success('批量 AI 生成已启动'); setTimeout(() => loadData(), 5000) }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
  finally { aiGenerating.value = false }
}

async function handleGenerateAllTTS() {
  ttsGenerating.value = true
  try { await generateAllAudio(courseId.value); ElMessage.success('批量 TTS 生成已启动'); setTimeout(() => loadData(), 5000) }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
  finally { ttsGenerating.value = false }
}

async function handleDeleteSlide() {
  try {
    await ElMessageBox.confirm('确定删除整个课件？此操作不可恢复。', '确认删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await deleteSlide(courseId.value, chapterId.value)
    ElMessage.success('课件已删除')
    slide.value = null
    pages.value = []
    selectedPage.value = null
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

async function handleDeletePage(page) {
  try {
    await ElMessageBox.confirm(`确定删除第 ${page.pageNumber} 页？此操作不可恢复。`, '确认删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await deleteSlidePage(courseId.value, page.pageNumber)
    ElMessage.success(`第 ${page.pageNumber} 页已删除`)
    pages.value = pages.value.filter(p => p.pageNumber !== page.pageNumber)
    if (selectedPage.value?.pageNumber === page.pageNumber) {
      selectedPage.value = pages.value[0] || null
      editingScript.value = selectedPage.value?.narrationScript || ''
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

async function handleDownloadSlide() {
  try {
    const res = await downloadOriginalSlide(courseId.value)
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.presentationml.presentation' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = slide.value?.fileName || 'slide.pptx'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载失败')
  }
}

async function loadContext() {
  if (courseId.value) {
    try { const r = await getCourseById(courseId.value); courseTitle.value = r.data?.title || '' } catch {}
  }
  if (chapterId.value) {
    try { const r = await getChapterById(chapterId.value); chapterTitle.value = r.data?.title || '' } catch {}
  }
}
onMounted(async () => {
  await loadContext()
  await loadData()
})
onUnmounted(() => { stopPolling(); stopProgressSim(); clearImageCache(); if (sortableInstance) { sortableInstance.destroy(); sortableInstance = null } })
</script>

<style scoped>
.slide-manage { min-height: 100dvh; background: var(--el-bg-color-page); padding-bottom: var(--space-12); }

/* === BREADCRUMB === */
.breadcrumb-bar { padding: 8px 24px 0; background: var(--el-bg-color); border-bottom: 1px solid var(--el-border-color-light); }

/* === HEADER === */
.manage-header {
  display: flex; align-items: center; gap: var(--space-4); padding: var(--space-2-5) var(--space-6);
  background: var(--el-fill-color-blank);
}
.header-info { flex: 1; display: flex; align-items: center; gap: var(--space-3); min-width: 0; }
.page-title { font-size: var(--text-lg); font-weight: var(--weight-semibold); color: var(--el-text-color-primary); margin: 0; }
.page-subtitle { font-size: var(--text-xs); color: var(--el-text-color-placeholder); }
.context-tags { display: flex; gap: 6px; margin: 4px 0; }
.status-chip { flex-shrink: 0; }
.header-actions { display: flex; gap: var(--space-2); flex-shrink: 0; }
.header-actions .el-button + .el-button { margin-left: 0; }

/* === UPLOAD HERO === */
.upload-hero { display: flex; align-items: center; justify-content: center; min-height: 500px; padding: var(--space-10); }
.upload-card { text-align: center; max-width: 480px; }
.upload-icon-wrapper {
  display: inline-flex; align-items: center; justify-content: center;
  width: 80px; height: 80px; border-radius: var(--radius-xl);
  background: var(--el-color-primary-light-9); color: var(--el-color-primary); margin-bottom: var(--space-5);
}
.upload-title { font-size: var(--text-2xl); font-weight: var(--weight-bold); color: var(--el-text-color-primary); margin: 0 0 var(--space-2); }
.upload-desc { font-size: var(--text-sm); color: var(--el-text-color-placeholder); margin: 0 0 var(--space-6); line-height: 1.6; }
.upload-dragger { width: 100%; }
.upload-dragger :deep(.el-upload-dragger) {
  border: 2px dashed var(--el-border-color); border-radius: var(--radius-xl);
  padding: var(--space-10); background: var(--el-fill-color-blank); transition: all var(--duration-normal) var(--ease-out);
}
.upload-dragger :deep(.el-upload-dragger:hover) {
  border-color: var(--el-color-primary); background: var(--el-color-primary-light-9);
}

/* === PROCESSING === */
.processing-card { display: flex; align-items: center; justify-content: center; min-height: 400px; }
.processing-content { text-align: center; max-width: 360px; }
.pulse-ring {
  width: 64px; height: 64px; margin: 0 auto var(--space-6);
  border-radius: 50%; border: 3px solid var(--el-color-primary-light-8);
  border-top-color: var(--el-color-primary); animation: spin 1s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.processing-content h3 { font-size: var(--text-base); color: var(--el-text-color-primary); margin: 0 0 var(--space-4); }
.processing-hint { font-size: var(--text-xs); color: var(--el-text-color-placeholder); margin-top: var(--space-3); }

/* === ERROR === */
.error-card { display: flex; align-items: center; justify-content: center; min-height: 400px; }

/* === WORKSPACE === */
.workspace { display: flex; gap: 0; padding: 0; height: calc(100dvh - 73px); flex-direction: column; }
.workspace-guide {
  display: flex; justify-content: center; gap: 6px; padding: 8px;
  background: var(--el-bg-color); border-bottom: 1px solid var(--el-border-color-light);
  flex-shrink: 0; z-index: 5;
}
/* workspace body = thumbnails + editor side by side */
.workspace-body { display: flex; flex: 1; overflow: hidden; }

/* --- Thumbnails --- */
.thumb-grid {
  flex: 1; display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--space-3); padding: var(--space-5); overflow-y: auto; align-content: start;
}
.thumb-card {
  background: var(--el-fill-color-blank); border-radius: var(--radius-lg); border: 2px solid transparent;
  cursor: pointer; transition: all var(--duration-fast) var(--ease-out);
  overflow: hidden; position: relative;
}
.thumb-card:hover {
  border-color: var(--el-color-primary-light-5); transform: translateY(-2px);
  box-shadow: var(--el-box-shadow-light);
}
.thumb-card.active {
  border-color: var(--el-color-primary); box-shadow: 0 0 0 3px var(--el-color-primary-light-8);
}
.thumb-img-wrap { position: relative; aspect-ratio: 4/3; background: var(--el-bg-color-page); overflow: hidden; }
.thumb-skeleton {
  width: 100%; height: 100%;
  background: linear-gradient(90deg, var(--el-bg-color-page) 25%, var(--el-border-color-extra-light) 50%, var(--el-bg-color-page) 75%);
  background-size: 200% 100%; animation: shimmer 1.5s infinite;
}
.thumb-overlay { position: absolute; inset: 0; display: flex; align-items: flex-start; justify-content: space-between; padding: var(--space-2); pointer-events: none; }
.thumb-num { background: rgba(0,0,0,0.55); color: #fff; padding: var(--space-0) var(--space-2); border-radius: var(--radius-sm); font-size: var(--text-xs); font-weight: var(--weight-semibold); pointer-events: auto; }
.thumb-del-btn {
  width: 24px; height: 24px; display: flex; align-items: center; justify-content: center;
  border: none; border-radius: var(--radius-sm); background: rgba(220,38,38,0.8);
  color: #fff; cursor: pointer; opacity: 0; transition: opacity var(--duration-fast) var(--ease-out);
  pointer-events: auto;
}
.thumb-card:hover .thumb-del-btn { opacity: 1; }
.thumb-del-btn:hover { background: rgb(220,38,38); }
.compat-warn { background: var(--el-color-warning); color: #fff; padding: var(--space-0) var(--space-2); border-radius: var(--radius-sm); font-size: var(--text-xs); pointer-events: auto; }
.thumb-status { padding: var(--space-2) var(--space-2-5); display: flex; align-items: center; gap: var(--space-1-5); }
.dot-pending { background: var(--el-color-info); }
.dot-script { background: var(--el-color-warning); }
.dot-ready { background: var(--el-color-success); }
.status-label { font-size: var(--text-xs); color: var(--el-text-color-secondary); }

/* --- Editor --- */
.editor-panel {
  width: 360px; min-width: 280px; background: var(--el-fill-color-blank); border-left: 1px solid var(--el-border-color-light);
  display: flex; flex-direction: column; overflow-y: auto; flex-shrink: 0;
}
.editor-empty { align-items: center; justify-content: center; }
.editor-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--space-3-5) var(--space-4); border-bottom: 1px solid var(--el-border-color-lighter);
}
.editor-header h3 { font-size: var(--text-sm); font-weight: var(--weight-semibold); color: var(--el-text-color-primary); margin: 0; }
.close-btn {
  width: 28px; height: 28px; display: flex; align-items: center; justify-content: center;
  border: none; background: transparent; border-radius: var(--radius-sm);
  cursor: pointer; color: var(--el-text-color-placeholder);
}
.close-btn:hover { background: var(--el-bg-color-page); color: var(--el-text-color-primary); }

.compat-alert {
  display: flex; align-items: flex-start; gap: var(--space-2); padding: var(--space-2-5) var(--space-3-5);
  margin: var(--space-2-5) var(--space-3-5) 0;
  background: var(--el-color-warning-light-9); border: 1px solid var(--el-color-warning-light-7);
  border-radius: var(--radius-md); font-size: var(--text-xs); color: var(--el-color-warning); line-height: 1.5;
}
.compat-alert .el-icon { flex-shrink: 0; margin-top: 1px; color: var(--el-color-warning); }

.preview-box { margin: var(--space-2-5) var(--space-3-5); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); overflow: hidden; }
.preview-img { width: 100%; display: block; }

.text-section { padding: var(--space-1) var(--space-4); }
.section-label { font-size: var(--text-xs); font-weight: var(--weight-semibold); color: var(--el-text-color-placeholder); text-transform: uppercase; letter-spacing: var(--tracking-wide); }
.extracted-text {
  font-size: var(--text-xs); color: var(--el-text-color-secondary); line-height: 1.6;
  max-height: 100px; overflow-y: auto; padding: var(--space-2);
  background: var(--el-bg-color-page); border-radius: var(--radius-sm); margin: var(--space-1-5) 0 0;
}

.narration-section { padding: var(--space-3) var(--space-4); flex: 1; display: flex; flex-direction: column; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--space-2); }
.section-actions { display: flex; gap: var(--space-1-5); }
.audio-meta { display: flex; align-items: center; gap: var(--space-1); margin-top: var(--space-2); font-size: var(--text-xs); color: var(--el-color-success); }

/* === RESPONSIVE === */
@media (max-width: 1280px) {
  .workspace-body { flex-direction: column; }
  .editor-panel { width: 100%; max-height: 50vh; border-left: none; border-top: 1px solid var(--el-border-color-light); }
  .thumb-grid { grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: var(--space-2); padding: var(--space-3); }
}
.create-guide {
  display: flex; justify-content: center; gap: 4px; margin-bottom: 24px; flex-wrap: wrap;
}
.guide-step {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 12px; border-radius: 20px;
  background: var(--el-fill-color-light); color: var(--el-text-color-placeholder);
  font-size: 12px; transition: all var(--duration-fast) var(--ease-out);
}
.guide-step.active {
  background: var(--el-color-primary-light-9); color: var(--el-color-primary);
  font-weight: var(--weight-semibold);
}
.step-num {
  display: inline-flex; align-items: center; justify-content: center;
  width: 18px; height: 18px; border-radius: 50%;
  background: var(--el-text-color-placeholder); color: #fff; font-size: 10px; font-weight: var(--weight-bold);
}
.guide-step.active .step-num { background: var(--el-color-primary); }
.guide-step.done { background: var(--el-color-success-light-9); color: var(--el-color-success); }
.guide-step.done .step-num { background: var(--el-color-success); }

.batch-checkbox {
  position: absolute; top: 6px; left: 6px; z-index: 2;
  background: rgba(255,255,255,0.9); border-radius: 4px; padding: 2px;
}
.thumb-card.batch-selected {
  border-color: var(--el-color-primary); box-shadow: 0 0 0 3px var(--el-color-primary-light-8);
}
.batch-bar {
  display: flex; align-items: center; gap: 8px; padding: 8px 16px;
  background: var(--el-color-primary-light-9); border-bottom: 1px solid var(--el-border-color-light);
  font-size: 13px;
}
.batch-count { font-weight: 600; margin-right: 8px; color: var(--el-color-primary); }

/* HTML 课时徽标 */
.html-badge {
  position: absolute; top: 4px; right: 4px; z-index: 2;
  background: #409eff; color: #fff; font-size: 10px; font-weight: 700;
  padding: 1px 5px; border-radius: 3px; line-height: 1.4;
}

.script-preview {
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  padding: 10px 12px;
  cursor: pointer;
  min-height: 60px;
  transition: background .2s;
}
.script-preview:hover { background: var(--el-fill-color-light); }
.script-text { font-size: 13px; line-height: 1.7; color: var(--el-text-color-primary); white-space: pre-wrap; }
.script-placeholder { font-size: 13px; color: var(--el-text-color-placeholder); }
.script-dialog-body { padding: 0; }
.script-dialog-body .el-textarea { width: 100%; }
.script-dialog-body .el-textarea__inner { font-size: 14px; line-height: 1.8; padding: 14px; }
.extracted-ref { margin-bottom: 16px; padding: 12px; background: var(--el-color-warning-light-9); border-radius: 6px; }
.ref-label { font-size: 11px; font-weight: 600; color: var(--el-color-warning); text-transform: uppercase; letter-spacing: 1px; display: block; margin-bottom: 4px; }
.ref-text { font-size: 13px; color: var(--el-text-color-secondary); line-height: 1.6; white-space: pre-wrap; }
.script-dialog-footer { display: flex; justify-content: space-between; align-items: center; }
.footer-left, .footer-right { display: flex; gap: 8px; }

@media (max-width: 640px) {
  .manage-header { padding: var(--space-3); flex-wrap: wrap; }
  .header-actions { width: 100%; }
  .header-actions .el-button { flex: 1; }
}
</style>
