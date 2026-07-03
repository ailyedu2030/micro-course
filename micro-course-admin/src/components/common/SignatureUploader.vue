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
      <div v-if="uploading" class="upload-placeholder uploading">
        <el-icon class="upload-icon is-loading" :size="24"><Loading /></el-icon>
        <span>上传中... {{ uploadProgress }}%</span>
        <el-progress v-if="uploadProgress > 0 && uploadProgress < 100" :percentage="uploadProgress" :stroke-width="3" class="upload-progress" />
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
import { Plus, Loading } from '@element-plus/icons-vue'

const props = defineProps({
  imageUrl: { type: String, default: '' },
  label: { type: String, default: '签名' },
  disabled: { type: Boolean, default: false },
  // P1-UX: 接受外部传入的上传函数 (file, onProgress) => Promise<{url, fileName, fileSize}>
  // 父组件需实现真实后端调用 + 进度回调
  uploader: { type: Function, default: null }
})

const emit = defineEmits(['update:imageUrl'])

const uploadRef = ref(null)
const uploading = ref(false)
const uploadProgress = ref(0)  // P1-UX: 0-100% 进度
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
  uploadProgress.value = 0
  // P1-UX: 先显示本地预览（立即反馈）
  const localPreview = URL.createObjectURL(file)
  localUrl.value = localPreview
  try {
    // P1-UX: 调用外部 uploader 真实上传，传 progress 回调
    if (props.uploader) {
      const result = await props.uploader(file, (p) => { uploadProgress.value = p })
      // P1-UX: 替换为后端 URL
      if (result?.url) {
        URL.revokeObjectURL(localPreview)
        localUrl.value = result.url
        emit('update:imageUrl', result.url)
        ElMessage.success(`${props.label || '图片'}上传成功`)
      } else {
        throw new Error('上传未返回 URL')
      }
    } else {
      // P1-UX: 无 uploader 时保留本地预览，但提示用户
      ElMessage.warning('未配置上传通道，请联系管理员')
    }
  } catch (e) {
    ElMessage.error(e?.message || '上传失败，请重试')
    // P1-UX: 上传失败时清除预览，避免假成功
    URL.revokeObjectURL(localPreview)
    localUrl.value = ''
    emit('update:imageUrl', '')
  } finally {
    uploading.value = false
    uploadProgress.value = 0
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
/* P1-UX: 上传中样式 — 蓝色边框 + Loading 旋转图标 */
.upload-placeholder.uploading {
  border-color: #409eff;
  border-style: solid;
  background: #ecf5ff;
  color: #409eff;
  cursor: not-allowed;
}
.upload-placeholder.uploading .upload-icon {
  color: #409eff;
  animation: rotating 1.2s linear infinite;
}
@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.upload-progress {
  width: 100px;
  margin-top: 2px;
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
