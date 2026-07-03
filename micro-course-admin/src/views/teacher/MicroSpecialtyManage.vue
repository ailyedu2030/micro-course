<!--
  微专业管理（教师端）
  路由: /teacher/micro-specialties/:id/manage
-->
<template>
  <div class="ms-manage" v-loading="loading">
    <!-- 错误/空 -->
    <el-result v-if="error" icon="error" title="加载失败" sub-title="请稍后重试">
      <template #extra><el-button type="primary" @click="fetchDetail">重试</el-button></template>
    </el-result>
    <el-empty v-else-if="!loading && !detail" description="微专业不存在" />

    <template v-if="detail">
      <!-- 页头 -->
      <div class="page-header">
        <div class="header-left">
          <el-page-header @back="$router.back()" :content="detail?.title || '微专业管理'" />
          <el-tag :type="statusType" size="small" class="status-tag">{{ statusLabel }}</el-tag>
        </div>
        <div class="header-actions">
          <el-button v-if="showSubmit" type="success" :loading="submitting" @click="handleSubmit">提交审核</el-button>
          <el-button v-if="showOpen" type="warning" :loading="actioning" @click="handleOpen">开课</el-button>
          <el-button v-if="showClose" type="danger" :loading="actioning" @click="handleClose">结业</el-button>
          <el-button v-if="detail.status === 'APPROVED' || detail.status === 'RECRUITING'" @click="showFeaturedDialog">申请置顶</el-button>
        </div>
      </div>

      <!-- 统计卡片 -->
      <el-row :gutter="16" class="stats-row">
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card"><el-statistic title="选课人数" :value="detail.stats?.totalEnrollments || 0" /></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card"><el-statistic title="课程数" :value="detail.stats?.courseCount || 0" /></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card"><el-statistic title="完成人数" :value="detail.stats?.completedCount || 0" /></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stat-card"><el-statistic title="待审报名" :value="detail.stats?.pendingEnrollmentCount || 0" /></el-card>
        </el-col>
      </el-row>

      <!-- 工具栏 -->
      <div class="toolbar">
        <el-button type="primary" @click="$router.push(`/teacher/micro-specialties/${detail.id}/courses`)">课程编排</el-button>
        <el-button @click="$router.push(`/teacher/micro-specialties/${detail.id}/team`)">团队管理</el-button>
      </div>

      <!-- 基本信息 -->
      <el-card shadow="never" class="section-card">
        <template #header><span class="card-title">基本信息</span></template>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="info-form">
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="标题" prop="title"><el-input v-model="form.title" /></el-form-item>
              <el-form-item label="副标题"><el-input v-model="form.subtitle" /></el-form-item>
              <el-form-item label="开课学院"><el-input :model-value="form.collegeName" disabled /></el-form-item>
              <el-form-item label="学期"><el-input v-model="form.semester" /></el-form-item>
              <el-form-item label="封面 URL"><el-input v-model="form.coverUrl" /></el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="说明">
                <div class="quill-wrapper"><QuillEditor v-model:content="form.description" content-type="html" toolbar="essential" placeholder="微专业说明..." :style="{ minHeight: '120px' }" /></div>
              </el-form-item>
              <el-form-item label="培养目标">
                <div class="quill-wrapper"><QuillEditor v-model:content="form.trainingObjective" content-type="html" toolbar="essential" placeholder="培养目标..." :style="{ minHeight: '120px' }" /></div>
              </el-form-item>
              <el-form-item label="准入门槛"><el-input v-model="form.admissionRequirement" type="textarea" :rows="2" /></el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <div class="form-actions"><el-button type="primary" :loading="saving" @click="handleSave">保存</el-button></div>
      </el-card>

      <!-- 选课列表 -->
      <el-card shadow="never" class="section-card">
        <template #header><span class="card-title">选课列表</span></template>
        <el-table :data="enrollments" v-loading="enrollLoading" stripe border>
          <template #empty><el-empty description="暂无选课记录" /></template>
          <el-table-column prop="userName" label="学生" width="120" />
          <el-table-column prop="className" label="班级" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag :type="enrollTagType(row.status)" size="small">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="appliedAt" label="报名时间" width="160" />
          <el-table-column prop="progress" label="进度" width="100">
            <template #default="{ row }">{{ row.progress ?? 0 }}%</template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 进度概览 -->
      <el-card shadow="never" class="section-card" v-loading="progressLoading">
        <template #header><span class="card-title">学习进度</span></template>
        <el-empty v-if="!progressData" description="暂无数据" />
        <el-row v-else :gutter="16">
          <el-col :span="8"><el-statistic title="总选中" :value="progressData.totalEnrollments || 0" /></el-col>
          <el-col :span="8"><el-statistic title="进行中" :value="progressData.inProgress || 0" /></el-col>
          <el-col :span="8"><el-statistic title="已完成" :value="progressData.completed || 0" /></el-col>
        </el-row>
      </el-card>
    </template>

    <!-- 申请置顶 Dialog -->
    <el-dialog v-model="featuredVisible" title="申请置顶" width="480px">
      <el-form :model="featuredForm" label-width="80px">
        <el-form-item label="申请理由"><el-input v-model="featuredForm.reason" type="textarea" :rows="3" placeholder="请填写申请置顶理由" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="featuredVisible = false">取消</el-button>
        <el-button type="primary" :loading="featuring" @click="handleFeatured">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getMicroSpecialtyDetail, updateMicroSpecialty, submitMicroSpecialty, openMicroSpecialty, closeMicroSpecialty, cancelMicroSpecialty, applyFeatured, getStats, getEnrollmentList } from '@/api/microSpecialty'
