<!--
  微专业团队管理（教师端）
  路由: /teacher/micro-specialties/:id/team
-->
<template>
  <div class="ms-team-page">
    <el-page-header @back="$router.back()" :content="'教师团队 · ' + (detail?.title || '')" class="mg-bottom-16" />

    <div v-loading="loading">
      <el-result v-if="error" icon="error" title="加载失败" sub-title="请稍后重试">
        <template #extra><el-button type="primary" @click="fetchData">重试</el-button></template>
      </el-result>
      <el-empty v-else-if="!loading && !detail" description="微专业不存在" />

      <template v-if="detail">
        <!-- 已邀请教师 -->
        <el-card shadow="never" class="section-card">
          <template #header>
            <div class="card-header">
              <span>已邀请教师（{{ teachers.length }} 人）</span>
              <el-button size="small" type="danger" @click="expelMode = !expelMode">{{ expelMode ? '完成' : '批量操作' }}</el-button>
            </div>
          </template>
          <el-table :data="teachers" stripe border>
            <template #empty><el-empty description="暂未邀请教师" /></template>
            <el-table-column prop="teacherName" label="姓名" width="120" />
            <el-table-column label="角色" width="120">
              <template #default="{ row }"><el-tag size="small">{{ roleMap[row.role] || row.role || '教师' }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="courseTitle" label="归属课程" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.courseTitle || '-' }}</template>
            </el-table-column>
            <el-table-column label="邀请状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.inviteStatus === 'INVITED'" type="warning" size="small">待响应</el-tag>
                <el-tag v-else-if="row.inviteStatus === 'ACTIVE'" type="success" size="small">已接受</el-tag>
                <el-tag v-else-if="row.inviteStatus === 'DECLINED'" type="danger" size="small">已拒绝</el-tag>
                <el-tag v-else-if="row.inviteStatus === 'REMOVED'" type="info" size="small">已移除</el-tag>
                <el-tag v-else size="small">{{ row.inviteStatus || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="过期" width="110" align="center">
              <template #default="{ row }">
                <span v-if="row.inviteStatus === 'INVITED'" :class="{ 'expiring': row.expiring }">{{ row.deadlineText || '-' }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" align="center" fixed="right">
              <template #default="{ row }">
                <el-button v-if="expelMode" size="small" type="danger" :loading="removingId === (row.id || row.teacherId)" @click="handleRemove(row)">移除</el-button>
                <template v-else>
                  <el-button size="small" type="danger" :loading="removingId === (row.id || row.teacherId)" @click="handleRemove(row)">移除</el-button>
                  <el-button v-if="row.inviteStatus === 'DECLINED' || row.inviteStatus === 'REMOVED'" size="small" @click="handleReinvite(row)">重邀</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 邀请新教师 -->
        <el-card shadow="never" class="section-card">
          <template #header><span class="card-title">邀请新教师</span></template>
          <!-- 搜索过滤 -->
          <div class="filter-bar">
            <el-input v-model="searchKeyword" placeholder="搜索教师姓名" clearable class="search-input" @clear="fetchCandidates" @keyup.enter="fetchCandidates">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="searchDept" placeholder="选择学院" clearable class="filter-select" @change="fetchCandidates">
              <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
            </el-select>
            <el-button type="primary" @click="fetchCandidates">搜索</el-button>
          </div>

          <!-- 候选教师表格 -->
          <el-table :data="candidates" stripe border v-loading="candidateLoading" @selection-change="handleSelectionChange" ref="candidateTableRef">
            <template #empty><el-empty :description="searched ? '未找到匹配的教师' : '点击搜索查看可选教师'" /></template>
            <el-table-column type="selection" width="50" />
            <el-table-column prop="realName" label="姓名" width="120" />
            <el-table-column prop="collegeName" label="学院" width="140" show-overflow-tooltip />
            <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
            <el-table-column label="角色" width="140">
              <template #default="{ row: r }">
                <el-select v-model="inviteRoles[r.id]" size="small" class="full-width">
                  <el-option label="团队成员" value="MEMBER" />
                  <el-option label="助教" value="ASSISTANT" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="归属课程" width="160">
              <template #default="{ row: r }">
                <el-select v-model="inviteCourses[r.id]" size="small" filterable placeholder="选择课程" class="full-width" clearable>
                  <el-option v-for="c in courseOptions" :key="c.id" :label="c.courseTitle || c.title" :value="c.id" />
                </el-select>
              </template>
            </el-table-column>
          </el-table>

          <div class="invite-bar" v-if="selectedCandidates.length > 0">
            <span>已选 <strong>{{ selectedCandidates.length }}</strong> 位教师</span>
            <el-button type="primary" :loading="inviting" @click="handleBatchInvite">批量邀请</el-button>
          </div>
        </el-card>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getMicroSpecialtyDetail, getTeachers, inviteTeacher, removeTeacher, reinviteTeacher, getCourses } from '@/api/microSpecialty'
import { getUsers } from '@/api/user'
import { getDepartments } from '@/api/department'

const roleMap = { LEAD: '负责人', MEMBER: '团队成员', ASSISTANT: '助教' }

const route = useRoute()
const msId = computed(() => route.params.id)
const loading = ref(true)
const error = ref(false)
const detail = ref(null)
const teachers = ref([])
const courseOptions = ref([])
const expelMode = ref(false)

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

const fetchData = async () => {
  error.value = false; loading.value = true
  try {
    const { data: d } = await getMicroSpecialtyDetail(msId.value); detail.value = d
    const { data: t } = await getTeachers(msId.value)
    const items = t.items || t || []
    const now = Date.now()
    teachers.value = items.map(i => {
      const dl = i.inviteExpiresAt ? new Date(i.inviteExpiresAt).getTime() : null
      const rem = dl ? Math.max(0, Math.ceil((dl - now) / 86400000)) : null
      return { ...i, expiring: i.inviteStatus === 'INVITED' && rem !== null && rem < 3, deadlineText: dl ? (rem > 0 ? `剩余${rem}天` : '已过期') : '' }
    })
    try { const { data: cc } = await getCourses(msId.value); courseOptions.value = cc.items || cc || [] } catch {}
  } catch { error.value = true }
  finally { loading.value = false; loadDepartments() }
}

const loadDepartments = async () => {
  try { const { data } = await getDepartments(); departments.value = data?.items || data || [] }
  catch {}
}

// 防抖搜索
let searchDebounceTimer = null
const fetchCandidates = () => {
  if (searchDebounceTimer) clearTimeout(searchDebounceTimer)
  searchDebounceTimer = setTimeout(async () => {
    candidateLoading.value = true; searched.value = true
    try {
      const params = { role: 'TEACHER', size: 200 }
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

const handleBatchInvite = async () => {
  if (selectedCandidates.value.length === 0) return
  inviting.value = true
  const failed = []
  for (const t of selectedCandidates.value) {
    try {
      await inviteTeacher(msId.value, {
        teacherId: t.id,
        role: inviteRoles[t.id] || 'MEMBER',
        courseId: inviteCourses[t.id] || null
      })
    } catch (e) {
      failed.push({ name: t.realName, msg: e?.response?.data?.message || '失败' })
    }
  }
  inviting.value = false
  // 详细的成功/失败反馈
  const succeeded = selectedCandidates.value.length - failed.length
  if (succeeded > 0) ElMessage.success(`已邀请 ${succeeded} 位教师`)
  if (failed.length > 0) {
    const msg = failed.map(f => `${f.name}: ${f.msg}`).join('; ')
    ElMessage.warning(`${failed.length} 位失败: ${msg.substring(0, 200)}`)
  }
  fetchData()
  // 刷新候选列表(已邀请的会被排除)
  if (searched.value) fetchCandidates()
  // 清空选择
  candidateTableRef.value?.clearSelection()
  selectedCandidates.value = []
}

const handleRemove = async (row) => {
  try { await ElMessageBox.confirm(`确定移除「${row.teacherName}」？`, '确认', { type: 'warning' }) } catch { return }
  removingId.value = row.id || row.teacherId
  try { await removeTeacher(msId.value, row.id || row.teacherId); ElMessage.success('已移除'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '移除失败') }
  finally { removingId.value = null }
}

const handleReinvite = async (row) => {
  try { await ElMessageBox.confirm(`确定重新邀请「${row.teacherName}」？`, '确认', { type: 'warning' }) } catch { return }
  try { await reinviteTeacher(row.id || row.inviteId, {}); ElMessage.success('已重新邀请'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '重邀失败') }
}

const handleBatchRemove = async () => {
  if (selectedCandidates.value.length === 0) return
  try { await ElMessageBox.confirm(`确定批量移除 ${selectedCandidates.value.length} 位教师？`, '确认', { type: 'warning' }) } catch { return }
  const failed = []
  for (const t of selectedCandidates.value) {
    try { await removeTeacher(msId.value, t.id || t.teacherId) }
    catch { failed.push(t.teacherName) }
  }
  if (failed.length === 0) ElMessage.success('已批量移除')
  else ElMessage.warning(`${failed.length} 位移除失败: ${failed.join(',')}`)
  fetchData()
  selectedCandidates.value = []
}

onMounted(fetchData)
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
</style>