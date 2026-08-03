import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

const testState = vi.hoisted(() => ({
  routerBack: vi.fn(),
  retryLoad: vi.fn(),
  retryHls: vi.fn(),
  deleteNote: vi.fn(),
  seekToTime: vi.fn(),
  switchChapter: vi.fn(),
  highlightTimeSpy: vi.fn(),
  toggleChapterListSpy: vi.fn(),
  toggleSubtitlesSpy: vi.fn(),
  refs: {}
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    back: testState.routerBack,
    push: vi.fn()
  }),
  useRoute: () => ({
    params: {
      id: '200',
      videoId: '300'
    },
    query: {
      courseId: '200',
      chapterId: '10',
      videoId: '300'
    }
  })
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => ({
    userInfo: {
      id: 1,
      realName: '测试学员'
    }
  })
}))

vi.mock('@/api/video', () => ({
  getVideoById: vi.fn().mockResolvedValue({ data: { id: 300, title: '测试视频', url: 'https://cdn.example.com/v.mp4' } }),
  getVideoSign: vi.fn().mockResolvedValue({ data: 'test-sign' })
}))

vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(() => 'test-token')
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: {
      success: vi.fn(),
      warning: vi.fn(),
      error: vi.fn()
    },
    ElMessageBox: {
      confirm: vi.fn(() => Promise.resolve()),
      alert: vi.fn(() => Promise.resolve())
    }
  }
})

vi.mock('@/composables/usePlaybackSpeed', () => ({
  SPEED_OPTIONS: [
    { value: 1, label: '1.0x' },
    { value: 1.25, label: '1.25x' }
  ]
}))

vi.mock('@/composables/useVideoChapterScroller', async () => {
  return {
    useVideoChapterScroller: () => ({
      setChapterItemRef: vi.fn(),
      scrollToActiveChapter: vi.fn()
    })
  }
})

vi.mock('@/composables/useVideoUiState', async () => {
  const vue = await vi.importActual('vue')
  return {
    useVideoUiState: () => ({
      isMobile: vue.ref(false),
      showObjectives: vue.ref(false),
      syncViewportMode: vi.fn(),
      handleResize: vi.fn(),
      showObjectivesOverlay: vi.fn()
    })
  }
})

vi.mock('@/composables/useVideoDisplayFormatters', () => ({
  useVideoDisplayFormatters: () => ({
    formatTime: (seconds) => `00:${String(Math.floor(seconds || 0)).padStart(2, '0')}`,
    formatDateTime: () => '2026-07-24'
  })
}))

vi.mock('@/composables/useVideoPlaybackControls', async () => {
  const vue = await vi.importActual('vue')
  return {
    useVideoPlaybackControls: () => ({
      isPlaying: vue.ref(false),
      isMuted: vue.ref(false),
      isFullscreen: vue.ref(false),
      isPip: vue.ref(false),
      playbackRate: vue.ref(1),
      volumePercent: vue.ref(50),
      currentTime: vue.ref(12),
      duration: vue.ref(120),
      bufferedPercent: vue.ref(40),
      controlsVisible: vue.ref(true),
      speedToastVisible: vue.ref(false),
      togglePlay: vi.fn(),
      skipBackward: vi.fn(),
      skipForward: vi.fn(),
      seekRelative: vi.fn(),
      toggleMute: vi.fn(),
      changeVolume: vi.fn(),
      changeSpeed: vi.fn(),
      toggleFullscreen: vi.fn(),
      togglePictureInPicture: vi.fn(),
      handlePipEnter: vi.fn(),
      handlePipLeave: vi.fn(),
      seekVideo: vi.fn(),
      showControls: vi.fn(),
      hideControlsDelayed: vi.fn(),
      onCanPlay: vi.fn(),
      onTimeUpdate: vi.fn(),
      onProgress: vi.fn(),
      handleFullscreenChange: vi.fn()
    })
  }
})

vi.mock('@/composables/useVideoSubtitles', async () => {
  const vue = await vi.importActual('vue')
  return {
    useVideoSubtitles: () => {
      const subtitlesEnabled = vue.ref(false)
      const currentSubtitle = vue.ref('')

      const toggleSubtitles = vi.fn(() => {
        subtitlesEnabled.value = !subtitlesEnabled.value
        currentSubtitle.value = subtitlesEnabled.value ? '测试字幕已显示' : ''
      })

      testState.toggleSubtitlesSpy = toggleSubtitles
      testState.refs.subtitlesEnabled = subtitlesEnabled
      testState.refs.currentSubtitle = currentSubtitle

      return {
        subtitlesEnabled,
        currentSubtitle,
        toggleSubtitles,
        syncSubtitleTrack: vi.fn()
      }
    }
  }
})

