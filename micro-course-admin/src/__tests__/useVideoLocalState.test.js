import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { useVideoLocalState } from '@/composables/useVideoLocalState'

function createMemoryStorage() {
  const values = new Map()
  return {
    getItem(key) {
      return values.has(key) ? values.get(key) : null
    },
    setItem(key, value) {
      values.set(key, String(value))
    }
  }
}

describe('useVideoLocalState', () => {
  it('prefers server progress and only falls back to local position when missing', () => {
    const storage = createMemoryStorage()
    storage.setItem('video_progress_12', JSON.stringify({ time: 88, updatedAt: 1 }))
    const videoId = computed(() => 12)
    const currentTime = ref(45)

    const videoState = useVideoLocalState({
      videoId,
      currentTime,
      storage,
      formatTime: (seconds) => `T${seconds}`,
      now: () => 123456
    })

    videoState.lastPosition.value = 64
    videoState.loadLocalPosition()
    expect(videoState.lastPosition.value).toBe(64)

    videoState.lastPosition.value = 0
    videoState.loadLocalPosition()
    expect(videoState.lastPosition.value).toBe(88)

    videoState.saveLocalPosition(91)
    expect(storage.getItem('video_progress_12')).toBe(JSON.stringify({ time: 91, updatedAt: 123456 }))
  })

  it('loads, adds, timestamps, and deletes notes with storage persistence', async () => {
    const storage = createMemoryStorage()
    storage.setItem('video_notes_9', JSON.stringify([
      { id: 1, time: 10, content: 'old', createdAt: '2026-01-01T00:00:00.000Z' }
    ]))
    const videoId = computed(() => 9)
    const currentTime = ref(25)
    const confirmDelete = vi.fn().mockResolvedValue(undefined)

    const videoState = useVideoLocalState({
      videoId,
      currentTime,
      storage,
      formatTime: (seconds) => `T${seconds}`,
      confirmDelete,
      now: () => 222,
      nowIso: () => '2026-07-24T04:00:00.000Z'
    })

    videoState.loadNotesFromStorage()
    expect(videoState.notes.value).toHaveLength(1)

    videoState.noteText.value = 'new note'
    const added = videoState.addNote()
    expect(added).toBe(true)
    expect(videoState.noteText.value).toBe('')
    expect(videoState.notes.value[0]).toMatchObject({
      id: 222,
      time: 25,
      content: 'new note',
      createdAt: '2026-07-24T04:00:00.000Z'
    })

    videoState.insertNoteAtCurrentTime()
    expect(videoState.noteText.value).toBe('[T25] ')

    await videoState.deleteNote(1)
    expect(confirmDelete).toHaveBeenCalledTimes(1)
    expect(videoState.notes.value.find((note) => note.id === 1)).toBeUndefined()
    expect(JSON.parse(storage.getItem('video_notes_9'))).toHaveLength(1)
  })
})
