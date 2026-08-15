<!--
  练习列表
  路由路径: /courses/:courseId/exercises
  Phase 1
  Author: jackie
-->
<template>
  <div class="exercise-list-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">{{ $t('course.home') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('course.courseMgmt') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('exercise.list') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 顶栏筛选卡 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('course.title')">
          <el-select v-model="searchForm.courseId" :placeholder="$t('exercise.selectCourse')" clearable class="filter-input-w200" @change="handleCourseChange">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('course.chapter')">
          <el-select v-model="searchForm.chapterId" :placeholder="$t('exercise.selectChapter')" clearable class="filter-input-w200" :disabled="!searchForm.courseId">
            <el-option v-for="item in chapterOptions" :key="item.id" :label="`${item.title}${item.sectionType ? `（${chapterTypeLabel(item.sectionType)}）` : ''}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('userSearch.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('exercise.list') }}</span>
          <el-button type="primary" v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" @click="handleCreate">{{ $t('exercise.create') }}</el-button>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="tableData.length === 0" :description="$t('exercise.emptyData')" />
      <el-table v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column prop="title" :label="$t('course.tableTitle')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="courseTitle" :label="$t('course.title')" min-width="120" />
        <el-table-column prop="chapterTitle" :label="$t('course.chapter')" min-width="120" />
        <el-table-column prop="questionCount" :label="$t('exercise.questionCount')" width="100" align="center">
          <template #default="{ row }">
            {{ row.questionCount ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="timeLimit" :label="$t('exercise.timeLimitMin')" width="120" align="center">
          <template #default="{ row }">
            {{ row.timeLimit ? $t('exercise.minutes', { count: row.timeLimit }) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="passScore" :label="$t('exercise.passScore')" width="100" align="center">
          <template #default="{ row }">
            {{ row.passScore ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="primary" link size="small" @click="handleSelectQuestions(row)">{{ $t('exercise.selectQuestions') }}</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="primary" link size="small" @click="handleEdit(row)">{{ $t('app.edit') }}</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="danger" link size="small" @click="handleDelete(row)">{{ $t('app.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap" v-if="!loading && tableData.length > 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,sizes,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" :aria-label="$t('course.paginationAria')"
/>
      </div>
    </el-card>

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" @close="handleDialogClose" :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item :label="$t('course.title')" prop="courseId">
          <el-select v-model="formData.courseId" :placeholder="$t('exercise.selectCourse')" class="full-width" @change="handleFormCourseChange">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('course.chapter')" prop="chapterIds">
          <el-select v-model="formData.chapterIds" :placeholder="$t('exercise.selectChaptersMulti')" multiple collapse-tags class="full-width" :disabled="!formData.courseId">
            <el-option v-for="item in formChapterOptions" :key="item.id" :label="`${item.title}${item.sectionType ? `（${chapterTypeLabel(item.sectionType)}）` : ''}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('course.tableTitle')" prop="title">
          <el-input v-model="formData.title" :placeholder="$t('exercise.titlePlaceholder')" />
        </el-form-item>

        <!-- 题库统计 + 随机选题 -->
        <el-divider v-if="formData.courseId" />
        <el-form-item v-if="formData.courseId" :label="$t('exercise.bankStats')">
          <div class="bank-stats">
            <el-tag v-for="s in bankStats" :key="s.type" :type="s.count > 0 ? 'primary' : 'info'" size="small" class="stat-tag" style="margin:2px">
              {{ $t('exercise.statCount', { label: s.label, count: s.count }) }}
            </el-tag>
            <el-tag type="primary" size="small" effect="dark" style="margin:2px">{{ $t('exercise.totalCount', { count: totalBankCount }) }}</el-tag>
          </div>
        </el-form-item>
        <el-form-item v-if="formData.courseId" :label="$t('exercise.randomPick')">
          <div class="random-pick" style="display:flex;flex-direction:column;gap:6px;width:100%">
            <el-select v-model="pickDifficulty" :placeholder="$t('exercise.difficultyFilter')" clearable size="small" style="width:120px">
              <el-option :label="$t('course.difficultyEasy')" value="EASY" />
              <el-option :label="$t('course.difficultyMedium')" value="MEDIUM" />
              <el-option :label="$t('course.difficultyHard')" value="HARD" />
            </el-select>
            <div v-for="s in bankStats" :key="s.type" class="pick-row" style="display:flex;align-items:center;gap:8px">
              <span class="pick-label" style="width:60px;font-size:13px">{{ s.label }}</span>
              <el-input-number v-model="s.pickCount" :min="0" :max="s.count" size="small" controls-position="right" style="width:130px" />
              <span style="font-size:12px;color:var(--el-text-color-secondary)">{{ $t('exercise.countSuffix', { count: s.count }) }}</span>
            </div>
            <el-button type="success" size="small" :disabled="totalPickCount === 0" @click="handleRandomPick" style="width:160px">
              {{ $t('exercise.randomPickCount', { count: totalPickCount }) }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="formData.courseId && pickedQuestions.length > 0" :label="$t('exercise.selected')">
          <el-tag type="success">{{ $t('exercise.pickedCount', { count: pickedQuestions.length }) }}</el-tag>
        </el-form-item>
        <el-divider v-if="formData.courseId" />

        <el-form-item :label="$t('exercise.passScore')" prop="passScore">
          <el-input-number v-model="formData.passScore" :min="0" :max="100" class="full-width" />
        </el-form-item>
        <el-form-item :label="$t('exercise.timeLimitLabel')" prop="timeLimit">
          <el-input-number v-model="formData.timeLimit" :min="0" :placeholder="$t('exercise.unlimitedHint')" class="full-width" />
        </el-form-item>
        <el-form-item :label="$t('exercise.maxAttempts')" prop="maxAttempts">
          <el-input-number v-model="formData.maxAttempts" :min="0" :placeholder="$t('exercise.unlimitedHint')" class="full-width" />
        </el-form-item>
        <el-form-item :label="$t('exercise.shuffleQuestions')" prop="shuffleQuestions">
          <el-switch v-model="formData.shuffleQuestions" />
        </el-form-item>
        <el-form-item :label="$t('exercise.shuffleOptions')" prop="shuffleOptions">
          <el-switch v-model="formData.shuffleOptions" />
        </el-form-item>
        <el-form-item :label="$t('exercise.description')" prop="description">
          <el-input v-model="formData.description" type="textarea" :placeholder="$t('exercise.descriptionPlaceholder')" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="submitLoading" @click="handleSubmit">{{ $t('course.dialogConfirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 选题组卷弹窗 -->
    <el-dialog v-model="questionPickerVisible" :title="$t('exercise.pickQuestionsTitle')" width="900px" @close="handleQuestionPickerClose" :close-on-press-escape="true">
      <div class="question-picker">
        <!-- 筛选区 -->
        <el-card class="picker-filter-card" shadow="never">
          <el-form :inline="true" :model="questionSearchForm" @submit.prevent>
            <el-form-item :label="$t('question.questionType')">
              <el-select v-model="questionSearchForm.questionType" :placeholder="$t('question.selectQuestionType')" clearable>
                <el-option :label="$t('question.typeSingle')" value="SINGLE" />
                <el-option :label="$t('question.typeMultiple')" value="MULTIPLE" />
                <el-option :label="$t('question.typeJudge')" value="JUDGE" />
                <el-option :label="$t('question.typeShortAnswer')" value="SHORT_ANSWER" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('course.difficulty')">
              <el-select v-model="questionSearchForm.difficulty" :placeholder="$t('question.selectDifficulty')" clearable>
                <el-option :label="$t('course.difficultyEasy')" value="EASY" />
                <el-option :label="$t('course.difficultyMedium')" value="MEDIUM" />
                <el-option :label="$t('course.difficultyHard')" value="HARD" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('course.category')">
              <el-select v-model="questionSearchForm.categoryId" :placeholder="$t('question.selectCategory')" clearable>
                <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.name" :value="cat.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleQuestionSearch">{{ $t('common.search') }}</el-button>
              <el-button @click="handleQuestionReset">{{ $t('common.reset') }}</el-button>
            </el-form-item>
          </el-form>
        </el-card>
        <!-- 题目列表 -->
        <el-table
          ref="questionTableRef"
          v-loading="questionLoading" :aria-busy="questionLoading"
          :data="questionTableData"
          stripe
          border
          height="350px"
          @selection-change="handleQuestionSelectionChange"
        >
          <el-table-column type="selection" width="55" />
          <el-table-column type="index" :label="$t('course.index')" width="60" align="center" />
          <el-table-column prop="questionType" :label="$t('question.questionType')" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.questionType === 'SINGLE'" type="primary" size="small">{{ $t('question.typeSingle') }}</el-tag>
              <el-tag v-else-if="row.questionType === 'MULTIPLE'" type="success" size="small">{{ $t('question.typeMultiple') }}</el-tag>
              <el-tag v-else-if="row.questionType === 'JUDGE'" type="warning" size="small">{{ $t('question.typeJudge') }}</el-tag>
              <el-tag v-else-if="row.questionType === 'SHORT_ANSWER'" type="info" size="small">{{ $t('question.typeShortAnswer') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="difficulty" :label="$t('course.difficulty')" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.difficulty === 1" type="success" size="small">{{ $t('course.difficultyEasy') }}</el-tag>
              <el-tag v-else-if="row.difficulty === 2" type="warning" size="small">{{ $t('course.difficultyMedium') }}</el-tag>
              <el-tag v-else-if="row.difficulty === 3" type="danger" size="small">{{ $t('course.difficultyHard') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="content" :label="$t('question.questionContent')" min-width="300" show-overflow-tooltip />
          <el-table-column prop="score" :label="$t('question.score')" width="70" align="center" />
        </el-table>
        <div class="picker-footer">
          <span class="selected-count">{{ $t('exercise.selectedCount', { count: selectedQuestions.length }) }}</span>
          <el-pagination
            v-model:current-page="questionPage"
            v-model:page-size="questionSize"
            :total="questionTotal"
            :page-sizes="[10, 20, 50]"
            layout="total,sizes,prev,pager,next"
            small
            @size-change="handleQuestionSizeChange"
            @current-change="handleQuestionPageChange" :aria-label="$t('course.paginationAria')"
/>
        </div>
      </div>
      <template #footer>
        <el-button @click="questionPickerVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="questionSubmitLoading" :disabled="questionSubmitLoading" @click="handleAddQuestions">{{ $t('exercise.addToExercise') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUrlPagination } from '@/composables/useUrlPagination';
import { swrCache } from '@/composables/useStaleWhileRevalidate';
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getExercises, createExercise, updateExercise, deleteExercise, addQuestionsToExercise, removeQuestionFromExercise } from '@/api/exercise'
import { getQuestions } from '@/api/question'
import { fetchAllPages } from '@/utils/fetchAllPages'
import { getCourses } from '@/api/course'
import { getChapters } from '@/api/chapter'
import { getCategories } from '@/api/course-category'

const { t } = useI18n()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)
const isTeacher = computed(() => userStore.role === 'TEACHER')

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const courseOptions = ref([])
const chapterOptions = ref([])
const formChapterOptions = ref([])

const searchForm = reactive({
  courseId: null,
  chapterId: null
})

// P2-14: URL 分页同步
const { bindToQuery } = useUrlPagination()
bindToQuery(page, size, searchForm, ['courseId', 'chapterId'], ['courseId', 'chapterId'])

const dialogVisible = ref(false)
const dialogTitle = ref(t('exercise.create'))
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

// 选题组卷相关
const questionPickerVisible = ref(false)
const questionTableRef = ref(null)
const questionLoading = ref(false)
const questionSubmitLoading = ref(false)
const questionTableData = ref([])
const questionTotal = ref(0)
const questionPage = ref(1)
const questionSize = ref(10)
const selectedQuestions = ref([])
const categoryOptions = ref([])
const currentCourseId = ref(null)

const questionSearchForm = reactive({
  questionType: '',
  difficulty: '',
  categoryId: ''
})

const formData = reactive({
  courseId: null,
  chapterIds: [],
  title: '',
  passScore: 60,
  timeLimit: null,
  maxAttempts: null,
  shuffleQuestions: false,
  shuffleOptions: false
})

// ===== 题库统计 & 随机选题 =====
// P1-C-7: 难度字符串→整数转换
const DIFFICULTY_MAP = { 'EASY': 1, 'MEDIUM': 2, 'HARD': 3 }
function resolveDifficulty(val) {
  if (!val) return undefined
  const key = String(val).toUpperCase()
  return DIFFICULTY_MAP[key] || undefined
}

const TYPE_LABELS = { SINGLE: t('question.typeSingle'), MULTIPLE: t('question.typeMultiple'), JUDGE: t('question.typeJudge'), FILL: t('question.typeFill'), SHORT_ANSWER: t('question.typeShortAnswer') }
const bankStats = ref([])
const totalBankCount = ref(0)
const totalPickCount = computed(() => bankStats.value.reduce((s, t) => s + (t.pickCount || 0), 0))
const pickDifficulty = ref('')
const pickedQuestions = ref([])

watch(() => formData.courseId, async (val) => {
  pickedQuestions.value = []
  if (!val) { bankStats.value = []; totalBankCount.value = 0; return }
  try {
    const params = { courseId: val }
    if (pickDifficulty.value) params.difficulty = resolveDifficulty(pickDifficulty.value)
    const items = await fetchAllPages(getQuestions, params, 100)
    const filterDiff = pickDifficulty.value ? resolveDifficulty(pickDifficulty.value) : undefined
    const filtered = filterDiff != null ? items.filter(q => q.difficulty === filterDiff) : items
    totalBankCount.value = filtered.length
    const groups = {}
    for (const q of filtered) { const t = q.questionType || 'OTHER'; groups[t] = (groups[t] || 0) + 1 }
    bankStats.value = Object.entries(TYPE_LABELS)
      .map(([type, label]) => ({ type, label, count: groups[type] || 0, pickCount: 0 }))
      .filter(s => s.count > 0)
  } catch { bankStats.value = []; totalBankCount.value = 0; ElMessage.warning(t('exercise.bankStatsLoadFailed')) }
})
watch(pickDifficulty, () => {
  if (formData.courseId) { const cb = formData.courseId; formData.courseId = null; setTimeout(() => formData.courseId = cb, 0) }
})

async function handleRandomPick() {
  const picks = {}
  for (const s of bankStats.value) { if (s.pickCount > 0) picks[s.type] = s.pickCount }
  try {
    const params = { courseId: formData.courseId }
    if (pickDifficulty.value) params.difficulty = resolveDifficulty(pickDifficulty.value)
    let all = await fetchAllPages(getQuestions, params, 100)
    const rd = pickDifficulty.value ? resolveDifficulty(pickDifficulty.value) : undefined
    if (rd != null) all = all.filter(q => q.difficulty === rd)
    const picked = []
    for (const [type, count] of Object.entries(picks)) {
      const pool = all.filter(q => (q.questionType || '') === type)
      const shuffled = [...pool].sort(() => Math.random() - 0.5)
      picked.push(...shuffled.slice(0, count))
    }
    pickedQuestions.value = picked
    bankStats.value.forEach(s => { s.pickCount = 0 })
  } catch { ElMessage.warning(t('exercise.randomPickFailed')) }
}

const formRules = computed(() => ({
  courseId: [{ required: true, message: t('exercise.selectCourse'), trigger: 'change' }],
  chapterIds: [{ required: true, message: t('exercise.selectChapter'), trigger: 'change' }],
  title: [{ required: true, message: t('exercise.titlePlaceholder'), trigger: 'blur' }]
}))

const fetchCourseOptions = async () => {
  try {
    const params = { page: 0, size: 100 }
    // P2-15: 使用 userId getter 替代 userInfo?.id 以保持一致
    if (isTeacher.value) params.teacherId = userStore.userId
    const { data } = await getCourses(params)
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error(t('course.fetchCoursesFailed'))
  }
}

const fetchChapterOptions = async (courseId) => {
  if (!courseId) {
    chapterOptions.value = []
    return
  }
  try {
    const { data } = await getChapters({ courseId })
    chapterOptions.value = data.items || []
  } catch {
    ElMessage.error(t('exercise.fetchChaptersFailed'))
  }
}

const fetchData = async () => {
  if (!searchForm.courseId) {
    tableData.value = []
    totalElements.value = 0
    return
  }
  loading.value = true
  try {
    const params = {
      courseId: searchForm.courseId,
      chapterId: searchForm.chapterId || undefined,
      page: page.value - 1,
      size: size.value
    }
    const { data } = await getExercises(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error(t('exercise.fetchListFailed'))
  } finally {
    loading.value = false
  }
}

const handleCourseChange = (val) => {
  searchForm.chapterId = null
  if (val) {
    fetchChapterOptions(val)
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const chapterTypeLabel = (type) => {
  const map = { VIDEO: t('course.typeVideo'), INTERACTIVE: t('course.courseware'), EXERCISE: t('course.exercise'), OFFLINE: t('exercise.typeOffline') }
  return map[type] || (type || t('course.unknown'))
}

const handleReset = () => {
  searchForm.courseId = null
  searchForm.chapterId = null
  page.value = 1
  tableData.value = []
  totalElements.value = 0
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const handleCreate = async () => {
  dialogTitle.value = t('exercise.create')
  isEdit.value = false
  currentId.value = null
  formData.courseId = searchForm.courseId
  formData.title = ''
  formData.passScore = 60
  formData.description = ''
  formData.timeLimit = null
  formData.maxAttempts = null
  formData.shuffleQuestions = false
  formData.shuffleOptions = false
  if (formData.courseId) {
    await handleFormCourseChange(formData.courseId)
    formData.chapterIds = searchForm.chapterId ? [searchForm.chapterId] : []
  }
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  dialogTitle.value = t('exercise.edit')
  isEdit.value = true
  currentId.value = row.id
  formData.courseId = row.courseId
  formData.chapterIds = row.chapterIds || []
  formData.title = row.title
  formData.passScore = row.passScore || 60
  formData.description = row.description || ''
  formData.timeLimit = row.timeLimit || null
  formData.maxAttempts = row.maxAttempts || null
  formData.shuffleQuestions = row.shuffleQuestions || false
  formData.shuffleOptions = row.shuffleOptions || false
  // P1-C: 始终按练习所属课程加载章节(不依赖搜索区缓存,避免课程错配)
  try {
    const { data } = await getChapters({ courseId: row.courseId, size: 100 })
    formChapterOptions.value = data?.items || []
  } catch { formChapterOptions.value = [] }
  dialogVisible.value = true
}

const handleFormCourseChange = async (val) => {
  formData.chapterIds = []
  if (val) {
    try {
      const { data } = await getChapters({ courseId: val })
      formChapterOptions.value = data.items || []
    } catch {
      formChapterOptions.value = []
      console.warn('[ExerciseList] 获取章节列表失败', val)
    }
  } else {
    formChapterOptions.value = []
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(t('exercise.confirmDelete'), t('course.hintTitle'), { type: 'warning' })
    await deleteExercise(row.id)
    ElMessage.success(t('course.deleteSuccess'))
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('course.deleteFailed'))
    }
  }
}

const handleSubmit = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (submitLoading.value) return
  if (!formRef.value) return
  submitLoading.value = true
  await formRef.value.validate(async (valid) => {
    if (!valid) { submitLoading.value = false; return }
    try {
      let exerciseId = currentId.value
      if (isEdit.value) {
        await updateExercise(exerciseId, formData)
        ElMessage.success(t('question.editSuccess'))
      } else {
        const { data } = await createExercise(formData)
        exerciseId = data.id
        ElMessage.success(t('course.createSuccess'))
      }
      // 自动保存随机选题
      if (pickedQuestions.value.length > 0) {
        const qIds = pickedQuestions.value.map(q => q.id).filter(Boolean)
        if (qIds.length > 0) await addQuestionsToExercise(exerciseId, { questionIds: qIds })
      }
      dialogVisible.value = false
      fetchData()
    } catch {
      ElMessage.error(isEdit.value ? t('exercise.editFailed') : t('course.createFailed'))
    } finally {
      submitLoading.value = false
      pickedQuestions.value = []
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

// 选题组卷相关方法
const handleSelectQuestions = async (row) => {
  currentId.value = row.id
  currentCourseId.value = row.courseId
  questionPickerVisible.value = true
  selectedQuestions.value = []
  questionPage.value = 1
  questionSize.value = 10
  questionSearchForm.questionType = ''
  questionSearchForm.difficulty = ''
  questionSearchForm.categoryId = ''
  await fetchCategoryOptions()
  await fetchQuestionData()
}

const fetchCategoryOptions = async () => {
  try {
    const { data } = await getCategories({ size: 100 })
    categoryOptions.value = data.items || []
  } catch {
    // 忽略错误
  }
}

const fetchQuestionData = async () => {
  questionLoading.value = true
  try {
    const params = {
      page: questionPage.value - 1,
      size: questionSize.value,
      courseId: currentCourseId.value,
      questionType: questionSearchForm.questionType || undefined,
      difficulty: resolveDifficulty(questionSearchForm.difficulty),
      categoryId: questionSearchForm.categoryId || undefined
    }
    const { data } = await getQuestions(params)
    questionTableData.value = data.items || []
    questionTotal.value = data.totalElements || 0
  } catch {
    ElMessage.error(t('question.fetchListFailed'))
  } finally {
    questionLoading.value = false
  }
}

const handleQuestionSearch = () => {
  questionPage.value = 1
  fetchQuestionData()
}

const handleQuestionReset = () => {
  questionSearchForm.questionType = ''
  questionSearchForm.difficulty = ''
  questionSearchForm.categoryId = ''
  questionPage.value = 1
  fetchQuestionData()
}

const handleQuestionSizeChange = () => {
  questionPage.value = 1
  fetchQuestionData()
}

const handleQuestionPageChange = () => {
  fetchQuestionData()
}

const handleQuestionSelectionChange = (selection) => {
  selectedQuestions.value = selection
}

const handleAddQuestions = async () => {
  if (selectedQuestions.value.length === 0) {
    ElMessage.warning(t('exercise.selectQuestionFirst'))
    return
  }
  questionSubmitLoading.value = true
  try {
    const questionIds = selectedQuestions.value.map(q => q.id)
    await addQuestionsToExercise(currentId.value, { questionIds })
    ElMessage.success(t('exercise.addSuccess'))
    questionPickerVisible.value = false
    fetchData()
  } catch {
    ElMessage.error(t('exercise.addFailed'))
  } finally {
    questionSubmitLoading.value = false
  }
}

const handleQuestionPickerClose = () => {
  questionTableRef.value?.clearSelection()
  selectedQuestions.value = []
  currentCourseId.value = null
}

onMounted(() => {
  fetchCourseOptions()
  if (searchForm.courseId) fetchChapterOptions(searchForm.courseId)
})
</script>

<style scoped>
.exercise-list-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.filter-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.table-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.table-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.table-card :deep(.el-card__header) {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
}

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: center;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header) th {
  color: var(--el-text-color-primary);
}

.data-table :deep(.el-table__row) {
  transition: background-color var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--role-primary-light-9);
}

.data-table :deep(.el-table__row--striped > td) {
  background: transparent;
}

.full-width {
  width: 100%;
}

.search-input,
.filter-input {
  width: 160px;
  border-radius: var(--radius-md);
}

.search-select,
.filter-select {
  width: 160px;
}

.filter-input-w200 {
  width: 200px;
}

.question-picker {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.picker-filter-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.picker-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--space-3);
}

.selected-count {
  color: var(--el-text-color-secondary);
  font-size: var(--text-sm);
}

:deep(.el-button) {
  border-radius: var(--radius-md);
}

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}

@media (max-width: 768px) {
  .exercise-list-page {
    padding: var(--space-4);
  }

  .filter-card {
    margin-bottom: var(--space-4);
  }

  .filter-input-w200 {
    width: 100%;
  }

  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
  }

  .pagination-wrap {
    justify-content: center;
  }
}
</style>
