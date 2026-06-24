import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { getToken, setToken, removeToken, getRefreshToken, setRefreshToken, removeRefreshToken } from './auth'

// P3-8: 提取硬编码配置为常量，便于统一调整
const API_BASE_URL = '/api'
const REQUEST_TIMEOUT = 60000 // 60s (文件上传可能较大)

let isRefreshing = false

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: REQUEST_TIMEOUT
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
    ElMessage.error(res.message || '请求失败')
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
      if (isRefreshing) return Promise.reject(error)
      isRefreshing = true
      try {
        const res = await axios.post('/api/auth/refresh', { refreshToken }, { _skipAuth: true, headers: {} })
        const newToken = res.data?.data?.accessToken
        const newRefreshToken = res.data?.data?.refreshToken
        if (newToken) {
          setToken(newToken)
          setRefreshToken(newRefreshToken || '')
          config.headers.Authorization = `Bearer ${newToken}`
          config._retry = true
          return request(config)
        }
      } catch {
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
    ElMessage.warning('资源不存在或已被删除')
  } else if (status === 403) {
    ElMessage.error('无权访问该资源，请联系管理员获取权限')
  } else if (status >= 500) {
    ElMessage.error('服务器错误，请稍后重试')
  } else {
    ElMessage.error(error.response?.data?.message || '请求失败')
  }
  return Promise.reject(error)
})

export default request