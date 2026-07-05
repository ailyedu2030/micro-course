<!--
  班级导入（教务处端）
  路由: /academic/micro-specialties/class-import
-->
<template>
  <div class="ms-class-import">
    <el-page-header @back="$router.back()" content="班级导入" class="mg-bottom-16" />

    <el-card shadow="never" class="import-card" v-loading="importing">
      <template #header><span>班级导入 · {{ form.microSpecialtyId ? specialtyTitle : '选择微专业' }}</span></template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="import-form">
        <el-form-item label="微专业" prop="microSpecialtyId">
          <el-select v-model="form.microSpecialtyId" filterable placeholder="请选择微专业" class="full-width" :loading="loadingSpecialties" @change="onSpecialtyChange">
            <el-option v-for="ms in specialtyOptions" :key="ms.id" :label="ms.title" :value="ms.id" />
            <template #empty><span class="no-data-hint">暂无可用微专业</span></template>
          </el-select>
        </el-form-item>
        <!-- P1I-068 修复：添加院系列筛选项，避免班级列表全量加载 -->
        <el-form-item label="院系列筛">
          <el-select v-model="departmentFilter" filterable clearable placeholder="请选择院系（筛选）" class="full-width" @change="onDepartmentFilterChange">
            <el-option v-for="d in departmentOptions" :key="d.id" :label="d.name" :value="d.id" />
            <template #empty><span class="no-data-hint">暂无院系</span></template>
          </el-select>
        </el-form-item>
        <el-form-item label="班级" prop="classIds">
          <el-select v-model="form.classIds" multiple filterable :placeholder="form.microSpecialtyId ? '请选择班级（可多选）' : '请先选择微专业'" class="full-width" :loading="loadingClasses" :disabled="!form.microSpecialtyId">
            <el-option v-for="c in filteredClassOptions" :key="c.id" :label="`${c.name} (${c.departmentName || c.majorName || ''} ${c.studentCount || 0}人)`" :value="c.id" />
            <template #empty><span class="no-data-hint">暂无可用班级</span></template>
          </el-select>
        </el-form-item>
      </el-form>
      <div class="submit-bar">
        <el-button type="primary" :loading="importing" :disabled="!form.microSpecialtyId || !form.classIds.length" @click="handleImport">导入</el-button>
      </div>
    </el-card>

    <!-- 导入结果 -->
    <el-card v-if="result" shadow="never" class="mg-top-16 result-card">
      <template #header><span>导入结果</span></template>
      <el-row :gutter="16">
        <el-col :span="8"><el-result icon="success" title="成功导入" :sub-title="`${successStudentCount} 人`" /></el-col>
        <el-col :span="8"><el-result icon="success" title="成功班级" :sub-title="`${result.successCount || 0} 个`" /></el-col>
        <el-col :span="8"><el-result icon="danger" title="失败班级" :sub-title="`${result.failedCount || 0} 个`" /></el-col>
      </el-row>
      <el-button v-if="importResult.success.length || importResult.failed.length" type="primary" size="small" class="mg-top-12" @click="showImportResult">查看班级明细</el-button>
      <div v-if="result.errors && result.errors.length" class="error-list mg-top-12">
        <h4>失败详情</h4>
        <div v-for="(err, i) in result.errors" :key="i" class="error-item">{{ err }}</div>
      </div>
    </el-card>

    <!-- 导入明细弹窗 -->
    <el-dialog v-model="importResultDialogVisible" title="导入结果明细" width="700px">
      <el-tabs v-model="importResultTab">
        <el-tab-pane label="成功" :name="'success'">
          <el-table :data="importResult.success" stripe border v-if="importResult.success.length">
            <el-table-column prop="className" label="班级" min-width="120" />
            <el-table-column prop="studentCount" label="成功导入人数" width="140" />
          </el-table>
          <el-empty v-else description="暂无成功记录" />
        </el-tab-pane>
        <el-tab-pane label="失败" :name="'failed'">
          <el-table :data="importResult.failed" stripe border v-if="importResult.failed.length">
            <el-table-column prop="className" label="班级" min-width="120" />
            <el-table-column prop="errorMsg" label="失败原因" min-width="240" />
          </el-table>
          <el-empty v-else description="暂无失败记录" />
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMicroSpecialtyList, classImport } from '@/api/microSpecialty'
import { getClasses } from '@/api/class'
import { getDepartments } from '@/api/department'

