<!--
  题目列表
  路由路径: /courses/questions
  Phase 1
  Author: jackie
-->
<template>
  <div class="question-list-page">
    <!-- 顶栏筛选卡 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="题型">
          <el-select v-model="searchForm.questionType" placeholder="请选择题型" clearable class="filter-input-w140">
            <el-option label="单选题" value="SINGLE_CHOICE" />
            <el-option label="多选题" value="MULTIPLE_CHOICE" />
            <el-option label="判断题" value="TRUE_FALSE" />
            <el-option label="简答题" value="SHORT_ANSWER" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="searchForm.difficulty" placeholder="请选择难度" clearable class="filter-input-w120">
            <el-option label="简单" value="EASY" />
            <el-option label="中等" value="MEDIUM" />
            <el-option label="困难" value="HARD" />
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
          <el-button type="primary" @click="handleCreate">新增题目</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <template #empty>
          <el-empty description="暂无题目数据" />
        </template>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="questionType" label="题型" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.questionType === 'SINGLE_CHOICE'" type="primary" size="small">单选题</el-tag>
            <el-tag v-else-if="row.questionType === 'MULTIPLE_CHOICE'" type="success" size="small">多选题</el-tag>
            <el-tag v-else-if="row.questionType === 'TRUE_FALSE'" type="warning" size="small">判断题</el-tag>
            <el-tag v-else-if="row.questionType === 'SHORT_ANSWER'" type="info" size="small">简答题</el-tag>
            <el-tag v-else type="info" size="small">{{ row.questionType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.difficulty === 'EASY'" type="success" size="small">简单</el-tag>
            <el-tag v-else-if="row.difficulty === 'MEDIUM'" type="warning" size="small">中等</el-tag>
            <el-tag v-else-if="row.difficulty === 'HARD'" type="danger" size="small">困难</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column prop="content" label="题目内容" min-width="250" show-overflow-tooltip />
        <el-table-column prop="score" label="分值" width="80" align="center">
          <template #default="{ row }">
            {{ row.score ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,sizes,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="题型" prop="questionType">
          <el-select v-model="formData.questionType" placeholder="请选择题型" class="full-width">
            <el-option label="单选题" value="SINGLE_CHOICE" />
            <el-option label="多选题" value="MULTIPLE_CHOICE" />
            <el-option label="判断题" value="TRUE_FALSE" />
            <el-option label="简答题" value="SHORT_ANSWER" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="formData.difficulty" placeholder="请选择难度" class="full-width">
            <el-option label="简单" value="EASY" />
            <el-option label="中等" value="MEDIUM" />
            <el-option label="困难" value="HARD" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="formData.categoryId" placeholder="请选择分类" class="full-width">
            <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目内容" prop="content">
          <el-input v-model="formData.content" type="textarea" :rows="4" placeholder="请输入题目内容" />
        </el-form-item>
        <el-form-item label="分值" prop="score">
          <el-input-number v-model="formData.score" :min="0" :max="100" class="full-width" />
        </el-form-item>
        <el-form-item label="答案解析" prop="analysis">
          <el-input v-model="formData.analysis" type="textarea" :rows="2" placeholder="请输入答案解析" />
        </el-form-item>
        <!-- 单选/多选选项编辑 -->
        <el-form-item v-if="formData.questionType === 'SINGLE_CHOICE' || formData.questionType === 'MULTIPLE_CHOICE'" label="选项" prop="options">
          <div class="options-editor">
            <div v-for="(opt, idx) in optionList" :key="idx" class="option-item">
              <el-input v-model="opt.label" placeholder="选项内容" class="option-input" />
              <el-radio v-if="formData.questionType === 'SINGLE_CHOICE'" :model-value="opt.correct" @click="setSingleCorrect(idx)" title="设为正确答案">√</el-radio>
              <el-checkbox v-if="formData.questionType === 'MULTIPLE_CHOICE'" v-model="opt.correct" title="设为正确答案">√</el-checkbox>
              <el-button type="danger" link @click="removeOption(idx)">删除</el-button>
            </div>
            <el-button type="primary" plain size="small" @click="addOption">添加选项</el-button>
          </div>
        </el-form-item>
        <!-- 判断题答案 -->
        <el-form-item v-if="formData.questionType === 'TRUE_FALSE'" label="正确答案" prop="answer">
          <el-radio-group v-model="formData.answer">
            <el-radio label="true">正确</el-radio>
            <el-radio label="false">错误</el-radio>
          </el-radio-group>
        </el-form-item>
        <!-- 填空题答案 -->
        <el-form-item v-if="formData.questionType === 'SHORT_ANSWER'" label="正确答案" prop="answer">
          <el-input v-model="formData.answer" placeholder="请输入正确答案" />
        </el-form-item>
        <!-- 多选题部分给分规则 -->
        <el-form-item v-if="formData.questionType === 'MULTIPLE_CHOICE'" label="部分给分规则" prop="partialScore">
          <el-input v-model="formData.partialScore" type="textarea" :rows="2" placeholder="如: A=30;B=30;C=40;D=40 (选对部分得部分分)" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getQuestions, createQuestion, updateQuestion, deleteQuestion } from '@/api/question'
import { getCategories } from '@/api/course-category'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const categoryOptions = ref([])

const searchForm = reactive({
  questionType: '',
  difficulty: '',
  categoryId: '',
  keyword: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增题目')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const optionList = ref([])

const formData = reactive({
  questionType: '',
  difficulty: '',
  categoryId: '',
  content: '',
  score: 10,
  analysis: '',
  options: '',
  answer: '',
  partialScore: ''
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

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      questionType: searchForm.questionType || undefined,
      difficulty: searchForm.difficulty || undefined,
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
  dialogTitle.value = '新增题目'
  isEdit.value = false
  currentId.value = null
  formData.questionType = ''
  formData.difficulty = ''
  formData.categoryId = ''
  formData.content = ''
  formData.score = 10
  formData.analysis = ''
  formData.options = ''
  formData.answer = ''
  formData.partialScore = ''
  optionList.value = []
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
  formData.analysis = row.analysis || ''
  formData.answer = row.answer || ''
  formData.partialScore = row.partialScore || ''
  // 解析选项
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
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      // 序列化选项
      if (formData.questionType === 'SINGLE_CHOICE' || formData.questionType === 'MULTIPLE_CHOICE') {
        formData.options = JSON.stringify(optionList.value)
        // 提取正确答案
        const correctOptions = optionList.value.filter(o => o.correct).map(o => o.label)
        formData.answer = correctOptions.join(',')
      }
      const payload = { ...formData }
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
  fetchData()
})
</script>

<style scoped>
.question-list-page {
  padding: var(--space-5);
}

.filter-card {
  margin-bottom: var(--space-4);
  border-radius: var(--radius-md);
}

.table-card {
  border-radius: var(--radius-md);
}

.table-card :deep(.el-card__header) {
  padding: var(--space-3) var(--space-5);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--color-text-primary);
}

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: flex-end;
}

.data-table {
  width: 100%;
  border-radius: var(--radius-md);
  overflow: hidden;
}

.data-table :deep(.el-table__row) {
  transition: background-color 0.2s ease;
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--color-bg-page);
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

.options-editor {
  width: 100%;
  padding: var(--space-2);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-sm);
}

.option-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.option-input {
  flex: 1;
  max-width: 300px;
}

@media (max-width: 768px) {
  .question-list-page {
    padding: var(--space-3);
  }

  .filter-card {
    margin-bottom: var(--space-3);
  }

  .filter-input-w140,
  .filter-input-w120,
  .filter-input-w160 {
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