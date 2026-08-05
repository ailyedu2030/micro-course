<template>
  <div class="signature-block">
    <el-divider />
    <h4 class="block-title">{{ title }}</h4>

    <el-form :model="localData" :rules="formRules" ref="formRef" label-width="80px" size="small">
      <el-form-item label="意见" prop="opinionText">
        <el-input v-model="localData.opinionText" type="textarea" :rows="3" placeholder="请输入意见..." maxlength="500" />
      </el-form-item>

      <el-form-item label="负责人签字">
        <SignatureUploader v-model="localData.signature" :upload-handler="signatureUploader" />
      </el-form-item>

      <el-form-item label="公章">
        <SignatureUploader v-model="localData.seal" :upload-handler="sealUploader" />
      </el-form-item>

      <el-form-item label="日期">
        <DatePickerYM v-model="localData.signDate" />
      </el-form-item>

      <el-form-item v-if="showRemark" label="备注">
        <el-input v-model="localData.remark" placeholder="备注信息" maxlength="200" />
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import SignatureUploader from './SignatureUploader.vue'
import DatePickerYM from './DatePickerYM.vue'

const formRef = ref(null)
const formRules = {
  opinionText: [
    { max: 500, message: '意见内容超出长度限制', trigger: 'blur' }
  ]
}

const props = defineProps({
  title: String,
  modelValue: Object,
  // P1-C 修复：父组件按 签名/公章 分别传入上传通道；此前只定义 uploadHandler，
  // 父组件传的 signature-uploader/seal-uploader 未接收 → 上传仅本地预览、永不落库
  signatureUploader: Function,
  sealUploader: Function,
  showRemark: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

const localData = ref(props.modelValue || {
  opinionText: '', signature: { type: 'TEXT', text: '', imageUrl: '' },
  seal: { type: 'TEXT', text: '', imageUrl: '' }, signDate: '', remark: ''
})
// P1-C 修复：props 回写改为内容级去重（JSON 比较），打断 双向深 watch 回声死循环。
// 此前任何交互（切换图片签名/输入意见/选日期）→ emit → 父级换新对象 → props watch 赋回
// → localData watch 再 emit → 无限循环，页面主线程冻结。
watch(() => props.modelValue, (v) => {
  if (v && JSON.stringify(v) !== JSON.stringify(localData.value)) {
    localData.value = v
  }
}, { deep: true })
watch(localData, () => emit('update:modelValue', { ...localData.value }), { deep: true })
</script>

<style scoped>
.block-title { margin: 8px 0; color: var(--el-text-color-primary); font-size: 14px; }
</style>
