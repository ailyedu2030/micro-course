<!--
  PptCoursewareManage.vue · PPT 课件管理模块（独立于 HTML，F-2026-08-07-13）

  设计裁定：课件类型（PPT/HTML）是唯一维度；PPT 使用独立的页级讲述稿/音频/跳转管线。
  覆盖：页列表 + 四面板（内容/讲述稿/音频/跳转逻辑）+ 预览/替换/下载/删除/批量操作。
-->
<template>
  <div class="ppt-courseware-manage">
    <div class="pcm-header">
      <el-tag :type="statusTagType(tree?.narrationStatus)" size="large" effect="plain">
        {{ statusLabel(tree?.narrationStatus) }} · {{ tree?.audioReadyCount || 0 }} 音频就绪
      </el-tag>
      <div class="pcm-actions">
        <el-button type="primary" plain :icon="View" :disabled="!canPreview" @click="showPreview = true">
          预览
        </el-button>
        <el-upload
          :show-file-list="false"
          :before-upload="(f) => upload.handleUpload(f, 'PPT')"
          accept=".pptx"
          :disabled="upload.uploading.value"
        >
          <el-button :icon="UploadFilled" :loading="upload.uploading.value">替换 PPT</el-button>
        </el-upload>
        <el-button :icon="Download" @click="handleDownload">下载 PPT</el-button>
        <el-button :icon="Select" @click="toggleBatchMode">{{ batchMode ? '退出批量' : '批量操作' }}</el-button>
        <el-popconfirm
title="确定删除该课件的全部 PPT 内容吗？" confirm-button-text="删除" cancel-button-text="取消"
                       @confirm="handleDeleteCourseware">
          <template #reference>
            <el-button :icon="Delete" type="danger" plain>删除课件</el-button>
          </template>
        </el-popconfirm>
      </div>
    </div>

    <!-- 批量操作条 -->
    <div v-if="batchMode" class="pcm-batch-bar">
      <span>已选 {{ selectedBatch.size }} 页</span>
      <el-button size="small" :loading="batchAiLoading" @click="handleBatchAI" :icon="MagicStick">批量 AI 生成</el-button>
      <el-button size="small" type="success" :loading="batchTtsLoading" @click="handleBatchTTS" :icon="Headset">批量生成音频</el-button>
      <el-button size="small" type="danger" plain @click="handleBatchDelete" :icon="Delete">批量删除</el-button>
      <el-button size="small" @click="selectedBatch.clear(); batchMode = false">取消选择</el-button>
    </div>

    <div class="pcm-render-tip" v-if="upload.renderPending.value">
      <el-icon class="is-loading"><Loading /></el-icon>
      PPT 正在后台渲染处理，完成后将自动显示页面…
    </div>

    <!-- 页列表 + 四面板 -->
    <div class="pcm-page-list">
      <h4 class="pcm-section-title">页面列表 ({{ tree?.pages?.length || 0 }})</h4>
      <el-radio-group v-model="activePageIdx" class="pcm-page-radios">
        <el-radio-button
          v-for="page in tree?.pages || []"
          :key="page.pageId"
          :value="page.pageId"
          class="pcm-page-radio"
          @click.stop="batchMode && toggleBatchSelect(page)"
        >
          <div class="pcm-page-radio-content">
            <el-checkbox
              v-if="batchMode"
              :model-value="selectedBatch.has(page.pageId)"
              @click.stop.prevent="toggleBatchSelect(page)"
            />
            <span>第 {{ page.pageNumber }} 页</span>
            <el-tag :type="statusTagType(page.narrationStatus)" size="small">
              {{ statusLabel(page.narrationStatus) }}
            </el-tag>
          </div>
        </el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="activePage" class="pcm-panels">
      <el-tabs v-model="activePanel" type="card">
        <el-tab-pane name="content" label="内容">
          <PptPageEditor :course-id="courseId" :page-id="activePage.pageId" />
        </el-tab-pane>
        <el-tab-pane name="script" label="讲述稿">
          <ScriptEditor
            :course-id="courseId"
            page-type="PPT"
            :page-id="activePage.pageId"
            :current-script-id="activePage.activeScript?.id || null"
          />
        </el-tab-pane>
        <el-tab-pane name="audio" label="音频">
          <AudioManager
            :course-id="courseId"
            page-type="PPT"
            :owner-id="activePage.pageId"
            :script-id="activePage.activeScript?.id || null"
          />
        </el-tab-pane>
        <el-tab-pane name="flow" label="跳转逻辑">
          <PptFlowEditor
            v-if="sectionId"
            :course-id="courseId"
            :section-id="sectionId"
            :pages="(tree?.pages || []).map(p => ({ id: p.pageId, pageNumber: p.pageNumber, pageTitle: p.pageTitle }))"
          />
          <el-alert v-else type="info" :closable="false" title="章节级课件暂不支持页间跳转规则" />
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 学生视角预览 -->
    <el-dialog v-model="showPreview" title="学生视角预览" fullscreen :destroy-on-close="true">
      <SlidePreview v-if="showPreview" :course-id="courseId" :section-id="sectionId" @close="showPreview = false" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View, UploadFilled, Download, Select, Delete, MagicStick, Headset, Loading } from '@element-plus/icons-vue'
