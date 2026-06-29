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
        <el-form-item label="班级" prop="classIds">
          <el-select v-model="form.classIds" multiple filterable :placeholder="form.microSpecialtyId ? '请选择班级（可多选）' : '请先选择微专业'" class="full-width" :loading="loadingClasses" :disabled="!form.microSpecialtyId">
            <el-option v-for="c in classOptions" :key="c.id" :label="`${c.name} (${c.majorName || ''} ${c.studentCount || 0}人)`" :value="c.id" />
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
        <el-col :span="8"><el-result icon="success" title="成功" :sub-title="`${result.successCount || 0} 人`" /></el-col>
        <el-col :span="8"><el-result icon="info" title="跳过" :sub-title="`${result.skipCount || 0} 人`" /></el-col>
        <el-col :span="8"><el-result icon="warning" title="待处理" :sub-title="`${result.pendingCount || 0} 人`" /></el-col>
      </el-row>
      <div v-if="result.errors && result.errors.length" class="error-list mg-top-12">
        <h4>失败详情</h4>
        <div v-for="(err, i) in result.errors" :key="i" class="error-item">{{ err }}</div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMicroSpecialtyList, classImport } from '@/api/microSpecialty'

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
const result = ref(null)

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
    // Load classes for selected specialty via microSpecialty enrollments API
    // For now, classOptions come from the parent context or a separate API
  } catch (e) { ElMessage.error(e?.response?.data?.message || '加载班级列表失败') }
  finally { loadingClasses.value = false }
}

const handleImport = async () => {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  importing.value = true
  try {
    const { data } = await classImport({ microSpecialtyId: form.value.microSpecialtyId, classIds: form.value.classIds })
    result.value = typeof data === 'number' ? { successCount: data, skipCount: 0, pendingCount: 0, errors: [] } : data
    ElMessage.success('导入完成')
  } catch (e) { ElMessage.error(e?.response?.data?.message || '导入失败') }
  finally { importing.value = false }
}

onMounted(fetchSpecialties)
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
