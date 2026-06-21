<!--
  讨论列表
  路由路径: /discussions
  Phase 1
  Author: jackie
-->
<template>
  <div class="discussion-list-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>课程管理</el-breadcrumb-item>
      <el-breadcrumb-item>讨论管理</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 顶栏筛选卡 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="searchForm.keyword" placeholder="标题/内容" clearable class="filter-input-w160" />
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable class="filter-input-w180">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable class="filter-input-w120">
            <el-option label="待审核" value="PENDING" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已删除" value="DELETED" />
          </el-select>
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
          <span class="card-title">讨论列表</span>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="tableData.length === 0" description="暂无讨论数据" />
      <el-table v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="authorName" label="作者" width="120" />
        <el-table-column prop="courseName" label="课程" min-width="150" show-overflow-tooltip />
        <el-table-column prop="replyCount" label="回复数" width="100" align="center">
          <template #default="{ row }">
            {{ row.replyCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="发布时间" width="170" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.statusStr === 'PENDING'" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="row.statusStr === 'APPROVED' || row.statusStr === 'PUBLISHED'" type="success" size="small">已发布</el-tag>
            <el-tag v-else-if="row.statusStr === 'REJECTED'" type="danger" size="small">已驳回</el-tag>
            <el-tag v-else-if="row.statusStr === 'DELETED'" type="info" size="small">已删除</el-tag>
            <el-tag v-else type="info" size="small">{{ row.statusStr || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleView(row)">查看</el-button>
            <el-button v-if="(row.status === 'PENDING' || row.statusStr === 'PENDING')" type="success" link size="small" @click="handleApprove(row)">通过</el-button>
            <el-button v-if="(row.status === 'PENDING' || row.statusStr === 'PENDING')" type="danger" link size="small" @click="handleReject(row)">驳回</el-button>
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
          @current-change="handlePageChange" aria-label="分页导航" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getDiscussions, approveDiscussion, rejectDiscussion, deleteDiscussion } from '@/api/discussion'
import { getCourses } from '@/api/course'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const courseOptions = ref([])

const searchForm = reactive({
  keyword: '',
  courseId: '',
  status: ''
})

const fetchCourseOptions = async () => {
  try {
    const params = { page: 0, size: 1000 }
    if (userStore?.role === 'TEACHER') params.teacherId = userStore.userId
    const { data } = await getCourses(params)
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error('获取课程列表失败')
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      keyword: searchForm.keyword || undefined,
      courseId: searchForm.courseId || undefined,
      status: searchForm.status || undefined
    }
    const { data } = await getDiscussions(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取讨论列表失败')
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
  searchForm.courseId = ''
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

const handleView = (row) => {
  router.push(`/discussions/${row.id}`)
}

const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm('确定通过该讨论?', '提示', { type: 'warning' })
    await approveDiscussion(row.id)
    ElMessage.success('审核通过')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleReject = async (row) => {
  try {
    await ElMessageBox.confirm('确定驳回该讨论?', '提示', { type: 'warning' })
    await rejectDiscussion(row.id)
    ElMessage.success('驳回成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该讨论?', '提示', { type: 'warning' })
    await deleteDiscussion(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  fetchCourseOptions()
  fetchData()
})
</script>

<style scoped>
.discussion-list-page {
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

.filter-input-w160 {
  width: 160px;
}

.filter-input-w180 {
  width: 180px;
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

@media (max-width: 768px) {
  .discussion-list-page {
    padding: var(--space-4);
  }

  .filter-card {
    margin-bottom: var(--space-4);
  }

  .filter-input-w160,
  .filter-input-w180,
  .filter-input-w120 {
    width: 100%;
  }

  .pagination-wrap {
    justify-content: center;
  }
}
</style>