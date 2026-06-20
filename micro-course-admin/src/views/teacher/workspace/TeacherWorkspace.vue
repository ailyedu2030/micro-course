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
      <section v-if="notInteractive" class="step-card" style="text-align:center;padding:48px 24px;">
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
          <div class="native-upload-zone" @drop.prevent="onDrop" @dragover.prevent @click="$refs.fileInput.click()" tabindex="0" role="button"
               @keydown.enter.prevent="$refs.fileInput.click()" @keydown.space.prevent="$refs.fileInput.click()">
            <input ref="fileInput" type="file" accept=".pptx" style="display:none" @change="onFileSelected" />
            <el-icon :size="40" color="#409eff"><UploadFilled /></el-icon>
            <div class="upload-text">拖拽 .pptx 文件到此处</div>
            <div class="upload-hint">或点击选择文件 · 支持 .pptx 格式，最大 50MB</div>
          </div>
        </div>
        <div v-else class="file-info">
          <p>✅ {{ slide.fileName }}（{{ pages.length }} 页）</p>
          <el-button size="small" @click="replacePPTX">替换文件</el-button>
        </div>
        <el-progress v-if="rendering" :percentage="renderProgress" :stroke-width="6" />
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
              <el-input v-model="page.narrationScript" type="textarea" :rows="4"
                @blur="saveNarration(page)" placeholder="点击「AI 生成本页」或手动输入..." />
              <div class="narration-actions">
                <el-button size="small" :loading="aiLoading[idx]" @click="generateOneNarration(idx)">
                  🤖 AI 生成本页
                </el-button>
                <el-button size="small" type="success" :loading="ttsLoading[idx]"
                  :disabled="!page.narrationScript" @click="generateOneAudio(idx)">
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
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
const openPages = ref(new Set([0]))
const aiGenerating = ref(false)
const aiLoading = ref({})
const ttsLoading = ref({})
const aiExerciseLoading = ref(false)
const exercises = ref([])
const pollTimer = ref(null)

function toggleOpen(idx) {
  const s = new Set(openPages.value)
  s.has(idx) ? s.delete(idx) : s.add(idx)
  openPages.value = s
}

function statusText(page) {
  const m = { AI_GENERATED: 'AI已生成', TEACHER_EDITED: '已编辑' }
  return m[page.narrationStatus] || page.narrationStatus
}

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
  } catch { /* no slides */ }
}

async function uploadFile(file) {
  if (!file || !file.name) { ElMessage.warning('无效的文件'); return }
  if (file.size > 50 * 1024 * 1024) { ElMessage.warning('文件超过 50MB 限制'); return }
  if (!file.name.toLowerCase().endsWith('.pptx')) { ElMessage.warning('仅支持 .pptx 格式，当前文件：' + file.name); return }
  // 检查文件是否为有效 ZIP/PPTX (前4字节 PK\x03\x04)
  if (file.slice && file.size >= 4) {
    try {
      const header = await file.slice(0, 4).arrayBuffer()
      const magic = new Uint8Array(header)
      if (magic[0] !== 0x50 || magic[1] !== 0x4B || magic[2] !== 0x03 || magic[3] !== 0x04) {
        ElMessage.warning('文件格式异常，请确认上传的是有效的 .pptx 文件')
        return
      }
    } catch (e) { /* 降级：跳过浏览器端魔数校验 */ }
  }
  rendering.value = true
  renderProgress.value = 0
  const sim = setInterval(() => { renderProgress.value = Math.min(renderProgress.value + 5, 90) }, 2000)
  try {
    await uploadSlide(courseId.value, file)
    pollTimer.value = setInterval(async () => {
      try {
        const s = await getSlides(courseId.value)
        slide.value = s.data
        if (s.data?.status === 2) {
          clearInterval(pollTimer.value); clearInterval(sim)
          rendering.value = false
          renderProgress.value = 100
          await loadSlides()
          ElMessage.success('课件渲染完成')
        } else if (s.data?.status === 3) {
          clearInterval(pollTimer.value); clearInterval(sim)
          rendering.value = false
          ElMessage.error('渲染失败')
        }
      } catch {}
    }, 3000)
  } catch (e) {
    clearInterval(sim)
    rendering.value = false
    const msg = e?.response?.data?.message || e?.message || '上传失败'
    ElMessage.error(msg)
  }
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

function replacePPTX() { slide.value = null; pages.value = []; }

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
    ElMessage.success('批量AI生成已启动，请稍后刷新')
    setTimeout(async () => {
      const p = await getSlidePages(courseId.value)
      pages.value = (p.data || []).map(page => ({ ...page, pageTitle: `课时 ${page.pageNumber}` }))
    }, 5000)
  } catch { ElMessage.error('操作失败') }
  finally { aiGenerating.value = false }
}

async function saveNarration(page) {
  try {
    await updateNarration(courseId.value, page.pageNumber, page.narrationScript || '')
    page.narrationStatus = 'TEACHER_EDITED'
  } catch {}
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

function onNarrationSettingsSaved() {
  ElMessage.success('讲述稿设置已更新，下次 AI 生成将使用新设置')
}

onMounted(() => loadCourse())
</script>

<style scoped>
.course-editor { max-width: 960px; margin: 0 auto; padding: 20px; min-height: 100vh; background: #f5f5f5; }
.editor-header { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; background: #fff; padding: 12px 20px; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.back-btn { border: none; background: none; color: #666; cursor: pointer; font-size: 14px; }
.back-btn:hover { color: #00cc7e; }
.editor-header h1 { font-size: 18px; font-weight: 600; margin: 0; flex: 1; }
.header-actions { display: flex; gap: 8px; }

.editor-body { display: flex; flex-direction: column; gap: 20px; }

.step-card { background: #fff; border-radius: 8px; padding: 24px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
.step-header { display: flex; align-items: center; justify-content: space-between; }
.step-title { font-size: 18px; font-weight: 600; color: #333; margin: 0 0 4px; }
.step-desc { font-size: 13px; color: #999; margin: 0 0 16px; }
.step-actions { display: flex; gap: 8px; }

.upload-box { text-align: center; padding: 20px; border: 2px dashed #e0e0e0; border-radius: 8px; }
.upload-text { font-size: 14px; color: #606266; margin-top: 12px; }
.upload-hint { font-size: 12px; color: #c0c4cc; margin-top: 4px; }
.file-info { display: flex; align-items: center; gap: 12px; }

.outline-item { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid #f5f5f5; }
.page-num { width: 28px; height: 28px; border-radius: 6px; background: #eef2ff; color: #00cc7e; font-weight: 600; display: flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: 13px; }
.page-title-input { flex: 1; }

.narration-card { border: 1px solid #f0f0f0; border-radius: 8px; margin-bottom: 8px; overflow: hidden; }
.narration-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; cursor: pointer; font-size: 14px; font-weight: 500; background: #fafafa; }
.narration-header .el-icon { transition: transform .2s; }
.narration-header .el-icon.rotated { transform: rotate(180deg); }
.narration-body { padding: 16px; }
.label { font-size: 12px; font-weight: 600; color: #999; display: block; margin-bottom: 4px; }
.extracted-text p { font-size: 13px; color: #666; line-height: 1.6; padding: 8px; background: #f9fafb; border-radius: 6px; }
.narration-editor { margin-top: 12px; }
.narration-actions { display: flex; gap: 8px; margin-top: 8px; }

.exercise-controls { display: flex; gap: 8px; margin-bottom: 16px; }
.exercise-item { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.ex-type { width: 120px; }
.ex-content { flex: 1; }
</style>
