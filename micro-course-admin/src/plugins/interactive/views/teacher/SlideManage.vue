<!--
  SlideManage.vue · 课件管理统一入口（F-2026-08-07-13 设计裁定）

  课件类型（PPT / HTML）是唯一维度，不再有"新版/旧版"切换：
  - 树按类型分发：PPT → PptCoursewareManage，HTML → HtmlCoursewareManage
  - 空课时 → 明确的创建二选一（上传 PPT / 上传 HTML），创建后即固定为该类型
  - 支持课时级（sectionId）与章节级（chapterId）两种挂载
-->
<template>
  <div class="slide-manage fade-in">
    <!-- Breadcrumb -->
    <div class="breadcrumb-bar">
      <el-breadcrumb separator="→">
        <el-breadcrumb-item :to="{ path: userRole === 'TEACHER' ? '/teacher/courses' : '/courses' }">课程管理</el-breadcrumb-item>
        <el-breadcrumb-item v-if="courseTitle" :to="{ path: `/courses/${courseId}` }">{{ courseTitle }}</el-breadcrumb-item>
        <el-breadcrumb-item>课件管理</el-breadcrumb-item>
      </el-breadcrumb>
      <div v-if="courseTitle || chapterTitle" class="context-tags">
        <el-tag v-if="courseTitle" type="primary" size="small" effect="plain">{{ courseTitle }}</el-tag>
        <el-tag v-if="chapterTitle" type="success" size="small" effect="plain">{{ chapterTitle }}</el-tag>
      </div>
    </div>

    <div v-loading="typeLoading" class="sm-body">
      <template v-if="!typeLoading">
        <!-- PPT 课件模块 -->
        <PptCoursewareManage
          v-if="tree?.type === 'PPT'"
          :course-id="Number(courseId)"
          :chapter-id="chapterId ? Number(chapterId) : null"
          :section-id="sectionId ? Number(sectionId) : null"
          :tree="tree"
          @changed="loadTree"
        />

        <!-- HTML 课件模块 -->
        <HtmlCoursewareManage
          v-else-if="tree?.type === 'HTML'"
          :course-id="Number(courseId)"
          :chapter-id="chapterId ? Number(chapterId) : null"
          :section-id="sectionId ? Number(sectionId) : null"
          :tree="tree"
          @changed="loadTree"
        />

        <!-- 空状态：创建二选一 -->
        <div v-else class="sm-empty">
          <div v-if="upload.renderPending.value" class="sm-render">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>PPT 正在后台渲染处理，完成后将自动进入课件工作区…</span>
          </div>
          <el-card v-else class="sm-create-card">
            <template #header>
              <h2 class="sm-create-title">{{ sectionId ? '该课时' : '该章节' }}暂无课件</h2>
              <p class="sm-create-sub">请选择要创建的课件类型。创建后该{{ sectionId ? '课时' : '章节' }}将固定为该类型，如需切换请先删除现有课件。</p>
            </template>
            <div class="sm-create-options">
              <div class="sm-option">
                <el-icon :size="36" class="sm-option-icon"><Picture /></el-icon>
                <h3>PPT 课件</h3>
                <p class="sm-option-desc">上传 .pptx，系统自动逐页渲染高清图片，支持页级讲述稿、音频与页间跳转。</p>
                <el-upload
                  drag
                  :show-file-list="false"
                  :before-upload="(f) => handleCreateUpload(f, 'PPT')"
                  accept=".pptx"
                  :disabled="upload.uploading.value"
                  class="sm-upload"
                >
                  <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                  <div class="el-upload__text">拖拽 .pptx 到此处，或 <em>点击上传</em></div>
                  <template #tip><div class="el-upload__tip">支持 .pptx（最大 50MB），上传后自动渲染</div></template>
                </el-upload>
              </div>
              <div class="sm-option">
                <el-icon :size="36" class="sm-option-icon"><Document /></el-icon>
                <h3>HTML 课件</h3>
                <p class="sm-option-desc">上传 .html 或在线编辑，支持分段讲述稿、段级音频与播放时段落高亮。</p>
                <el-upload
                  drag
                  :show-file-list="false"
                  :before-upload="(f) => handleCreateUpload(f, 'HTML')"
                  accept=".html,.htm"
                  :disabled="upload.uploading.value"
                  class="sm-upload"
                >
                  <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                  <div class="el-upload__text">拖拽 .html 到此处，或 <em>点击上传</em></div>
                  <template #tip><div class="el-upload__tip">支持 .html（最大 5MB），上传后可直接在线播放</div></template>
                </el-upload>
              </div>
            </div>
          </el-card>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Picture, Document, UploadFilled, Loading } from '@element-plus/icons-vue'
