<template>
  <div class="rich-text-counter">
    <el-form-item v-if="label" :label="label" class="rich-form-item">
      <el-input
        :model-value="modelValue"
        type="textarea"
        :rows="6"
        :placeholder="placeholder"
        :readonly="readonly"
        :maxlength="maxChars"
        @input="handleInput"
      />
      <div class="word-count" :class="wordCountClass">
        {{ plainCount }} / {{ maxChars }}
        <span v-if="minChars && plainCount > 0 && plainCount < minChars" class="min-hint">（建议至少 {{ minChars }} 字）</span>
      </div>
    </el-form-item>
    <template v-else>
      <el-input
        :model-value="modelValue"
        type="textarea"
        :rows="6"
        :placeholder="placeholder"
        :readonly="readonly"
        :maxlength="maxChars"
        @input="handleInput"
      />
      <div class="word-count" :class="wordCountClass">
        {{ plainCount }} / {{ maxChars }}
        <span v-if="minChars && plainCount > 0 && plainCount < minChars" class="min-hint">（建议至少 {{ minChars }} 字）</span>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  maxChars: { type: Number, default: 5000 },
  warningThreshold: { type: Number, default: 0 },
  placeholder: { type: String, default: '' },
  readonly: { type: Boolean, default: false },
  label: { type: String, default: '' },
  minChars: { type: Number, default: 0 }
})

const emit = defineEmits(['update:modelValue'])

const plainCount = computed(() => {
  if (!props.modelValue) return 0
  return props.modelValue
    .replace(/<[^>]*>/g, '')
    .replace(/&[\w;]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .length
})

const effectiveWarningThreshold = computed(() => {
  if (props.warningThreshold > 0) return props.warningThreshold
  return Math.floor(props.maxChars * 0.9)
})

const wordCountClass = computed(() => {
  if (plainCount.value > props.maxChars) return 'error'
  if (plainCount.value > effectiveWarningThreshold.value) return 'warning'
  return ''
})

function handleInput(value) {
  emit('update:modelValue', value)
}
</script>

<style scoped>
.rich-text-counter {
  width: 100%;
}
.rich-text-counter :deep(.el-textarea__inner) {
  font-size: 14px;
  line-height: 1.6;
}
.word-count {
  text-align: right;
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1;
}
.word-count.warning {
  color: #e6a23c;
}
.word-count.error {
  color: #f56c6c;
  font-weight: 600;
}
.min-hint {
  color: #909399;
  font-weight: 400;
}
.rich-form-item {
  width: 100%;
}
</style>
