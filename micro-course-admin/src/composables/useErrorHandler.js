import { ElMessage } from 'element-plus'

/**
 * 统一错误处理 composable（Round 11-3）
 *
 * 统一各页面 catch 后的错误提示逻辑，避免每个页面重复实现：
 * - 控制台打印（便于排查）
 * - 优先取后端返回的 message，其次取 error.message，最后用默认文案
 * - ElMessage.error 弹出提示
 *
 * 用法：
 *   const { handleError, handleSuccess } = useErrorHandler()
 *   try { ... } catch (e) { handleError(e, '加载失败') }
 */
export function useErrorHandler() {
  /**
   * 统一错误处理
   * @param {*} error 捕获到的错误对象
   * @param {string} defaultMessage 默认错误提示文案
   * @returns {null} 始终返回 null，便于在赋值语句中使用
   */
  const handleError = (error, defaultMessage = '操作失败') => {
    console.error(defaultMessage + ':', error)
    const message = error?.response?.data?.message || error?.message || defaultMessage
    ElMessage.error(message)
    return null
  }

  /**
   * 统一成功提示
   * @param {string} message 成功提示文案
   */
  const handleSuccess = (message) => {
    ElMessage.success(message)
  }

  return { handleError, handleSuccess }
}
