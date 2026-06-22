<!--
  教师工作区
  路由路径: /teacher/courses/:id/workspace
  Phase 10
  Author: Phase10-Development-Team
-->
<template>
  <div class="course-editor" v-loading="loading">
    <header class="editor-header">
      <button class="back-btn" @click="$router.push('/teacher/courses')">← 返回课程列表</button>
      <h1>{{ course.title || '加载中...' }}</h1>
      <el-tag v-if="course.status === 0" type="info">草稿</el-tag>
      <el-tag v-else-if="course.status === 1" type="warning">待审核</el-tag>
      <el-tag v-else-if="course.status === 4" type="success">已发布</el-tag>
      <span class="header-actions">
        <el-button size="small" @click="previewCourse">预览</el-button>
        <el-button size="small" @click="showNarrationSettings = true" v-if="slide">讲述稿设置</el-button>
        <el-button v-if="course.status === 0" type="primary" size="small" :loading="submitting" @click="submitForReview">提交审核</el-button>
      </span>
    </header>

    <div class="editor-body">
      <!-- 非互动课程提示 -->
      <section v-if="notInteractive" class="step-card not-interactive-card">
        <el-result icon="info" title="此课程为视频课程" sub-title="互动课件工作台仅支持互动课程，视频课程请使用视频管理功能">
          <template #extra>
            <el-button type="primary" @click="$router.push('/teacher/courses')">返回课程列表</el-button>
          </template>
        </el-result>
      </section>

      <!-- Step 1: Upload PPT -->
      <section v-if="!notInteractive" class="step-card">
        <h2 class="step-title">📤 上传课件</h2>
        <p class="step-desc">上传 PPT 文件，系统将自动提取内容生成课程大纲和讲述稿</p>
        <div v-if="!slide" class="upload-box">
          <div
class="native-upload-zone" @drop.prevent="onDrop" @dragover.prevent @click="$refs.fileInput.click()" tabindex="0" role="button"
               @keydown.enter.prevent="$refs.fileInput.click()" @keydown.space.prevent="$refs.fileInput.click()"
