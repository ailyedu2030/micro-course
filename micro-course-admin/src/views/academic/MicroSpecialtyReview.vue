<!--
  微专业审核（教务处端）
  路由: /academic/micro-specialties/review
-->
<template>
  <div class="ms-review">
    <el-page-header @back="$router.back()" :content="$t('route.AcademicMicroSpecialtyReview')" class="mg-bottom-16" />

    <el-tabs v-model="activeTab" @tab-change="() => { page = 1; fetchData() }">
      <el-tab-pane :label="$t('microSpecialtyReview.tabPending')" name="PENDING" />
      <el-tab-pane :label="$t('app.all')" name="ALL" />
    </el-tabs>

    <el-card shadow="never">
      <el-alert v-if="error" :title="$t('microSpecialtyManage.loadFailed')" type="error" show-icon :closable="false" class="mg-bottom-12">
        <template #default><el-button size="small" @click="fetchData">{{ $t('common.retry') }}</el-button></template>
      </el-alert>
      <el-table v-loading="loading" :data="items" stripe border>
        <template #empty><el-empty :description="$t('microSpecialtyReview.emptyPending')" style="max-width: 100%; min-width: 0; --el-empty-padding: 0;" /></template>
        <el-table-column prop="title" :label="$t('course.tableTitle')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="departmentName" :label="$t('microSpecialtyReview.college')" width="120" />
        <el-table-column prop="creatorName" :label="$t('microSpecialtyReview.creator')" width="100" />
        <el-table-column :label="$t('app.status')" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="$t('microSpecialtyReview.createdAt')" width="130" align="center" :formatter="$formatDateTime">
          <template #default="{ row }">{{ $formatDate(row.createdAt) || '-' }}</template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="380" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING_REVIEW'">
              <el-button size="small" @click="showDetail(row)">{{ $t('course.view') }}</el-button>
              <el-button size="small" type="success" :loading="actingId === row.id" @click="handleApprove(row)">{{ $t('microSpecialtyReview.approve') }}</el-button>
              <el-button size="small" type="danger" :loading="actingId === row.id" @click="handleReject(row)">{{ $t('microSpecialtyReview.reject') }}</el-button>
              <el-button size="small" @click="handleCancel(row)">{{ $t('app.cancel') }}</el-button>
            </template>
            <el-button v-if="row.status === 'COMPLETED' && (userStore.role === 'ACADEMIC' || userStore.role === 'ADMIN')" size="small" type="primary" @click="handleReopen(row)">{{ $t('microSpecialtyManage.reopen') }}</el-button>
            <el-button v-if="row.status === 'COMPLETED'" size="small" type="info" @click="handleArchive(row)">{{ $t('course.archive') }}</el-button>
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
    <el-dialog v-model="rejectVisible" :title="$t('microSpecialtyReview.rejectReasonTitle')" width="480px" @close="rejectTarget.value = null">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" @submit.prevent>
        <el-form-item prop="reason">
          <el-input v-model="rejectForm.reason" type="textarea" :rows="3" :placeholder="$t('microSpecialtyReview.rejectReasonPlaceholder')" maxlength="500" show-word-limit :aria-label="$t('microSpecialtyReview.rejectReasonTitle')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false; rejectTarget.value = null">{{ $t('common.cancel') }}</el-button>
        <el-button type="danger" :loading="actingId !== null" @click="confirmReject">{{ $t('microSpecialtyReview.confirmReject') }}</el-button>
      </template>
    </el-dialog>
    <!-- 查看详情 Dialog -->
    <el-dialog v-model="detailVisible" :title="$t('microSpecialtyReview.detailTitle')" width="560px">
      <div class="detail-grid" v-if="detailRow">
        <div class="detail-item"><label>{{ $t('course.tableTitle') }}</label><span>{{ detailRow.title }}</span></div>
        <div class="detail-item"><label>{{ $t('microSpecialtyReview.college') }}</label><span>{{ detailRow.departmentName || '-' }}</span></div>
        <div class="detail-item"><label>{{ $t('microSpecialtyReview.creator') }}</label><span>{{ detailRow.creatorName || '-' }}</span></div>
        <div class="detail-item"><label>{{ $t('course.semester') }}</label><span>{{ detailRow.semester || '-' }}</span></div>
        <div class="detail-item"><label>{{ $t('microSpecialtyReview.maxStudents') }}</label><span>{{ detailRow.maxStudents || '-' }}</span></div>
        <div class="detail-item"><label>{{ $t('app.status') }}</label><span><el-tag :type="statusType(detailRow.status)" size="small">{{ statusLabel(detailRow.status) }}</el-tag></span></div>
        <div class="detail-item full-width"><label>{{ $t('microSpecialtyManage.description') }}</label><span v-html="sanitizeHtml(detailRow.description || '-')" class="detail-html"></span></div>
        <div class="detail-item full-width"><label>{{ $t('microSpecialtyManage.trainingObjective') }}</label><span v-html="sanitizeHtml(detailRow.trainingObjective || '-')" class="detail-html"></span></div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">{{ $t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMicroSpecialtyList, approveMicroSpecialty, rejectMicroSpecialty, cancelMicroSpecialty, archiveMicroSpecialty, reopenMicroSpecialty } from '@/api/microSpecialty'
import { useUserStore } from '@/store/user'
import { sanitizeHtml } from '@/utils/xss'

