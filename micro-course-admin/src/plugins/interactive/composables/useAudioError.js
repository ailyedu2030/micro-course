/**
 * useAudioError.js · 音频生成失败原因分类 → 可操作建议 (L0 铁律)
 *
 * 后端 error_message 真实来源（TtsServiceImpl / TtsWorkerService）：
 *   - "账户余额不足"                                  → recharge
 *   - "TTS 限流，请 5 分钟后重试"                      → retry（可等 5 分钟自动恢复）
 *   - "生成超时（>10 分钟）" / "MiniMax 响应解析失败"   → retry（瞬时故障，可重试）
 *   - "MiniMax 错误: ..."（含 voice/speaker 关键词）   → voice（音色不可用）
 *   - "MiniMax API Key 无效，请检查 backend 配置"      → config（管理员介入）
 *   - 其它                                            → retry（默认重新生成）
 *
 * L0 铁律：每个错误状态必须告诉用户"该怎么办" + 提供"行动按钮"。
 */
export function classifyAudioError(msg = '') {
  const m = (msg || '').trim()
  if (!m) {
    return {
      level: 'error',
      alertType: 'error',
      action: 'retry',
      advice: '音频生成失败，请重新生成',
      actionLabel: '重新生成'
    }
  }
  if (m.includes('余额') || /insufficient|balance/i.test(m)) {
    return {
      level: 'warning',
      alertType: 'warning',
      action: 'recharge',
      advice: '账户余额不足，请联系管理员充值后重新生成',
      actionLabel: '联系管理员充值'
    }
  }
  if (m.includes('限流') || m.includes('rate limit') || m.includes('429')) {
    return {
      level: 'warning',
      alertType: 'warning',
      action: 'retry',
      advice: 'TTS 服务限流，5 分钟后自动重试，也可立即手动重试',
      actionLabel: '手动重试'
    }
  }
  if (m.includes('超时') || /timeout|timed ?out/i.test(m)) {
    return {
      level: 'warning',
      alertType: 'warning',
      action: 'retry',
      advice: '音频生成超时，重新生成即可',
      actionLabel: '重新生成'
    }
  }
  if (m.includes('音色') || /voice|speaker/i.test(m)) {
    return {
      level: 'warning',
      alertType: 'warning',
      action: 'voice',
      advice: '当前音色不可用，建议切换默认音色',
      actionLabel: '切换默认音色'
    }
  }
  if (m.includes('Key 无效') || m.includes('API Key') || m.includes('backend 配置')) {
    return {
      level: 'error',
      alertType: 'error',
      action: 'config',
      advice: 'TTS 服务配置异常，请联系管理员检查后重试',
      actionLabel: '重新生成'
    }
  }
  return {
    level: 'error',
    alertType: 'error',
    action: 'retry',
    advice: m.length > 80 ? `生成失败：${m.slice(0, 80)}…` : `生成失败：${m}`,
    actionLabel: '重新生成'
  }
}