>
            <input ref="fileInput" type="file" accept=".pptx" style="display:none" @change="onFileSelected" />
            <el-icon :size="40" class="upload-icon"><UploadFilled /></el-icon>
            <div class="upload-text">拖拽 .pptx 文件到此处</div>
            <div class="upload-hint">或点击选择文件 · 支持 .pptx 格式，最大 50MB</div>
          </div>
        </div>
        <div v-else-if="slide?.status === 2" class="file-info">
          <p>✅ {{ slide.fileName }}（{{ pages.length }} 页）</p>
          <el-button size="small" @click="replacePPTX">替换文件</el-button>
        </div>
        <div v-else-if="slide && (slide.status === 0 || slide.status === 1)" class="file-info">
          <p>{{ slide.fileName }}</p>
          <el-tag :type="statusTagType" size="small">{{ dynamicRenderText }}</el-tag>
        </div>
        <div v-if="uploadingFile">
          <el-progress :percentage="realUploadProgress" :stroke-width="6" />
          <p class="progress-hint">
            正在上传文件... {{ realUploadProgress }}%
            <template v-if="uploadSpeedMBps"> · {{ uploadSpeedMBps }} MB/s</template>
            <template v-if="uploadEtaText"> · 剩余 {{ uploadEtaText }}</template>
          </p>
        </div>
        <div v-else-if="rendering || (slide && (slide.status === 0 || slide.status === 1))">
          <el-progress :percentage="renderProgress" :stroke-width="6" :status="renderProgress === 100 ? 'success' : ''" />
          <p class="progress-hint">{{ dynamicRenderText }}</p>
          <p v-if="renderElapsed > 0" class="progress-hint progress-elapsed">已渲染 {{ renderElapsed }} 秒</p>
          <el-alert v-if="slide?.status === 1" type="warning" :closable="false" show-icon class="render-warning">
            <template #title>渲染期间请勿关闭此页面</template>
          </el-alert>
          <!-- 渲染超时渐进反馈 -->
          <div v-if="renderTimeoutLevel >= 1" class="timeout-feedback">
            <div v-if="renderTimeoutLevel === 1" class="timeout-notice">
              <el-text type="warning" size="small">⏱ 渲染耗时较长（已 {{ renderElapsed }} 秒），您可以先去处理其他事务，稍后刷新本页即可查看结果。</el-text>
            </div>
            <div v-else-if="renderTimeoutLevel === 2" class="timeout-notice">
              <el-text type="warning" size="small">⏱ 已等待 {{ renderElapsed }} 秒，渲染仍在进行中。</el-text>
              <el-button size="small" type="warning" plain class="timeout-btn" @click="viewBackendLogs">📋 查看后台日志</el-button>
            </div>
            <div v-else-if="renderTimeoutLevel === 3" class="timeout-notice timeout-urgent">
              <el-text type="danger" size="small">⚠ 渲染已耗时 {{ renderElapsed }} 秒，可能存在问题。建议截图当前页面（含课程 ID：{{ courseId }}），联系管理员处理。</el-text>
            </div>
          </div>
          <!-- 完全超时对话框 -->
          <el-dialog
            v-model="showTimeoutFeedback"
            title="渲染超时"
            width="440px"
            :close-on-click-modal="false"
            :show-close="false"
          >
            <div class="timeout-dialog-body">
              <p>渲染已超过 5 分钟仍未完成。后台可能遇到异常。</p>
              <p class="timeout-course-id">课程 ID：<code>{{ courseId }}</code></p>
            </div>
            <template #footer>
              <el-button type="primary" @click="showTimeoutFeedback = false">我已知晓</el-button>
              <el-button @click="viewBackendLogs">查看后台日志</el-button>
            </template>
          </el-dialog>
        </div>
        <div v-else-if="slide?.status === 3" class="render-failure">
          <el-result icon="error" title="课件渲染失败" :sub-title="slide.errorMessage || '请尝试重新上传'">
            <template #extra>
              <el-button type="primary" size="small" @click="copyCourseId">📋 复制课程ID</el-button>
              <el-button size="small" @click="replacePPTX">重新上传</el-button>
              <p class="contact-admin">如问题持续，请联系管理员</p>
            </template>
          </el-result>
        </div>
        <div v-if="uploadFailed" class="upload-retry">
          <el-result icon="error" title="上传失败" sub-title="文件上传失败，请重试">
            <template #extra>
              <el-button type="primary" @click="retryUpload">重新上传</el-button>
            </template>
          </el-result>
        </div>
      </section>

      <!-- Step 2: Course Outline -->
      <section v-if="pages.length > 0" class="step-card">
        <h2 class="step-title">📋 课程大纲</h2>
        <p class="step-desc">每个页面作为一个课时，可查看状态</p>
        <div v-for="(page, idx) in pages" :key="idx" class="outline-item">
          <span class="page-num">{{ idx + 1 }}</span>
          <span class="page-outline-title">第 {{ idx + 1 }} 页</span>
          <el-tag v-if="page.narrationStatus === 'AUDIO_READY'" type="success" size="small">音频就绪</el-tag>
          <el-tag v-else-if="page.narrationStatus !== 'PENDING'" type="warning" size="small">{{ statusText(page) }}</el-tag>
          <el-tag v-else type="info" size="small">等待处理</el-tag>
        </div>
      </section>

      <!-- Step 3: AI Narration + Edit -->
      <section v-if="pages.length > 0" class="step-card">
        <div class="step-header">
          <h2 class="step-title">🎙️ 讲述稿</h2>
          <div class="step-actions">
            <el-button size="small" type="primary" :loading="aiGenerating" @click="handleGenerateAllNarrations">🤖 AI 生成全部</el-button>
          </div>
        </div>
        <p class="step-desc">AI自动生成讲述稿，教师可逐页编辑确认</p>

        <div v-for="(page, idx) in pages" :key="idx" class="narration-card">
          <div class="narration-header" @click="toggleOpen(idx)">
            <span>第 {{ idx + 1 }} 页：{{ page.pageTitle || '课时 ' + (idx + 1) }}</span>
            <el-icon :class="{ rotated: openPages.has(idx) }"><ArrowDown /></el-icon>
          </div>
          <div v-show="openPages.has(idx)" class="narration-body">
            <div class="extracted-text" v-if="page.extractedText">
              <span class="label">PPT原文：</span>
              <p>{{ page.extractedText }}</p>
            </div>
            <div class="narration-editor">
              <span class="label">讲述稿：</span>
              <el-input
