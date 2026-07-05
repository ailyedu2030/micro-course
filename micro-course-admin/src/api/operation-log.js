/**
 * 操作日志 API
 * /operation-logs 接口封装
 */
import request from '../utils/request'

/**
 * 分页查询操作日志
 * GET /operation-logs?page=0&size=20&userId=&action=&startTime=&endTime=
 * @param {Object} params - { page, size, userId, action, startTime, endTime }
 */
export function getLogs(params) {
  return request({ method: 'GET', url: '/operation-logs', params })
}

/**
 * 导出操作日志 Excel
 * GET /operation-logs/export
 * @param {Object} params - 与 getLogs 相同的筛选参数
 * @returns {Blob} Excel 文件二进制数据
 */
export function exportOperationLogs(params) {
  return request({
    method: 'GET',
    url: '/operation-logs/export',
    params,
    responseType: 'blob'
  })
}