<template>
  <div class="course-chapter-editor">
    <!-- 课程列表 -->
    <el-table
      :data="localData" stripe border size="small"
      :row-class-name="rowClassName"
      @row-click="onRowClick">
      <el-table-column prop="moduleName" label="模块" min-width="120">
        <template #default="{ row }">
          <el-input
v-model="row.moduleName" placeholder="如：专业基础模块" size="small"
                    @input="emitChange" @click.stop />
        </template>
      </el-table-column>
      <el-table-column prop="courseName" label="课程名称" min-width="160">
        <template #default="{ row }">
          <el-input
v-model="row.courseName" placeholder="必填" size="small"
                    @input="emitChange" @click.stop />
        </template>
      </el-table-column>
      <el-table-column prop="hours" label="学时" width="90">
        <template #default="{ row }">
          <el-input-number
v-model="row.hours" :min="0" :max="500" size="small" controls-position="right"
                           style="width:100%" @change="emitChange" @click.stop />
        </template>
      </el-table-column>
      <el-table-column prop="credits" label="学分" width="90">
        <template #default="{ row }">
          <el-input-number
v-model="row.credits" :min="0" :max="99.9" :precision="1" size="small" controls-position="right"
                           style="width:100%" @change="emitChange" @click.stop />
        </template>
      </el-table-column>
      <el-table-column prop="semester" label="学期" min-width="100">
        <template #default="{ row }">
          <el-input
