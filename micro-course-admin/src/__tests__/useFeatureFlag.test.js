import { beforeEach, describe, expect, it } from 'vitest'
import { isRef } from 'vue'

import { useFeatureFlag } from '@/plugins/interactive/composables/useFeatureFlag'

describe('useFeatureFlag', () => {
  beforeEach(() => {
    // vitest 默认 node 环境无 localStorage：注入内存 stub
    const store = new Map()
    globalThis.window = {
      localStorage: {
        getItem: (k) => store.get(k) ?? null,
        setItem: (k, v) => store.set(k, String(v)),
        removeItem: (k) => store.delete(k)
      }
    }
    // 重置模块单例（memoryFallback 兜底路径会跨用例残留）
    useFeatureFlag().setCoursewareV2(false)
  })

  it('coursewareV2 是 ref（模板可自动解包，v-if 正确响应）', () => {
    const { coursewareV2 } = useFeatureFlag()
    expect(isRef(coursewareV2)).toBe(true)
    expect(coursewareV2.value).toBe(false)
  })

  it('setCoursewareV2 切换值并持久化', () => {
    const { coursewareV2, setCoursewareV2 } = useFeatureFlag()
    setCoursewareV2(true)
    expect(coursewareV2.value).toBe(true)
    expect(window.localStorage.getItem('mc:feature:courseware_v2')).toBe('true')
    setCoursewareV2(false)
    expect(coursewareV2.value).toBe(false)
    expect(window.localStorage.getItem('mc:feature:courseware_v2')).toBe('false')
  })

  it('模板解包语义：!coursewareV2 仅在关闭时为真（防 2026-08-06 旧版头部恒隐藏回归）', () => {
    const { coursewareV2, setCoursewareV2 } = useFeatureFlag()
    // 模拟模板 v-if="!coursewareV2" 的求值（模板中 ref 自动解包为布尔值）
    const templateOldVisible = () => !coursewareV2.value
    expect(templateOldVisible()).toBe(true)
    setCoursewareV2(true)
    expect(templateOldVisible()).toBe(false)
  })
})
