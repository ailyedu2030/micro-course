<!--
  题目列表
  路由路径: /questions
  Phase 1
  Author: jackie
-->
<template>
  <div class="question-list-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>课程管理</el-breadcrumb-item>
      <el-breadcrumb-item>题库管理</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 课程选择卡 -->
    <el-card class="course-select-card" shadow="never">
      <div class="course-select-header">
        <span class="course-select-label">选择课程</span>
        <el-select v-model="selectedCourseId" placeholder="请先选择课程" size="large" clearable filterable class="course-select-input" @change="onCourseChange">
          <el-option v-for="c in courseOptions" :key="c.id" :label="c.title" :value="c.id" />
        </el-select>
      </div>
    </el-card>

    <!-- 未选课程时的提示 -->
    <el-card v-if="!selectedCourse" class="empty-card" shadow="never">
      <el-empty description="请先在上方选择一个课程" />
    </el-card>

    <!-- 已选课程：筛选 + 题目列表 -->
    <template v-if="selectedCourse">
      <!-- 当前课程信息 -->
      <div class="course-info-bar">
        <el-tag type="primary" size="large" effect="plain" class="course-tag">{{ selectedCourse.title }}</el-tag>
        <span class="course-info-hint">题库管理</span>
      </div>

      <!-- 搜索筛选卡 -->
      <el-card class="search-card filter-card" shadow="never">
        <el-form :inline="true" :model="searchForm" @submit.prevent>
          <el-form-item label="题型">
            <el-select v-model="searchForm.questionType" placeholder="请选择题型" clearable class="filter-input-w140">
              <el-option label="单选题" value="SINGLE" />
              <el-option label="多选题" value="MULTIPLE" />
              <el-option label="判断题" value="JUDGE" />
              <el-option label="简答题" value="SHORT_ANSWER" />
              <el-option label="综合题" value="ESSAY" />
              <el-option label="填空题" value="FILL" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="searchForm.difficulty" placeholder="请选择难度" clearable class="filter-input-w120">
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
            </el-select>
          </el-form-item>
          <el-form-item label="章节">
            <el-select v-model="searchForm.chapterId" placeholder="全部章节" clearable class="filter-input-w160">
              <el-option v-for="ch in chapterOptions" :key="ch.id" :label="ch.title" :value="ch.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="分类">
            <el-select v-model="searchForm.categoryId" placeholder="请选择分类" clearable class="filter-input-w160">
              <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.name" :value="cat.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键字">
            <el-input v-model="searchForm.keyword" placeholder="题目内容" clearable class="filter-input-w160" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">题目列表</span>
          <div class="header-actions">
            <el-upload
              :show-file-list="false"
              :before-upload="handleImportExcel"
              accept=".xlsx,.xls"
              class="upload-inline"
