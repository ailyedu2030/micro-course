import { describe, expect, it } from 'vitest'

import { useVideoDisplayFormatters } from '@/composables/useVideoDisplayFormatters'

describe('useVideoDisplayFormatters', () => {
  it('formats playback seconds into mm:ss or h:mm:ss', () => {
    const { formatTime } = useVideoDisplayFormatters()

    expect(formatTime(undefined)).toBe('00:00')
    expect(formatTime(Number.NaN)).toBe('00:00')
    expect(formatTime(5)).toBe('00:05')
    expect(formatTime(65)).toBe('01:05')
    expect(formatTime(3665)).toBe('1:01:05')
  })

  it('formats discussion timestamps as date-only labels', () => {
    const { formatDateTime } = useVideoDisplayFormatters()

    expect(formatDateTime('2026-07-24T12:34:56Z')).toBe('2026-07-24')
    expect(formatDateTime('')).toBe('')
    expect(formatDateTime('invalid-date')).toBe('')
  })
})