vi.mock('@/composables/useVideoPageViewState', async () => {
  const vue = await vi.importActual('vue')
  return {
    useVideoPageViewState: (options) => {
      const showChapterList = vue.ref(true)
      const activeTab = vue.ref('notes')
      const currentChapter = vue.computed(() => options.chaptersRef.value[options.currentChapterIndexRef.value] || null)
      const volume = vue.computed(() => options.volumePercentRef.value / 100)
      const toggleChapterList = vi.fn(() => {
        showChapterList.value = !showChapterList.value
      })

      testState.toggleChapterListSpy = toggleChapterList
      testState.refs.showChapterList = showChapterList

      return {
        activeTab,
        showChapterList,
        currentChapter,
        volume,
        toggleChapterList
      }
    }
  }
})

vi.mock('@/composables/useVideoDisplayState', async () => {
  const vue = await vi.importActual('vue')
  return {
    useVideoDisplayState: () => ({
      progressPercent: vue.ref(10),
      watermarkText: vue.ref('用户 1 · 20260724 10:00')
    })
  }
})

vi.mock('@/composables/useVideoKeyboardShortcuts', () => ({
  useVideoKeyboardShortcuts: () => ({
    handleKeydown: vi.fn()
  })
}))

vi.mock('@/composables/useVideoTouchGestures', async () => {
  const vue = await vi.importActual('vue')
  return {
    useVideoTouchGestures: () => ({
      volumeIndicatorVisible: vue.ref(false),
      brightnessIndicatorVisible: vue.ref(false),
      volumeIndicatorValue: vue.ref(0),
      brightnessIndicatorValue: vue.ref(0),
      gestureIndicatorX: vue.ref(0),
      gestureIndicatorY: vue.ref(0),
      showSeekIndicator: vue.ref(false),
      seekIndicatorDir: vue.ref('forward'),
      seekIndicatorSeconds: vue.ref(0),
      handleTouchStart: vi.fn(),
      handleTouchMove: vi.fn(),
      handleTouchEnd: vi.fn()
    })
  }
})

vi.mock('@/composables/useVideoBufferingWatchdog', async () => {
  const vue = await vi.importActual('vue')
  return {
    useVideoBufferingWatchdog: () => ({
      isBuffering: vue.ref(false),
      onBufferingStart: vi.fn(),
      onBufferingEnd: vi.fn(),
      stopWatchdog: vi.fn()
    })
  }
})

vi.mock('@/composables/useVideoSourceLifecycle', async () => {
  const vue = await vi.importActual('vue')
  return {
    useVideoSourceLifecycle: () => ({
      hlsFatal: vue.ref(false),
      initPlayer: vi.fn(),
      retryLoad: testState.retryLoad,
      retryHls: testState.retryHls,
      destroyPlayer: vi.fn()
    })
  }
})

vi.mock('@/composables/useVideoLocalState', async () => {
  const vue = await vi.importActual('vue')
  return {
    useVideoLocalState: () => ({
      lastPosition: vue.ref(0),
      notes: vue.ref([
        { id: 1, time: 12, content: '记录一个重点' }
      ]),
      noteText: vue.ref(''),
      saveLocalPosition: vi.fn(),
      loadLocalPosition: vi.fn(),
      loadNotesFromStorage: vi.fn(),
      addNote: vi.fn(),
      deleteNote: vi.fn(),
      insertNoteAtCurrentTime: vi.fn()
    })
  }
})

vi.mock('@/composables/useVideoProgressFlow', async () => {
  const vue = await vi.importActual('vue')
  return {
    useVideoProgressFlow: () => ({
      progressId: vue.ref(1),
      reportProgress: vi.fn(() => Promise.resolve()),
      resetProgressReporter: vi.fn()
    })
  }
})

vi.mock('@/composables/useVideoLearningData', async () => {
  return {
    useVideoLearningData: (options) => {
      options.chaptersRef.value = [
        { id: 10, title: '第一章', description: '掌握核心概念', duration: 60, isCompleted: false }
      ]
      options.discussionsRef.value = [
        { id: 1, authorName: '同学A', createdAt: '2026-07-24T10:00:00Z', title: '讨论标题', content: '讨论内容' }
      ]
      options.currentChapterIndexRef.value = 0

      return {
        loadChapters: vi.fn(),
        loadProgress: vi.fn(),
        loadDiscussions: vi.fn(),
        switchChapter: testState.switchChapter
      }
    }
  }
})

vi.mock('@/composables/useVideoLoadOrchestrator', async () => {
  return {
    useVideoLoadOrchestrator: (options) => {
      options.loadingRef.value = false
      options.errorMsgRef.value = ''
      options.videoDataRef.value = {
        title: '测试视频标题',
        thumbnail: '/poster.png',
        subtitleUrl: '/subtitle.vtt'
      }

      return {
        loadVideo: vi.fn()
      }
    }
  }
})

