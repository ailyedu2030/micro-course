<template>
  <div class="video-editor">
    <div class="upload-area" v-loading="uploading" element-loading-text="正在上传中，请勿离开...">
      <el-upload drag :show-file-list="false" accept="video/*" :disabled="uploading" :before-upload="handleUpload">
        <el-icon :size="32" class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">拖拽视频到此处，或 <em>点击上传</em></div>
        <div class="upload-hint">支持 mp4/mov 格式，最大 2GB</div>
      </el-upload>
    </div>
    <el-progress v-if="uploading" :percentage="uploadProgress" :stroke-width="6" style="margin-bottom:16px" />
    <div class="video-info" v-if="videoFile">
      <p>已上传: {{ videoFile }}</p>
      <video v-if="previewUrl" :src="previewUrl" controls width="100%" height="300" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadVideo } from '@/api/video'
import { UploadFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'

const props = defineProps({ lessonId: { type: [Number, String], required: true }, courseId: { type: [Number, String], required: true } })
const videoFile = ref('')
const previewUrl = ref('')
const uploading = ref(false)
const uploadProgress = ref(0)

async function handleUpload(file) {
  uploading.value = true
  uploadProgress.value = 0
  const fd = new FormData()
  fd.append('file', file)
  fd.append('courseId', String(props.courseId))
  fd.append('lessonId', String(props.lessonId))
  try {
    const res = await request({
      method: 'POST',
      url: '/videos/upload',
      data: fd,
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e) => {
        uploadProgress.value = Math.round((e.loaded / e.total) * 100)
      }
    })
    videoFile.value = file.name
    if (res.data?.url) previewUrl.value = res.data.url
    ElMessage.success('视频上传成功')
  } catch { ElMessage.error('上传失败') }
  finally { uploading.value = false }
  return false
}
</script>
<style scoped>
.video-editor { padding: var(--space-5); }
.upload-area { margin-bottom: var(--space-5); }
.upload-icon { color: var(--el-color-primary); }
.upload-text { font-size: var(--text-base); color: var(--el-text-color-regular); }
.upload-hint { font-size: var(--text-xs); color: var(--el-text-color-secondary); margin-top: var(--space-1-5); }
.video-info { margin-top: var(--space-4); }
</style>