v-model="row.semester" placeholder="如：第1学期" size="small"
                    @input="emitChange" @click.stop />
        </template>
      </el-table-column>
      <el-table-column label="章节" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="getChapters(row).length" type="info" size="small" effect="plain">
            {{ getChapters(row).length }} 章
          </el-tag>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="校验" width="80" align="center">
        <template #default="{ row }">
          <el-tooltip
            v-if="rowValidation(row).issues.length > 0"
            placement="top"
            :content="rowValidation(row).issues.map(i => i.message).join('\n')">
            <span :class="['validation-icon', `validation-${rowValidation(row).level}`]">
              <el-icon v-if="rowValidation(row).level === 'error'"><CircleCloseFilled /></el-icon>
              <el-icon v-else><WarningFilled /></el-icon>
              <span class="validation-text">{{ rowValidation(row).issues.length }}</span>
            </span>
          </el-tooltip>
          <el-tooltip v-else content="校验通过" placement="top">
            <span class="validation-icon validation-ok">
              <el-icon><CircleCheckFilled /></el-icon>
            </span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row, $index }">
          <el-button link type="primary" size="small" @click.stop="openChapterDrawer(row, $index)">
            编辑章节
          </el-button>
          <el-button link type="danger" size="small" @click.stop="removeRow($index)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="table-actions">
      <el-button type="primary" size="small" @click="addRow">+ 新增课程</el-button>
      <el-button size="small" @click="clearAll">清空</el-button>
    </div>

    <!-- 章节编辑 Drawer -->
    <el-drawer v-model="drawerVisible" :title="drawerTitle" direction="rtl" size="60%">
      <div v-if="currentCourse" class="chapter-drawer-body">
        <el-alert
          :title="`课程: ${currentCourse.courseName || '(未命名)'}`"
          type="info"
          :closable="false"
          show-icon
          class="mg-bottom-12" />
        <el-form :model="currentCourse" ref="chapterFormRef" label-width="80px" size="small">
          <el-table :data="currentCourse.chapters || []" border size="small">
            <el-table-column prop="sortOrder" label="#" width="60" align="center">
              <template #default="{ $index }">
                {{ $index + 1 }}
              </template>
            </el-table-column>
            <el-table-column label="章节名称" min-width="180">
              <template #default="{ row }">
                <el-input v-model="row.title" placeholder="必填" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="学时" width="100">
              <template #default="{ row }">
                <el-input-number v-model="row.hours" :min="0" :max="500" size="small" controls-position="right" style="width:100%" />
              </template>
            </el-table-column>
            <el-table-column label="描述" min-width="160">
              <template #default="{ row }">
                <el-input v-model="row.description" placeholder="可选" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" @click="removeChapter($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="mg-top-12">
            <el-button type="primary" plain size="small" @click="addChapter">+ 新增章节</el-button>
          </div>
        </el-form>
        <div v-if="!currentCourse.chapters || currentCourse.chapters.length === 0" class="empty-hint">
          暂无章节,点击 [+ 新增章节] 添加第一个章节
        </div>
      </div>
      <template #footer>
        <el-button @click="drawerVisible = false">关闭</el-button>
        <el-button type="primary" @click="saveChapters">完成</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { ElMessageBox } from 'element-plus'
import { CircleCheckFilled, CircleCloseFilled, WarningFilled } from '@element-plus/icons-vue'
import { validateCourseHours } from '@/utils/courseValidation'

function rowValidation(row) {
  return validateCourseHours(row)
}
function rowClassName({ row }) {
  const r = rowValidation(row)
  if (r.level === 'error') return 'course-row-error'
  if (r.level === 'warn') return 'course-row-warn'
  return ''
}

const props = defineProps({
  modelValue: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'change'])

const localData = ref([])
const drawerVisible = ref(false)
const currentCourseIndex = ref(-1)
const currentCourse = ref(null)
const chapterFormRef = ref(null)

watch(() => props.modelValue, (v) => {
  // 深拷贝,确保展开行修改不会影响父组件的 data
  localData.value = JSON.parse(JSON.stringify(v || []))
}, { immediate: true, deep: true })

const drawerTitle = computed(() => {
  if (!currentCourse.value) return '编辑章节'
  return `编辑章节 — ${currentCourse.value.courseName || '(未命名)'}`
})

function getChapters(row) {
  return row.chapters || []
}

function emitChange() {
  emit('update:modelValue', JSON.parse(JSON.stringify(localData.value)))
  emit('change', localData.value)
}

function addRow() {
  localData.value.push({
    moduleName: '',
    courseName: '',
    hours: 0,
    credits: 0,
    semester: '',
    chapters: []
  })
  emitChange()
}

function removeRow(index) {
  ElMessageBox.confirm('确认删除此课程?该课程的章节也会被删除。', '确认删除', {
    type: 'warning'
  }).then(() => {
    localData.value.splice(index, 1)
    emitChange()
  }).catch(() => {})
}

function clearAll() {
  ElMessageBox.confirm('确认清空所有课程?', '确认清空', {
    type: 'warning'
  }).then(() => {
    localData.value = []
    emitChange()
  }).catch(() => {})
}

function onRowClick() {
  // 占位, 实际编辑通过点击行内的"编辑章节"按钮
}

function openChapterDrawer(row, index) {
  currentCourseIndex.value = index
  // 深拷贝避免修改引用影响父组件
  if (!row.chapters) row.chapters = []
  currentCourse.value = JSON.parse(JSON.stringify(row))
  drawerVisible.value = true
}

function addChapter() {
  if (!currentCourse.value.chapters) currentCourse.value.chapters = []
  const maxSort = currentCourse.value.chapters.length
  // P2-1 修复: 新章节生成临时负 ID,确保 toggleChapter 引用有效,
  // 后端通过 oldIdToNewChapterMap 正确映射到新自增 ID
  const tempId = Date.now() + Math.floor(Math.random() * 10000)
  currentCourse.value.chapters.push({
    id: -tempId,
    title: '',
    description: '',
    hours: 0,
    sortOrder: maxSort
  })
}

function removeChapter(index) {
  currentCourse.value.chapters.splice(index, 1)
  // 重新设置 sortOrder
  currentCourse.value.chapters.forEach((c, i) => { c.sortOrder = i })
}

function saveChapters() {
  // 校验章节名称是否必填
  const chapters = currentCourse.value?.chapters || []
  for (let i = 0; i < chapters.length; i++) {
    if (!chapters[i].title || !chapters[i].title.trim()) {
      ElMessageBox.alert(`第 ${i + 1} 章名称不能为空`, '校验不通过', { type: 'warning' })
      return
    }
  }
  // 同步回 courses 列表
  if (currentCourseIndex.value >= 0 && currentCourseIndex.value < localData.value.length) {
    localData.value[currentCourseIndex.value] = currentCourse.value
  }
  emitChange()
  drawerVisible.value = false
  ElMessageBox.alert('章节已保存', '保存成功', { type: 'success' })
}
</script>

<style scoped>
.course-chapter-editor {
  width: 100%;
}
.table-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}
.muted {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
.chapter-drawer-body {
  padding: 0 20px;
}
.empty-hint {
  margin-top: 16px;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 4px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

/* 课程学时校验 - 行内 icon */
.validation-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  cursor: help;
  font-size: 16px;
}
.validation-ok {
  color: #67c23a;
}
.validation-warn {
  color: #e6a23c;
}
.validation-error {
  color: #f56c6c;
}
.validation-text {
  font-size: 11px;
  color: inherit;
  font-weight: 600;
}

/* 整行轻染色（更直观） */
:deep(.course-row-warn) {
  background: #fdf6ec !important;
}
:deep(.course-row-warn:hover > td) {
  background: #f9ead9 !important;
}
:deep(.course-row-error) {
  background: #fef0f0 !important;
}
:deep(.course-row-error:hover > td) {
  background: #fde2e2 !important;
}
</style>
