import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '../../../utils/request'

export const useWorkspaceStore = defineStore('workspace', () => {
  // === 选中状态 ===
  const selectedItem = ref(null)     // { type: 'chapter'|'lesson', id: number }
  const courseData = ref({})
  const courseStructure = ref([])     // [{ chapter, lessons: [] }]
  const activeTab = ref('content')    // content | students | stats | settings
  const expandedChapters = ref(new Set())

  // === 自动保存 ===
  const dirtyQueue = ref([])          // [{ entity, field, value }]
  const isSaving = ref(false)
  const saveError = ref(null)
  let saveTimer = null

  // === 撤销/重做 ===
  const undoStack = ref([])           // 最大50步
  const redoStack = ref([])

  // === 进度 ===
  const progress = computed(() => {
    const chapters = courseStructure.value
    let total = 0, done = 0
    for (const ch of chapters) {
      if (ch.lessons) {
        for (const l of ch.lessons) {
          total++
          if (l.videoUrl || l.slideCount > 0) done++
        }
      }
    }
    return { total, done, percent: total > 0 ? Math.round(done / total * 100) : 0 }
  })

  // === 自动保存 ===
  function markDirty(entity, field, value) {
    dirtyQueue.value.push({ entity, field, value, time: Date.now() })
    scheduleSave()
  }

  function scheduleSave() {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(() => flushSave(), 800)
  }

  async function flushSave() {
    if (isSaving.value || dirtyQueue.value.length === 0) return
    isSaving.value = true
    saveError.value = null
    try {
      // Group by entity
      const groups = {}
      for (const d of dirtyQueue.value) {
        const key = `${d.entity.type}:${d.entity.id}`
        if (!groups[key]) groups[key] = { entity: d.entity, changes: {} }
        groups[key].changes[d.field] = d.value
      }
      for (const g of Object.values(groups)) {
        const { entity, changes } = g
        const url = entity.type === 'lesson' ? `/api/lessons/${entity.id}`
          : entity.type === 'chapter' ? `/api/chapters/${entity.id}`
          : `/api/courses/${entity.id}`
        await request({
          method: 'PUT',
          url,
          data: changes
        })
      }
      dirtyQueue.value = []
    } catch (e) {
      saveError.value = '自动保存失败，请检查网络'
    } finally {
      isSaving.value = false
    }
  }

  // === 撤销/重做 ===
  function pushUndo(snapshot) {
    undoStack.value.push(JSON.parse(JSON.stringify(snapshot)))
    if (undoStack.value.length > 50) undoStack.value.shift()
    redoStack.value = []
  }

  function undo() {
    if (undoStack.value.length === 0) return null
    const current = { selectedItem: selectedItem.value, courseStructure: courseStructure.value }
    redoStack.value.push(current)
    return undoStack.value.pop()
  }

  function redo() {
    if (redoStack.value.length === 0) return null
    const current = { selectedItem: selectedItem.value, courseStructure: courseStructure.value }
    undoStack.value.push(current)
    return redoStack.value.pop()
  }

  return {
    selectedItem, courseData, courseStructure, activeTab, expandedChapters,
    dirtyQueue, isSaving, saveError, progress,
    undoStack, redoStack,
    markDirty, scheduleSave, flushSave, pushUndo, undo, redo
  }
})
