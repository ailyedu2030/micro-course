import { defineComponent, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { useVideoPlaybackControls } from '@/composables/useVideoPlaybackControls'

function createHarness(context = {}) {
  let api

  const component = defineComponent({
    setup() {
      const videoRef = ref(context.video ?? null)
      const videoContainerRef = ref(context.videoContainer ?? null)
      const progressTrackRef = ref(context.progressTrack ?? null)
      const lastPosition = ref(context.lastPosition ?? 0)

      api = useVideoPlaybackControls({
        videoRef,
        videoContainerRef,
        progressTrackRef,
        getLastPosition: () => lastPosition.value,
        documentRef: context.documentRef,
        hideControlsDelayMs: context.hideControlsDelayMs ?? 3000,
        speedToastDurationMs: context.speedToastDurationMs ?? 1500
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

describe('useVideoPlaybackControls', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('toggles playback and updates volume, mute, and speed state', async () => {
    const video = {
      paused: true,
      muted: false,
      duration: 120,
      currentTime: 10,
      volume: 1,
      playbackRate: 1,
      play: vi.fn().mockImplementation(() => {
        video.paused = false
        return Promise.resolve()
      }),
      pause: vi.fn().mockImplementation(() => {
        video.paused = true
      })
    }
    const { component, getApi } = createHarness({ video })
    mount(component)
    const controls = getApi()

    controls.togglePlay()
    await Promise.resolve()
    expect(video.play).toHaveBeenCalledTimes(1)
    expect(controls.isPlaying.value).toBe(true)

    controls.togglePlay()
    expect(video.pause).toHaveBeenCalledTimes(1)
    expect(controls.isPlaying.value).toBe(false)

    controls.changeVolume(35)
    expect(video.volume).toBe(0.35)
    expect(controls.volumePercent.value).toBe(35)
    expect(controls.isMuted.value).toBe(false)

    controls.toggleMute()
    expect(video.muted).toBe(true)
    expect(controls.isMuted.value).toBe(true)

    controls.changeSpeed(1.5)
    expect(video.playbackRate).toBe(1.5)
    expect(controls.playbackRate.value).toBe(1.5)
    expect(controls.speedToastVisible.value).toBe(true)
  })

  it('restores playback state on canplay, auto-hides controls, and clears timers on unmount', async () => {
    vi.useFakeTimers()
    const video = {
      paused: false,
      muted: false,
      duration: 150,
      currentTime: 0,
      volume: 1,
      playbackRate: 1
    }
    const { component, getApi } = createHarness({ video, lastPosition: 40 })
    const wrapper = mount(component)
    const controls = getApi()

    controls.onCanPlay()
    expect(controls.duration.value).toBe(150)
    expect(video.currentTime).toBe(40)

    controls.isPlaying.value = true
    controls.hideControlsDelayed()
    await vi.advanceTimersByTimeAsync(3000)
    expect(controls.controlsVisible.value).toBe(false)

    controls.showControls()
    controls.hideControlsDelayed()
    wrapper.unmount()
    await nextTick()
    await vi.advanceTimersByTimeAsync(3000)
    expect(controls.controlsVisible.value).toBe(true)
  })
})
