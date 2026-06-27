/**
 * Console 静默开关
 * ----------------------------------------------------------------------------
 * 客户体验第一原则 - 保护内部日志不外泄
 * 1. 生产环境(import.meta.env.PROD=true)保留 warn/error 用于线上排查
 * 2. 静默 debug/log(避免暴露内部技术细节给 F12 用户)
 * 3. 上线后 console.log/warn 仍然有效,但仅供开发/运维通过 Sentry/Logback 看到
 *
 * 使用方法:
 *   import { logger } from '@/utils/logger'
 *   logger.debug('something')  // 生产静默
 *   logger.info('something')   // 生产静默（防止 F12 泄露内部技术细节）
 *   logger.warn('something')   // 生产输出
 *   logger.error('something')  // 生产输出
 */

const isProd = typeof import.meta !== 'undefined' && import.meta.env?.PROD

export const logger = {
  debug: isProd ? () => {} : console.log.bind(console, '[DEBUG]'),
  info: isProd ? () => {} : console.log.bind(console, '[INFO]'),
  log: isProd ? () => {} : console.log.bind(console),
  warn: console.warn.bind(console),
  error: console.error.bind(console)
}
