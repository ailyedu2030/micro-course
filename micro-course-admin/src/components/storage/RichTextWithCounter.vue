<template>
  <div class="rich-text-counter">
    <div class="quill-wrapper">
      <QuillEditor
        ref="quillRef"
        :content="modelValue"
        content-type="html"
        toolbar="essential"
        :placeholder="placeholder"
        :style="{ minHeight: minHeight + 'px' }"
        @update:content="$emit('update:modelValue', $event)"
      />
    </div>
    <div class="word-count" :class="{ warning: count > effectiveWarningThreshold, error: count > maxThreshold }">
      字数：{{ count }} / {{ maxThreshold > 0 ? '最多' + maxThreshold : '建议≤' + recommendThreshold }}
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onBeforeUnmount } from 'vue'
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'

const quillRef = ref(null)

const props = defineProps({
  modelValue: String,
  placeholder: { type: String, default: '' },
  minHeight: { type: Number, default: 140 },
  recommendThreshold: { type: Number, default: 0 },
  warningThreshold: { type: Number, default: 0 },
  maxThreshold: { type: Number, default: 0 }
})

defineEmits(['update:modelValue'])

// R-004: 组件卸载时显式清理 QuillEditor 实例
onBeforeUnmount(() => {
  try {
    const quill = quillRef.value?.getQuill()
    if (quill) {
      quill.destroy()
    }
  } catch (e) {
    // 静默处理，防止卸载时异常
  }
})

const count = computed(() => {
  if (!props.modelValue) return 0
  return props.modelValue.replace(/<[^>]*>/g, '').replace(/&[\w;]+/g, ' ').replace(/\s+/g, '').length
})

const effectiveWarningThreshold = computed(() => {
  // 当 warningThreshold 未传值（保持默认0）时，使用 recommendThreshold
  // 当 warningThreshold 被显式传入0时，0是合法值，不覆盖
  if (props.warningThreshold > 0) return props.warningThreshold
  return props.recommendThreshold
})
</script>

<style scoped>
.quill-wrapper { width: 100%; border-radius: 4px; }
.quill-wrapper :deep(.ql-toolbar) { border-radius: 4px 4px 0 0; background: #fafafa; }
.quill-wrapper :deep(.ql-container) { border-radius: 0 0 4px 4px; font-size: 14px; }
.word-count { text-align: right; font-size: 12px; color: #909399; margin-top: 4px; }
.word-count.warning { color: #e6a23c; }
.word-count.error { color: #f56c6c; }
</style>