import PptPageEditor from './PptPageEditor.vue'
import ScriptEditor from './ScriptEditor.vue'
import AudioManager from './AudioManager.vue'
import PptFlowEditor from './PptFlowEditor.vue'
import SlidePreview from './SlidePreview.vue'
import { useCoursewareUpload } from '../composables/useCoursewareUpload'
import { generatePptScriptAi, getTtsOptions } from '../api/queryCourseware'
import { deletePptPage, generatePptAudio } from '../api/pptCourseware'
import { downloadOriginalSlide, deleteCourseware } from '../api/slide'

const props = defineProps({
  courseId: { type: Number, required: true },
  chapterId: { type: Number, default: null },
  sectionId: { type: Number, default: null },
  tree: { type: Object, required: true }
})
const emit = defineEmits(['changed'])

const activePageIdx = ref(props.tree?.pages?.[0]?.pageId ?? null)
const activePanel = ref('content')
const showPreview = ref(false)
const batchMode = ref(false)
const selectedBatch = ref(new Set())
const batchAiLoading = ref(false)
const batchTtsLoading = ref(false)
// R-6: 批量 TTS 默认音色/模型来自 tts-options 契约（AudioManager 同源），禁止硬编码
const ttsOptions = ref(null)

// 与 AudioManager.loadTtsOptions 同源：一次 GET 拉取官方 voice/model 枚举
async function loadTtsOptions() {
  try {
    const res = await getTtsOptions(props.courseId)
    ttsOptions.value = res.data || res
  } catch {
    // 后端不可用时使用内置官方枚举兜底（与 AudioManager 一致）
  }
}
onMounted(loadTtsOptions)

const upload = useCoursewareUpload({
  courseId: computed(() => props.courseId),
  chapterId: computed(() => props.chapterId),
  sectionId: computed(() => props.sectionId),
  onSuccess: () => {
    emit('changed')
    if (props.tree?.type === 'EMPTY') upload.startRenderPolling(() => emit('changed'))
  }
})

const activePage = computed(() => {
  if (!props.tree?.pages) return null
  return props.tree.pages.find(p => p.pageId === activePageIdx.value) || props.tree.pages[0]
})

const canPreview = computed(() =>
  props.tree?.type === 'PPT' && (props.tree.pages?.length || 0) > 0 && !upload.renderPending.value
)

function statusLabel(s) {
  return { PENDING: '待生成', AUDIO_GENERATING: '生成中', AUDIO_READY: '就绪' }[s] || s
}
function statusTagType(s) {
  return { PENDING: 'info', AUDIO_GENERATING: 'warning', AUDIO_READY: 'success' }[s] || 'info'
}

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) selectedBatch.value = new Set()
}

function toggleBatchSelect(page) {
  const next = new Set(selectedBatch.value)
  if (next.has(page.pageId)) next.delete(page.pageId)
  else next.add(page.pageId)
  selectedBatch.value = next
}

