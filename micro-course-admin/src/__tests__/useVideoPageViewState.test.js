import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { useVideoPageViewState } from '@/composables/useVideoPageViewState'

function createHarness(context = {}) {
  let api
  const chapters = ref(context.chapters ?? [])
  const currentChapterIndex = ref(context.currentChapterIndex ?? 0)
  const volumePercent = ref(context.volumePercent ?? 100)

  const component = defineComponent({
    setup() {
      api = useVideoPageViewState({
        chaptersRef: chapters,
        currentChapterIndexRef: currentChapterIndex,
        volumePercentRef: volumePercent
      })

      return api
    },
    template: '<div />'
  })

  return {
    component,
    chapters,
    currentChapterIndex,
    volumePercent,
    getApi: () => api
  }
}

describe('useVideoPageViewState', () => {
  it('tracks shell view state and derives current chapter and volume', async () => {
    const { component, currentChapterIndex, volumePercent, getApi } = createHarness({
      chapters: [
        { id: 1, title: '第一章' },
        { id: 2, title: '第二章' }
      ],
      currentChapterIndex: 0,
      volumePercent: 65
    })

    mount(component)
    const viewState = getApi()

    expect(viewState.activeTab.value).toBe('chapters')
    expect(viewState.showChapterList.value).toBe(true)
    expect(viewState.currentChapter.value).toEqual({ id: 1, title: '第一章' })
    expect(viewState.volume.value).toBe(0.65)

    viewState.toggleChapterList()
    expect(viewState.showChapterList.value).toBe(false)

    currentChapterIndex.value = 1
    expect(viewState.currentChapter.value).toEqual({ id: 2, title: '第二章' })

    volumePercent.value = 0
    expect(viewState.volume.value).toBe(0)
  })
})