const importing = ref(false)
const loadingSpecialties = ref(false)
const loadingClasses = ref(false)
const formRef = ref(null)
const form = ref({ microSpecialtyId: null, classIds: [] })
const rules = {
  microSpecialtyId: [{ required: true, message: '请选择微专业', trigger: 'change' }],
  classIds: [{ type: 'array', required: true, message: '请至少选择一个班级', trigger: 'change' }]
}
const specialtyOptions = ref([])
const classOptions = ref([])

// P1I-068: 院系列筛
const departmentFilter = ref(null)
const departmentOptions = ref([])
const filteredClassOptions = computed(() => {
  if (!departmentFilter.value) return classOptions.value
  return classOptions.value.filter(c => c.departmentId === departmentFilter.value || c.departmentName === departmentOptions.value.find(d => d.id === departmentFilter.value)?.name)
})
const result = ref(null)
const importResultDialogVisible = ref(false)
const importResultTab = ref('success')
const importResult = ref({ success: [], failed: [] })
const successStudentCount = computed(() => {
  if (!result.value) return 0
  const list = result.value.successList || []
  return list.reduce((sum, item) => sum + (item.studentCount || 0), 0)
})

const showImportResult = () => {
  importResultDialogVisible.value = true
}

const specialtyTitle = computed(() => {
  const s = specialtyOptions.value.find(o => o.id === form.value.microSpecialtyId)
  return s ? s.title : ''
})

const fetchSpecialties = async () => {
  loadingSpecialties.value = true
  try {
    const { data } = await getMicroSpecialtyList({ size: 200 })
    specialtyOptions.value = data.items || data || []
  } catch (e) { ElMessage.error(e?.response?.data?.message || '加载微专业列表失败') }
  finally { loadingSpecialties.value = false }
}

const onSpecialtyChange = async (id) => {
  form.value.classIds = []
  classOptions.value = []
  if (!id) return
  loadingClasses.value = true
  try {
    // 加载所有班级 (学院级, 与微专业无关, 由用户筛选选择)
    const { data } = await getClasses({ size: 1000 })
    classOptions.value = data?.items || data || []
  } catch (e) { ElMessage.error(e?.response?.data?.message || '加载班级列表失败') }
  finally { loadingClasses.value = false }
}

// P1I-068: 院系筛选变更
const onDepartmentFilterChange = () => {
  form.value.classIds = []
}

const handleImport = async () => {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  // 二次确认
  try {
    await ElMessageBox.confirm(
      `确认将 ${form.value.classIds.length} 个班级导入该微专业？此操作会创建修读记录。`,
      '确认导入',
      { type: 'warning', confirmButtonText: '确认导入', cancelButtonText: '取消' }
    )
  } catch { return }
  importing.value = true
  try {
    const { data } = await classImport({ microSpecialtyId: form.value.microSpecialtyId, classIds: form.value.classIds })
    result.value = data
    const successList = result.value.successList || []
    const failedList = result.value.failedList || []
    importResult.value = { success: successList, failed: failedList }
    ElMessage.success('导入完成')
  } catch (e) { ElMessage.error(e?.response?.data?.message || '导入失败') }
  finally { importing.value = false }
}

// P1I-068: 加载院系列表
const fetchDepartments = async () => {
  try {
    const { data } = await getDepartments()
    departmentOptions.value = data?.items || data || []
  } catch { /* 院系加载失败不影响主流程 */ }
}

onMounted(() => {
  fetchSpecialties()
  fetchDepartments()
})
</script>

<style scoped>
.ms-class-import { padding: var(--space-4); max-width: 800px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.mg-top-16 { margin-top: var(--space-4); }
.mg-top-12 { margin-top: var(--space-3); }
.full-width { width: 100%; }
.import-card { min-height: 200px; }
.import-form { max-width: 100%; }
.submit-bar { display: flex; justify-content: flex-end; margin-top: var(--space-4); padding-top: var(--space-4); border-top: 1px solid var(--el-border-color-lighter); }
.result-card { border-color: var(--el-color-success-light-5); }
.error-list { background: var(--el-color-danger-light-9); padding: var(--space-3); border-radius: var(--el-border-radius-base); }
.error-list h4 { margin: 0 0 var(--space-2); color: var(--el-color-danger); }
.error-item { font-size: var(--el-font-size-extra-small); color: var(--el-text-color-secondary); padding: 2px 0; }
.no-data-hint { color: var(--el-text-color-placeholder); font-size: var(--el-font-size-small); padding: 8px; display: block; text-align: center; }
</style>
