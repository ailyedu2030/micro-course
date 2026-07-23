import { beforeEach, describe, expect, it, vi } from 'vitest'

const messageMocks = vi.hoisted(() => ({
  warning: vi.fn(),
  error: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    warning: messageMocks.warning,
    error: messageMocks.error,
  },
}))

vi.mock('@/router', () => ({
  default: {
    currentRoute: {
      value: {
        path: '/teacher/dashboard',
        fullPath: '/teacher/dashboard',
      },
    },
    push: vi.fn(),
  },
}))

vi.mock('@/utils/auth', () => ({
  getToken: vi.fn(() => ''),
  setToken: vi.fn(),
  removeToken: vi.fn(),
  getRefreshToken: vi.fn(() => ''),
  setRefreshToken: vi.fn(),
  removeRefreshToken: vi.fn(),
}))

import request, { globalUploadState } from '@/utils/request'

const responseRejected = request.interceptors.response.handlers[0].rejected
const responseResolved = request.interceptors.response.handlers[0].fulfilled

describe('request.js error handling', () => {
  beforeEach(() => {
    messageMocks.warning.mockReset()
    messageMocks.error.mockReset()
    globalUploadState.active = false
    globalUploadState.percent = 0
    globalUploadState.fileName = ''
  })

  it('shows backend message for 429 responses without throwing a secondary error', async () => {
    const error = {
      response: {
        status: 429,
        data: { message: '请求过于频繁' },
      },
      config: {},
    }

    await expect(responseRejected(error)).rejects.toBe(error)
    expect(messageMocks.warning).toHaveBeenCalledWith('请求过于频繁')
    expect(messageMocks.error).not.toHaveBeenCalled()
  })

  it('shows backend message for 413 responses without throwing a secondary error', async () => {
    const error = {
      response: {
        status: 413,
        data: { message: '文件超过限制' },
      },
      config: {},
    }

    await expect(responseRejected(error)).rejects.toBe(error)
    expect(messageMocks.error).toHaveBeenCalledWith('文件超过限制')
  })

  it('resets upload state when business code is not 200', async () => {
    globalUploadState.active = true
    globalUploadState.percent = 70
    globalUploadState.fileName = 'demo.mp4'

    const response = {
      config: {},
      data: {
        code: 500,
        message: '业务失败',
      },
    }

    await expect(responseResolved(response)).rejects.toThrow('业务失败')
    expect(globalUploadState.active).toBe(false)
    expect(globalUploadState.percent).toBe(0)
    expect(globalUploadState.fileName).toBe('')
  })
})
