<!--
  CoursewareWorkbench.vue · 课件四面板工作台 (新版本)

  这是 spec 5.1 设计的 SlideManage.vue 重构版本, 客户体验至上:
  - 顶部 PPT/HTML 双类型选择 (进入不同工作流)
  - 内容/脚本/音频 三面板切换
  - 额外: 页间跳转 (PPT) / 预览与发布

  与旧 SlideManage.vue 并存, 通过 feature flag 控制:
  - mc:feature:courseware_v2=true 使用新版
  - 默认 false (老用户不打扰)

  Props:
    courseId, chapterId, sectionId
-->
<template>
  <div class="courseware-workbench">
    <!-- Step 1: 课件类型选择 -->
    <div class="cw-step cw-type-select">
      <el-radio-group v-model="coursewareType" size="large">
        <el-radio-button value="PPT">
          <el-icon><Picture /></el-icon>
          PPT 课件
        </el-radio-button>
        <el-radio-button value="HTML">
          <el-icon><Document /></el-icon>
          HTML 课件
        </el-radio-button>
      </el-radio-group>
      <div class="cw-actions">
        <el-tag v-if="tree" :type="statusTagType(tree.narrationStatus)" size="large" effect="plain" class="cw-status-tag">
          {{ statusLabel(tree.narrationStatus) }} · {{ tree.audioReadyCount }} 音频就绪
        </el-tag>
        <el-tooltip
          :disabled="!renderPending"
          content="课件渲染中，完成后方可预览"
          placement="top"
        >
          <span>
            <el-button
              v-if="tree && (tree.type !== 'EMPTY' || renderPending)"
              type="primary"
              plain
              :icon="View"
              :disabled="!previewReady"
              @click="showPreview = true"
            >
              预览
            </el-button>
          </span>
        </el-tooltip>
      </div>
    </div>

    <!-- PPT 工作流 -->
    <div v-if="coursewareType === 'PPT' && tree?.type === 'PPT'" class="cw-ppt">
      <!-- 页选择 -->
      <div class="cw-page-list">
        <h4 class="cw-section-title">页面列表 ({{ tree.pages.length }})</h4>
        <el-radio-group v-model="activePageIdx" class="cw-page-radios">
          <el-radio-button
            v-for="page in tree.pages"
            :key="page.pageId"
            :value="page.pageId"
            class="cw-page-radio"
          >
            <div class="cw-page-radio-content">
              <span>第 {{ page.pageNumber }} 页</span>
              <el-tag :type="statusTagType(page.narrationStatus)" size="small">
                {{ statusLabel(page.narrationStatus) }}
              </el-tag>
            </div>
          </el-radio-button>
        </el-radio-group>
      </div>

      <!-- 当前页面板 -->
      <div v-if="activePage" class="cw-panels">
        <el-tabs v-model="activePanel" type="card" class="cw-tabs">
          <!-- Panel 1: 内容 -->
          <el-tab-pane name="content" label="内容">
            <PptPageEditor :course-id="courseId" :page-id="activePage.pageId" />
          </el-tab-pane>

          <!-- Panel 2: 脚本 -->
          <el-tab-pane name="script" label="讲述稿">
            <ScriptEditor
              :course-id="courseId"
              page-type="PPT"
              :page-id="activePage.pageId"
              :current-script-id="activePage.activeScript?.id || null"
            />
          </el-tab-pane>

          <!-- Panel 3: 音频 -->
          <el-tab-pane name="audio" label="音频">
            <AudioManager
              :course-id="courseId"
              page-type="PPT"
              :owner-id="activePage.pageId"
              :script-id="activePage.activeScript?.id || null"
            />
          </el-tab-pane>

          <!-- Panel 4: 页间跳转 -->
          <el-tab-pane name="flow" label="跳转逻辑">
            <PptFlowEditor :course-id="courseId" :section-id="sectionId" :pages="tree.pages.map(p => ({ id: p.pageId, pageNumber: p.pageNumber, pageTitle: p.pageTitle }))" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- HTML 工作流 -->
    <!-- P1-C 修复：HTML 课件首次上传时 html_unit 尚不存在（tree.type 非 HTML），
         原条件导致 HtmlBlockEditor 永不可达 → 内容编辑/单元创建入口死循环。
         改为按 coursewareType 渲染，HtmlBlockEditor 保存时懒创建 unit。 -->
    <div v-if="coursewareType === 'HTML'" class="cw-html">
      <div class="cw-panels">
        <el-tabs v-model="activePanel" type="card" class="cw-tabs">
          <el-tab-pane name="content" label="HTML 内容">
            <HtmlBlockEditor :course-id="courseId" :section-id="sectionId" @unit-saved="loadTree" />
          </el-tab-pane>
          <el-tab-pane name="segment" label="分段脚本">
            <el-alert
              v-if="!tree?.htmlUnit"
              type="info"
              :closable="false"
              show-icon
              title="单元尚未初始化"
              description="请在「HTML 内容」中编辑并保存一次，系统将自动创建课件单元，之后即可为各分段配置脚本与音频。"
              class="cw-segment-empty"
            />
            <!--
              【BUG #20 修复】 默认显示 5 个 segment 编辑入口 (与 detectedSegments 取较大值).
              若 detectedSegments=0 (新建 unit),仍允许教师编辑默认 5 段.
              若 detectedSegments=10,显示 10 段.
            -->
              <div
                v-for="(seg, idx) in tree?.htmlUnit ? Array.from(
                  { length: Math.max((tree.htmlUnit.detectedSegments || 0), 5) },
                  (_, i) => ({ idx: i + 1 })
                ) : []"
                :key="idx"
                class="cw-segment-block"
              >
              <h5 class="cw-segment-title">第 {{ seg.idx }} 段</h5>
              <ScriptEditor
                :course-id="courseId"
                page-type="HTML"
                :unit-id="tree.htmlUnit.id"
                :segment-index="seg.idx"
                :current-script-id="null"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="tree?.type === 'EMPTY'" class="cw-empty">
      <el-empty
        v-if="renderPending"
        :description="`${coursewareType === 'PPT' ? 'PPT' : 'HTML'} 课件正在后台渲染处理…`"
      >
        <div class="cw-render-tip">渲染完成后本面板将自动显示课件内容，请稍候</div>
      </el-empty>
      <el-empty v-else :description="`该 section 暂无${coursewareType === 'PPT' ? 'PPT' : 'HTML'}课件`">
        <el-upload
          drag
          :show-file-list="false"
          :before-upload="handleUpload"
          accept=".pptx,.html,.htm"
          :disabled="uploading"
          class="cw-upload-dragger"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 .pptx（最大 50MB）和 .html（最大 5MB）</div>
          </template>
        </el-upload>
      </el-empty>
    </div>

    <!-- 学生视角预览 -->
    <el-dialog v-model="showPreview" title="学生视角预览" fullscreen :destroy-on-close="true" class="cw-preview-dialog">
      <SlidePreview v-if="showPreview" :course-id="courseId" :section-id="sectionId" @close="showPreview = false" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, Document, UploadFilled, View } from '@element-plus/icons-vue'
