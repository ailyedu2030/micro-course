<!--
  微专业审核（教务处端）
  路由: /academic/micro-specialties/review
-->
<template>
  <div class="ms-review">
    <el-page-header @back="$router.back()" content="微专业审核" class="mg-bottom-16" />

    <el-tabs v-model="activeTab" @tab-change="() => { page = 1; fetchData() }">
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
        <el-table-column label="操作" width="380" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING_REVIEW'">
              <el-button size="small" @click="showDetail(row)">查看</el-button>
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
    <el-dialog v-model="rejectVisible" title="驳回原因" width="480px" @close="rejectTarget.value = null">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写驳回原因" />
      <template #footer>
        <el-button @click="rejectVisible = false; rejectTarget.value = null">取消</el-button>
        <el-button type="danger" :loading="actingId !== null" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>
    <!-- 查看详情 Dialog -->
    <el-dialog v-model="detailVisible" title="微专业详情" width="560px">
      <div class="detail-grid" v-if="detailRow">
        <div class="detail-item"><label>标题</label><span>{{ detailRow.title }}</span></div>
        <div class="detail-item"><label>学院</label><span>{{ detailRow.collegeName || '-' }}</span></div>
        <div class="detail-item"><label>创建者</label><span>{{ detailRow.creatorName || '-' }}</span></div>
        <div class="detail-item"><label>学期</label><span>{{ detailRow.semester || '-' }}</span></div>
        <div class="detail-item"><label>招生上限</label><span>{{ detailRow.maxStudents || '-' }}</span></div>
        <div class="detail-item"><label>状态</label><span><el-tag :type="statusType(detailRow.status)" size="small">{{ statusLabel(detailRow.status) }}</el-tag></span></div>
        <div class="detail-item full-width"><label>说明</label><span v-html="sanitizeHtml(detailRow.description || '-')" class="detail-html"></span></div>
        <div class="detail-item full-width"><label>培养目标</label><span v-html="sanitizeHtml(detailRow.trainingObjective || '-')" class="detail-html"></span></div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMicroSpecialtyList, approveMicroSpecialty, rejectMicroSpecialty, cancelMicroSpecialty, archiveMicroSpecialty } from '@/api/microSpecialty'
import { useUserStore } from '@/store/user'
import { sanitizeHtml } from '@/utils/xss'

const userStore = useUserStore()
// 路由守卫竞态: 组件异步 load 在 store.getInfo() 前完成 → 错误 fetch → 403
const hasAccess = ['ACADEMIC', 'ADMIN'].includes(userStore.role)

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
const detailVisible = ref(false)
const detailRow = ref(null)

const showDetail = (row) => { detailRow.value = row; detailVisible.value = true }

const statusMap = { DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '已通过', RECRUITING: '招生中', COMPLETED: '已结业', REJECTED: '已驳回', CANCELLED: '已取消', ARCHIVED: '已归档' }
const statusTypeMap = { DRAFT: 'info', PENDING_REVIEW: 'warning', APPROVED: 'success', RECRUITING: '', COMPLETED: 'info', REJECTED: 'danger', CANCELLED: 'danger', ARCHIVED: 'info' }
const error = ref(false)

const fetchData = async () => {
  if (!hasAccess) return
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
  try { await ElMessageBox.confirm(`确定通过「${row.title}」的微专业？`, '确认通过', { type: 'info', confirmButtonText: '通过', cancelButtonText: '取消' }) }
  catch { return }
  actingId.value = row.id
  try { await approveMicroSpecialty(row.id); ElMessage.success('已通过'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
  finally { actingId.value = null }
}

const handleReject = async (row) => {
  try { await ElMessageBox.confirm(`确定驳回「${row.title}」？`, '确认驳回', { type: 'warning', confirmButtonText: '驳回', cancelButtonText: '取消' }) }
  catch { return }
  rejectTarget.value = row; rejectReason.value = ''; rejectVisible.value = true
}
const confirmReject = async () => {
  actingId.value = rejectTarget.value.id
  try { await rejectMicroSpecialty(rejectTarget.value.id, { reason: rejectReason.value }); ElMessage.success('已驳回'); rejectVisible.value = false; fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
  finally { actingId.value = null }
}

const handleCancel = async (row) => {
  try { await ElMessageBox.confirm('确定取消该微专业？', '确认', { type: 'warning' }) }
  catch { return }
  try { await cancelMicroSpecialty(row.id); ElMessage.success('已取消'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
}

const handleArchive = async (row) => {
  try { await ElMessageBox.confirm(`确定归档「${row.title}」？`, '确认归档', { type: 'info', confirmButtonText: '归档', cancelButtonText: '取消' }) }
  catch { return }
  try { await archiveMicroSpecialty(row.id); ElMessage.success('已归档'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
}

onMounted(fetchData)

</script>

<style scoped>
.ms-review { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.mg-bottom-12 { margin-bottom: var(--space-3); }
.mg-top-12 { margin-top: var(--space-3); }
.pagination { display: flex; justify-content: flex-end; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 24px; }
.detail-item { display: flex; flex-direction: column; gap: 4px; }
.detail-item.full-width { grid-column: 1 / -1; }
.detail-item label { font-size: 13px; color: #909399; }
.detail-item span { font-size: 14px; color: #303133; word-break: break-word; }
.detail-html { line-height: 1.6; }
.detail-html :deep(p) { margin: 4px 0; }
</style>
