<!--
  课程列表
  路由路径: /courses
  Phase 1
  Author: jackie
-->
<template>
  <div class="course-list-page">
    <!-- 面包屑导航 -->
    <el-breadcrumb separator="/" class="page-breadcrumb">
      <el-breadcrumb-item>课程管理</el-breadcrumb-item>
      <el-breadcrumb-item>课程列表</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="课程标题" clearable class="filter-input-w160" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.categoryId" placeholder="请选择分类" clearable class="filter-input-w160">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教师">
          <el-input v-model="searchForm.teacherName" placeholder="教师姓名" clearable class="filter-input-w120" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择" clearable class="filter-input-w120">
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

    <!-- 数据表 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ userRole === 'TEACHER' ? '我的课程' : '课程列表' }}</span>
          <el-button type="primary" v-if="userRole !== 'ACADEMIC'" @click="handleCreate">新增课程</el-button>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="5" animated />
      <el-empty v-else-if="tableData.length === 0" description="暂无课程数据" :image-size="120" />
      <el-table v-else :data="tableData" stripe border class="data-table" ref="tableRef" @row-click="handleRowClick">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column label="封面" width="80" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.coverUrl"
              :src="row.coverUrl"
              fit="cover"
              class="table-thumb"
              :preview-src-list="[row.coverUrl]"
              lazy
            />
            <span v-else class="no-thumb">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.courseType === 'INTERACTIVE'" type="success" size="small" effect="plain">互动</el-tag>
            <el-tag v-else type="primary" size="small" effect="plain">视频</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column prop="teacherName" label="教师" width="100" />
        <el-table-column prop="studentCount" label="学员数" width="90" align="center" />
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="info" size="small">草稿</el-tag>
            <el-tag v-else-if="row.status === 1" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="row.status === 2" type="success" size="small">通过</el-tag>
            <el-tag v-else-if="row.status === 3" type="danger" size="small">驳回</el-tag>
            <el-tag v-else-if="row.status === 4" type="success" size="small">已发布</el-tag>
            <el-tag v-else-if="row.status === 5" type="warning" size="small">下架</el-tag>
            <el-tag v-else type="info" size="small">归档</el-tag>
            <div v-if="row.status === 1" class="review-hint">审核中，预计48h</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.courseType === 'INTERACTIVE'" type="primary" link size="small" @click.stop="goWorkspace(row)">工作台</el-button>
            <el-button type="primary" link size="small" @click.stop="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.courseType === 'INTERACTIVE'" type="success" link size="small" @click.stop="goSlides(row)">课件</el-button>
            <el-button v-if="row.status === 1 && userRole === 'ADMIN'" type="success" link size="small" @click.stop="handleApprove(row)">审核通过</el-button>
            <el-button v-if="row.status === 1 && userRole === 'ADMIN'" type="danger" link size="small" @click.stop="handleReject(row)">驳回</el-button>
            <el-button v-if="row.status === 2 && (userRole === 'ADMIN' || userRole === 'ACADEMIC')" type="primary" link size="small" @click.stop="handlePublish(row)">发布</el-button>
            <el-button v-if="row.status === 4 && (userRole === 'ADMIN' || userRole === 'ACADEMIC')" type="warning" link size="small" @click.stop="handleUnpublish(row)">下架</el-button>
            <el-button type="info" link size="small" @click.stop="handleView(row)">查看</el-button>
            <el-button type="primary" link size="small" @click.stop="handleCopy(row)">复制</el-button>
            <el-button type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && tableData.length > 0" class="pagination-wrap">
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="handleDialogClose" :close-on-press-escape="true">
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
        <el-form-item label="课程类型" prop="courseType">
          <el-select v-model="formData.courseType" class="full-width">
            <el-option label="视频课程" value="VIDEO" />
            <el-option label="互动课程" value="INTERACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格(元)" prop="price">
          <el-input-number v-model="formData.price" :min="0" :precision="2" class="full-width" />
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
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getCourses, createCourse, updateCourseStatus, deleteCourse, approveCourse, rejectCourse, copyCourse } from '@/api/course'
import { getCategories } from '@/api/course-category'

const router = useRouter()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)

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
  difficulty: '',
  courseType: 'VIDEO',
  price: null
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
      status: searchForm.status !== '' ? searchForm.status : undefined,
      // 教师自动过滤为自己的课程
      teacherId: userStore.role === 'TEACHER' ? userStore.userId : undefined
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

