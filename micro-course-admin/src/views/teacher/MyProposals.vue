<!--
  我的申报（教师端）
  路由: /teacher/micro-specialties/my-proposals
-->
<template>
  <div class="my-proposals">
    <div class="page-header-wrapper">
      <el-page-header @back="$router.back()" content="我的申报" class="mg-bottom-16" />
      <el-button type="primary" @click="$router.push('/teacher/micro-specialties/proposals')">新建申报</el-button>
    </div>

    <!-- 列表卡片 -->
    <el-card shadow="never">
      <el-result v-if="error" icon="error" title="加载失败" sub-title="请稍后重试">
        <template #extra><el-button type="primary" @click="fetchData">重试</el-button></template>
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
        <el-table-column label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.title || '未命名申报' }}
          </template>
        </el-table-column>
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
        <el-table-column label="操作" width="360" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'DRAFT'">
              <el-button size="small" type="primary" @click="openEditor(row)">编辑</el-button>
              <el-button size="small" @click="handlePreview(row)">预览</el-button>
              <el-button size="small" :loading="exportingRowId === row.id && exportingType === 'word'" :disabled="!!exportingRowId" @click="handleExportWord(row)">Word</el-button>
              <el-button size="small" :loading="exportingRowId === row.id && exportingType === 'pdf'" :disabled="!!exportingRowId" @click="handleExportPdf(row)">PDF</el-button>
              <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" size="small" type="danger" :disabled="!!exportingRowId" @click="handleDelete(row)">删除</el-button>
            </template>
            <template v-else-if="row.status === 'APPROVED'">
              <el-button size="small" type="success" @click="$router.push(`/teacher/micro-specialties/${row.microSpecialtyId || row.id}/manage`)">进入管理</el-button>
              <el-button size="small" @click="handlePreview(row)">预览</el-button>
              <el-button size="small" :loading="exportingRowId === row.id && exportingType === 'word'" :disabled="!!exportingRowId" @click="handleExportWord(row)">Word</el-button>
              <el-button size="small" :loading="exportingRowId === row.id && exportingType === 'pdf'" :disabled="!!exportingRowId" @click="handleExportPdf(row)">PDF</el-button>
            </template>
            <template v-else-if="row.status === 'REJECTED'">
              <el-button size="small" type="warning" @click="openEditor(row)">编辑重提</el-button>
              <el-button size="small" @click="handlePreview(row)">预览</el-button>
              <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" size="small" type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
            <template v-else-if="row.status === 'PENDING_REVIEW'">
              <el-button size="small" @click="handlePreview(row)">预览</el-button>
              <el-button size="small" type="danger" @click="handleWithdraw(row)">撤回</el-button>
            </template>
            <template v-else-if="row.status === 'WITHDRAWN'">
              <el-button size="small" type="primary" @click="openEditor(row)">编辑</el-button>
              <el-button size="small" @click="handlePreview(row)">预览</el-button>
              <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" size="small" type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
            <span v-else class="no-action">-</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination mg-top-12">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total, prev, pager, next" @current-change="fetchData" />
      </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyProposals, withdrawProposal, deleteProposal } from '@/api/microSpecialty'
import { exportStorageWord, exportStoragePdf } from '@/api/storageApplication'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const userRole = computed(() => userStore.role)

const loading = ref(false)
const error = ref(false)
const proposals = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const router = useRouter()

