<!--
  举报处理管理
  管理员 审核用户举报
  Route: /admin/reports
-->
<template>
  <div class="reports-page">
    <el-breadcrumb separator="→" class="page-breadcrumb">
      <el-breadcrumb-item>{{ $t('reportsManagement.systemMgmt') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('reportsManagement.title') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value">{{ stats.pending }}</div>
          <div class="stat-label">{{ $t('reportsManagement.statusPending') }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value">{{ stats.dismissed }}</div>
          <div class="stat-label">{{ $t('reportsManagement.statusDismissed') }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value">{{ stats.processed }}</div>
          <div class="stat-label">{{ $t('reportsManagement.statusProcessed') }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">{{ $t('app.all') }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm">
        <el-form-item :label="$t('app.status')">
          <el-select v-model="searchForm.status" :placeholder="$t('app.all')" clearable @change="handleSearch" style="width:140px">
            <el-option :label="$t('reportsManagement.statusPending')" :value="0" />
            <el-option :label="$t('reportsManagement.statusDismissed')" :value="1" />
            <el-option :label="$t('reportsManagement.statusProcessed')" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('app.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <el-skeleton v-if="loading" :rows="5" animated />
      <el-empty v-else-if="tableData.length === 0" :description="$t('reportsManagement.noData')" :image-size="120" />
      <el-table v-loading="loading" v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" :label="$t('reportsManagement.indexLabel')" width="60" align="center" />
        <el-table-column prop="reporterName" :label="$t('reportsManagement.reporter')" width="120" show-overflow-tooltip />
        <el-table-column :label="$t('reportsManagement.reportType')" width="150" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.reportedItemType)" size="small">
              {{ typeText(row.reportedItemType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('reportsManagement.reportedId')" width="100" align="center">
          <template #default="{ row }">
            <span>{{ row.reportedItemId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" :label="$t('reportsManagement.reason')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="$t('app.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reviewerName" :label="$t('reportsManagement.reviewer')" width="100" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.reviewerName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reviewNotes" :label="$t('reportsManagement.reviewNotes')" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.reviewNotes || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="$t('reportsManagement.reportTime')" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button type="warning" link size="small" @click="handleDismiss(row)">
                <el-icon><Close /></el-icon>{{ $t('reportsManagement.dismiss') }}
              </el-button>
              <el-button type="danger" link size="small" @click="handleRemove(row)">
                <el-icon><Delete /></el-icon>{{ $t('reportsManagement.remove') }}
              </el-button>
            </template>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="tableData.length > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminReports, reviewReport } from '@/api/review'
import { Close, Delete } from '@element-plus/icons-vue'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(20)

const searchForm = reactive({
  status: null,
})

const stats = reactive({
  pending: 0,
  dismissed: 0,
  processed: 0,
  total: 0,
})

async function fetchData() {
  loading.value = true
  try {
    const params = { page: page.value - 1, size: size.value }
    if (searchForm.status !== null && searchForm.status !== '') {
      params.status = searchForm.status
    }
    const { data } = await getAdminReports(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
    await computeStats()
  } catch (err) {
    ElMessage.error(t('reportsManagement.fetchFailed'))
  } finally {
    loading.value = false
  }
}

async function computeStats() {
  try {
    const [pendingRes, dismissedRes, processedRes] = await Promise.all([
      getAdminReports({ page: 0, size: 1, status: 0 }),
      getAdminReports({ page: 0, size: 1, status: 1 }),
      getAdminReports({ page: 0, size: 1, status: 2 })
    ])
    stats.pending = pendingRes.data.totalElements || 0
    stats.dismissed = dismissedRes.data.totalElements || 0
    stats.processed = processedRes.data.totalElements || 0
    stats.total = stats.pending + stats.dismissed + stats.processed
  } catch {
    // 静默降级：从当前页数据估算
    const all = tableData.value
    stats.total = totalElements.value
    stats.pending = all.filter(r => r.status === 0).length
    stats.dismissed = all.filter(r => r.status === 1).length
    stats.processed = all.filter(r => r.status === 2).length
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function handleReset() {
  searchForm.status = null
  page.value = 1
  fetchData()
}

async function handleDismiss(row) {
  try {
    const { value: notes } = await ElMessageBox.prompt(t('reportsManagement.dismissReasonLabel'), t('reportsManagement.dismissTitle'), {
      confirmButtonText: t('reportsManagement.confirmDismiss'),
      cancelButtonText: t('common.cancel'),
      inputType: 'textarea',
      inputPlaceholder: t('reportsManagement.dismissReasonPlaceholder'),
    })
    await reviewReport(row.id, { action: 'DISMISS', reviewNotes: notes || null })
    ElMessage.success(t('reportsManagement.statusDismissed'))
    fetchData()
  } catch (e) {
    if (!['cancel', 'close'].includes(e)) ElMessage.error(e?.response?.data?.message || t('common.failed'))
  }
}

async function handleRemove(row) {
  try {
    const { value: notes } = await ElMessageBox.prompt(t('reportsManagement.notesLabel'), t('reportsManagement.removeTitle'), {
      confirmButtonText: t('reportsManagement.confirmRemove'),
      cancelButtonText: t('common.cancel'),
      inputType: 'textarea',
      inputPlaceholder: t('reportsManagement.notesPlaceholder'),
      confirmButtonClass: 'el-button--danger',
    })
    await reviewReport(row.id, { action: 'REMOVE', reviewNotes: notes || null })
    ElMessage.success(t('reportsManagement.removedSuccess'))
    fetchData()
  } catch (e) {
    if (!['cancel', 'close'].includes(e)) ElMessage.error(e?.response?.data?.message || t('common.failed'))
  }
}

function typeText(type) {
  const map = {
    REVIEW: t('reportsManagement.typeReview'),
    DISCUSSION_POST: t('reportsManagement.typeDiscussionPost'),
    DISCUSSION_COMMENT: t('reportsManagement.typeDiscussionComment')
  }
  return map[type] || type
}

function typeTagType(type) {
  const map = { REVIEW: 'primary', DISCUSSION_POST: 'success', DISCUSSION_COMMENT: 'info' }
  return map[type] || ''
}

function statusTagType(status) {
  if (status === 0) return 'warning'
  if (status === 1) return 'info'
  if (status === 2) return 'success'
  return ''
}

function formatDate(iso) {
  if (!iso) return '-'
  const d = new Date(iso)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(fetchData)
</script>

<style scoped>
.reports-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: calc(100dvh - 120px);
  max-width: 1280px;
  margin: 0 auto;
}
.page-breadcrumb { margin-bottom: var(--space-4); }
.stats-row { margin-bottom: var(--space-4); }
.stat-card { text-align: center; padding: var(--space-4) 0; }
.stat-value { font-size: 32px; font-weight: var(--weight-bold); color: var(--el-color-primary); }
.stat-label { font-size: var(--text-sm); color: var(--el-text-color-secondary); margin-top: var(--space-1); }
.search-card { margin-bottom: var(--space-4); }
.table-card { margin-bottom: var(--space-6); }
.data-table { width: 100%; }
.text-muted { color: var(--el-text-color-placeholder); }
.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: center;
  padding: var(--space-4) 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
