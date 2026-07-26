import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { useVideoTouchGestures } from '@/composables/useVideoTouchGestures'

function createTouchTarget(rect = { left: 0, width: 300 }) {
  return {
    closest: vi.fn().mockReturnValue({
      getBoundingClientRect: () => rect
    })
  }
}

function createTouchEvent({ x, y, target, changed = false }) {
  const touch = { clientX: x, clientY: y }
  return changed
    ? { changedTouches: [touch], target }
    : { touches: [touch], target }
}

function createHarness(context = {}) {
  let api

  const component = defineComponent({
    setup() {
      const isMobile = ref(context.isMobile ?? true)
      const videoRef = ref(context.video ?? null)

      api = useVideoTouchGestures({
        isMobile,
        videoRef,
        changeVolume: context.changeVolume,
        skipBackward: context.skipBackward,
        skipForward: context.skipForward,
        now: context.now,
        indicatorHideDelayMs: context.indicatorHideDelayMs,
        seekIndicatorHideDelayMs: context.seekIndicatorHideDelayMs,
        doubleTapWindowMs: context.doubleTapWindowMs
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

describe('useVideoTouchGestures', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('adjusts volume on right-side vertical swipe and shows the indicator', () => {
    const changeVolume = vi.fn()
    const video = { volume: 0.5, style: {} }
    const target = createTouchTarget()
    const { component, getApi } = createHarness({ video, changeVolume })

    mount(component)
    const gestures = getApi()

    gestures.handleTouchStart(createTouchEvent({ x: 250, y: 100, target }))
    gestures.handleTouchMove(createTouchEvent({ x: 250, y: 60, target }))

    expect(changeVolume).toHaveBeenCalledWith(66)
    expect(gestures.volumeIndicatorVisible.value).toBe(true)
    expect(gestures.volumeIndicatorValue.value).toBe(66)
    expect(gestures.gestureIndicatorX.value).toBe(250)
    expect(gestures.gestureIndicatorY.value).toBe(60)
  })

  it('adjusts brightness on left-side vertical swipe and updates the video filter', () => {
    const video = { volume: 0.5, style: {} }
    const target = createTouchTarget()
    const { component, getApi } = createHarness({ video })

    mount(component)
    const gestures = getApi()

    gestures.handleTouchStart(createTouchEvent({ x: 50, y: 100, target }))
    gestures.handleTouchMove(createTouchEvent({ x: 50, y: 150, target }))

    expect(gestures.brightnessIndicatorVisible.value).toBe(true)
    expect(gestures.brightnessIndicatorValue.value).toBe(80)
    expect(video.style.filter).toBe('brightness(0.8)')
  })

  it('detects double taps for backward and forward seek and hides indicators after the timeout', async () => {
    vi.useFakeTimers()
    let nowValue = 1000
    const now = vi.fn(() => nowValue)
    const skipBackward = vi.fn()
    const skipForward = vi.fn()
    const video = { volume: 0.5, style: {} }
    const leftTarget = createTouchTarget()
    const rightTarget = createTouchTarget()
    const { component, getApi } = createHarness({
      video,
      skipBackward,
      skipForward,
      now
    })

    mount(component)
    const gestures = getApi()

    gestures.handleTouchStart(createTouchEvent({ x: 20, y: 100, target: leftTarget }))
    gestures.handleTouchEnd(createTouchEvent({ x: 20, y: 100, target: leftTarget, changed: true }))
    nowValue += 100
    gestures.handleTouchStart(createTouchEvent({ x: 20, y: 100, target: leftTarget }))
    gestures.handleTouchEnd(createTouchEvent({ x: 20, y: 100, target: leftTarget, changed: true }))

    expect(skipBackward).toHaveBeenCalledTimes(1)
    expect(gestures.showSeekIndicator.value).toBe(true)
    expect(gestures.seekIndicatorDir.value).toBe('backward')

    await vi.advanceTimersByTimeAsync(600)
    expect(gestures.showSeekIndicator.value).toBe(false)

    nowValue += 400
    gestures.handleTouchStart(createTouchEvent({ x: 280, y: 100, target: rightTarget }))
    gestures.handleTouchEnd(createTouchEvent({ x: 280, y: 100, target: rightTarget, changed: true }))
    nowValue += 100
    gestures.handleTouchStart(createTouchEvent({ x: 280, y: 100, target: rightTarget }))
    gestures.handleTouchEnd(createTouchEvent({ x: 280, y: 100, target: rightTarget, changed: true }))

    expect(skipForward).toHaveBeenCalledTimes(1)
    expect(gestures.seekIndicatorDir.value).toBe('forward')
  })
})
