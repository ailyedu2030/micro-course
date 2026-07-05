<template>
  <div class="signature-block">
    <el-divider />
    <h4 class="block-title">{{ title }}</h4>

    <el-form :model="localData" :rules="formRules" ref="formRef" label-width="80px" size="small">
      <el-form-item label="意见" prop="opinionText">
        <el-input v-model="localData.opinionText" type="textarea" :rows="3" placeholder="请输入意见..." maxlength="500" />
      </el-form-item>

      <el-form-item label="负责人签字">
        <SignatureUploader v-model="localData.signature" :upload-handler="uploadHandler" />
      </el-form-item>

      <el-form-item label="公章">
        <SignatureUploader v-model="localData.seal" :upload-handler="uploadHandler" />
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
  uploadHandler: Function,
  showRemark: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])

const localData = ref(props.modelValue || {
  opinionText: '', signature: { type: 'TEXT', text: '', imageUrl: '' },
  seal: { type: 'TEXT', text: '', imageUrl: '' }, signDate: '', remark: ''
})
watch(() => props.modelValue, (v) => { if (v) localData.value = v }, { deep: true })
watch(localData, () => emit('update:modelValue', { ...localData.value }), { deep: true })
</script>

<style scoped>
.block-title { margin: 8px 0; color: #303133; font-size: 14px; }
</style>
