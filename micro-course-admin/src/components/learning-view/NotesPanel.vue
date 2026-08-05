<!--
  学习内容面板（Round 11-3 从 LearningView.vue 拆分）
  NotesPanel: 课程内容 / 公告 / 讨论 三个 Tab 的内容展示
  Author: jackie
-->
<template>
  <div>
    <!-- 课程 Tab -->
    <transition name="panel-fade">
      <div v-if="activeTab === 'course'" class="tab-panel" key="course">
        <div class="course-content-card">
          <h3 class="content-title">
            <el-icon><Document /></el-icon>
            课程内容
          </h3>
          <div class="content-body">
            <p v-if="currentChapter?.description" class="chapter-desc">{{ currentChapter.description }}</p>
            <p v-else class="chapter-desc muted">本章节暂无课程内容描述</p>
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
    </transition>

    <transition name="panel-fade">
      <div v-if="activeTab === 'announcement'" class="tab-panel" key="announcement">
        <div class="empty-state-card">
          <el-icon size="48" color="#CBD5E1"><Bell /></el-icon>
          <p class="empty-title">暂无公告</p>
          <p class="empty-desc">课程公告将在此处显示</p>
        </div>
      </div>
    </transition>

    <transition name="panel-fade">
      <div v-if="activeTab === 'discussion'" class="tab-panel" key="discussion">
        <div class="empty-state-card">
          <el-icon size="48" color="#CBD5E1"><ChatDotRound /></el-icon>
          <p class="empty-title">参与课程讨论</p>
          <p class="empty-desc">发帖提问、交流心得，与同学和老师互动</p>
          <el-button type="primary" plain size="small" @click="goDiscussion">进入讨论区</el-button>
        </div>
      </div>
    </transition>

    <!-- 笔记 Tab（P1-C 补全 2026-08-04：原"笔记"按钮无任何功能） -->
    <transition name="panel-fade">
      <div v-if="activeTab === 'note'" class="tab-panel" key="note">
        <div class="note-panel">
          <div class="note-list">
            <div v-for="n in notes" :key="n.id" class="note-item">
              <p class="note-content">{{ n.content }}</p>
              <div class="note-meta">
                <span>{{ $formatDateTime(n.createdAt) }}</span>
                <el-button link type="danger" size="small" @click="handleDelete(n)">删除</el-button>
              </div>
            </div>
            <p v-if="notes.length === 0" class="note-empty">本章节暂无笔记，记录你的学习心得吧</p>
          </div>
          <div class="note-editor">
            <el-input
              v-model="noteContent"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
              placeholder="记录本章节的学习笔记..."
              aria-label="笔记内容"
            />
            <el-button type="primary" size="small" :disabled="!noteContent.trim()" :loading="saving" @click="handleSave">
              保存笔记
            </el-button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { Document, Bell, ChatDotRound } from '@element-plus/icons-vue'
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCourseNotes, createCourseNote, deleteCourseNote } from '@/api/note'
import { useRouter } from 'vue-router'

const props = defineProps({
  activeTab: { type: String, default: 'course' },
  currentChapter: { type: Object, default: null },
  courseId: { type: [Number, String], default: null }
})

defineEmits(['change-tab'])

const notes = ref([])
const noteContent = ref('')
const saving = ref(false)
const router = useRouter()

// 讨论区为独立页面：携带当前章节上下文跳转，避免学习视图内出现功能空壳
function goDiscussion() {
  const chapterId = props.currentChapter?.id
  router.push({
    path: '/student/discussions',
    query: chapterId ? { chapterId } : { courseId: props.courseId }
  })
}

async function loadNotes() {
  if (!props.courseId) return
  try {
    const { data } = await getCourseNotes({
      courseId: props.courseId,
      chapterId: props.currentChapter?.id || undefined
    })
    notes.value = data || []
  } catch {
    notes.value = []
  }
}

async function handleSave() {
  if (!noteContent.value.trim() || !props.courseId) return
  saving.value = true
  try {
    await createCourseNote({
      courseId: props.courseId,
      chapterId: props.currentChapter?.id || null,
      content: noteContent.value.trim()
    })
    ElMessage.success('笔记已保存')
    noteContent.value = ''
    await loadNotes()
  } catch {
    ElMessage.error('笔记保存失败，请重试')
  } finally {
    saving.value = false
  }
}

async function handleDelete(note) {
  try {
    await ElMessageBox.confirm('确定删除这条笔记？', '删除笔记', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteCourseNote(note.id)
    ElMessage.success('笔记已删除')
    await loadNotes()
  } catch {
    ElMessage.error('删除失败，请重试')
  }
}

watch(() => props.activeTab, (tab) => {
  if (tab === 'note') loadNotes()
})

watch(() => props.currentChapter?.id, () => {
  if (props.activeTab === 'note') loadNotes()
})
</script>

<style scoped>
.panel-fade-enter-active { animation: fadeIn 0.2s ease; }
.panel-fade-leave-active { animation: fadeIn 0.15s ease reverse; }
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
