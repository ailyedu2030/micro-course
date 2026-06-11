<template>
  <div class="chapter-list">
    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable style="width: 240px" @change="handleSearch">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>章节列表</span>
          <el-button type="primary" @click="handleCreate">新增章节</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column prop="title" label="标题" min-width="150" />
        <el-table-column prop="chapterType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.chapterType === 'VIDEO'" type="primary">视频</el-tag>
            <el-tag v-else-if="row.chapterType === 'EXERCISE'" type="success">练习</el-tag>
            <el-tag v-else type="warning">混合</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="时长" width="100">
          <template #default="{ row }">
            {{ row.duration ? `${row.duration}分钟` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="formData.courseId" placeholder="请选择课程" style="width: 100%">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入章节标题" />
        </el-form-item>
        <el-form-item label="类型" prop="chapterType">
          <el-select v-model="formData.chapterType" placeholder="请选择类型" style="width: 100%">
            <el-option label="视频" value="VIDEO" />
            <el-option label="练习" value="EXERCISE" />
            <el-option label="混合" value="MIXED" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时长(分钟)" prop="duration">
          <el-input-number v-model="formData.duration" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" placeholder="请输入描述" :rows="3" />
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
import { getChapters, createChapter, updateChapter, deleteChapter } from '@/api/chapter'
import { getCourses } from '@/api/course'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const searchForm = reactive({
  courseId: null
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增章节')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)
const courseOptions = ref([])

const formData = reactive({
  courseId: null,
  title: '',
  chapterType: 'VIDEO',
  sortOrder: 0,
  duration: 0,
  description: ''
})

const formRules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  title: [{ required: true, message: '请输入章节标题', trigger: 'blur' }],
  chapterType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  sortOrder: [{ required: true, message: '请输入排序值', trigger: 'blur' }]
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
      page: page.value - 1,
      size: size.value
    }
    const { data } = await getChapters(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch (error) {
    ElMessage.error('获取章节列表失败')
  } finally {
    loading.value = false
  }
}

const fetchCourseOptions = async () => {
  try {
    const { data } = await getCourses({ page: 0, size: 1000 })
    courseOptions.value = data.items || []
  } catch (error) {
    console.error('获取课程列表失败', error)
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.courseId = null
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
  dialogTitle.value = '新增章节'
  isEdit.value = false
  currentId.value = null
  formData.courseId = searchForm.courseId
  formData.title = ''
  formData.chapterType = 'VIDEO'
  formData.sortOrder = 0
  formData.duration = 0
  formData.description = ''
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑章节'
  isEdit.value = true
  currentId.value = row.id
  formData.courseId = row.courseId
  formData.title = row.title
  formData.chapterType = row.chapterType
  formData.sortOrder = row.sortOrder
  formData.duration = row.duration || 0
  formData.description = row.description || ''
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该章节?', '提示', { type: 'warning' })
    await deleteChapter(row.id)
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
      if (isEdit.value) {
        await updateChapter(currentId.value, formData)
        ElMessage.success('编辑成功')
      } else {
        await createChapter(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (error) {
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
  fetchCourseOptions()
})
</script>

<style scoped>
.chapter-list {
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

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>