<template>
  <div class="dynamic-table-editor">
    <el-empty v-if="localData.length === 0" description="暂无数据，请添加" />
    <el-table v-else :data="localData" stripe border size="small" style="width: 100%">
      <el-table-column
        v-for="col in columns"
        :key="col.key"
        :prop="col.key"
        :label="col.label"
        :width="col.width"
        header-align="center"
      >
        <template #default="{ row }">
          <el-input
            v-if="col.type === 'text'"
            v-model="row[col.key]"
            :placeholder="col.placeholder || '请输入'"
            size="small"
            @input="onChange"
          />
          <el-input-number
            v-else-if="col.type === 'number'"
            v-model="row[col.key]"
            size="small"
            controls-position="right"
            style="width: 100%"
            @change="onChange"
          />
          <el-select
            v-else-if="col.type === 'select'"
            v-model="row[col.key]"
            size="small"
            style="width: 100%"
            @change="onChange"
          >
            <el-option
              v-for="opt in col.options"
              :key="opt.value ?? opt"
              :label="opt.label ?? opt"
              :value="opt.value ?? opt"
            />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="90" fixed="right" header-align="center">
        <template #default="{ $index }">
          <el-button type="danger" size="small" link :disabled="localData.length <= minRows" @click="removeRow($index)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="table-actions">
      <el-button type="primary" size="small" :disabled="localData.length >= maxRows" @click="addRow">
        + 新增行
      </el-button>
      <span v-if="localData.length >= maxRows" class="max-hint">已达最大行数</span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  columns: { type: Array, required: true },
  minRows: { type: Number, default: 1 },
  maxRows: { type: Number, default: 100 }
})

const emit = defineEmits(['update:modelValue'])

function buildDefaultRow() {
  const row = {}
  for (const col of props.columns) {
    row[col.key] = col.type === 'number' ? null : ''
  }
  return row
}

const localData = ref(JSON.parse(JSON.stringify(props.modelValue || [])))

watch(
  () => props.modelValue,
  (v) => {
    localData.value = JSON.parse(JSON.stringify(v || []))
  },
  { deep: true }
)

function addRow() {
  if (localData.value.length >= props.maxRows) return
  localData.value.push(buildDefaultRow())
  emitChange()
}

function removeRow(index) {
  if (localData.value.length <= props.minRows) return
  localData.value.splice(index, 1)
  emitChange()
}

function onChange() {
  emitChange()
}

function emitChange() {
  emit('update:modelValue', JSON.parse(JSON.stringify(localData.value)))
}
</script>

<style scoped>
.dynamic-table-editor {
  width: 100%;
}
.table-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.max-hint {
  font-size: 12px;
  color: #909399;
}
</style>
