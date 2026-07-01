<template>
  <div class="dynamic-table-editor">
    <el-table :data="localData" stripe border size="small">
      <el-table-column v-for="col in columns" :key="col.prop" :prop="col.prop" :label="col.label" :width="col.width" :min-width="col.minWidth">
        <template #default="{ row, $index }">
          <el-input v-if="col.type === 'text'" v-model="row[col.prop]" :placeholder="col.placeholder" size="small" @input="onChange" />
          <el-input-number v-else-if="col.type === 'number'" v-model="row[col.prop]" :min="col.min ?? 0" :max="col.max" size="small" controls-position="right" style="width:100%" @change="onChange" />
          <el-select v-else-if="col.type === 'select'" v-model="row[col.prop]" size="small" style="width:100%" @change="onChange">
            <el-option v-for="opt in col.options" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ $index }">
          <el-button type="danger" size="small" link @click="removeRow($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="table-actions">
      <el-button type="primary" size="small" @click="addRow">+ 新增行</el-button>
      <el-button size="small" @click="clearAll">清空</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

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
watch(() => props.modelValue, (v) => { localData.value = JSON.parse(JSON.stringify(v || [])) })

function addRow() {
  if (props.maxRows > 0 && localData.value.length >= props.maxRows) return
  localData.value.push({ ...props.defaultRow })
  onChange()
}
function removeRow(index) {
  localData.value.splice(index, 1)
  onChange()
}
function clearAll() {
  localData.value = []
  onChange()
}
function onChange() {
  emit('update:modelValue', JSON.parse(JSON.stringify(localData.value)))
  emit('change', localData.value)
}
</script>

<style scoped>
.table-actions { display: flex; gap: 8px; margin-top: 8px; }
</style>