import { getCoursewareTree } from '../api/queryCourseware'
import { uploadSlide } from '../api/slide'
import PptPageEditor from './PptPageEditor.vue'
import HtmlBlockEditor from './HtmlBlockEditor.vue'
import ScriptEditor from './ScriptEditor.vue'
import AudioManager from './AudioManager.vue'
import PptFlowEditor from './PptFlowEditor.vue'
import SlidePreview from './SlidePreview.vue'

const props = defineProps({
  courseId: { type: Number, required: true },
  chapterId: { type: Number, default: null },
  sectionId: { type: Number, required: true }
})

const coursewareType = ref(sessionStorage.getItem(`cw_type_${props.sectionId}`) || 'PPT')
const tree = ref(null)
const activePageIdx = ref(null)
const activePanel = ref('content')
const showPreview = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const renderPending = ref(false)

let renderPollTimer = null

const activePage = computed(() => {
  if (!tree.value?.pages) return null
  return tree.value.pages.find(p => p.pageId === activePageIdx.value) || tree.value.pages[0]
})

const previewReady = computed(() => {
  const t = tree.value
  if (!t || t.type === 'EMPTY' || renderPending.value) return false
  if (t.type === 'PPT') return (t.pages?.length || 0) > 0
  return true
})

async function loadTree() {
  try {
    const res = await getCoursewareTree(props.courseId, props.sectionId)
    tree.value = res.data || res
    // 自动选择第一个页
    if (tree.value?.pages?.length > 0) {
      activePageIdx.value = tree.value.pages[0].pageId
    }
  } catch (e) {
    ElMessage.error('加载课件失败: ' + (e.message || '未知错误'))
  }
}

function statusLabel(s) {
  return { PENDING: '待生成', AUDIO_GENERATING: '生成中', AUDIO_READY: '就绪' }[s] || s
}

function statusTagType(s) {
  return { PENDING: 'info', AUDIO_GENERATING: 'warning', AUDIO_READY: 'success' }[s] || 'info'
}

