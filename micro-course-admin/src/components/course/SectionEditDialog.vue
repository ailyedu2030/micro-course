<template>
  <el-dialog v-model="visible" :title="isEdit ? '编辑课时' : '新增课时'" width="500px" @close="handleClose">
    <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="200" show-word-limit />
      </el-form-item>
      <el-form-item label="类型" prop="sectionType">
        <!-- F-2026-08-10-17: 5 种课件类型在章节维度独立选项。
             PPT/HTML 内部用 coursewareType 区分（sectionType 保持 INTERACTIVE 技术值），
             与后端 SectionCreateRequest @Pattern(coursewareType=HTML|PPT) 对齐。 -->
        <el-select
          v-model="form.sectionType"
          class="full-width"
          @change="onTypeChange"
        >
          <el-option label="📹 视频课件" value="VIDEO" />
          <el-option label="📄 PPT 课件" value="INTERACTIVE_PPT" />
          <el-option label="📄 互动课件（HTML 课件）" value="INTERACTIVE_HTML" />
          <el-option label="🏫 线下课程" value="OFFLINE" />
          <el-option label="📝 练习课件" value="EXERCISE" />
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
const form = reactive({ title: '', sectionType: 'VIDEO', coursewareType: null, sortOrder: 0, duration: 0, description: '' })
const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  sectionType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}
watch(() => props.modelValue, (v) => { visible.value = v })

// F-2026-08-10-17: sectionType + coursewareType 双向映射
// INTERACTIVE + coursewareType=PPT → 下拉 INTERACTIVE_PPT；HTML → INTERACTIVE_HTML
function sectionTypeToOption(sectionType, coursewareType) {
  if (sectionType === 'INTERACTIVE') {
    return coursewareType === 'PPT' ? 'INTERACTIVE_PPT' : 'INTERACTIVE_HTML'
  }
  return sectionType || 'VIDEO'
}
function optionToSectionType(option) {
  if (option === 'INTERACTIVE_PPT') return { sectionType: 'INTERACTIVE', coursewareType: 'PPT' }
  if (option === 'INTERACTIVE_HTML') return { sectionType: 'INTERACTIVE', coursewareType: 'HTML' }
  return { sectionType: option, coursewareType: null }
}
function onTypeChange(val) {
  const { coursewareType } = optionToSectionType(val)
  form.coursewareType = coursewareType
}

watch(() => props.section, (s) => {
  if (s) Object.assign(form, {
    title: s.title || '',
    sectionType: sectionTypeToOption(s.sectionType, s.coursewareType),
    coursewareType: s.coursewareType || null,
    sortOrder: s.sortOrder ?? 0, duration: s.duration ?? 0, description: s.description || ''
  })
}, { immediate: true })

const handleClose = () => emit('update:modelValue', false)
const handleSubmit = async () => {
  // P1-C 修复: 原裸 await validate() 无 try/catch → 校验失败时产生
  // unhandled promise rejection
  try {
    await formRef.value.validate()
  } catch {
    return // 校验错误消息已由 Element Plus 展示
  }
  // F-2026-08-10-17: 提交时把下拉复合值映射回 sectionType + coursewareType
  const { sectionType, coursewareType } = optionToSectionType(form.sectionType)
  emit('submit', { ...form, sectionType, coursewareType })
  handleClose()
}
</script>
