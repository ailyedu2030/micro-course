<!--
  申报审核（教务处端）
  路由: /academic/micro-specialties/proposals
-->
<template>
  <div class="ms-proposal-review">
    <el-page-header @back="$router.back()" :content="$t('microSpecialtyProposalReview.pageTitle')" class="mg-bottom-16" />

    <el-tabs v-model="activeTab" @tab-change="() => { page = 1; fetchData() }">
      <el-tab-pane :label="$t('microSpecialtyProposalReview.tabPending')" name="PENDING" />
      <el-tab-pane :label="$t('app.all')" name="ALL" />
    </el-tabs>

    <el-card shadow="never">
      <el-alert v-if="error" :title="$t('microSpecialtyManage.loadFailed')" type="error" show-icon :closable="false" class="mg-bottom-12">
        <template #default><el-button size="small" @click="fetchData">{{ $t('common.retry') }}</el-button></template>
      </el-alert>
      <!-- P2-11: 批量审批操作栏（仅待审批状态可选） -->
      <div v-if="activeTab === 'PENDING'" class="batch-bar">
        <el-button size="small" type="success" :disabled="!selectedIds.length" :loading="batchActing" @click="handleBatchApprove">
          {{ $t('microSpecialtyProposalReview.batchApprove', { count: selectedIds.length }) }}
        </el-button>
        <el-button size="small" type="danger" :disabled="!selectedIds.length" :loading="batchActing" @click="handleBatchReject">
          {{ $t('microSpecialtyProposalReview.batchReject', { count: selectedIds.length }) }}
        </el-button>
        <span v-if="selectedIds.length" class="batch-hint">{{ $t('microSpecialtyProposalReview.batchHint', { count: selectedIds.length }) }}</span>
      </div>
      <el-table v-loading="loading" :data="items" stripe border @selection-change="handleSelectionChange">
        <template #empty>
          <el-empty :description="$t('microSpecialtyProposalReview.emptyPending')">
            <el-button type="primary" @click="$router.push('/academic/micro-specialties/proposals?tab=ALL')">{{ $t('microSpecialtyProposalReview.viewAll') }}</el-button>
          </el-empty>
        </template>
        <el-table-column type="selection" width="50" :selectable="row => row.status === 'PENDING_REVIEW'" />
        <el-table-column prop="title" :label="$t('course.tableTitle')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="collegeName" :label="$t('microSpecialtyProposalReview.college')" width="120" />
        <el-table-column prop="applicantName" :label="$t('microSpecialtyProposalReview.applicant')" width="100" />
        <el-table-column prop="semester" :label="$t('microSpecialtyProposalReview.suggestedSemester')" width="120" />
        <el-table-column :label="$t('app.status')" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="$t('microSpecialtyProposalReview.submittedAt')" width="130" align="center" :formatter="$formatDateTime">
          <template #default="{ row }">{{ $formatDate(row.createdAt) || '-' }}</template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="320" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING_REVIEW'">
              <el-button size="small" @click="showDetail(row)">{{ $t('course.view') }}</el-button>
              <el-button size="small" @click="goPreview(row)">{{ $t('microSpecialtyProposalReview.preview') }}</el-button>
              <el-button size="small" type="success" :loading="actingId === row.id" @click="handleApprove(row)">{{ $t('microSpecialtyProposalReview.approve') }}</el-button>
              <el-button size="small" type="danger" :loading="actingId === row.id" @click="handleReject(row)">{{ $t('microSpecialtyProposalReview.reject') }}</el-button>
            </template>
            <template v-else>
              <el-button size="small" @click="goPreview(row)">{{ $t('microSpecialtyProposalReview.preview') }}</el-button>
            </template>
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
    <el-dialog v-model="rejectVisible" :title="$t('microSpecialtyProposalReview.rejectReasonTitle')" width="480px">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" @submit.prevent>
        <el-form-item prop="reason">
          <el-input v-model="rejectForm.reason" type="textarea" :rows="3" :placeholder="$t('microSpecialtyProposalReview.rejectReasonPlaceholder')" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="danger" :loading="actingId !== null" @click="confirmReject">{{ $t('microSpecialtyProposalReview.confirmReject') }}</el-button>
      </template>
    </el-dialog>
    <!-- 查看详情 Dialog -->
    <el-dialog v-model="detailVisible" :title="$t('microSpecialtyProposalReview.detailTitle')" width="560px">
      <div class="detail-grid" v-if="detailRow">
        <div class="detail-item"><label>{{ $t('course.tableTitle') }}</label><span>{{ detailRow.title }}</span></div>
        <div class="detail-item"><label>{{ $t('microSpecialtyProposalReview.college') }}</label><span>{{ detailRow.collegeName || '-' }}</span></div>
        <div class="detail-item"><label>{{ $t('microSpecialtyProposalReview.applicant') }}</label><span>{{ detailRow.applicantName || '-' }}</span></div>
        <div class="detail-item"><label>{{ $t('microSpecialtyProposalReview.suggestedSemester') }}</label><span>{{ detailRow.semester || '-' }}</span></div>
        <div class="detail-item"><label>{{ $t('microSpecialtyProposalReview.maxStudents') }}</label><span>{{ detailRow.maxStudents || '-' }}</span></div>
        <div class="detail-item"><label>{{ $t('app.status') }}</label><span><el-tag :type="statusType(detailRow.status)" size="small">{{ statusLabel(detailRow.status) }}</el-tag></span></div>
        <div class="detail-item full-width"><label>{{ $t('microSpecialtyManage.description') }}</label><span v-html="sanitizeHtml(detailRow.description || '-')" class="detail-html"></span></div>
        <div class="detail-item full-width"><label>{{ $t('microSpecialtyManage.trainingObjective') }}</label><span v-html="sanitizeHtml(detailRow.trainingObjective || '-')" class="detail-html"></span></div>
        <div class="detail-item full-width"><label>{{ $t('microSpecialtyManage.admissionRequirement') }}</label><span>{{ detailRow.prerequisites || '-' }}</span></div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">{{ $t('common.close') }}</el-button>
        <el-button type="primary" @click="goPreview(detailRow); detailVisible = false">{{ $t('microSpecialtyProposalReview.previewProposal') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue' // P2: 移除 nextTick 死导入
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAllProposals, approveProposal, rejectProposal, batchApproveProposals, batchRejectProposals } from '@/api/microSpecialty'
import { useUserStore } from '@/store/user'
import { sanitizeHtml } from '@/utils/xss'

const activeTab = ref('PENDING')
const loading = ref(false)
const actingId = ref(null)
const router = useRouter()
const route = useRoute()
const { t } = useI18n()
// 直接读 role，不通过函数（避免 Vite tree-shake 把 userStore 去掉了）
const userStore = useUserStore()
const hasAccess = ['ACADEMIC', 'ADMIN'].includes(userStore.role)
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
const error = ref(false)
const detailVisible = ref(false)
const detailRow = ref(null)
const selectedIds = ref([])
const batchActing = ref(false)

const handleSelectionChange = (rows) => {
  selectedIds.value = rows.map(r => r.id)
}

const handleBatchApprove = async () => {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(t('microSpecialtyProposalReview.confirmBatchApprove', { count: selectedIds.value.length }), t('microSpecialtyProposalReview.batchApproveTitle'), {
      confirmButtonText: t('microSpecialtyProposalReview.batchApproveTitle'),
      cancelButtonText: t('common.cancel'),
      type: 'info'
    })
  } catch { return }
  batchActing.value = true
  try {
    const { data } = await batchApproveProposals(selectedIds.value)
    ElMessage.success(t('microSpecialtyProposalReview.batchApproveDone', { success: data.successCount, fail: data.failCount }))
    selectedIds.value = []
    fetchData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('microSpecialtyProposalReview.batchApproveFailed'))
  } finally {
    batchActing.value = false
  }
}