async function handleUpload(file) {
  // 防并发：uploading 单值，并发上传会互相覆盖
  if (uploading.value) {
    ElMessage.warning('已有课件正在上传，请稍候')
    return false
  }
  // 前端校验：文件大小和类型（与旧版 SlideManage 保持一致）
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.warning('文件超过 50MB 限制')
    return false
  }
  const lowerName = file.name.toLowerCase()
  const isHtmlFile = lowerName.endsWith('.html') || lowerName.endsWith('.htm')
  const isValidType = lowerName.endsWith('.pptx')
    || lowerName.endsWith('.html')
    || lowerName.endsWith('.htm')
  if (!isValidType) {
    ElMessage.warning('仅支持 .pptx / .html / .htm 格式')
    return false
  }
  if (isHtmlFile
      && file.size > 5 * 1024 * 1024) {
    ElMessage.warning('HTML 文件不能超过 5MB')
    return false
  }
  if (lowerName.endsWith('.pptx')) {
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
  uploading.value = true
  uploadProgress.value = 0
  try {
    await uploadSlide(props.courseId, file, (e) => {
      uploadProgress.value = Math.round((e.loaded / e.total) * 100)
    }, props.chapterId ? Number(props.chapterId) : null, props.sectionId ? Number(props.sectionId) : null)
    await loadTree()
    if (isHtmlFile) {
      // HTML 上传即完成（sanitize 入库）；单元需在「HTML 内容」中保存一次后创建，
      // 编辑器会自动预载本次上传的内容，避免内容丢失。
      coursewareType.value = 'HTML'
      ElMessage.success('HTML 上传成功，内容已载入编辑器，请点击「保存」完成单元初始化')
    } else {
      ElMessage.success('上传成功，正在后台渲染...')
      startRenderPolling()
    }
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
  return false // 阻止 el-upload 默认上传行为
}

function startRenderPolling() {
  stopRenderPolling()
  renderPending.value = true
  let count = 0
  renderPollTimer = setInterval(async () => {
    count++
    try {
      const res = await getCoursewareTree(props.courseId, props.sectionId)
      const t = res.data || res
      tree.value = t
      if (t.type !== 'EMPTY') {
        stopRenderPolling()
        renderPending.value = false
        ElMessage.success('课件处理完成')
      }
    } catch (e) {
      // 渲染轮询失败不打断，下一轮重试
    }
    if (count > 30) {
      stopRenderPolling()
      renderPending.value = false
      ElMessage.error('课件处理超时，请稍后刷新查看')
    }
  }, 3000)
}

function stopRenderPolling() {
  if (renderPollTimer) {
    clearInterval(renderPollTimer)
    renderPollTimer = null
  }
}

watch(() => [props.courseId, props.sectionId], loadTree, { immediate: true })
watch(coursewareType, (v) => {
  if (props.sectionId) sessionStorage.setItem(`cw_type_${props.sectionId}`, v)
})
watch(() => tree.value?.type, (t) => {
  if (t === 'HTML') coursewareType.value = 'HTML'
  else if (t === 'PPT') coursewareType.value = 'PPT'
})

onMounted(loadTree)
onUnmounted(stopRenderPolling)
</script>

<style scoped>
.courseware-workbench { padding: 20px; max-width: 1400px; margin: 0 auto; }
.cw-step { margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center; }
.cw-actions { display: flex; align-items: center; gap: 12px; }
.cw-status-tag { margin-left: 12px; }
.cw-page-list { background: var(--el-fill-color-light); padding: 16px; border-radius: 8px; margin-bottom: 16px; }
.cw-section-title { margin: 0 0 12px; font-size: 14px; color: var(--el-text-color-secondary); }
.cw-page-radios { display: flex; flex-wrap: wrap; gap: 8px; }
.cw-page-radio { margin-right: 0 !important; margin-bottom: 4px; }
.cw-page-radio-content { display: flex; align-items: center; gap: 6px; }
.cw-panels { background: var(--el-fill-color-blank); border-radius: 8px; padding: 0; }
.cw-tabs { background: transparent; }
.cw-segment-block { margin-bottom: 16px; padding: 12px; background: var(--el-fill-color-light); border-radius: 6px; }
.cw-segment-title { margin: 0 0 8px; font-size: 14px; font-weight: 600; }
.cw-empty { padding: 60px 0; }
.cw-render-tip { color: var(--el-text-color-secondary); font-size: 13px; }
.cw-upload-dragger :deep(.el-upload) { width: 100%; }
.cw-preview-dialog :deep(.el-dialog__body) { padding: 0; }
</style>
