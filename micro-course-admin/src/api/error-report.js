import request from '../utils/request'

export function reportError(data) {
  return request({
    method: 'POST',
    url: '/frontend-errors',
    data: {
      message: data.message,
      stack: data.stack,
      url: data.url,
      line: data.line,
      col: data.col,
      timestamp: Date.now()
    }
  })
}
