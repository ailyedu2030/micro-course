<!--
  报名列表
  路由路径: /courses/enrollments
  Phase 1
  Author: jackie
-->
<template>
  <div class="enrollment-list-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">{{ $t('course.home') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('course.courseMgmt') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('enrollment.title') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 顶栏筛选卡 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('enrollment.student')">
          <el-input v-model="searchForm.studentName" :placeholder="$t('enrollment.studentNamePlaceholder')" clearable @clear="handleSearch" class="filter-input-w140" />
        </el-form-item>
        <el-form-item :label="$t('course.title')">
          <el-input v-model="searchForm.courseName" :placeholder="$t('course.courseName')" clearable @clear="handleSearch" class="filter-input-w180" />
        </el-form-item>
        <el-form-item :label="$t('course.status')">
          <el-select v-model="searchForm.status" :placeholder="$t('enrollment.pleaseSelectStatus')" clearable class="filter-input-w120">
            <el-option :label="$t('learning.tagLearning')" value="ENROLLED" />
            <el-option :label="$t('course.approved')" value="APPROVED" />
            <el-option :label="$t('course.pendingReview')" value="PENDING" />
            <el-option :label="$t('course.waitlisted')" value="WAITLIST" />
            <el-option :label="$t('course.completed')" value="COMPLETED" />
            <el-option :label="$t('studentList.statusCancelled')" value="CANCELLED" />
            <el-option :label="$t('enrollment.rejected')" value="REJECTED" />
            <el-option :label="$t('teachingClass.studentStatusDropped')" value="DROPPED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('app.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('app.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('enrollment.title') }}</span>
          <el-button type="success" size="small" :loading="exporting" @click="handleExport">
            <el-icon><Download /></el-icon>{{ $t('course.export') }}
          </el-button>
        </div>
      </template>
      <el-result v-if="error" icon="error" :title="$t('enrollment.loadFailed')" :sub-title="$t('enrollment.networkError')">
        <template #extra>
          <el-button type="primary" @click="fetchData">{{ $t('common.retry') }}</el-button>
        </template>
      </el-result>
      <template v-else>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="tableData.length === 0" :description="$t('enrollment.noData')" />
      <el-table v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column prop="userName" :label="$t('enrollment.student')" min-width="120" />
        <el-table-column prop="courseName" :label="$t('course.title')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="progress" :label="$t('learning.progressLabel')" width="140" align="center">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress :percentage="row.progress || 0" :stroke-width="8" />
              <span class="progress-text">{{ row.progress || 0 }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="enrolledAt" :label="$t('enrollment.enrolledAt')" width="170" :formatter="$formatDateTime" />
        <el-table-column prop="enrollmentStatus" :label="$t('course.status')" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.enrollmentStatus === 'ENROLLED'" type="primary" size="small">{{ $t('learning.tagLearning') }}</el-tag>
            <el-tag v-else-if="row.enrollmentStatus === 'PENDING'" type="warning" size="small">{{ $t('course.pendingReview') }}</el-tag>
            <el-tag v-else-if="row.enrollmentStatus === 'WAITLIST'" type="warning" size="small">{{ $t('course.waitlisted') }}</el-tag>
            <el-tag v-else-if="row.enrollmentStatus === 'APPROVED'" type="success" size="small">{{ $t('course.approved') }}</el-tag>
            <el-tag v-else-if="row.enrollmentStatus === 'COMPLETED'" type="success" size="small">{{ $t('course.completed') }}</el-tag>
            <el-tag v-else-if="row.enrollmentStatus === 'CANCELLED'" type="info" size="small">{{ $t('studentList.statusCancelled') }}</el-tag>
            <el-tag v-else-if="row.enrollmentStatus === 'DROPPED'" type="danger" size="small">{{ $t('teachingClass.studentStatusDropped') }}</el-tag>
            <el-tag v-else-if="row.enrollmentStatus === 'REJECTED'" type="danger" size="small">{{ $t('enrollment.rejected') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ row.enrollmentStatus || '-' }}</el-tag>
          </template>
        </el-table-column>
        <!-- P1C-056: 审核操作列 -->
        <el-table-column :label="$t('app.operation')" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.enrollmentStatus === 'PENDING'">
              <el-button type="success" link size="small" @click="handleApprove(row)">
                <el-icon><Check /></el-icon>{{ $t('course.statusApproved') }}
              </el-button>
              <el-button type="danger" link size="small" @click="handleReject(row)">
                <el-icon><Close /></el-icon>{{ $t('enrollment.reject') }}
              </el-button>
            </template>
            <template v-else-if="row.enrollmentStatus === 'WAITLIST'">
              <el-button type="warning" link size="small" @click="handlePromote(row)">
                <el-icon><Top /></el-icon>{{ $t('enrollment.promote') }}
              </el-button>
            </template>
            <span v-else class="text-secondary">-</span>
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
          @current-change="handlePageChange" :aria-label="$t('course.paginationAria')"
/>
      </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Top, Download } from '@element-plus/icons-vue'
import { getEnrollments, updateEnrollment, exportEnrollments } from '@/api/enrollment'
import { fetchAllPages } from '@/utils/fetchAllPages'

const { t } = useI18n()

const loading = ref(false)
const error = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const exporting = ref(false)

const searchForm = reactive({
  studentName: '',
  courseName: '',
  status: ''
})

const fetchData = async () => {
  loading.value = true
  error.value = false
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
    error.value = true
    ElMessage.error(t('enrollment.fetchFailed'))
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

// P1I-058: 导出选课数据
const handleExport = async () => {
  if (exporting.value) return
  exporting.value = true
  try {
    ElMessage.info(t('enrollment.exporting'))
    // 获取全量数据用于客户端导出
    // P1-I-2026-08-15（R3 审查）· 改用 fetchAllPages 循环分页，规避后端 size 上限触发 400
    const exportParams = {
      studentName: searchForm.studentName || undefined,
      courseName: searchForm.courseName || undefined,
      status: searchForm.status || undefined
    }
    const items = await fetchAllPages(getEnrollments, exportParams, 100)

    if (items.length === 0) {
      ElMessage.warning(t('enrollment.noExportData'))
      return
    }

    // 客户端 exceljs 导出（F-2026-08-10-22: xlsx 替换）
    const { Workbook } = await import('exceljs')
    const exportRows = items.map((item, index) => ({
      [t('course.index')]: index + 1,
      [t('enrollment.student')]: item.userName || '',
      [t('course.title')]: item.courseName || '',
      [t('learning.progressLabel')]: (item.progress || 0) + '%',
      [t('enrollment.enrolledAt')]: item.enrolledAt || '',
      [t('course.status')]: item.enrollmentStatus || ''
    }))
    const wb = new Workbook()
    const ws = wb.addWorksheet(t('enrollment.exportSheetName'))
    ws.addRows(exportRows.map(row => Object.values(row)))
    const date = new Date().toISOString().split('T')[0]
    await wb.xlsx.writeFile(`enrollments-${date}.xlsx`)
    ElMessage.success(t('enrollment.exportSuccess'))
  } catch (e) {
    ElMessage.error(t('enrollment.exportFailedMsg', { msg: e.message || t('enrollment.unknownError') }))
  } finally {
    exporting.value = false
  }
}

// P1C-056: 审核操作 - 通过选课
const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm(t('enrollment.confirmApprove', { name: row.userName }), t('enrollment.approveTitle'), {
      type: 'success',
      confirmButtonText: t('enrollment.confirmApproveBtn'),
      cancelButtonText: t('app.cancel')
    })
    await updateEnrollment(row.id, { enrollmentStatus: 'APPROVED' })
    ElMessage.success(t('course.approved'))
    fetchData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('course.operationFailed'))
  }
}

