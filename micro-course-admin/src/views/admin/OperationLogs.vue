<!--
  操作日志页面
  /admin/logs
  Phase 7 - 管理后台补齐
-->
<template>
  <div class="operation-logs-container">
    <!-- 搜索筛选区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="用户ID">
          <el-input v-model="searchForm.userId" placeholder="请输入用户ID" clearable class="search-user-id" />
        </el-form-item>
        <el-form-item label="动作类型">
          <el-select v-model="searchForm.action" placeholder="全部" clearable class="search-action">
            <el-option label="登录" value="LOGIN" />
            <el-option label="登出" value="LOGOUT" />
            <el-option label="创建" value="CREATE" />
            <el-option label="更新" value="UPDATE" />
            <el-option label="删除" value="DELETE" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            class="search-date-range"
            @change="handleDateChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card shadow-hover" shadow="never">
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="createdAt" label="时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" width="100" align="center" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="action" label="动作" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.action === 'LOGIN'" type="success" size="small">登录</el-tag>
            <el-tag v-else-if="row.action === 'LOGOUT'" type="info" size="small">登出</el-tag>
            <el-tag v-else-if="row.action === 'CREATE'" type="primary" size="small">创建</el-tag>
            <el-tag v-else-if="row.action === 'UPDATE'" type="warning" size="small">更新</el-tag>
            <el-tag v-else-if="row.action === 'DELETE'" type="danger" size="small">删除</el-tag>
            <el-tag v-else type="info" size="small">其他</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetType" label="对象类型" width="120" align="center">
          <template #default="{ row }">{{ row.targetType || '-' }}</template>
        </el-table-column>
        <el-table-column prop="targetId" label="对象ID" width="100" align="center">
          <template #default="{ row }">{{ row.targetId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP地址" width="140" align="center">
          <template #default="{ row }">{{ row.ipAddress || '-' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && tableData.length === 0" description="暂无操作日志" />
      <div class="pagination-wrap">
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
/**
 * 操作日志页面
 * Vue 3.4 Composition API + script setup
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getLogs } from '@/api/operation-log'

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(20)
const dateRange = ref(null)

const searchForm = reactive({
  userId: '',
  action: '',
  startTime: '',
  endTime: ''
})

function handleDateChange(val) {
  if (val && val.length === 2) {
    searchForm.startTime = val[0]
    searchForm.endTime = val[1]
  } else {
    searchForm.startTime = ''
    searchForm.endTime = ''
  }
}

function formatTime(isoString) {
  if (!isoString) return '-'
  const d = new Date(isoString)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function fetchData() {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      userId: searchForm.userId || undefined,
      action: searchForm.action || undefined,
      startTime: searchForm.startTime || undefined,
      endTime: searchForm.endTime || undefined
    }
    const { data } = await getLogs(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取操作日志失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function handleReset() {
  searchForm.userId = ''
  searchForm.action = ''
  searchForm.startTime = ''
  searchForm.endTime = ''
  dateRange.value = null
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

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.operation-logs-container {
  padding: 20px;
}

.search-card {
  margin-bottom: 16px;
}

.search-user-id {
  width: 140px;
}

.search-action {
  width: 140px;
}

.search-date-range {
  width: 240px;
}

.data-table {
  width: 100%;
}

.table-card :deep(.el-card__header) {
  padding: 12px 20px;
}

.table-card {
  transition: box-shadow var(--el-transition-duration) ease;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>