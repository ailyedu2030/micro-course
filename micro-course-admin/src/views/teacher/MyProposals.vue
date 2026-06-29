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
              <el-button size="small" type="warning" @click="handleEdit(row)">编辑重提</el-button>
            </template>
            <template v-else-if="row.status === 'PENDING_REVIEW'">
              <el-button size="small" type="danger" @click="handleWithdraw(row)">撤回</el-button>
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

    <!-- 编辑重提 Dialog -->
    <el-dialog v-model="editVisible" title="编辑申报" width="560px" @closed="resetEditForm">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="培养目标" prop="trainingObjective">
          <el-input v-model="editForm.trainingObjective" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="建议学期">
          <el-input v-model="editForm.semester" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="resubmitting" @click="handleResubmit">重新提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyProposals, withdrawProposal, resubmitProposal } from '@/api/microSpecialty'

const loading = ref(false)
const error = ref(false)
const proposals = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const editVisible = ref(false)
const resubmitting = ref(false)
const editFormRef = ref(null)
const editForm = ref({})
const editRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入说明', trigger: 'blur' }],
  trainingObjective: [{ required: true, message: '请输入培养目标', trigger: 'blur' }]
}
const editingId = ref(null)

const statusMap = { PENDING_REVIEW: '审核中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回' }
const statusTypeMap = { PENDING_REVIEW: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info' }
const statusLabel = (s) => statusMap[s] || s
const statusType = (s) => statusTypeMap[s] || 'info'
const formatTime = (t) => t ? new Date(t).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-'

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

const handleEdit = (row) => {
  editingId.value = row.id
  editForm.value = { title: row.title, description: row.description, trainingObjective: row.trainingObjective, semester: row.semester }
  editVisible.value = true
}
const resetEditForm = () => { editFormRef.value?.clearValidate() }

const handleResubmit = async () => {
  if (!editFormRef.value) return
  try { await editFormRef.value.validate() } catch { return }
  resubmitting.value = true
  try { await resubmitProposal(editingId.value, editForm.value); ElMessage.success('已重新提交'); editVisible.value = false; fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '提交失败') }
  finally { resubmitting.value = false }
}

onMounted(fetchData)
</script>

<style scoped>
.my-proposals { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.mg-top-12 { margin-top: var(--space-3); }
.pagination { display: flex; justify-content: flex-end; }
.no-action { color: var(--el-text-color-placeholder); }
</style>
