import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { useVideoSubtitles } from '@/composables/useVideoSubtitles'

function createMockTrack() {
  const listeners = new Map()

  return {
    mode: 'disabled',
    activeCues: [],
    addEventListener: vi.fn((event, handler) => {
      listeners.set(event, handler)
    }),
    removeEventListener: vi.fn((event, handler) => {
      if (listeners.get(event) === handler) {
        listeners.delete(event)
      }
    }),
    emit(event) {
      listeners.get(event)?.()
    }
  }
}

function createHarness(context = {}) {
  let api
  const videoRef = ref(context.video ?? null)
  const subtitleUrl = ref(context.subtitleUrl ?? '')

  const component = defineComponent({
    setup() {
      api = useVideoSubtitles({
        videoRef,
        subtitleUrlRef: subtitleUrl
      })

      return api
    },
    template: '<div />'
  })

  return {
    component,
    videoRef,
    subtitleUrl,
    getApi: () => api
  }
}

describe('useVideoSubtitles', () => {
  it('toggles subtitle tracks and updates current subtitle text', async () => {
    const track = createMockTrack()
    const video = { textTracks: [track] }
    const { component, getApi } = createHarness({
      video,
      subtitleUrl: '/subtitles/demo.vtt'
    })

    const wrapper = mount(component)
    const subtitles = getApi()

    subtitles.syncSubtitleTrack()
    expect(subtitles.subtitlesEnabled.value).toBe(false)
    expect(track.mode).toBe('disabled')

    subtitles.toggleSubtitles()
    expect(subtitles.subtitlesEnabled.value).toBe(true)
    expect(track.mode).toBe('showing')

    track.activeCues = [{ text: '第一行字幕' }]
    track.emit('cuechange')
    expect(subtitles.currentSubtitle.value).toBe('第一行字幕')

    subtitles.toggleSubtitles()
    expect(subtitles.subtitlesEnabled.value).toBe(false)
    expect(track.mode).toBe('disabled')
    expect(subtitles.currentSubtitle.value).toBe('')

    wrapper.unmount()
    expect(track.removeEventListener).toHaveBeenCalled()
  })

  it('clears subtitle state when subtitle url becomes unavailable', async () => {
    const track = createMockTrack()
    const video = { textTracks: [track] }
    const { component, subtitleUrl, getApi } = createHarness({
      video,
      subtitleUrl: '/subtitles/demo.vtt'
    })

    mount(component)
    const subtitles = getApi()

    subtitles.syncSubtitleTrack()
    subtitles.toggleSubtitles()
    track.activeCues = [{ text: '第二行字幕' }]
    track.emit('cuechange')
    expect(subtitles.currentSubtitle.value).toBe('第二行字幕')

    subtitleUrl.value = ''
    await Promise.resolve()

    expect(subtitles.subtitlesEnabled.value).toBe(false)
    expect(subtitles.currentSubtitle.value).toBe('')
    expect(track.mode).toBe('disabled')
  })
})
