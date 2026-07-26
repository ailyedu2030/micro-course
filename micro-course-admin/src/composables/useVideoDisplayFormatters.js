import { formatDate } from '@/utils/format'

export function useVideoDisplayFormatters() {
  function formatTime(seconds) {
    if (!seconds || Number.isNaN(seconds)) return '00:00'
    const h = Math.floor(seconds / 3600)
    const m = Math.floor((seconds % 3600) / 60)
    const s = Math.floor(seconds % 60)
    if (h > 0) {
      return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
    }
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }

  function formatDateTime(isoString) {
    return formatDate(isoString)
  }

  return {
    formatTime,
    formatDateTime
  }
}
