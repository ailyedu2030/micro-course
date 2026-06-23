<!--
  微专业管理（教师端）
  路由: /teacher/micro-specialties/:id/manage
-->
<template>
  <div class="ms-manage">
    <el-page-header @back="$router.back()" :content="detail?.title || '微专业管理'" class="mg-bottom-16" />

    <div v-loading="loading">
      <el-result
        v-if="error"
        icon="error"
        title="加载失败"
        sub-title="请稍后重试"
      >
        <template #extra>
          <el-button type="primary" @click="fetchDetail">重试</el-button>
        </template>
      </el-result>
      <el-empty v-else-if="!loading && !detail" description="微专业不存在" />

      <div v-if="detail" class="manage-body">
        <el-card shadow="never" class="mg-bottom-16">
          <template #header><span>基本信息</span></template>
          <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="info-form">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="标题" prop="title">
                  <el-input v-model="form.title" />
                </el-form-item>
                <el-form-item label="副标题">
                  <el-input v-model="form.subtitle" />
                </el-form-item>
                <el-form-item label="开课学院">
                  <el-input v-model="form.collegeName" disabled />
                </el-form-item>
                <el-form-item label="学期">
                  <el-input v-model="form.semester" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="封面 URL">
                  <el-input v-model="form.coverUrl" />
                </el-form-item>
                <el-form-item label="描述">
                  <el-input v-model="form.description" type="textarea" :rows="2" />
                </el-form-item>
                <el-form-item label="培养目标">
                  <el-input v-model="form.trainingObjective" type="textarea" :rows="2" />
                </el-form-item>
                <el-form-item label="准入门槛">
                  <el-input v-model="form.admissionRequirement" type="textarea" :rows="2" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-card>

        <el-card v-if="detail.stats" shadow="never" class="mg-bottom-16">
          <template #header><span>数据概览</span></template>
          <el-row :gutter="16">
            <el-col :span="6"><el-statistic title="选课人数" :value="detail.stats.totalEnrollments || 0" /></el-col>
            <el-col :span="6"><el-statistic title="课程数" :value="detail.stats.courseCount || 0" /></el-col>
            <el-col :span="6"><el-statistic title="完成人数" :value="detail.stats.completedCount || 0" /></el-col>
            <el-col :span="6"><el-statistic title="待审报名" :value="detail.stats.pendingEnrollmentCount || 0" /></el-col>
          </el-row>
        </el-card>

        <div class="action-bar">
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
          <el-button v-if="showSubmit" type="success" :loading="submitting" @click="handleSubmit">提交审核</el-button>
          <el-button v-if="showOpen" type="warning" :loading="actioning" @click="handleOpen">开课</el-button>
          <el-button v-if="showClose" type="danger" :loading="actioning" @click="handleClose">结业</el-button>
          <el-button @click="showFeaturedDialog">申请置顶</el-button>
          <el-button v-if="userStore.role === 'ACADEMIC'" type="danger" :loading="actioning" @click="handleCancel">取消微专业</el-button>
        </div>
      </div>
    </div>

    <!-- 申请置顶 Dialog -->
    <el-dialog v-model="featuredVisible" title="申请置顶" width="480px">
      <el-form :model="featuredForm" label-width="80px">
        <el-form-item label="申请理由">
          <el-input v-model="featuredForm.reason" type="textarea" :rows="3" placeholder="请填写申请置顶理由" />
        </el-form-item>
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
import { getMicroSpecialtyDetail, updateMicroSpecialty, submitMicroSpecialty, openMicroSpecialty, closeMicroSpecialty, cancelMicroSpecialty, applyFeatured, getStats } from '@/api/microSpecialty'

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

const featuredVisible = ref(false)
const featuring = ref(false)
const featuredForm = ref({ reason: '' })

const status = computed(() => detail.value?.status)
const showSubmit = computed(() => ['DRAFT', 'REJECTED'].includes(status.value))
const showOpen = computed(() => status.value === 'APPROVED')
const showClose = computed(() => status.value === 'RECRUITING')

const fetchDetail = async () => {
  error.value = false
  loading.value = true
  try {
    const { data: d } = await getMicroSpecialtyDetail(msId.value)
    detail.value = d
    form.value = { ...d }
    try {
      const { data: stats } = await getStats(msId.value)
      detail.value = { ...detail.value, stats }
    } catch { /* stats optional */ }
  } catch { error.value = true }
  finally { loading.value = false }
}

const handleSave = async () => {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  saving.value = true
  try {
    await updateMicroSpecialty(msId.value, form.value)
    ElMessage.success('保存成功')
    fetchDetail()
  } catch { ElMessage.error('保存失败') }
  finally { saving.value = false }
}

const handleSubmit = async () => {
  submitting.value = true
  try { await submitMicroSpecialty(msId.value); ElMessage.success('已提交审核'); fetchDetail() }
  catch { ElMessage.error('提交失败') }
  finally { submitting.value = false }
}

const handleOpen = async () => {
  actioning.value = true
  try { await openMicroSpecialty(msId.value); ElMessage.success('已开课'); fetchDetail() }
  catch { ElMessage.error('操作失败') }
  finally { actioning.value = false }
}

const handleClose = async () => {
  actioning.value = true
  try { await closeMicroSpecialty(msId.value); ElMessage.success('已结业'); fetchDetail() }
  catch { ElMessage.error('操作失败') }
  finally { actioning.value = false }
}

const handleCancel = async () => {
  try { await ElMessageBox.confirm('确定取消该微专业？此操作不可恢复。', '确认', { type: 'warning' }) }
  catch { return }
  actioning.value = true
  try { await cancelMicroSpecialty(msId.value); ElMessage.success('已取消'); fetchDetail() }
  catch { ElMessage.error('操作失败') }
  finally { actioning.value = false }
}

const showFeaturedDialog = () => { featuredForm.value.reason = ''; featuredVisible.value = true }
const handleFeatured = async () => {
  featuring.value = true
  try { await applyFeatured(msId.value, { reason: featuredForm.value.reason }); ElMessage.success('置顶申请已提交'); featuredVisible.value = false }
  catch { ElMessage.error('申请失败') }
  finally { featuring.value = false }
}

onMounted(fetchDetail)
</script>

<style scoped>
.ms-manage { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.manage-body { min-height: 300px; }
.info-form { max-width: 100%; }
.action-bar { display: flex; gap: var(--space-2); justify-content: flex-end; padding-top: var(--space-4); border-top: 1px solid var(--el-border-color-lighter); }
</style>
