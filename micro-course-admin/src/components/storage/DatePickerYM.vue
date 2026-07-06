<template>
  <el-date-picker
    :model-value="modelValue"
    type="month"
    format="YYYY.M"
    value-format="YYYY.M"
    :placeholder="placeholder"
    :disabled-date="disabledDate"
    style="width:160px"
    @update:model-value="$emit('update:modelValue', $event)"
  />
</template>

<script setup>
const props = defineProps({
  modelValue: String,
  placeholder: { type: String, default: '选择日期' },
  /** true=只允许未来日期(开课时间), false=只允许过去日期(申请时间) */
  future: { type: Boolean, default: false }
})
defineEmits(['update:modelValue'])

const disabledDate = (time) => {
  const now = new Date()
  const min = new Date(new Date().getFullYear() - 5, 0, 1)
  if (props.future) {
    // 开课时间模式: 只允许今天及未来, 禁用过去
    const minTs = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
    return time.getTime() < minTs
  } else {
    // 申请时间模式: 只允许今天及过去, 禁用未来(明天起)
    const max = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1).getTime()
    return time.getTime() < min.getTime() || time.getTime() >= max
  }
}
</script>
