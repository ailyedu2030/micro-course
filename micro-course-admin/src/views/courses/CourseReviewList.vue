<!--
  课程评价列表
  路由路径: /courses/reviews
  Phase 1
  Author: jackie
-->
<template>
  <div class="review-list-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>课程管理</el-breadcrumb-item>
      <el-breadcrumb-item>课程审核</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 顶栏筛选卡 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="课程">
          <el-input v-model="searchForm.courseName" placeholder="课程名称" clearable class="filter-input-w160" />
        </el-form-item>
        <el-form-item label="评分">
          <el-select v-model="searchForm.rating" placeholder="请选择评分" clearable class="filter-input-w120">
            <el-option label="5星" :value="5" />
            <el-option label="4星" :value="4" />
            <el-option label="3星" :value="3" />
            <el-option label="2星" :value="2" />
            <el-option label="1星" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable class="filter-input-w120">
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="待审核" value="PENDING" />
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
          <span class="card-title">评价列表</span>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="tableData.length === 0" description="暂无评价数据" />
      <el-table v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="courseName" label="课程" min-width="150" show-overflow-tooltip />
        <el-table-column prop="authorName" label="作者" width="120" />
        <el-table-column prop="rating" label="评分" width="100" align="center">
          <template #default="{ row }">
            <div class="rating-stars">
              <el-rate v-model="row.rating" disabled text-color="#ff9900" />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="评价内容" min-width="250" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="发布时间" width="170" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'PUBLISHED'" type="success" size="small">已发布</el-tag>
            <el-tag v-else-if="row.status === 'PENDING'" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="row.status === 'DELETED'" type="info" size="small">已删除</el-tag>
            <el-tag v-else type="info" size="small">{{ row.status || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" type="success" link size="small" @click="handleApprove(row)">通过</el-button>
            <el-button v-if="row.status === 'PENDING'" type="danger" link size="small" @click="handleReject(row)">驳回</el-button>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { getReviews, approveReview, rejectReview, deleteReview } from '@/api/review'

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const searchForm = reactive({
  courseName: '',
  rating: '',
  status: ''
})

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      courseName: searchForm.courseName || undefined,
      rating: searchForm.rating || undefined,
      status: searchForm.status || undefined
    }
    const { data } = await getReviews(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取评价列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.courseName = ''
  searchForm.rating = ''
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

const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm('确定通过该评价?', '提示', { type: 'warning' })
    await approveReview(row.id)
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
    await ElMessageBox.confirm('确定驳回该评价?', '提示', { type: 'warning' })
    await rejectReview(row.id)
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
    await ElMessageBox.confirm('确定删除该评价?', '提示', { type: 'warning' })
    await deleteReview(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.review-list-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100%;
  max-width: 1440px;
  margin: 0 auto;
}

.filter-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  border: 1px solid var(--el-border-color-lighter);
}

.table-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  border: 1px solid var(--el-border-color-lighter);
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
  margin-top: var(--space-6);
  display: flex;
  justify-content: flex-end;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header) th {
  background: var(--el-fill-color-light);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
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

.rating-stars {
  display: flex;
  align-items: center;
}

.filter-input-w160 {
  width: 160px;
}

.filter-input-w120 {
  width: 120px;
}

:deep(.el-button) {
  border-radius: var(--radius-md);
}

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}

@media (max-width: 768px) {
  .review-list-page {
    padding: var(--space-4);
  }

  .filter-card {
    margin-bottom: var(--space-4);
  }

  .filter-input-w160,
  .filter-input-w120 {
    width: 100%;
  }

  .pagination-wrap {
    justify-content: center;
  }
}
</style>