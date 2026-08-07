<!--
  HtmlCoursewareManage.vue · HTML 课件管理模块（独立于 PPT，F-2026-08-07-13）

  设计裁定：课件类型（PPT/HTML）是唯一维度；HTML 使用段级讲述稿/音频管线。
  覆盖：HTML 内容编辑器 + 分段脚本 + 预览/替换/删除。
-->
<template>
  <div class="html-courseware-manage">
    <div class="hcm-header">
      <el-tag :type="statusTagType(tree?.narrationStatus)" size="large" effect="plain">
        {{ statusLabel(tree?.narrationStatus) }} · {{ tree?.audioReadyCount || 0 }} 音频就绪
      </el-tag>
      <div class="hcm-actions">
        <el-button type="primary" plain :icon="View" :disabled="!canPreview" @click="showPreview = true">
          预览
        </el-button>
        <el-upload
          :show-file-list="false"
          :before-upload="(f) => upload.handleUpload(f, 'HTML')"
          accept=".html,.htm"
          :disabled="upload.uploading.value"
        >
          <el-button :icon="UploadFilled" :loading="upload.uploading.value">替换 HTML</el-button>
        </el-upload>
        <el-popconfirm
title="确定删除该课件的全部 HTML 内容吗？" confirm-button-text="删除" cancel-button-text="取消"
                       @confirm="handleDeleteCourseware">
          <template #reference>
            <el-button :icon="Delete" type="danger" plain>删除课件</el-button>
          </template>
        </el-popconfirm>
      </div>
    </div>

    <div class="hcm-panels">
      <el-tabs v-model="activePanel" type="card">
        <el-tab-pane name="content" label="HTML 内容">
          <HtmlBlockEditor :course-id="courseId" :section-id="sectionId" @unit-saved="emit('changed')" />
        </el-tab-pane>
        <el-tab-pane name="segment" label="分段脚本">
          <el-alert
            v-if="!tree?.htmlUnit"
            type="info"
            :closable="false"
            show-icon
            title="单元尚未初始化"
            description="请在「HTML 内容」中编辑并保存一次，系统将自动创建课件单元，之后即可为各分段配置脚本与音频。"
            class="hcm-segment-empty"
          />
          <div
            v-for="(seg, idx) in tree?.htmlUnit ? Array.from(
              { length: Math.max((tree.htmlUnit.detectedSegments || 0), 5) },
              (_, i) => ({ idx: i + 1 })
            ) : []"
            :key="idx"
            class="hcm-segment-block"
          >
            <h5 class="hcm-segment-title">第 {{ seg.idx }} 段</h5>
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

    <!-- 学生视角预览 -->
    <el-dialog v-model="showPreview" title="学生视角预览" fullscreen :destroy-on-close="true">
      <SlidePreview v-if="showPreview" :course-id="courseId" :section-id="sectionId" @close="showPreview = false" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { View, UploadFilled, Delete } from '@element-plus/icons-vue'
import HtmlBlockEditor from './HtmlBlockEditor.vue'
import ScriptEditor from './ScriptEditor.vue'
import SlidePreview from './SlidePreview.vue'
import { useCoursewareUpload } from '../composables/useCoursewareUpload'
import { deleteCourseware } from '../api/slide'

const props = defineProps({
  courseId: { type: Number, required: true },
  chapterId: { type: Number, default: null },
  sectionId: { type: Number, default: null },
  tree: { type: Object, required: true }
})
const emit = defineEmits(['changed'])

const activePanel = ref('content')
const showPreview = ref(false)

const upload = useCoursewareUpload({
  courseId: computed(() => props.courseId),
  chapterId: computed(() => props.chapterId),
  sectionId: computed(() => props.sectionId),
  onSuccess: () => emit('changed')
})

const canPreview = computed(() => props.tree?.type === 'HTML')

function statusLabel(s) {
  return { PENDING: '待生成', AUDIO_GENERATING: '生成中', AUDIO_READY: '就绪' }[s] || s
}
function statusTagType(s) {
  return { PENDING: 'info', AUDIO_GENERATING: 'warning', AUDIO_READY: 'success' }[s] || 'info'
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
.html-courseware-manage { padding: 20px; max-width: 1400px; margin: 0 auto; }
.hcm-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.hcm-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.hcm-panels { background: var(--el-fill-color-blank); border-radius: 8px; }
.hcm-segment-empty { margin-bottom: 12px; }
.hcm-segment-block { margin-bottom: 16px; padding: 12px; background: var(--el-fill-color-light); border-radius: 6px; }
.hcm-segment-title { margin: 0 0 8px; font-size: 14px; font-weight: 600; }
</style>