import { getCourseById } from '@/api/course'
import { getChapterById } from '@/api/chapter'
import { useUserStore } from '@/store/user'
import { getCoursewareTree } from '../../api/queryCourseware'
import { useCoursewareUpload } from '../../composables/useCoursewareUpload'
import PptCoursewareManage from '../../components/PptCoursewareManage.vue'
import HtmlCoursewareManage from '../../components/HtmlCoursewareManage.vue'

const route = useRoute()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)
const courseId = computed(() => route.params.courseId)
const chapterId = computed(() => route.params.chapterId || route.query.chapterId || null)
const sectionId = computed(() => route.query.sectionId || null)

const courseTitle = ref('')
const chapterTitle = ref('')
const tree = ref(null)
const typeLoading = ref(true)

const upload = useCoursewareUpload({
  courseId,
  chapterId,
  sectionId,
  onSuccess: () => {
    loadTree().then(() => {
      // PPT 上传后渲染异步：树仍为 EMPTY 时启动轮询，渲染完成自动刷新进入 PPT 模块
      if (tree.value?.type !== 'PPT') {
        upload.startRenderPolling(loadTree)
      }
    })
  }
})

async function loadTree() {
  typeLoading.value = true
  try {
    const res = await getCoursewareTree(courseId.value, sectionId.value, chapterId.value)
    tree.value = res.data || res
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '加载课件失败')
  } finally {
    typeLoading.value = false
  }
}

async function handleCreateUpload(file, type) {
  const ok = await upload.handleUpload(file, type)
  return ok
}

onMounted(async () => {
  if (courseId.value) {
    try {
      const c = await getCourseById(courseId.value)
      courseTitle.value = c?.data?.title || ''
    } catch { /* 标题加载失败不阻断 */ }
  }
  if (chapterId.value) {
    try {
      const ch = await getChapterById(chapterId.value)
      chapterTitle.value = ch?.data?.title || ''
    } catch { /* 标题加载失败不阻断 */ }
  }
  await loadTree()
})
onUnmounted(() => upload.stopRenderPolling())
</script>

<style scoped>
.slide-manage { padding: 20px; max-width: 1440px; margin: 0 auto; }
.breadcrumb-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; gap: 12px; flex-wrap: wrap; }
.context-tags { display: flex; gap: 8px; }
.sm-body { min-height: 320px; }
.sm-render { display: flex; align-items: center; justify-content: center; gap: 10px; padding: 80px 0; color: var(--el-text-color-secondary); font-size: 15px; }
.sm-create-card { max-width: 1000px; margin: 0 auto; }
.sm-create-title { margin: 0 0 6px; font-size: 18px; }
.sm-create-sub { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; }
.sm-create-options { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.sm-option { border: 1px solid var(--el-border-color-light); border-radius: 10px; padding: 24px; text-align: center; }
.sm-option-icon { color: var(--el-color-primary); margin-bottom: 8px; }
.sm-option h3 { margin: 0 0 8px; font-size: 16px; }
.sm-option-desc { color: var(--el-text-color-secondary); font-size: 13px; margin: 0 0 16px; min-height: 38px; }
.sm-upload :deep(.el-upload) { width: 100%; }
@media (max-width: 768px) {
  .sm-create-options { grid-template-columns: 1fr; }
}
</style>
