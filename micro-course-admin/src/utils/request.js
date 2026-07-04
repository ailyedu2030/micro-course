import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { getToken, setToken, removeToken, getRefreshToken, setRefreshToken, removeRefreshToken } from './auth'

// P3-8: 提取硬编码配置为常量，便于统一调整
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
// P1-2: 超时按 HTTP 方法分级 — 客户体验:
//   - GET  10s: 列表/详情查询,网络慢时 10s 足以感知问题
//   - POST 30s: 提交表单/创建资源,服务端处理稍长
//   - Upload 300s: 视频/文件上传,2GB 视频按 100Mbps ≈ 160s,给足余量
const TIMEOUT_GET = 10000
const TIMEOUT_POST = 30000
const TIMEOUT_UPLOAD = 300000

/**
 * P1-2: 根据请求方法 + 是否 FormData 自动选择超时
 * 客户体验: 之前所有请求统一 60s,网络故障时用户要等 60s 才看到错误
 *          现在 GET 类 10s 即报错,减少用户等待焦虑
 */
function pickTimeout(config) {
  // 显式指定 _timeout 优先
  if (config._timeout) return config._timeout
  // 上传类 (multipart/form-data) 给 300s
  if (config.data instanceof FormData) return TIMEOUT_UPLOAD
  // 按方法分级
  if (config.method === 'get' || config.method === 'GET' || !config.method) return TIMEOUT_GET
  if (config.method === 'post' || config.method === 'POST') return TIMEOUT_POST
  if (config.method === 'put' || config.method === 'PUT' || config.method === 'patch' || config.method === 'PATCH') return TIMEOUT_POST
  if (config.method === 'delete' || config.method === 'DELETE') return TIMEOUT_GET
  return TIMEOUT_POST
}

let isRefreshing = false
// P1-I #22: 并发401重试队列——refresh期间积压的请求，refresh成功后统一重放
let pendingRequests = []

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: TIMEOUT_GET  // 默认 GET 10s,实际请求会被 pickTimeout 覆盖
})

// D2: 全局上传进度管理器（可被外部组件 useUploadProgress 消费）
import { reactive } from 'vue'
export const globalUploadState = reactive({
  active: false,
  percent: 0,
  fileName: ''
})

request.interceptors.request.use(config => {
  if (config._skipAuth) return config
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`

  // P1-2: 按方法 + 数据类型选择超时
  config.timeout = pickTimeout(config)

  // D2: FormData 上传自动注入进度回调
  if (config.data instanceof FormData && !config.onUploadProgress) {
    globalUploadState.active = true
    globalUploadState.percent = 0
    globalUploadState.fileName = ''
    // 尝试提取文件名
    for (const [key, value] of config.data.entries()) {
      if (value instanceof File || value instanceof Blob) {
        globalUploadState.fileName = value.name || ''
        break
      }
    }
    config.onUploadProgress = (progressEvent) => {
      if (progressEvent.total) {
        globalUploadState.percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
      }
    }
  }

  return config
}, error => Promise.reject(error))

request.interceptors.response.use(response => {
  const res = response.data
  if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
    return response
  }
  if (res.code !== 200) {
    return Promise.reject(new Error(res.message))
  }
  // D2: 请求完成后重置上传状态
  globalUploadState.active = false
  globalUploadState.percent = 0
  return res
}, async error => {
  // D2: 请求异常也重置上传状态
  globalUploadState.active = false
  globalUploadState.percent = 0
  if (!error.response) {
    if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络后重试')
    } else {
      ElMessage.error('网络连接异常，请检查网络后重试')
    }
    return Promise.reject(error)
  }
  const status = error.response?.status
  const config = error.config

  if (status === 401 && !config._retry && !config._skipAuth) {
    const refreshToken = getRefreshToken()
    if (refreshToken) {
      if (isRefreshing) {
        // P1-I #22: 积压到重试队列，refresh成功后重放
        return new Promise((resolve, reject) => {
          pendingRequests.push({ config, resolve, reject })
        })
      }
      isRefreshing = true
      try {
        const res = await axios.post(`${API_BASE_URL}/auth/refresh`, { refreshToken }, { _skipAuth: true, headers: {} })
        const newToken = res.data?.data?.accessToken
        const newRefreshToken = res.data?.data?.refreshToken
        if (newToken) {
          setToken(newToken)
          setRefreshToken(newRefreshToken || '')
          // 通知 store token 已刷新（避免 store.token 与 localStorage 不一致）
          window.dispatchEvent(new CustomEvent('token-refreshed', {
            detail: { token: newToken, refreshToken: newRefreshToken || '' }
          }))
          config.headers.Authorization = `Bearer ${newToken}`
          config._retry = true
          // P1-I #22: 重放所有积压请求
          const queue = pendingRequests.slice()
          pendingRequests = []
          queue.forEach(({ config: reqCfg, resolve, reject }) => {
            reqCfg.headers.Authorization = `Bearer ${newToken}`
            request(reqCfg).then(resolve).catch(reject)
          })
          return request(config)
        }
      } catch (e) {
        // P1-I #22: refresh失败，积压请求也拒绝
        const queue = pendingRequests.slice()
        pendingRequests = []
        queue.forEach(({ reject }) => reject(e))
        removeToken()
        removeRefreshToken()
      } finally {
        isRefreshing = false
      }
    }
    removeToken()
    if (router.currentRoute.value.path !== '/login') {
      const currentPath = router.currentRoute.value.fullPath
      router.push({ path: '/login', query: { redirect: currentPath } })
      ElMessage.warning('登录已过期，请重新登录')
    } else {
      const msg = error.response?.data?.message || '用户名或密码错误'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }

  if (status === 404) {
    if (!config._suppressErrorToast) {
      ElMessage.warning('资源不存在或已被删除')
    }
  } else if (status === 403) {
    if (!config._suppressErrorToast) {
      ElMessage.error('无权访问该资源，请联系管理员获取权限')
    }
  } else if (status >= 500) {
    ElMessage.error('服务器错误，请稍后重试')
  } else {
    ElMessage.error(error.response?.data?.message || '请求失败')
  }
  return Promise.reject(error)
})

export default request