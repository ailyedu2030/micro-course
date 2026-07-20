/**
 * useFeatureFlag.js · 课件 v2 灰度开关 (spec 5.1 + 9.3)
 *
 * 设计:
 * - localStorage key: mc:feature:courseware_v2
 * - 默认 false (不打扰老用户)
 * - 教师可一键切换, 不需刷新页面 (Vue ref 实时响应)
 * - localStorage 异常 (隐私模式) → 内存 fallback
 *
 * 使用:
 *   import { useFeatureFlag } from '@/plugins/interactive/composables/useFeatureFlag'
 *   const { coursewareV2, toggleCoursewareV2 } = useFeatureFlag()
 *   if (coursewareV2.value) { /* 新版 UI *\/ }
 */

const STORAGE_KEY = 'mc:feature:courseware_v2'
const DEFAULT_VALUE = false

// 单例 in-memory fallback (localStorage 不可用时使用)
const memoryFallback = { value: DEFAULT_VALUE }

/**
 * 读取持久化值
 */
function readPersisted() {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (raw === null) return DEFAULT_VALUE
    return raw === 'true'
  } catch (e) {
    return memoryFallback.value
  }
}

/**
 * 写入持久化值
 */
function writePersisted(value) {
  try {
    window.localStorage.setItem(STORAGE_KEY, String(value))
  } catch (e) {
    memoryFallback.value = value
  }
}

/**
 * 创建响应式 feature flag (单例模式, 多组件共享状态)
 */
const stateRef = { value: readPersisted() }

export function useFeatureFlag() {
  const coursewareV2 = stateRef

  function setCoursewareV2(value) {
    stateRef.value = Boolean(value)
    writePersisted(stateRef.value)
  }

  function toggleCoursewareV2() {
    setCoursewareV2(!stateRef.value)
  }

  return {
    coursewareV2,
    setCoursewareV2,
    toggleCoursewareV2
  }
}