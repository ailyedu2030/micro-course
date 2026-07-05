<!--
  微专业列表（教师端）
  路由: /teacher/micro-specialties
-->
<template>
  <div class="ms-list-page">
    <el-page-header @back="$router.back()" content="微专业" class="mg-bottom-16" />

    <el-result
      v-if="error"
      icon="error"
      title="加载失败"
      sub-title="请稍后重试"
    >
      <template #extra>
        <el-button type="primary" @click="fetchList(activeTab)">重试</el-button>
      </template>
    </el-result>

    <el-tabs v-else v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="我负责的" name="leading">
        <div v-loading="loading" class="card-grid">
          <el-empty v-if="!loading && list.length === 0" description="暂无负责的微专业">
            <template #image><el-icon :size="64" style="color: var(--el-text-color-placeholder);"><Notebook /></el-icon></template>
            <p class="empty-guide">微专业课程管理流程：</p>
            <ol class="empty-steps">
              <li>在「<el-link type="primary" @click="$router.push('/teacher/micro-specialties/proposals')">微专业申报</el-link>」提交申报材料</li>
              <li>教务处审核通过后，微专业将出现在本列表</li>
              <li>点击卡片上的「编排课程」添加课程并指派授课教师</li>
              <li>点击「团队」邀请其他教师并分配归属课程</li>
            </ol>
            <el-button type="primary" class="mg-top-12" @click="$router.push('/teacher/micro-specialties/proposals')">立即申报微专业</el-button>
          </el-empty>
            <div v-for="item in list" :key="item.id" class="ms-card" @click="$router.push('/teacher/micro-specialties/' + item.id + '/courses')" style="cursor:pointer">
              <el-image :src="item.coverUrl" fit="cover" class="card-cover" />
            <div class="card-body">
              <div class="card-header-row">
                <span class="card-title">{{ item.title }}</span>
                <el-tag :type="statusType(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
              </div>
              <div class="card-meta">
                 <span>{{ item.departmentName || '-' }}</span>
                 <span>{{ item.totalEnrollments || 0 }} 人选修</span>
                 <span>{{ item.courseCount || 0 }} 门课</span>
               </div>
               <div class="card-actions">
                 <el-button size="small" @click="$router.push(`/teacher/micro-specialties/${item.id}/manage`)">管理</el-button>
                 <el-button size="small" @click="$router.push(`/teacher/micro-specialties/${item.id}/courses`)">编排课程</el-button>
                 <el-button size="small" @click="$router.push(`/teacher/micro-specialties/${item.id}/team`)">团队</el-button>
               </div>
               <el-badge v-if="item.pendingEnrollmentCount" :value="item.pendingEnrollmentCount" class="pending-badge">
                 <el-button size="small" type="warning" @click="$router.push(`/teacher/micro-specialties/${item.id}/manage`)">待审报名</el-button>
               </el-badge>
             </div>
           </div>
         </div>
       </el-tab-pane>

       <el-tab-pane label="我参与的" name="participating">
         <div v-loading="loading" class="card-grid">
           <el-empty v-if="!loading && list.length === 0" description="暂无参与的微专业" />
           <div v-for="item in list" :key="item.id" class="ms-card" @click="$router.push('/teacher/micro-specialties/' + item.id + '/courses')" style="cursor:pointer">
             <el-image :src="item.coverUrl" fit="cover" class="card-cover" />
             <div class="card-body">
               <div class="card-header-row">
                 <span class="card-title">{{ item.title }}</span>
                 <el-tag :type="statusType(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
               </div>
               <div class="card-meta">
                 <span>{{ item.departmentName || '-' }}</span>
                 <span>{{ item.totalEnrollments || 0 }} 人选修</span>
                 <span>{{ item.courseCount || 0 }} 门课</span>
               </div>
                               <div class="card-actions">
                  <el-button size="small" @click="$router.push(`/teacher/micro-specialties/${item.id}/manage`)">查看详情</el-button>
                </div>
             </div>
           </div>
         </div>
       </el-tab-pane>

      <el-tab-pane name="invites">
        <template #label>
          <span>待处理邀请 <el-badge v-if="pendingInviteCount" :value="pendingInviteCount" class="tab-badge" /></span>
        </template>
        <div v-loading="inviteLoading" class="invite-section">
          <el-result
            v-if="inviteError"
            icon="error"
            title="加载失败"
            sub-title="请稍后重试"
          >
            <template #extra>
              <el-button type="primary" @click="fetchInvites">重试</el-button>
            </template>
          </el-result>
          <el-empty v-else-if="!inviteLoading && invites.length === 0" description="暂无待处理邀请" />
          <div v-for="inv in invites" :key="inv.id" class="invite-card">
            <div class="invite-info">
              <span class="invite-ms">{{ inv.microSpecialtyTitle }}</span>
               <span class="invite-role">{{ roleMap[inv.role] || inv.role || '教师' }}</span>
              <span class="invite-from">来自 {{ inv.inviterName }}</span>
              <span class="invite-deadline" :class="{ 'expiring': inv.expiring }">
                {{ inv.deadlineText }}
              </span>
            </div>
            <div class="invite-actions">
              <el-button size="small" type="primary" @click="handleAccept(inv)">接受</el-button>
              <el-button size="small" @click="handleDecline(inv)">拒绝</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <div class="action-bar mg-top-16">
      <el-button type="primary" @click="$router.push('/teacher/micro-specialties/proposals')">提交申报</el-button>
      <el-button v-if="userStore.role === 'ACADEMIC'" @click="showCreateDialog">新增微专业</el-button>
    </div>

    <!-- 创建微专业 Dialog -->
    <el-dialog v-model="createVisible" title="新增微专业" width="560px" @closed="resetCreateForm">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="createForm.title" placeholder="微专业名称" />
        </el-form-item>
        <el-form-item label="副标题">
          <el-input v-model="createForm.subtitle" placeholder="副标题（选填）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="开课学院" prop="offerDepartmentId">
          <el-select v-model="createForm.offerDepartmentId" placeholder="选择学院" class="full-width">
            <el-option v-for="c in colleges" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学期">
          <el-input v-model="createForm.semester" placeholder="如 2025-2026-1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" :disabled="creating" @click="handleCreate">新增</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getMicroSpecialtyList, createMicroSpecialty } from '@/api/microSpecialty'
