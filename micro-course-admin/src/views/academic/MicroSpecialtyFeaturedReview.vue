<!--
  置顶申请审核（教务处端）
  路由: /academic/micro-specialties/featured
-->
<template>
  <div class="ms-featured-review">
    <el-page-header @back="$router.back()" content="置顶审核" class="mg-bottom-16" />

    <el-tabs v-model="activeTab" @tab-change="() => { page = 1; fetchData() }">
      <el-tab-pane label="待审批" name="PENDING" />
      <el-tab-pane label="全部" name="ALL" />
    </el-tabs>

    <el-card shadow="never">
      <el-alert v-if="error" title="加载失败" type="error" show-icon :closable="false" class="mg-bottom-12">
        <template #default><el-button size="small" @click="fetchData">重试</el-button></template>
      </el-alert>
      <el-table v-loading="loading" :data="items" stripe border>
        <template #empty>
          <el-empty description="暂无置顶申请">
            <el-button type="primary" @click="$router.push('/academic/micro-specialties/proposals')">查看申报列表</el-button>
          </el-empty>
        </template>
        <!-- P1I-067: 字段对齐 — MicroSpecialtyVO 实际字段: title/departmentName/leadTeacherName/featuredApplyReason/featuredApplyAt/featuredStatus -->
        <el-table-column prop="title" label="微专业" min-width="180" show-overflow-tooltip />
        <el-table-column prop="departmentName" label="学院" width="120" />
        <el-table-column prop="leadTeacherName" label="申请人" width="100" />
        <el-table-column prop="featuredApplyReason" label="申请理由" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.featuredStatus)" size="small">{{ statusLabel(row.featuredStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="featuredApplyAt" label="申请时间" width="130" align="center">
          <template #default="{ row }">{{ row.featuredApplyAt?.slice(0, 10) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.featuredStatus === 'PENDING'">
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

    <!-- 驳回原因 Dialog -->
    <el-dialog v-model="rejectVisible" title="驳回原因" width="480px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写驳回原因" maxlength="500" show-word-limit />
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
import { getMicroSpecialtyList, approveFeatured, rejectFeatured } from '@/api/microSpecialty'

const activeTab = ref('PENDING')
const loading = ref(false)
const actingId = ref(null)
const items = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const rejectVisible = ref(false)
const rejectReason = ref('')
const rejectTarget = ref(null)
const error = ref(false)

const statusMap = { PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回' }
const statusTypeMap = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
const statusLabel = (s) => statusMap[s] || s
const statusType = (s) => statusTypeMap[s] || 'info'

const fetchData = async () => {
  loading.value = true
  error.value = false
  try {
    const params = { page: page.value - 1, size: size.value }
    if (activeTab.value === 'PENDING') params.featuredStatus = 'PENDING'
    const res = await getMicroSpecialtyList(params)
    items.value = res.data.items || res.data || []
    total.value = res.data.totalElements || 0
  } catch (e) {
    error.value = true
    ElMessage.error('获取置顶申请列表失败')
  } finally {
    loading.value = false
  }
}

const handleApprove = async (row) => {
  try { await ElMessageBox.confirm(`确定批准「${row.title}」的置顶申请？`, '确认批准', { type: 'info', confirmButtonText: '批准', cancelButtonText: '取消' }) }
  catch { return }
  actingId.value = row.id
  try { await approveFeatured(row.id); ElMessage.success('已批准'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
  finally { actingId.value = null }
}

const handleReject = async (row) => {
  try { await ElMessageBox.confirm(`确定驳回「${row.title}」的置顶申请？`, '确认驳回', { type: 'warning', confirmButtonText: '驳回', cancelButtonText: '取消' }) }
  catch { return }
  rejectTarget.value = row; rejectReason.value = ''; rejectVisible.value = true
}
const confirmReject = async () => {
  actingId.value = rejectTarget.value.id
  try { await rejectFeatured(rejectTarget.value.id, rejectReason.value); ElMessage.success('已驳回'); rejectVisible.value = false; fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
  finally { actingId.value = null }
}

onMounted(fetchData)
</script>

<style scoped>
.ms-featured-review { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.mg-bottom-12 { margin-bottom: var(--space-3); }
.mg-top-12 { margin-top: var(--space-3); }
.pagination { display: flex; justify-content: flex-end; }
.no-action { color: var(--el-text-color-placeholder); }
</style>