async function handleBatchAI() {
  if (selectedBatch.value.size === 0) {
    ElMessage.warning('请先选择页面')
    return
  }
  batchAiLoading.value = true
  const failed = []
  let ok = 0
  for (const page of props.tree.pages || []) {
    if (!selectedBatch.value.has(page.pageId)) continue
    try {
      await generatePptScriptAi(props.courseId, page.pageId)
      ok++
    } catch (e) {
      failed.push({ page: page.pageNumber, err: e?.response?.data?.message || e?.message || '未知错误' })
    }
  }
  ElMessage[failed.length ? 'warning' : 'success'](
    `批量 AI 生成完成：成功 ${ok} 页${failed.length ? `，失败 ${failed.length} 页（${failed.map(f => `第${f.page}页: ${f.err}`).join('；')}）` : ''}`
  )
  batchAiLoading.value = false
  emit('changed')
}

async function handleBatchTTS() {
  if (selectedBatch.value.size === 0) {
    ElMessage.warning('请先选择页面')
    return
  }
  batchTtsLoading.value = true
  const failed = []
  let ok = 0
  // R-6: 批量生成前确保 tts-options 已加载（mounted 预加载可能尚未返回），
  // 用服务端默认 voice/model，而非硬编码 female-shaonv / speech-2.8-hd
  if (!ttsOptions.value) await loadTtsOptions()
  const voice = ttsOptions.value?.defaultVoice || 'female-shaonv'
  const model = ttsOptions.value?.defaultModel || 'speech-2.8-hd'
  for (const page of props.tree.pages || []) {
    if (!selectedBatch.value.has(page.pageId)) continue
    const scriptId = page.activeScript?.id
    if (!scriptId) {
      failed.push({ page: page.pageNumber, err: '尚未保存讲述稿' })
      continue
    }
    try {
      await generatePptAudio(props.courseId, scriptId, {
        voice,
        model,
        ttsParams: '{}'
      })
      ok++
    } catch (e) {
      failed.push({ page: page.pageNumber, err: e?.response?.data?.message || e?.message || '未知错误' })
    }
  }
  ElMessage[failed.length ? 'warning' : 'success'](
    `批量音频生成：成功 ${ok} 页${failed.length ? `，失败 ${failed.length} 页（${failed.map(f => `第${f.page}页: ${f.err}`).join('；')}）` : ''}`
  )
  batchTtsLoading.value = false
}

async function handleBatchDelete() {
  const ids = [...selectedBatch.value]
  if (ids.length === 0) {
    ElMessage.warning('请先选择页面')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${ids.length} 页吗？`, '批量删除', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch {
    return
  }
  for (const pageId of ids) {
    try { await deletePptPage(props.courseId, pageId) } catch (e) { /* 逐页失败不阻断 */ }
  }
  ElMessage.success('批量删除完成')
  selectedBatch.value = new Set()
  batchMode.value = false
  emit('changed')
}

async function handleDownload() {
  try {
    const res = await downloadOriginalSlide(props.courseId)
    const blob = res?.data
    if (!blob || !(blob instanceof Blob) || blob.size === 0) {
      ElMessage.info('暂无原始文件可下载')
      return
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `courseware-${props.courseId}.pptx`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '下载失败')
  }
}

async function handleDeleteCourseware() {
  try {
    await deleteCourseware(props.courseId, props.sectionId || null, props.sectionId ? null : props.chapterId)
    ElMessage.success('课件已删除')
    emit('changed')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

onUnmounted(() => upload.stopRenderPolling())
</script>

<style scoped>
.ppt-courseware-manage { padding: 20px; max-width: 1400px; margin: 0 auto; }
.pcm-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.pcm-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.pcm-batch-bar { display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: var(--el-fill-color-light); border-radius: 8px; margin-bottom: 14px; }
.pcm-render-tip { display: flex; align-items: center; gap: 8px; color: var(--el-text-color-secondary); margin-bottom: 12px; }
.pcm-page-list { background: var(--el-fill-color-light); padding: 16px; border-radius: 8px; margin-bottom: 16px; }
.pcm-section-title { margin: 0 0 12px; font-size: 14px; color: var(--el-text-color-secondary); }
.pcm-page-radios { display: flex; flex-wrap: wrap; gap: 8px; }
.pcm-page-radio { margin-right: 0 !important; }
.pcm-page-radio-content { display: flex; align-items: center; gap: 6px; }
.pcm-panels { background: var(--el-fill-color-blank); border-radius: 8px; }
</style>
