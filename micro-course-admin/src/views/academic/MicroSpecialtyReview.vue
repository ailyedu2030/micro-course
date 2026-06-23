<!--
  微专业审核（教务处端）
  路由: /academic/micro-specialties/review
-->
<template>
  <div class="ms-review">
    <el-page-header @back="$router.back()" content="微专业审核" class="mg-bottom-16" />

    <el-tabs v-model="activeTab" @tab-change="fetchData">
      <el-tab-pane label="待审批" name="PENDING" />
      <el-tab-pane label="全部" name="ALL" />
    </el-tabs>

    <el-card shadow="never">
      <el-alert v-if="error" title="加载失败" type="error" show-icon :closable="false" class="mg-bottom-12">
        <template #default><el-button size="small" @click="fetchData">重试</el-button></template>
      </el-alert>
      <el-table v-loading="loading" :data="items" stripe border>
        <template #empty><el-empty description="暂无待审核微专业" /></template>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="collegeName" label="学院" width="120" />
        <el-table-column prop="creatorName" label="创建者" width="100" />
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="130" align="center">
          <template #default="{ row }">{{ row.createdAt?.slice(0, 10) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="320" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING_REVIEW'">
              <el-button size="small" type="success" :loading="actingId === row.id" @click="handleApprove(row)">通过</el-button>
              <el-button size="small" type="danger" :loading="actingId === row.id" @click="handleReject(row)">驳回</el-button>
              <el-button size="small" @click="handleCancel(row)">取消</el-button>
            </template>
            <el-button v-if="row.status === 'COMPLETED'" size="small" type="info" @click="handleArchive(row)">归档</el-button>
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

    <!-- 驳回原因 Dialog -->
    <el-dialog v-model="rejectVisible" title="驳回原因" width="480px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写驳回原因" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="actingId !== null" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMicroSpecialtyList, approveMicroSpecialty, rejectMicroSpecialty, cancelMicroSpecialty, archiveMicroSpecialty } from '@/api/microSpecialty'

const activeTab = ref('PENDING')
const loading = ref(false)
const actingId = ref(null)
const items = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const rejectVisible = ref(false)
const rejectReason = ref('')
const rejectTarget = ref(null)

const statusMap = { DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '已通过', RECRUITING: '招生中', COMPLETED: '已结业', REJECTED: '已驳回', CANCELLED: '已取消', ARCHIVED: '已归档' }
const statusTypeMap = { DRAFT: 'info', PENDING_REVIEW: 'warning', APPROVED: 'success', RECRUITING: '', COMPLETED: 'info', REJECTED: 'danger', CANCELLED: 'danger', ARCHIVED: 'info' }
const error = ref(false)

const fetchData = async () => {
  loading.value = true
  error.value = false
  try {
    const params = { page: page.value, size: size.value }
    if (activeTab.value === 'PENDING') params.status = 'PENDING_REVIEW'
    const { data } = await getMicroSpecialtyList(params)
    items.value = data.items || data || []
    total.value = data.totalElements || 0
  } catch {
    error.value = true
    ElMessage.error('加载失败')
  }
  finally { loading.value = false }
}
const statusLabel = (s) => statusMap[s] || s
const statusType = (s) => statusTypeMap[s] || 'info'

const handleApprove = async (row) => {
  actingId.value = row.id
  try { await approveMicroSpecialty(row.id); ElMessage.success('已通过'); fetchData() }
  catch { ElMessage.error('操作失败') }
  finally { actingId.value = null }
}

const handleReject = (row) => { rejectTarget.value = row; rejectReason.value = ''; rejectVisible.value = true }
const confirmReject = async () => {
  actingId.value = rejectTarget.value.id
  try { await rejectMicroSpecialty(rejectTarget.value.id, { reason: rejectReason.value }); ElMessage.success('已驳回'); rejectVisible.value = false; fetchData() }
  catch { ElMessage.error('操作失败') }
  finally { actingId.value = null }
}

const handleCancel = async (row) => {
  try { await ElMessageBox.confirm('确定取消该微专业？', '确认', { type: 'warning' }) }
  catch { return }
  try { await cancelMicroSpecialty(row.id); ElMessage.success('已取消'); fetchData() }
  catch { ElMessage.error('操作失败') }
}

const handleArchive = async (row) => {
  try { await archiveMicroSpecialty(row.id); ElMessage.success('已归档'); fetchData() }
  catch { ElMessage.error('操作失败') }
}

onMounted(fetchData)
</script>

<style scoped>
.ms-review { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.mg-bottom-12 { margin-bottom: var(--space-3); }
.mg-top-12 { margin-top: var(--space-3); }
.pagination { display: flex; justify-content: flex-end; }
</style>
