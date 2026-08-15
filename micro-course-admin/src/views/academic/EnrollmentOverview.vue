<!--
  教务处 - 选课数据总览
  路由: /academic/enrollments
  P1C-077: 教务处专属选课数据概览页面
-->
<template>
  <div class="academic-enrollment-overview">
    <el-breadcrumb separator="→" class="page-breadcrumb">
      <el-breadcrumb-item :to="{ path: '/academic/dashboard' }">{{ $t('enrollmentOverview.workbench') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('route.AcademicEnrollments') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <el-card class="stat-card" shadow="never">
        <div class="stat-item">
          <div class="stat-value text-primary">{{ stats.totalEnrollments }}</div>
          <div class="stat-label">{{ $t('enrollmentOverview.totalEnrollments') }}</div>
        </div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-item">
          <div class="stat-value text-success">{{ stats.activeEnrollments }}</div>
          <div class="stat-label">{{ $t('enrollmentOverview.statusEnrolled') }}</div>
        </div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-item">
          <div class="stat-value text-warning">{{ stats.pendingEnrollments }}</div>
          <div class="stat-label">{{ $t('enrollmentOverview.statusPending') }}</div>
        </div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-item">
          <div class="stat-value text-info">{{ stats.completedEnrollments }}</div>
          <div class="stat-label">{{ $t('enrollmentOverview.statusCompleted') }}</div>
        </div>
      </el-card>
    </div>

    <!-- 搜索筛选 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('enrollment.student')">
          <el-input v-model="searchForm.studentName" :placeholder="$t('enrollment.studentNamePlaceholder')" clearable @clear="handleSearch" class="filter-input" />
        </el-form-item>
        <el-form-item :label="$t('enrollmentOverview.course')">
          <el-input v-model="searchForm.courseName" :placeholder="$t('enrollmentOverview.courseNamePlaceholder')" clearable @clear="handleSearch" class="filter-input" />
        </el-form-item>
        <el-form-item :label="$t('app.status')">
          <el-select v-model="searchForm.status" :placeholder="$t('enrollmentOverview.allStatuses')" clearable class="filter-input" @change="handleSearch">
            <el-option :label="$t('enrollmentOverview.statusEnrolled')" value="ENROLLED" />
            <el-option :label="$t('enrollmentOverview.statusApproved')" value="APPROVED" />
            <el-option :label="$t('enrollmentOverview.statusPending')" value="PENDING" />
            <el-option :label="$t('enrollmentOverview.statusCompleted')" value="COMPLETED" />
            <el-option :label="$t('enrollmentOverview.statusCancelled')" value="CANCELLED" />
            <el-option :label="$t('enrollmentOverview.statusDropped')" value="DROPPED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('enrollmentOverview.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('app.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('enrollmentOverview.detailTitle') }}</span>
          <span class="card-total">{{ $t('course.rows', { count: totalElements }) }}</span>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="tableData.length === 0" :description="$t('enrollmentOverview.noData')" :image-size="120" />
      <el-table v-loading="loading" v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" :label="$t('course.index')" width="60" align="center" />
        <el-table-column prop="realName" :label="$t('enrollment.student')" width="120" show-overflow-tooltip />
        <el-table-column prop="courseName" :label="$t('enrollmentOverview.course')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="$t('app.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'ENROLLED'" type="primary" size="small">{{ $t('enrollmentOverview.statusEnrolled') }}</el-tag>
            <el-tag v-else-if="row.status === 'APPROVED'" type="success" size="small">{{ $t('enrollmentOverview.statusApproved') }}</el-tag>
            <el-tag v-else-if="row.status === 'PENDING'" type="warning" size="small">{{ $t('enrollmentOverview.statusPending') }}</el-tag>
            <el-tag v-else-if="row.status === 'COMPLETED'" type="success" size="small">{{ $t('enrollmentOverview.statusCompleted') }}</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="info" size="small">{{ $t('enrollmentOverview.statusCancelled') }}</el-tag>
            <el-tag v-else-if="row.status === 'DROPPED'" type="danger" size="small">{{ $t('enrollmentOverview.statusDropped') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ row.status || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="progress" :label="$t('enrollmentOverview.progress')" width="100" align="center">
          <template #default="{ row }">
            <span>{{ row.progress != null ? row.progress + '%' : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="$t('enrollmentOverview.enrolledAt')" width="170" :formatter="$formatDateTime" />
      </el-table>
      <div v-if="tableData.length > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getEnrollments } from '@/api/enrollment'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(20)

const searchForm = reactive({
  studentName: '',
  courseName: '',
  status: ''
})

const stats = reactive({
  totalEnrollments: 0,
  activeEnrollments: 0,
  pendingEnrollments: 0,
  completedEnrollments: 0
})

async function fetchStats() {
  try {
    // 获取全量选课数据用于统计（不分页）
    const { data } = await getEnrollments({ page: 0, size: 100 })
    const items = data.items || []
    stats.totalEnrollments = data.totalElements || items.length
    stats.activeEnrollments = items.filter(i => i.status === 'ENROLLED').length
    stats.pendingEnrollments = items.filter(i => i.status === 'PENDING').length
    stats.completedEnrollments = items.filter(i => i.status === 'COMPLETED').length
  } catch {
    // 统计加载失败不影响主体表格
  }
}

async function fetchData() {
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
    ElMessage.error(t('enrollmentOverview.fetchFailed'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function handleReset() {
  searchForm.studentName = ''
  searchForm.courseName = ''
  searchForm.status = ''
  page.value = 1
  fetchData()
}

function handleSizeChange() {
  page.value = 1
  fetchData()
}

function handlePageChange() {
  fetchData()
}

function formatDate(iso) {
  if (!iso) return '-'
  const d = new Date(iso)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  fetchStats()
  fetchData()
})
</script>

<style scoped>
.academic-enrollment-overview {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: calc(100dvh - 120px);
  max-width: 1440px;
  margin: 0 auto;
}

.page-breadcrumb {
  margin-bottom: var(--space-4);
}

/* 统计卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-5);
  margin-bottom: var(--space-6);
}

.stat-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  text-align: center;
}

.stat-item {
  padding: var(--space-5) var(--space-4);
}

.stat-value {
  font-size: var(--text-3xl);
  font-weight: var(--weight-bold);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
  font-variant-numeric: tabular-nums;
}

.stat-label {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin-top: var(--space-2);
}

.text-primary { color: var(--el-color-primary); }
.text-success { color: var(--el-color-success); }
.text-warning { color: var(--el-color-warning); }
.text-info { color: var(--el-color-info); }

.search-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.filter-input {
  width: 180px;
}

.table-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
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
}

.card-total {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.data-table {
  width: 100%;
}

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: center;
  padding: var(--space-4) 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

@media (max-width: 1279px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
