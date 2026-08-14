<template>
  <el-dialog v-model="visible" :title="t('narration.dialog.title')" width="540px" :close-on-click-modal="false" destroy-on-close>
    <el-form :model="form" :rules="rules" ref="formRef" label-width="110px" label-position="left">
      <el-form-item :label="t('narration.dialog.speakerIdentity')" prop="speakerIdentity">
        <el-input v-model="form.speakerIdentity" :placeholder="t('narration.dialog.speakerPlaceholder')" maxlength="200" show-word-limit />
        <div class="form-tip">{{ t('narration.dialog.speakerTip') }}</div>
      </el-form-item>

      <el-form-item :label="t('narration.dialog.targetAudience')" prop="targetAudience">
        <el-input v-model="form.targetAudience" :placeholder="t('narration.dialog.audiencePlaceholder')" maxlength="200" show-word-limit />
        <div class="form-tip">{{ t('narration.dialog.audienceTip') }}</div>
      </el-form-item>

      <el-form-item :label="t('narration.dialog.speakingStyle')" prop="speakingStyle">
        <el-select v-model="form.speakingStyle" class="full-width">
          <el-option :label="t('narration.dialog.styleNatural')" value="亲切自然，像在课堂上讲课" />
          <el-option :label="t('narration.dialog.styleAcademic')" value="专业严谨，注重学术表达" />
          <el-option :label="t('narration.dialog.styleHumorous')" value="幽默风趣，用生动案例讲解" />
          <el-option :label="t('narration.dialog.styleConcise')" value="简洁精炼，重点突出" />
          <el-option :label="t('narration.dialog.styleStory')" value="故事化叙事，引人入胜" />
          <el-option :label="t('narration.dialog.styleCustom')" value="__custom__" />
        </el-select>
        <el-input v-if="form.speakingStyle === '__custom__'" v-model="customStyle" :placeholder="t('narration.dialog.customStylePlaceholder')" maxlength="200" class="custom-style-input" show-word-limit />
        <div class="form-tip">{{ t('narration.dialog.styleTip') }}</div>
      </el-form-item>

      <el-form-item :label="t('narration.dialog.totalDuration')" prop="totalDurationMinutes">
        <el-slider v-model="form.totalDurationMinutes" :min="3" :max="60" :step="1" show-input />
        <div class="form-tip">{{ t('narration.dialog.durationTip1') }}<br>{{ t('narration.dialog.durationTip2') }}</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">{{ t('narration.dialog.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">{{ t('narration.dialog.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getNarrationSettings, updateNarrationSettings } from '../api/slide'

const { t } = useI18n()

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  courseId: { type: [Number, String], default: null }
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
  speakerIdentity: [{ required: true, message: t('narration.dialog.speakerRequired'), trigger: 'blur' }],
  targetAudience: [{ required: true, message: t('narration.dialog.audienceRequired'), trigger: 'blur' }],
  speakingStyle: [{ required: true, message: t('narration.dialog.styleRequired'), trigger: 'change' }],
  totalDurationMinutes: [{ required: true, message: t('narration.dialog.durationRequired'), trigger: 'change' }]
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
    } catch (e) { console.warn('[NarrationSettings]', t('narration.dialog.loadFailed'), e) }
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
    ElMessage.success(t('narration.dialog.saved'))
    emit('saved', payload)
    visible.value = false
  } catch (e) {
    ElMessage.error(t('narration.dialog.saveFailed'))
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
