<template>
  <aside class="panel panel-sidebar">
    <div class="panel-header">
      <span>课程结构</span>
      <el-button size="small" text @click="$emit('addChapter')"><el-icon><Plus /></el-icon> 章节</el-button>
    </div>
    <div class="outline-tree">
      <div v-for="ch in structure" :key="ch.id" class="chapter-group" :data-chapter-id="ch.id">
        <div class="chapter-row"
          :class="{ active: selected?.type === 'chapter' && selected?.id === ch.id }"
          @click="$emit('selectChapter', ch)">
          <span class="drag-handle"><el-icon><Rank /></el-icon></span>
          <el-icon><FolderOpened /></el-icon>
          <span class="node-title">{{ ch.title }}</span>
          <el-tag size="small" type="info" effect="plain">{{ ch.lessons?.length || 0 }}</el-tag>
        </div>
        <div class="lesson-list">
          <div v-for="lesson in ch.lessons" :key="lesson.id" class="lesson-row"
            :class="{ active: selected?.type === 'lesson' && selected?.id === lesson.id }"
            @click="$emit('selectLesson', lesson, ch)">
            <span class="drag-handle"><el-icon><Rank /></el-icon></span>
            <el-icon :size="16" v-if="lesson.lessonType === 'INTERACTIVE'" color="#67c23a"><Present /></el-icon>
            <el-icon :size="16" v-else color="#409eff"><VideoPlay /></el-icon>
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
defineProps({ structure: Array, selected: Object, progress: Object })
defineEmits(['selectChapter', 'selectLesson', 'addChapter', 'addLesson'])
</script>

<style scoped>
.panel-sidebar { width: 280px; background: #fff; border-right: 1px solid #eee; display: flex; flex-direction: column; flex-shrink: 0; overflow: hidden; }
.panel-header { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px; border-bottom: 1px solid #f5f5f5; font-size: 13px; font-weight: 600; color: #333; flex-shrink: 0; }
.outline-tree { flex: 1; padding: 8px 0; overflow-y: auto; }
.chapter-group { margin-bottom: 4px; }
.chapter-row, .lesson-row { display: flex; align-items: center; gap: 6px; padding: 8px 14px; cursor: pointer; font-size: 13px; transition: background .15s; }
.chapter-row:hover, .lesson-row:hover { background: #f5f7fa; }
.chapter-row.active, .lesson-row.active { background: #eef2ff; color: #00cc7e; }
.chapter-row { font-weight: 500; }
.lesson-row { padding-left: 34px; font-size: 12px; color: #555; }
.drag-handle { cursor: grab; color: #ccc; flex-shrink: 0; }
.node-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lesson-status { font-size: 11px; font-weight: 600; color: #67c23a; flex-shrink: 0; }
.lesson-status.empty { color: #d1d5db; }
.add-lesson-btn { margin-left: 34px; font-size: 11px; }
.sidebar-footer { padding: 10px 14px; border-top: 1px solid #f5f5f5; flex-shrink: 0; }
.progress-text { font-size: 11px; color: #999; display: block; margin-top: 4px; }
</style>
