<template>
  <div class="signature-uploader">
    <el-radio-group v-model="signType" size="small" @change="onChange">
      <el-radio-button value="TEXT">文字签名</el-radio-button>
      <el-radio-button value="IMAGE">图片签名</el-radio-button>
    </el-radio-group>

    <div v-if="signType === 'TEXT'" class="sign-text-input">
      <el-input v-model="signText" placeholder="输入签名文字" maxlength="20" @input="onChange" />
    </div>

    <div v-else class="sign-image-upload">
      <CommonSignatureUploader
        label="签名图片"
        :image-url="imageUrl"
        :uploader="props.uploadHandler ? forwardUpload : null"
        @update:image-url="handleImageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import CommonSignatureUploader from '../common/SignatureUploader.vue'

const props = defineProps({
  modelValue: { type: Object, default: () => ({ type: 'TEXT', text: '', imageUrl: '' }) },
  uploadHandler: { type: Function }
})
const emit = defineEmits(['update:modelValue'])

const signType = ref(props.modelValue?.type || 'TEXT')
const signText = ref(props.modelValue?.text || '')
const imageUrl = ref(props.modelValue?.imageUrl || '')

// P1-C-12 修复：监听外部 modelValue 变化以同步状态
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    signType.value = newVal.type || 'TEXT'
    signText.value = newVal.text || ''
    imageUrl.value = newVal.imageUrl || ''
  }
}, { deep: true })

function forwardUpload(file, onProgress) {
  return props.uploadHandler(file, onProgress)
}

function handleImageChange(url) {
  imageUrl.value = url || ''
  onChange()
}

function onChange() {
  emit('update:modelValue', { type: signType.value, text: signText.value, imageUrl: imageUrl.value })
}
</script>

<style scoped>
.sign-text-input { margin-top: 8px; }
.sign-image-upload { margin-top: 8px; }
.preview-img { margin-top: 8px; display: flex; align-items: center; gap: 8px; }
.preview-img img { max-height: 60px; border: 1px solid #dcdfe6; border-radius: 4px; }
</style>
