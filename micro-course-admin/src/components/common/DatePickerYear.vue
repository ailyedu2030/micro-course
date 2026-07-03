<template>
  <div class="date-picker-year">
    <el-date-picker
      :model-value="pickerValue"
      type="year"
      :placeholder="placeholder"
      :disabled="disabled"
      :disabled-date="disabledDate"
      value-format="YYYY"
      format="YYYY"
      style="width: 140px"
      @update:model-value="handleChange"
    />
    <div v-if="validationMessage" class="validation-hint" :class="{ error: !yearInRange }">
      {{ validationMessage }}
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Number, default: null },
  label: { type: String, default: '' },
  minYear: { type: Number, default: 2024 },
  maxYear: { type: Number, default: new Date().getFullYear() + 10 },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '选择年份' }
})

const emit = defineEmits(['update:modelValue'])

const pickerValue = computed(() => {
  if (!props.modelValue) return null
  return String(props.modelValue)
})

const yearInRange = computed(() => {
  if (!props.modelValue) return true
  return props.modelValue >= props.minYear && props.modelValue <= props.maxYear
})

const validationMessage = computed(() => {
  if (!props.modelValue) return ''
  if (props.modelValue < props.minYear) {
    return `年份不能早于 ${props.minYear} 年`
  }
  if (props.modelValue > props.maxYear) {
    return `年份不能晚于 ${props.maxYear} 年`
  }
  return ''
})

function disabledDate(time) {
  const year = time.getFullYear()
  return year < props.minYear || year > props.maxYear
}

function handleChange(val) {
  if (!val) {
    emit('update:modelValue', null)
    return
  }
  const year = parseInt(val, 10)
  emit('update:modelValue', year)
}
</script>

<style scoped>
.date-picker-year {
  display: inline-flex;
  flex-direction: column;
  gap: 4px;
}
.validation-hint {
  font-size: 12px;
  color: #e6a23c;
  line-height: 1;
}
.validation-hint.error {
  color: #f56c6c;
}
</style>
