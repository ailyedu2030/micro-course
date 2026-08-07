/**
 * useAsyncAction.js · 统一异步操作 loading 守卫
 *
 * L0 铁律：每个触发异步操作的按钮必须有 loading + 防重复触发（点过一次立刻禁用）。
 * 用法：
 *   const { loading, run } = useAsyncAction(async () => { await api() })
 *   <el-button :loading="loading" @click="run">保存</el-button>
 *
 * 行为：
 *   - loading 为 true 时再次调用 run 直接忽略（防双击重复提交/重复计费）
 *   - run 返回 true/false（成功/失败），错误由调用方自行处理（透传，不吞）
 */
import { ref } from 'vue'

export function useAsyncAction(fn) {
  const loading = ref(false)

  async function run(...args) {
    if (loading.value) return false
    loading.value = true
    try {
      await fn(...args)
      return true
    } finally {
      loading.value = false
    }
  }

  return { loading, run }
}
