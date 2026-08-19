<!--
  HtmlCoursewareManage.vue · HTML 课件管理模块（独立于 PPT，F-2026-08-07-13）

  设计裁定：课件类型（PPT/HTML）是唯一维度；HTML 使用段级讲述稿/音频管线。
  覆盖：HTML 内容编辑器 + 分段脚本 + 预览/替换/删除。
-->
<template>
  <div class="html-courseware-manage">
    <div class="hcm-header">
      <el-tag :type="statusTagType(tree?.narrationStatus)" size="large" effect="plain">
        {{ statusLabel(tree?.narrationStatus) }} · {{ t('htmlCourseware.manage.audioReadyCount', { count: tree?.audioReadyCount || 0 }) }}
      </el-tag>
      <div class="hcm-actions">
        <el-button type="primary" plain :icon="View" :disabled="!canPreview" @click="showPreview = true">
          {{ t('htmlCourseware.manage.preview') }}
        </el-button>
        <el-upload
          :show-file-list="false"
          :before-upload="(f) => upload.handleUpload(f, 'HTML')"
          accept=".html,.htm"
          :disabled="upload.uploading.value"
        >
          <el-button :icon="UploadFilled" :loading="upload.uploading.value">{{ t('htmlCourseware.manage.replaceHtml') }}</el-button>
        </el-upload>
        <el-popconfirm
          :title="t('htmlCourseware.manage.confirmDeleteAll')" :confirm-button-text="t('htmlCourseware.manage.delete')" :cancel-button-text="t('htmlCourseware.manage.cancel')"
                        confirm-button-type="danger"
                        @confirm="deleteAction.run">
          <template #reference>
            <el-button :icon="Delete" type="danger" plain :loading="deleteAction.loading.value">{{ t('htmlCourseware.manage.deleteCourseware') }}</el-button>
          </template>
        </el-popconfirm>
      </div>
    </div>

    <div class="hcm-panels">
      <el-tabs v-model="activePanel" type="card">
        <el-tab-pane name="content" :label="t('htmlCourseware.manage.tabContent')">
          <HtmlBlockEditor
            :course-id="courseId"
            :section-id="sectionId"
            :reload-key="htmlReloadKey"
            @unit-saved="emit('changed')"
          />
        </el-tab-pane>
        <el-tab-pane name="segment" :label="t('htmlCourseware.manage.tabSegments')">
          <el-alert
            v-if="!tree?.htmlUnit"
            type="info"
            :closable="false"
            show-icon
            :title="t('htmlCourseware.manage.unitNotInitialized')"
            :description="t('htmlCourseware.manage.unitNotInitializedDesc')"
            class="hcm-segment-empty"
          />
          <!-- L0 U-4：无任何分段的真实空状态（替代原 Math.max(...,5) 渲染 5 个空编辑块的误导） -->
          <div v-if="hasNoSegments" class="hcm-segment-empty-card">
            <el-icon :size="40" class="hcm-empty-icon"><Files /></el-icon>
            <p class="hcm-empty-title">{{ t('htmlCourseware.manage.noSegmentsTitle') }}</p>
            <p class="hcm-empty-desc">{{ t('htmlCourseware.manage.noSegmentsDesc') }}</p>
            <div class="hcm-empty-actions">
              <el-button type="primary" :icon="MagicStick" :loading="detectAction.loading.value" @click="detectAction.run">
                {{ t('htmlCourseware.manage.detectSegments') }}
              </el-button>
              <el-button :icon="Plus" @click="addSegmentManually">{{ t('htmlCourseware.manage.addSegment') }}</el-button>
            </div>
          </div>
          <div v-else-if="tree?.htmlUnit && segmentsLoading" class="hcm-segment-loading">
            <el-icon class="is-loading" :size="16"><Loading /></el-icon> {{ t('htmlCourseware.manage.loadingSegments') }}
          </div>
          <div
            v-for="(seg, idx) in segmentSlots"
            :key="idx"
            class="hcm-segment-block"
          >
            <h5 class="hcm-segment-title">{{ t('htmlCourseware.manage.segmentTitle', { idx: seg.idx }) }}</h5>
            <ScriptEditor
              :course-id="courseId"
              page-type="HTML"
              :unit-id="tree.htmlUnit.id"
              :segment-index="seg.idx"
              :current-script-id="scriptIdOf(seg.idx)"
              @save-success="loadSegments"
            />
          </div>
          <!-- P0-2 修复: unit 级 AudioManager 多段模式 (与 PPT 每页一个 AudioManager 对称,
               HTML 按 unit 聚合各段音频, 内部 tabs 切换, 段有脚本才出现在列表) -->
          <div v-if="tree?.htmlUnit" class="hcm-segment-audio">
            <h5 class="hcm-segment-audio-title">{{ t('htmlCourseware.manage.segmentAudio') }}</h5>
            <AudioManager
              :course-id="courseId"
              page-type="HTML"
              :owner-id="tree.htmlUnit.id"
              :segments="audioSegments"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 学生视角预览 -->
    <el-dialog v-model="showPreview" :title="t('htmlCourseware.manage.studentPreviewTitle')" fullscreen :destroy-on-close="true">
      <SlidePreview v-if="showPreview" :course-id="courseId" :section-id="sectionId" @close="showPreview = false" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { View, UploadFilled, Delete, Files, MagicStick, Plus, Loading } from '@element-plus/icons-vue'
