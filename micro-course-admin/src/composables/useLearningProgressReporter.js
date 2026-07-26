export function useLearningProgressReporter(options) {
  const {
    getDedupKey,
    shouldPersist,
    getProgressRecord,
    setProgressRecord,
    createPayload,
    updatePayload,
    createProgress,
    updateProgress,
    findExistingProgress,
    onPersisted,
    onError,
    dedupMs = 5000,
    storage = typeof window !== 'undefined' ? window.sessionStorage : null,
    now = () => Date.now()
  } = options

  let lastPersistAt = now()
  let failureCount = 0

  function shouldSkipByDedup(force) {
    if (force || !storage || typeof getDedupKey !== 'function') {
      return false
    }
    const dedupKey = getDedupKey()
    if (!dedupKey) {
      return false
    }
    const lastReport = storage.getItem(dedupKey)
    const currentNow = now()
    if (lastReport && (currentNow - parseInt(lastReport, 10)) < dedupMs) {
      return true
    }
    storage.setItem(dedupKey, String(currentNow))
    return false
  }

  async function persistProgress({ force = false, completed = false } = {}) {
    if (typeof shouldPersist === 'function' && !shouldPersist({ force, completed })) {
      return false
    }
    if (shouldSkipByDedup(force)) {
      return false
    }

    const currentNow = now()
    const watchDelta = Math.max(0, Math.floor((currentNow - lastPersistAt) / 1000))
    const context = {
      force,
      completed,
      now: currentNow,
      watchDelta,
      record: getProgressRecord?.() || null
    }

    try {
      if (context.record?.id) {
        await updateProgress(context.record.id, updatePayload(context))
      } else {
        try {
          const res = await createProgress(createPayload(context))
          const createdRecord = res?.data?.id
            ? res.data
            : (res?.data || res)
          if (createdRecord?.id) {
            setProgressRecord?.(createdRecord)
            context.record = createdRecord
          }
        } catch (createError) {
          if (typeof findExistingProgress !== 'function') {
            throw createError
          }
          const existingRecord = await findExistingProgress({ ...context, error: createError })
          if (!existingRecord?.id) {
            throw createError
          }
          setProgressRecord?.(existingRecord)
          context.record = existingRecord
          await updateProgress(existingRecord.id, updatePayload(context))
        }
      }

      lastPersistAt = currentNow
      failureCount = 0
      onPersisted?.(context)
      return true
    } catch (error) {
      failureCount += 1
      onError?.({ error, failureCount, context })
      return false
    }
  }

  function resetProgressReporter(timestamp = now()) {
    lastPersistAt = timestamp
    failureCount = 0
  }

  return {
    persistProgress,
    resetProgressReporter
  }
}