const handleRowClick = (row) => {
  // 预留：可点击行查看详情
}

// a11y:让 el-table 行可被键盘聚焦,Enter 触发 handleRowClick(A11Y-016)
const tableRef = ref(null)
let _keydownBound = false
const bindTableKeyboard = () => {
  // 在表格 tbody 上挂 keydown 监听,聚焦行后 Enter/Space 等价于点击
  if (_keydownBound) return
  const tbody = tableRef.value?.$el?.querySelector('tbody')
  if (!tbody) return
  tbody.addEventListener('keydown', (e) => {
    const tr = e.target.closest('tr')
    if (!tr) return
    if (e.key !== 'Enter' && e.key !== ' ') return
    const idx = Array.from(tbody.querySelectorAll('tr')).indexOf(tr)
    const row = tableData.value?.[idx]
    if (row) {
      e.preventDefault()
      handleRowClick(row)
    }
  })
  tbody.querySelectorAll('tr').forEach((tr, idx) => {
    tr.setAttribute('tabindex', '0')
    tr.setAttribute('role', 'button')
    tr.setAttribute('aria-label', `选择课程 ${tableData.value?.[idx]?.title || ''}`)
  })
  _keydownBound = true
}
onMounted(() => nextTick(bindTableKeyboard))

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
  try { await ElMessageBox.confirm('确定审核通过该课程?', '提示', { type: 'warning' }) } catch { return }
  try { await approveCourse(row.id); ElMessage.success('审核通过成功'); fetchData() }
  catch { ElMessage.error('审核通过失败') }
}

const handleReject = async (row) => {
  let value
  try {
    const res = await ElMessageBox.prompt('请输入驳回原因（选填）', '驳回课程', {
      confirmButtonText: '确定驳回', cancelButtonText: '取消',
      inputType: 'textarea', inputPlaceholder: '请填写驳回原因，以便教师修改后重新提交'
    })
    value = res.value
  } catch { return }
  try { await rejectCourse(row.id, value || ''); ElMessage.success('驳回成功'); fetchData() }
  catch { ElMessage.error('驳回失败') }
}

const handlePublish = async (row) => {
  try { await ElMessageBox.confirm('确定发布该课程?', '提示', { type: 'warning' }) } catch { return }
  try { await updateCourseStatus(row.id, 4); ElMessage.success('发布成功'); fetchData() }
  catch { ElMessage.error('发布失败') }
}

const handleUnpublish = async (row) => {
  try { await ElMessageBox.confirm('确定下架该课程?', '提示', { type: 'warning' }) } catch { return }
  try { await updateCourseStatus(row.id, 5); ElMessage.success('下架成功'); fetchData() }
  catch { ElMessage.error('下架失败') }
}

const handleDelete = async (row) => {
  try { await ElMessageBox.confirm('确定删除该课程?', '提示', { type: 'warning' }) } catch { return }
  try { await deleteCourse(row.id); ElMessage.success('删除成功'); fetchData() }
  catch { ElMessage.error('删除失败') }
}

const handleCopy = async (row) => {
  try {
    const { data } = await copyCourse(row.id)
    const newId = data?.id || data
    ElMessage.success('复制成功，即将跳转到编辑页面')
    router.push(`/courses/${newId}/edit`)
  } catch {
    ElMessage.error('复制失败')
  }
}

const goSlides = (row) => {
  router.push(`/teacher/courses/${row.id}/slides`)
}
const goWorkspace = (row) => {
  router.push(`/teacher/courses/${row.id}/workspace`)
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
.course-list-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.page-breadcrumb {
  margin-bottom: var(--space-4);
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
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--role-primary-light-9);
}

.data-table :deep(.el-table__row--striped > td) {
  background: transparent;
}

.table-thumb {
  width: 48px;
  height: 32px;
  border-radius: var(--radius-md);
  object-fit: cover;
}

.no-thumb {
  color: var(--el-text-color-placeholder);
}

.review-hint {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  margin-top: var(--space-1);
  line-height: 1.2;
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

.filter-input-w120 {
  width: 120px;
}

.full-width {
  width: 100%;
}

/* Button border-radius */
:deep(.el-button) {
  border-radius: var(--radius-md);
}

/* Dialog border-radius */
:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}

@media (max-width: 768px) {
  .course-list-page {
    padding: var(--space-4);
  }

  .filter-card {
    margin-bottom: var(--space-4);
  }
}
</style>