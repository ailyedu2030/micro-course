<template>
  <el-date-picker
    :model-value="modelValue"
    type="month"
    format="YYYY.M"
    value-format="YYYY.M"
    placeholder="选择年月"
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
  const max = new Date(now.getFullYear(), now.getMonth(), 1)
  return time.getTime() < min.getTime() || time.getTime() > max.getTime()
}
</script>
