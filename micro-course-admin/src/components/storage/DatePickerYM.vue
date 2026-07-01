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
  // 使用 UTC 日期避免时区导致当月被禁用（getMonth() = 当月自然月）
  const max = new Date(Date.UTC(now.getFullYear(), now.getMonth() + 1, 1))
  return time.getTime() < min.getTime() || time.getTime() >= max.getTime()
}
</script>
