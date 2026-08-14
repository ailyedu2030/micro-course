<!--
  题目列表
  路由路径: /questions
  Phase 1
  Author: jackie
-->
<template>
  <div class="question-list-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">{{ $t('course.home') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('course.courseMgmt') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('question.bankTitle') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 课程选择卡 -->
    <el-card class="course-select-card" shadow="never">
      <div class="course-select-header">
        <span class="course-select-label">{{ $t('question.selectCourse') }}</span>
        <el-select v-model="selectedCourseId" :placeholder="$t('question.selectCourseFirst')" size="large" clearable filterable class="course-select-input" :aria-label="$t('question.selectCourse')" @change="onCourseChange">
          <el-option v-for="c in courseOptions" :key="c.id" :label="c.title" :value="c.id" />
        </el-select>
      </div>
    </el-card>

    <!-- 未选课程时的提示 -->
    <el-card v-if="!selectedCourse" class="empty-card" shadow="never">
      <el-empty :description="$t('question.selectCourseEmpty')" />
    </el-card>

    <!-- 已选课程：筛选 + 题目列表 -->
    <template v-if="selectedCourse">
      <!-- 当前课程信息 -->
      <div class="course-info-bar">
        <el-tag type="primary" size="large" effect="plain" class="course-tag">{{ selectedCourse.title }}</el-tag>
        <span class="course-info-hint">{{ $t('question.bankTitle') }}</span>
      </div>

      <!-- 搜索筛选卡 -->
      <el-card class="search-card filter-card" shadow="never">
        <el-form :inline="true" :model="searchForm" @submit.prevent>
          <el-form-item :label="$t('question.questionType')">
            <el-select v-model="searchForm.questionType" :placeholder="$t('question.selectQuestionType')" clearable class="filter-input-w140">
              <el-option :label="$t('question.typeSingle')" value="SINGLE" />
              <el-option :label="$t('question.typeMultiple')" value="MULTIPLE" />
              <el-option :label="$t('question.typeJudge')" value="JUDGE" />
              <el-option :label="$t('question.typeShortAnswer')" value="SHORT_ANSWER" />
              <el-option :label="$t('question.typeEssay')" value="ESSAY" />
              <el-option :label="$t('question.typeFill')" value="FILL" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('course.difficulty')">
            <el-select v-model="searchForm.difficulty" :placeholder="$t('question.selectDifficulty')" clearable class="filter-input-w120">
              <el-option :label="$t('course.difficultyEasy')" value="EASY" />
              <el-option :label="$t('course.difficultyMedium')" value="MEDIUM" />
              <el-option :label="$t('course.difficultyHard')" value="HARD" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('course.chapter')">
            <el-select v-model="searchForm.chapterId" :placeholder="$t('question.allChapters')" clearable class="filter-input-w160">
              <el-option v-for="ch in chapterOptions" :key="ch.id" :label="ch.title" :value="ch.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('course.category')">
            <el-select v-model="searchForm.categoryId" :placeholder="$t('question.selectCategory')" clearable class="filter-input-w160">
              <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.name" :value="cat.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('question.keyword')">
            <el-input v-model="searchForm.keyword" :placeholder="$t('question.questionContent')" clearable class="filter-input-w160" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">{{ $t('app.search') }}</el-button>
            <el-button @click="handleReset">{{ $t('app.reset') }}</el-button>
          </el-form-item>
        </el-form>
      </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('question.list') }}</span>
          <div class="header-actions">
            <el-upload
              :show-file-list="false"
              :before-upload="handleImportExcel"
              accept=".xlsx,.xls"
              class="upload-inline"
