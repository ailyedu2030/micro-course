import { defineComponent, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { useVideoPageActions } from '@/composables/useVideoPageActions'

function createHarness() {
  let api
  const errorMsg = ref('')
  const router = {
    back: vi.fn()
  }

  const component = defineComponent({
    setup() {
      api = useVideoPageActions({
        router,
        errorMsgRef: errorMsg
      })

      return api
    },
    template: '<div />'
  })

  return {
    component,
    router,
    errorMsg,
    getApi: () => api
  }
}

describe('useVideoPageActions', () => {
  it('handles page shell actions and note hover state', () => {
    const { component, router, getApi } = createHarness()

    mount(component)
    const actions = getApi()

    expect(actions.highlightedNoteTime.value).toBeNull()

    actions.highlightTime(42)
    expect(actions.highlightedNoteTime.value).toBe(42)

    actions.highlightTime(null)
    expect(actions.highlightedNoteTime.value).toBeNull()

    actions.onVideoError()
    expect(actions.errorMsgRef.value).toBe('视频播放出错，请尝试刷新页面')

    actions.goBack()
    expect(router.back).toHaveBeenCalledTimes(1)
  })
})