const statusMap = { DRAFT: '草稿', PENDING_REVIEW: '审核中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回' }
const statusTypeMap = { DRAFT: 'info', PENDING_REVIEW: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info' }
const statusLabel = (s) => statusMap[s] || s
const statusType = (s) => statusTypeMap[s] || 'info'
const formatTime = (t) => t ? new Date(t).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '-'

const fetchData = async () => {
  error.value = false; loading.value = true
  try { const { data } = await getMyProposals({ page: page.value - 1, size: size.value }); proposals.value = data.items || data || []; total.value = data.totalElements || 0 }
  catch (e) { ElMessage.error(e?.response?.data?.message || '获取申报列表失败'); error.value = true }
  finally { loading.value = false }
}

const handleWithdraw = async (row) => {
  try { await ElMessageBox.confirm('确定撤回该申报？', '确认', { type: 'warning' }) } catch { return }
  try { await withdrawProposal(row.id); ElMessage.success('已撤回'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '撤回失败') }
}

const openEditor = (row) => {
  // 跳转到完整的新申报页面（和新建申报使用同一表单）
  router.push(`/teacher/micro-specialties/proposals?id=${row.id}`)
}

const handleDelete = async (row) => {
  try { await ElMessageBox.confirm('确定删除该申报？删除后不可恢复。', '确认', { type: 'warning' }) } catch { return }
  try { await deleteProposal(row.id); ElMessage.success('已删除'); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '删除失败') }
}

const handlePreview = (row) => {
  router.push(`/teacher/micro-specialties/storage-preview/${row.id}`)
}

// P1-UX: 跟踪正在导出的行+格式，按钮显示 loading 并禁用
const exportingRowId = ref(null)
const exportingType = ref(null)

const handleExportWord = async (row) => {
  exportingRowId.value = row.id
  exportingType.value = 'word'
  try {
    const res = await exportStorageWord(row.id)
    const blob = res.data instanceof Blob ? res.data : new Blob([res.data])
    if (blob.type === 'application/json') {
      const text = await new Response(blob).text()
      const err = JSON.parse(text)
      if (err.errors && Array.isArray(err.errors) && err.errors.length) {
        ElMessageBox.alert(err.errors.map(e => `• ${e}`).join('\n'), err.message || '请补全必填项后再导出', { type: 'warning' })
      } else {
        ElMessage.error(err?.message || '导出失败')
      }
      return
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const title = row.title || row.microSpecialtyName || '微专业'
    const date = new Date().toISOString().slice(0, 10)
    a.download = `【${title}】整理收纳微专业申请表_${date}.docx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('Word 导出成功')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '导出失败')
  } finally {
    exportingRowId.value = null
    exportingType.value = null
  }
}

const handleExportPdf = async (row) => {
  exportingRowId.value = row.id
  exportingType.value = 'pdf'
  try {
    const res = await exportStoragePdf(row.id)
    const blob = res.data instanceof Blob ? res.data : new Blob([res.data])
    if (blob.type === 'application/json') {
      const text = await new Response(blob).text()
      const err = JSON.parse(text)
      if (err.errors && Array.isArray(err.errors) && err.errors.length) {
        ElMessageBox.alert(err.errors.map(e => `• ${e}`).join('\n'), err.message || '请补全必填项后再导出', { type: 'warning' })
      } else {
        ElMessage.error(err?.message || '导出失败')
      }
      return
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const title = row.title || row.microSpecialtyName || '微专业'
    const date = new Date().toISOString().slice(0, 10)
    a.download = `【${title}】整理收纳微专业申请表_${date}.pdf`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('PDF 导出成功')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '导出失败')
  } finally {
    exportingRowId.value = null
    exportingType.value = null
  }
}

onMounted(fetchData)
</script>

<style scoped>
.my-proposals { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.mg-top-12 { margin-top: var(--space-3); }
.pagination { display: flex; justify-content: flex-end; }
.no-action { color: var(--el-text-color-placeholder); }

.page-header-wrapper { display: flex; align-items: center; justify-content: space-between; }
.card-title { font-size: 16px; font-weight: 600; color: #303133; }
.full-width { width: 100%; }
.quill-wrapper { width: 100%; border-radius: 4px; }
.quill-wrapper :deep(.ql-toolbar) { border-radius: 4px 4px 0 0; background: #fafafa; }
.quill-wrapper :deep(.ql-container) { border-radius: 0 0 4px 4px; font-size: 14px; }
</style>