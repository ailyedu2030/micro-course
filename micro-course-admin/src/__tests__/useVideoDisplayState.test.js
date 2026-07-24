import { computed, defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { useVideoDisplayState } from '@/composables/useVideoDisplayState'

function createHarness(context = {}) {
  let api
  const currentTime = ref(context.currentTime ?? 0)
  const duration = ref(context.duration ?? 0)
  const userId = ref(context.userId ?? null)

  const component = defineComponent({
    setup() {
      api = useVideoDisplayState({
        currentTimeRef: currentTime,
        durationRef: duration,
        userIdRef: computed(() => userId.value),
        nowFn: context.nowFn,
        refreshIntervalMs: context.refreshIntervalMs,
        setIntervalFn: context.setIntervalFn,
        clearIntervalFn: context.clearIntervalFn
      })

      return api
    },
    template: '<div />'
  })

  return {
    component,
    currentTime,
    duration,
    userId,
    getApi: () => api
  }
}

describe('useVideoDisplayState', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('computes progress percent with bounds protection', async () => {
    const { component, currentTime, duration, getApi } = createHarness({
      currentTime: 30,
      duration: 120
    })

    mount(component)
    const displayState = getApi()

    expect(displayState.progressPercent.value).toBe(25)

    currentTime.value = 300
    expect(displayState.progressPercent.value).toBe(100)

    currentTime.value = -5
    expect(displayState.progressPercent.value).toBe(0)

    duration.value = 0
    expect(displayState.progressPercent.value).toBe(0)
  })

  it('formats the watermark text and refreshes it on the configured interval', async () => {
    vi.useFakeTimers()
    let now = new Date('2026-07-24T10:05:00')
    const { component, userId, getApi } = createHarness({
      userId: 42,
      nowFn: () => now,
      refreshIntervalMs: 60_000
    })

    const wrapper = mount(component)
    const displayState = getApi()

    expect(displayState.watermarkText.value).toBe('用户 42 · 20260724 10:05')

    now = new Date('2026-07-24T10:06:00')
    await vi.advanceTimersByTimeAsync(60_000)
    expect(displayState.watermarkText.value).toBe('用户 42 · 20260724 10:06')

    userId.value = null
    expect(displayState.watermarkText.value).toBe('用户 unknown · 20260724 10:06')

    wrapper.unmount()
    now = new Date('2026-07-24T10:07:00')
    await vi.advanceTimersByTimeAsync(60_000)
    expect(displayState.watermarkText.value).toBe('用户 unknown · 20260724 10:06')
  })
})
