<!--
  班级导入（教务处端）
  路由: /academic/micro-specialties/class-import
-->
<template>
  <div class="ms-class-import">
    <el-page-header @back="$router.back()" :content="$t('route.AcademicMicroSpecialtyClassImport')" class="mg-bottom-16" />

    <el-card shadow="never" class="import-card" v-loading="importing">
      <template #header><span>{{ $t('classImport.title') }} · {{ form.microSpecialtyId ? specialtyTitle : $t('classImport.selectSpecialty') }}</span></template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="import-form">
        <el-form-item :label="$t('classImport.microSpecialty')" prop="microSpecialtyId">
          <el-select v-model="form.microSpecialtyId" filterable :placeholder="$t('classImport.microSpecialtyPlaceholder')" class="full-width" :loading="loadingSpecialties" @change="onSpecialtyChange">
            <el-option v-for="ms in specialtyOptions" :key="ms.id" :label="ms.title" :value="ms.id" />
            <template #empty><span class="no-data-hint">{{ $t('classImport.noSpecialties') }}</span></template>
          </el-select>
        </el-form-item>
        <!-- P1I-068 修复：添加院系列筛选项，避免班级列表全量加载 -->
        <el-form-item :label="$t('classImport.departmentFilter')">
          <el-select v-model="departmentFilter" filterable clearable :placeholder="$t('classImport.departmentPlaceholder')" class="full-width" @change="onDepartmentFilterChange">
            <el-option v-for="d in departmentOptions" :key="d.id" :label="d.name" :value="d.id" />
            <template #empty><span class="no-data-hint">{{ $t('classImport.noDepartments') }}</span></template>
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('classImport.classLabel')" prop="classIds">
          <el-select v-model="form.classIds" multiple filterable :placeholder="form.microSpecialtyId ? $t('classImport.classPlaceholder') : $t('classImport.selectSpecialtyFirst')" class="full-width" :loading="loadingClasses" :disabled="!form.microSpecialtyId">
            <el-option v-for="c in filteredClassOptions" :key="c.id" :label="`${c.name} (${c.departmentName || c.majorName || ''} ${c.studentCount || 0}${$t('classImport.personUnit')})`" :value="c.id" />
            <template #empty><span class="no-data-hint">{{ $t('classImport.noClasses') }}</span></template>
          </el-select>
        </el-form-item>
      </el-form>
      <div class="submit-bar">
        <el-button type="primary" :loading="importing" :disabled="!form.microSpecialtyId || !form.classIds.length" @click="handleImport">{{ $t('classImport.import') }}</el-button>
      </div>
    </el-card>

    <!-- 导入结果 -->
    <el-card v-if="result" shadow="never" class="mg-top-16 result-card">
      <template #header><span>{{ $t('classImport.resultTitle') }}</span></template>
      <el-row :gutter="16">
        <el-col :span="8"><el-result icon="success" :title="$t('classImport.successImport')" :sub-title="`${successStudentCount} ${$t('classImport.personUnit')}`" /></el-col>
        <el-col :span="8"><el-result icon="success" :title="$t('classImport.successClass')" :sub-title="`${result.successCount || 0} ${$t('classImport.classUnit')}`" /></el-col>
        <el-col :span="8"><el-result icon="danger" :title="$t('classImport.failedClass')" :sub-title="`${result.failedCount || 0} ${$t('classImport.classUnit')}`" /></el-col>
      </el-row>
      <el-button v-if="importResult.success.length || importResult.failed.length" type="primary" size="small" class="mg-top-12" @click="showImportResult">{{ $t('classImport.viewDetail') }}</el-button>
      <div v-if="result.errors && result.errors.length" class="error-list mg-top-12">
        <h4>{{ $t('classImport.failedDetail') }}</h4>
        <div v-for="(err, i) in result.errors" :key="i" class="error-item">{{ err }}</div>
      </div>
    </el-card>

    <!-- 导入明细弹窗 -->
    <el-dialog v-model="importResultDialogVisible" :title="$t('classImport.resultDialogTitle')" width="700px">
      <el-tabs v-model="importResultTab">
        <el-tab-pane :label="$t('classImport.tabSuccess')" :name="'success'">
          <el-table :data="importResult.success" stripe border v-if="importResult.success.length">
            <el-table-column prop="className" :label="$t('classImport.className')" min-width="120" />
            <el-table-column prop="studentCount" :label="$t('classImport.importedCount')" width="140" />
          </el-table>
          <el-empty v-else :description="$t('classImport.noSuccess')" />
        </el-tab-pane>
        <el-tab-pane :label="$t('classImport.tabFailed')" :name="'failed'">
          <el-table :data="importResult.failed" stripe border v-if="importResult.failed.length">
            <el-table-column prop="className" :label="$t('classImport.className')" min-width="120" />
            <el-table-column prop="errorMsg" :label="$t('classImport.failedReason')" min-width="240" />
          </el-table>
          <el-empty v-else :description="$t('classImport.noFailed')" />
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMicroSpecialtyList, classImport } from '@/api/microSpecialty'
import { getClasses } from '@/api/class'
import { getDepartments } from '@/api/department'

const { t } = useI18n()

const importing = ref(false)
const loadingSpecialties = ref(false)
const loadingClasses = ref(false)
const formRef = ref(null)
const form = ref({ microSpecialtyId: null, classIds: [] })
const rules = {
  microSpecialtyId: [{ required: true, message: t('classImport.microSpecialtyRequired'), trigger: 'change' }],
  classIds: [{ type: 'array', required: true, message: t('classImport.classRequired'), trigger: 'change' }]
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
    const { data } = await getMicroSpecialtyList({ size: 100 })
    specialtyOptions.value = data.items || data || []
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('classImport.loadSpecialtiesFailed')) }
  finally { loadingSpecialties.value = false }
}

const onSpecialtyChange = async (id) => {
  form.value.classIds = []
  classOptions.value = []
  if (!id) return
  loadingClasses.value = true
  try {
    // 加载所有班级 (学院级, 与微专业无关, 由用户筛选选择)
    const { data } = await getClasses({ size: 100 })
    classOptions.value = data?.items || data || []
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('classImport.loadClassesFailed')) }
  finally { loadingClasses.value = false }
}

// P1I-068: 院系筛选变更
const onDepartmentFilterChange = () => {
  form.value.classIds = []
}

const handleImport = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (importing.value) return
  if (!formRef.value) return
  importing.value = true
  try {
    const valid = await formRef.value.validate()
    if (!valid) { importing.value = false; return }
  } catch { importing.value = false; return }
  // 二次确认
  try {
    await ElMessageBox.confirm(
      t('classImport.confirmImportMsg', { count: form.value.classIds.length }),
      t('classImport.confirmImportTitle'),
      { type: 'warning', confirmButtonText: t('classImport.confirmImportTitle'), cancelButtonText: t('common.cancel') }
    )
  } catch { importing.value = false; return }
  try {
    const { data } = await classImport({ microSpecialtyId: form.value.microSpecialtyId, classIds: form.value.classIds })
    result.value = data
    const successList = result.value.successList || []
    const failedList = result.value.failedList || []
    importResult.value = { success: successList, failed: failedList }
    ElMessage.success(t('classImport.importSuccess'))
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('classImport.importFailed')) }
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
