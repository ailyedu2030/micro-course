import { computed, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import { useVideoChapterScroller } from '@/composables/useVideoChapterScroller'

describe('useVideoChapterScroller', () => {
  it('stores chapter item refs by index', () => {
    const { chapterItemRefs, setChapterItemRef } = useVideoChapterScroller({
      currentChapterIndexRef: computed(() => 0)
    })
    const chapterEl = { scrollIntoView: vi.fn() }

    setChapterItemRef(chapterEl, 2)
    setChapterItemRef(null, 3)

    expect(chapterItemRefs.value[2]).toEqual(chapterEl)
    expect(chapterItemRefs.value[3]).toBeUndefined()
  })

  it('scrolls the active chapter into view after next tick', async () => {
    const scrollIntoView = vi.fn()
    const nextTickFn = vi.fn(async (callback) => {
      callback()
    })
    const { setChapterItemRef, scrollToActiveChapter } = useVideoChapterScroller({
      currentChapterIndexRef: ref(1),
      nextTickFn
    })

    setChapterItemRef({ scrollIntoView }, 1)
    scrollToActiveChapter()

    await Promise.resolve()

    expect(nextTickFn).toHaveBeenCalledTimes(1)
    expect(scrollIntoView).toHaveBeenCalledWith({
      behavior: 'smooth',
      block: 'nearest'
    })
  })

  it('skips scrolling when the active chapter element is missing', async () => {
    const nextTickFn = vi.fn(async (callback) => {
      callback()
    })
    const { setChapterItemRef, scrollToActiveChapter } = useVideoChapterScroller({
      currentChapterIndexRef: ref(4),
      nextTickFn
    })

    setChapterItemRef({ scrollIntoView: vi.fn() }, 1)
    scrollToActiveChapter()

    await Promise.resolve()

    expect(nextTickFn).toHaveBeenCalledTimes(1)
  })
})
