<!--
  课程大纲侧边栏（Round 11-3 从 LearningView.vue 拆分）
  ChapterSidebar: 折叠章节列表 + 学习统计卡片 + 移动端抽屉
  Author: jackie
-->
<template>
  <aside class="content-sidebar" :class="{ 'drawer-open': drawerOpen && isMobile }">
    <!-- 移动端关闭按钮 -->
    <button v-if="isMobile" class="drawer-close-btn" @click="$emit('update:drawerOpen', false)">
      <el-icon><Close /></el-icon>
    </button>

    <div class="sidebar-inner">
      <h3 class="sidebar-title">
        <el-icon><List /></el-icon>
        课程大纲
      </h3>

      <!-- 折叠章节列表 -->
      <el-collapse v-model="expandedModel" class="chapter-collapse">
        <el-collapse-item
          v-for="ch in chapters"
          :key="ch.id"
          :name="ch.id"
          class="chapter-collapse-item"
        >
          <template #title>
            <div class="chapter-header">
              <span class="chapter-name">{{ ch.title }}</span>
              <div class="chapter-progress-bar">
                <div class="chapter-progress-fill" :style="{ width: getChapterProgress(ch.id) + '%' }" />
              </div>
              <span class="chapter-progress-text">{{ getChapterProgress(ch.id) }}%</span>
            </div>
          </template>
          <div class="lesson-list">
            <div
              v-for="lesson in ch.lessons"
              :key="lesson.id"
              class="lesson-item"
              :class="{ active: currentLessonId === lesson.id, completed: lesson.status === 'COMPLETED' }"
              role="button"
              tabindex="0"
              @click="$emit('select-lesson', lesson)"
              @keydown.enter="$emit('select-lesson', lesson)"
              @keydown.space.prevent="$emit('select-lesson', lesson)"
            >
              <!-- 状态图标 -->
              <span class="lesson-status-icon">
                <el-icon v-if="lesson.status === 'COMPLETED'" color="#10B981"><CircleCheck /></el-icon>
                <el-icon v-else-if="currentLessonId === lesson.id" color="#4F46E5"><VideoPlay /></el-icon>
                <el-icon v-else color="#94A3B8"><VideoCamera /></el-icon>
              </span>
              <span class="lesson-title">{{ lesson.title }}</span>
              <span v-if="lesson.duration" class="lesson-duration">{{ lesson.duration }}</span>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>

      <!-- 学习统计卡片 -->
      <div class="stats-card">
        <h4 class="stats-title">
          <el-icon><DataAnalysis /></el-icon>
          学习统计
        </h4>
        <div class="stats-grid">
          <div class="stat-item">
            <span class="stat-value">{{ statsData.videoCompleted }}/{{ statsData.videoTotal }}</span>
            <span class="stat-label">视频完成</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ statsData.exerciseCompleted }}/{{ statsData.exerciseTotal }}</span>
            <span class="stat-label">练习完成</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ statsData.totalTime }}</span>
            <span class="stat-label">累计学习</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ statsData.streakDays }}</span>
            <span class="stat-label">连续打卡</span>
          </div>
        </div>
        <div class="stats-actions">
          <el-button type="primary" size="small" @click="$emit('continue-learning')"><el-icon><VideoPlay /></el-icon>
            继续学习
          </el-button>
          <el-button size="small" @click="$emit('start-exercise')"><el-icon><Edit /></el-icon>
            开始练习
          </el-button>
        </div>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { Close, List, VideoPlay, VideoCamera, CircleCheck, DataAnalysis, Edit } from '@element-plus/icons-vue'

const props = defineProps({
  chapters: { type: Array, default: () => [] },
  currentLessonId: { type: [Number, String], default: null },
  expandedChapters: { type: Array, default: () => [] },
  statsData: { type: Object, default: () => ({}) },
  isMobile: { type: Boolean, default: false },
  drawerOpen: { type: Boolean, default: false }
})

const emit = defineEmits([
  'select-lesson',
  'continue-learning',
  'start-exercise',
  'update:expandedChapters',
  'update:drawerOpen'
])

// el-collapse v-model 桥接：读 props，写 emit
const expandedModel = computed({
  get: () => props.expandedChapters,
  set: (val) => emit('update:expandedChapters', val)
})

