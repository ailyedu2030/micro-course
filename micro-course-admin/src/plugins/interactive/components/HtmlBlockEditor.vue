<!--
  HtmlBlockEditor.vue · HTML 课件区块编辑器 (内容 Panel)

  Props:
    courseId, sectionId, unitId

  设计:
  - 主编辑区: textarea (HTML source)
  - 预览区: iframe sandbox="allow-scripts" (与生产一致)
  - 7-19 P0 防御: 后端强制 HtmlSanitizer, 前端编辑不必 sanitize
-->
<template>
  <div class="html-block-editor">
    <div class="hbe-header">
      <h3 class="hbe-title">
        <el-icon><Document /></el-icon>
        HTML 课件内容
        <el-tag v-if="unit" size="small" type="info">id={{ unit.id }}</el-tag>
      </h3>
      <div class="hbe-actions">
        <el-button :icon="View" size="small" plain @click="previewOpen = true">预览</el-button>
        <el-button type="primary" size="small" :icon="Check" :loading="saving" @click="handleSave" :disabled="!htmlDirty">
          保存
        </el-button>
      </div>
    </div>

    <div class="hbe-body">
      <el-input
        v-model="htmlContent"
        type="textarea"
        :rows="20"
        placeholder="<p>在这里粘贴 HTML 内容...</p>"
        class="hbe-source"
        @input="htmlDirty = true"
      />
    </div>

    <el-dialog v-model="previewOpen" title="预览 (学生视角)" width="80%" top="5vh">
      <iframe
        v-if="previewOpen"
        :srcdoc="htmlContent"
        sandbox="allow-scripts"
        class="hbe-iframe"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Check, View } from '@element-plus/icons-vue'
import { getHtmlUnitBySection, createHtmlUnit, updateHtmlUnit } from '../api/htmlCourseware'

const props = defineProps({
  courseId: { type: Number, required: true },
  sectionId: { type: Number, required: true }
})

const unit = ref(null)
const htmlContent = ref('')
const htmlDirty = ref(false)
const saving = ref(false)
const previewOpen = ref(false)

async function load() {
  const res = await getHtmlUnitBySection(props.courseId, props.sectionId)
  unit.value = res.data || res
  if (unit.value) {
    // 用 sanitized 内容填编辑器 (后端已 sanitize)
    htmlContent.value = unit.value.htmlSanitized || unit.value.htmlContent || ''
    htmlDirty.value = false
  } else {
    htmlContent.value = ''
  }
}

async function handleSave() {
  saving.value = true
  try {
    if (unit.value) {
      await updateHtmlUnit(props.courseId, unit.value.id, {
        pageTitle: unit.value.pageTitle,
        htmlContent: htmlContent.value,
        fileSizeBytes: new Blob([htmlContent.value]).size
      })
    } else {
      const dto = {
        pageTitle: '',
        htmlContent: htmlContent.value,
        fileSizeBytes: new Blob([htmlContent.value]).size
        // 【BUG #24 修复】 slideId 由后端通过 sectionId 反查 (不允许前端传 0)
      }
      const res = await createHtmlUnit(props.courseId, props.sectionId, dto)
      ElMessage.success(`已创建 unit id=${res.data || res}`)
    }
    ElMessage.success('已保存')
    htmlDirty.value = false
    await load()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

watch(() => [props.courseId, props.sectionId], load, { immediate: true })
</script>

<style scoped>
.html-block-editor { background: var(--el-fill-color-blank); border-radius: 8px; padding: 16px; }
.hbe-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.hbe-title { margin: 0; font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.hbe-actions { display: flex; gap: 8px; }
.hbe-body { display: flex; gap: 12px; }
.hbe-source { flex: 1; font-family: 'Monaco', 'Consolas', monospace; font-size: 13px; }
.hbe-iframe { width: 100%; min-height: 60vh; border: 1px solid var(--el-border-color-lighter); border-radius: 4px; }
</style>