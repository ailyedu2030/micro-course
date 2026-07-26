<template>
  <div class="dynamic-table-editor">
    <CommonDynamicTableEditor
      v-model="localData"
      :columns="normalizedColumns"
      :min-rows="0"
      :max-rows="resolvedMaxRows"
    />
    <div v-if="localData.length > 0" class="table-actions">
      <el-button size="small" @click="clearAll">清空</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import CommonDynamicTableEditor from '../common/DynamicTableEditor.vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  columns: { type: Array, required: true },
  defaultRow: { type: Object, default: () => ({}) },
  maxRows: { type: Number, default: 0 },
  showSummary: { type: Boolean, default: false },
  summaryText: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'change', 'summary-change'])

const localData = ref(JSON.parse(JSON.stringify(props.modelValue || [])))
const resolvedMaxRows = computed(() => (props.maxRows > 0 ? props.maxRows : Number.MAX_SAFE_INTEGER))
const normalizedColumns = computed(() => {
  return props.columns.map((col) => {
    const fieldKey = col.key ?? col.prop
    return {
      ...col,
      key: fieldKey,
      defaultValue: col.defaultValue !== undefined ? col.defaultValue : props.defaultRow[fieldKey]
    }
  })
})

watch(() => props.modelValue, (v) => {
  localData.value = JSON.parse(JSON.stringify(v || []))
}, { deep: true })

watch(localData, (value) => {
  const cloned = JSON.parse(JSON.stringify(value || []))
  emit('update:modelValue', cloned)
  emit('change', cloned)
  if (props.showSummary) {
    emit('summary-change', {
      rows: cloned,
      summaryText: props.summaryText
    })
  }
}, { deep: true })

function clearAll() {
  localData.value = []
}
</script>

<style scoped>
.table-actions { display: flex; gap: 8px; margin-top: 8px; }
</style>
