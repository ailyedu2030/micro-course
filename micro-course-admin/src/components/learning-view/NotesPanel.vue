<!--
  学习内容面板（Round 11-3 从 LearningView.vue 拆分）
  NotesPanel: 课程内容 / 公告 / 讨论 三个 Tab 的内容展示
  Author: jackie
-->
<template>
  <div>
    <!-- 课程 Tab -->
    <div v-show="activeTab === 'course'" class="tab-panel">
      <div class="course-content-card">
        <h3 class="content-title">
          <el-icon><Document /></el-icon>
          课程内容
        </h3>
        <div class="content-body">
          <p v-if="currentChapter?.description" class="chapter-desc">{{ currentChapter.description }}</p>
          <p v-else class="chapter-desc muted">本章节暂无课程内容描述</p>
          <!-- 关键概念高亮列表 -->
          <div v-if="currentChapter?.keyConcepts?.length" class="key-concepts">
            <h4 class="concepts-title">关键概念</h4>
            <ul class="concepts-list">
              <li v-for="(concept, idx) in currentChapter.keyConcepts" :key="idx" class="concept-item">
                {{ concept }}
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>

    <!-- 公告 Tab -->
    <div v-show="activeTab === 'announcement'" class="tab-panel">
      <div class="empty-state-card">
        <el-icon size="48" color="#CBD5E1"><Bell /></el-icon>
        <p class="empty-title">暂无公告</p>
        <p class="empty-desc">课程公告将在此处显示</p>
      </div>
    </div>

    <!-- 讨论 Tab -->
    <div v-show="activeTab === 'discussion'" class="tab-panel">
      <div class="empty-state-card">
        <el-icon size="48" color="#CBD5E1"><ChatDotRound /></el-icon>
        <p class="empty-title">暂无讨论</p>
        <p class="empty-desc">点击开始与同学和老师讨论</p>
        <el-button type="primary" plain size="small" @click="$emit('change-tab', 'course')">返回课程</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Document, Bell, ChatDotRound } from '@element-plus/icons-vue'

defineProps({
  activeTab: { type: String, default: 'course' },
  currentChapter: { type: Object, default: null }
})

defineEmits(['change-tab'])
</script>

<style scoped>
.tab-panel { animation: fadeIn 0.2s ease; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }

.course-content-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  border: 1px solid var(--el-border-color-lighter);
  overflow: hidden;
}
.content-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin: 0;
}
.content-body { padding: var(--space-5); }
.chapter-desc {
  font-size: var(--text-base);
  color: var(--el-text-color-secondary);
  line-height: 1.7;
  margin: 0 0 var(--space-4);
}
.chapter-desc.muted { color: var(--el-text-color-secondary); font-style: italic; }

.key-concepts { margin-top: var(--space-4); }
.concepts-title {
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin: 0 0 var(--space-2-5);
}
.concepts-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.concept-item {
  font-size: var(--text-base);
  color: var(--el-text-color-secondary);
  padding: var(--space-2) var(--space-3);
  background: var(--color-primary-light, #EEF2FF);
  border-radius: var(--radius-sm);
  border-left: 3px solid var(--color-primary-dark, #4F46E5);
}

.empty-state-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  border: 1px solid var(--el-border-color-lighter);
  padding: 64px var(--space-8);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  text-align: center;
}
.empty-title { font-size: var(--text-md); font-weight: var(--weight-semibold); color: var(--el-text-color-primary); margin: 0; }
.empty-desc { font-size: var(--text-base); color: var(--el-text-color-secondary); margin: 0; }
</style>