// P1C-056: 审核操作 - 拒绝选课
const handleReject = async (row) => {
  try {
    await ElMessageBox.confirm(t('enrollment.confirmReject', { name: row.userName }), t('enrollment.rejectTitle'), {
      type: 'warning',
      confirmButtonText: t('enrollment.confirmRejectBtn'),
      cancelButtonText: t('app.cancel')
    })
    await updateEnrollment(row.id, { enrollmentStatus: 'REJECTED' })
    ElMessage.success(t('enrollment.rejected'))
    fetchData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('course.operationFailed'))
  }
}

// P1C-056: 审核操作 - 晋升候补为已通过
const handlePromote = async (row) => {
  try {
    await ElMessageBox.confirm(t('enrollment.confirmPromote', { name: row.userName }), t('enrollment.promoteTitle'), {
      type: 'warning',
      confirmButtonText: t('enrollment.confirmPromoteBtn'),
      cancelButtonText: t('app.cancel')
    })
    await updateEnrollment(row.id, { enrollmentStatus: 'APPROVED' })
    ElMessage.success(t('enrollment.promoteSuccess'))
    fetchData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('course.operationFailed'))
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.enrollment-list-page {
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

.data-table :deep(.el-table__row--striped > td) {
  background: transparent;
}

.progress-cell {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.progress-text {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  min-width: 36px;
  font-variant-numeric: tabular-nums;
}

.filter-input-w140 {
  width: 140px;
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

:deep(.el-button) {
  border-radius: var(--radius-md);
}

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}

@media (max-width: 768px) {
  .enrollment-list-page {
    padding: var(--space-4);
  }

  .filter-card {
    margin-bottom: var(--space-4);
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