function getChapterProgress(chapterId) {
  const ch = props.chapters.find(c => c.id === chapterId)
  if (!ch?.lessons?.length) return 0
  const completed = ch.lessons.filter(l => l.status === 'COMPLETED').length
  return Math.round((completed / ch.lessons.length) * 100)
}
</script>

<style scoped>
.content-sidebar {
  --color-primary: #6366F1;
  --color-primary-dark: #4F46E5;
  --color-primary-darker: #4338CA;
  --color-primary-light: #EEF2FF;
  --color-primary-100: #E0E7FF;

  width: 380px;
  flex-shrink: 0;
  position: sticky;
  top: 140px;
  max-height: calc(100dvh - 160px);
  overflow-y: auto;
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  border: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
}

.sidebar-inner {
  padding: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  flex: 1;
}

.sidebar-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-md);
  font-weight: var(--weight-bold);
  color: var(--el-text-color-primary);
  margin: 0;
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

/* Collapse */
.chapter-collapse { border: none !important; }
.chapter-collapse :deep(.el-collapse-item__header) {
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-sm);
  padding: var(--space-2-5) var(--space-3);
  margin-bottom: var(--space-1);
  border: none;
}
.chapter-collapse :deep(.el-collapse-item__wrap) {
  border: none;
  background: transparent;
}
.chapter-collapse :deep(.el-collapse-item__content) { padding: 0 0 var(--space-2); }
.chapter-collapse :deep(.el-icon) { font-size: var(--text-base); }

.chapter-header {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
  width: 100%;
}
.chapter-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chapter-progress-bar {
  width: 60px;
  height: 4px;
  background: var(--el-border-color);
  border-radius: 2px;
  overflow: hidden;
}
.chapter-progress-fill {
  height: 100%;
  background: var(--color-primary);
  border-radius: 2px;
  transition: width 0.3s;
}
.chapter-progress-text { font-size: var(--text-xs); color: var(--el-text-color-secondary); min-width: 30px; text-align: right; }

.lesson-list { padding: var(--space-1) 0 var(--space-1) var(--space-2); }
.lesson-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2-5) var(--space-2-5);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-out);
  border-left: 3px solid transparent;
}
.lesson-item:hover { background: var(--role-primary-light-9); }
.lesson-item.active {
  background: var(--color-primary-light);
  border-left-color: var(--color-primary-dark);
}
.lesson-item.completed .lesson-title { color: var(--el-text-color-secondary); }

.lesson-status-icon { display: flex; align-items: center; font-size: var(--text-base); }
.lesson-title { flex: 1; font-size: var(--text-sm); color: var(--el-text-color-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lesson-item.active .lesson-title { color: var(--color-primary-dark); font-weight: var(--weight-medium); }
.lesson-duration { font-size: var(--text-xs); color: var(--el-text-color-secondary); white-space: nowrap; }

/* 统计卡片 */
.stats-card {
  background: linear-gradient(135deg, var(--color-primary-light), var(--color-primary-100));
  border-radius: var(--radius-md);
  padding: var(--space-4);
  border: 1px solid var(--color-primary-100);
}
.stats-title {
  display: flex;
  align-items: center;
  gap: var(--space-1-5);
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--color-primary-darker);
  margin: 0 0 var(--space-3);
}
.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}
.stat-item { display: flex; flex-direction: column; gap: 2px; }
.stat-value { font-size: var(--text-lg); font-weight: var(--weight-bold); color: var(--color-primary-darker); }
.stat-label { font-size: var(--text-xs); color: var(--color-primary); }
.stats-actions { display: flex; gap: var(--space-2); }
.stats-actions .el-button { flex: 1; }

/* 移动端 */
@media (max-width: 768px) {
  .content-sidebar {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    width: 100%;
    max-height: 70vh;
    border-radius: var(--radius-lg) var(--radius-lg) 0 0;
    z-index: 200;
    transform: translateY(100%);
    transition: transform 0.3s ease;
    box-shadow: var(--shadow-lg);
  }
  .content-sidebar.drawer-open { transform: translateY(0); }

  .drawer-close-btn {
    position: absolute;
    top: var(--space-3);
    right: var(--space-3);
    background: none;
    border: none;
    font-size: var(--text-xl);
    color: var(--el-text-color-secondary);
    cursor: pointer;
    z-index: 1;
    padding: var(--space-1);
  }
}
</style>
