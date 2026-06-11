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