>
              <el-button type="success" size="small" :disabled="!selectedCourse">{{ $t('question.importExcel') }}</el-button>
            </el-upload>
            <el-button type="warning" size="small" @click="handleExportExcel">{{ $t('question.exportExcel') }}</el-button>
            <el-button type="primary" v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" :disabled="!selectedCourse" @click="handleCreate">{{ $t('question.create') }}</el-button>
          </div>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="tableData.length === 0" :description="$t('question.noQuestions')" />
      <el-table v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column prop="questionType" :label="$t('question.questionType')" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.questionType === 'SINGLE'" type="primary" size="small">{{ $t('question.typeSingle') }}</el-tag>
            <el-tag v-else-if="row.questionType === 'MULTIPLE'" type="success" size="small">{{ $t('question.typeMultiple') }}</el-tag>
            <el-tag v-else-if="row.questionType === 'JUDGE'" type="warning" size="small">{{ $t('question.typeJudge') }}</el-tag>
            <el-tag v-else-if="row.questionType === 'SHORT_ANSWER'" type="info" size="small">{{ $t('question.typeShortAnswer') }}</el-tag>
            <el-tag v-else-if="row.questionType === 'ESSAY'" type="danger" size="small">{{ $t('question.typeEssay') }}</el-tag>
            <el-tag v-else-if="row.questionType === 'FILL'" type="info" size="small">{{ $t('question.typeFill') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ row.questionType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" :label="$t('course.difficulty')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.difficulty === 1" type="success" size="small">{{ $t('course.difficultyEasy') }}</el-tag>
            <el-tag v-else-if="row.difficulty === 2" type="warning" size="small">{{ $t('course.difficultyMedium') }}</el-tag>
            <el-tag v-else-if="row.difficulty === 3" type="danger" size="small">{{ $t('course.difficultyHard') }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" :label="$t('course.category')" width="120">
          <template #default="{ row }">
            {{ row.categoryName || '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('question.relatedChapter')" min-width="120">
          <template #default="{ row }">
            <span>{{ row.chapterTitles?.join(', ') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" :label="$t('question.questionContent')" min-width="250" show-overflow-tooltip />
        <el-table-column :label="$t('app.operation')" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="info" link size="small" @click="handlePreview(row)">{{ $t('question.preview') }}</el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">{{ $t('app.edit') }}</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">{{ $t('app.delete') }}</el-button>
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
    </template>

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="handleDialogClose" :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item :label="$t('question.belongCourse')">
          <el-tag type="primary" effect="plain">{{ selectedCourse?.title }}</el-tag>
        </el-form-item>
        <el-form-item v-if="selectedCourse?.categoryName" :label="$t('question.courseCategory')">
          <el-tag type="info" effect="plain">{{ selectedCourse.categoryName }}</el-tag>
        </el-form-item>
        <el-form-item :label="$t('question.questionType')" prop="questionType">
          <el-select v-model="formData.questionType" :placeholder="$t('question.selectQuestionType')" class="full-width">
            <el-option :label="$t('question.typeSingle')" value="SINGLE" />
            <el-option :label="$t('question.typeMultiple')" value="MULTIPLE" />
            <el-option :label="$t('question.typeJudge')" value="JUDGE" />
            <el-option :label="$t('question.typeShortAnswer')" value="SHORT_ANSWER" />
            <el-option :label="$t('question.typeEssay')" value="ESSAY" />
            <el-option :label="$t('question.typeFill')" value="FILL" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('question.relatedChapter')">
          <el-select v-model="formData.chapterIds" :placeholder="$t('question.multipleSelect')" multiple collapse-tags clearable class="full-width" :disabled="!selectedCourse">
            <el-option v-for="ch in chapterOptions" :key="ch.id" :label="ch.title" :value="ch.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('course.difficulty')" prop="difficulty">
          <el-select v-model="formData.difficulty" :placeholder="$t('question.selectDifficulty')" class="full-width">
            <el-option :label="$t('course.difficultyEasy')" :value="1" />
            <el-option :label="$t('course.difficultyMedium')" :value="2" />
            <el-option :label="$t('course.difficultyHard')" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('question.questionContent')" prop="content">
          <el-input v-model="formData.content" type="textarea" :rows="4" :placeholder="$t('question.inputContent')" />
        </el-form-item>
        <el-form-item :label="$t('question.score')" prop="score">
          <el-input-number v-model="formData.score" :min="0" :max="100" class="full-width" />
        </el-form-item>
        <el-form-item :label="$t('question.explanation')" prop="explanation">
          <el-input v-model="formData.explanation" type="textarea" :rows="2" :placeholder="$t('question.inputExplanation')" />
        </el-form-item>
        <!-- 单选/多选选项编辑 -->
        <el-form-item v-if="formData.questionType === 'SINGLE' || formData.questionType === 'MULTIPLE'" :label="$t('question.options')" prop="options">
          <div class="options-editor">
            <div v-for="(opt, idx) in optionList" :key="idx" class="option-item">
              <span class="option-label">{{ ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'][idx] }}.</span>
              <el-input v-model="opt.label" :placeholder="$t('question.optionContent')" class="option-input" />
              <el-radio v-if="formData.questionType === 'SINGLE'" :model-value="opt.correct" @click="setSingleCorrect(idx)" :title="$t('question.setCorrect')">√</el-radio>
              <el-checkbox v-if="formData.questionType === 'MULTIPLE'" v-model="opt.correct" :title="$t('question.setCorrect')">√</el-checkbox>
              <el-button type="danger" link @click="removeOption(idx)">{{ $t('app.delete') }}</el-button>
            </div>
            <el-button type="primary" plain size="small" @click="addOption">{{ $t('question.addOption') }}</el-button>
          </div>
        </el-form-item>
        <!-- 单选/多选题答案 -->
        <el-form-item v-if="formData.questionType === 'SINGLE' || formData.questionType === 'MULTIPLE'" :label="$t('question.correctAnswer')" prop="answer">
          <el-input v-model="formData.answer" :placeholder="$t('question.selectCorrectHint')" disabled class="full-width" />
        </el-form-item>
        <!-- 判断题答案 -->
        <el-form-item v-if="formData.questionType === 'JUDGE'" :label="$t('question.correctAnswer')" prop="answer">
          <el-radio-group v-model="formData.answer">
            <el-radio value="true">{{ $t('question.correct') }}</el-radio>
            <el-radio value="false">{{ $t('question.wrong') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <!-- 填空题答案 -->
        <el-form-item v-if="formData.questionType === 'SHORT_ANSWER'" :label="$t('question.correctAnswer')" prop="answer">
          <el-input v-model="formData.answer" :placeholder="$t('question.inputCorrectAnswer')" />
        </el-form-item>
        <!-- 多选题部分给分 -->
        <el-form-item v-if="formData.questionType === 'MULTIPLE'" :label="$t('question.partialScore')" prop="partialScore">
          <el-switch v-model="formData.partialScore" :active-text="$t('question.enable')" :inactive-text="$t('question.disable')" />
          <div v-if="formData.partialScore" class="partial-score-rule">
            <el-input v-model="formData.partialScoreRule" type="textarea" :rows="2" :placeholder="$t('question.partialScoreRulePlaceholder')" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="submitLoading" @click="handleSubmit">{{ $t('course.dialogConfirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 题目预览 -->
    <QuestionPreview v-model="previewVisible" :question="previewQuestion" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUrlPagination } from '@/composables/useUrlPagination';
import { swrCache } from '@/composables/useStaleWhileRevalidate';
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getQuestions, createQuestion, updateQuestion, deleteQuestion, batchImportQuestion, exportQuestions } from '@/api/question'
import { getCategories } from '@/api/course-category'
import { getCourses } from '@/api/course'
import { getChapters } from '@/api/chapter'
import { Workbook } from 'exceljs'
import QuestionPreview from './QuestionPreview.vue'

const { t } = useI18n()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const categoryOptions = ref([])
const courseOptions = ref([])
const chapterOptions = ref([])

const selectedCourseId = ref('')
const selectedCourse = computed(() => {
  if (!selectedCourseId.value) return null
  return courseOptions.value.find(c => c.id === selectedCourseId.value) || null
})

const searchForm = reactive({
  questionType: '',
  difficulty: '',
  chapterId: '',
  categoryId: '',
  keyword: ''
})

// P2-14: URL 分页同步
const { bindToQuery } = useUrlPagination()
bindToQuery(page, size, searchForm, ['questionType', 'difficulty', 'categoryId', 'keyword'])

// 难度字符串→整数映射（前端UI用字符串，后端用整数）
const DIFFICULTY_MAP = { 'EASY': 1, 'MEDIUM': 2, 'HARD': 3 }
function resolveDifficulty(raw) {
  if (raw === '' || raw === null || raw === undefined) return undefined
  const n = Number(raw)
  if (!Number.isNaN(n) && n >= 1 && n <= 3) return n
  return DIFFICULTY_MAP[String(raw).toUpperCase()] || undefined
}

function onCourseChange(val) {
  if (!val) {
    selectedCourseId.value = ''
    tableData.value = []
    totalElements.value = 0
    chapterOptions.value = []
    return
  }
  searchForm.questionType = ''
  searchForm.difficulty = ''
  searchForm.categoryId = ''
  searchForm.keyword = ''
  page.value = 1
  fetchChapterOptions()
  fetchData()
}

const fetchChapterOptions = async () => {
  if (!selectedCourseId.value) {
    chapterOptions.value = []
    return
  }
  try {
    const { data } = await getChapters({ courseId: selectedCourseId.value })
    chapterOptions.value = data.items || []
  } catch {
    chapterOptions.value = []
  }
}

const dialogVisible = ref(false)
const dialogTitle = ref(t('question.create'))
const previewVisible = ref(false)
const previewQuestion = ref(null)
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const optionList = ref([])

const formData = reactive({
  questionType: '',
  difficulty: 1,
  categoryId: '',
  content: '',
  score: 10,
  explanation: '',
  options: '',
  answer: '',
  partialScore: false,
  partialScoreRule: '',
  chapterIds: []
})

const formRules = {
  questionType: [{ required: true, message: t('question.selectQuestionType'), trigger: 'change' }],
  difficulty: [{ required: true, message: t('question.selectDifficulty'), trigger: 'change' }],
  content: [{ required: true, message: t('question.inputContent'), trigger: 'blur' }],
  score: [{ required: true, message: t('question.scoreRequired'), trigger: 'blur' }]
}

const fetchCategoryOptions = async () => {
  try {
    const { data } = await getCategories({ size: 1000 })
    categoryOptions.value = data.items || []
  } catch {
    ElMessage.error(t('question.fetchCategoriesFailed'))
  }
}

const fetchCourseOptions = async () => {
  try {
    const params = { size: 1000 }
    if (userRole.value === 'TEACHER') params.teacherId = userStore.userId
    const { data } = await getCourses(params)
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error(t('question.fetchCoursesFailed'))
  }
}

const handleImportExcel = async (file) => {
  if (!selectedCourse.value) {
    ElMessage.warning(t('question.importCourseFirst'))
    return false
  }
  try {
    const { data } = await batchImportQuestion(file, selectedCourse.value.id)
    if (data.successCount > 0) {
      let msg = t('question.importSuccess', { count: data.successCount })
      if (data.failCount > 0) msg += t('question.importFailPart', { count: data.failCount })
      ElMessage.success(msg)
    } else {
      ElMessage.warning(t('question.importFailedCheck'))
    }
    if (data.errors && data.errors.length > 0) {
      data.errors.slice(0, 5).forEach(err => ElMessage.error(err))
    }
    fetchData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('question.importFailed'))
  }
  return false
}

const handleExportExcel = async () => {
  if (tableData.value.length === 0) {
    ElMessage.warning(t('question.noExportData'))
    return
  }
  // 优先使用后端导出接口（支持服务端全量导出）
  const params = {
    courseId: selectedCourseId.value || undefined,
    questionType: searchForm.questionType || undefined,
    difficulty: resolveDifficulty(searchForm.difficulty),
    categoryId: searchForm.categoryId || undefined,
    keyword: searchForm.keyword || undefined
  }
  try {
    const res = await exportQuestions(params)
    const blob = res instanceof Blob ? res : new Blob([res])
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    const date = new Date().toISOString().split('T')[0]
    link.download = t('question.exportFileName', { date })
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success(t('question.exportSuccess'))
    return
  } catch (err) {
    console.warn('[QuestionList] 后端导出失败，回退到客户端导出', err)
    // 后端导出失败时回退到客户端 XLSX 导出
  }
  // 客户端 XLSX 兜底导出
  try {
    ElMessage.info(t('question.exporting'))
    const allParams = {
      size: 10000,
      courseId: selectedCourseId.value || undefined,
      questionType: searchForm.questionType || undefined,
      difficulty: resolveDifficulty(searchForm.difficulty),
      categoryId: searchForm.categoryId || undefined,
      keyword: searchForm.keyword || undefined
    }
    const { data } = await getQuestions(allParams)
    const allData = data.items || []
    if (allData.length === 0) {
      ElMessage.warning(t('question.noDataToExport'))
      return
    }
    const exportData = allData.map(q => ({
      [t('question.questionType')]: getQuestionTypeLabel(q.questionType),
      [t('course.difficulty')]: getDifficultyLabel(q.difficulty),
      [t('course.category')]: q.categoryName || '',
      [t('question.questionContent')]: q.content,
      [t('question.score')]: q.score,
      [t('question.correctAnswer')]: q.answer,
      [t('question.explanation')]: q.explanation || ''
    }))
    const wb = new Workbook()
    const ws = wb.addWorksheet(t('question.list'))
    ws.addRows(exportData.map(row => Object.values(row)))
    await wb.xlsx.writeFile(t('question.exportFileName', { date: Date.now() }))
    ElMessage.success(t('question.exportSuccessCount', { count: exportData.length }))
  } catch {
    ElMessage.error(t('question.exportFailed'))
  }
}

function getQuestionTypeLabel(type) {
  const map = {
    'SINGLE': t('question.typeSingle'),
    'MULTIPLE': t('question.typeMultiple'),
    'JUDGE': t('question.typeJudge'),
    'SHORT_ANSWER': t('question.typeShortAnswer'),
    'ESSAY': t('question.typeEssay'),
    'FILL': t('question.typeFill')
  }
  return map[type] || type || ''
}

function getDifficultyLabel(diff) {
  const map = { 'EASY': t('course.difficultyEasy'), 'MEDIUM': t('course.difficultyMedium'), 'HARD': t('course.difficultyHard') }
  return map[diff] || diff || ''
}

const handlePreview = (row) => {
  previewQuestion.value = { ...row }
  previewVisible.value = true
}

const fetchData = async () => {
  if (!selectedCourseId.value) return
  loading.value = true
  try {
    // P2-13: 搜索时 difficulty 为字符串 EASY/MEDIUM/HARD，经 resolveDifficulty 转为数字 1/2/3 发送给后端
    // 后端返回的 difficulty 为数字 1/2/3，表格展示直接使用数字判断
    const params = {
      page: page.value - 1,
      size: size.value,
      courseId: selectedCourseId.value,
      questionType: searchForm.questionType || undefined,
      difficulty: resolveDifficulty(searchForm.difficulty),
      chapterId: searchForm.chapterId || undefined,
      categoryId: searchForm.categoryId || undefined,
      keyword: searchForm.keyword || undefined
    }
    const { data } = await getQuestions(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error(t('question.fetchListFailed'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.questionType = ''
  searchForm.difficulty = ''
  searchForm.chapterId = ''
  searchForm.categoryId = ''
  searchForm.keyword = ''
  page.value = 1
  fetchData()
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const handleCreate = () => {
  if (!selectedCourse.value) return
  dialogTitle.value = t('question.create')
  isEdit.value = false
  currentId.value = null
  formData.questionType = ''
  formData.difficulty = 1
  formData.categoryId = selectedCourse.value.categoryId || null
  formData.content = ''
  formData.score = 10
  formData.explanation = ''
  formData.options = ''
  formData.answer = ''
  formData.partialScore = false
  formData.partialScoreRule = ''
  formData.chapterIds = []
  optionList.value = []
  if (chapterOptions.value.length === 0) fetchChapterOptions()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = t('question.edit')
  isEdit.value = true
  currentId.value = row.id
  formData.questionType = row.questionType
  formData.difficulty = row.difficulty
  formData.categoryId = row.categoryId
  formData.content = row.content
  formData.score = row.score || 10
  formData.explanation = row.explanation || ''
  formData.answer = row.answer || ''
  formData.partialScore = !!row.partialScore
  formData.partialScoreRule = row.partialScoreRule || ''
  formData.chapterIds = Array.isArray(row.chapterIds) ? [...row.chapterIds] : []
  if (chapterOptions.value.length === 0) fetchChapterOptions()
  if (row.options) {
    try {
      optionList.value = JSON.parse(row.options)
    } catch {
      optionList.value = []
    }
  } else {
    optionList.value = []
    // P1-C 修复：判断题旧数据 options 缺失时补默认"正确/错误"，保证编辑回显与再次提交完整
    if (formData.questionType === 'JUDGE' || formData.questionType === 'TRUE_FALSE') {
      formData.options = '[{"value":"true","label":"A","text":"正确"},{"value":"false","label":"B","text":"错误"}]'
    }
  }
  dialogVisible.value = true
}

function addOption() {
  optionList.value.push({ label: '', correct: false })
}

async function removeOption(idx) {
  try {
    await ElMessageBox.confirm(t('question.confirmDeleteOption'), t('question.confirmDeleteTitle'), {
      type: 'warning', confirmButtonText: t('app.delete'), cancelButtonText: t('common.cancel')
    })
    optionList.value.splice(idx, 1)
    ElMessage.success(t('question.optionDeleted'))
  } catch {}
}

function setSingleCorrect(idx) {
  optionList.value.forEach((opt, i) => {
    opt.correct = i === idx
  })
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(t('question.confirmDeleteQuestion'), t('userList.confirmTitle'), { type: 'warning' })
    await deleteQuestion(row.id)
    ElMessage.success(t('course.deleteSuccess'))
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(t('question.deleteFailed'))
    }
  }
}

const handleSubmit = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (submitLoading.value) return
  if (!formRef.value || !selectedCourse.value) return
  submitLoading.value = true
  await formRef.value.validate(async (valid) => {
    if (!valid) { submitLoading.value = false; return }
    try {
      if (formData.questionType === 'SINGLE' || formData.questionType === 'MULTIPLE') {
        // P2-14: 仅在 options 为对象时才 JSON.stringify，避免双重序列化
        formData.options = typeof optionList.value === 'string' ? optionList.value : JSON.stringify(optionList.value)
        const correctOptions = optionList.value.filter(o => o.correct).map(o => o.label)
        formData.answer = correctOptions.join(',')
      }
      if (formData.questionType === 'JUDGE' || formData.questionType === 'TRUE_FALSE') {
        // P1-C 修复：判断题必须带默认选项，否则答题页无选项可渲染、学生无法作答
        formData.options = '[{"value":"true","label":"A","text":"正确"},{"value":"false","label":"B","text":"错误"}]'
      }
      const payload = {
        ...formData,
        courseId: selectedCourse.value.id,
        categoryId: selectedCourse.value.categoryId || null,
        teacherId: userStore.userId
      }
      if (formData.questionType === 'MULTIPLE' && formData.partialScore) {
        payload.partialScore = formData.partialScoreRule
      } else {
        payload.partialScore = null
      }
      if (isEdit.value) {
        await updateQuestion(currentId.value, payload)
        ElMessage.success(t('question.editSuccess'))
      } else {
        await createQuestion(payload)
        ElMessage.success(t('course.createSuccess'))
      }
      dialogVisible.value = false
      fetchData()
    } catch {
      ElMessage.error(isEdit.value ? t('question.editFailed') : t('question.createFailed'))
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

onMounted(() => {
  fetchCategoryOptions()
  fetchCourseOptions()
})
</script>

<style scoped>
.question-list-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.course-select-card {
  margin-bottom: var(--space-4);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.course-select-header {
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.course-select-label {
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.course-select-input {
  flex: 1;
  max-width: 420px;
}

.empty-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
}

.course-info-bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
  padding: var(--space-3) var(--space-4);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
}

.course-tag {
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
}

.course-info-hint {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.filter-card {
  margin-bottom: var(--space-4);
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

.header-actions {
  display: flex;
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

.filter-input-w140 {
  width: 140px;
}

.filter-input-w120 {
  width: 120px;
}

.filter-input-w160 {
  width: 160px;
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

.options-editor {
  width: 100%;
  padding: var(--space-2);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-md);
}

.option-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.option-label {
  width: 24px;
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-regular);
}

.option-input {
  flex: 1;
  max-width: 300px;
}

.partial-score-rule {
  margin-top: var(--space-2);
}

:deep(.el-button) {
  border-radius: var(--radius-md);
}

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}

@media (max-width: 768px) {
  .question-list-page {
    padding: var(--space-4);
  }

  .filter-card {
    margin-bottom: var(--space-4);
  }

  .filter-input-w140,
  .filter-input-w120,
  .filter-input-w160,
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
.upload-inline { display: inline-block; margin-right: var(--space-2); }</style>
