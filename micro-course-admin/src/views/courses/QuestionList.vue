<template>
  <div class="question-list">
    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable class="search-input-w160">
            <el-option v-for="c in courses" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="searchForm.questionType" placeholder="请选择题型" clearable class="search-input-w160">
            <el-option label="单选题" value="SINGLE" />
            <el-option label="多选题" value="MULTIPLE" />
            <el-option label="判断题" value="JUDGE" />
            <el-option label="填空题" value="FILL" />
            <el-option label="简答题" value="SHORT_ANSWER" />
            <el-option label="论述题" value="ESSAY" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>题库管理</span>
          <div class="header-actions">
            <el-button type="success" @click="handleImport">导入题目</el-button>
            <el-button v-if="tableData.length > 0" type="primary" @click="handleExport">导出 Excel</el-button>
            <el-button type="primary" @click="handleCreate">新增题目</el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.content ? row.content.substring(0, 50) + (row.content.length > 50 ? '...' : '') : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="questionType" label="题型" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.questionType === 'SINGLE'" size="small">单选题</el-tag>
            <el-tag v-else-if="row.questionType === 'MULTIPLE'" type="info" size="small">多选题</el-tag>
            <el-tag v-else-if="row.questionType === 'JUDGE'" type="warning" size="small">判断题</el-tag>
            <el-tag v-else-if="row.questionType === 'FILL'" type="success" size="small">填空题</el-tag>
            <el-tag v-else-if="row.questionType === 'SHORT_ANSWER'" type="primary" size="small">简答题</el-tag>
            <el-tag v-else-if="row.questionType === 'ESSAY'" type="danger" size="small">论述题</el-tag>
            <span v-else>-</span>
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
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="info" size="small">禁用</el-tag>
            <el-tag v-else-if="row.status === 1" type="success" size="small">启用</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
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

    <!-- 弹窗区 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="题目内容" prop="content">
          <el-input v-model="formData.content" type="textarea" :rows="3" placeholder="请输入题目内容" />
        </el-form-item>
        <el-form-item label="题型" prop="questionType">
          <el-select v-model="formData.questionType" placeholder="请选择题型" class="full-width">
            <el-option label="单选题" value="SINGLE" />
            <el-option label="多选题" value="MULTIPLE" />
            <el-option label="判断题" value="JUDGE" />
            <el-option label="填空题" value="FILL" />
            <el-option label="简答题" value="SHORT_ANSWER" />
            <el-option label="论述题" value="ESSAY" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="formData.courseId" placeholder="请选择课程" class="full-width">
            <el-option v-for="c in courses" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="选项" prop="options">
          <el-input v-model="formData.options" type="textarea" :rows="3" placeholder='请输入JSON格式选项，如：[{"label":"A","content":"选项内容"}]' />
        </el-form-item>
        <el-form-item label="答案" prop="answer">
          <el-input v-model="formData.answer" type="textarea" :rows="2" placeholder="请输入答案" />
        </el-form-item>
        <el-form-item label="解析" prop="explanation">
          <el-input v-model="formData.explanation" type="textarea" :rows="2" placeholder="请输入题目解析" />
        </el-form-item>
        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="formData.difficulty" placeholder="请选择难度" class="full-width">
            <el-option label="简单" :value="1" />
            <el-option label="中等" :value="2" />
            <el-option label="困难" :value="3" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importDialogVisible" title="导入题目" width="500px">
      <div class="import-tips">
        <p>请上传 Excel 文件（.xlsx/.xls），模板格式如下：</p>
        <el-link type="primary" :href="templateUrl" target="_blank">下载导入模板</el-link>
      </div>
      <el-upload
        ref="importUploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="handleImportFileChange"
      >
        <el-button type="primary" size="small">选择文件</el-button>
      </el-upload>
      <div v-if="importPreview.length > 0" class="import-preview">
        <div class="preview-title">预览（前5条）:</div>
        <div v-for="(item, idx) in importPreview.slice(0, 5)" :key="idx" class="preview-item">
          {{ item.content?.substring(0, 30) }}...
        </div>
      </div>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleConfirmImport">确认导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 题库管理页面 - Phase 6 增强：Excel题目导入 + 试题导出
 * @author Claude Code Agent
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import { getQuestions, createQuestion, updateQuestion, deleteQuestion } from '@/api/question'
import { getCourses } from '@/api/course'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const courses = ref([])

const searchForm = reactive({
  courseId: '',
  questionType: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增题目')
const isEdit = ref(false)
const formRef = ref(null)
const currentId = ref(null)

const formData = reactive({
  content: '',
  questionType: '',
  courseId: '',
  options: '',
  answer: '',
  explanation: '',
  difficulty: 1
})

const formRules = {
  content: [{ required: true, message: '请输入题目内容', trigger: 'blur' }],
  questionType: [{ required: true, message: '请选择题型', trigger: 'change' }],
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }]
}

