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

// P1-C 修复：与 SignatureBlock 同型的双向深 watch 回声死循环。
// 此前 props 回写 + localData deep watch emit 重建数组 → 父级换新引用 → 无限循环，
// 点击"+ 新增行"即主线程冻结。回写改为内容级（JSON）去重。
watch(() => props.modelValue, (v) => {
  const incoming = JSON.parse(JSON.stringify(v || []))
  if (JSON.stringify(incoming) !== JSON.stringify(localData.value)) {
    localData.value = incoming
  }
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
