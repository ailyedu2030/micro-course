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
      <el-upload
        :show-file-list="false"
        :before-upload="beforeUpload"
        :http-request="handleUpload"
        accept="image/jpeg,image/png"
      >
        <el-button size="small" type="primary">选择图片</el-button>
        <template #tip>
          <div class="el-upload__tip">jpg/png，≤2MB</div>
        </template>
      </el-upload>
      <div v-if="imageUrl" class="preview-img">
        <img :src="imageUrl" alt="签名预览" />
        <el-button size="small" type="danger" link @click="removeImage">移除</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

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

function beforeUpload(file) {
  const isJpgPng = file.type === 'image/jpeg' || file.type === 'image/png'
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isJpgPng) ElMessage.error('仅支持 jpg/png 格式')
  if (!isLt2M) ElMessage.error('图片大小不能超过 2MB')
  return isJpgPng && isLt2M
}

async function handleUpload({ file }) {
  if (props.uploadHandler) {
    try {
      const res = await props.uploadHandler(file)
      imageUrl.value = res.data?.url || URL.createObjectURL(file)
    } catch {
      ElMessage.error('上传失败')
      if (imageUrl.value) { URL.revokeObjectURL(imageUrl.value); imageUrl.value = ''; }
    }
  } else {
    imageUrl.value = URL.createObjectURL(file)
  }
  onChange()
}

function removeImage() { imageUrl.value = ''; onChange() }
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
