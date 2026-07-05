/**
 * P2-17: SWR (stale-while-revalidate) 缓存 composable。
 *
 * 不引入新库，手动实现一个轻量级 SWR：
 * - 首次获取后缓存
 * - staleTime 内的请求直接返回缓存
 * - staleTime 外返回缓存同时后台重取
 *
 * 使用:
 *   const { data, refresh } = useSWR('/api/courses', { staleTime: 30000 })
 */
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

const cache = new Map()
const inflight = new Map()

/** P2-17: 导出共享 cache 供外部代码直接读写（如需自定义 SWR 行为） */
export const swrCache = cache

export function useSWR(key, options = {}) {
  const { staleTime = 30000, immediate = true } = options
  const data = ref(null)
  const loading = ref(false)
  const error = ref(null)

  async function fetcher(url) {
    // 复用 in-flight 请求
    if (inflight.has(url)) return inflight.get(url)
    const promise = request.get(url).then(res => res.data)
    inflight.set(url, promise)
    promise.finally(() => inflight.delete(url))
    return promise
  }

  async function refresh() {
    if (!key) return
    const entry = cache.get(key)
    const now = Date.now()
    if (entry && now - entry.ts < staleTime) {
      // 缓存新鲜
      data.value = entry.data
      return entry.data
    }
    if (entry) {
      // 缓存过期：先返回旧值，后台重取
      data.value = entry.data
      loading.value = true
      try {
        const fresh = await fetcher(key)
        cache.set(key, { data: fresh, ts: Date.now() })
        data.value = fresh
      } catch (e) {
        error.value = e
      } finally {
        loading.value = false
      }
    } else {
      // 无缓存：等待
      loading.value = true
      try {
        const fresh = await fetcher(key)
        cache.set(key, { data: fresh, ts: Date.now() })
        data.value = fresh
      } catch (e) {
        error.value = e
      } finally {
        loading.value = false
      }
    }
  }

  if (immediate) {
    onMounted(() => { refresh() })
  }

  return { data, loading, error, refresh }
}
