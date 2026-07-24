import { ref, unref } from 'vue'

export function useVideoNoteActions(options = {}) {
  const {
    addStoredNote = () => false,
    deleteStoredNote = async () => false,
    insertStoredNoteAtCurrentTime = () => {},
    videoRef,
    showSuccessMessage = () => {}
  } = options

  const video = videoRef ?? ref(null)

  function addNote() {
    if (!addStoredNote()) return
    showSuccessMessage('笔记已添加')
  }

  async function deleteNote(id) {
    const deleted = await deleteStoredNote(id)
    if (!deleted) return
    showSuccessMessage('笔记已删除')
  }

  function insertNoteAtCurrentTime() {
    insertStoredNoteAtCurrentTime()
  }

  function seekToTime(time) {
    const currentVideo = unref(video)
    if (!currentVideo) return
    currentVideo.currentTime = time
  }

  return {
    addNote,
    deleteNote,
    insertNoteAtCurrentTime,
    seekToTime
  }
}
