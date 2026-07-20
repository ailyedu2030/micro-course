<!--
  HtmlBlockEditor.vue · HTML 课件区块编辑器 (内容 Panel)

  【W37】升级为 Quill 富文本编辑器 + 源码模式双视图

  Props:
    courseId, sectionId

  设计:
  - 双模式: WYSIWYG (Quill) / Source (HTML textarea)
  - WYSIWYG 工具栏: 标题/加粗/斜体/链接/列表/图片/代码块
  - 7-19 P0 防御: 后端强制 HtmlSanitizer, 前端不做 sanitize
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
        <el-radio-group v-model="editorMode" size="small">
          <el-radio-button label="wysiwyg">富文本</el-radio-button>
          <el-radio-button label="source">HTML 源码</el-radio-button>
        </el-radio-group>
        <el-button :icon="View" size="small" plain @click="previewOpen = true">预览</el-button>
        <el-button type="primary" size="small" :icon="Check" :loading="saving" @click="handleSave" :disabled="!htmlDirty">
          保存
        </el-button>
      </div>
    </div>

    <div class="hbe-body">
      <!-- WYSIWYG 模式: Quill 富文本编辑器 -->
      <div v-show="editorMode === 'wysiwyg'" class="hbe-wysiwyg">
        <QuillEditor
          v-model:content="htmlContent"
          :options="quillOptions"
          content-type="html"
          @update:content="htmlDirty = true"
          class="hbe-quill"
        />
      </div>

      <!-- Source 模式: HTML 源码编辑 -->
      <div v-show="editorMode === 'source'" class="hbe-source-wrapper">
        <el-input
          v-model="htmlContent"
          type="textarea"
          :rows="20"
          placeholder="<p>在这里粘贴 HTML 内容...</p>"
          class="hbe-source"
          @input="htmlDirty = true"
        />
      </div>
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
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'
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
const editorMode = ref('wysiwyg')

const quillOptions = {
  theme: 'snow',
  modules: {
    toolbar: [
      [{ header: [1, 2, 3, 4, 5, 6, false] }],
      ['bold', 'italic', 'underline', 'strike'],
      [{ color: [] }, { background: [] }],
      [{ script: 'sub' }, { script: 'super' }],
      [{ list: 'ordered' }, { list: 'bullet' }],
      [{ indent: '-1' }, { indent: '+1' }],
      [{ align: [] }],
      ['blockquote', 'code-block'],
      ['link', 'image', 'video'],
      ['clean']
    ]
  },
  placeholder: '在这里编辑课件内容...'
}

async function load() {
  const res = await getHtmlUnitBySection(props.courseId, props.sectionId)
  unit.value = res.data || res
  if (unit.value) {
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
.hbe-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px; }
.hbe-title { margin: 0; font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.hbe-actions { display: flex; gap: 8px; align-items: center; }
.hbe-body { display: flex; flex-direction: column; gap: 12px; }
.hbe-wysiwyg { background: white; border-radius: 6px; min-height: 400px; }
.hbe-quill :deep(.ql-editor) { min-height: 360px; font-size: 14px; line-height: 1.7; }
.hbe-quill :deep(.ql-toolbar) { border-top-left-radius: 6px; border-top-right-radius: 6px; }
.hbe-source-wrapper { width: 100%; }
.hbe-source { font-family: 'Monaco', 'Consolas', monospace; font-size: 13px; }
.hbe-iframe { width: 100%; min-height: 60vh; border: 1px solid var(--el-border-color-lighter); border-radius: 4px; }
</style>