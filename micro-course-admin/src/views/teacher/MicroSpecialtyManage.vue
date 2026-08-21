<!--
  微专业管理（教师端）
  路由: /teacher/micro-specialties/:id/manage
-->
<template>
  <div class="ms-manage" v-loading="loading">
    <!-- 错误/空 -->
    <el-result v-if="error" icon="error" :title="$t('microSpecialtyManage.loadFailed')" :sub-title="$t('microSpecialtyManage.loadFailedSubtitle')">
      <template #extra><el-button type="primary" @click="fetchDetail">{{ $t('common.retry') }}</el-button></template>
    </el-result>
    <el-empty v-else-if="!loading && !detail" :description="$t('microSpecialtyManage.notFound')" />

    <template v-if="detail">
      <!-- 页头 -->
      <div class="page-header">
        <div class="header-left">
          <el-page-header @back="$router.back()" :content="detail?.title || $t('microSpecialtyManage.title')" />
          <el-tag :type="statusType" size="small" class="status-tag">{{ statusLabel }}</el-tag>
        </div>
        <div class="header-actions">
          <el-button v-if="showSubmit" type="success" :loading="submitting" :disabled="submitting || actioning" @click="handleSubmit">{{ $t('course.submitForReview') }}</el-button>
          <el-button v-if="showOpen" type="warning" :loading="actioning" :disabled="actioning" @click="handleOpen">{{ $t('microSpecialtyManage.open') }}</el-button>
          <el-button v-if="showClose" type="danger" :loading="actioning" :disabled="actioning" @click="handleClose">{{ $t('microSpecialtyManage.close') }}</el-button>
          <el-button v-if="detail.status === 'APPROVED' || detail.status === 'RECRUITING'" @click="showFeaturedDialog">{{ $t('microSpecialtyManage.applyFeatured') }}</el-button>
          <el-button v-if="detail.featuredStatus === 'APPROVED'" type="warning" :loading="unfeaturing" :disabled="unfeaturing" @click="handleUnsetFeatured">{{ $t('microSpecialtyManage.unsetFeatured') }}</el-button>
          <el-button v-if="status === 'COMPLETED' || status === 'CANCELLED' || status === 'ARCHIVED'" type="danger" :loading="actioning" :disabled="actioning" @click="handleCancel">{{ $t('microSpecialtyManage.forceCancel') }}</el-button>
          <el-button v-if="(status === 'COMPLETED' || status === 'CANCELLED') && (userStore.role === 'ADMIN' || userStore.role === 'ACADEMIC')" type="primary" :loading="actioning" :disabled="actioning" @click="handleReopen">{{ $t('microSpecialtyManage.reopen') }}</el-button>
          <el-button v-if="status === 'COMPLETED'" type="info" :loading="actioning" :disabled="actioning" @click="handleArchive">{{ $t('course.archive') }}</el-button>
        </div>
      </div>

      <!-- 统计卡片 -->
      <el-row :gutter="16" class="stats-row">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card"><el-statistic :title="$t('microSpecialtyManage.statEnrollments')" :value="detail.stats?.totalEnrollments || 0" /></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card"><el-statistic :title="$t('microSpecialtyManage.statCourses')" :value="detail.stats?.courseCount || 0" /></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card"><el-statistic :title="$t('microSpecialtyManage.statCompleted')" :value="detail.stats?.completedCount || 0" /></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card"><el-statistic :title="$t('microSpecialtyManage.statPending')" :value="detail.stats?.pendingEnrollmentCount || 0" /></el-card>
        </el-col>
      </el-row>

      <!-- 工具栏 -->
      <div class="toolbar">
        <el-button type="primary" @click="$router.push(`/teacher/micro-specialties/${detail.id}/courses`)">{{ $t('microSpecialtyManage.courseArrangement') }}</el-button>
        <el-button @click="$router.push(`/teacher/micro-specialties/${detail.id}/team`)">{{ $t('microSpecialtyManage.teamManagement') }}</el-button>
      </div>

      <!-- 基本信息 -->
      <el-card shadow="never" class="section-card">
        <template #header><span class="card-title">{{ $t('course.basicInfo') }}</span></template>
        <el-form ref="formRef" :model="form" :rules="canEdit ? rules : {}" label-width="100px" class="info-form">
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item :label="$t('course.tableTitle')" prop="title"><el-input v-model="form.title" :disabled="!canEdit" /></el-form-item>
              <el-form-item :label="$t('microSpecialtyManage.subtitle')"><el-input v-model="form.subtitle" :disabled="!canEdit" /></el-form-item>
              <el-form-item :label="$t('microSpecialtyManage.collegeName')"><el-input :model-value="form.collegeName" disabled /></el-form-item>
              <el-form-item :label="$t('course.semester')"><el-input v-model="form.semester" :disabled="!canEdit" /></el-form-item>
              <el-form-item :label="$t('microSpecialtyManage.coverUrl')"><el-input v-model="form.coverUrl" :disabled="!canEdit" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('microSpecialtyManage.description')">
                <div class="quill-wrapper"><QuillEditor v-model:content="form.description" content-type="html" toolbar="essential" :placeholder="$t('microSpecialtyManage.descriptionPlaceholder')" :style="{ minHeight: '120px' }" :readonly="!canEdit" /></div>
              </el-form-item>
              <el-form-item :label="$t('microSpecialtyManage.trainingObjective')">
                <div class="quill-wrapper"><QuillEditor v-model:content="form.trainingObjective" content-type="html" toolbar="essential" :placeholder="$t('microSpecialtyManage.trainingObjectivePlaceholder')" :style="{ minHeight: '120px' }" :readonly="!canEdit" /></div>
              </el-form-item>
              <el-form-item :label="$t('microSpecialtyManage.admissionRequirement')"><el-input v-model="form.admissionRequirement" type="textarea" :rows="2" :disabled="!canEdit" /></el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <div class="form-actions"><el-button type="primary" :loading="saving" :disabled="saving || !canEdit" @click="handleSave">{{ $t('app.save') }}</el-button></div>
      </el-card>

      <!-- 选课列表 -->
      <el-card shadow="never" class="section-card">
        <template #header><span class="card-title">{{ $t('microSpecialtyManage.enrollmentList') }}</span></template>
        <el-table :data="enrollments" v-loading="enrollLoading" stripe border>
          <template #empty><el-empty :description="$t('microSpecialtyManage.noEnrollments')" /></template>
          <el-table-column prop="userName" :label="$t('course.student')" width="120" />
          <el-table-column prop="className" :label="$t('userSearch.classLabel')" width="120" />
          <el-table-column :label="$t('app.status')" width="100">
            <template #default="{ row }"><el-tag :type="enrollTagType(row.status)" size="small">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="appliedAt" :label="$t('microSpecialtyManage.appliedAt')" width="160" />
          <el-table-column prop="progress" :label="$t('microSpecialtyManage.progress')" width="100">
            <template #default="{ row }">{{ row.progress ?? 0 }}%</template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 进度概览 -->
      <el-card shadow="never" class="section-card" v-loading="progressLoading">
        <template #header><span class="card-title">{{ $t('microSpecialtyManage.progressOverview') }}</span></template>
        <el-empty v-if="!progressData" :description="$t('common.noData')" />
        <el-row v-else :gutter="16">
          <el-col :span="8"><el-statistic :title="$t('microSpecialtyManage.progressTotal')" :value="progressData.totalEnrollments || 0" /></el-col>
          <el-col :span="8"><el-statistic :title="$t('course.inProgress')" :value="progressData.inProgress || 0" /></el-col>
          <el-col :span="8"><el-statistic :title="$t('course.completed')" :value="progressData.completed || 0" /></el-col>
        </el-row>
      </el-card>
    </template>

    <!-- 申请置顶 Dialog -->
    <el-dialog v-model="featuredVisible" :title="$t('microSpecialtyManage.applyFeatured')" width="480px">
      <el-form :model="featuredForm" label-width="80px">
        <el-form-item :label="$t('microSpecialtyManage.featuredReason')"><el-input v-model="featuredForm.reason" type="textarea" :rows="3" :placeholder="$t('microSpecialtyManage.featuredReasonPlaceholder')" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="featuredVisible = false">{{ $t('app.cancel') }}</el-button>
        <el-button type="primary" :loading="featuring" :disabled="featuring" @click="handleFeatured">{{ $t('microSpecialtyManage.submitFeatured') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getMicroSpecialtyDetail, updateMicroSpecialty, submitMicroSpecialty, openMicroSpecialty, closeMicroSpecialty, cancelMicroSpecialty, reopenMicroSpecialty, archiveMicroSpecialty, applyFeatured, unsetFeatured, getStats, getEnrollmentList } from '@/api/microSpecialty'
import { getEnrollments } from '@/api/enrollment'
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()
const msId = computed(() => route.params.id)
const loading = ref(true)

// P1-C: 修复 QuillEditor 工具栏按钮缺少 aria-label
const QUILL_LABELS = {
  'ql-bold': t('microSpecialtyManage.quillBold'), 'ql-italic': t('microSpecialtyManage.quillItalic'), 'ql-underline': t('microSpecialtyManage.quillUnderline'),
  'ql-strike': t('microSpecialtyManage.quillStrike'), 'ql-link': t('microSpecialtyManage.quillLink'), 'ql-clean': t('microSpecialtyManage.quillClean'),
  'ql-blockquote': t('microSpecialtyManage.quillBlockquote'), 'ql-code-block': t('microSpecialtyManage.quillCodeBlock'),
  'ql-image': t('microSpecialtyManage.quillImage'), 'ql-list': t('microSpecialtyManage.quillListOrdered'), 'ql-bullet': t('microSpecialtyManage.quillListUnordered'),
  'ql-header': t('microSpecialtyManage.quillHeader'), 'ql-indent': t('microSpecialtyManage.quillIndentIncrease'), 'ql-outdent': t('microSpecialtyManage.quillIndentDecrease'),
  'ql-align': t('microSpecialtyManage.quillAlign'), 'ql-direction': t('microSpecialtyManage.quillDirection'), 'ql-size': t('microSpecialtyManage.quillSize'),
  'ql-color': t('microSpecialtyManage.quillColor'), 'ql-background': t('microSpecialtyManage.quillBackground'), 'ql-font': t('microSpecialtyManage.quillFont'),
  'ql-script': t('microSpecialtyManage.quillScript'), 'ql-formula': t('microSpecialtyManage.quillFormula'), 'ql-video': t('microSpecialtyManage.quillVideo')
}
function fixQuillAria() {
  setTimeout(() => {
    document.querySelectorAll('.ql-toolbar button').forEach(btn => {
      if (btn.hasAttribute('aria-label')) return
      const cls = Array.from(btn.classList).find(c => c.startsWith('ql-'))
      if (cls && QUILL_LABELS[cls]) btn.setAttribute('aria-label', QUILL_LABELS[cls])
      else if (cls) btn.setAttribute('aria-label', cls.replace('ql-', '').replace('-', ' '))
    })
    // 二次检查：Quill 的 header 下拉中的 button 也会被创建
    document.querySelectorAll('.ql-picker').forEach(picker => {
      if (!picker.hasAttribute('aria-label') && picker.classList.contains('ql-header')) {
        // ql-header picker trigger 本身是个 button
        const labelBtn = picker.querySelector('.ql-picker-label')
        if (labelBtn && !labelBtn.getAttribute('aria-label')) {
          labelBtn.setAttribute('aria-label', t('microSpecialtyManage.quillHeader'))
        }
      }
    })
  }, 500)
}
const error = ref(false)
const saving = ref(false)
const submitting = ref(false)
const actioning = ref(false)
const detail = ref(null)
const formRef = ref(null)
const form = ref({})
const rules = { title: [{ required: true, message: t('microSpecialtyManage.titleRequired'), trigger: 'blur' }] }

const enrollments = ref([])
const enrollLoading = ref(false)
const progressData = ref(null)
const progressLoading = ref(false)
const featuredVisible = ref(false)
const featuring = ref(false)
const unfeaturing = ref(false)
const featuredForm = ref({ reason: '' })

const enrollTagType = (s) => ({ PENDING: 'warning', APPROVED: '', IN_PROGRESS: 'primary', COMPLETED: 'success', CERTIFIED: 'success', FAILED: 'danger', DROPPED: 'info' })[s] || 'info'

const status = computed(() => detail.value?.status)
const showSubmit = computed(() => ['DRAFT', 'REJECTED'].includes(status.value))
const showOpen = computed(() => status.value === 'APPROVED' && (detail.value?.courses?.length || 0) >= 1 && (detail.value?.teachers?.length || 0) >= 2)
const showClose = computed(() => status.value === 'RECRUITING')
const canEdit = computed(() => !['COMPLETED', 'CANCELLED', 'ARCHIVED'].includes(status.value) && !route.query._readonly)

const statusMap = { DRAFT: t('course.draft'), PENDING_REVIEW: t('course.pendingReview'), APPROVED: t('course.approved'), REJECTED: t('microSpecialtyManage.statusRejected'), RECRUITING: t('courseSquare.msRecruiting'), COMPLETED: t('courseSquare.msCompleted'), CANCELLED: t('microSpecialtyManage.statusCancelled'), ARCHIVED: t('course.archived') }
const statusTypeMap = { DRAFT: 'info', PENDING_REVIEW: 'warning', APPROVED: 'success', REJECTED: 'danger', RECRUITING: 'success', COMPLETED: 'info', CANCELLED: 'danger', ARCHIVED: 'info' }
const statusLabel = computed(() => statusMap[status.value] || status.value || '-')
const statusType = computed(() => statusTypeMap[status.value] || 'info')

const fetchDetail = async () => {
  error.value = false; loading.value = true
  try {
    const { data: d } = await getMicroSpecialtyDetail(msId.value)
    detail.value = d
    // 仅提取可编辑字段到form
    form.value = {
      title: d.title || '', subtitle: d.subtitle || '',
      description: d.description || '', trainingObjective: d.trainingObjective || '',
      admissionRequirement: d.admissionRequirement || '',
      semester: d.semester || '', coverUrl: d.coverUrl || ''
    }
    try { const { data: stats } = await getStats(msId.value); detail.value = { ...detail.value, stats } } catch { /* skip stats */ }
    fetchEnrollments(); fetchProgress()
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyManage.fetchDetailFailed')); error.value = true }
  finally { loading.value = false }
}

const fetchEnrollments = async () => {
  enrollLoading.value = true
  try { const { data } = await getEnrollmentList(msId.value); enrollments.value = data?.items || data || [] }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyManage.fetchEnrollmentsFailed')) }
  finally { enrollLoading.value = false }
}

const fetchProgress = async () => {
  progressLoading.value = true
  try { progressData.value = detail.value?.stats || {} }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyManage.fetchProgressFailed')) }
  finally { progressLoading.value = false }
}

const handleSave = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (saving.value) return
  if (!formRef.value) return
  saving.value = true
  try {
    const valid = await formRef.value.validate()
    if (!valid) { saving.value = false; return }
  } catch { saving.value = false; return }
  try { await updateMicroSpecialty(msId.value, form.value); ElMessage.success(t('app.saveSuccess')); fetchDetail() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyManage.saveFailed')) }
  finally { saving.value = false }
}

const handleSubmit = async () => {
  submitting.value = true
  try { await submitMicroSpecialty(msId.value); ElMessage.success(t('microSpecialtyManage.submitSuccess')); fetchDetail() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyManage.submitFailed')) }
  finally { submitting.value = false }
}

const handleOpen = async () => {
  actioning.value = true
  try { await openMicroSpecialty(msId.value); ElMessage.success(t('microSpecialtyManage.openSuccess')); fetchDetail() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
  finally { actioning.value = false }
}

const handleClose = async () => {
  actioning.value = true
  try { await closeMicroSpecialty(msId.value); ElMessage.success(t('microSpecialtyManage.closeSuccess')); fetchDetail() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
  finally { actioning.value = false }
}

const handleCancel = async () => {
  try {
    const { value } = await ElMessageBox.prompt(t('microSpecialtyManage.cancelReasonPrompt'), t('microSpecialtyManage.cancelTitle'), {
      confirmButtonText: t('microSpecialtyManage.confirmCancelBtn'), cancelButtonText: t('app.cancel'),
      inputType: 'textarea', inputPlaceholder: t('microSpecialtyManage.cancelReasonPlaceholder'),
      inputValidator: v => v?.trim()?.length >= 1 || t('microSpecialtyManage.cancelReasonRequired')
    })
    actioning.value = true
    await cancelMicroSpecialty(msId.value, value)
    ElMessage.success(t('microSpecialtyManage.cancelSuccess'))
    fetchDetail()
  } catch (e) { if (!['cancel', 'close'].includes(e)) ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
  finally { actioning.value = false }
}

const handleReopen = async () => {
  try { await ElMessageBox.confirm(t('microSpecialtyManage.reopenConfirmMsg'), t('app.confirm'), { type: 'warning' }) } catch { return }
  actioning.value = true
  try { await reopenMicroSpecialty(msId.value); ElMessage.success(t('microSpecialtyManage.reopenSuccess')); fetchDetail() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
  finally { actioning.value = false }
}

const handleArchive = async () => {
  try { await ElMessageBox.confirm(t('microSpecialtyManage.archiveConfirmMsg'), t('app.confirm'), { type: 'warning' }) } catch { return }
  actioning.value = true
  try { await archiveMicroSpecialty(msId.value); ElMessage.success(t('microSpecialtyManage.archiveSuccess')); fetchDetail() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
  finally { actioning.value = false }
}

const showFeaturedDialog = () => { featuredForm.value.reason = ''; featuredVisible.value = true }
const handleFeatured = async () => {
  featuring.value = true
  try { await applyFeatured(msId.value, { reason: featuredForm.value.reason }); ElMessage.success(t('microSpecialtyManage.featuredSubmitted')); featuredVisible.value = false }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyManage.featuredFailed')) }
  finally { featuring.value = false }
}
const handleUnsetFeatured = async () => {
  try { await ElMessageBox.confirm(t('microSpecialtyManage.unfeatureConfirmMsg'), t('app.confirm'), { type: 'warning' }) } catch { return }
  unfeaturing.value = true
  try { await unsetFeatured(msId.value); ElMessage.success(t('microSpecialtyManage.unfeatureSuccess')); fetchDetail() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
  finally { unfeaturing.value = false }
}

onMounted(async () => {
  await fetchDetail()
  fixQuillAria()
})
</script>

<style scoped>
.ms-manage { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: var(--space-4); flex-wrap: wrap; gap: var(--space-3); }
.header-left { display: flex; align-items: center; gap: var(--space-3); }
.status-tag { flex-shrink: 0; }
.header-actions { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.stats-row { margin-bottom: var(--space-4); }
.stat-card { text-align: center; }
.toolbar { display: flex; gap: var(--space-2); margin-bottom: var(--space-4); }
.section-card { margin-bottom: var(--space-4); }
.card-title { font-size: 16px; font-weight: 600; color: var(--el-text-color-primary); }
.info-form { max-width: 100%; }
.form-actions { display: flex; justify-content: flex-end; padding-top: var(--space-4); border-top: 1px solid var(--el-border-color-lighter); }
.quill-wrapper { width: 100%; border-radius: 4px; }
.quill-wrapper :deep(.ql-toolbar) { border-radius: 4px 4px 0 0; background: #fafafa; }
.quill-wrapper :deep(.ql-container) { border-radius: 0 0 4px 4px; font-size: 14px; }
</style>