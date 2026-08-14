<!--
  管理员 - 操作日志
  /admin/logs
  Author: jackie
-->
<template>
  <div class="operation-logs-container">
    <!-- 搜索筛选区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('user.userId')">
          <el-input
            v-model="searchForm.userId"
            :placeholder="$t('operationLogs.inputUserId')"
            clearable
            class="filter-input"
            @clear="debouncedSearch"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('operationLogs.operator')">
          <el-input
            v-model="searchForm.username"
            :placeholder="$t('operationLogs.inputUsername')"
            clearable
            class="filter-input"
            @clear="debouncedSearch"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('operationLogs.module')">
          <el-select
            v-model="searchForm.module"
            :placeholder="$t('operationLogs.allModules')"
            clearable
            class="filter-select"
            @change="debouncedSearch"
          >
            <el-option :label="$t('operationLogs.moduleUser')" value="USER" />
            <el-option :label="$t('operationLogs.moduleCourse')" value="COURSE" />
            <el-option :label="$t('operationLogs.moduleGrade')" value="GRADE" />
            <el-option :label="$t('operationLogs.moduleSetting')" value="SETTING" />
            <el-option :label="$t('operationLogs.modulePermission')" value="PERMISSION" />
            <el-option :label="$t('operationLogs.moduleAuth')" value="AUTH" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('operationLogs.actionType')">
          <el-select
            v-model="searchForm.action"
            :placeholder="$t('operationLogs.allActions')"
            clearable
            class="filter-select"
            @change="debouncedSearch"
          >
            <el-option :label="$t('operationLogs.actionLogin')" value="LOGIN" />
            <el-option :label="$t('operationLogs.actionLogout')" value="LOGOUT" />
            <el-option :label="$t('operationLogs.actionCreate')" value="CREATE" />
            <el-option :label="$t('operationLogs.actionUpdate')" value="UPDATE" />
            <el-option :label="$t('operationLogs.actionDelete')" value="DELETE" />
            <el-option :label="$t('operationLogs.actionApprove')" value="COURSE_APPROVE" />
            <el-option :label="$t('operationLogs.actionReject')" value="COURSE_REJECT" />
            <el-option :label="$t('operationLogs.actionOther')" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('operationLogs.targetIdLabel')">
          <el-input
            v-model="searchForm.targetId"
            :placeholder="$t('operationLogs.inputTargetId')"
            clearable
            class="filter-input"
            @clear="debouncedSearch"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('operationLogs.dateRange')">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            :range-separator="$t('operationLogs.to')"
            :start-placeholder="$t('operationLogs.startDate')"
            :end-placeholder="$t('operationLogs.endDate')"
            value-format="YYYY-MM-DD"
            class="date-range-picker"
            @change="handleDateChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :aria-label="$t('common.search')">
<el-icon><Search /></el-icon>{{ $t('common.search') }}
          </el-button>
          <el-button @click="handleReset" :aria-label="$t('common.reset')">