vi.mock('@/composables/useLearningProgressHeartbeat', () => ({
  useLearningProgressHeartbeat: () => ({
    startHeartbeat: vi.fn(),
    stopHeartbeat: vi.fn()
  })
}))

vi.mock('@/composables/useVideoCompletionFlow', () => ({
  useVideoCompletionFlow: () => ({
    handleEnded: vi.fn()
  })
}))

vi.mock('@/composables/useVideoNoteActions', () => ({
  useVideoNoteActions: () => ({
    addNote: vi.fn(),
    deleteNote: testState.deleteNote,
    insertNoteAtCurrentTime: vi.fn(),
    seekToTime: testState.seekToTime
  })
}))

vi.mock('@/composables/useVideoPageActions', async () => {
  const vue = await vi.importActual('vue')
  return {
    useVideoPageActions: (options) => {
      const highlightedNoteTime = vue.ref(null)

      const highlightTime = vi.fn((time) => {
        highlightedNoteTime.value = time ?? null
      })

      const onVideoError = vi.fn(() => {
        options.errorMsgRef.value = '视频播放出错，请尝试刷新页面'
      })

      const goBack = vi.fn(() => {
        options.router.back()
      })

      testState.highlightTimeSpy = highlightTime
      testState.refs.highlightedNoteTime = highlightedNoteTime

      return {
        highlightedNoteTime,
        highlightTime,
        onVideoError,
        goBack
      }
    }
  }
})

vi.mock('@/composables/useVideoPageLifecycle', () => ({
  useVideoPageLifecycle: vi.fn()
}))

const globalStubs = {
  'el-button': {
    emits: ['click'],
    template: '<button v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>'
  },
  'el-dropdown': {
    template: '<div class="el-dropdown-stub"><slot /><slot name="dropdown" /></div>'
  },
  'el-dropdown-menu': {
    template: '<div class="el-dropdown-menu-stub"><slot /></div>'
  },
  'el-dropdown-item': {
    emits: ['click'],
    template: '<button class="el-dropdown-item-stub" v-bind="$attrs" @click="$emit(\'click\', $event)"><slot /></button>'
  },
  'el-tabs': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<div class="el-tabs-stub"><slot /></div>'
  },
  'el-tab-pane': {
    template: '<section class="el-tab-pane-stub"><slot /></section>'
  },
  'el-input': {
    props: ['modelValue'],
    emits: ['update:modelValue', 'keyup'],
    template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" @keyup="$emit(\'keyup\', $event)" />'
  },
  transition: {
    template: '<div><slot /></div>'
  },
  'transition-group': {
    template: '<div><slot /></div>'
  }
}

import VideoPlayer from '@/views/student/VideoPlayer.vue'

describe('VideoPlayer.vue module regression', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    testState.refs = {}
  })

  it('toggles the chapter sidebar and subtitle overlay from the wired controls', async () => {
    const wrapper = mount(VideoPlayer, {
      global: {
        stubs: globalStubs
      }
    })

    const sidebar = wrapper.find('.pc-sidebar')
    expect(sidebar.exists()).toBe(true)
    expect(sidebar.attributes('style') || '').not.toContain('display: none')

    const chapterToggleButton = wrapper.find('button[aria-label="章节列表"]')
    expect(chapterToggleButton.exists()).toBe(true)

    await chapterToggleButton.trigger('click')
    await nextTick()

    expect(testState.toggleChapterListSpy).toHaveBeenCalledTimes(1)
    expect(sidebar.attributes('style')).toContain('display: none')

    const subtitleButton = wrapper.find('button[aria-label="字幕"]')
    expect(subtitleButton.exists()).toBe(true)

    await subtitleButton.trigger('click')
    await nextTick()

    expect(testState.toggleSubtitlesSpy).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.video-subtitles').text()).toContain('测试字幕已显示')
  })

  it('wires the back action and note hover highlight state', async () => {
    const wrapper = mount(VideoPlayer, {
      global: {
        stubs: globalStubs
      }
    })

    const backButton = wrapper.find('.pc-header button[aria-label="返回"]')
    expect(backButton.exists()).toBe(true)

    await backButton.trigger('click')
    expect(testState.routerBack).toHaveBeenCalledTimes(1)

    const noteItem = wrapper.find('.notes-tab .note-item')
    expect(noteItem.exists()).toBe(true)

    await noteItem.trigger('mouseenter')
    await nextTick()

    expect(testState.highlightTimeSpy).toHaveBeenCalledWith(12)
    expect(noteItem.classes()).toContain('is-highlighted')

    await noteItem.trigger('mouseleave')
    await nextTick()

    expect(testState.highlightTimeSpy).toHaveBeenLastCalledWith(null)
    expect(noteItem.classes()).not.toContain('is-highlighted')
  })
})
