<template>
  <el-dialog v-model="visible" title="讲述稿生成设置" width="540px" :close-on-click-modal="false" destroy-on-close>
    <el-form :model="form" :rules="rules" ref="formRef" label-width="110px" label-position="left">
      <el-form-item label="演讲人身份" prop="speakerIdentity">
        <el-input v-model="form.speakerIdentity" placeholder="例：大学英语教师、专业讲师" maxlength="200" show-word-limit />
        <div class="form-tip">AI 将以什么身份来讲述这段内容</div>
      </el-form-item>

      <el-form-item label="目标受众" prop="targetAudience">
        <el-input v-model="form.targetAudience" placeholder="例：专升本学生、大一新生" maxlength="200" show-word-limit />
        <div class="form-tip">讲述内容面向的对象</div>
      </el-form-item>

      <el-form-item label="讲述风格" prop="speakingStyle">
        <el-select v-model="form.speakingStyle" class="full-width">
          <el-option label="亲切自然，像在课堂上讲课" value="亲切自然，像在课堂上讲课" />
          <el-option label="专业严谨，注重学术表达" value="专业严谨，注重学术表达" />
          <el-option label="幽默风趣，用生动案例讲解" value="幽默风趣，用生动案例讲解" />
          <el-option label="简洁精炼，重点突出" value="简洁精炼，重点突出" />
          <el-option label="故事化叙事，引人入胜" value="故事化叙事，引人入胜" />
          <el-option label="自定义" value="__custom__" />
        </el-select>
        <el-input v-if="form.speakingStyle === '__custom__'" v-model="customStyle" placeholder="请输入自定义风格要求" maxlength="200" class="custom-style-input" show-word-limit />
        <div class="form-tip">AI 生成讲述稿的语气和表达方式</div>
      </el-form-item>

      <el-form-item label="总讲述时长" prop="totalDurationMinutes">
        <el-slider v-model="form.totalDurationMinutes" :min="3" :max="60" :step="1" show-input />
        <div class="form-tip">整个课件的总讲述时长（分钟），AI 会根据各页内容重要性自动分配时间。<br>重点页会多讲，简单页一带而过，形成有节奏的讲解</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存设置</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getNarrationSettings, updateNarrationSettings } from '../api/slide'

const props = defineProps({
  modelValue: Boolean,
  courseId: [Number, String]
})
const emit = defineEmits(['update:modelValue', 'saved'])

const visible = ref(props.modelValue)
watch(() => props.modelValue, v => visible.value = v)
watch(visible, v => emit('update:modelValue', v))

const formRef = ref(null)
const saving = ref(false)
const customStyle = ref('')

const form = reactive({
  speakerIdentity: '大学教师',
  targetAudience: '学生',
  speakingStyle: '亲切自然，像在课堂上讲课',
  totalDurationMinutes: 15
})

const rules = {
  speakerIdentity: [{ required: true, message: '请输入演讲人身份', trigger: 'blur' }],
  targetAudience: [{ required: true, message: '请输入目标受众', trigger: 'blur' }],
  speakingStyle: [{ required: true, message: '请选择演讲风格', trigger: 'change' }],
  totalDurationMinutes: [{ required: true, message: '请选择讲述时长', trigger: 'blur' }]
}

watch(() => visible.value, async (v) => {
  if (v && props.courseId) {
    try {
      const res = await getNarrationSettings(props.courseId)
      const data = res.data || {}
      form.speakerIdentity = data.speakerIdentity || '大学教师'
      form.targetAudience = data.targetAudience || '学生'
      form.speakingStyle = data.speakingStyle || '亲切自然，像在课堂上讲课'
      form.totalDurationMinutes = data.totalDurationMinutes || 15
    } catch { /* 使用默认值 */ }
  }
})

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = {
      speakerIdentity: form.speakerIdentity,
      targetAudience: form.targetAudience,
      speakingStyle: form.speakingStyle === '__custom__' ? customStyle.value : form.speakingStyle,
      totalDurationMinutes: form.totalDurationMinutes
    }
    await updateNarrationSettings(props.courseId, payload)
    ElMessage.success('讲述稿设置已保存')
    emit('saved', payload)
    visible.value = false
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.full-width { width: 100%; }
.custom-style-input { margin-top: 8px; }
.form-tip { font-size: 12px; color: #9ca3af; margin-top: 4px; line-height: 1.4; }
</style>
