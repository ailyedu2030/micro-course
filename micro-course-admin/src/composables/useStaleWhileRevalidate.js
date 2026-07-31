/**
 * P2-17: SWR (stale-while-revalidate) 缓存 composable。
 *
 * 不引入新库，手动实现一个轻量级 SWR：
 * - 首次获取后缓存
 * - staleTime 内的请求直接返回缓存
 * - staleTime 外返回缓存同时后台重取
 * - 缓存有界：LRU 淘汰 + HARD_TTL 过期清理
 *
 * 使用:
 *   const { data, refresh } = useSWR('/api/courses', { staleTime: 30000 })
 *
 * 清缓存:
 *   import { clearSwrCache } from '@/composables/useStaleWhileRevalidate'
 *   clearSwrCache()
 */
import { ref, onMounted } from 'vue'
import request from '@/utils/request'

/** 最大缓存条目数，超过后淘汰最久未访问的条目 */
const MAX_CACHE_SIZE = 50
/** 硬 TTL：超过此时间的条目即使 staleTime 内也会被完全清理 */
const HARD_TTL = 5 * 60 * 1000  // 5 分钟

const cache = new Map()
const inflight = new Map()

/** P2-17: 导出共享 cache 供外部代码直接读写（如需自定义 SWR 行为） */
export const swrCache = cache

/**
 * LRU touch：删除后重新插入使 key 移到 Map 尾部（最新访问）
 */
function _touch(key) {
  if (cache.has(key)) {
    const entry = cache.get(key)
    cache.delete(key)
    cache.set(key, entry)
  }
}

/**
 * 清理过期条目 + LRU 淘汰
 */
function _pruneCache() {
  const now = Date.now()
  // 硬 TTL 过期清理
  for (const [key, entry] of cache) {
    if (now - entry.ts > HARD_TTL) cache.delete(key)
  }
  // LRU 淘汰
  while (cache.size > MAX_CACHE_SIZE) {
    const oldestKey = cache.keys().next().value
    if (oldestKey) cache.delete(oldestKey)
    else break
  }
}

/**
 * 清空 SWR 缓存（包括 in-flight 去重 Map）
 * 页面退出/登出时调用，避免跨会话数据残留
 */
export function clearSwrCache() {
  cache.clear()
  inflight.clear()
}

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
    const now = Date.now()
    // 前置裁剪：清除过期条目，确保缓存有界
    _pruneCache()
    const entry = cache.get(key)
    if (entry && now - entry.ts < staleTime) {
      // 缓存新鲜
      _touch(key)
      data.value = entry.data
      return entry.data
    }
    if (entry) {
      // 缓存过期但未超 HARD_TTL：先返回旧值，后台重取
      _touch(key)
      data.value = entry.data
      loading.value = true
      try {
        const fresh = await fetcher(key)
        cache.set(key, { data: fresh, ts: Date.now() })
        _pruneCache()
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
        _pruneCache()
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
