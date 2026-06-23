<!--
  微专业申报（教师端）
  路由: /teacher/micro-specialties/proposals
-->
<template>
  <div class="ms-proposal-page">
    <el-page-header @back="$router.back()" content="微专业申报" class="mg-bottom-16" />

    <el-card shadow="never" class="proposal-card" v-loading="loading">
      <template #header><span>申报信息</span></template>
      <el-result
        v-if="error"
        icon="error"
        title="加载失败"
        sub-title="学院信息加载失败，请稍后重试"
      >
        <template #extra>
          <el-button type="primary" @click="loadColleges">重试</el-button>
        </template>
      </el-result>
      <el-form v-else-if="!loading" ref="formRef" :model="form" :rules="rules" label-width="100px" class="proposal-form" @submit.prevent>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="微专业名称" />
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="详细说明微专业的内容与意义" />
        </el-form-item>
        <el-form-item label="开课学院" prop="offerDepartmentId">
          <el-select v-model="form.offerDepartmentId" placeholder="选择学院" class="full-width">
            <el-option v-for="c in colleges" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="培养目标" prop="trainingObjective">
          <el-input v-model="form.trainingObjective" type="textarea" :rows="3" placeholder="描述培养目标与预期成果" />
        </el-form-item>
        <el-form-item label="建议学期">
          <el-input v-model="form.semester" placeholder="如 2025-2026-1" />
        </el-form-item>
        <el-form-item label="准入门槛">
          <el-input v-model="form.prerequisites" type="textarea" :rows="2" placeholder="选课前提条件（可选）" />
        </el-form-item>
        <el-form-item label="总学分" prop="credits">
          <el-input-number v-model="form.credits" :min="0" :max="100" placeholder="总学分" />
        </el-form-item>
      </el-form>
      <div class="submit-bar">
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交申报</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { submitProposal } from '@/api/microSpecialty'
import { getDepartments } from '@/api/department'

const router = useRouter()
const error = ref(false)
const loading = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const form = ref({ title: '', description: '', offerDepartmentId: null, trainingObjective: '', semester: '', prerequisites: '', credits: null })
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入说明', trigger: 'blur' }],
  offerDepartmentId: [{ required: true, message: '请选择学院', trigger: 'change' }],
  trainingObjective: [{ required: true, message: '请输入培养目标', trigger: 'blur' }]
}
const colleges = ref([])

const handleSubmit = async () => {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  submitting.value = true
  try { await submitProposal(form.value); ElMessage.success('申报已提交'); router.push('/teacher/micro-specialties/my-proposals') }
  catch { ElMessage.error('提交失败') }
  finally { submitting.value = false }
}

const loadColleges = async () => {
    error.value = false
    loading.value = true
    try {
      const res = await getDepartments()
      colleges.value = res.data.items || res.data || []
    } catch { error.value = true }
    finally { loading.value = false }
}

onMounted(() => { loadColleges() })
</script>

<style scoped>
.ms-proposal-page { padding: var(--space-4); max-width: 800px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.full-width { width: 100%; }
.proposal-card { min-height: 400px; }
.proposal-form { max-width: 100%; }
.submit-bar { display: flex; justify-content: flex-end; margin-top: var(--space-4); padding-top: var(--space-4); border-top: 1px solid var(--el-border-color-lighter); }
</style>