import { getEnrollments } from '@/api/enrollment'
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'

const route = useRoute()
const userStore = useUserStore()
const msId = computed(() => route.params.id)
const loading = ref(true)
const error = ref(false)
const saving = ref(false)
const submitting = ref(false)
const actioning = ref(false)
const detail = ref(null)
const formRef = ref(null)
const form = ref({})
const rules = { title: [{ required: true, message: '请输入标题', trigger: 'blur' }] }

const enrollments = ref([])
const enrollLoading = ref(false)
const progressData = ref(null)
const progressLoading = ref(false)
const featuredVisible = ref(false)
const featuring = ref(false)
const featuredForm = ref({ reason: '' })

const enrollTagType = (s) => ({ PENDING: 'warning', APPROVED: '', IN_PROGRESS: 'primary', COMPLETED: 'success', CERTIFIED: 'success', FAILED: 'danger', DROPPED: 'info' })[s] || 'info'

const status = computed(() => detail.value?.status)
const showSubmit = computed(() => ['DRAFT', 'REJECTED'].includes(status.value))
const showOpen = computed(() => status.value === 'APPROVED')
const showClose = computed(() => status.value === 'RECRUITING')

const statusMap = { DRAFT: '草稿', PENDING_REVIEW: '待审核', APPROVED: '已通过', REJECTED: '已驳回', RECRUITING: '招生中', COMPLETED: '已结业', CANCELLED: '已取消', ARCHIVED: '已归档' }
const statusTypeMap = { DRAFT: 'info', PENDING_REVIEW: 'warning', APPROVED: 'success', REJECTED: 'danger', RECRUITING: '', COMPLETED: 'info', CANCELLED: 'danger', ARCHIVED: 'info' }
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
  } catch (e) { ElMessage.error(e?.response?.data?.message || '获取微专业详情失败'); error.value = true }
  finally { loading.value = false }
}

const fetchEnrollments = async () => {
  enrollLoading.value = true
  try { const { data } = await getEnrollmentList(msId.value); enrollments.value = data?.items || data || [] }
  catch (e) { ElMessage.error(e?.response?.data?.message || '获取报名列表失败') }
  finally { enrollLoading.value = false }
}

const fetchProgress = async () => {
  progressLoading.value = true
  try { progressData.value = detail.value?.stats || {} }
  catch (e) { ElMessage.error(e?.response?.data?.message || '获取进度数据失败') }
  finally { progressLoading.value = false }
}

const handleSave = async () => {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try { await updateMicroSpecialty(msId.value, form.value); ElMessage.success('保存成功'); fetchDetail() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '保存失败') }
  finally { saving.value = false }
}

const handleSubmit = async () => {
  submitting.value = true
  try { await submitMicroSpecialty(msId.value); ElMessage.success('已提交审核'); fetchDetail() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '提交失败') }
  finally { submitting.value = false }
}

const handleOpen = async () => {
  actioning.value = true
  try { await openMicroSpecialty(msId.value); ElMessage.success('已开课'); fetchDetail() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
  finally { actioning.value = false }
}

const handleClose = async () => {
  actioning.value = true
  try { await closeMicroSpecialty(msId.value); ElMessage.success('已结业'); fetchDetail() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
  finally { actioning.value = false }
}

const handleCancel = async () => {
  try { await ElMessageBox.confirm('确定取消该微专业？此操作不可恢复。', '确认', { type: 'warning' }) } catch { return }
  actioning.value = true
  try { await cancelMicroSpecialty(msId.value); ElMessage.success('已取消'); fetchDetail() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
  finally { actioning.value = false }
}

const showFeaturedDialog = () => { featuredForm.value.reason = ''; featuredVisible.value = true }
const handleFeatured = async () => {
  featuring.value = true
  try { await applyFeatured(msId.value, { reason: featuredForm.value.reason }); ElMessage.success('置顶申请已提交'); featuredVisible.value = false }
  catch (e) { ElMessage.error(e?.response?.data?.message || '申请失败') }
  finally { featuring.value = false }
}

onMounted(fetchDetail)
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
.card-title { font-size: 16px; font-weight: 600; color: #303133; }
.info-form { max-width: 100%; }
.form-actions { display: flex; justify-content: flex-end; padding-top: var(--space-4); border-top: 1px solid var(--el-border-color-lighter); }
.quill-wrapper { width: 100%; border-radius: 4px; }
.quill-wrapper :deep(.ql-toolbar) { border-radius: 4px 4px 0 0; background: #fafafa; }
.quill-wrapper :deep(.ql-container) { border-radius: 0 0 4px 4px; font-size: 14px; }
</style>