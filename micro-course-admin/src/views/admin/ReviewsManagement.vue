<!--
  评价管理
  管理员/教务处 审核课程评价
  Route: /reviews
-->
<template>
  <div class="reviews-page">
    <el-breadcrumb separator="→" class="page-breadcrumb">
      <el-breadcrumb-item>课程管理</el-breadcrumb-item>
      <el-breadcrumb-item>评价管理</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">全部评价</span>
          <span class="card-total">共 {{ totalElements }} 条</span>
        </div>
      </template>

      <el-skeleton v-if="loading" :rows="5" animated />
      <el-empty v-else-if="tableData.length === 0" description="暂无评价数据" :image-size="120" />
      <el-table v-loading="loading" v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="#" width="60" align="center" />
        <el-table-column prop="realName" label="用户" width="120" show-overflow-tooltip />
        <el-table-column label="评分" width="140" align="center">
          <template #default="{ row }">
            <el-rate v-if="row.rating" :model-value="row.rating" disabled show-score text-color="#ff9900" score-template="{value} 分" />
            <span v-else class="text-muted">未评分</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="评价内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="匿名" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isAnonymous" type="warning" size="small">匿名</el-tag>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleApprove(row)">
              <el-icon><Select /></el-icon>通过
            </el-button>
            <el-button type="danger" link size="small" @click="handleReject(row)">
              <el-icon><Close /></el-icon>驳回
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>删除
            </el-button>
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getReviews, approveReview, rejectReview, deleteReview } from '@/api/review'
import { Select, Close, Delete } from '@element-plus/icons-vue'

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(20)

async function fetchData() {
  loading.value = true
  try {
    const { data } = await getReviews({ page: page.value - 1, size: size.value })
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch (err) {
    ElMessage.error('加载评价列表失败')
  } finally {
    loading.value = false
  }
}

async function handleApprove(row) {
  try {
    await approveReview(row.id)
    ElMessage.success('已通过')
    fetchData()
  } catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
}

async function handleReject(row) {
  try {
    await ElMessageBox.confirm('确定驳回该评价？', '提示', { type: 'warning' })
  } catch { return }
  try {
    await rejectReview(row.id)
    ElMessage.success('已驳回')
    fetchData()
  } catch (e) { ElMessage.error(e?.response?.data?.message || '驳回失败') }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除该评价？', '提示', { type: 'warning' })
  } catch { return }
  try {
    await deleteReview(row.id)
    ElMessage.success('已删除')
    fetchData()
  } catch (e) { ElMessage.error(e?.response?.data?.message || '删除失败') }
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
.reviews-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: calc(100dvh - 120px);
  max-width: 1280px;
  margin: 0 auto;
}
.page-breadcrumb { margin-bottom: var(--space-4); }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: var(--text-md); font-weight: var(--weight-semibold); }
.card-total { font-size: var(--text-sm); color: var(--el-text-color-secondary); }
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
