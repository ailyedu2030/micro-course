import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { useLearningProgressHeartbeat } from '@/composables/useLearningProgressHeartbeat'

function createHarness(options = {}) {
  return defineComponent({
    setup() {
      return useLearningProgressHeartbeat({
        intervalMs: 10000,
        onInterval: options.onInterval,
        onBeforeUnmountPersist: options.onBeforeUnmountPersist
      })
    },
    template: '<div />'
  })
}

describe('useLearningProgressHeartbeat', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('starts only one heartbeat timer and stops cleanly', async () => {
    vi.useFakeTimers()
    const onInterval = vi.fn()
    const wrapper = mount(createHarness({ onInterval }))

    wrapper.vm.startHeartbeat()
    wrapper.vm.startHeartbeat()

    await vi.advanceTimersByTimeAsync(20000)
    expect(onInterval).toHaveBeenCalledTimes(2)

    wrapper.vm.stopHeartbeat()
    await vi.advanceTimersByTimeAsync(10000)
    expect(onInterval).toHaveBeenCalledTimes(2)
  })

  it('restarts the heartbeat and flushes once on component unmount', async () => {
    vi.useFakeTimers()
    const onInterval = vi.fn()
    const onBeforeUnmountPersist = vi.fn().mockResolvedValue(undefined)
    const wrapper = mount(createHarness({ onInterval, onBeforeUnmountPersist }))

    wrapper.vm.startHeartbeat()
    await vi.advanceTimersByTimeAsync(10000)
    expect(onInterval).toHaveBeenCalledTimes(1)

    wrapper.vm.restartHeartbeat()
    await vi.advanceTimersByTimeAsync(10000)
    expect(onInterval).toHaveBeenCalledTimes(2)

    wrapper.unmount()
    await Promise.resolve()
    expect(onBeforeUnmountPersist).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(10000)
    expect(onInterval).toHaveBeenCalledTimes(2)
  })
})
