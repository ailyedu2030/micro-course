import Sortable from 'sortablejs'
import { onMounted, onUnmounted, nextTick } from 'vue'

export function useDragSort(sidebarRef, chapters, onSortEnd) {
  let chapterSortable = null
  const lessonSortables = []

  function init() {
    nextTick(() => {
      if (!sidebarRef.value) return
      chapterSortable = Sortable.create(sidebarRef.value, {
        group: 'chapters',
        handle: '.drag-handle',
        animation: 200,
        onEnd: () => { if (onSortEnd) onSortEnd() }
      })
      chapters.value.forEach((ch, idx) => {
        const el = sidebarRef.value.querySelector(`[data-chapter-id="${ch.id}"] .lesson-list`)
        if (el) {
          const s = Sortable.create(el, {
            group: 'lessons',
            handle: '.drag-handle',
            animation: 200,
            onEnd: () => { if (onSortEnd) onSortEnd() }
          })
          lessonSortables.push(s)
        }
      })
    })
  }

  function destroy() {
    if (chapterSortable) chapterSortable.destroy()
    lessonSortables.forEach(s => s.destroy())
  }

  return { init, destroy }
}
