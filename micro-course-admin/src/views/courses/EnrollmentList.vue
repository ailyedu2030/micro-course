<!--
  报名列表
  路由路径: /courses/enrollments
  Phase 1
  Author: jackie
-->
<template>
  <div class="enrollment-list-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>课程管理</el-breadcrumb-item>
      <el-breadcrumb-item>选课列表</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 顶栏筛选卡 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="学员">
          <el-input v-model="searchForm.studentName" placeholder="学员姓名" clearable class="filter-input-w140" />
        </el-form-item>
        <el-form-item label="课程">
          <el-input v-model="searchForm.courseName" placeholder="课程名称" clearable class="filter-input-w180" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable class="filter-input-w120">
            <el-option label="学习中" value="ENROLLED" />
            <el-option label="已取消" value="CANCELLED" />
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
          <span class="card-title">报名列表</span>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="tableData.length === 0" description="暂无报名数据" />
      <el-table v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="userName" label="学员" min-width="120" />
        <el-table-column prop="courseName" label="课程" min-width="180" show-overflow-tooltip />
        <el-table-column prop="progress" label="学习进度" width="140" align="center">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress :percentage="row.progress || 0" :stroke-width="8" />
              <span class="progress-text">{{ row.progress || 0 }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="enrolledAt" label="报名时间" width="170" />
        <el-table-column prop="enrollmentStatus" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.enrollmentStatus === 'ENROLLED'" type="primary" size="small">学习中</el-tag>
            <el-tag v-else-if="row.enrollmentStatus === 'CANCELLED'" type="info" size="small">已取消</el-tag>
            <el-tag v-else-if="row.enrollmentStatus === 'PENDING'" type="warning" size="small">待审核</el-tag>
            <el-tag v-else type="info" size="small">{{ row.enrollmentStatus || '-' }}</el-tag>
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
import { ElMessage } from 'element-plus'
import { getEnrollments } from '@/api/enrollment'

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const searchForm = reactive({
  studentName: '',
  courseName: '',
  status: ''
})

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      studentName: searchForm.studentName || undefined,
      courseName: searchForm.courseName || undefined,
      status: searchForm.status || undefined
    }
    const { data } = await getEnrollments(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取报名列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.studentName = ''
  searchForm.courseName = ''
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

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.enrollment-list-page {
  padding: 24px;
  background: #F5F6FA;
  min-height: 100%;
}

.filter-card {
  margin-bottom: 24px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  border: none;
}

.table-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  border: none;
}

.table-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #F1F5F9;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1E293B;
}

.pagination-wrap {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

.data-table {
  width: 100%;
  border-radius: 12px;
  overflow: hidden;
}

.data-table :deep(.el-table__header) th {
  background: #F8FAFC;
  font-weight: 600;
  color: #1E293B;
}

.data-table :deep(.el-table__row) {
  transition: background-color 0.2s ease;
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: #F1F5F9;
}

.data-table :deep(.el-table__row--striped > td) {
  background: transparent;
}

.progress-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-text {
  font-size: 12px;
  color: #909399;
  min-width: 36px;
}

.filter-input-w140 {
  width: 140px;
}

.filter-input-w180 {
  width: 180px;
}

.filter-input-w120 {
  width: 120px;
}

:deep(.el-button) {
  border-radius: 8px;
}

:deep(.el-dialog) {
  border-radius: 12px;
}

@media (max-width: 768px) {
  .enrollment-list-page {
    padding: 16px;
  }

  .filter-card {
    margin-bottom: 16px;
  }

  .filter-input-w140,
  .filter-input-w180,
  .filter-input-w120 {
    width: 100%;
  }

  .pagination-wrap {
    justify-content: center;
  }
}
</style>