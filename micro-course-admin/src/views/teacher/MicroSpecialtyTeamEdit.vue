<!--
  微专业团队管理（教师端）
  路由: /teacher/micro-specialties/:id/team
-->
<template>
  <div class="ms-team-edit">
    <el-page-header @back="$router.back()" :content="'教师团队 · ' + (detail?.title || '')" class="mg-bottom-16" />

    <div v-loading="loading">
      <el-result
        v-if="error"
        icon="error"
        title="加载失败"
        sub-title="请稍后重试"
      >
        <template #extra>
          <el-button type="primary" @click="fetchData">重试</el-button>
        </template>
      </el-result>
      <el-empty v-else-if="!loading && !detail" description="微专业不存在" />

      <div v-if="detail">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>团队成员（{{ teachers.length }} 人）</span>
              <el-button type="primary" size="small" @click="showInviteDialog">邀请教师</el-button>
            </div>
          </template>
          <el-table :data="teachers" stripe border>
            <template #empty><el-empty description="暂未邀请教师" /></template>
            <el-table-column prop="teacherName" label="姓名" width="120" />
            <el-table-column prop="role" label="角色" width="120">
              <template #default="{ row }"><el-tag size="small">{{ roleMap[row.role] || row.role || '教师' }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="courseTitle" label="归属课程" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.courseTitle || '-' }}</template>
            </el-table-column>
            <el-table-column label="邀请状态" width="130" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.inviteStatus === 'INVITED'" type="warning" size="small">待响应</el-tag>
                <el-tag v-else-if="row.inviteStatus === 'ACTIVE'" type="success" size="small">已接受</el-tag>
                <el-tag v-else-if="row.inviteStatus === 'DECLINED'" type="danger" size="small">已拒绝</el-tag>
                <el-tag v-else-if="row.inviteStatus === 'REMOVED'" type="info" size="small">已移除</el-tag>
                <el-tag v-else size="small">{{ row.inviteStatus || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="倒计时" width="100" align="center">
              <template #default="{ row }">
                <span v-if="row.inviteStatus === 'INVITED'" :class="{ 'expiring': row.expiring }">
                  {{ row.deadlineText || '-' }}
                </span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" align="center" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="danger" @click="handleRemove(row)">移除</el-button>
                <el-button v-if="row.inviteStatus === 'DECLINED' || row.inviteStatus === 'REMOVED'" size="small" @click="handleReinvite(row)">重邀</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </div>

    <!-- 邀请教师 Dialog -->
    <el-dialog v-model="inviteVisible" title="邀请教师" width="500px" @closed="resetInviteForm">
      <el-form ref="inviteFormRef" :model="inviteForm" :rules="inviteRules" label-width="100px">
        <el-form-item label="教师" prop="teacherId">
          <el-select v-model="inviteForm.teacherId" filterable placeholder="搜索教师" class="full-width">
            <el-option v-for="t in teacherOptions" :key="t.id" :label="`${t.realName} (${t.collegeName || ''})`" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="inviteForm.role" class="full-width">
            <el-option label="负责人" value="LEAD" />
            <el-option label="团队成员" value="MEMBER" />
            <el-option label="助教" value="ASSISTANT" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属课程">
          <el-select v-model="inviteForm.courseId" filterable placeholder="选择课程（可选）" class="full-width" clearable>
            <el-option v-for="c in courseOptions" :key="c.id" :label="c.courseTitle || c.title" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inviteVisible = false">取消</el-button>
        <el-button type="primary" :loading="inviting" @click="handleInvite">发送邀请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMicroSpecialtyDetail, getTeachers, inviteTeacher, removeTeacher, reinviteTeacher, getCourses } from '@/api/microSpecialty'

const roleMap = { LEAD: '负责人', MEMBER: '团队成员', ASSISTANT: '助教' }

const route = useRoute()
const msId = computed(() => route.params.id)
const loading = ref(true)
const error = ref(false)
const detail = ref(null)
const teachers = ref([])
const courseOptions = ref([])

const inviteVisible = ref(false)
const inviting = ref(false)
const inviteFormRef = ref(null)
const inviteForm = ref({ teacherId: null, role: 'MEMBER', courseId: null })
const inviteRules = { teacherId: [{ required: true, message: '请选择教师', trigger: 'change' }] }
const teacherOptions = ref([])

const fetchData = async () => {
  error.value = false
  loading.value = true
  try {
    const { data: d } = await getMicroSpecialtyDetail(msId.value)
    detail.value = d
    const { data: t } = await getTeachers(msId.value)
    const items = t.items || t || []
    const now = Date.now()
    teachers.value = items.map(i => {
      const dl = i.inviteExpiresAt ? new Date(i.inviteExpiresAt).getTime() : null
      const rem = dl ? Math.max(0, Math.ceil((dl - now) / 86400000)) : null
      return { ...i, expiring: i.inviteStatus === 'INVITED' && rem !== null && rem < 3, deadlineText: dl ? (rem > 0 ? `剩余${rem}天` : '已过期') : '' }
    })
    // Load courses for invite dialog
    try { const { data: cc } = await getCourses(msId.value); courseOptions.value = cc.items || cc || [] } catch { /* skip */ }
  } catch { error.value = true }
  finally { loading.value = false }
}

const showInviteDialog = () => {
  inviteForm.value = { teacherId: null, role: 'MEMBER', courseId: null }
  inviteVisible.value = true
}
const resetInviteForm = () => { inviteFormRef.value?.clearValidate() }

const handleInvite = async () => {
  if (!inviteFormRef.value) return
  try { await inviteFormRef.value.validate() } catch { return }
  inviting.value = true
  try { await inviteTeacher(msId.value, inviteForm.value); ElMessage.success('邀请已发送'); inviteVisible.value = false; fetchData() }
  catch { ElMessage.error('邀请失败') }
  finally { inviting.value = false }
}

const handleRemove = async (row) => {
  try { await ElMessageBox.confirm(`确定移除「${row.teacherName}」？`, '确认', { type: 'warning' }) }
  catch { return }
  try { await removeTeacher(msId.value, row.id || row.teacherId); ElMessage.success('已移除'); fetchData() }
  catch { ElMessage.error('移除失败') }
}

const handleReinvite = async (row) => {
  try {       await reinviteTeacher(row.id || row.inviteId, {}); ElMessage.success('已重新邀请'); fetchData() }
  catch { ElMessage.error('重邀失败') }
}

onMounted(fetchData)
</script>

<style scoped>
.ms-team-edit { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.full-width { width: 100%; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.expiring { color: var(--el-color-danger); font-weight: 600; }
</style>
