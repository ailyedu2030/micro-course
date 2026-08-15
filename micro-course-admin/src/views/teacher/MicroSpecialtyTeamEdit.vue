<!--
  微专业团队管理（教师端）
  路由: /teacher/micro-specialties/:id/team
-->
<template>
  <div class="ms-team-page">
    <el-page-header @back="$router.back()" :content="$t('microSpecialtyTeamEdit.pageHeader', { title: detail?.title || '' })" class="mg-bottom-16" />

    <div v-loading="loading">
      <el-result v-if="error" icon="error" :title="$t('microSpecialtyManage.loadFailed')" :sub-title="$t('microSpecialtyManage.loadFailedSubtitle')">
        <template #extra><el-button type="primary" @click="fetchData">{{ $t('common.retry') }}</el-button></template>
      </el-result>
      <el-empty v-else-if="!loading && !detail" :description="$t('microSpecialtyManage.notFound')" />

      <template v-if="detail">
        <!-- 已邀请教师 -->
        <el-card shadow="never" class="section-card">
          <template #header>
            <div class="card-header">
              <span>{{ $t('microSpecialtyTeamEdit.invitedTeachers', { count: teachers.length }) }}</span>
              <el-button size="small" type="danger" @click="expelMode = !expelMode">{{ expelMode ? $t('microSpecialtyTeamEdit.done') : $t('microSpecialtyTeamEdit.batchOps') }}</el-button>
              <el-button v-if="expelMode" size="small" type="danger" plain :loading="batchRemoving" :disabled="selectedMembers.length === 0" @click="handleBatchRemoveMembers">
                {{ $t('microSpecialtyTeamEdit.batchRemoveCount', { count: selectedMembers.length }) }}
              </el-button>
            </div>
          </template>
          <el-table ref="memberTableRef" :data="teachers" stripe border :empty-text="$t('microSpecialtyTeamEdit.emptyTeachers')" @selection-change="handleMemberSelectionChange">
            <el-table-column v-if="expelMode" type="selection" width="50" />
            <el-table-column prop="teacherName" :label="$t('user.realName')" width="120" />
            <el-table-column :label="$t('user.role')" width="120">
              <template #default="{ row }"><el-tag size="small">{{ $t(roleMap[row.role] || row.role || 'role.TEACHER') }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="courseTitle" :label="$t('microSpecialtyTeamEdit.belongCourse')" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.courseTitle || '-' }}</template>
            </el-table-column>
            <el-table-column :label="$t('microSpecialtyTeamEdit.inviteStatus')" width="110" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.inviteStatus === 'INVITED'" type="warning" size="small">{{ $t('microSpecialtyTeamEdit.statusInvited') }}</el-tag>
                <el-tag v-else-if="row.inviteStatus === 'ACTIVE'" type="success" size="small">{{ $t('microSpecialtyTeamEdit.statusActive') }}</el-tag>
                <el-tag v-else-if="row.inviteStatus === 'PENDING_ACADEMIC'" type="warning" size="small">{{ $t('microSpecialtyTeamEdit.statusPendingAcademic') }}</el-tag>
                <el-tag v-else-if="row.inviteStatus === 'DECLINED'" type="danger" size="small">{{ $t('microSpecialtyTeamEdit.statusDeclined') }}</el-tag>
                <el-tag v-else-if="row.inviteStatus === 'REMOVED'" type="info" size="small">{{ $t('microSpecialtyTeamEdit.statusRemoved') }}</el-tag>
                <el-tag v-else size="small">{{ row.inviteStatus || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('microSpecialtyTeamEdit.columnExpired')" width="110" align="center">
              <template #default="{ row }">
                <span v-if="row.inviteStatus === 'INVITED'" :class="{ 'expiring': row.expiring }">{{ row.deadlineText || '-' }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column :label="$t('app.operation')" width="180" align="center" fixed="right">
              <template #default="{ row }">
                <el-button v-if="expelMode" size="small" type="danger" :loading="removingId === (row.id || row.teacherId)" @click="handleRemove(row)">{{ $t('microSpecialtyTeamEdit.remove') }}</el-button>
                <template v-else>
                  <el-button size="small" type="danger" :loading="removingId === (row.id || row.teacherId)" @click="handleRemove(row)">{{ $t('microSpecialtyTeamEdit.remove') }}</el-button>
                  <el-button v-if="row.inviteStatus === 'DECLINED' || row.inviteStatus === 'REMOVED'" size="small" @click="handleReinvite(row)">{{ $t('microSpecialtyTeamEdit.reinvite') }}</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 邀请新教师 -->
        <el-card shadow="never" class="section-card">
          <template #header><span class="card-title">{{ $t('microSpecialtyTeamEdit.inviteNewTeacher') }}</span></template>
          <!-- 搜索过滤 -->
          <div class="filter-bar">
            <el-input v-model="searchKeyword" :placeholder="$t('microSpecialtyTeamEdit.searchTeacherPlaceholder')" clearable class="search-input" @clear="fetchCandidates" @keyup.enter="fetchCandidates" :aria-label="$t('microSpecialtyTeamEdit.searchTeacherPlaceholder')">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="searchDept" :placeholder="$t('microSpecialtyTeamEdit.selectCollegePlaceholder')" clearable class="filter-select" @change="fetchCandidates" :aria-label="$t('microSpecialtyTeamEdit.filterDeptAria')">
              <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
            <el-button type="primary" @click="fetchCandidates">{{ $t('common.search') }}</el-button>
          </div>

          <!-- 候选教师表格 -->
          <el-table :data="candidates" stripe border v-loading="candidateLoading" @selection-change="handleSelectionChange" ref="candidateTableRef">
            <template #empty><el-empty :description="searched ? $t('microSpecialtyTeamEdit.noMatchTeacher') : $t('microSpecialtyTeamEdit.clickSearchHint')" /></template>
            <el-table-column type="selection" width="50" />
            <el-table-column prop="realName" :label="$t('user.realName')" width="120" />
            <el-table-column prop="collegeName" :label="$t('microSpecialtyTeamEdit.college')" width="140" show-overflow-tooltip />
            <el-table-column prop="email" :label="$t('user.email')" min-width="180" show-overflow-tooltip />
            <el-table-column :label="$t('user.role')" width="140">
              <template #default="{ row: r }">
                <el-select v-model="inviteRoles[r.id]" size="small" class="full-width" :aria-label="$t('microSpecialtyTeamEdit.selectRoleAria', { name: r.realName || '' })">
                  <el-option :label="$t('microSpecialtyTeamEdit.roleMember')" value="MEMBER" />
                  <el-option :label="$t('microSpecialtyTeamEdit.roleAssistant')" value="ASSISTANT" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="$t('microSpecialtyProposal.assignChapters')" width="120">
              <template #default="{ row: r }">
                <el-button link type="primary" size="small" @click="openChapterSelect(r)">
                  {{ $t('course.selectChapter') }} {{ getInviteChapterCount(r.id) > 0 ? $t('microSpecialtyTeamEdit.chapterCountSuffix', { count: getInviteChapterCount(r.id) }) : '' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="invite-bar" v-if="selectedCandidates.length > 0">
            <span>{{ $t('microSpecialtyTeamEdit.selectedPrefix') }} <strong>{{ selectedCandidates.length }}</strong> {{ $t('microSpecialtyTeamEdit.selectedSuffix') }}</span>
            <el-button type="primary" :loading="inviting" :disabled="inviting" @click="handleBatchInvite">{{ $t('microSpecialtyTeamEdit.batchInvite') }}</el-button>
          </div>
        </el-card>
      </template>
    </div>
    <el-dialog v-model="chapterPopupVisible" :title="$t('course.selectChapter')" width="500px">
      <template v-if="chapterPopupTeacher">
        <el-alert :title="$t('microSpecialtyProposal.assignChapterFor', { name: chapterPopupTeacher.realName || '' })" type="info" :closable="false" show-icon class="mg-bottom-12" />
        <el-checkbox-group v-model="inviteChapters[chapterPopupTeacher.id]" :aria-label="$t('course.selectChapter')">
          <div v-for="ch in chapterOptions" :key="ch.id" class="chapter-check-row">
            <el-checkbox :label="ch.id" :value="ch.id">
              {{ ch.courseTitle || ch.courseName }} / {{ ch.chapterTitle || ch.title }}
            </el-checkbox>
          </div>
        </el-checkbox-group>
        <div v-if="chapterOptions.length === 0" class="empty-hint">{{ $t('video.noChapters') }}</div>
      </template>
      <template #footer>
        <el-button @click="chapterPopupVisible = false">{{ $t('app.cancel') }}</el-button>
        <el-button type="primary" @click="chapterPopupVisible = false">{{ $t('course.dialogConfirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getMicroSpecialtyDetail, getTeachersForManage, inviteTeacher, removeTeacher, reinviteTeacher, getCourses } from '@/api/microSpecialty'
import { getUsers } from '@/api/user'
import { getDepartments } from '@/api/department'

const { t } = useI18n()

const roleMap = { LEAD: 'microSpecialtyTeamEdit.roleLead', MEMBER: 'microSpecialtyTeamEdit.roleMember', ASSISTANT: 'microSpecialtyTeamEdit.roleAssistant' }

const route = useRoute()
const msId = computed(() => route.params.id)
const loading = ref(true)
const error = ref(false)
const detail = ref(null)
const teachers = ref([])
const courseOptions = ref([])
const expelMode = ref(false)
const memberTableRef = ref(null)
const selectedMembers = ref([])
const batchRemoving = ref(false)

// 搜索候选教师
const searchKeyword = ref('')
const searchDept = ref(null)
const searched = ref(false)
const candidateLoading = ref(false)
const candidates = ref([])
const departments = ref([])
const selectedCandidates = ref([])
const inviteRoles = reactive({})
const inviteCourses = reactive({})
const inviting = ref(false)
const removingId = ref(null)
const candidateTableRef = ref(null)
const chapterOptions = ref([])
const chapterPopupVisible = ref(false)
const chapterPopupTeacher = ref(null)
const inviteChapters = reactive({})

const fetchData = async () => {
  error.value = false; loading.value = true
  try {
    const { data: d } = await getMicroSpecialtyDetail(msId.value); detail.value = d
    const { data: teachersRes } = await getTeachersForManage(msId.value)
    const items = teachersRes.items || teachersRes || []
    const now = Date.now()
    teachers.value = items.map(i => {
      const dl = i.inviteExpiresAt ? new Date(i.inviteExpiresAt).getTime() : null
      const rem = dl ? Math.max(0, Math.ceil((dl - now) / 86400000)) : null
      return { ...i, expiring: i.inviteStatus === 'INVITED' && rem !== null && rem < 3, deadlineText: dl ? (rem > 0 ? t('microSpecialtyTeamEdit.remainingDays', { count: rem }) : t('microSpecialtyTeamEdit.deadlineExpired')) : '' }
    })
    try { const { data: cc } = await getCourses(msId.value); courseOptions.value = cc.items || cc || [] } catch { /* skip course options */ }
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyTeamEdit.fetchDetailFailed')); error.value = true }
  finally { loading.value = false; loadDepartments() }
}

const loadDepartments = async () => {
  try { const { data } = await getDepartments(); departments.value = data?.items || data || [] }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyTeamEdit.fetchDepartmentsFailed')) }
}

// 防抖搜索
let searchDebounceTimer = null
const fetchCandidates = () => {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(async () => {
    candidateLoading.value = true; searched.value = true
    try {
      const params = { role: 'TEACHER', size: 100 }
      if (searchKeyword.value) params.keyword = searchKeyword.value
      if (searchDept.value) params.departmentId = searchDept.value
      const { data } = await getUsers(params)
      const all = data?.items || data || []
      // 排除已邀请/已接受/待响应(teacherId可能为null)
      const invitedIds = new Set(teachers.value.map(t => t.teacherId).filter(id => id != null))
      candidates.value = all.filter(t => !invitedIds.has(t.id))
      // 为每个候选初始化默认角色(MEMBER)
      candidates.value.forEach(t => {
        if (!(t.id in inviteRoles)) inviteRoles[t.id] = 'MEMBER'
      })
    } catch { candidates.value = [] }
    finally { candidateLoading.value = false }
  }, 300)
}

const handleSelectionChange = (rows) => {
  selectedCandidates.value = rows
  // 为新选中的教师初始化角色
  rows.forEach(t => {
    if (!(t.id in inviteRoles)) inviteRoles[t.id] = 'MEMBER'
  })
}

function handleMemberSelectionChange(rows) {
  selectedMembers.value = rows
}

async function handleBatchRemoveMembers() {
  if (selectedMembers.value.length === 0) return
  try {
    await ElMessageBox.confirm(t('microSpecialtyTeamEdit.confirmBatchRemove', { count: selectedMembers.value.length }), t('app.confirm'), { type: 'warning' })
  } catch { return }
  batchRemoving.value = true
  const failed = []
  for (const t of selectedMembers.value) {
    try {
      await removeTeacher(msId.value, t.teacherId)
    } catch {
      failed.push(t.teacherName)
    }
  }
  batchRemoving.value = false
  if (failed.length === 0) ElMessage.success(t('microSpecialtyTeamEdit.batchRemoveSuccess'))
  else ElMessage.warning(t('microSpecialtyTeamEdit.batchRemovePartialFail', { count: failed.length, names: failed.join(',') }))
  memberTableRef.value?.clearSelection()
  selectedMembers.value = []
  fetchData()
}

const handleBatchInvite = async () => {
  if (selectedCandidates.value.length === 0) return
  inviting.value = true
  const failed = []
  for (const teacher of selectedCandidates.value) {
    try {
      await inviteTeacher(msId.value, {
        teacherId: teacher.id,
        role: inviteRoles[teacher.id] || 'MEMBER',
        courseId: inviteCourses[teacher.id] || null,
        chapterIds: inviteChapters[teacher.id] || []
      })
    } catch (e) {
      failed.push({ name: teacher.realName, msg: e?.response?.data?.message || t('microSpecialtyTeamEdit.failed') })
    }
  }
  inviting.value = false
  // 详细的成功/失败反馈
  const succeeded = selectedCandidates.value.length - failed.length
  if (succeeded > 0) ElMessage.success(t('microSpecialtyTeamEdit.inviteSuccessCount', { count: succeeded }))
  if (failed.length > 0) {
    const msg = failed.map(f => `${f.name}: ${f.msg}`).join('; ')
    ElMessage.warning(t('microSpecialtyTeamEdit.invitePartialFail', { count: failed.length, msg: msg.substring(0, 200) }))
  }
  fetchData()
  // 刷新候选列表(已邀请的会被排除)
  if (searched.value) fetchCandidates()
  // 清空选择
  candidateTableRef.value?.clearSelection()
  selectedCandidates.value = []
}

const handleRemove = async (row) => {
  try { await ElMessageBox.confirm(t('microSpecialtyTeamEdit.confirmRemove', { name: row.teacherName }), t('app.confirm'), { type: 'warning' }) } catch { return }
  removingId.value = row.teacherId
  try { await removeTeacher(msId.value, row.teacherId); ElMessage.success(t('microSpecialtyTeamEdit.removedSuccess')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyTeamEdit.removeFailed')) }
  finally { removingId.value = null }
}

const handleReinvite = async (row) => {
  try { await ElMessageBox.confirm(t('microSpecialtyTeamEdit.confirmReinvite', { name: row.teacherName }), t('app.confirm'), { type: 'warning' }) } catch { return }
  // P1-C 修复：重邀请求体必须带 teacherId/role/courseId（此前空 body → "教师ID不能为空"）
  try {
    await reinviteTeacher(row.id || row.inviteId, {
      teacherId: row.teacherId,
      role: row.role || 'MEMBER',
      courseId: row.courseId || null
    })
    ElMessage.success(t('microSpecialtyTeamEdit.reinviteSuccess'))
    fetchData()
  }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyTeamEdit.reinviteFailed')) }
}

const handleBatchRemove = async () => {
  if (selectedCandidates.value.length === 0) return
  try { await ElMessageBox.confirm(t('microSpecialtyTeamEdit.confirmBatchRemove', { count: selectedCandidates.value.length }), t('app.confirm'), { type: 'warning' }) } catch { return }
  const failed = []
  for (const t of selectedCandidates.value) {
    try { await removeTeacher(msId.value, t.teacherId) }
    catch { failed.push(t.teacherName) }
  }
  if (failed.length === 0) ElMessage.success(t('microSpecialtyTeamEdit.batchRemoveSuccess'))
  else ElMessage.warning(t('microSpecialtyTeamEdit.batchRemovePartialFail', { count: failed.length, names: failed.join(',') }))
  fetchData()
  selectedCandidates.value = []
}

// 获取某教师当前已选章节数
function getInviteChapterCount(teacherId) {
  return (inviteChapters[teacherId] || []).length
}

// 打开章节选择弹窗
function openChapterSelect(teacher) {
  chapterPopupTeacher.value = teacher
  if (!inviteChapters[teacher.id]) inviteChapters[teacher.id] = []
  chapterPopupVisible.value = true
}

// 加载微专业的章节选项
async function loadChapterOptions() {
  try {
    const { data } = await getMicroSpecialtyDetail(msId.value)
    const chapters = []
    if (data.courses) {
      for (const c of data.courses) {
        if (c.chapters) {
          for (const ch of c.chapters) {
            chapters.push({
              id: ch.id,
              chapterTitle: ch.title,
              hours: ch.hours,
              courseTitle: c.courseName,
              courseId: c.id
            })
          }
        }
      }
    }
    chapterOptions.value = chapters
  } catch { ElMessage.warning(t('microSpecialtyTeamEdit.loadChaptersFailed')) }
}

onMounted(() => { fetchData(); loadChapterOptions() })
</script>

<style scoped>
.ms-team-page { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.full-width { width: 100%; }
.section-card { margin-bottom: var(--space-4); }
.card-title { font-size: 16px; font-weight: 600; color: #303133; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.expiring { color: var(--el-color-danger); font-weight: 600; }

.filter-bar { display: flex; gap: var(--space-3); margin-bottom: var(--space-4); }
.search-input { width: 280px; }
.filter-select { width: 200px; }

.invite-bar { display: flex; align-items: center; justify-content: space-between; padding: var(--space-4); margin-top: var(--space-4); background: var(--el-color-primary-light-9); border-radius: var(--radius-md); }
.mg-bottom-12 { margin-bottom: 12px; }
.chapter-check-row { padding: 6px 0; }
.empty-hint { padding: 24px; text-align: center; color: var(--el-text-color-secondary); }
</style>