v-model="page.narrationScript" type="textarea" :rows="4"
                @blur="saveNarration(page)" placeholder="点击「AI 生成本页」或手动输入..."
/>
              <div class="narration-actions">
                <el-button size="small" :loading="aiLoading[idx]" @click="generateOneNarration(idx)">
                  🤖 AI 生成本页
                </el-button>
                <el-button
size="small" type="success" :loading="ttsLoading[idx]"
                  :disabled="!page.narrationScript" @click="generateOneAudio(idx)"
>
                  🔊 生成音频
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Step 4: Exercises -->
      <section v-if="!notInteractive" class="step-card">
        <h2 class="step-title">📝 练习</h2>
        <p class="step-desc">手动添加练习题目，或指定类型由AI全量生成</p>

        <div class="exercise-controls">
          <el-button size="small" @click="addExercise">➕ 添加题目</el-button>
          <el-button size="small" type="primary" :loading="aiExerciseLoading">🤖 AI 生成练习</el-button>
        </div>

        <div v-for="(ex, idx) in exercises" :key="idx" class="exercise-item">
          <el-select v-model="ex.type" size="small" class="ex-type">
            <el-option label="单选题" value="SINGLE" />
            <el-option label="多选题" value="MULTIPLE" />
            <el-option label="判断题" value="JUDGE" />
            <el-option label="填空题" value="FILL" />
          </el-select>
          <el-input v-model="ex.content" size="small" placeholder="题目内容" class="ex-content" />
          <el-button size="small" text type="danger" @click="removeExercise(idx)">删除</el-button>
        </div>
      </section>

      <!-- 讲述稿设置弹窗 -->
      <NarrationSettingsDialog v-model="showNarrationSettings" :course-id="courseId" @saved="onNarrationSettingsSaved" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, UploadFilled } from '@element-plus/icons-vue'
import { getCourseById, submitCourseForReview } from '@/api/course'
import { getSlides, getSlidePages, uploadSlide, generateNarration, updateNarration, generateAllNarrations, generateAudio, generateAllAudio } from '@/plugins/interactive/api/slide'
import NarrationSettingsDialog from '@/plugins/interactive/components/NarrationSettingsDialog.vue'

const route = useRoute()
const courseId = computed(() => route.params.id)
const course = ref({})
const loading = ref(true)
const notInteractive = ref(false)
const showNarrationSettings = ref(false)
const submitting = ref(false)
const slide = ref(null)
const pages = ref([])
const rendering = ref(false)
const renderProgress = ref(0)
const uploadingFile = ref(false)
const realUploadProgress = ref(0)
const uploadSpeed = ref(null)
const uploadEta = ref(null)
const uploadStartBytes = ref(0)
const uploadStartTime = ref(0)
const openPages = ref(new Set([0]))
const aiGenerating = ref(false)
const aiLoading = ref({})
const ttsLoading = ref({})
const aiExerciseLoading = ref(false)
const exercises = ref([])
const pollTimer = ref(null)
const narrationPollTimer = ref(null)
const uploadFailed = ref(false)
const renderStatusText = ref('')
const statusTagType = ref('warning')
const renderStartTime = ref(0)
const renderElapsed = ref(0)
const renderTimeoutLevel = ref(0) // 0=正常 1=60s 2=120s 3=180s 4=300s超时
const showTimeoutFeedback = ref(false)
let elapsedTimer = null

