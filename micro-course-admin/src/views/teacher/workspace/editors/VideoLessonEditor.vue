<template>
  <div class="video-editor">
    <div class="upload-area">
      <el-upload drag :show-file-list="false" accept="video/*" :before-upload="handleUpload">
        <el-icon :size="32" class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">拖拽视频到此处，或 <em>点击上传</em></div>
        <div class="upload-hint">支持 mp4/mov 格式，最大 2GB</div>
      </el-upload>
    </div>
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

const props = defineProps({ lessonId: { type: [Number, String], required: true }, courseId: { type: [Number, String], required: true } })
const videoFile = ref('')
const previewUrl = ref('')

async function handleUpload(file) {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('courseId', String(props.courseId))
  fd.append('lessonId', String(props.lessonId))
  try {
    const res = await uploadVideo(fd)
    videoFile.value = file.name
    if (res.data?.url) previewUrl.value = res.data.url
    ElMessage.success('视频上传成功')
  } catch { ElMessage.error('上传失败') }
  return false
}
</script>
<style scoped>
.video-editor { padding: 20px; }
.upload-area { margin-bottom: 20px; }
.upload-icon { color: var(--el-color-primary); }
.upload-text { font-size: 14px; color: #606266; }
.upload-hint { font-size: 12px; color: #909399; margin-top: 6px; }
.video-info { margin-top: 16px; }
</style>
