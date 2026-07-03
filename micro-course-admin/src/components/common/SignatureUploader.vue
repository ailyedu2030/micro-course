<template>
  <div class="signature-uploader">
    <div v-if="localUrl && !uploading" class="preview-area">
      <img :src="localUrl" alt="预览" class="preview-img" @click="handleSelect" />
      <div class="preview-actions">
        <el-button size="small" type="primary" link @click="handleSelect">更换</el-button>
        <el-button size="small" type="danger" link @click="handleRemove">删除</el-button>
      </div>
    </div>

    <el-upload
      v-else
      ref="uploadRef"
      action=""
      :accept="acceptTypes"
      :show-file-list="false"
      :before-upload="beforeUpload"
      :http-request="handleUpload"
      :disabled="disabled"
      class="upload-box"
    >
      <div v-if="uploading" class="upload-placeholder">
        <el-icon class="upload-icon" :size="24"><UploadFilled /></el-icon>
        <span>上传中...</span>
      </div>
      <div v-else class="upload-placeholder">
        <el-icon class="upload-icon" :size="24"><Plus /></el-icon>
        <span>{{ uploadText }}</span>
      </div>
    </el-upload>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, UploadFilled } from '@element-plus/icons-vue'

const props = defineProps({
  imageUrl: { type: String, default: '' },
  label: { type: String, default: '签名' },
  disabled: { type: Boolean, default: false },
  uploadUrl: { type: String, default: '/api/storage-applications/{id}/upload-image' }
})

const emit = defineEmits(['update:imageUrl'])

const uploadRef = ref(null)
const uploading = ref(false)
const localUrl = ref(props.imageUrl || '')

const acceptTypes = 'image/jpeg,image/png'

const uploadText = '点击上传'

watch(
  () => props.imageUrl,
  (v) => { localUrl.value = v || '' }
)

function beforeUpload(file) {
  const isValidType = file.type === 'image/jpeg' || file.type === 'image/png'
  const isValidSize = file.size / 1024 / 1024 < 2

  if (!isValidType) {
    ElMessage.error('仅支持 jpg/png 格式')
    return false
  }
  if (!isValidSize) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

async function handleUpload({ file }) {
  uploading.value = true
  try {
    localUrl.value = URL.createObjectURL(file)
    emit('update:imageUrl', localUrl.value)
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

function handleSelect() {
  uploadRef.value?.$el?.querySelector('input')?.click()
}

function handleRemove() {
  localUrl.value = ''
  emit('update:imageUrl', '')
}
</script>

<style scoped>
.signature-uploader {
  display: inline-block;
}
.upload-box {
  display: inline-flex;
}
.upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 100px;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  background: #fafafa;
  transition: border-color 0.2s;
  gap: 6px;
  font-size: 12px;
  color: #909399;
}
.upload-placeholder:hover {
  border-color: #409eff;
  color: #409eff;
}
.upload-icon {
  color: #c0c4cc;
}
.upload-placeholder:hover .upload-icon {
  color: #409eff;
}
.preview-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.preview-img {
  max-width: 120px;
  max-height: 80px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.preview-img:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}
.preview-actions {
  display: flex;
  gap: 8px;
}
</style>