const handleBatchReject = async () => {
  if (!selectedIds.value.length) return
  let reason
  try {
    const res = await ElMessageBox.prompt(t('microSpecialtyProposalReview.batchRejectPrompt'), t('microSpecialtyProposalReview.batchRejectTitle'), {
      confirmButtonText: t('microSpecialtyProposalReview.batchRejectTitle'),
      cancelButtonText: t('common.cancel'),
      inputType: 'textarea',
      inputValidator: v => (v && v.trim().length >= 10) ? true : t('microSpecialtyProposalReview.rejectReasonMin')
    })
    reason = res.value.trim()
  } catch { return }
  batchActing.value = true
  try {
    const { data } = await batchRejectProposals(selectedIds.value, reason)
    ElMessage.success(t('microSpecialtyProposalReview.batchRejectDone', { success: data.successCount, fail: data.failCount }))
    selectedIds.value = []
    fetchData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('microSpecialtyProposalReview.batchRejectFailed'))
  } finally {
    batchActing.value = false
  }
}

const showDetail = (row) => { detailRow.value = row; detailVisible.value = true }

const statusKeyMap = { PENDING_REVIEW: 'microSpecialtyProposalReview.statusPendingReview', APPROVED: 'course.approved', REJECTED: 'microSpecialtyManage.statusRejected', WITHDRAWN: 'microSpecialtyProposalReview.statusWithdrawn' }
const statusTypeMap = { PENDING_REVIEW: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info' }
const statusLabel = (s) => (statusKeyMap[s] ? t(statusKeyMap[s]) : s)
const statusType = (s) => statusTypeMap[s] || 'info'

const fetchData = async () => {
  if (!hasAccess) return  // 无权限,直接静默返回 (守卫会重定向)
  loading.value = true
  error.value = false
  try {
    const params = { page: page.value - 1, size: size.value }
    if (activeTab.value === 'PENDING') params.status = 'PENDING_REVIEW'
    const { data } = await getAllProposals(params)
    items.value = data.items || data || []
    total.value = data.totalElements || 0
  } catch (e) {
    // 403 预期行为 - 路由守卫若未及时重定向,后端会拦截非ACADEMIC角色
    // 静默忽略,不显示错误也不报console
    if (e?.response?.status !== 403) {
      error.value = true
      ElMessage.error(t('microSpecialtyManage.loadFailed'))
    }
  }
  finally { loading.value = false }
}

const handleApprove = async (row) => {
  if (!hasAccess) return
  try { await ElMessageBox.confirm(t('microSpecialtyProposalReview.confirmApproveMsg', { title: row.title }), t('microSpecialtyProposalReview.confirmApproveTitle'), { type: 'info', confirmButtonText: t('microSpecialtyProposalReview.approve'), cancelButtonText: t('common.cancel') }) }
  catch { return }
  actingId.value = row.id
  try { await approveProposal(row.id); ElMessage.success(t('microSpecialtyProposalReview.approved')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
  finally { actingId.value = null }
}

const handleReject = async (row) => {
  if (!hasAccess) return
  try { await ElMessageBox.confirm(t('microSpecialtyProposalReview.confirmRejectMsg', { title: row.title }), t('microSpecialtyProposalReview.confirmReject'), { type: 'warning', confirmButtonText: t('microSpecialtyProposalReview.reject'), cancelButtonText: t('common.cancel') }) }
  catch { return }
  rejectTarget.value = row; rejectForm.reason = ''; rejectFormRef.value?.clearValidate(); rejectVisible.value = true
}
const confirmReject = async () => {
  if (!hasAccess) return
  if (!rejectFormRef.value) return
  try {
    await rejectFormRef.value.validate()
  } catch { return }
  actingId.value = rejectTarget.value.id
  try { await rejectProposal(rejectTarget.value.id, { reason: rejectForm.reason }); ElMessage.success(t('microSpecialtyManage.statusRejected')); rejectVisible.value = false; fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
  finally { actingId.value = null }
}

const goPreview = (row) => {
  router.push(`/teacher/micro-specialties/storage-preview/${row.id}`)
}

// P2: 读 route.query.tab 让查看全部按钮(?tab=ALL)真正切到 ALL tab
onMounted(() => {
  const tab = route.query.tab
  if (tab === "ALL") activeTab.value = "ALL"
  fetchData()
})

</script>

<style scoped>
.ms-proposal-review { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.batch-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.batch-hint { font-size: 12px; color: var(--el-text-color-secondary); }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.mg-bottom-12 { margin-bottom: var(--space-3); }
.mg-top-12 { margin-top: var(--space-3); }
.pagination { display: flex; justify-content: flex-end; }
.no-action { color: var(--el-text-color-placeholder); }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 24px; }
.detail-item { display: flex; flex-direction: column; gap: 4px; }
.detail-item.full-width { grid-column: 1 / -1; }
.detail-item label { font-size: 13px; color: #909399; }
.detail-item span { font-size: 14px; color: #303133; word-break: break-word; }
.detail-html { line-height: 1.6; }
.detail-html :deep(p) { margin: 4px 0; }
</style>