function toggleOpen(idx) {
  const s = new Set(openPages.value)
  s.has(idx) ? s.delete(idx) : s.add(idx)
  openPages.value = s
}

function statusText(page) {
  const m = { AI_GENERATED: 'AI已生成', TEACHER_EDITED: '已编辑' }
  return m[page.narrationStatus] || page.narrationStatus
}

const tagTypeMap = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }

const uploadSpeedMBps = computed(() => {
  if (!uploadSpeed.value) return null
  return (uploadSpeed.value / (1024 * 1024)).toFixed(1)
})
const uploadEtaText = computed(() => {
  if (uploadEta.value === null || uploadEta.value === undefined) return ''
  if (uploadEta.value < 60) return `${uploadEta.value} 秒`
  const min = Math.floor(uploadEta.value / 60)
  const sec = uploadEta.value % 60
  return `${min} 分 ${sec} 秒`
})

async function loadCourse() {
  loading.value = true
  try {
    const { data } = await getCourseById(courseId.value)
    course.value = data || {}
    // 非互动课程提示并阻止后续加载
    if (data && data.courseType !== 'INTERACTIVE') {
      notInteractive.value = true
      ElMessage.warning('此课程为视频课程，不支持互动课件编辑')
      loading.value = false
      return
    }
    notInteractive.value = false
    await loadSlides()
  } catch { ElMessage.error('加载课程失败') }
  finally { loading.value = false }
}

async function loadSlides() {
  try {
    const s = await getSlides(courseId.value)
    slide.value = s.data
    if (s.data?.status === 2) {
      const p = await getSlidePages(courseId.value)
      pages.value = (p.data || []).map(page => ({ ...page, pageTitle: `课时 ${page.pageNumber}` }))
      if (pages.value.length > 0) openPages.value = new Set([0])
    }
  } catch (e) {
    if (e?.response?.status !== 404) {
      console.warn('[TeacherWorkspace] loadSlides error', e)
    }
  }
}

async function uploadFile(file) {
  if (!file || !file.name) { ElMessage.warning('无效的文件'); return }
  if (file.size > 50 * 1024 * 1024) { ElMessage.warning('文件超过 50MB 限制'); return }
  if (!file.name.toLowerCase().endsWith('.pptx')) { ElMessage.warning('仅支持 .pptx 格式，当前文件：' + file.name); return }
  if (file.slice && file.size >= 4) {
    try {
      const header = await file.slice(0, 4).arrayBuffer()
      const magic = new Uint8Array(header)
      if (magic[0] !== 0x50 || magic[1] !== 0x4B || magic[2] !== 0x03 || magic[3] !== 0x04) {
        ElMessage.warning('文件格式异常，请确认上传的是有效的 .pptx 文件')
        return
      }
    } catch (e) { /* skip magic check */ }
  }

  uploadingFile.value = true
  uploadFailed.value = false
  realUploadProgress.value = 0
  uploadSpeed.value = null
  uploadEta.value = null
  uploadStartBytes.value = 0
  uploadStartTime.value = Date.now()
  try {
    await uploadSlide(courseId.value, file, (e) => {
      realUploadProgress.value = Math.round((e.loaded / e.total) * 100)
      const now = Date.now()
      const deltaBytes = e.loaded - uploadStartBytes.value
      const deltaSec = Math.max((now - uploadStartTime.value) / 1000, 0.01)
      if (deltaSec >= 0.5 && deltaBytes > 0) {
        uploadSpeed.value = deltaBytes / deltaSec
        const remaining = e.total - e.loaded
        uploadEta.value = uploadSpeed.value > 0 ? Math.ceil(remaining / uploadSpeed.value) : null
        uploadStartBytes.value = e.loaded
        uploadStartTime.value = now
      }
    })
  } catch (e) {
    uploadingFile.value = false
    slide.value = null
    pages.value = []
    uploadFailed.value = true
    ElMessage.error('上传失败，请稍后重试')
    return
  }
  uploadingFile.value = false

  rendering.value = true
  renderProgress.value = 0
  renderStartTime.value = Date.now()
  renderElapsed.value = 0
  renderTimeoutLevel.value = 0
  showTimeoutFeedback.value = false
  renderStatusText.value = '正在后台渲染课件...'

  // 每秒更新一次已耗时（驱动 UI 提示）
  if (elapsedTimer) clearInterval(elapsedTimer)
  elapsedTimer = setInterval(() => {
    renderElapsed.value = Math.floor((Date.now() - renderStartTime.value) / 1000)
    // 渐进反馈：60s / 120s / 180s
    if (renderElapsed.value >= 180) {
      renderTimeoutLevel.value = 3
    } else if (renderElapsed.value >= 120) {
      renderTimeoutLevel.value = 2
    } else if (renderElapsed.value >= 60) {
      renderTimeoutLevel.value = 1
    }
  }, 1000)

  let pollCount = 0
  const maxPolls = 100 // 300s
  pollTimer.value = setInterval(async () => {
    pollCount++
    if (pollCount > maxPolls) {
      clearInterval(pollTimer.value)
      if (elapsedTimer) clearInterval(elapsedTimer)
      renderTimeoutLevel.value = 4
      renderStatusText.value = '渲染超时（超过 5 分钟），请稍后刷新页面查看状态'
      showTimeoutFeedback.value = true
      return
    }
    try {
      const s = await getSlides(courseId.value)
      slide.value = s.data
      const status = s.data?.status
      renderStatusText.value = s.data?.statusText || '处理中...'
      if (status === 2) {
        clearInterval(pollTimer.value)
        if (elapsedTimer) clearInterval(elapsedTimer)
        renderProgress.value = 100
        renderTimeoutLevel.value = 0
        await loadSlides()
        ElMessage.success('课件渲染完成')
        setTimeout(() => { rendering.value = false }, 1500)
      } else if (status === 3) {
        clearInterval(pollTimer.value)
        if (elapsedTimer) clearInterval(elapsedTimer)
        rendering.value = false
        renderTimeoutLevel.value = 0
        ElMessage.error(getRenderErrorTip(s.data?.errorMessage))
      }
    } catch (e) {
      /* poll silently */
    }
  }, 3000)
}

