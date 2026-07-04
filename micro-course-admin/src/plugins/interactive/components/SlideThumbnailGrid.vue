<template>
  <div class="thumb-grid">
    <div
      v-for="page in pages"
      :key="page.pageNumber"
      class="thumb-item"
      :class="{ active: selected?.pageNumber === page.pageNumber, loading: loadingMap[page.pageNumber] }"
      @click="$emit('select', page)"
>
      <div class="thumb-img-wrap">
        <img
          v-if="thumbSrc(page)"
          :src="thumbSrc(page)"
          :alt="'第' + page.pageNumber + '页'"
          class="thumb-img"
          @error="onImgError(page)"
          @load="onImgLoad(page)"
        />
        <div v-else class="thumb-placeholder">{{ page.pageNumber }}</div>
      </div>
      <div class="thumb-status">
        <span class="status-dot" :class="statusClass(page)" />
        <span class="status-label">{{ statusText(page) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { loadAuthResource } from '@/utils/authImage'

const props = defineProps({
  pages: { type: Array, default: () => [] },
  selected: { type: Object, default: null },
  courseId: { type: [String, Number], default: null }
})
defineEmits(['select'])

const thumbUrls = ref({})
const loadingMap = ref({})
const errorMap = ref({})

function thumbSrc(page) {
  return errorMap.value[page.pageNumber] ? null : thumbUrls.value[page.pageNumber]
}

function onImgError(page) {
  errorMap.value[page.pageNumber] = true
}

function onImgLoad(page) {
  loadingMap.value[page.pageNumber] = false
}

async function loadThumbnails() {
  const cid = props.courseId
  if (!cid || !props.pages.length) return
  const CONCURRENCY = 6
  for (let i = 0; i < props.pages.length; i += CONCURRENCY) {
    const batch = props.pages.slice(i, i + CONCURRENCY)
    const urls = await Promise.all(batch.map(p =>
      loadAuthResource(`/courses/${cid}/slides/pages/${p.pageNumber}/thumbnail`)
        .catch(() => null)
    ))
    batch.forEach((p, j) => {
      if (urls[j]) {
        thumbUrls.value[p.pageNumber] = urls[j]
        loadingMap.value[p.pageNumber] = true
      } else {
        errorMap.value[p.pageNumber] = true
      }
    })
  }
}

watch(() => [props.courseId, props.pages], () => {
  thumbUrls.value = {}
  errorMap.value = {}
  loadingMap.value = {}
  loadThumbnails()
}, { deep: true, immediate: true })

const statusClass = (p) => {
  if (p.narrationStatus === 'AUDIO_READY') return 'dot-ready'
  if (p.narrationStatus === 'AI_GENERATED' || p.narrationStatus === 'TEACHER_EDITED') return 'dot-script'
  return 'dot-pending'
}
const statusText = (p) => {
  const m = { PENDING: '待处理', AI_GENERATED: 'AI已生成', TEACHER_EDITED: '已编辑', AUDIO_GENERATING: '配音中', AUDIO_READY: '音频就绪' }
  return m[p.narrationStatus] || p.narrationStatus
}
</script>

<style scoped>
.thumb-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 8px; padding: 12px; }
.thumb-item { cursor: pointer; border: 2px solid transparent; border-radius: 8px; overflow: hidden; transition: all .2s; }
.thumb-item.active { border-color: var(--el-color-primary); box-shadow: 0 0 0 2px var(--el-color-primary-light-8); }
.thumb-item:hover { border-color: var(--el-color-primary-light-5); transform: translateY(-2px); box-shadow: var(--el-box-shadow-light); }
.thumb-img-wrap { aspect-ratio: 4/3; background: #f5f5f5; display: flex; align-items: center; justify-content: center; overflow: hidden; position: relative; }
.thumb-img { width: 100%; height: 100%; object-fit: cover; transition: transform .3s; }
.thumb-item:hover .thumb-img { transform: scale(1.08); }
.thumb-placeholder { font-size: 20px; font-weight: 700; color: #c0c4cc; }
.thumb-status { display: flex; align-items: center; gap: 4px; padding: 4px 6px; font-size: 11px; }
.status-dot { width: 6px; height: 6px; border-radius: 50%; }
.dot-pending { background: #d1d5db; }
.dot-script { background: #f59e0b; }
.dot-ready { background: #22c55e; }
.status-label { color: #909399; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.thumb-item.loading .thumb-img { opacity: 0.6; }
</style>
