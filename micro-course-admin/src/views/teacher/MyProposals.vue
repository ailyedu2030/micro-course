<!--
  我的申报（教师端）
  路由: /teacher/micro-specialties/my-proposals
-->
<template>
  <div class="my-proposals">
    <el-page-header @back="$router.back()" content="我的申报" class="mg-bottom-16" />

    <el-card shadow="never">
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
      <template v-else>
      <el-table v-loading="loading" :data="proposals" stripe border>
        <template #empty>
          <el-empty description="暂未提交申报">
            <template #default>
              <el-button type="primary" @click="$router.push('/teacher/micro-specialties/proposals')">提交申报</el-button>
            </template>
          </el-empty>
        </template>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="collegeName" label="学院" width="120" />
        <el-table-column prop="semester" label="建议学期" width="120" />
        <el-table-column label="状态" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="130" align="center">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'APPROVED'">
              <el-button size="small" type="success" @click="$router.push(`/teacher/micro-specialties/${row.microSpecialtyId || row.id}/manage`)">进入管理</el-button>
            </template>
            <template v-else-if="row.status === 'REJECTED'">
              <el-button size="small" type="warning" @click="openEditor(row)">编辑重提</el-button>
            </template>
            <template v-else-if="row.status === 'PENDING_REVIEW'">
              <el-button size="small" type="danger" @click="handleWithdraw(row)">撤回</el-button>
            </template>
            <template v-else-if="row.status === 'WITHDRAWN'">
              <el-button size="small" type="primary" @click="openEditor(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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
      </template>
    </el-card>

    <!-- 编辑申报 Dialog -->
    <el-dialog
      v-model="editorVisible"
      :title="editorMode === 'resubmit' ? '编辑并重新提交' : '编辑申报'"
      width="700px"
      :close-on-click-modal="false"
      @closed="resetEditor"
    >
      <el-card shadow="never" class="editor-card" v-loading="collegeLoading">
        <template #header>
          <div class="editor-header">
            <span>{{ editorMode === 'resubmit' ? '修改后将以新申报重新提交审核' : '编辑已撤回的申报' }}</span>
            <el-tag :type="editorMode === 'resubmit' ? 'warning' : 'info'" size="small" class="editor-mode-tag">
              {{ editorMode === 'resubmit' ? '重新提交' : '仅保存' }}
            </el-tag>
          </div>
        </template>
        <el-form ref="editorFormRef" :model="editorForm" :rules="editorRules" label-width="100px" class="editor-form">
          <el-form-item label="标题" prop="title">
            <el-input v-model="editorForm.title" placeholder="微专业名称" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item label="说明" prop="description">
            <el-input v-model="editorForm.description" type="textarea" :rows="4" placeholder="详细说明微专业的内容与意义" maxlength="2000" show-word-limit />
          </el-form-item>
          <el-form-item label="开课学院" prop="offerDepartmentId">
            <el-select v-model="editorForm.offerDepartmentId" placeholder="选择学院" class="full-width">
              <el-option v-for="c in colleges" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="培养目标" prop="trainingObjective">
            <el-input v-model="editorForm.trainingObjective" type="textarea" :rows="3" placeholder="描述培养目标与预期成果" maxlength="2000" show-word-limit />
          </el-form-item>
          <el-form-item label="建议学期">
            <el-input v-model="editorForm.semester" placeholder="如 2025-2026-1" />
          </el-form-item>
          <el-form-item label="准入门槛">
            <el-input v-model="editorForm.prerequisites" type="textarea" :rows="2" placeholder="选课前提条件（可选）" maxlength="1000" show-word-limit />
          </el-form-item>
          <el-form-item label="招生上限">
            <el-input-number v-model="editorForm.maxStudents" :min="1" :max="10000" placeholder="招生人数上限" class="full-width" />
          </el-form-item>
          <el-form-item label="总学分">
            <el-input-number v-model="editorForm.credits" :min="0" :max="100" :precision="1" placeholder="总学分" class="full-width" />
          </el-form-item>
        </el-form>
      </el-card>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitEdit">
          {{ editorMode === 'resubmit' ? '重新提交' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyProposals, withdrawProposal, resubmitProposal, updateProposal, deleteProposal } from '@/api/microSpecialty'
import { getDepartments } from '@/api/department'

const loading = ref(false)
const error = ref(false)
const proposals = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const editorVisible = ref(false)
const submitting = ref(false)
const editorFormRef = ref(null)
const editorForm = ref({})
const editorMode = ref('edit')
const editingId = ref(null)
const collegeLoading = ref(false)
const colleges = ref([])
const editorRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入说明', trigger: 'blur' }],
  offerDepartmentId: [{ required: true, message: '请选择学院', trigger: 'change' }],
  trainingObjective: [{ required: true, message: '请输入培养目标', trigger: 'blur' }]
}