function onFileSelected(e) {
  const file = e.target.files?.[0]
  if (file) uploadFile(file)
  e.target.value = ''
}

function onDrop(e) {
  const file = e.dataTransfer?.files?.[0]
  if (file) uploadFile(file)
}

function retryUpload() {
  uploadFailed.value = false
  // 通过 Vue template ref 触发原生文件选择对话框
  const inputEl = fileInput?.value || document.querySelector('.native-upload-zone input[type="file"]')
  if (inputEl) {
    inputEl.value = ''
    inputEl.click()
  }
}

function getRenderErrorTip(errorMessage) {
  if (!errorMessage) return '课件渲染失败，请重新上传'
  const msg = errorMessage.toLowerCase()
  if (msg.includes('损坏') || msg.includes('corrupt') || msg.includes('invalid') || msg.includes('magic') || msg.includes('不是有效的')) {
    return 'PPT 文件可能已损坏，请检查源文件后重新上传'
  }
  if (msg.includes('太大') || msg.includes('过大') || msg.includes('exceed') || msg.includes('too large')) {
    return 'PPT 文件过大，请压缩图片或拆分为多个课程后重新上传'
  }
  if (msg.includes('格式') || msg.includes('format') || msg.includes('不支持') || msg.includes('unsupported')) {
    return 'PPT 格式异常，请确认文件为有效的 .pptx 格式'
  }
  if (msg.includes('超时') || msg.includes('timeout') || msg.includes('timed out')) {
    return '渲染超时，可能是文件页数过多或图片分辨率过高，请精简后重试'
  }
  if (msg.includes('内存') || msg.includes('memory') || msg.includes('oom')) {
    return '文件内容过多导致内存不足，请拆分 PPT 或压缩图片后重试'
  }
  return `课件渲染失败：${errorMessage}`
}

