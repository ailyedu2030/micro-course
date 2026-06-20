<template>
  <div class="thumb-grid">
    <div v-for="page in pages" :key="page.pageNumber" class="thumb-item"
      :class="{ active: selected?.pageNumber === page.pageNumber }"
      @click="$emit('select', page)">
      <div class="thumb-img-wrap">
        <div class="thumb-num">{{ page.pageNumber }}</div>
      </div>
      <div class="thumb-status">
        <span class="status-dot" :class="statusClass(page)" />
        <span class="status-label">{{ statusText(page) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({ pages: Array, selected: Object })
defineEmits(['select'])
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
.thumb-item { cursor: pointer; border: 2px solid transparent; border-radius: 8px; overflow: hidden; transition: border-color .2s; }
.thumb-item.active { border-color: var(--el-color-primary); }
.thumb-img-wrap { aspect-ratio: 4/3; background: #f5f5f5; display: flex; align-items: center; justify-content: center; }
.thumb-num { font-size: 20px; font-weight: 700; color: #c0c4cc; }
.thumb-status { display: flex; align-items: center; gap: 4px; padding: 4px 6px; font-size: 11px; }
.status-dot { width: 6px; height: 6px; border-radius: 50%; }
.dot-pending { background: #d1d5db; }
.dot-script { background: #f59e0b; }
.dot-ready { background: #22c55e; }
.status-label { color: #909399; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
