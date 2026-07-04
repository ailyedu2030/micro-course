<template>
  <div class="exam-list-page">
    <header class="page-header">
      <h1>试卷管理</h1>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="showCreate = true">新增试卷</el-button>
      </div>
    </header>

    <el-card class="table-card" shadow="never">
      <el-table :data="exams" stripe v-loading="loading" empty-text="暂未创建试卷">
        <el-table-column prop="title" label="试卷标题" min-width="180" />
        <el-table-column label="所属课程" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ getCourseTitle(row.courseId) }}</template>
        </el-table-column>
        <el-table-column prop="questionCount" label="题目数" width="80" align="center" />
        <el-table-column prop="totalScore" label="总分" width="80" align="center" />
        <el-table-column label="限时" width="80" align="center">
          <template #default="{ row }">{{ row.timeLimit ? row.timeLimit + 'min' : '不限' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" type="danger" :loading="deleting === row.id" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="totalElements > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page" v-model:page-size="size"
          :total="totalElements" :page-sizes="[10, 20, 50]"
          layout="total,sizes,prev,pager,next"
          @size-change="loadExams" @current-change="loadExams"
        />
      </div>
    </el-card>

    <el-dialog v-model="showCreate" title="新增试卷" width="600px" :close-on-click-modal="false" @closed="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="试卷标题" prop="title">
          <el-input v-model="createForm.title" placeholder="如：期中考试" />
        </el-form-item>
        <el-form-item label="所属课程" prop="courseId">
          <el-select v-model="createForm.courseId" placeholder="选择课程" class="full-width" filterable @change="onCourseChange">
            <el-option v-for="c in courseOptions" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="涵盖章节">
          <el-select v-model="createForm.chapterIds" placeholder="不限章节（可不选）" multiple collapse-tags clearable class="full-width" :disabled="!createForm.courseId">
            <el-option v-for="ch in chapterOptions" :key="ch.id" :label="ch.title" :value="ch.id" />
          </el-select>
        </el-form-item>
        <el-divider>题型配置</el-divider>
        <div v-for="(item, idx) in createForm.typeConfigs" :key="idx" class="type-config-row">
          <span class="type-label">{{ typeLabel(item.type) }}</span>
          <el-input-number v-model="item.count" :min="0" :max="99" size="small" class="type-count" />
          <span class="type-hint">题</span>
        </div>
        <el-form-item label="总分">
          <el-input-number v-model="createForm.totalScore" :min="0" :step="10" />
        </el-form-item>
        <el-form-item label="限时(分)">
          <el-input-number v-model="createForm.timeLimit" :min="0" :step="10" placeholder="0=不限时" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="handleGenerate">一键组卷</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getCourses } from '@/api/course'
import { getChapters, getChapterById } from '@/api/chapter'
import { getExamList, generateExam, deleteExam } from '@/api/exam'
import { useUserStore } from '@/store/user'

const route = useRoute()
const chapterIdFromRoute = computed(() => route.params.chapterId || route.query.chapterId)

const userStore = useUserStore()
const loading = ref(false)
const exams = ref([])
const showCreate = ref(false)
const generating = ref(false)
const courseOptions = ref([])
const chapterOptions = ref([])
const courseTitleMap = ref({})
const createFormRef = ref(null)
const page = ref(1)
const size = ref(20)
const totalElements = ref(0)

const createForm = reactive({
  title: '',
  courseId: null,
  chapterIds: [],
  typeConfigs: [
    { type: 'SINGLE', count: 0 },
    { type: 'MULTIPLE', count: 0 },
    { type: 'JUDGE', count: 0 },
    { type: 'FILL', count: 0 },
    { type: 'SHORT_ANSWER', count: 0 },
    { type: 'ESSAY', count: 0 },
  ],
  totalScore: 100,
  timeLimit: 0,
})

const createRules = {
  title: [{ required: true, message: '请输入试卷标题', trigger: 'blur' }],
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
}

const deleting = ref(null)

function typeLabel(t) {
  const m = {
    SINGLE: '单选题',
    MULTIPLE: '多选题',
    JUDGE: '判断题',
    FILL: '填空题（需人工批改）',
    SHORT_ANSWER: '简答题（需人工批改）',
    ESSAY: '论述题（需人工批改）',
  }
  return m[t] || t
}

function formatTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  if (isNaN(d.getTime())) return '-'
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function getCourseTitle(courseId) {
  return courseTitleMap.value[courseId] || `课程 #${courseId}`
}