function replacePPTX() {
  if (pollTimer.value) { clearInterval(pollTimer.value); pollTimer.value = null }
  slide.value = null
  pages.value = []
  uploadFailed.value = false
}

async function saveOutline(page) {
  // page title updates are displayed-only; slide_pages title not supported in current API
}

async function generateOneNarration(idx) {
  aiLoading.value[idx] = true
  try {
    const res = await generateNarration(courseId.value, pages.value[idx].pageNumber)
    pages.value[idx].narrationScript = res.data.narrationScript
    pages.value[idx].narrationStatus = res.data.narrationStatus
    ElMessage.success('讲述稿已生成')
  } catch { ElMessage.error('生成失败') }
  finally { aiLoading.value[idx] = false }
}

async function handleGenerateAllNarrations() {
  aiGenerating.value = true
  try {
    await generateAllNarrations(courseId.value)
    ElMessage.success('批量讲述稿生成已启动，正在等待完成...')
  } catch (e) {
    ElMessage.error('操作失败')
    console.warn('[TeacherWorkspace] generateAllNarrations failed', e)
    aiGenerating.value = false
    return
  }

  // 短轮询：每 2s 检查一次，最多 30 次（60s），直到 narrationStatus 全部变化
  let pollCount = 0
  const maxPolls = 30
  narrationPollTimer.value = setInterval(async () => {
    pollCount++
    try {
      const p = await getSlidePages(courseId.value)
      const newPages = (p.data || []).map(page => ({ ...page, pageTitle: `课时 ${page.pageNumber}` }))
      const allDone = newPages.every(page => page.narrationStatus !== 'PENDING')
      if (allDone || pollCount >= maxPolls) {
        clearInterval(narrationPollTimer.value)
        narrationPollTimer.value = null
        pages.value = newPages
        if (allDone) {
          ElMessage.success('全部讲述稿生成完成')
        } else {
          ElMessage.warning('部分讲述稿仍在生成中，请稍后手动刷新')
        }
        aiGenerating.value = false
      }
    } catch {
      // poll silently
    }
  }, 2000)
}

async function saveNarration(page) {
  try {
    await updateNarration(courseId.value, page.pageNumber, page.narrationScript || '')
    page.narrationStatus = 'TEACHER_EDITED'
  } catch (e) {
    ElMessage.error('保存讲述稿失败')
    console.warn('[TeacherWorkspace] saveNarration failed', e)
  }
}

async function generateOneAudio(idx) {
  ttsLoading.value[idx] = true
  try {
    await generateAudio(courseId.value, pages.value[idx].pageNumber)
    const p = await getSlidePages(courseId.value)
    if (p.data?.[idx]) pages.value[idx].narrationStatus = p.data[idx].narrationStatus
    ElMessage.success('音频已生成')
  } catch { ElMessage.error('TTS生成失败') }
  finally { ttsLoading.value[idx] = false }
}

function addExercise() {
  exercises.value.push({ type: 'SINGLE', content: '' })
  ElMessage.info('练习暂存于本地，请到练习管理页完成创建')
}

function removeExercise(idx) {
  exercises.value.splice(idx, 1)
}

async function submitForReview() {
  submitting.value = true
  try { await submitCourseForReview(courseId.value); ElMessage.success('已提交审核') }
  catch { ElMessage.error('提交失败') }
  finally { submitting.value = false }
}

function previewCourse() { window.open(`/student/courses/${courseId.value}`, '_blank') }

function viewBackendLogs() {
  // 打开新窗口访问后端日志端点（如已配置），否则提示前往服务器查看
  const logUrl = `${import.meta.env.VITE_API_BASE_URL || ''}/actuator/logfile`
  window.open(logUrl, '_blank')
  ElMessage.info('如无法打开，请联系管理员并提供课程 ID：' + courseId.value)
}

function onNarrationSettingsSaved() {
  ElMessage.success('讲述稿设置已更新，下次 AI 生成将使用新设置')
}

onMounted(() => loadCourse())

onUnmounted(() => {
  if (pollTimer.value) clearInterval(pollTimer.value)
  if (elapsedTimer) clearInterval(elapsedTimer)
})
</script>

