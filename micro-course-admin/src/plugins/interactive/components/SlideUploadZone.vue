<template>
  <div class="slide-upload-zone">
    <el-upload drag :show-file-list="false" :before-upload="handleUpload" accept=".pptx" :disabled="uploading">
      <el-icon class="upload-icon" :size="40"><UploadFilled /></el-icon>
      <div class="upload-text">拖拽 .pptx 文件到此处，或 <em>点击上传</em></div>
      <div class="upload-hint">支持 .pptx 格式，最大 50MB</div>
    </el-upload>
  </div>
</template>

<script setup>
defineProps({ uploading: Boolean, handleUpload: { type: Function, required: true } })
</script>

<style scoped>
.slide-upload-zone { padding: 40px 20px; }
.upload-icon { color: var(--el-color-primary); margin-bottom: 12px; }
.upload-text { font-size: 14px; color: #606266; }
.upload-hint { font-size: 12px; color: #909399; margin-top: 6px; }
</style>
