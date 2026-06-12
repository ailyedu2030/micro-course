<!--
  课程列表
  路由路径: /courses
  Phase 1
  Author: jackie
-->
<template>
  <div class="course-list">
    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="课程标题" clearable class="search-input-w160" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.categoryId" placeholder="请选择分类" clearable class="search-input-w160">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教师">
          <el-input v-model="searchForm.teacherName" placeholder="教师姓名" clearable class="search-input-w120" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable class="search-input-w120">
            <el-option label="草稿" :value="0" />
            <el-option label="待审核" :value="1" />
            <el-option label="通过" :value="2" />
            <el-option label="驳回" :value="3" />
            <el-option label="已发布" :value="4" />
            <el-option label="下架" :value="5" />
            <el-option label="归档" :value="6" />
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
          <span>课程列表</span>
          <el-button type="primary" @click="handleCreate">新增课程</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <template #empty>
          <div class="empty-state">
            <el-icon :size="48"><Monitor /></el-icon>
            <p>暂无数据</p>
          </div>
        </template>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column prop="teacherName" label="教师" width="100" />
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="info" size="small">草稿</el-tag>
            <el-tag v-else-if="row.status === 1" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="row.status === 2" type="success" size="small">通过</el-tag>
            <el-tag v-else-if="row.status === 3" type="danger" size="small">驳回</el-tag>
            <el-tag v-else-if="row.status === 4" type="primary" size="small">已发布</el-tag>
            <el-tag v-else-if="row.status === 5" size="small">下架</el-tag>
            <el-tag v-else type="info" size="small">归档</el-tag>
            <div v-if="row.status === 1" class="review-tip">预计48小时内审核</div>
          </template>
        </el-table-column>
        <el-table-column prop="rating" label="评分" width="80" align="center">
          <template #default="{ row }">
            {{ row.rating ? row.rating.toFixed(1) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="studentCount" label="学生数" width="90" align="center" />
        <el-table-column label="操作" width="320" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 1" type="success" link size="small" @click="handleApprove(row)">审核通过</el-button>
            <el-button v-if="row.status === 1" type="danger" link size="small" @click="handleReject(row)">驳回</el-button>
            <el-button v-if="row.status === 2" type="primary" link size="small" @click="handlePublish(row)">发布</el-button>
            <el-button v-if="row.status === 4" type="warning" link size="small" @click="handleUnpublish(row)">下架</el-button>
            <el-button type="info" link size="small" @click="handleView(row)">查看</el-button>
            <el-button type="primary" link size="small" @click="handleCopy(row)">复制</el-button>
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
        <el-form-item label="课程标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入课程标题" />
        </el-form-item>
        <el-form-item label="分类" prop="categoryId">
          <el-select v-model="formData.categoryId" placeholder="请选择分类" class="full-width">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教师ID" prop="teacherId">
          <el-input v-model.number="formData.teacherId" placeholder="请输入教师ID" type="number" />
        </el-form-item>
        <el-form-item label="课程描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入课程描述" />
        </el-form-item>
        <el-form-item label="学分" prop="creditHours">
          <el-input-number v-model="formData.creditHours" :min="0" :max="20" class="full-width" />
        </el-form-item>
        <el-form-item label="学期" prop="semester">
          <el-input v-model="formData.semester" placeholder="如：2024春季" />
        </el-form-item>
        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="formData.difficulty" placeholder="请选择难度" class="full-width">
            <el-option label="初级" value="BEGINNER" />
            <el-option label="中级" value="INTERMEDIATE" />
            <el-option label="高级" value="ADVANCED" />
          </el-select>
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
/**
 * 课程列表页面 - Phase 6 增强：课程复制 + 审核时效提示
 * @author Claude Code Agent
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Monitor } from '@element-plus/icons-vue'
import { getCourses, createCourse, updateCourseStatus, deleteCourse } from '@/api/course'
import { getCategories } from '@/api/course-category'

const router = useRouter()

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const categories = ref([])

const searchForm = reactive({
  keyword: '',
  categoryId: '',
  teacherName: '',
  status: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增课程')
const isEdit = ref(false)
const formRef = ref(null)

const formData = reactive({
  title: '',
  categoryId: '',
  teacherId: '',
  description: '',
  creditHours: 1,
  semester: '',
  difficulty: ''
})

const formRules = {
  title: [{ required: true, message: '请输入课程标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  teacherId: [{ required: true, message: '请输入教师ID', trigger: 'blur' }]
}

const fetchCategories = async () => {
  try {
    const { data } = await getCategories({ size: 1000 })
    categories.value = data.items || []
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
      keyword: searchForm.keyword || undefined,
      categoryId: searchForm.categoryId || undefined,
      teacherName: searchForm.teacherName || undefined,
      status: searchForm.status !== '' ? searchForm.status : undefined
    }
    const { data } = await getCourses(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取课程列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.categoryId = ''
  searchForm.teacherName = ''
  searchForm.status = ''
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
  dialogTitle.value = '新增课程'
  isEdit.value = false
  formData.title = ''
  formData.categoryId = ''
  formData.teacherId = ''
  formData.description = ''
  formData.creditHours = 1
  formData.semester = ''
  formData.difficulty = ''
  dialogVisible.value = true
}

const handleEdit = (row) => {
  router.push(`/courses/${row.id}/edit`)
}

const handleView = (row) => {
  router.push(`/courses/${row.id}`)
}

const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm('确定审核通过该课程?', '提示', { type: 'warning' })
    await updateCourseStatus(row.id, 2)
    ElMessage.success('审核通过成功')
    fetchData()
  } catch {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleReject = async (row) => {
  try {
    await ElMessageBox.confirm('确定驳回该课程?', '提示', { type: 'warning' })
    await updateCourseStatus(row.id, 3)
    ElMessage.success('驳回成功')
    fetchData()
  } catch {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handlePublish = async (row) => {
  try {
    await ElMessageBox.confirm('确定发布该课程?', '提示', { type: 'warning' })
    await updateCourseStatus(row.id, 4)
    ElMessage.success('发布成功')
    fetchData()
  } catch {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleUnpublish = async (row) => {
  try {
    await ElMessageBox.confirm('确定下架该课程?', '提示', { type: 'warning' })
    await updateCourseStatus(row.id, 5)
    ElMessage.success('下架成功')
    fetchData()
  } catch {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该课程?', '提示', { type: 'warning' })
    await deleteCourse(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleCopy = async (row) => {
  try {
    await ElMessageBox.confirm(`确定复制课程"${row.title}"?`, '复制课程', { type: 'info' })
    const copyData = {
      title: '复制-' + row.title,
      categoryId: row.categoryId,
      teacherId: row.teacherId,
      description: row.description,
      creditHours: row.creditHours,
      semester: row.semester,
      difficulty: row.difficulty
    }
    await createCourse(copyData)
    ElMessage.success('复制成功')
    fetchData()
  } catch {
    if (error !== 'cancel') {
      ElMessage.error('复制失败')
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      await createCourse(formData)
      ElMessage.success('创建成功')
      dialogVisible.value = false
      fetchData()
    } catch {
      ElMessage.error('创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

onMounted(() => {
  fetchCategories()
  fetchData()
})
</script>

<style scoped>
.course-list {
  padding: 20px;
}

.search-card {
  margin-bottom: 16px;
  transition: box-shadow 0.3s;
}

.search-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}


.table-card {
  transition: box-shadow 0.3s;
}

.table-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
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

.review-tip {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

@media (max-width: 768px) {
  .course-list {
    padding: 12px;
  }

  .search-card {
    margin-bottom: 12px;
  }
}

.data-table { width: 100%; }
.full-width { width: 100%; }
.search-input-w160 { width: 160px; }
.search-input-w120 { width: 120px; }
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
  color: var(--el-text-color-secondary);
}
.empty-state p {
  margin-top: 12px;
  font-size: 14px;
}
</style>