import { describe, expect, it, vi } from 'vitest'

import { useLearningProgressReporter } from '@/composables/useLearningProgressReporter'

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

describe('useLearningProgressReporter', () => {
  it('deduplicates repeated reports and preserves elapsed watch time', async () => {
    const storage = createMemoryStorage()
    let currentNow = 1000
    const updateProgress = vi.fn().mockResolvedValue({})

    const reporter = useLearningProgressReporter({
      getDedupKey: () => 'progress_dedup_lesson_7',
      shouldPersist: () => true,
      getProgressRecord: () => ({ id: 7 }),
      createPayload: () => ({}),
      updatePayload: ({ watchDelta }) => ({ watchDelta, videoPosition: 42 }),
      createProgress: vi.fn(),
      updateProgress,
      storage,
      now: () => currentNow
    })

    await reporter.persistProgress()
    currentNow += 1000
    await reporter.persistProgress()
    currentNow += 6000
    await reporter.persistProgress()

    expect(updateProgress).toHaveBeenCalledTimes(2)
    expect(updateProgress).toHaveBeenNthCalledWith(1, 7, { watchDelta: 0, videoPosition: 42 })
    expect(updateProgress).toHaveBeenNthCalledWith(2, 7, { watchDelta: 7, videoPosition: 42 })
  })

  it('reuses an existing record after create conflict and updates completion state', async () => {
    const storage = createMemoryStorage()
    let progressRecord = null
    const createProgress = vi.fn().mockRejectedValue(new Error('duplicate key'))
    const updateProgress = vi.fn().mockResolvedValue({})
    const findExistingProgress = vi.fn().mockResolvedValue({ id: 18, sectionId: 9 })

    const reporter = useLearningProgressReporter({
      getDedupKey: () => 'progress_dedup_lesson_9',
      shouldPersist: () => true,
      getProgressRecord: () => progressRecord,
      setProgressRecord: (record) => {
        progressRecord = record
      },
      createPayload: ({ completed }) => ({ completed }),
      updatePayload: ({ completed }) => ({ completed }),
      createProgress,
      updateProgress,
      findExistingProgress,
      storage,
      now: () => 5000
    })

    const persisted = await reporter.persistProgress({ force: true, completed: true })

    expect(persisted).toBe(true)
    expect(createProgress).toHaveBeenCalledWith({ completed: true })
    expect(findExistingProgress).toHaveBeenCalledTimes(1)
    expect(progressRecord).toEqual({ id: 18, sectionId: 9 })
    expect(updateProgress).toHaveBeenCalledWith(18, { completed: true })
  })
})