<el-icon><RefreshRight /></el-icon>{{ $t('common.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card shadow-hover" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('operationLogs.title') }}</span>
          <span class="card-count">{{ $t('operationLogs.totalRecords', { count: totalElements }) }}</span>
          <div class="card-actions">
            <el-button type="success" :loading="exporting" @click="handleExport">
              <el-icon><Download /></el-icon>{{ $t('operationLogs.exportExcel') }}
            </el-button>
          </div>
        </div>
      </template>

      <!-- 加载中 -->
      <el-skeleton v-if="loading" :rows="6" animated />

      <!-- 错误态 -->
      <el-result
        v-else-if="error"
        icon="error"
        :title="$t('operationLogs.loadFailed')"
        :sub-title="errorMessage"
        class="error-result"
      >
        <template #extra>
          <el-button type="primary" @click="fetchData">{{ $t('common.retry') }}</el-button>
        </template>
      </el-result>

      <!-- 空状态 -->
      <el-empty
        v-else-if="!loading && tableData.length === 0"
        :description="$t('operationLogs.noLogs')"
        :image-size="120"
      />

      <!-- 数据表格 -->
      <el-table
        v-else
        v-loading="loading" :aria-busy="loading"
        :data="tableData"
        stripe
        border
        class="data-table"
        row-key="id"
      >
        <!-- 可展开行：操作详情 -->
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-detail">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item :label="$t('operationLogs.requestMethod')">{{ row.method || '-' }}</el-descriptions-item>
                <el-descriptions-item :label="$t('operationLogs.requestPath')">{{ row.path || '-' }}</el-descriptions-item>
                <el-descriptions-item :label="$t('operationLogs.targetType')">{{ row.targetType || '-' }}</el-descriptions-item>
                <el-descriptions-item :label="$t('operationLogs.objectId')">{{ row.targetId || '-' }}</el-descriptions-item>
                <el-descriptions-item :label="$t('app.detail')" :span="2">{{ row.detail || '-' }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </template>
        </el-table-column>
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column prop="createdAt" :label="$t('app.time')" width="180">
          <template #default="{ row }">
            <span class="text-secondary">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="username" :label="$t('operationLogs.operator')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="ip" :label="$t('operationLogs.ipAddress')" width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="text-secondary">{{ row.ip || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="module" :label="$t('operationLogs.module')" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getModuleTagType(row.module)">
              {{ getModuleLabel(row.module) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="action" :label="$t('operationLogs.action')" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getActionTagType(row.action)">
              {{ getActionLabel(row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('app.status')" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.status === 0 ? 'danger' : 'success'"
              effect="light"
            >
              {{ row.status === 0 ? $t('operationLogs.failed') : $t('operationLogs.success') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" :label="$t('operationLogs.duration')" width="100" align="center">
          <template #default="{ row }">
            <span :class="['duration-text', row.duration > 1000 ? 'duration-slow' : '']">
              {{ row.duration != null ? `${row.duration}ms` : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewDetail(row)" :aria-label="$t('operationLogs.viewDetail')">
<el-icon><View /></el-icon>{{ $t('app.detail') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div v-if="tableData.length > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" :aria-label="$t('course.paginationAria')"
/>
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      :title="$t('operationLogs.logDetail')"
      width="560px"
      destroy-on-close
     :close-on-press-escape="true"
>
      <el-descriptions :column="2" border v-if="currentLog">
        <el-descriptions-item :label="$t('app.time')" :span="2">{{ formatTime(currentLog.createdAt) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('operationLogs.operator')">{{ currentLog.username || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('user.userId')">{{ currentLog.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('operationLogs.ipAddress')">{{ currentLog.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('operationLogs.module')">
          <el-tag size="small" :type="getModuleTagType(currentLog.module)">
            {{ getModuleLabel(currentLog.module) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('operationLogs.action')">
          <el-tag size="small" :type="getActionTagType(currentLog.action)">
            {{ getActionLabel(currentLog.action) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('app.status')">
          <el-tag
            size="small"
            :type="currentLog.status === 0 ? 'danger' : 'success'"
            effect="light"
          >
            {{ currentLog.status === 0 ? $t('operationLogs.failed') : $t('operationLogs.success') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="$t('operationLogs.duration')">{{ currentLog.duration != null ? `${currentLog.duration}ms` : '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('operationLogs.targetType')">{{ currentLog.targetType || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('operationLogs.objectId')">{{ currentLog.targetId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('operationLogs.requestMethod')">{{ currentLog.method || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('operationLogs.requestPath')">{{ currentLog.path || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('app.detail')" :span="2">{{ currentLog.detail || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">{{ $t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 管理员 - 操作日志
 * Vue 3.4 Composition API + script setup
 */
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useUrlPagination } from '@/composables/useUrlPagination';
import { swrCache } from '@/composables/useStaleWhileRevalidate';
import { ElMessage } from 'element-plus'
import { Search, RefreshRight, View, Download } from '@element-plus/icons-vue'
import { getLogs, exportOperationLogs } from '@/api/operation-log'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// 加载状态
const loading = ref(false)
const error = ref(false)
const errorMessage = ref(t('operationLogs.retryLater'))

// 表格数据
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(20)
const dateRange = ref(null)

// P1-2: 请求序列号（竞态防护）
let requestSeq = 0

// P1-4: 搜索防抖定时器
let searchTimer = null

// P1-10: 自动重试（500 错误重试一次）
let retryTimer = null

// 搜索表单
const searchForm = reactive({
  userId: '',
  username: '',
  module: '',
  action: '',
  startTime: '',
  endTime: '',
  targetId: ''
})

// P2-14: URL 分页同步
const { bindToQuery } = useUrlPagination()
bindToQuery(page, size, searchForm, ['userId', 'username', 'module', 'action', 'startTime', 'endTime'])

// 导出状态
const exporting = ref(false)

// 详情弹窗
const detailVisible = ref(false)
const currentLog = ref(null)

// 日期范围变化
function handleDateChange(val) {
  if (val && val.length === 2) {
    searchForm.startTime = val[0]
    searchForm.endTime = val[1]
  } else {
    searchForm.startTime = ''
    searchForm.endTime = ''
  }
  debouncedSearch()
}

// P1-4: 防抖搜索（300ms）
function debouncedSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    fetchData()
  }, 300)
}

// 立即搜索（搜索按钮点击）
function handleSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  page.value = 1
  fetchData()
}

// 重置
function handleReset() {
  if (searchTimer) clearTimeout(searchTimer)
  searchForm.userId = ''
  searchForm.username = ''
  searchForm.module = ''
  searchForm.action = ''
  searchForm.startTime = ''
  searchForm.endTime = ''
  searchForm.targetId = ''
  dateRange.value = null
  page.value = 1
  fetchData()
}

// 获取数据（含 P1-2 竞态防护）
async function fetchData() {
  const seq = ++requestSeq
  // P2-17: SWR 模式
  const params = {
    page: page.value - 1,
    size: size.value,
    userId: searchForm.userId ? (() => { const n = Number(searchForm.userId); return Number.isNaN(n) ? undefined : n })() : undefined,
    username: searchForm.username || undefined,
    module: searchForm.module || undefined,
    action: searchForm.action || undefined,
    startTime: searchForm.startTime || undefined,
    endTime: searchForm.endTime || undefined,
    targetId: searchForm.targetId ? (() => { const n = Number(searchForm.targetId); return Number.isNaN(n) ? undefined : n })() : undefined
  }
  const cacheKey = `OperationLogs:${JSON.stringify(params)}`
  const cached = swrCache.get(cacheKey)
  if (cached && Date.now() - cached.ts < 30000) {
    tableData.value = cached.data.items || []
    totalElements.value = cached.data.totalElements || 0
    getLogs(params).then(({ data }) => {
      swrCache.set(cacheKey, { data, ts: Date.now() })
      tableData.value = data.items || []
      totalElements.value = data.totalElements || 0
    }).catch(() => {})
    return
  }
  loading.value = true
  error.value = false
  errorMessage.value = t('operationLogs.retryLater')
  try {
    const { data } = await getLogs(params)
    // P2-17: SWR 缓存
    swrCache.set(cacheKey, { data, ts: Date.now() })
    // P1-2: 过期响应丢弃
    if (seq !== requestSeq) return
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch (e) {
    // P1-2: 过期请求的错误不处理
    if (seq !== requestSeq) return
    error.value = true
    // P2: 按状态码分类错误提示
    const status = e?.response?.status
    if (status === 401 || status === 403) {
      errorMessage.value = t('operationLogs.noAccessMessage')
      ElMessage.error(t('operationLogs.noAccessLogs'))
    } else if (status === 400) {
      errorMessage.value = t('operationLogs.invalidParams')
      ElMessage.warning(t('operationLogs.invalidParams'))
    } else if (status >= 500) {
      // P1-10: 服务器错误自动重试一次（3 秒后）
      if (!retryTimer) {
        errorMessage.value = t('operationLogs.serverBusyRetry')
        retryTimer = setTimeout(() => {
          retryTimer = null
          fetchData()
        }, 3000)
      } else {
        errorMessage.value = t('operationLogs.serverError')
        ElMessage.error(t('operationLogs.serverErrorRetryFailed'))
      }
    } else {
      errorMessage.value = t('operationLogs.networkError')
      ElMessage.error(t('operationLogs.fetchFailed'))
    }
  } finally {
    if (seq === requestSeq) {
      loading.value = false
    }
  }
}

// 翻页
function handleSizeChange() {
  page.value = 1
  fetchData()
}

function handlePageChange() {
  fetchData()
}

// 查看详情
function handleViewDetail(row) {
  currentLog.value = row
  detailVisible.value = true
}

// 导出 Excel
async function handleExport() {
  exporting.value = true
  try {
    const res = await exportOperationLogs(searchForm)
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `operation_logs_${formatTimestamp()}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(t('operationLogs.exportSuccess'))
  } catch (e) {
// eslint-disable-next-line no-console
    console.debug(e)
    ElMessage.error(t('operationLogs.exportFailed'))
  } finally {
    exporting.value = false
  }
}

// 工具方法
function formatTimestamp() {
  const d = new Date()
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`
}

function formatTime(isoString) {
  if (!isoString) return '-'
  const d = new Date(isoString)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function getModuleLabel(module) {
  const map = {
    USER: t('operationLogs.moduleUser'),
    COURSE: t('operationLogs.moduleCourse'),
    GRADE: t('operationLogs.moduleGrade'),
    SETTING: t('operationLogs.moduleSetting'),
    PERMISSION: t('operationLogs.modulePermission'),
    AUTH: t('operationLogs.moduleAuth')
  }
  return map[module] || module || '-'
}

function getModuleTagType(module) {
  const map = {
    USER: 'primary',
    COURSE: 'success',
    GRADE: 'warning',
    SETTING: 'info',
    PERMISSION: 'danger',
    AUTH: ''
  }
  return map[module] || 'info'
}

function getActionLabel(action) {
  const map = {
    LOGIN: t('operationLogs.actionLogin'),
    LOGOUT: t('operationLogs.actionLogout'),
    CREATE: t('operationLogs.actionCreate'),
    UPDATE: t('operationLogs.actionUpdate'),
    DELETE: t('operationLogs.actionDelete'),
    COURSE_APPROVE: t('operationLogs.actionApprove'),
    COURSE_REJECT: t('operationLogs.actionReject'),
    OTHER: t('operationLogs.actionOther')
  }
  return map[action] || action || '-'
}

function getActionTagType(action) {
  const map = {
    LOGIN: 'success',
    LOGOUT: 'info',
    CREATE: 'primary',
    UPDATE: 'warning',
    DELETE: 'danger',
    COURSE_APPROVE: 'success',
    COURSE_REJECT: 'danger',
    OTHER: 'info'
  }
  return map[action] || 'info'
}

onMounted(() => {
  fetchData()
})

// P1-4: 组件卸载时清理定时器
onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
  if (retryTimer) clearTimeout(retryTimer)
})
</script>

<style scoped>
.operation-logs-container {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.search-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.filter-input {
  width: 140px;
  border-radius: var(--radius-md);
}

.filter-select {
  width: 140px;
  border-radius: var(--radius-md);
}

.date-range-picker {
  width: 260px;
}

.table-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.table-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.card-actions {
  display: flex;
  gap: var(--space-2);
}

.card-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
}

.card-count {
  font-size: var(--text-base);
  color: var(--el-text-color-secondary);
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header th) {
  color: var(--el-text-color-primary);
}

.data-table :deep(.el-table__row:hover > td) {
  background: var(--role-primary-light-9) !important;
}

.data-table :deep(.el-table__row) {
  transition: background var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__body tr) {
  background: var(--el-fill-color-blank);
}

.data-table :deep(.el-table__body tr:hover > td) {
  background: var(--role-primary-light-9) !important;
}

.error-result {
  padding: var(--space-7) 0;
}

.pagination-wrap {
  margin-top: var(--space-6);
  display: flex;
  justify-content: flex-end;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.text-secondary {
  color: var(--el-text-color-secondary);
  font-size: var(--text-base);
}

/* 展开行详情 */
.expand-detail {
  padding: var(--space-3) var(--space-6);
  background: var(--el-fill-color-lighter);
}

.expand-detail :deep(.el-descriptions__label) {
  width: 90px;
  background: var(--el-fill-color-light) !important;
  color: var(--el-text-color-secondary);
  font-weight: var(--weight-medium);
}

/* 耗时文字 */
.duration-text {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.duration-slow {
  color: var(--el-color-danger);
  font-weight: var(--weight-semibold);
}

/* 弹窗 border-radius */
:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}
:deep(.el-dialog__header) {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
:deep(.el-dialog__body) {
  padding: var(--space-5);
}
:deep(.el-dialog__footer) {
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

/* el-descriptions 精致化 */
:deep(.el-descriptions__label) {
  background: var(--el-fill-color-light) !important;
  color: var(--el-text-color-primary);
  font-weight: var(--weight-medium);
}
:deep(.el-descriptions__cell) {
  padding: var(--space-3) var(--space-4) !important;
}
</style>
