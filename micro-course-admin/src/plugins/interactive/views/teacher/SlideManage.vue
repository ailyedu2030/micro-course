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
        <el-breadcrumb-item :to="{ path: userRole === 'TEACHER' ? '/teacher/courses' : '/courses' }">{{ t('slide.manage.breadcrumbCourse') }}</el-breadcrumb-item>
        <el-breadcrumb-item v-if="courseTitle" :to="{ path: `/courses/${courseId}` }">{{ courseTitle }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ t('slide.manage.breadcrumbCourseware') }}</el-breadcrumb-item>
      </el-breadcrumb>
      <div v-if="courseTitle || chapterTitle" class="context-tags">
        <el-tag v-if="courseTitle" type="primary" size="small" effect="plain">{{ courseTitle }}</el-tag>
        <el-tag v-if="chapterTitle" type="success" size="small" effect="plain">{{ chapterTitle }}</el-tag>
        <!-- D-3 P1-C：章节切换器 —— 课程级入口进入后可自由切换章节，不再"死路" -->
        <el-select
          v-if="chapterOptions.length > 1 && chapterId"
          :model-value="Number(chapterId)"
          size="small"
          class="sm-chapter-switch"
          :placeholder="t('slide.manage.switchChapter')"
          @change="switchChapter"
          :aria-label="t('slide.manage.switchChapter')"
        >
          <el-option v-for="c in chapterOptions" :key="c.id" :label="c.title" :value="Number(c.id)" />
        </el-select>
      </div>
    </div>

    <!-- 章节级：课时课件概览（每个课时一种课件，跳转课时级管理） -->
    <div v-if="chapterId && !sectionId" class="sm-chapter-overview">
      <h3 class="sm-co-title">{{ t('slide.manage.chapterOverviewTitle') }}</h3>
      <!-- L0 Task 3: 章节尚无课时的真实空状态 → 告诉用户该怎么办 -->
      <div
        v-if="!sectionsLoading && sectionStatus.length === 0"
        class="sm-co-empty"
      >
        <el-icon :size="36" class="sm-co-empty-icon"><Files /></el-icon>
        <p class="sm-co-empty-title">{{ t('slide.manage.noSectionsTitle') }}</p>
        <p class="sm-co-empty-desc">{{ t('slide.manage.noSectionsDesc') }}</p>
        <el-button
          type="primary"
          plain
          size="small"
          @click="router.push(`/courses/${courseId.value}`)"
        >
          {{ t('slide.manage.goAddSections') }}
        </el-button>
      </div>
      <el-table
        v-else
        v-loading="sectionsLoading"
        :data="sectionStatus"
        size="small"
        border
        class="sm-co-table"
      >
        <el-table-column prop="title" :label="t('slide.manage.colSection')" min-width="220" />
        <el-table-column :label="t('slide.manage.colCoursewareType')" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.type === 'PPT'" type="primary" size="small">{{ t('slide.manage.pptCourseware') }}</el-tag>
            <el-tag v-else-if="row.type === 'HTML'" type="success" size="small">{{ t('slide.manage.htmlCourseware') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ t('slide.manage.noCourseware') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('slide.manage.colActions')" width="220">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="goManageSection(row.id)">{{ t('slide.manage.manageCourseware') }}</el-button>
            <!-- P2-5：章节概览直接预览该课时课件（无课件时禁用），复用 SlidePreview 学生视角 -->
            <el-button size="small" :disabled="!row.type" @click="openSectionPreview(row.id)">{{ t('slide.manage.preview') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <!-- P2-5：章节级预览 dialog（课时 = row.id 即 sectionId） -->
      <el-dialog v-model="showSectionPreview" :title="t('slide.manage.studentPreviewTitle')" fullscreen :destroy-on-close="true">
        <SlidePreview
          v-if="showSectionPreview && previewSectionId !== null"
          :course-id="courseId"
          :section-id="previewSectionId"
          @close="showSectionPreview = false"
        />
      </el-dialog>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="t('slide.manage.coursewareTip')"
        class="sm-co-tip"
      />
    </div>

    <div v-loading="typeLoading" class="sm-body">
      <template v-if="!typeLoading">
        <!-- 【V333】类型限定模式：?type=HTML / ?type=PPT（从 HTML 课件 / PPT 课件独立管理页进入） -->
        <!-- 当限定类型与该课时实际类型不一致时，给出明确提示，避免错误渲染 -->
        <el-alert
          v-if="restrictedType && tree?.type && tree.type !== 'EMPTY' && tree.type !== restrictedType"
          type="warning"
          :closable="false"
          show-icon
          class="sm-type-mismatch"
          :title="typeMismatchText"
        >
          <template #default>
            <el-button size="small" type="primary" plain @click="clearRestrictedType">{{ t('slide.manage.viewAllTypes') }}</el-button>
          </template>
        </el-alert>

        <!-- PPT 课件模块（限定模式 ?type=PPT 时仅渲染 PPT） -->
        <PptCoursewareManage
          v-if="tree?.type === 'PPT' || (restrictedType === 'PPT' && tree?.type === 'EMPTY')"
          :course-id="Number(courseId)"
          :chapter-id="chapterId ? Number(chapterId) : null"
          :section-id="sectionId ? Number(sectionId) : null"
          :tree="tree"
          @changed="loadTree"
        />

        <!-- HTML 课件模块（限定模式 ?type=HTML 时仅渲染 HTML） -->
        <HtmlCoursewareManage
          v-else-if="tree?.type === 'HTML' || (restrictedType === 'HTML' && tree?.type === 'EMPTY')"
          :course-id="Number(courseId)"
          :chapter-id="chapterId ? Number(chapterId) : null"
          :section-id="sectionId ? Number(sectionId) : null"
          :tree="tree"
          @changed="loadTree"
        />

        <!-- 空状态：创建二选一（限定模式只显示对应类型） -->
        <div v-else class="sm-empty">
          <div v-if="upload.renderPending.value" class="sm-render">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>{{ t('slide.manage.renderPending') }}</span>
          </div>
          <el-card v-else class="sm-create-card">
            <template #header>
              <h2 class="sm-create-title">{{ noCoursewareTitle }}</h2>
              <p class="sm-create-sub">{{ createSubText }}</p>
            </template>
            <div class="sm-create-options" :class="{ 'sm-create-options-single': restrictedType }">
              <div v-if="!restrictedType || restrictedType === 'PPT'" class="sm-option">
                <el-icon :size="36" class="sm-option-icon"><Picture /></el-icon>
                <h3>{{ t('slide.manage.pptCourseware') }}</h3>
                <p class="sm-option-desc">{{ t('slide.manage.optionPptDesc') }}</p>
                <el-upload
                  drag
                  :show-file-list="false"
                  :before-upload="(f) => handleCreateUpload(f, 'PPT')"
                  accept=".pptx"
                  :disabled="upload.uploading.value"
                  class="sm-upload"
                >
                  <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                  <div class="el-upload__text">{{ t('slide.manage.dragPptHint') }} <em>{{ t('slide.manage.clickToUpload') }}</em></div>
                  <template #tip><div class="el-upload__tip">{{ t('slide.manage.pptUploadTip') }}</div></template>
                </el-upload>
              </div>
              <div v-if="!restrictedType || restrictedType === 'HTML'" class="sm-option">
                <el-icon :size="36" class="sm-option-icon"><Document /></el-icon>
                <h3>{{ t('slide.manage.htmlCourseware') }}</h3>
                <p class="sm-option-desc">{{ t('slide.manage.optionHtmlDesc') }}</p>
                <el-upload
                  drag
                  :show-file-list="false"
                  :before-upload="(f) => handleCreateUpload(f, 'HTML')"
                  accept=".html,.htm"
                  :disabled="upload.uploading.value"
                  class="sm-upload"
                >
                  <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                  <div class="el-upload__text">{{ t('slide.manage.dragHtmlHint') }} <em>{{ t('slide.manage.clickToUpload') }}</em></div>
                  <template #tip><div class="el-upload__tip">{{ t('slide.manage.htmlUploadTip') }}</div></template>
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
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Picture, Document, UploadFilled, Loading, Files } from '@element-plus/icons-vue'
import { getCourseById } from '@/api/course'
import { getChapterById, getChapters } from '@/api/chapter'
import { listSections } from '@/api/section'
import { useUserStore } from '@/store/user'
import { getCoursewareTree } from '../../api/queryCourseware'
import { useCoursewareUpload } from '../../composables/useCoursewareUpload'
import PptCoursewareManage from '../../components/PptCoursewareManage.vue'
import HtmlCoursewareManage from '../../components/HtmlCoursewareManage.vue'
import SlidePreview from '../../components/SlidePreview.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)
const courseId = computed(() => route.params.courseId)
const chapterId = computed(() => route.params.chapterId || route.query.chapterId || null)
const sectionId = computed(() => route.query.sectionId || null)
// 【V333】类型限定模式：?type=HTML / ?type=PPT（从独立管理页进入时只允许该类型）
const restrictedType = computed(() => {
  const rt = route.query.type
  return rt === 'HTML' || rt === 'PPT' ? rt : null
})
function clearRestrictedType() {
  const q = { ...route.query }
  delete q.type
  router.replace({ path: route.path, query: q })
}

// 【i18n】类型不一致提示（限定类型与课时实际类型冲突）
const typeMismatchText = computed(() => {
  if (!tree.value?.type || !restrictedType.value) return ''
  return t('slide.manage.typeMismatch', {
    actual: tree.value.type === 'PPT' ? t('slide.manage.pptCourseware') : t('slide.manage.htmlCourseware'),
    restricted: restrictedType.value === 'PPT' ? t('slide.manage.pptCourseware') : t('slide.manage.htmlCourseware')
  })
})

// 【i18n】空状态标题：该课时/该章节暂无课件
const noCoursewareTitle = computed(() =>
  t('slide.manage.noCoursewareTitle', {
    scope: sectionId.value ? t('slide.manage.scopeSection') : t('slide.manage.scopeChapter')
  })
)

// 【i18n】创建卡副标题：限定类型 / 自由选择两种文案
const createSubText = computed(() => {
  if (restrictedType.value) {
    const typeLabel = restrictedType.value === 'PPT' ? t('slide.manage.pptCourseware') : t('slide.manage.htmlCourseware')
    return t('slide.manage.createSubRestricted', { type: typeLabel })
  }
  const scope = sectionId.value ? t('slide.manage.scopeSection') : t('slide.manage.scopeChapter')
  return t('slide.manage.createSubFree', { scope })
})

const courseTitle = ref('')
const chapterTitle = ref('')
// D-3：章节选项（课程级入口自动选择第一个章节 + 顶部切换器）
const chapterOptions = ref([])
const tree = ref(null)
const typeLoading = ref(true)
const sectionsLoading = ref(false)
const sectionStatus = ref([])
// P2-5：章节级预览 dialog 状态（previewSectionId = 课时 id = sectionId）
const showSectionPreview = ref(false)
const previewSectionId = ref(null)

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
    ElMessage.error(e?.response?.data?.message || t('slide.manage.loadTreeFailed'))
  } finally {
    typeLoading.value = false
  }
}

async function loadSectionOverview() {
  if (!chapterId.value || sectionId.value) return
  sectionsLoading.value = true
  try {
    const res = await listSections(courseId.value, chapterId.value, { page: 0, size: 100 })
    const items = res?.data?.items || res?.data?.records || res?.data || []
    const rows = Array.isArray(items) ? items : []
    // 并行取各课时课件类型（章节课时数有限，避免逐个串行等待）
    const statuses = await Promise.all(rows.map(async (s) => {
      let type = null
      try {
        const tw = await getCoursewareTree(courseId.value, s.id, null)
        type = tw?.data?.type === 'EMPTY' ? null : tw?.data?.type
      } catch { /* 单课时加载失败按无课件处理 */ }
      return { id: s.id, title: s.title || t('slide.manage.sectionFallbackTitle', { id: s.id }), type }
    }))
    sectionStatus.value = statuses
  } catch (e) {
    ElMessage.warning(t('slide.manage.loadSectionsFailed'))
  } finally {
    sectionsLoading.value = false
  }
}

function goManageSection(id) {
  router.push({ path: `/teacher/courses/${courseId.value}/slides/manage`, query: { sectionId: id } })
}

// P2-5：打开章节级课时预览（row.id = sectionId）
function openSectionPreview(id) {
  previewSectionId.value = id
  showSectionPreview.value = true
}

async function handleCreateUpload(file, type) {
  const ok = await upload.handleUpload(file, type)
  return ok
}

// D-3：顶部章节切换器 —— 切换后回到该章节的课时课件概览（移除 sectionId 上下文）
function switchChapter(id) {
  const q = { ...route.query, chapterId: id }
  delete q.sectionId
  router.push({ path: route.path, query: q })
}

async function loadChapters() {
  if (!courseId.value) return
  try {
    const res = await getChapters({ courseId: courseId.value, size: 100 })
    chapterOptions.value = res?.data?.items || res?.data || []
  } catch {
    chapterOptions.value = []
  }
}

onMounted(async () => {
  if (courseId.value) {
    try {
      const c = await getCourseById(courseId.value)
      courseTitle.value = c?.data?.title || ''
    } catch { /* 标题加载失败不阻断 */ }
  }
  await loadChapters()
  // D-3 P1-C：课程级入口（无 chapterId/sectionId）→ 自动选择第一个章节，
  // 教师直接进入章节级课件管理（不再停留在只有创建卡的"死路"）。
  if (!chapterId.value && !sectionId.value && chapterOptions.value.length > 0) {
    const q = { ...route.query, chapterId: chapterOptions.value[0].id }
    await router.replace({ path: route.path, query: q })
  }
  if (chapterId.value) {
    try {
      const ch = await getChapterById(chapterId.value)
      chapterTitle.value = ch?.data?.title || ''
    } catch { /* 标题加载失败不阻断 */ }
  }
  await loadTree()
  await loadSectionOverview()
})
onUnmounted(() => upload.stopRenderPolling())
</script>

<style scoped>
.slide-manage { padding: 20px; max-width: 1440px; margin: 0 auto; }
.breadcrumb-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; gap: 12px; flex-wrap: wrap; }
.context-tags { display: flex; gap: 8px; }
.sm-chapter-switch { width: 220px; }
.sm-chapter-overview { margin-bottom: 20px; }
.sm-co-title { margin: 0 0 10px; font-size: 15px; }
.sm-co-table { max-width: 720px; }
.sm-co-tip { margin-top: 10px; max-width: 720px; }
/* L0 Task 3: 章节无课时空状态 → 明确引导 */
.sm-co-empty {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 8px; padding: 40px 24px; max-width: 720px; margin-bottom: 12px;
  background: var(--el-fill-color-light); border: 1px dashed var(--el-border-color);
  border-radius: 8px; text-align: center;
}
.sm-co-empty-icon { color: var(--el-text-color-placeholder); }
.sm-co-empty-title { margin: 0; font-size: 15px; font-weight: 600; color: var(--el-text-color-primary); }
.sm-co-empty-desc { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); }
.sm-body { min-height: 320px; }
.sm-type-mismatch { max-width: 1000px; margin: 0 auto 14px; }
.sm-type-mismatch :deep(.el-alert__content) { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.sm-render { display: flex; align-items: center; justify-content: center; gap: 10px; padding: 80px 0; color: var(--el-text-color-secondary); font-size: 15px; }
.sm-create-card { max-width: 1000px; margin: 0 auto; }
.sm-create-title { margin: 0 0 6px; font-size: 18px; }
.sm-create-sub { margin: 0; color: var(--el-text-color-secondary); font-size: 13px; }
.sm-create-options { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.sm-create-options-single { grid-template-columns: 1fr; max-width: 480px; margin: 0 auto; }
.sm-option { border: 1px solid var(--el-border-color-light); border-radius: 10px; padding: 24px; text-align: center; }
.sm-option-icon { color: var(--el-color-primary); margin-bottom: 8px; }
.sm-option h3 { margin: 0 0 8px; font-size: 16px; }
.sm-option-desc { color: var(--el-text-color-secondary); font-size: 13px; margin: 0 0 16px; min-height: 38px; }
.sm-upload :deep(.el-upload) { width: 100%; }
@media (max-width: 768px) {
  .sm-create-options { grid-template-columns: 1fr; }
}
</style>
