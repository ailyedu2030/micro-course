<!--
  微专业申报（教师端）
  路由: /teacher/micro-specialties/proposals
-->
<template>
  <div class="ms-proposal-page">
    <el-page-header @back="$router.back()" content="微专业申报" class="mg-bottom-16" />

    <el-card shadow="never" class="proposal-card">
      <template #header><span>申报信息</span></template>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="proposal-form">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="微专业名称" />
        </el-form-item>
        <el-form-item label="说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="详细说明微专业的内容与意义" />
        </el-form-item>
        <el-form-item label="开课学院" prop="collegeId">
          <el-select v-model="form.collegeId" placeholder="选择学院" class="full-width">
            <el-option v-for="c in colleges" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="培养目标" prop="objectives">
          <el-input v-model="form.objectives" type="textarea" :rows="3" placeholder="描述培养目标与预期成果" />
        </el-form-item>
        <el-form-item label="建议学期">
          <el-input v-model="form.semester" placeholder="如 2025-2026-1" />
        </el-form-item>
        <el-form-item label="准入门槛">
          <el-input v-model="form.prerequisites" type="textarea" :rows="2" placeholder="选课前提条件（可选）" />
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

const router = useRouter()
const submitting = ref(false)
const formRef = ref(null)
const form = ref({ title: '', description: '', collegeId: null, objectives: '', semester: '', prerequisites: '' })
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入说明', trigger: 'blur' }],
  collegeId: [{ required: true, message: '请选择学院', trigger: 'change' }],
  objectives: [{ required: true, message: '请输入培养目标', trigger: 'blur' }]
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

onMounted(() => { /* colleges could be loaded if needed */ })
</script>

<style scoped>
.ms-proposal-page { padding: var(--space-4); max-width: 800px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.full-width { width: 100%; }
.proposal-card { min-height: 400px; }
.proposal-form { max-width: 100%; }
.submit-bar { display: flex; justify-content: flex-end; margin-top: var(--space-4); padding-top: var(--space-4); border-top: 1px solid var(--el-border-color-lighter); }
</style>
