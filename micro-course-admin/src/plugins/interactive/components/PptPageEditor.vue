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
        {{ t('ppt.page.title') }}
        <el-tag v-if="page" size="small" type="info">{{ t('ppt.page.pageNumber', { number: page.pageNumber }) }}</el-tag>
      </h3>
    </div>

    <el-form v-if="page" label-position="top" :model="form" class="pe-form">
      <el-form-item :label="t('ppt.page.pageTitleLabel')">
        <el-input v-model="form.pageTitle" :placeholder="t('ppt.page.pageTitlePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('ppt.page.imageUrlLabel')">
        <el-input v-model="form.imageUrl" :placeholder="t('ppt.page.imageUrlPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('ppt.page.thumbnailUrlLabel')">
        <el-input v-model="form.thumbnailUrl" :placeholder="t('ppt.page.thumbnailUrlPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('ppt.page.dimensions')">
        <el-input-number v-model="form.imageWidth" :min="100" :max="4096" controls-position="right" />
        <span class="pe-times">×</span>
        <el-input-number v-model="form.imageHeight" :min="100" :max="4096" controls-position="right" />
      </el-form-item>
      <el-form-item :label="t('ppt.page.features')">
        <el-checkbox v-model="form.hasAnimation">{{ t('ppt.page.hasAnimation') }}</el-checkbox>
        <el-checkbox v-model="form.hasEmbeddedMedia">{{ t('ppt.page.hasEmbeddedMedia') }}</el-checkbox>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Check" :loading="saving" @click="handleSave">{{ t('ppt.page.saveMetadata') }}</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Picture, Check } from '@element-plus/icons-vue'
import { getPptPage, updatePptPage } from '../api/pptCourseware'

const { t } = useI18n()

const props = defineProps({
  courseId: { type: Number, required: true },
  pageId: { type: Number, required: true }
})

const emit = defineEmits(['page-updated'])

const page = ref(null)
const saving = ref(false)
const form = ref({})

async function load() {
  if (!props.pageId) return
  try {
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
  } catch (e) {
    // P1-C 修复: 原 load() 无 try/catch → getPptPage 失败产生未处理 rejection + 空白面板
    console.error('Failed to load PPT page:', e)
    ElMessage.error(t('ppt.page.loadFailed', { msg: e.message || t('ppt.page.unknownError') }))
  }
}

async function handleSave() {
  saving.value = true
  try {
    await updatePptPage(props.courseId, props.pageId, form.value)
    ElMessage.success(t('ppt.page.saved'))
    emit('page-updated', form.value)
    await load()
  } catch (e) {
    ElMessage.error(t('ppt.page.saveFailed', { msg: e.message || t('ppt.page.unknownError') }))
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