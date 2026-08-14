<template>
  <div class="slide-upload-zone">
    <el-upload drag :show-file-list="false" :before-upload="handleUpload" accept=".pptx,.html,.htm" :disabled="uploading">
      <el-icon class="upload-icon" :size="40"><UploadFilled /></el-icon>
      <div class="upload-text">{{ t('slide.upload.dragHint') }} <em>{{ t('slide.upload.clickToUpload') }}</em></div>
      <div class="upload-hint">{{ t('slide.upload.hint') }}</div>
    </el-upload>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import { UploadFilled } from '@element-plus/icons-vue'

const { t } = useI18n()

defineProps({ uploading: Boolean, handleUpload: { type: Function, required: true }, chapterId: { type: Number, default: null } })
</script>

<style scoped>
.slide-upload-zone { padding: 40px 20px; }
.upload-icon { color: var(--el-color-primary); margin-bottom: 12px; }
.upload-text { font-size: 14px; color: #606266; }
.upload-hint { font-size: 12px; color: #909399; margin-top: 6px; }
</style>