import HtmlBlockEditor from './HtmlBlockEditor.vue'
import ScriptEditor from './ScriptEditor.vue'
import AudioManager from './AudioManager.vue'
import SlidePreview from './SlidePreview.vue'
import { useCoursewareUpload } from '../composables/useCoursewareUpload'
import { useAsyncAction } from '../composables/useAsyncAction'
import { deleteCourseware } from '../api/slide'
import { listActiveHtmlSegments, detectHtmlSegments } from '../api/htmlCourseware'

const { t } = useI18n()

const props = defineProps({
  courseId: { type: Number, required: true },
  chapterId: { type: Number, default: null },
  sectionId: { type: Number, default: null },
  tree: { type: Object, required: true }
})
const emit = defineEmits(['changed'])

const activePanel = ref('content')
const showPreview = ref(false)

// P0-2 修复: HTML 段级"讲述稿→音频"管线
// tree.htmlUnit 不含段级 scriptId, 需按 unit 拉取 active segments (含 scriptId)
// 每段 ScriptEditor 传真实 current-script-id; unit 级 AudioManager 多段模式
const segments = ref([])
const segmentsLoading = ref(false)

async function loadSegments() {
  const unitId = props.tree?.htmlUnit?.id
  if (!unitId) {
    segments.value = []
    return
  }
  segmentsLoading.value = true
  try {
    const res = await listActiveHtmlSegments(props.courseId, unitId)
    segments.value = (res.data || res || []).map((s) => ({
      segmentIndex: s.segmentIndex,
      scriptId: s.id
    }))
  } catch (e) {
    segments.value = []
  } finally {
    segmentsLoading.value = false
  }
}

function scriptIdOf(idx) {
  return segments.value.find((s) => s.segmentIndex === idx)?.scriptId || null
}

// 传给 AudioManager 的多段 segments 数组 (有脚本的段才出现在音频列表)
const audioSegments = computed(() =>
  segments.value
    .filter((s) => s.scriptId)
    .map((s) => ({ idx: s.segmentIndex, segmentScriptId: s.scriptId }))
)

// L0 U-4（原 U9 魔法数修复）：去掉 Math.max(detectedSegments, 5)。
// - 槽位数量 = max(后端 detectedSegments, 数据库已存在段的最高序号, 本次会话手动添加数)；
//   这样：detectedSegments=0 且无任何段 → 0 槽位（渲染空状态卡）；
//   已保存段（如手动添加 1..3 后刷新）仍按真实段渲染，不破坏"已有 N 个段"逻辑。
// - manualSlots 为会话级（刷新重置），已保存段由 existingMaxIndex 兜底，刷新不丢。
const manualSlots = ref(0)

const existingMaxIndex = computed(() => {
  if (!segments.value.length) return 0
  return Math.max(...segments.value.map((s) => s.segmentIndex || 0))
})

const segmentSlots = computed(() => {
  if (!props.tree?.htmlUnit) return []
  const detected = props.tree.htmlUnit.detectedSegments || 0
  const total = Math.max(detected, existingMaxIndex.value, manualSlots.value)
  return Array.from({ length: total }, (_, i) => ({ idx: i + 1 }))
})

// 无任何段的真实空状态（加载中不算空，避免闪烁）
const hasNoSegments = computed(() =>
  !!props.tree?.htmlUnit && segmentSlots.value.length === 0 && !segmentsLoading.value
)

// 「手动添加段」：追加一个空槽位（后续保存讲述稿即落库为真实段）
function addSegmentManually() {
  manualSlots.value += 1
  activePanel.value = 'segment'
}

