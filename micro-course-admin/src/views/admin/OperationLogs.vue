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
        <el-form-item label="用户ID">
          <el-input
            v-model="searchForm.userId"
            placeholder="输入用户ID"
            clearable
            class="filter-input"
            @clear="debouncedSearch"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input
            v-model="searchForm.username"
            placeholder="输入用户名"
            clearable
            class="filter-input"
            @clear="debouncedSearch"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="模块">
          <el-select
            v-model="searchForm.module"
            placeholder="全部模块"
            clearable
            class="filter-select"
            @change="debouncedSearch"
          >
            <el-option label="用户管理" value="USER" />
            <el-option label="课程管理" value="COURSE" />
            <el-option label="成绩管理" value="GRADE" />
            <el-option label="系统设置" value="SETTING" />
            <el-option label="权限管理" value="PERMISSION" />
            <el-option label="认证" value="AUTH" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作类型">
          <el-select
            v-model="searchForm.action"
            placeholder="全部动作"
            clearable
            class="filter-select"
            @change="debouncedSearch"
          >
            <el-option label="登录" value="LOGIN" />
            <el-option label="登出" value="LOGOUT" />
            <el-option label="创建" value="CREATE" />
            <el-option label="更新" value="UPDATE" />
            <el-option label="删除" value="DELETE" />
            <el-option label="审核通过" value="COURSE_APPROVE" />
            <el-option label="审核驳回" value="COURSE_REJECT" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标ID">
          <el-input
            v-model="searchForm.targetId"
            placeholder="输入目标ID"
            clearable
            class="filter-input"
            @clear="debouncedSearch"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            class="date-range-picker"
            @change="handleDateChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" aria-label="搜索">
<el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="handleReset" aria-label="重置">
<el-icon><RefreshRight /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card shadow-hover" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">操作日志</span>
          <span class="card-count">共 {{ totalElements }} 条记录</span>
        </div>
      </template>

      <!-- 加载中 -->
      <el-skeleton v-if="loading" :rows="6" animated />

      <!-- 错误态 -->
      <el-result
        v-else-if="error"
        icon="error"
        title="数据加载失败"
        :sub-title="errorMessage"
        class="error-result"
      >
        <template #extra>
          <el-button type="primary" @click="fetchData">重试</el-button>
        </template>
      </el-result>

      <!-- 空状态 -->
      <el-empty
        v-else-if="!loading && tableData.length === 0"
        description="暂无操作日志"
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
                <el-descriptions-item label="请求方法">{{ row.method || '-' }}</el-descriptions-item>
                <el-descriptions-item label="请求路径">{{ row.path || '-' }}</el-descriptions-item>
                <el-descriptions-item label="对象类型">{{ row.targetType || '-' }}</el-descriptions-item>
                <el-descriptions-item label="对象ID">{{ row.targetId || '-' }}</el-descriptions-item>
                <el-descriptions-item label="详情" :span="2">{{ row.detail || '-' }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </template>
        </el-table-column>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="createdAt" label="时间" width="180">
          <template #default="{ row }">
            <span class="text-secondary">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="操作人" min-width="140" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP 地址" width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="text-secondary">{{ row.ip || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getModuleTagType(row.module)">
              {{ getModuleLabel(row.module) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="动作" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getActionTagType(row.action)">
              {{ getActionLabel(row.action) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.status === 0 ? 'danger' : 'success'"
              effect="light"
            >
              {{ row.status === 0 ? '失败' : '成功' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="耗时" width="100" align="center">
          <template #default="{ row }">
            <span :class="['duration-text', row.duration > 1000 ? 'duration-slow' : '']">
              {{ row.duration != null ? `${row.duration}ms` : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewDetail(row)" aria-label="查看详情">
<el-icon><View /></el-icon>详情
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
          @current-change="handlePageChange" aria-label="分页导航"
/>
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="日志详情"
      width="560px"
      destroy-on-close
     :close-on-press-escape="true"
>
      <el-descriptions :column="2" border v-if="currentLog">
        <el-descriptions-item label="时间" :span="2">{{ formatTime(currentLog.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentLog.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ currentLog.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="IP 地址">{{ currentLog.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模块">
          <el-tag size="small" :type="getModuleTagType(currentLog.module)">
            {{ getModuleLabel(currentLog.module) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="动作">
          <el-tag size="small" :type="getActionTagType(currentLog.action)">
            {{ getActionLabel(currentLog.action) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag
            size="small"
            :type="currentLog.status === 0 ? 'danger' : 'success'"
            effect="light"
          >
            {{ currentLog.status === 0 ? '失败' : '成功' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="耗时">{{ currentLog.duration != null ? `${currentLog.duration}ms` : '-' }}</el-descriptions-item>
        <el-descriptions-item label="对象类型">{{ currentLog.targetType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="对象ID">{{ currentLog.targetId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ currentLog.method || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求路径">{{ currentLog.path || '-' }}</el-descriptions-item>
        <el-descriptions-item label="详情" :span="2">{{ currentLog.detail || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
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
import { ElMessage } from 'element-plus'
import { Search, RefreshRight, View } from '@element-plus/icons-vue'
import { getLogs } from '@/api/operation-log'

// 加载状态
const loading = ref(false)
const error = ref(false)
const errorMessage = ref('请稍后重试')

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
  loading.value = true
  error.value = false
  errorMessage.value = '请稍后重试'
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      userId: searchForm.userId ? Number(searchForm.userId) : undefined,
      username: searchForm.username || undefined,
      module: searchForm.module || undefined,
      action: searchForm.action || undefined,
      startTime: searchForm.startTime || undefined,
      endTime: searchForm.endTime || undefined,
      targetId: searchForm.targetId ? Number(searchForm.targetId) : undefined
    }
    const { data } = await getLogs(params)
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
      errorMessage.value = '无权访问，请确认登录状态'
      ElMessage.error('无权访问操作日志')
    } else if (status === 400) {
      errorMessage.value = '请求参数有误，请检查筛选条件'
      ElMessage.warning('请求参数有误，请检查筛选条件')
    } else if (status >= 500) {
      errorMessage.value = '服务器异常，请稍后重试'
      ElMessage.error('服务器异常，请稍后重试')
    } else {
      errorMessage.value = '网络异常，请检查网络连接'
      ElMessage.error('获取操作日志失败')
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

// 工具方法
function formatTime(isoString) {
  if (!isoString) return '-'
  const d = new Date(isoString)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function getModuleLabel(module) {
  const map = {
    USER: '用户管理',
    COURSE: '课程管理',
    GRADE: '成绩管理',
    SETTING: '系统设置',
    PERMISSION: '权限管理',
    AUTH: '认证'
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
    LOGIN: '登录',
    LOGOUT: '登出',
    CREATE: '创建',
    UPDATE: '更新',
    DELETE: '删除',
    COURSE_APPROVE: '审核通过',
    COURSE_REJECT: '审核驳回',
    OTHER: '其他'
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
