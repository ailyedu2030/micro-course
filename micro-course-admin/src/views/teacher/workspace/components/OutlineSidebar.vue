<template>
  <aside class="panel panel-sidebar">
    <div class="panel-header">
      <span>课程结构</span>
      <el-button size="small" text @click="$emit('addChapter')"><el-icon><Plus /></el-icon> 章节</el-button>
    </div>
    <div class="outline-tree">
      <div v-for="ch in structure" :key="ch.id" class="chapter-group" :data-chapter-id="ch.id">
        <div
class="chapter-row"
          :class="{ active: selected?.type === 'chapter' && selected?.id === ch.id }"
          @click="$emit('selectChapter', ch)"
>
          <span class="drag-handle"><el-icon><Rank /></el-icon></span>
          <el-icon><FolderOpened /></el-icon>
          <span class="node-title">{{ ch.title }}</span>
          <el-tag size="small" type="info" effect="plain">{{ ch.lessons?.length || 0 }}</el-tag>
        </div>
        <div class="lesson-list">
          <div
v-for="lesson in ch.lessons" :key="lesson.id" class="lesson-row"
            :class="{ active: selected?.type === 'lesson' && selected?.id === lesson.id }"
            @click="$emit('selectLesson', lesson, ch)"
>
            <span class="drag-handle"><el-icon><Rank /></el-icon></span>
            <el-icon :size="16" v-if="lesson.lessonType === 'INTERACTIVE'" class="lesson-icon-interactive"><Present /></el-icon>
            <el-icon :size="16" v-else class="lesson-icon-video"><VideoPlay /></el-icon>
            <span class="node-title">{{ lesson.title }}</span>
            <span class="lesson-status" v-if="lesson.videoUrl || lesson.slideCount">✓</span>
            <span class="lesson-status empty" v-else>◌</span>
          </div>
        </div>
        <el-button size="small" text class="add-lesson-btn" @click="$emit('addLesson', ch)">
          <el-icon><Plus /></el-icon> 课时
        </el-button>
      </div>
    </div>
    <div class="sidebar-footer">
      <el-progress :percentage="progress.percent" :stroke-width="6" />
      <span class="progress-text">{{ progress.done }}/{{ progress.total }} 课时完成</span>
    </div>
  </aside>
</template>

<script setup>
import { Plus, Rank, FolderOpened, Present, VideoPlay } from '@element-plus/icons-vue'
defineProps({
  structure: { type: Array, default: () => [] },
  selected: { type: Object, default: null },
  progress: { type: Object, default: () => ({ percent: 0, done: 0, total: 0 }) }
})
defineEmits(['selectChapter', 'selectLesson', 'addChapter', 'addLesson'])
</script>

<style scoped>
.panel-sidebar {
  width: 280px;
  background: var(--el-fill-color-blank);
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  flex-shrink: 0;
}

.outline-tree {
  flex: 1;
  padding: var(--space-2) 0;
  overflow-y: auto;
}

.chapter-group {
  margin-bottom: var(--space-1);
}

.chapter-row,
.lesson-row {
  display: flex;
  align-items: center;
  gap: var(--space-1-5);
  padding: var(--space-2) var(--space-4);
  cursor: pointer;
  font-size: var(--text-sm);
  transition: background var(--duration-fast) var(--ease-out);
  border-left: 3px solid transparent;
}

.chapter-row:hover,
.lesson-row:hover {
  background: var(--el-fill-color-light);
}

.chapter-row:focus-visible,
.lesson-row:focus-visible {
  outline: 2px solid var(--role-primary);
  outline-offset: -2px;
  border-radius: var(--radius-sm);
}

.chapter-row.active,
.lesson-row.active {
  background: var(--role-primary-light-9);
  color: var(--role-primary);
  border-left-color: var(--role-primary);
}

.chapter-row {
  font-weight: var(--weight-medium);
}

.lesson-row {
  padding-left: var(--space-8);
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.drag-handle {
  cursor: grab;
  color: var(--el-text-color-placeholder);
  flex-shrink: 0;
}

.node-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lesson-status {
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  color: var(--el-color-success);
  flex-shrink: 0;
}

.lesson-status.empty {
  color: var(--el-text-color-placeholder);
}

.lesson-icon-interactive {
  color: var(--el-color-success);
}

.lesson-icon-video {
  color: var(--role-primary);
}

.add-lesson-btn {
  margin-left: var(--space-8);
  font-size: var(--text-xs);
}

.sidebar-footer {
  padding: var(--space-3) var(--space-4);
  border-top: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
}

.progress-text {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  display: block;
  margin-top: var(--space-1);
}
</style>