const userStore = useUserStore()
const { t } = useI18n()
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
// P1-C 修复: 驳回原因必填校验（此前可空原因直接驳回）
const rejectForm = reactive({ reason: '' })
const rejectFormRef = ref(null)
const rejectRules = {
  reason: [
    { required: true, message: t('microSpecialtyReview.rejectReasonRequired') || '请输入驳回原因', trigger: 'blur' }
  ]
}
const rejectTarget = ref(null)
const detailVisible = ref(false)
const detailRow = ref(null)

const showDetail = (row) => { detailRow.value = row; detailVisible.value = true }

const statusKeyMap = { DRAFT: 'course.draft', PENDING_REVIEW: 'course.pendingReview', APPROVED: 'course.approved', RECRUITING: 'microSpecialtyReview.statusRecruiting', COMPLETED: 'microSpecialtyReview.statusCompleted', REJECTED: 'microSpecialtyManage.statusRejected', CANCELLED: 'microSpecialtyManage.statusCancelled', ARCHIVED: 'microSpecialtyReview.statusArchived' }
const statusTypeMap = { DRAFT: 'info', PENDING_REVIEW: 'warning', APPROVED: 'success', RECRUITING: 'success', COMPLETED: 'info', REJECTED: 'danger', CANCELLED: 'danger', ARCHIVED: 'info' }
const error = ref(false)

const fetchData = async () => {
  if (!hasAccess) return
  loading.value = true
  error.value = false
  try {
    const params = { page: page.value - 1, size: size.value }
    if (activeTab.value === 'PENDING') params.status = 'PENDING_REVIEW'
    const { data } = await getMicroSpecialtyList(params)
    items.value = data.items || data || []
    total.value = data.totalElements || 0
  } catch {
    error.value = true
    ElMessage.error(t('microSpecialtyManage.loadFailed'))
  }
  finally { loading.value = false }
}
const statusLabel = (s) => (statusKeyMap[s] ? t(statusKeyMap[s]) : s)
const statusType = (s) => statusTypeMap[s] || 'info'

const handleApprove = async (row) => {
  try { await ElMessageBox.confirm(t('microSpecialtyReview.confirmApproveMsg', { title: row.title }), t('microSpecialtyReview.confirmApproveTitle'), { type: 'info', confirmButtonText: t('microSpecialtyReview.approve'), cancelButtonText: t('common.cancel') }) }
  catch { return }
  actingId.value = row.id
  try { await approveMicroSpecialty(row.id); ElMessage.success(t('course.approved')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
  finally { actingId.value = null }
}

const handleReject = async (row) => {
  try { await ElMessageBox.confirm(t('microSpecialtyReview.confirmRejectMsg', { title: row.title }), t('microSpecialtyReview.confirmReject'), { type: 'warning', confirmButtonText: t('microSpecialtyReview.reject'), cancelButtonText: t('common.cancel') }) }
  catch { return }
  rejectTarget.value = row; rejectForm.reason = ''; rejectFormRef.value?.clearValidate(); rejectVisible.value = true
}
const confirmReject = async () => {
  if (!rejectFormRef.value) return
  try {
    await rejectFormRef.value.validate()
  } catch { return }
  actingId.value = rejectTarget.value.id
  try { await rejectMicroSpecialty(rejectTarget.value.id, { reason: rejectForm.reason }); ElMessage.success(t('microSpecialtyManage.statusRejected')); rejectVisible.value = false; fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
  finally { actingId.value = null }
}

const handleCancel = async (row) => {
  let reason
  try {
    const res = await ElMessageBox.prompt(t('microSpecialtyManage.cancelReasonPrompt'), t('microSpecialtyManage.cancelTitle'), {
      confirmButtonText: t('microSpecialtyManage.confirmCancelBtn'), cancelButtonText: t('common.cancel'),
      inputType: 'textarea', inputPlaceholder: t('microSpecialtyManage.cancelReasonPrompt'),
      inputValidator: v => v?.trim()?.length >= 1 || t('microSpecialtyManage.cancelReasonRequired')
    })
    reason = res.value
  } catch { return }
  try { await cancelMicroSpecialty(row.id, reason); ElMessage.success(t('microSpecialtyManage.cancelSuccess')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
}

const handleReopen = async (row) => {
  try {
    await ElMessageBox.confirm(t('microSpecialtyReview.reopenConfirmMsg'), t('microSpecialtyManage.reopen'), {
      confirmButtonText: t('app.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
    await reopenMicroSpecialty(row.id)
    ElMessage.success(t('microSpecialtyManage.reopenSuccess'))
    fetchData()
  } catch (e) {
    // P1-C 修复: 原 catch 仅 console.debug 静默吞掉 API 失败，
    // 用户点击"重新开启"失败时无任何反馈
    if (e === 'cancel') return
    ElMessage.error(e?.response?.data?.message || t('course.operationFailed'))
  }
}

const handleArchive = async (row) => {
  try { await ElMessageBox.confirm(t('microSpecialtyReview.confirmArchiveMsg', { title: row.title }), t('microSpecialtyReview.confirmArchiveTitle'), { type: 'info', confirmButtonText: t('course.archive'), cancelButtonText: t('common.cancel') }) }
  catch { return }
  try { await archiveMicroSpecialty(row.id); ElMessage.success(t('microSpecialtyReview.statusArchived')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
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
