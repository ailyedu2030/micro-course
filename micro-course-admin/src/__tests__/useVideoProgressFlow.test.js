import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { useVideoProgressFlow } from '@/composables/useVideoProgressFlow'

describe('useVideoProgressFlow', () => {
  function createReporterFactory() {
    const persistProgress = vi.fn().mockResolvedValue(undefined)
    const resetProgressReporter = vi.fn()
    let capturedOptions = null

    const reporterFactory = vi.fn((options) => {
      capturedOptions = options
      return {
        persistProgress,
        resetProgressReporter
      }
    })

    return {
      reporterFactory,
      persistProgress,
      resetProgressReporter,
      getCapturedOptions: () => capturedOptions
    }
  }

  it('skips progress persistence when the video snapshot is unavailable', async () => {
    const { reporterFactory, persistProgress } = createReporterFactory()
    const videoRef = ref(null)

    const progressFlow = useVideoProgressFlow({
      reporterFactory,
      videoRef,
      videoId: computed(() => 88),
      courseId: computed(() => 9),
      chapterId: computed(() => 5),
      userId: computed(() => 3)
    })

    await progressFlow.reportProgress()

    expect(progressFlow.progressId.value).toBe(null)
    expect(persistProgress).not.toHaveBeenCalled()
  })

  it('skips non-forced persistence when progress delta is below one percent and no retry is pending', async () => {
    const { reporterFactory, persistProgress, getCapturedOptions } = createReporterFactory()
    const videoRef = ref({
      currentTime: 10,
      duration: 100,
      paused: false
    })

    const progressFlow = useVideoProgressFlow({
      reporterFactory,
      videoRef,
      videoId: computed(() => 88),
      courseId: computed(() => 9),
      chapterId: computed(() => 5),
      userId: computed(() => 3),
      saveLocalPosition: vi.fn()
    })

    getCapturedOptions().onPersisted()
    videoRef.value.currentTime = 10.5
    await progressFlow.reportProgress()

    expect(persistProgress).not.toHaveBeenCalled()
  })

  it('persists progress when a retry is pending and only shows the warning once', async () => {
    const { reporterFactory, persistProgress, getCapturedOptions } = createReporterFactory()
    const consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const storage = {
      getItem: vi.fn().mockReturnValueOnce(null).mockReturnValueOnce('1'),
      setItem: vi.fn()
    }
    const showWarning = vi.fn()
    const videoRef = ref({
      currentTime: 10,
      duration: 100,
      paused: false
    })

    const progressFlow = useVideoProgressFlow({
      reporterFactory,
      videoRef,
      videoId: computed(() => 88),
      courseId: computed(() => 9),
      chapterId: computed(() => 5),
      userId: computed(() => 3),
      showWarning,
      storage
    })

    getCapturedOptions().onPersisted()
    videoRef.value.currentTime = 10.5
    getCapturedOptions().onError({ error: new Error('network') })
    await progressFlow.reportProgress()
    getCapturedOptions().onError({ error: new Error('network again') })

    expect(persistProgress).toHaveBeenCalledTimes(1)
    expect(showWarning).toHaveBeenCalledTimes(1)
    expect(showWarning).toHaveBeenCalledWith('进度上报失败,请检查网络')
    expect(storage.setItem).toHaveBeenCalledWith('progress_error_88', '1')
    expect(consoleWarnSpy).toHaveBeenCalledTimes(2)

    consoleWarnSpy.mockRestore()
  })

  it('updates the progress id and stores the latest local position after persistence', () => {
    const { reporterFactory, getCapturedOptions } = createReporterFactory()
    const saveLocalPosition = vi.fn()
    const progressId = ref(null)
    const videoRef = ref({
      currentTime: 25,
      duration: 100,
      paused: false
    })

    useVideoProgressFlow({
      reporterFactory,
      videoRef,
      progressIdRef: progressId,
      videoId: computed(() => 88),
      courseId: computed(() => 9),
      chapterId: computed(() => 5),
      userId: computed(() => 3),
      saveLocalPosition
    })

    getCapturedOptions().setProgressRecord({ id: 321 })
    getCapturedOptions().onPersisted()

    expect(progressId.value).toBe(321)
    expect(saveLocalPosition).toHaveBeenCalledWith(25)
  })
})
