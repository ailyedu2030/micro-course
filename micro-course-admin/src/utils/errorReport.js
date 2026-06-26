import { reportError as reportErrorApi } from '../api/error-report'

/**
 * P1-I-2: 错误上报去重 + 频率限制
 * ----------------------------------------------------------------------------
 * 客户体验第一原则 - 避免以下问题:
 * 1. 同一错误刷屏 (用堆栈+路径+5分钟时间窗作为去重 key)
 * 2. 网络异常时错误本身触发更多错误 (频率限制)
 * 3. 用户操作过快产生大量重复错误 (节流)
 */
const MAX_STACK_LENGTH = 500
const DEDUP_WINDOW_MS = 5 * 60 * 1000 // 5 分钟时间窗
const RATE_LIMIT_MS = 1000 // 最小上报间隔 1 秒
const MAX_REPORTS_PER_MINUTE = 30

// 错误去重 Map<key, { count, firstSeen }>
const dedupMap = new Map()
// 频率限制 - 上次上报时间
let lastReportTime = 0
// 1 分钟内的上报次数
let recentCount = 0
let recentWindowStart = Date.now()

/**
 * 计算去重 key - 同一错误 5 分钟内只上报一次
 * 包含: 错误类型 + URL 路径 + 堆栈前 200 字符
 */
function computeKey(type, message, stack, url) {
  const stackPrefix = (stack || '').slice(0, 200)
  return `${type}:${url}:${message.slice(0, 100)}:${stackPrefix}`
}

/**
 * 频率限制检查
 * - 最小间隔 1 秒 (避免错误刷屏)
 * - 1 分钟内最多 30 次 (避免后端被刷爆)
 */
function canReport() {
  const now = Date.now()
  // 重置 1 分钟窗口
  if (now - recentWindowStart > 60000) {
    recentWindowStart = now
    recentCount = 0
  }
  // 1 秒最小间隔
  if (now - lastReportTime < RATE_LIMIT_MS) {
    return false
  }
  // 1 分钟最多 30 次
  if (recentCount >= MAX_REPORTS_PER_MINUTE) {
    return false
  }
  return true
}

function recordReport() {
  lastReportTime = Date.now()
  recentCount++
}

function shouldDedup(key) {
  const now = Date.now()
  const existing = dedupMap.get(key)
  if (!existing) {
    dedupMap.set(key, { count: 1, firstSeen: now })
    return false
  }
  // 5 分钟内重复错误 → 去重
  if (now - existing.firstSeen < DEDUP_WINDOW_MS) {
    existing.count++
    return true
  }
  // 5 分钟后重新计数
  dedupMap.set(key, { count: 1, firstSeen: now })
  return false
}

// 定期清理过期的 dedup 条目
setInterval(() => {
  const now = Date.now()
  for (const [key, info] of dedupMap.entries()) {
    if (now - info.firstSeen > DEDUP_WINDOW_MS) {
      dedupMap.delete(key)
    }
  }
}, 60000) // 每分钟清理一次

function doReport(type, message, stack, url, line = 0, col = 0) {
  if (!canReport()) return
  const key = computeKey(type, message, stack, url)
  if (shouldDedup(key)) return
  recordReport()
  reportErrorApi({
    message: String(message).slice(0, 200),
    stack: (stack || '').slice(0, MAX_STACK_LENGTH),
    url: url || window.location.href,
    line,
    col
  }).catch(() => {
    // 上报失败不影响业务,静默忽略
  })
}

export function initErrorReporting() {
  if (typeof window === 'undefined') return

  // 全局 JS 错误
  window.onerror = function (message, source, lineno, colno, error) {
    doReport(
      'JS',
      String(message),
      error?.stack || '',
      source || window.location.href,
      lineno,
      colno
    )
    return false
  }

  // 未处理的 Promise 异常
  window.addEventListener('unhandledrejection', function (event) {
    const reason = event.reason
    const message = reason?.message || String(reason)
    doReport(
      'PROMISE',
      '[Promise] ' + String(message),
      reason?.stack || '',
      window.location.href
    )
  })
}

export function reportError(err) {
  const message = err?.message || String(err)
  doReport('VUE', message, err?.stack || '', window.location.href)
}
