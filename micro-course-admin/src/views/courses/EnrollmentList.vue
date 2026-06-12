<!--
  报名列表
  路由路径: /courses/enrollments
  Phase 1
  Author: jackie
-->
<template>
  <div class="page-container">
    <!-- 顶栏筛选卡 -->
    <div class="filter-card">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="学员">
          <el-input v-model="searchForm.studentName" placeholder="学员姓名" clearable class="filter-input-w140" />
        </el-form-item>
        <el-form-item label="课程">
          <el-input v-model="searchForm.courseName" placeholder="课程名称" clearable class="filter-input-w180" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="请选择状态" clearable class="filter-input-w120">
            <el-option label="学习中" value="ACTIVE" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已过期" value="EXPIRED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 表格卡 -->
    <div class="table-card">
      <div class="card-header">
        <span class="card-title">报名列表</span>
      </div>
      <el-table v-loading="loading" :data="tableData" stripe class="data-table">
        <template #empty>
          <el-empty description="暂无报名数据" />
        </template>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="studentName" label="学员" min-width="120" />
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
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'ACTIVE'" type="primary" size="small">学习中</el-tag>
            <el-tag v-else-if="row.status === 'COMPLETED'" type="success" size="small">已完成</el-tag>
            <el-tag v-else-if="row.status === 'EXPIRED'" type="warning" size="small">已过期</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="info" size="small">已取消</el-tag>
            <el-tag v-else type="info" size="small">{{ row.status || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expiredAt" label="到期时间" width="170" />
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
    </div>
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
.page-container {
  padding: 24px 28px;
  background: #F8F9FB;
  min-height: 100%;
}

.filter-card {
  background: white;
  border-radius: 14px;
  padding: 16px 20px;
  margin-bottom: 16px;
  box-shadow: none;
  border: 1px solid #EDEFF2;
}

.table-card {
  background: white;
  border-radius: 14px;
  box-shadow: none;
  border: 1px solid #EDEFF2;
}

.table-card :deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #F0F1F3;
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
  padding: 16px 20px;
  border-top: 1px solid #F0F1F3;
  display: flex;
  justify-content: flex-end;
}

.data-table {
  width: 100%;
}

:deep(.el-table) {
  border: none !important;
}
:deep(.el-table th.el-table__cell) {
  background: #F8F9FB;
  color: #8A94A6;
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 12px 16px;
  border-bottom: 1px solid #EDEFF2;
}
:deep(.el-table td.el-table__cell) {
  padding: 14px 16px;
  color: #2C3E50;
  border-bottom: 1px solid #F0F1F3;
}
:deep(.el-table__row:hover > td) {
  background: #F6F8FA !important;
}
:deep(.el-table .el-table__row--striped td) {
  background: transparent !important;
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
  .page-container {
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