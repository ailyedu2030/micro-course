import { computed, ref, unref } from 'vue'

export function useVideoLocalState(options = {}) {
  const {
    videoId,
    currentTime,
    storage = typeof window !== 'undefined' ? window.localStorage : null,
    formatTime = (seconds) => String(seconds ?? 0),
    confirmDelete = async () => {},
    onStorageError,
    now = () => Date.now(),
    nowIso = () => new Date().toISOString()
  } = options

  const lastPosition = ref(0)
  const notes = ref([])
  const noteText = ref('')

  const progressStorageKey = computed(() => {
    const id = unref(videoId)
    if (!id || (typeof id !== 'string' && typeof id !== 'number')) return null
    return `video_progress_${id}`
  })

  const notesStorageKey = computed(() => {
    const id = unref(videoId)
    if (!id || (typeof id !== 'string' && typeof id !== 'number')) return null
    return `video_notes_${id}`
  })

  function reportStorageError(type, error) {
    onStorageError?.({ type, error })
  }

  function saveLocalPosition(time) {
    if (!storage || !progressStorageKey.value) return false
    try {
      storage.setItem(progressStorageKey.value, JSON.stringify({ time, updatedAt: now() }))
      return true
    } catch (error) {
      reportStorageError('position_save', error)
      return false
    }
  }

  function loadLocalPosition() {
    try {
      if (lastPosition.value > 0 || !storage || !progressStorageKey.value) return false
      const saved = storage.getItem(progressStorageKey.value)
      if (!saved) return false
      const { time } = JSON.parse(saved)
      if (time > 0) {
        lastPosition.value = time
        return true
      }
      return false
    } catch (error) {
      reportStorageError('position_load', error)
      return false
    }
  }

  function saveNotesToStorage() {
    if (!storage || !notesStorageKey.value) return false
    try {
      storage.setItem(notesStorageKey.value, JSON.stringify(notes.value))
      return true
    } catch (error) {
      reportStorageError('notes_save', error)
      return false
    }
  }

  function loadNotesFromStorage() {
    if (!storage || !notesStorageKey.value) return false
    try {
      const saved = storage.getItem(notesStorageKey.value)
      if (!saved) return false
      const parsed = JSON.parse(saved)
      if (Array.isArray(parsed)) {
        notes.value = parsed
        return true
      }
      return false
    } catch (error) {
      reportStorageError('notes_load', error)
      return false
    }
  }

  function addNote() {
    if (!noteText.value.trim()) return false
    const note = {
      id: now(),
      time: unref(currentTime) || 0,
      content: noteText.value.trim(),
      createdAt: nowIso()
    }
    notes.value.unshift(note)
    noteText.value = ''
    saveNotesToStorage()
    return true
  }

  async function deleteNote(id) {
    try {
      await confirmDelete?.(id)
    } catch {
      return false
    }
    notes.value = notes.value.filter((note) => note.id !== id)
    saveNotesToStorage()
    return true
  }

  function insertNoteAtCurrentTime() {
    noteText.value = `[${formatTime(unref(currentTime) || 0)}] ${noteText.value}`
  }

  return {
    lastPosition,
    notes,
    noteText,
    progressStorageKey,
    notesStorageKey,
    saveLocalPosition,
    loadLocalPosition,
    saveNotesToStorage,
    loadNotesFromStorage,
    addNote,
    deleteNote,
    insertNoteAtCurrentTime
  }
}