// P1-修复: 在挂载时加载所有课程标题,避免列表显示"课程 #1"回退
watch(courseOptions, (list) => {
  if (Array.isArray(list)) {
    list.forEach(c => { courseTitleMap.value[c.id] = c.title })
  }
})

async function loadExams() {
  loading.value = true
  try {
    const params = { page: page.value - 1, size: size.value, isExam: true }
    // 按章节过滤 (P1-修复: manage-exam 路由应只显示本章试卷)
    if (chapterIdFromRoute.value) {
      params.chapterId = Number(chapterIdFromRoute.value)
    }
    const { data } = await getExamList(params)
    exams.value = data?.items || []
    totalElements.value = data?.totalElements || 0
  } catch {
    ElMessage.error('获取试卷列表失败')
  } finally {
    loading.value = false
  }
}

async function loadCourses() {
  try {
    // P1-修复: 不按 teacherId 过滤,管理员也能看到所有课程
    const { data } = await getCourses({ size: 200 })
    const list = data?.items || []
    courseOptions.value = list
    list.forEach(c => { courseTitleMap.value[c.id] = c.title })
  } catch { /* ignore */ }
}

async function onCourseChange(courseId) {
  createForm.chapterIds = []
  if (!courseId) { chapterOptions.value = []; return }
  try {
    const { data } = await getChapters({ courseId, size: 100 })
    chapterOptions.value = data?.items || []
  } catch { chapterOptions.value = [] }
}

function resetCreateForm() {
  createForm.title = ''
  createForm.courseId = null
  createForm.chapterIds = []
  createForm.typeConfigs.forEach(t => (t.count = 0))
  createForm.totalScore = 100
  createForm.timeLimit = 0
  chapterOptions.value = []
}

async function handleGenerate() {
  if (!createFormRef.value) return
  try {
    const valid = await createFormRef.value.validate()
    if (!valid) return
  } catch { return }

  const counts = {}
  let totalNeeded = 0
  for (const tc of createForm.typeConfigs) {
    if (tc.count > 0) { counts[tc.type] = tc.count; totalNeeded += tc.count }
  }
  if (totalNeeded === 0) {
    ElMessage.warning('请至少选择一道题')
    return
  }

  generating.value = true
  try {
    const examReq = {
      title: createForm.title,
      courseId: createForm.courseId,
      chapterIds: createForm.chapterIds.length > 0 ? createForm.chapterIds : [],
      questionCounts: counts,
      totalScore: createForm.totalScore,
      timeLimit: createForm.timeLimit > 0 ? createForm.timeLimit : null,
    }
    if (chapterIdFromRoute.value) {
      examReq.chapterId = Number(chapterIdFromRoute.value)
    }
    await generateExam(examReq)
    ElMessage.success('组卷成功')
    showCreate.value = false
    page.value = 1
    loadExams()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '组卷失败')
  } finally {
    generating.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除试卷「${row.title}」？`, '确认删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  } catch { return }
  deleting.value = row.id
  try {
    await deleteExam(row.id)
    ElMessage.success('已删除')
    exams.value = exams.value.filter(e => e.id !== row.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  } finally {
    deleting.value = null
  }
}

// P1-修复: 从 manage-exam 路由打开时,预填课程+章节信息
async function prefillFromRoute() {
  if (!chapterIdFromRoute.value) return
  try {
    const r = await getChapterById(Number(chapterIdFromRoute.value))
    const ch = r.data
    if (ch && ch.courseId) {
      const courseId = ch.courseId
      createForm.courseId = courseId
      // 触发章节选项加载
      await onCourseChange(courseId)
      // 预选本章节
      createForm.chapterIds = [Number(chapterIdFromRoute.value)]
    }
  } catch { /* ignore */ }
}

watch(() => showCreate.value, (v) => {
  if (v) prefillFromRoute()
})

onMounted(async () => {
  await loadCourses()
  if (chapterIdFromRoute.value) {
    await prefillFromRoute()
  }
  loadExams()
})
</script>

<style scoped>
.exam-list-page { padding: 24px; max-width: 1280px; margin: 0 auto; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.page-header h1 { font-size: 22px; font-weight: 600; margin: 0; }
.header-actions { display: flex; gap: 12px; }
.table-card { background: var(--el-bg-color); border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
.type-config-row { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; padding: 0 20px; }
.type-label { width: 80px; font-size: 14px; color: var(--el-text-color-regular); }
.type-count { width: 100px; }
.type-hint { font-size: 13px; color: var(--el-text-color-secondary); }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: center; padding: 16px 20px 0; border-top: 1px solid var(--el-border-color-lighter); }
:deep(.full-width) { width: 100%; }
</style>
