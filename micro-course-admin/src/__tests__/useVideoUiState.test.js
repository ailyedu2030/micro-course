import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { useVideoUiState } from '@/composables/useVideoUiState'

function createHarness(context = {}) {
  let api

  const component = defineComponent({
    setup() {
      api = useVideoUiState({
        getViewportWidth: context.getViewportWidth,
        resizeDelayMs: context.resizeDelayMs,
        objectivesHideDelayMs: context.objectivesHideDelayMs
      })

      return api
    },
    template: '<div />'
  })

  return {
    component,
    getApi: () => api
  }
}

describe('useVideoUiState', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('debounces viewport mode updates on resize', async () => {
    vi.useFakeTimers()
    let viewportWidth = 1024
    const { component, getApi } = createHarness({
      getViewportWidth: () => viewportWidth,
      resizeDelayMs: 200
    })

    mount(component)
    const uiState = getApi()

    expect(uiState.isMobile.value).toBe(false)

    viewportWidth = 640
    uiState.handleResize()
    await vi.advanceTimersByTimeAsync(199)
    expect(uiState.isMobile.value).toBe(false)

    await vi.advanceTimersByTimeAsync(1)
    expect(uiState.isMobile.value).toBe(true)
  })

  it('shows the objectives overlay and resets the auto-hide timer', async () => {
    vi.useFakeTimers()
    const { component, getApi } = createHarness({
      getViewportWidth: () => 640,
      objectivesHideDelayMs: 3000
    })

    mount(component)
    const uiState = getApi()

    uiState.showObjectivesOverlay()
    expect(uiState.showObjectives.value).toBe(true)

    await vi.advanceTimersByTimeAsync(2000)
    uiState.showObjectivesOverlay()
    await vi.advanceTimersByTimeAsync(2000)
    expect(uiState.showObjectives.value).toBe(true)

    await vi.advanceTimersByTimeAsync(1000)
    expect(uiState.showObjectives.value).toBe(false)
  })
})
