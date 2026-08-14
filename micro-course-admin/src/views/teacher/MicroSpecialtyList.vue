<!--
  微专业列表（教师端）
  路由: /teacher/micro-specialties
-->
<template>
  <div class="ms-list-page">
    <el-page-header @back="$router.back()" :content="$t('course.microSpecialty')" class="mg-bottom-16" />

    <el-result
      v-if="error"
      icon="error"
      :title="$t('microSpecialtyManage.loadFailed')"
      :sub-title="$t('microSpecialtyManage.loadFailedSubtitle')"
    >
      <template #extra>
        <el-button type="primary" @click="fetchList(activeTab)">{{ $t('common.retry') }}</el-button>
      </template>
    </el-result>

    <el-tabs v-else v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane :label="$t('microSpecialtyList.tabLeading')" name="leading">
        <div v-loading="loading" class="card-grid">
          <el-empty v-if="!loading && list.length === 0" :description="$t('microSpecialtyList.emptyLeading')">
            <template #image><el-icon :size="64" style="color: var(--el-text-color-placeholder);"><Notebook /></el-icon></template>
            <p class="empty-guide">{{ $t('microSpecialtyList.emptyGuide') }}</p>
            <ol class="empty-steps">
              <li>{{ $t('microSpecialtyList.step1Prefix') }}<el-link type="primary" @click="$router.push('/teacher/micro-specialties/proposals')">{{ $t('microSpecialtyList.proposalLink') }}</el-link>{{ $t('microSpecialtyList.step1Suffix') }}</li>
              <li>{{ $t('microSpecialtyList.step2') }}</li>
              <li>{{ $t('microSpecialtyList.step3') }}</li>
              <li>{{ $t('microSpecialtyList.step4') }}</li>
            </ol>
            <el-button type="primary" class="mg-top-12" @click="$router.push('/teacher/micro-specialties/proposals')">{{ $t('microSpecialtyList.applyNow') }}</el-button>
          </el-empty>
            <div
              v-for="item in list" :key="item.id" class="ms-card"
              role="button" tabindex="0"
              :aria-label="$t('microSpecialtyList.cardAria', { title: item.title, status: statusLabel(item.status) })"
              @click="$router.push('/teacher/micro-specialties/' + item.id + '/courses')"
              @keydown.enter="$router.push('/teacher/micro-specialties/' + item.id + '/courses')"
              style="cursor:pointer">
              <el-image :src="item.coverUrl" fit="cover" class="card-cover" :alt="$t('microSpecialtyList.coverAlt', { title: item.title })" />
            <div class="card-body">
              <div class="card-header-row">
                <span class="card-title">{{ item.title }}</span>
                <el-tag :type="statusType(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
              </div>
              <div class="card-meta">
                 <span>{{ item.departmentName || '-' }}</span>
                 <span>{{ $t('microSpecialtyList.enrollmentCount', { count: item.totalEnrollments || 0 }) }}</span>
                 <span>{{ $t('microSpecialtyList.courseCount', { count: item.courseCount || 0 }) }}</span>
               </div>
               <div class="card-actions">
                 <el-button size="small" @click="$router.push(`/teacher/micro-specialties/${item.id}/manage`)">{{ $t('microSpecialtyList.manage') }}</el-button>
                 <el-button size="small" @click="$router.push(`/teacher/micro-specialties/${item.id}/courses`)">{{ $t('microSpecialtyList.arrangeCourses') }}</el-button>
                 <el-button size="small" @click="$router.push(`/teacher/micro-specialties/${item.id}/team`)">{{ $t('microSpecialtyList.team') }}</el-button>
               </div>
               <el-badge v-if="item.pendingEnrollmentCount" :value="item.pendingEnrollmentCount" class="pending-badge">
                 <el-button size="small" type="warning" @click="$router.push(`/teacher/micro-specialties/${item.id}/manage`)">{{ $t('microSpecialtyManage.statPending') }}</el-button>
               </el-badge>
             </div>
           </div>
         </div>
       </el-tab-pane>

       <el-tab-pane :label="$t('microSpecialtyList.tabParticipating')" name="participating">
         <div v-loading="loading" class="card-grid">
           <el-empty v-if="!loading && list.length === 0" :description="$t('microSpecialtyList.emptyParticipating')" />
            <div
              v-for="item in list" :key="item.id" class="ms-card"
              role="button" tabindex="0"
              :aria-label="$t('microSpecialtyList.cardAria', { title: item.title, status: statusLabel(item.status) })"
              @click="$router.push('/teacher/micro-specialties/' + item.id + '/courses')"
              @keydown.enter="$router.push('/teacher/micro-specialties/' + item.id + '/courses')"
              style="cursor:pointer">
              <el-image :src="item.coverUrl" fit="cover" class="card-cover" :alt="$t('microSpecialtyList.coverAlt', { title: item.title })" />
              <div class="card-body">
                <div class="card-header-row">
                  <span class="card-title">{{ item.title }}</span>
                  <el-tag :type="statusType(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
                </div>
                <div class="card-meta">
                  <span>{{ item.departmentName || '-' }}</span>
                  <span>{{ $t('microSpecialtyList.enrollmentCount', { count: item.totalEnrollments || 0 }) }}</span>
                  <span>{{ $t('microSpecialtyList.courseCount', { count: item.courseCount || 0 }) }}</span>
                </div>
                                <div class="card-actions">
                  <el-button size="small" @click="$router.push(`/teacher/micro-specialties/${item.id}/manage`)">{{ $t('course.viewDetail') }}</el-button>
                </div>
             </div>
           </div>
         </div>
       </el-tab-pane>

      <el-tab-pane name="invites">
        <template #label>
          <span>{{ $t('microSpecialtyList.tabInvites') }} <el-badge v-if="pendingInviteCount" :value="pendingInviteCount" class="tab-badge" /></span>
        </template>
        <div v-loading="inviteLoading" class="invite-section">
          <el-result
            v-if="inviteError"
            icon="error"
            :title="$t('microSpecialtyManage.loadFailed')"
            :sub-title="$t('microSpecialtyManage.loadFailedSubtitle')"
          >
            <template #extra>
              <el-button type="primary" @click="fetchInvites">{{ $t('common.retry') }}</el-button>
            </template>
          </el-result>
          <el-empty v-else-if="!inviteLoading && invites.length === 0" :description="$t('microSpecialtyList.emptyInvites')" />
          <div v-for="inv in invites" :key="inv.id" class="invite-card">
            <div class="invite-info">
              <span class="invite-ms">{{ inv.microSpecialtyTitle }}</span>
               <span class="invite-role">{{ $t(roleMap[inv.role] || inv.role || 'role.TEACHER') }}</span>
              <span class="invite-from">{{ $t('microSpecialtyList.inviteFrom', { name: inv.inviterName }) }}</span>
              <span class="invite-deadline" :class="{ 'expiring': inv.expiring }">
                {{ inv.deadlineText }}
              </span>
            </div>
            <div class="invite-actions">
              <el-button size="small" type="primary" @click="handleAccept(inv)">{{ $t('microSpecialtyList.accept') }}</el-button>
              <el-button size="small" @click="handleDecline(inv)">{{ $t('microSpecialtyList.decline') }}</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <div class="action-bar mg-top-16">
      <el-button type="primary" @click="$router.push('/teacher/micro-specialties/proposals')">{{ $t('microSpecialtyList.submitProposal') }}</el-button>
      <el-button v-if="userStore.role === 'ACADEMIC'" @click="showCreateDialog">{{ $t('microSpecialtyList.createTitle') }}</el-button>
    </div>

    <!-- 创建微专业 Dialog -->
    <el-dialog v-model="createVisible" :title="$t('microSpecialtyList.createTitle')" width="560px" @closed="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item :label="$t('course.tableTitle')" prop="title">
          <el-input v-model="createForm.title" :placeholder="$t('microSpecialtyList.titlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('microSpecialtyManage.subtitle')">
          <el-input v-model="createForm.subtitle" :placeholder="$t('microSpecialtyList.subtitlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('microSpecialtyList.description')">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item :label="$t('microSpecialtyManage.collegeName')" prop="offerDepartmentId">
          <el-select v-model="createForm.offerDepartmentId" :placeholder="$t('microSpecialtyList.selectCollegePlaceholder')" class="full-width">
            <el-option v-for="c in colleges" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('course.semester')">
          <el-input v-model="createForm.semester" :placeholder="$t('microSpecialtyList.semesterPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ $t('app.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" :disabled="creating" @click="handleCreate">{{ $t('microSpecialtyList.create') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getMicroSpecialtyList, createMicroSpecialty } from '@/api/microSpecialty'
import { getPendingInvites, acceptInvite, declineInvite } from '@/api/microSpecialty'
import { getDepartments } from '@/api/department'
import { Notebook } from '@element-plus/icons-vue'

const { t } = useI18n()
const userStore = useUserStore()
const activeTab = ref('leading')
const loading = ref(false)
const inviteLoading = ref(false)
const error = ref(false)
const inviteError = ref(false)
const list = ref([])
const invites = ref([])
const pendingInviteCount = ref(0)

const roleMap = { LEAD: 'microSpecialtyTeamEdit.roleLead', MEMBER: 'microSpecialtyTeamEdit.roleMember', ASSISTANT: 'microSpecialtyTeamEdit.roleAssistant' }

const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref(null)
const createForm = ref({ title: '', subtitle: '', description: '', offerDepartmentId: null, semester: '' })
const createRules = { title: [{ required: true, message: t('microSpecialtyManage.titleRequired'), trigger: 'blur' }], offerDepartmentId: [{ required: true, message: t('microSpecialtyList.selectCollegeRequired'), trigger: 'change' }] }
const colleges = ref([])

const statusMap = { DRAFT: t('course.draft'), PENDING_REVIEW: t('course.pendingReview'), APPROVED: t('course.approved'), RECRUITING: t('courseSquare.msRecruiting'), COMPLETED: t('microSpecialtyDetail.completed'), REJECTED: t('microSpecialtyManage.statusRejected'), CANCELLED: t('microSpecialtyManage.statusCancelled'), ARCHIVED: t('course.archived') }
const statusTypeMap = { DRAFT: 'info', PENDING_REVIEW: 'warning', APPROVED: 'success', RECRUITING: 'success', COMPLETED: 'info', REJECTED: 'danger', CANCELLED: 'danger', ARCHIVED: 'info' }
const statusLabel = (s) => statusMap[s] || s
const statusType = (s) => statusTypeMap[s] || 'info'

const fetchList = async (role) => {
  error.value = false
  loading.value = true
  try {
    const { data } = await getMicroSpecialtyList({ role, page: 0, size: 50 })
    list.value = data.items || data || []
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyList.fetchListFailed')); error.value = true }
  finally { loading.value = false }
}

const fetchInvites = async () => {
  inviteError.value = false
  inviteLoading.value = true
  try {
    const { data } = await getPendingInvites()
    const items = data.items || data || []
    const now = Date.now()
    invites.value = items.map(i => {
      const deadline = i.inviteExpiresAt ? new Date(i.inviteExpiresAt).getTime() : null
      const remaining = deadline ? Math.max(0, Math.ceil((deadline - now) / 86400000)) : null
      return {
        ...i,
        expiring: remaining !== null && remaining < 3,
        deadlineText: deadline ? (remaining > 0 ? t('microSpecialtyList.remainingDays', { count: remaining }) : t('microSpecialtyList.deadlineExpired')) : ''
      }
    })
    pendingInviteCount.value = invites.value.filter(i => i.inviteStatus === 'INVITED' || i.inviteStatus === 'PENDING_ACADEMIC').length
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyList.fetchInvitesFailed')); inviteError.value = true }
  finally { inviteLoading.value = false }
}

const handleTabChange = (name) => {
  if (name === 'invites') fetchInvites()
  else fetchList(name)
}

const handleAccept = async (inv) => {
  try { await acceptInvite(inv.id); ElMessage.success(t('microSpecialtyList.acceptSuccess')); fetchInvites() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
}

const handleDecline = async (inv) => {
  try { await ElMessageBox.confirm(t('microSpecialtyList.confirmDecline'), t('course.hintTitle'), { type: 'warning' }) }
  catch { return }
  try { await declineInvite(inv.id); ElMessage.success(t('microSpecialtyList.declineSuccess')); fetchInvites() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
}

const showCreateDialog = () => { createVisible.value = true }
const resetCreateForm = () => {
  createForm.value = { title: '', subtitle: '', description: '', offerDepartmentId: null, semester: '' }
  createFormRef.value?.clearValidate()
}
const handleCreate = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (creating.value) return
  if (!createFormRef.value) return
  creating.value = true
  try {
    const valid = await createFormRef.value.validate()
    if (!valid) { creating.value = false; return }
  } catch { creating.value = false; return }
  try { await createMicroSpecialty(createForm.value); ElMessage.success(t('course.createSuccess')); createVisible.value = false; fetchList(activeTab.value) }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.createFailed')) }
  finally { creating.value = false }
}

const fetchColleges = async () => {
  try {
    const { data } = await getDepartments({ size: 1000 })
    colleges.value = data.items || data || []
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyList.fetchCollegesFailed')) }
}

onMounted(() => { fetchList('leading'); fetchColleges() })
</script>

<style scoped>
.ms-list-page { padding: var(--space-4); max-width: 1440px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.mg-top-16 { margin-top: var(--space-4); }
.full-width { width: 100%; }
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: var(--space-4); min-height: 200px; }
@media (max-width: 400px) { .card-grid { grid-template-columns: 1fr; } }
.ms-card { border: 1px solid var(--el-border-color-lighter); border-radius: var(--el-border-radius-base); overflow: hidden; transition: box-shadow var(--el-transition-duration) var(--el-transition-function-ease-in-out-bezier); }
.ms-card:hover { box-shadow: var(--el-box-shadow-light); }
.card-cover { width: 100%; height: 160px; }
.card-body { padding: var(--space-3); }
.card-header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--space-2); }
.card-title { font-size: var(--el-font-size-base); font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 220px; }
.card-meta { display: flex; gap: var(--space-3); font-size: var(--el-font-size-extra-small); color: var(--el-text-color-secondary); margin-bottom: var(--space-3); }
.card-actions { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.pending-badge { margin-top: var(--space-2); }
.action-bar { display: flex; gap: var(--space-2); justify-content: flex-end; }
.empty-guide { margin: var(--space-4) 0 var(--space-2); font-weight: var(--weight-medium); color: var(--el-text-color-primary); }
.empty-steps { text-align: left; max-width: 420px; margin: 0 auto; line-height: 2.2; color: var(--el-text-color-secondary); font-size: var(--text-sm); }
.empty-steps li { list-style: decimal; margin-left: var(--space-5); }
.mg-top-12 { margin-top: var(--space-4); }
.tab-badge { margin-left: var(--space-1); }
.invite-section { min-height: 200px; }
.invite-card { display: flex; justify-content: space-between; align-items: center; padding: var(--space-3); border-bottom: 1px solid var(--el-border-color-lighter); }
.invite-info { display: flex; flex-direction: column; gap: 2px; }
.invite-ms { font-weight: 600; }
.invite-role { font-size: var(--el-font-size-extra-small); color: var(--el-text-color-secondary); }
.invite-from { font-size: var(--el-font-size-extra-small); color: var(--el-text-color-secondary); }
.invite-deadline { font-size: var(--el-font-size-extra-small); color: var(--el-text-color-secondary); }
.invite-deadline.expiring { color: var(--el-color-danger); font-weight: 600; }
.invite-actions { display: flex; gap: var(--space-2); }
</style>
