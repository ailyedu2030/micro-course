<template>
  <div class="exam-list-page">
    <header class="page-header">
      <h1>试卷管理</h1>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="showCreate = true">{{ chapterIdFromRoute ? '智能组卷' : '新增试卷' }}</el-button>
        <el-button v-if="chapterIdFromRoute" type="success" :icon="Plus" @click="openScheduleDialog">安排考试</el-button>
      </div>
    </header>

    <!-- 搜索筛选区 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="所属课程">
          <el-select v-model="searchForm.courseId" placeholder="选择课程" clearable class="filter-input-w200" @change="onSearchCourseChange" filterable>
            <el-option v-for="c in courseOptions" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节">
          <el-select v-model="searchForm.chapterId" placeholder="选择章节" clearable class="filter-input-w200" :disabled="!searchForm.courseId">
            <el-option v-for="ch in searchChapterOptions" :key="ch.id" :label="ch.title" :value="ch.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <el-table :data="exams" stripe v-loading="loading">
        <template #empty><el-empty description="暂未创建试卷，点击「新增试卷」一键组卷" /></template>
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

    <!-- 安排考试对话框 -->
    <el-dialog v-model="showSchedule" title="安排考试到本章节" width="600px" @close="resetScheduleForm">
      <el-form ref="scheduleFormRef" label-width="100px">
        <el-form-item label="选择试卷">
          <el-select v-model="scheduleForm.examId" placeholder="从已有试卷中选择" filterable class="full-width">
            <el-option v-for="e in scheduleExamOptions" :key="e.id" :label="e.title" :value="e.id">
              <span>{{ e.title }}</span>
              <span style="float:right;color:#909399;font-size:12px">{{ getCourseTitle(e.courseId) }} · {{ e.questionCount }}题 · {{ e.totalScore }}分</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-divider>考试配置</el-divider>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="限时(分)">
              <el-input-number v-model="scheduleForm.timeLimit" :min="0" :step="10" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="答题次数">
              <el-input-number v-model="scheduleForm.maxAttempts" :min="0" :step="1" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="及格分(%)">
              <el-input-number v-model="scheduleForm.passScore" :min="0" :max="100" :step="5" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示答案">
              <el-select v-model="scheduleForm.showAnswerWhen" class="full-width">
                <el-option label="提交后立即显示" value="AFTER_SUBMIT" />
                <el-option label="及格后显示" value="AFTER_PASS" />
                <el-option label="不显示" value="NEVER" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="乱序题目">
              <el-switch v-model="scheduleForm.shuffleQuestions" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="乱序选项">
              <el-switch v-model="scheduleForm.shuffleOptions" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="showSchedule = false">取消</el-button>
        <el-button type="primary" :loading="scheduling" :disabled="scheduling" @click="submitSchedule">确认安排</el-button>
      </template>
    </el-dialog>

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
        <el-button type="primary" :loading="generating" :disabled="generating" @click="handleGenerate">一键组卷</el-button>
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
import { updateExercise } from '@/api/exercise'
import { useUserStore } from '@/store/user'

const route = useRoute()
const chapterIdFromRoute = computed(() => route.params.chapterId || route.query.chapterId)

const userStore = useUserStore()
const loading = ref(false)
const exams = ref([])
const showCreate = ref(false)
const showSchedule = ref(false)
const scheduling = ref(false)
const scheduleFormRef = ref(null)
const scheduleExamOptions = ref([])
const scheduleForm = reactive({
  examId: null, timeLimit: 0, maxAttempts: 0, passScore: 60,
  showAnswerWhen: 'AFTER_SUBMIT', shuffleQuestions: false, shuffleOptions: false
})
const generating = ref(false)
const courseOptions = ref([])
const chapterOptions = ref([])
const searchChapterOptions = ref([])
const searchForm = ref({ courseId: null, chapterId: null })
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
    // 搜索筛选条件优先于路由参数
    if (searchForm.value.chapterId) {
      params.chapterId = Number(searchForm.value.chapterId)
    } else if (chapterIdFromRoute.value) {
      params.chapterId = Number(chapterIdFromRoute.value)
    }
    if (searchForm.value.courseId) {
      params.courseId = Number(searchForm.value.courseId)
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

async function onSearchCourseChange(courseId) {
  searchForm.value.chapterId = null
  if (!courseId) { searchChapterOptions.value = []; return }
  try {
    const { data } = await getChapters({ courseId, size: 100 })
    searchChapterOptions.value = data?.items || []
  } catch { searchChapterOptions.value = [] }
}

function handleSearch() {
  page.value = 1
  loadExams()
}

function handleReset() {
  searchForm.value = { courseId: null, chapterId: null }
  searchChapterOptions.value = []
  page.value = 1
  loadExams()
}

// 安排考试
async function openScheduleDialog() {
  showSchedule.value = true
  try {
    const { data } = await getExamList({ isExam: true, size: 200 })
    scheduleExamOptions.value = data?.items || []
  } catch { scheduleExamOptions.value = [] }
}
function resetScheduleForm() {
  scheduleForm.examId = null; scheduleForm.timeLimit = 0; scheduleForm.maxAttempts = 0
  scheduleForm.passScore = 60; scheduleForm.showAnswerWhen = 'AFTER_SUBMIT'
  scheduleForm.shuffleQuestions = false; scheduleForm.shuffleOptions = false
}
async function submitSchedule() {
  if (!scheduleForm.examId || !chapterIdFromRoute.value) {
    ElMessage.warning('请选择试卷')
    return
  }
  scheduling.value = true
  try {
    // 先获取现有章节关联,追加当前章节(避免覆盖已有安排)
    let existingChapterIds = []
    try {
      const { data } = await getExamById(scheduleForm.examId)
      existingChapterIds = data?.chapterIds || []
    } catch {}
    const newChapterId = Number(chapterIdFromRoute.value)
    const mergedIds = existingChapterIds.includes(newChapterId)
      ? existingChapterIds
      : [...existingChapterIds, newChapterId]

    await updateExercise(scheduleForm.examId, {
      chapterId: newChapterId,
      chapterIds: mergedIds,
      timeLimit: scheduleForm.timeLimit > 0 ? scheduleForm.timeLimit : null,
      maxAttempts: scheduleForm.maxAttempts > 0 ? scheduleForm.maxAttempts : null,
      passScore: scheduleForm.passScore,
      showAnswerWhen: scheduleForm.showAnswerWhen,
      shuffleQuestions: scheduleForm.shuffleQuestions,
      shuffleOptions: scheduleForm.shuffleOptions,
    })
    ElMessage.success('考试已安排到本章节')
    showSchedule.value = false
    loadExams()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '安排失败')
  } finally {
    scheduling.value = false
  }
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
.filter-input-w200 { width: 200px; }
.search-card { margin-bottom: 16px; padding: 16px 20px; }
.table-card { background: var(--el-bg-color); border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
.type-config-row { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; padding: 0 20px; }
.type-label { width: 80px; font-size: 14px; color: var(--el-text-color-regular); }
.type-count { width: 100px; }
.type-hint { font-size: 13px; color: var(--el-text-color-secondary); }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: center; padding: 16px 20px 0; border-top: 1px solid var(--el-border-color-lighter); }
:deep(.full-width) { width: 100%; }
</style>
