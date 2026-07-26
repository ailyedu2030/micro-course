import { defineComponent, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { useVideoPageLifecycle } from '@/composables/useVideoPageLifecycle'

function createHarness(options = {}) {
  return defineComponent({
    setup() {
      return useVideoPageLifecycle({
        isPlayingRef: options.isPlayingRef ?? ref(false),
        componentUnmountedRef: options.componentUnmountedRef ?? ref(false),
        isPipSupportedRef: options.isPipSupportedRef ?? ref(false),
        syncViewportMode: options.syncViewportMode,
        pictureInPictureEnabled: options.pictureInPictureEnabled,
        canRequestPictureInPicture: options.canRequestPictureInPicture,
        resetVideoProgressReporter: options.resetVideoProgressReporter,
        nextTickFn: options.nextTickFn,
        loadVideo: options.loadVideo,
        handleKeydown: options.handleKeydown,
        handleFullscreenChange: options.handleFullscreenChange,
        handleResize: options.handleResize,
        scrollToActiveChapter: options.scrollToActiveChapter,
        startVideoProgressHeartbeat: options.startVideoProgressHeartbeat,
        stopVideoProgressHeartbeat: options.stopVideoProgressHeartbeat,
        destroyPlayer: options.destroyPlayer,
        stopBufferingWatchdog: options.stopBufferingWatchdog,
        addDocumentListener: options.addDocumentListener,
        removeDocumentListener: options.removeDocumentListener,
        addWindowListener: options.addWindowListener,
        removeWindowListener: options.removeWindowListener
      })
    },
    template: '<div />'
  })
}

describe('useVideoPageLifecycle', () => {
  it('initializes page state on mount and binds global listeners', async () => {
    const callSequence = []
    const isPipSupportedRef = ref(false)
    const handleKeydown = vi.fn()
    const handleFullscreenChange = vi.fn()
    const handleResize = vi.fn()
    const addDocumentListener = vi.fn()
    const addWindowListener = vi.fn()

    mount(createHarness({
      isPipSupportedRef,
      syncViewportMode: vi.fn(() => {
        callSequence.push('syncViewportMode')
      }),
      pictureInPictureEnabled: () => true,
      canRequestPictureInPicture: () => true,
      resetVideoProgressReporter: vi.fn(() => {
        callSequence.push('resetVideoProgressReporter')
      }),
      nextTickFn: vi.fn(async () => {
        callSequence.push('nextTick')
      }),
      loadVideo: vi.fn(() => {
        callSequence.push('loadVideo')
      }),
      handleKeydown,
      handleFullscreenChange,
      handleResize,
      scrollToActiveChapter: vi.fn(() => {
        callSequence.push('scrollToActiveChapter')
      }),
      addDocumentListener,
      addWindowListener
    }))

    await nextTick()
    await vi.waitFor(() => {
      expect(callSequence).toContain('loadVideo')
      expect(callSequence).toContain('scrollToActiveChapter')
    })

    expect(isPipSupportedRef.value).toBe(true)
    expect(callSequence).toEqual([
      'syncViewportMode',
      'resetVideoProgressReporter',
      'nextTick',
      'loadVideo',
      'scrollToActiveChapter'
    ])
    expect(addDocumentListener).toHaveBeenCalledWith('keydown', handleKeydown)
    expect(addDocumentListener).toHaveBeenCalledWith('fullscreenchange', handleFullscreenChange)
    expect(addWindowListener).toHaveBeenCalledWith('resize', handleResize)
  })

  it('starts and stops the progress heartbeat with playback state changes', async () => {
    const isPlayingRef = ref(false)
    const startVideoProgressHeartbeat = vi.fn()
    const stopVideoProgressHeartbeat = vi.fn()

    mount(createHarness({
      isPlayingRef,
      startVideoProgressHeartbeat,
      stopVideoProgressHeartbeat
    }))

    isPlayingRef.value = true
    await Promise.resolve()
    expect(startVideoProgressHeartbeat).toHaveBeenCalledTimes(1)
    expect(stopVideoProgressHeartbeat).not.toHaveBeenCalled()

    isPlayingRef.value = false
    await Promise.resolve()
    expect(stopVideoProgressHeartbeat).toHaveBeenCalledTimes(1)
  })

  it('marks the component as unmounted and removes global listeners during cleanup', async () => {
    const componentUnmountedRef = ref(false)
    const handleKeydown = vi.fn()
    const handleFullscreenChange = vi.fn()
    const handleResize = vi.fn()
    const destroyPlayer = vi.fn()
    const stopBufferingWatchdog = vi.fn()
    const removeDocumentListener = vi.fn()
    const removeWindowListener = vi.fn()

    const wrapper = mount(createHarness({
      componentUnmountedRef,
      handleKeydown,
      handleFullscreenChange,
      handleResize,
      destroyPlayer,
      stopBufferingWatchdog,
      removeDocumentListener,
      removeWindowListener
    }))

    wrapper.unmount()

    expect(componentUnmountedRef.value).toBe(true)
    expect(destroyPlayer).toHaveBeenCalledTimes(1)
    expect(stopBufferingWatchdog).toHaveBeenCalledTimes(1)
    expect(removeDocumentListener).toHaveBeenCalledWith('keydown', handleKeydown)
    expect(removeDocumentListener).toHaveBeenCalledWith('fullscreenchange', handleFullscreenChange)
    expect(removeWindowListener).toHaveBeenCalledWith('resize', handleResize)
  })
})
