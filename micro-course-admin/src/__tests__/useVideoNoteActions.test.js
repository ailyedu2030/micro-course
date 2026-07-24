import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { useVideoNoteActions } from '@/composables/useVideoNoteActions'

describe('useVideoNoteActions', () => {
  it('shows a success message only when adding a note succeeds', () => {
    const showSuccessMessage = vi.fn()
    const addStoredNote = vi.fn()
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true)

    const noteActions = useVideoNoteActions({
      addStoredNote,
      insertStoredNoteAtCurrentTime: vi.fn(),
      deleteStoredNote: vi.fn(),
      videoRef: ref(null),
      showSuccessMessage
    })

    noteActions.addNote()
    noteActions.addNote()

    expect(addStoredNote).toHaveBeenCalledTimes(2)
    expect(showSuccessMessage).toHaveBeenCalledTimes(1)
    expect(showSuccessMessage).toHaveBeenCalledWith('笔记已添加')
  })

  it('shows a success message only when deleting a note succeeds', async () => {
    const showSuccessMessage = vi.fn()
    const deleteStoredNote = vi.fn()
      .mockResolvedValueOnce(false)
      .mockResolvedValueOnce(true)

    const noteActions = useVideoNoteActions({
      addStoredNote: vi.fn(),
      insertStoredNoteAtCurrentTime: vi.fn(),
      deleteStoredNote,
      videoRef: ref(null),
      showSuccessMessage
    })

    await noteActions.deleteNote(1)
    await noteActions.deleteNote(2)

    expect(deleteStoredNote).toHaveBeenCalledWith(1)
    expect(deleteStoredNote).toHaveBeenCalledWith(2)
    expect(showSuccessMessage).toHaveBeenCalledTimes(1)
    expect(showSuccessMessage).toHaveBeenCalledWith('笔记已删除')
  })

  it('delegates note timestamp insertion and seeks the video element', () => {
    const insertStoredNoteAtCurrentTime = vi.fn()
    const videoRef = ref({ currentTime: 10 })

    const noteActions = useVideoNoteActions({
      addStoredNote: vi.fn(),
      insertStoredNoteAtCurrentTime,
      deleteStoredNote: vi.fn(),
      videoRef,
      showSuccessMessage: vi.fn()
    })

    noteActions.insertNoteAtCurrentTime()
    noteActions.seekToTime(88)

    expect(insertStoredNoteAtCurrentTime).toHaveBeenCalledTimes(1)
    expect(videoRef.value.currentTime).toBe(88)
  })
})
