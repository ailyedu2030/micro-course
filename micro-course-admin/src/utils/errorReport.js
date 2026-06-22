import { reportError as reportErrorApi } from '../api/error-report'

let reportedErrors = new Set()
const MAX_STACK_LENGTH = 500

export function initErrorReporting() {
  if (typeof window === 'undefined') return

  // 全局 JS 错误
  window.onerror = function (message, source, lineno, colno, error) {
    const key = message + '|' + source
    if (reportedErrors.has(key)) return
    reportedErrors.add(key)
    if (reportedErrors.size > 100) reportedErrors.clear()

    reportErrorApi({
      message: String(message).slice(0, 200),
      stack: error?.stack?.slice(0, MAX_STACK_LENGTH) || '',
      url: source || window.location.href,
      line: lineno,
      col: colno
    }).catch(() => {})
    return false
  }

  // 未处理的 Promise 异常
  window.addEventListener('unhandledrejection', function (event) {
    const reason = event.reason
    const message = reason?.message || String(reason)
    const key = 'PROMISE:' + message
    if (reportedErrors.has(key)) return
    reportedErrors.add(key)
    if (reportedErrors.size > 100) reportedErrors.clear()

    reportErrorApi({
      message: '[Promise] ' + String(message).slice(0, 200),
      stack: reason?.stack?.slice(0, MAX_STACK_LENGTH) || '',
      url: window.location.href,
      line: 0,
      col: 0
    }).catch(() => {})
  })
}

export function reportError(err) {
  const message = err?.message || String(err)
  const key = 'VUE:' + message
  if (reportedErrors.has(key)) return
  reportedErrors.add(key)
  if (reportedErrors.size > 100) reportedErrors.clear()
  reportErrorApi({
    message: String(message).slice(0, 200),
    stack: err?.stack?.slice(0, MAX_STACK_LENGTH) || '',
    url: window.location.href,
    line: 0,
    col: 0
  }).catch(() => {})
}
