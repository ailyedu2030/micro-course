<template>
  <el-dialog v-model="visible" :title="isEdit ? '编辑课时' : '新增课时'" width="500px" @close="handleClose">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="类型" prop="sectionType">
        <el-select v-model="form.sectionType" class="full-width">
          <el-option label="📹 视频课" value="VIDEO" />
          <el-option label="🎯 互动课件" value="INTERACTIVE" />
          <el-option label="🏫 线下课" value="OFFLINE" />
          <el-option label="📝 练习" value="EXERCISE" />
        </el-select>
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="form.sortOrder" :min="0" />
      </el-form-item>
      <el-form-item label="时长(分钟)">
        <el-input-number v-model="form.duration" :min="0" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">{{ isEdit ? '保存' : '新增' }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({ modelValue: Boolean, section: Object, isEdit: Boolean, loading: Boolean })
const emit = defineEmits(['update:modelValue', 'submit'])
const visible = ref(props.modelValue)
const formRef = ref(null)
const form = reactive({ title: '', sectionType: 'VIDEO', sortOrder: 0, duration: 0, description: '' })
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  sectionType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}
watch(() => props.modelValue, (v) => { visible.value = v })
watch(() => props.section, (s) => {
  if (s) Object.assign(form, {
    title: s.title || '', sectionType: s.sectionType || 'VIDEO',
    sortOrder: s.sortOrder ?? 0, duration: s.duration ?? 0, description: s.description || ''
  })
}, { immediate: true })
const handleClose = () => emit('update:modelValue', false)
const handleSubmit = async () => {
  await formRef.value.validate()
  emit('submit', { ...form })
  handleClose()
}
</script>
