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
    <el-alert
      v-if="!sectionId"
      type="info"
      :closable="false"
      show-icon
      :title="t('htmlBlock.editor.chapterNotSupportedTitle')"
      :description="t('htmlBlock.editor.chapterNotSupportedDesc')"
      class="hbe-chapter-notice"
    />
    <div class="hbe-header">
      <h3 class="hbe-title">
        <el-icon><Document /></el-icon>
        {{ t('htmlBlock.editor.title') }}
        <el-tag v-if="unit" size="small" type="info">id={{ unit.id }}</el-tag>
      </h3>
      <div class="hbe-actions">
        <el-radio-group v-model="editorMode" size="small">
          <el-radio-button value="wysiwyg">{{ t('htmlBlock.editor.wysiwygMode') }}</el-radio-button>
          <el-radio-button value="source">{{ t('htmlBlock.editor.sourceMode') }}</el-radio-button>
        </el-radio-group>
        <el-button :icon="View" size="small" plain @click="previewOpen = true">{{ t('htmlBlock.editor.preview') }}</el-button>
        <el-button type="primary" size="small" :icon="Check" :loading="saving" @click="handleSave" :disabled="!htmlDirty">
          {{ t('htmlBlock.editor.save') }}
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
          :placeholder="t('htmlBlock.editor.sourcePlaceholder')"
          class="hbe-source"
          @input="htmlDirty = true"
        />
      </div>
    </div>

    <el-dialog v-model="previewOpen" :title="t('htmlBlock.editor.previewTitle')" width="80%" top="5vh">
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
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Document, Check, View } from '@element-plus/icons-vue'
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'
import { getHtmlUnitBySection, createHtmlUnit, updateHtmlUnit } from '../api/htmlCourseware'
import { getSlidePages } from '../api/slide'

const { t } = useI18n()

const props = defineProps({
  courseId: { type: Number, required: true },
  sectionId: { type: Number, default: null },
  // P0-3: 替换 HTML 上传成功后由父组件 +1 强制重载编辑器内容（watch courseId/sectionId 不触发）
  reloadKey: { type: Number, default: 0 }
})
const emit = defineEmits(['unit-saved'])

const unit = ref(null)
const htmlContent = ref('')
const htmlDirty = ref(false)
const saving = ref(false)
const previewOpen = ref(false)
const editorMode = ref('wysiwyg')

const quillOptions = computed(() => ({
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
  placeholder: t('htmlBlock.editor.quillPlaceholder')
}))

async function load() {
  try {
    if (!props.sectionId) {
      unit.value = null
      htmlContent.value = ''
      return
    }
    const res = await getHtmlUnitBySection(props.courseId, props.sectionId)
    // P1-C 修复：后端 R 包装 {code,data} 且单元不存在时 data=null，
    // 原 `res.data || res` 回退成整个 R 包装对象（truthy）→ 误走 update 路径
    // （PUT /html/units/undefined → 500），导致单元永远无法创建。
    const payload = res?.data
    const unitData = payload && typeof payload === 'object' && 'data' in payload ? payload.data : payload
    unit.value = unitData || null
    if (unit.value) {
      htmlContent.value = unit.value.htmlSanitized || unit.value.htmlContent || ''
      htmlDirty.value = false
    } else {
      htmlContent.value = ''
      // 无单元时预载已上传的 HTML 课件内容（course_slides + slide_pages HTML_DIRECT），
      // 避免「上传 HTML 后编辑器为空、保存清空内容」的内容丢失问题。
      try {
        const pagesRes = await getSlidePages(props.courseId, null, props.sectionId)
        const pages = pagesRes?.data || []
        const htmlPage = pages.find(p => p.contentType === 'HTML_DIRECT' && p.htmlContent)
        if (htmlPage?.htmlContent) {
          htmlContent.value = htmlPage.htmlContent
          htmlDirty.value = true
        }
      } catch (e) {
        // 预载失败不阻断编辑器，保持空内容
      }
    }
  } catch (e) {
    // P1-C 修复: 原 load() 无 try/catch → getHtmlUnitBySection 失败时
    // 编辑器静默空白且无任何反馈
    console.error('Failed to load HTML unit:', e)
    ElMessage.error(t('htmlBlock.editor.loadFailed', { msg: e.message || t('htmlBlock.editor.unknownError') }))
  }
}

async function handleSave() {
  if (!props.sectionId) {
    ElMessage.warning(t('htmlBlock.editor.chapterNotSupportedMsg'))
    return
  }
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
      ElMessage.success(t('htmlBlock.editor.unitCreated', { id: res?.data?.data ?? res?.data ?? res }))
    }
    ElMessage.success(t('htmlBlock.editor.saved'))
    htmlDirty.value = false
    await load()
    // 通知工作台刷新 tree（单元创建/更新后分段脚本面板才能正确渲染）
    emit('unit-saved')
  } catch (e) {
    ElMessage.error(t('htmlBlock.editor.saveFailed', { msg: e.message || t('htmlBlock.editor.unknownError') }))
  } finally {
    saving.value = false
  }
}

watch(() => [props.courseId, props.sectionId], load, { immediate: true })
// P0-3: 替换上传后父组件 bump reloadKey → 强制重载（v2 unit 内容已由后端同步更新）
watch(() => props.reloadKey, (val, old) => {
  if (val !== old) {
    load()
  }
})
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
