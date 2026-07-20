<!--
  PptPageEditor.vue · PPT 单页编辑面板 (内容 Panel)

  Props:
    courseId, pageId, pageType="PPT"
  Emits:
    page-updated (updatedPageDto)
-->
<template>
  <div class="page-editor">
    <div class="pe-header">
      <h3 class="pe-title">
        <el-icon><Picture /></el-icon>
        PPT 页面元数据
        <el-tag v-if="page" size="small" type="info">第 {{ page.pageNumber }} 页</el-tag>
      </h3>
    </div>

    <el-form v-if="page" label-position="top" :model="form" class="pe-form">
      <el-form-item label="页面标题">
        <el-input v-model="form.pageTitle" placeholder="(可选) 简短描述" />
      </el-form-item>
      <el-form-item label="图片 URL">
        <el-input v-model="form.imageUrl" placeholder="CDN 签名 URL" />
      </el-form-item>
      <el-form-item label="缩略图 URL">
        <el-input v-model="form.thumbnailUrl" placeholder="缩略图 URL (可选)" />
      </el-form-item>
      <el-form-item label="尺寸">
        <el-input-number v-model="form.imageWidth" :min="100" :max="4096" controls-position="right" />
        <span class="pe-times">×</span>
        <el-input-number v-model="form.imageHeight" :min="100" :max="4096" controls-position="right" />
      </el-form-item>
      <el-form-item label="特征">
        <el-checkbox v-model="form.hasAnimation">包含动画</el-checkbox>
        <el-checkbox v-model="form.hasEmbeddedMedia">包含嵌入媒体</el-checkbox>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Check" :loading="saving" @click="handleSave">保存元数据</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, Check } from '@element-plus/icons-vue'
import { getPptPage, updatePptPage } from '../api/pptCourseware'

const props = defineProps({
  courseId: { type: Number, required: true },
  pageId: { type: Number, required: true }
})

const emit = defineEmits(['page-updated'])

const page = ref(null)
const saving = ref(false)
const form = ref({})

async function load() {
  const res = await getPptPage(props.courseId, props.pageId)
  page.value = res.data || res
  form.value = {
    pageTitle: page.value.pageTitle || '',
    imageUrl: page.value.imageUrl || '',
    thumbnailUrl: page.value.thumbnailUrl || '',
    imageWidth: page.value.imageWidth || 1280,
    imageHeight: page.value.imageHeight || 720,
    hasAnimation: page.value.hasAnimation || false,
    hasEmbeddedMedia: page.value.hasEmbeddedMedia || false
  }
}

async function handleSave() {
  saving.value = true
  try {
    await updatePptPage(props.courseId, props.pageId, form.value)
    ElMessage.success('已保存')
    emit('page-updated', form.value)
    await load()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

watch(() => props.pageId, load, { immediate: true })
</script>

<style scoped>
.page-editor { background: var(--el-fill-color-blank); border-radius: 8px; padding: 16px; }
.pe-header { margin-bottom: 12px; }
.pe-title { margin: 0; font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.pe-form { max-width: 600px; }
.pe-times { margin: 0 8px; color: var(--el-text-color-secondary); }
</style>