>
              <el-button type="success" size="small" :disabled="!selectedCourse">导入Excel</el-button>
            </el-upload>
            <el-button type="warning" size="small" @click="handleExportExcel">导出Excel</el-button>
            <el-button type="primary" v-if="userRole !== 'ACADEMIC'" :disabled="!selectedCourse" @click="handleCreate">新增题目</el-button>
          </div>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="tableData.length === 0" description="当前课程暂无题目" />
      <el-table v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="questionType" label="题型" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.questionType === 'SINGLE'" type="primary" size="small">单选题</el-tag>
            <el-tag v-else-if="row.questionType === 'MULTIPLE'" type="success" size="small">多选题</el-tag>
            <el-tag v-else-if="row.questionType === 'JUDGE'" type="warning" size="small">判断题</el-tag>
            <el-tag v-else-if="row.questionType === 'SHORT_ANSWER'" type="info" size="small">简答题</el-tag>
            <el-tag v-else-if="row.questionType === 'ESSAY'" type="danger" size="small">综合题</el-tag>
            <el-tag v-else-if="row.questionType === 'FILL'" type="info" size="small">填空题</el-tag>
            <el-tag v-else type="info" size="small">{{ row.questionType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.difficulty === 1" type="success" size="small">简单</el-tag>
            <el-tag v-else-if="row.difficulty === 2" type="warning" size="small">中等</el-tag>
            <el-tag v-else-if="row.difficulty === 3" type="danger" size="small">困难</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="120">
          <template #default="{ row }">
            {{ row.categoryName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="关联章节" min-width="120">
          <template #default="{ row }">
            <span>{{ row.chapterTitles?.join(', ') || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="题目内容" min-width="250" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="info" link size="small" @click="handlePreview(row)">预览</el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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
          @current-change="handlePageChange" aria-label="分页导航"
/>
      </div>
    </el-card>
    </template>

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="handleDialogClose" :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="所属课程">
          <el-tag type="primary" effect="plain">{{ selectedCourse?.title }}</el-tag>
        </el-form-item>
        <el-form-item v-if="selectedCourse?.categoryName" label="课程分类">
          <el-tag type="info" effect="plain">{{ selectedCourse.categoryName }}</el-tag>
        </el-form-item>
        <el-form-item label="题型" prop="questionType">
          <el-select v-model="formData.questionType" placeholder="请选择题型" class="full-width">
            <el-option label="单选题" value="SINGLE" />
            <el-option label="多选题" value="MULTIPLE" />
            <el-option label="判断题" value="JUDGE" />
            <el-option label="简答题" value="SHORT_ANSWER" />
            <el-option label="综合题" value="ESSAY" />
            <el-option label="填空题" value="FILL" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联章节">
          <el-select v-model="formData.chapterIds" placeholder="可多选" multiple collapse-tags clearable class="full-width" :disabled="!selectedCourse">
            <el-option v-for="ch in chapterOptions" :key="ch.id" :label="ch.title" :value="ch.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="formData.difficulty" placeholder="请选择难度" class="full-width">
            <el-option label="简单" :value="1" />
            <el-option label="中等" :value="2" />
            <el-option label="困难" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目内容" prop="content">
          <el-input v-model="formData.content" type="textarea" :rows="4" placeholder="请输入题目内容" />
        </el-form-item>
        <el-form-item label="分值" prop="score">
          <el-input-number v-model="formData.score" :min="0" :max="100" class="full-width" />
        </el-form-item>
        <el-form-item label="答案解析" prop="explanation">
          <el-input v-model="formData.explanation" type="textarea" :rows="2" placeholder="请输入答案解析" />
        </el-form-item>
        <!-- 单选/多选选项编辑 -->
        <el-form-item v-if="formData.questionType === 'SINGLE' || formData.questionType === 'MULTIPLE'" label="选项" prop="options">
          <div class="options-editor">
            <div v-for="(opt, idx) in optionList" :key="idx" class="option-item">
              <span class="option-label">{{ ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'][idx] }}.</span>
              <el-input v-model="opt.label" placeholder="选项内容" class="option-input" />
              <el-radio v-if="formData.questionType === 'SINGLE'" :model-value="opt.correct" @click="setSingleCorrect(idx)" title="设为正确答案">√</el-radio>
              <el-checkbox v-if="formData.questionType === 'MULTIPLE'" v-model="opt.correct" title="设为正确答案">√</el-checkbox>
              <el-button type="danger" link @click="removeOption(idx)">删除</el-button>
            </div>
            <el-button type="primary" plain size="small" @click="addOption">添加选项</el-button>
          </div>
        </el-form-item>
        <!-- 单选/多选题答案 -->
        <el-form-item v-if="formData.questionType === 'SINGLE' || formData.questionType === 'MULTIPLE'" label="正确答案" prop="answer">
          <el-input v-model="formData.answer" placeholder="请在选项中勾选正确答案" disabled class="full-width" />
        </el-form-item>
        <!-- 判断题答案 -->
        <el-form-item v-if="formData.questionType === 'JUDGE'" label="正确答案" prop="answer">
          <el-radio-group v-model="formData.answer">
            <el-radio value="true">正确</el-radio>
            <el-radio value="false">错误</el-radio>
          </el-radio-group>
        </el-form-item>
        <!-- 填空题答案 -->
        <el-form-item v-if="formData.questionType === 'SHORT_ANSWER'" label="正确答案" prop="answer">
          <el-input v-model="formData.answer" placeholder="请输入正确答案" />
        </el-form-item>
        <!-- 多选题部分给分 -->
        <el-form-item v-if="formData.questionType === 'MULTIPLE'" label="部分给分" prop="partialScore">
          <el-switch v-model="formData.partialScore" active-text="启用" inactive-text="关闭" />
          <div v-if="formData.partialScore" class="partial-score-rule">
            <el-input v-model="formData.partialScoreRule" type="textarea" :rows="2" placeholder="如: A=30;B=30;C=40;D=40 (选对部分得部分分)" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 题目预览 -->
    <QuestionPreview v-model="previewVisible" :question="previewQuestion" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useUrlPagination } from '@/composables/useUrlPagination';
import { swrCache } from '@/composables/useStaleWhileRevalidate';
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getQuestions, createQuestion, updateQuestion, deleteQuestion, batchImportQuestion } from '@/api/question'
import { getCategories } from '@/api/course-category'
import { getCourses } from '@/api/course'
import { getChapters } from '@/api/chapter'
import * as XLSX from 'xlsx'
import QuestionPreview from './QuestionPreview.vue'

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
const dialogTitle = ref('新增题目')
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
  questionType: [{ required: true, message: '请选择题型', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  content: [{ required: true, message: '请输入题目内容', trigger: 'blur' }],
  score: [{ required: true, message: '请输入分值', trigger: 'blur' }]
}

const fetchCategoryOptions = async () => {
  try {
    const { data } = await getCategories({ size: 1000 })
    categoryOptions.value = data.items || []
  } catch {
    ElMessage.error('获取分类列表失败')
  }
}

const fetchCourseOptions = async () => {
  try {
    const params = { size: 1000 }
    if (userRole.value === 'TEACHER') params.teacherId = userStore.userId
    const { data } = await getCourses(params)
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error('获取课程列表失败')
  }
}

const handleImportExcel = async (file) => {
  if (!selectedCourse.value) {
    ElMessage.warning('请先选择课程再导入题目')
    return false
  }
  try {
    const { data } = await batchImportQuestion(file, selectedCourse.value.id)
    if (data.successCount > 0) {
      ElMessage.success(`导入成功 ${data.successCount} 条${data.failCount > 0 ? `，失败 ${data.failCount} 条` : ''}`)
    } else {
      ElMessage.warning('导入失败，请检查文件格式')
    }
    if (data.errors && data.errors.length > 0) {
      data.errors.slice(0, 5).forEach(err => ElMessage.error(err))
    }
    fetchData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '导入失败')
  }
  return false
}

const handleExportExcel = async () => {
  if (tableData.value.length === 0) {
    ElMessage.warning('暂无题目数据可导出')
    return
  }
  try {
    ElMessage.info('正在获取全部题目数据，请稍候…')
    // P2-16: 导出使用 size: 10000 全量请求，若数据量极大可能内存紧张，可考虑分批导出
    // Fetch ALL filtered data (remove pagination limits by using a large size)
    const params = {
      size: 10000,
      courseId: selectedCourseId.value || undefined,
      questionType: searchForm.questionType || undefined,
      difficulty: resolveDifficulty(searchForm.difficulty),
      categoryId: searchForm.categoryId || undefined,
      keyword: searchForm.keyword || undefined
    }
    const { data } = await getQuestions(params)
    const allData = data.items || []
    if (allData.length === 0) {
      ElMessage.warning('没有找到可导出的题目数据')
      return
    }
    const exportData = allData.map(q => ({
      '题型': getQuestionTypeLabel(q.questionType),
      '难度': getDifficultyLabel(q.difficulty),
      '分类': q.categoryName || '',
      '题目内容': q.content,
      '分值': q.score,
      '正确答案': q.answer,
      '答案解析': q.explanation || ''
    }))
    const ws = XLSX.utils.json_to_sheet(exportData)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '题目列表')
    XLSX.writeFile(wb, `题目导出_${Date.now()}.xlsx`)
    ElMessage.success(`导出成功，共 ${exportData.length} 条`)
  } catch {
    ElMessage.error('导出失败')
  }
}

function getQuestionTypeLabel(type) {
  const map = {
    'SINGLE': '单选题',
    'MULTIPLE': '多选题',
    'JUDGE': '判断题',
    'SHORT_ANSWER': '简答题',
    'ESSAY': '综合题',
    'FILL': '填空题'
  }
  return map[type] || type || ''
}

function getDifficultyLabel(diff) {
  const map = { 'EASY': '简单', 'MEDIUM': '中等', 'HARD': '困难' }
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
    ElMessage.error('获取题目列表失败')
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
  dialogTitle.value = '新增题目'
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
  dialogTitle.value = '编辑题目'
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
  }
  dialogVisible.value = true
}

function addOption() {
  optionList.value.push({ label: '', correct: false })
}

function removeOption(idx) {
  optionList.value.splice(idx, 1)
}

function setSingleCorrect(idx) {
  optionList.value.forEach((opt, i) => {
    opt.correct = i === idx
  })
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该题目?', '提示', { type: 'warning' })
    await deleteQuestion(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value || !selectedCourse.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (formData.questionType === 'SINGLE' || formData.questionType === 'MULTIPLE') {
        // P2-14: 仅在 options 为对象时才 JSON.stringify，避免双重序列化
        formData.options = typeof optionList.value === 'string' ? optionList.value : JSON.stringify(optionList.value)
        const correctOptions = optionList.value.filter(o => o.correct).map(o => o.label)
        formData.answer = correctOptions.join(',')
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
        ElMessage.success('编辑成功')
      } else {
        await createQuestion(payload)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch {
      ElMessage.error(isEdit.value ? '编辑失败' : '创建失败')
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