// P2-1: 「开始检测」调用后端启发式分段检测接口（标题/段落边界）——
// 后端落库 slide_html_units.detected_segments 并返回段列表。
// 成功后刷新课件树（segmentSlots 由 detectedSegments 驱动渲染 N 个槽位），
// 并如实提示检测到的段落数（不再是"仅重读服务器状态"的按钮摆设）。
async function runSegmentDetection() {
  const unitId = props.tree?.htmlUnit?.id
  if (!unitId) {
    ElMessage.warning(t('htmlCourseware.manage.saveFirstTip'))
    return
  }
  try {
    const res = await detectHtmlSegments(props.courseId, unitId)
    const payload = res?.data || res
    const count = payload?.detectedCount
      ?? (Array.isArray(payload?.segments) ? payload.segments.length : 0)
    emit('changed')
    ElMessage.success(count > 0 ? t('htmlCourseware.manage.detectedCount', { count }) : t('htmlCourseware.manage.noDetected'))
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('htmlCourseware.manage.detectFailed'))
  }
}
const detectAction = useAsyncAction(runSegmentDetection)

watch(
  () => props.tree?.htmlUnit?.id,
  () => loadSegments(),
  { immediate: true }
)

// P0-3: 替换 HTML 后编辑器重载信号（每次上传 +1）
const htmlReloadKey = ref(0)

const upload = useCoursewareUpload({
  courseId: computed(() => props.courseId),
  chapterId: computed(() => props.chapterId),
  sectionId: computed(() => props.sectionId),
  onSuccess: () => {
    // P0-3: 替换 HTML 上传成功后：
    // 1) bump reloadKey → HtmlBlockEditor 强制重载 v2 unit 新内容（后端已同步）
    // 2) 刷新课件树（detected_segments / narrationStatus 等）
    htmlReloadKey.value++
    emit('changed')
  }
})

const canPreview = computed(() => props.tree?.type === 'HTML')

// P1-C-4/P1-C-3：AUDIO_PENDING（待生成）/ AUDIO_FAILED（失败）聚合枚举映射（G3 后端新枚举）
function statusLabel(s) {
  return {
    PENDING: t('htmlCourseware.manage.status.pending'),
    AUDIO_PENDING: t('htmlCourseware.manage.status.pending'),
    AUDIO_GENERATING: t('htmlCourseware.manage.status.generating'),
    AUDIO_READY: t('htmlCourseware.manage.status.ready'),
    AUDIO_FAILED: t('htmlCourseware.manage.status.failed')
  }[s] || s
}
function statusTagType(s) {
  return {
    PENDING: 'info',
    AUDIO_PENDING: 'info',
    AUDIO_GENERATING: 'warning',
    AUDIO_READY: 'success',
    AUDIO_FAILED: 'danger'
  }[s] || 'info'
}

async function handleDeleteCourseware() {
  try {
    await deleteCourseware(props.courseId, props.sectionId || null, props.sectionId ? null : props.chapterId)
    ElMessage.success(t('htmlCourseware.manage.coursewareDeleted'))
    emit('changed')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('htmlCourseware.manage.deleteFailed'))
  }
}

// L0 Task 2: 删除操作用 useAsyncAction 统一防重复触发 (loading + 双击保护)
const deleteAction = useAsyncAction(handleDeleteCourseware)

onUnmounted(() => upload.stopRenderPolling())
</script>

<style scoped>
.html-courseware-manage { padding: 20px; max-width: 1400px; margin: 0 auto; }
.hcm-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.hcm-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.hcm-panels { background: var(--el-fill-color-blank); border-radius: 8px; }
.hcm-segment-empty { margin-bottom: 12px; }
/* L0 U-4：空状态卡（无分段时的明确引导，替代 5 个空编辑块） */
.hcm-segment-empty-card {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 10px; padding: 48px 24px; margin-bottom: 16px;
  background: var(--el-fill-color-light); border: 1px dashed var(--el-border-color);
  border-radius: 8px; text-align: center;
}
.hcm-empty-icon { color: var(--el-text-color-placeholder); }
.hcm-empty-title { margin: 0; font-size: 15px; font-weight: 600; color: var(--el-text-color-primary); }
.hcm-empty-desc { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); }
.hcm-empty-actions { display: flex; gap: 10px; margin-top: 4px; }
.hcm-segment-loading {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 32px; color: var(--el-text-color-secondary); font-size: 13px;
}
.hcm-segment-block { margin-bottom: 16px; padding: 12px; background: var(--el-fill-color-light); border-radius: 6px; }
.hcm-segment-title { margin: 0 0 8px; font-size: 14px; font-weight: 600; }
.hcm-segment-audio { margin-top: 4px; }
.hcm-segment-audio-title { margin: 0 0 8px; font-size: 14px; font-weight: 600; }
/* L0 Task 4: Tab 焦点环可见 (键盘用户 / 读屏用户) */
:deep(.el-button:focus-visible),
:deep(.el-upload:focus-visible),
:deep(.el-upload input:focus-visible) {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