<style scoped>
.course-editor {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--space-6);
  min-height: 100dvh;
  background: var(--el-bg-color-page);
}

.not-interactive-card {
  text-align: center;
  padding: var(--space-8) var(--space-6);
}

.editor-header {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  padding: var(--space-4) var(--space-5);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  border: 1px solid var(--el-border-color-lighter);
}

.back-btn {
  border: none;
  background: none;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  transition: color var(--duration-base) var(--ease-out);
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
}

.back-btn:hover {
  color: var(--role-primary);
  background: var(--role-primary-light-9);
}

.editor-header h1 {
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  margin: 0;
  flex: 1;
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-tight);
}

.header-actions {
  display: flex;
  gap: var(--space-2);
}

.editor-body {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.step-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  border: 1px solid var(--el-border-color-lighter);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.step-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.step-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.step-title {
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin: 0 0 var(--space-1);
  letter-spacing: var(--tracking-tight);
}

.step-desc {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin: 0 0 var(--space-4);
}

.step-actions {
  display: flex;
  gap: var(--space-2);
}

.upload-box {
  text-align: center;
  padding: var(--space-6);
  border: 2px dashed var(--el-border-color);
  border-radius: var(--radius-lg);
  transition: border-color var(--duration-base) var(--ease-out),
              background var(--duration-base) var(--ease-out);
}

.upload-retry {
  margin-top: var(--space-4);
}

.upload-box:hover {
  border-color: var(--role-primary);
  background: var(--role-primary-light-9);
}

.upload-icon {
  color: var(--role-primary);
}

.upload-text {
  font-size: var(--text-base);
  color: var(--el-text-color-regular);
  margin-top: var(--space-3);
}

.upload-hint {
  font-size: var(--text-xs);
  color: var(--el-text-color-placeholder);
  margin-top: var(--space-1);
}

.progress-hint {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin-top: var(--space-2);
  text-align: center;
}

.file-info {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.outline-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.page-num {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  background: var(--role-primary-light-9);
  color: var(--role-primary);
  font-weight: var(--weight-semibold);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: var(--text-sm);
}

.page-title-input {
  flex: 1;
}

.narration-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
  margin-bottom: var(--space-2);
  overflow: hidden;
}

.narration-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  cursor: pointer;
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  transition: background var(--duration-base) var(--ease-out);
}

.narration-header:hover {
  background: var(--role-primary-light-9);
}

.narration-header .el-icon {
  transition: transform var(--duration-base) var(--ease-out);
}

.narration-header .el-icon.rotated {
  transform: rotate(180deg);
}

.narration-body {
  padding: var(--space-4);
}

.label {
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-secondary);
  display: block;
  margin-bottom: var(--space-1);
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
}

.extracted-text p {
  font-size: var(--text-sm);
  color: var(--el-text-color-regular);
  line-height: var(--leading-relaxed);
  padding: var(--space-3);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-md);
}

.narration-editor {
  margin-top: var(--space-3);
}

.narration-actions {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.exercise-controls {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.exercise-item {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  margin-bottom: var(--space-2);
}

.ex-type {
  width: 120px;
}

.ex-content {
  flex: 1;
}

/* 渲染超时渐进反馈 */
.timeout-feedback {
  margin-top: var(--space-3);
}

.timeout-notice {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}

.timeout-notice.timeout-urgent {
  background: var(--el-color-danger-light-9);
  border-color: var(--el-color-danger-light-5);
}

.timeout-btn {
  align-self: flex-start;
}

.timeout-dialog-body {
  line-height: var(--leading-relaxed);
  color: var(--el-text-color-regular);
}

.timeout-course-id {
  margin-top: var(--space-2);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.timeout-course-id code {
  background: var(--el-fill-color-light);
  padding: 2px 6px;
  border-radius: 3px;
  font-family: monospace;
}

@media (max-width: 768px) {
  .course-editor {
    padding: var(--space-4);
  }

  .editor-header {
    flex-wrap: wrap;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