// 导入相关
const importDialogVisible = ref(false)
const importLoading = ref(false)
const importUploadRef = ref(null)
const importFile = ref(null)
const importPreview = ref([])
const templateUrl = '/api/templates/question-import-template.xlsx'

const fetchCourses = async () => {
  try {
    const { data } = await getCourses({ size: 1000 })
    courses.value = data.items || []
  } catch (error) {
    ElMessage.error('获取课程列表失败')
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      courseId: searchForm.courseId || undefined,
      questionType: searchForm.questionType || undefined
    }
    const { data } = await getQuestions(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch (error) {
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
  searchForm.courseId = ''
  searchForm.questionType = ''
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
  formData.content = ''
  formData.questionType = ''
  formData.courseId = ''
  formData.options = ''
  formData.answer = ''
  formData.explanation = ''
  formData.difficulty = 1
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑题目'
  isEdit.value = true
  currentId.value = row.id
  formData.content = row.content || ''
  formData.questionType = row.questionType || ''
  formData.courseId = row.courseId || ''
  formData.options = row.options ? JSON.stringify(row.options) : ''
  formData.answer = row.answer || ''
  formData.explanation = row.explanation || ''
  formData.difficulty = row.difficulty || 1
  dialogVisible.value = true
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
      const submitData = { ...formData }
      if (submitData.options) {
        try {
          submitData.options = JSON.parse(submitData.options)
        } catch (e) {
          ElMessage.warning('选项格式应为JSON')
          submitLoading.value = false
          return
        }
      }
      if (isEdit.value) {
        await updateQuestion(currentId.value, submitData)
        ElMessage.success('更新成功')
      } else {
        await createQuestion(submitData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

// 导入相关
const handleImport = () => {
  importFile.value = null
  importPreview.value = []
  importDialogVisible.value = true
}

const handleImportFileChange = (file) => {
  importFile.value = file.raw
  parseImportFile(file.raw)
}

const parseImportFile = (file) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const wb = XLSX.read(e.target.result, { type: 'binary' })
      const ws = wb.Sheets[wb.SheetNames[0]]
      const jsonData = XLSX.utils.sheet_to_json(ws)
      importPreview.value = jsonData
    } catch (error) {
      ElMessage.error('文件解析失败')
    }
  }
  reader.readAsBinaryString(file)
}

const handleConfirmImport = async () => {
  if (!importFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importLoading.value = true
  try {
    // 实际应调用后端批量导入API
    ElMessage.info('批量导入API未提供，已本地解析文件内容')
    importDialogVisible.value = false
  } catch (error) {
    ElMessage.error('导入失败')
  } finally {
    importLoading.value = false
  }
}

// 导出
const handleExport = () => {
  if (!tableData.value.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  const exportData = tableData.value.map(item => ({
    内容: item.content || '',
    题型: item.questionType || '',
    课程ID: item.courseId || '',
    选项: item.options ? JSON.stringify(item.options) : '',
    答案: item.answer || '',
    解析: item.explanation || '',
    难度: item.difficulty === 1 ? '简单' : item.difficulty === 2 ? '中等' : item.difficulty === 3 ? '困难' : '',
    状态: item.status === 0 ? '禁用' : item.status === 1 ? '启用' : ''
  }))
  const ws = XLSX.utils.json_to_sheet(exportData)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '题目列表')
  const date = new Date().toISOString().split('T')[0]
  XLSX.writeFile(wb, `questions-${date}.xlsx`)
  ElMessage.success('导出成功')
}

onMounted(() => {
  fetchCourses()
  fetchData()
})
</script>

<style scoped>
.question-list {
  padding: 20px;
}

.search-card {
  margin-bottom: 16px;
}

.table-card :deep(.el-card__header) {
  padding: 12px 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.import-tips {
  margin-bottom: 16px;
  font-size: 14px;
  color: #606266;
}

.import-preview {
  margin-top: 16px;
  max-height: 200px;
  overflow-y: auto;
}

.preview-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.preview-item {
  padding: 4px 0;
  font-size: 13px;
  color: #606266;
  border-bottom: 1px solid #f0f0f0;
}

@media (max-width: 768px) {
  .question-list {
    padding: 12px;
  }

  .search-card {
    margin-bottom: 12px;
  }

  .header-actions {
    flex-direction: column;
  }
}

.data-table { width: 100%; }
.full-width { width: 100%; }
.search-input-w160 { width: 160px; }
</style>