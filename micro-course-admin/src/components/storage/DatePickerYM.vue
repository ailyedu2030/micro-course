<template>
  <el-date-picker
    :model-value="modelValue"
    type="date"
    format="YYYY.M.D"
    value-format="YYYY.M.D"
    placeholder="选择日期"
    :disabled-date="disabledDate"
    style="width:160px"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>

<script setup>
defineProps({ modelValue: String })
defineEmits(['update:modelValue'])

const disabledDate = (time) => {
  const now = new Date()
  const min = new Date('2024-01-01')
  // 禁用明天及未来日期（今天可选）
  // Element Plus 的 time 参数是该日 local midnight 0:00:00
  // today=7月2日, max=7月3日 local midnight → 7月2日 enabled, 7月3日+ disabled
  const max = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1).getTime()
  return time.getTime() < min.getTime() || time.getTime() >= max
}
</script>