const statusMap = { PENDING_REVIEW: '审核中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回' }
const statusTypeMap = { PENDING_REVIEW: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info' }
const statusLabel = (s) => statusMap[s] || s
const statusType = (s) => statusTypeMap[s] || 'info'
const formatTime = (t) => t ? new Date(t).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-'

const loadColleges = async () => {
  collegeLoading.value = true
  try {
    const res = await getDepartments()
    colleges.value = res.data?.items || res.data || []
  } catch { /* 学院加载失败不影响编辑 */ }
  finally { collegeLoading.value = false }
}

const fetchData = async () => {
  error.value = false
  loading.value = true
  try {
    const { data } = await getMyProposals({ page: page.value, size: size.value })
    proposals.value = data.items || data || []
    total.value = data.totalElements || 0
  } catch { error.value = true }
  finally { loading.value = false }
}

const handleWithdraw = async (row) => {
  try { await ElMessageBox.confirm('确定撤回该申报？', '确认', { type: 'warning' }) }
  catch { return }
  try { await withdrawProposal(row.id); ElMessage.success('已撤回'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '撤回失败') }
}

const openEditor = (row) => {
  editingId.value = row.id
  editorMode.value = row.status === 'REJECTED' ? 'resubmit' : 'edit'
  editorForm.value = {
    title: row.title || '',
    description: row.description || '',
    offerDepartmentId: row.offerDepartmentId || row.collegeId || null,
    trainingObjective: row.trainingObjective || '',
    semester: row.semester || '',
    prerequisites: row.prerequisites || '',
    maxStudents: row.maxStudents || null,
    credits: row.credits || null
  }
  editorVisible.value = true
  loadColleges()
}

const resetEditor = () => {
  editorFormRef.value?.resetFields()
  editorForm.value = {}
  editingId.value = null
}

const handleSubmitEdit = async () => {
  if (!editorFormRef.value) return
  try { await editorFormRef.value.validate() } catch { return }
  submitting.value = true
  try {
    if (editorMode.value === 'edit') {
      await updateProposal(editingId.value, editorForm.value)
      ElMessage.success('已保存')
    } else {
      await resubmitProposal(editingId.value, editorForm.value)
      ElMessage.success('已重新提交')
    }
    editorVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try { await ElMessageBox.confirm('确定删除该申报？删除后不可恢复。', '确认', { type: 'warning' }) }
  catch { return }
  try { await deleteProposal(row.id); ElMessage.success('已删除'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '删除失败') }
}

onMounted(fetchData)
</script>

<style scoped>
.my-proposals { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.mg-top-12 { margin-top: var(--space-3); }
.pagination { display: flex; justify-content: flex-end; }
.no-action { color: var(--el-text-color-placeholder); }

.editor-card { border: 1px solid var(--el-border-color-light); }
.editor-header { display: flex; justify-content: space-between; align-items: center; }
.editor-mode-tag { flex-shrink: 0; }
.editor-form { margin-top: var(--space-2); }
.full-width { width: 100%; }
</style>