import { ref } from 'vue'

/**
 * 统一异步数据加载 composable（Round 11-3）
 *
 * 统一各页面"loading + data + error"三件套，避免每个页面重复声明：
 *   const loading = ref(false)
 *   try { loading.value = true; data.value = await fn() } finally { loading.value = false }
 *
 * 用法：
 *   const { data, loading, error, execute } = useAsyncData(fetchList)
 *   await execute(params)   // loading 自动 true → false，data 自动赋值
 *
 * @param {Function} asyncFn 返回 Promise 的异步函数
 * @returns {{ data: import('vue').Ref, loading: import('vue').Ref<boolean>, error: import('vue').Ref, execute: Function }}
 */
export function useAsyncData(asyncFn) {
  const data = ref(null)
  const loading = ref(false)
  const error = ref(null)

  const execute = async (...args) => {
    loading.value = true
    error.value = null
    try {
      data.value = await asyncFn(...args)
      return data.value
    } catch (e) {
      error.value = e
      throw e
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, execute }
}