import { getPendingInvites, acceptInvite, declineInvite } from '@/api/microSpecialty'
import { getDepartments } from '@/api/department'
import { Notebook } from '@element-plus/icons-vue'

const userStore = useUserStore()
const activeTab = ref('leading')
const loading = ref(false)
const inviteLoading = ref(false)
const error = ref(false)
const inviteError = ref(false)
const list = ref([])
const invites = ref([])
const pendingInviteCount = ref(0)

const roleMap = { LEAD: '负责人', MEMBER: '团队成员', ASSISTANT: '助教' }

const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref(null)
const createForm = ref({ title: '', subtitle: '', description: '', offerDepartmentId: null, semester: '' })
const createRules = { title: [{ required: true, message: '请输入标题', trigger: 'blur' }], offerDepartmentId: [{ required: true, message: '请选择学院', trigger: 'change' }] }
const colleges = ref([])

const statusMap = { DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '已通过', RECRUITING: '招生中', COMPLETED: '已结业', REJECTED: '已驳回', CANCELLED: '已取消', ARCHIVED: '已归档' }
const statusTypeMap = { DRAFT: 'info', PENDING_REVIEW: 'warning', APPROVED: 'success', RECRUITING: '', COMPLETED: 'info', REJECTED: 'danger', CANCELLED: 'danger', ARCHIVED: 'info' }
const statusLabel = (s) => statusMap[s] || s
const statusType = (s) => statusTypeMap[s] || 'info'

const fetchList = async (role) => {
  error.value = false
  loading.value = true
  try {
    const { data } = await getMicroSpecialtyList({ role, page: 0, size: 50 })
    list.value = data.items || data || []
  } catch (e) { ElMessage.error(e?.response?.data?.message || '获取微专业列表失败'); error.value = true }
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
        deadlineText: deadline ? (remaining > 0 ? `剩余 ${remaining} 天` : '已过期') : ''
      }
    })
    pendingInviteCount.value = invites.value.filter(i => i.inviteStatus === 'INVITED' || i.inviteStatus === 'PENDING_ACADEMIC').length
  } catch (e) { ElMessage.error(e?.response?.data?.message || '获取邀请列表失败'); inviteError.value = true }
  finally { inviteLoading.value = false }
}

const handleTabChange = (name) => {
  if (name === 'invites') fetchInvites()
  else fetchList(name)
}

const handleAccept = async (inv) => {
  try { await acceptInvite(inv.id); ElMessage.success('已接受'); fetchInvites() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
}

const handleDecline = async (inv) => {
  try { await ElMessageBox.confirm('确定拒绝该邀请？', '提示', { type: 'warning' }) }
  catch { return }
  try { await declineInvite(inv.id); ElMessage.success('已拒绝'); fetchInvites() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
}

const showCreateDialog = () => { createVisible.value = true }
const resetCreateForm = () => {
  createForm.value = { title: '', subtitle: '', description: '', offerDepartmentId: null, semester: '' }
  createFormRef.value?.clearValidate()
}
const handleCreate = async () => {
  if (!createFormRef.value) return
  try { await createFormRef.value.validate() } catch { return }
  creating.value = true
  try { await createMicroSpecialty(createForm.value); ElMessage.success('创建成功'); createVisible.value = false; fetchList(activeTab.value) }
  catch (e) { ElMessage.error(e?.response?.data?.message || '创建失败') }
  finally { creating.value = false }
}

const fetchColleges = async () => {
  try {
    const { data } = await getDepartments({ size: 1000 })
    colleges.value = data.items || data || []
  } catch (e) { ElMessage.error(e?.response?.data?.message || '获取学院列表失败') }
}

onMounted(() => { fetchList('leading'); fetchColleges() })
</script>

<style scoped>
.ms-list-page { padding: var(--space-4); max-width: 1440px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.mg-top-16 { margin-top: var(--space-4); }
.full-width { width: 100%; }
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: var(--space-4); min-height: 200px; }
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
