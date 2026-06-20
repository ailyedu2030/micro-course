<template>
  <div class="interactive-editor">
    <SlideUploadZone v-if="!slide" :uploading="uploading" :handle-upload="handleUpload" />
    <div v-else class="editor-layout">
      <SlideThumbnailGrid :pages="pages" :selected="selectedPage" @select="selectPage" />
      <SlideEditorPanel v-if="selectedPage" :page="selectedPage"
        :ai-loading="aiLoading" :tts-loading="ttsLoading"
        @generateAI="handleGenerateAI" @generateTTS="handleGenerateTTS"
        @saveScript="handleSaveScript" />
      <div v-else class="editor-empty-hint">点击左侧缩略图编辑讲述稿</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useSlideManager } from '../composables/useSlideManager'
import SlideUploadZone from './SlideUploadZone.vue'
import SlideThumbnailGrid from './SlideThumbnailGrid.vue'
import SlideEditorPanel from './SlideEditorPanel.vue'

const props = defineProps({ lessonId: { type: [Number, String], required: true }, courseId: { type: [Number, String], required: true } })
const { slide, pages, selectedPage, editingScript, uploading, aiLoading, ttsLoading, loadData, selectPage, handleUpload, handleGenerateAI, handleGenerateTTS, handleSaveScript } = useSlideManager(props)

onMounted(() => loadData())
</script>

<style scoped>
.interactive-editor { height: 100%; display: flex; flex-direction: column; }
.editor-layout { display: flex; flex: 1; overflow: hidden; }
.editor-layout > :first-child { width: 260px; overflow-y: auto; flex-shrink: 0; }
.editor-layout > :last-child { flex: 1; overflow-y: auto; }
.editor-empty-hint { display: flex; align-items: center; justify-content: center; color: #c0c4cc; font-size: 14px; }
</style>
