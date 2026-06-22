<!--
  跨学院教师审核（教务处端）
  路由: /academic/micro-specialties/cross-dept
-->
<template>
  <div class="ms-cross-dept">
    <el-page-header @back="$router.back()" content="跨学院教师审核" class="mg-bottom-16" />

    <el-tabs v-model="activeTab" @tab-change="fetchData">
      <el-tab-pane label="待审批" name="PENDING" />
      <el-tab-pane label="全部" name="ALL" />
    </el-tabs>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="items" stripe border>
        <template #empty><el-empty description="暂无待审批跨学院教师" /></template>
        <el-table-column prop="teacherName" label="教师" width="100" />
        <el-table-column prop="microSpecialtyTitle" label="微专业" min-width="180" show-overflow-tooltip />
        <el-table-column prop="role" label="角色" width="100" />
        <el-table-column prop="teacherCollege" label="教师学院" width="120" />
        <el-table-column prop="specialtyCollege" label="微专业学院" width="120" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.reviewStatus)" size="small">{{ statusLabel(row.reviewStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="邀请时间" width="130" align="center">
          <template #default="{ row }">{{ row.createdAt?.slice(0, 10) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.reviewStatus === 'PENDING' || row.reviewStatus === 'PENDING_CROSS_DEPT'">
              <el-button size="small" type="success" :loading="actingId === row.id" @click="handleApprove(row)">批准</el-button>
              <el-button size="small" type="danger" :loading="actingId === row.id" @click="handleReject(row)">驳回</el-button>
            </template>
            <span v-else class="no-action">-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination mg-top-12">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPendingInvites, reviewCrossDept } from '@/api/microSpecialty'

const activeTab = ref('PENDING')
const loading = ref(false)
const actingId = ref(null)
const items = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const statusMap = { PENDING: '待审批', PENDING_CROSS_DEPT: '待审批', APPROVED: '已批准', REJECTED: '已驳回' }
const statusTypeMap = { PENDING: 'warning', PENDING_CROSS_DEPT: 'warning', APPROVED: 'success', REJECTED: 'danger' }
const statusLabel = (s) => statusMap[s] || s
const statusType = (s) => statusTypeMap[s] || 'info'

const fetchData = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value, crossDept: true }
    if (activeTab.value === 'PENDING') params.status = 'PENDING_CROSS_DEPT'
    const { data } = await getPendingInvites(params)
    items.value = data.items || data || []
    total.value = data.totalElements || 0
  } catch { ElMessage.error('加载失败') }
  finally { loading.value = false }
}

const handleApprove = async (row) => {
  actingId.value = row.id
  try { await reviewCrossDept(row.id, { approved: true }); ElMessage.success('已批准'); fetchData() }
  catch { ElMessage.error('操作失败') }
  finally { actingId.value = null }
}

const handleReject = async (row) => {
  actingId.value = row.id
  try { await reviewCrossDept(row.id, { approved: false }); ElMessage.success('已驳回'); fetchData() }
  catch { ElMessage.error('操作失败') }
  finally { actingId.value = null }
}

onMounted(fetchData)
</script>

<style scoped>
.ms-cross-dept { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.mg-top-12 { margin-top: var(--space-3); }
.pagination { display: flex; justify-content: flex-end; }
.no-action { color: var(--el-text-color-placeholder); }
</style>
