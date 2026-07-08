<!--
  练习列表
  路由路径: /courses/:courseId/exercises
  Phase 1
  Author: jackie
-->
<template>
  <div class="exercise-list-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>课程管理</el-breadcrumb-item>
      <el-breadcrumb-item>练习列表</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 顶栏筛选卡 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable class="filter-input-w200" @change="handleCourseChange">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节">
          <el-select v-model="searchForm.chapterId" placeholder="请选择章节" clearable class="filter-input-w200" :disabled="!searchForm.courseId">
            <el-option v-for="item in chapterOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">练习列表</span>
          <el-button type="primary" v-if="userRole !== 'ACADEMIC'" @click="handleCreate">新增练习</el-button>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="tableData.length === 0" description="暂无练习数据，请先选择课程和章节后点击「新增练习」" />
      <el-table v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="courseTitle" label="课程" min-width="120" />
        <el-table-column prop="chapterTitle" label="章节" min-width="120" />
        <el-table-column prop="questionCount" label="题目数" width="100" align="center">
          <template #default="{ row }">
            {{ row.questionCount ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="timeLimit" label="时长(分钟)" width="120" align="center">
          <template #default="{ row }">
            {{ row.timeLimit ? `${row.timeLimit}分钟` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="passScore" label="及格分数" width="100" align="center">
          <template #default="{ row }">
            {{ row.passScore ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="primary" link size="small" @click="handleSelectQuestions(row)">选题</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" @close="handleDialogClose" :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="formData.courseId" placeholder="请选择课程" class="full-width" @change="handleFormCourseChange">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节" prop="chapterIds">
          <el-select v-model="formData.chapterIds" placeholder="请选择章节（可多选）" multiple collapse-tags class="full-width" :disabled="!formData.courseId">
            <el-option v-for="item in formChapterOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入练习标题" />
        </el-form-item>

        <!-- 题库统计 + 随机选题 -->
        <el-divider v-if="formData.courseId" />
        <el-form-item v-if="formData.courseId" label="题库统计">
          <div class="bank-stats">
            <el-tag v-for="s in bankStats" :key="s.type" :type="s.count > 0 ? 'primary' : 'info'" size="small" class="stat-tag" style="margin:2px">
              {{ s.label }}: {{ s.count }} 题
            </el-tag>
            <el-tag type="primary" size="small" effect="dark" style="margin:2px">共 {{ totalBankCount }} 题</el-tag>
          </div>
        </el-form-item>
        <el-form-item v-if="formData.courseId" label="随机选题">
          <div class="random-pick" style="display:flex;flex-direction:column;gap:6px;width:100%">
            <el-select v-model="pickDifficulty" placeholder="难度筛选" clearable size="small" style="width:120px">
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
            </el-select>
            <div v-for="s in bankStats" :key="s.type" class="pick-row" style="display:flex;align-items:center;gap:8px">
              <span class="pick-label" style="width:60px;font-size:13px">{{ s.label }}</span>
              <el-input-number v-model="s.pickCount" :min="0" :max="s.count" size="small" controls-position="right" style="width:130px" />
              <span style="font-size:12px;color:var(--el-text-color-secondary)">/ {{ s.count }} 题</span>
            </div>
            <el-button type="success" size="small" :disabled="totalPickCount === 0" @click="handleRandomPick" style="width:160px">
              随机抽取 {{ totalPickCount }} 题
            </el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="formData.courseId && pickedQuestions.length > 0" label="已选">
          <el-tag type="success">{{ pickedQuestions.length }} 题已随机选取</el-tag>
        </el-form-item>
        <el-divider v-if="formData.courseId" />

        <el-form-item label="及格分数" prop="passScore">
          <el-input-number v-model="formData.passScore" :min="0" :max="100" class="full-width" />
        </el-form-item>
        <el-form-item label="时间限制(分钟)" prop="timeLimit">
          <el-input-number v-model="formData.timeLimit" :min="0" placeholder="0表示无限制" class="full-width" />
        </el-form-item>
        <el-form-item label="答题次数" prop="maxAttempts">
          <el-input-number v-model="formData.maxAttempts" :min="0" placeholder="0表示无限制" class="full-width" />
        </el-form-item>
        <el-form-item label="题目乱序" prop="shuffleQuestions">
          <el-switch v-model="formData.shuffleQuestions" />
        </el-form-item>
        <el-form-item label="选项乱序" prop="shuffleOptions">
          <el-switch v-model="formData.shuffleOptions" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" placeholder="请输入描述" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 选题组卷弹窗 -->
    <el-dialog v-model="questionPickerVisible" title="选题组卷" width="900px" @close="handleQuestionPickerClose" :close-on-press-escape="true">
      <div class="question-picker">
        <!-- 筛选区 -->
        <el-card class="picker-filter-card" shadow="never">
          <el-form :inline="true" :model="questionSearchForm" @submit.prevent>
            <el-form-item label="题型">
              <el-select v-model="questionSearchForm.questionType" placeholder="请选择题型" clearable>
                <el-option label="单选题" value="SINGLE" />
                <el-option label="多选题" value="MULTIPLE" />
                <el-option label="判断题" value="JUDGE" />
                <el-option label="简答题" value="SHORT_ANSWER" />
              </el-select>
            </el-form-item>
            <el-form-item label="难度">
              <el-select v-model="questionSearchForm.difficulty" placeholder="请选择难度" clearable>
                <el-option label="简单" value="EASY" />
                <el-option label="中等" value="MEDIUM" />
                <el-option label="困难" value="HARD" />
              </el-select>
            </el-form-item>
            <el-form-item label="分类">
              <el-select v-model="questionSearchForm.categoryId" placeholder="请选择分类" clearable>
                <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.name" :value="cat.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleQuestionSearch">搜索</el-button>
              <el-button @click="handleQuestionReset">重置</el-button>
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
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="questionType" label="题型" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.questionType === 'SINGLE'" type="primary" size="small">单选题</el-tag>
              <el-tag v-else-if="row.questionType === 'MULTIPLE'" type="success" size="small">多选题</el-tag>
              <el-tag v-else-if="row.questionType === 'JUDGE'" type="warning" size="small">判断题</el-tag>
              <el-tag v-else-if="row.questionType === 'SHORT_ANSWER'" type="info" size="small">简答题</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="difficulty" label="难度" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.difficulty === 1" type="success" size="small">简单</el-tag>
              <el-tag v-else-if="row.difficulty === 2" type="warning" size="small">中等</el-tag>
              <el-tag v-else-if="row.difficulty === 3" type="danger" size="small">困难</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="content" label="题目内容" min-width="300" show-overflow-tooltip />
          <el-table-column prop="score" label="分值" width="70" align="center" />
        </el-table>
        <div class="picker-footer">
          <span class="selected-count">已选 {{ selectedQuestions.length }} 题</span>
          <el-pagination
            v-model:current-page="questionPage"
            v-model:page-size="questionSize"
            :total="questionTotal"
            :page-sizes="[10, 20, 50]"
            layout="total,sizes,prev,pager,next"
            small
            @size-change="handleQuestionSizeChange"
            @current-change="handleQuestionPageChange" aria-label="分页导航"
/>
        </div>
      </div>
      <template #footer>
        <el-button @click="questionPickerVisible = false">取消</el-button>
        <el-button type="primary" :loading="questionSubmitLoading" :disabled="questionSubmitLoading" @click="handleAddQuestions">添加到练习</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useUrlPagination } from '@/composables/useUrlPagination';
import { swrCache } from '@/composables/useStaleWhileRevalidate';
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getExercises, createExercise, updateExercise, deleteExercise, addQuestionsToExercise, removeQuestionFromExercise } from '@/api/exercise'
import { getQuestions } from '@/api/question'
import { getCourses } from '@/api/course'
import { getChapters } from '@/api/chapter'
import { getCategories } from '@/api/course-category'

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
bindToQuery(page, size, searchForm, ['courseId', 'chapterId'])

const dialogVisible = ref(false)
const dialogTitle = ref('新增练习')
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

const TYPE_LABELS = { SINGLE: '单选题', MULTIPLE: '多选题', JUDGE: '判断题', FILL: '填空题', SHORT_ANSWER: '简答题' }
const bankStats = ref([])
const totalBankCount = ref(0)
const totalPickCount = computed(() => bankStats.value.reduce((s, t) => s + (t.pickCount || 0), 0))
const pickDifficulty = ref('')
const pickedQuestions = ref([])

watch(() => formData.courseId, async (val) => {
  pickedQuestions.value = []
  if (!val) { bankStats.value = []; totalBankCount.value = 0; return }
  try {
    const params = { courseId: val, size: 9999 }
    if (pickDifficulty.value) params.difficulty = resolveDifficulty(pickDifficulty.value)
    const { data } = await getQuestions(params)
    const items = data?.items || []
    const filterDiff = pickDifficulty.value ? resolveDifficulty(pickDifficulty.value) : undefined
    const filtered = filterDiff != null ? items.filter(q => q.difficulty === filterDiff) : items
    totalBankCount.value = filtered.length
    const groups = {}
    for (const q of filtered) { const t = q.questionType || 'OTHER'; groups[t] = (groups[t] || 0) + 1 }
    bankStats.value = Object.entries(TYPE_LABELS)
      .map(([type, label]) => ({ type, label, count: groups[type] || 0, pickCount: 0 }))
      .filter(s => s.count > 0)
  } catch { bankStats.value = []; totalBankCount.value = 0; ElMessage.warning('加载题库统计失败') }
})
watch(pickDifficulty, () => {
  if (formData.courseId) { const cb = formData.courseId; formData.courseId = null; setTimeout(() => formData.courseId = cb, 0) }
})

async function handleRandomPick() {
  const picks = {}
  for (const s of bankStats.value) { if (s.pickCount > 0) picks[s.type] = s.pickCount }
  try {
    const params = { courseId: formData.courseId, size: 9999 }
    if (pickDifficulty.value) params.difficulty = resolveDifficulty(pickDifficulty.value)
    const { data } = await getQuestions(params)
    let all = data?.items || []
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
  } catch { ElMessage.warning('随机选题失败') }
}

const formRules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  chapterIds: [{ required: true, message: '请选择章节', trigger: 'change' }],
  title: [{ required: true, message: '请输入练习标题', trigger: 'blur' }]
}

const fetchCourseOptions = async () => {
  try {
    const params = { page: 0, size: 1000 }
    // P2-15: 使用 userId getter 替代 userInfo?.id 以保持一致
    if (isTeacher.value) params.teacherId = userStore.userId
    const { data } = await getCourses(params)
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error('获取课程列表失败')
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
    ElMessage.error('获取章节列表失败')
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
    ElMessage.error('获取练习列表失败')
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

const handleCreate = () => {
  dialogTitle.value = '新增练习'
  isEdit.value = false
  currentId.value = null
  formData.courseId = searchForm.courseId
  formData.chapterIds = []
  formData.title = ''
  formData.passScore = 60
  formData.description = ''
  formData.timeLimit = null
  formData.maxAttempts = null
  formData.shuffleQuestions = false
  formData.shuffleOptions = false
  if (formData.courseId) handleFormCourseChange(formData.courseId)
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  dialogTitle.value = '编辑练习'
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
    const { data } = await getChapters({ courseId: row.courseId, size: 1000 })
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
    await ElMessageBox.confirm('确定删除该练习?', '提示', { type: 'warning' })
    await deleteExercise(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleSubmit = async () => {
  if (submitLoading.value) return
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      let exerciseId = currentId.value
      if (isEdit.value) {
        await updateExercise(exerciseId, formData)
        ElMessage.success('编辑成功')
      } else {
        const { data } = await createExercise(formData)
        exerciseId = data.id
        ElMessage.success('创建成功')
      }
      // 自动保存随机选题
      if (pickedQuestions.value.length > 0) {
        const qIds = pickedQuestions.value.map(q => q.id).filter(Boolean)
        if (qIds.length > 0) await addQuestionsToExercise(exerciseId, { questionIds: qIds })
      }
      dialogVisible.value = false
      fetchData()
    } catch {
      ElMessage.error(isEdit.value ? '编辑失败' : '创建失败')
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
    const { data } = await getCategories({ size: 1000 })
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
    ElMessage.error('获取题目列表失败')
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
    ElMessage.warning('请先选择题目')
    return
  }
  questionSubmitLoading.value = true
  try {
    const questionIds = selectedQuestions.value.map(q => q.id)
    await addQuestionsToExercise(currentId.value, { questionIds })
    ElMessage.success('添加成功')
    questionPickerVisible.value = false
    fetchData()
  } catch {
    ElMessage.error('添加失败')
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