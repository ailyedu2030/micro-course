import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { getToken, setToken, removeToken } from './auth'

let isRefreshing = false

const request = axios.create({
  baseURL: '/api',
  timeout: 60000
})

request.interceptors.request.use(config => {
  if (config._skipAuth) return config
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
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
  return res
}, async error => {
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
    const refreshToken = sessionStorage.getItem('micro_course_refresh_token')
    if (refreshToken) {
      if (isRefreshing) return Promise.reject(error)
      isRefreshing = true
      try {
        const res = await axios.post('/api/auth/refresh', { refreshToken }, { _skipAuth: true, headers: {} })
        const newToken = res.data?.data?.accessToken
        const newRefreshToken = res.data?.data?.refreshToken
        if (newToken) {
          setToken(newToken)
          sessionStorage.setItem('micro_course_refresh_token', newRefreshToken || '')
          config.headers.Authorization = `Bearer ${newToken}`
          config._retry = true
          return request(config)
        }
      } catch {
        removeToken()
        sessionStorage.removeItem('micro_course_refresh_token')
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

  if (status === 403) {
    ElMessage.error('无权访问该资源')
  } else if (status >= 500) {
    ElMessage.error('服务器错误，请稍后重试')
  } else {
    ElMessage.error(error.response?.data?.message || '请求失败')
  }
  return Promise.reject(error)
})

export default request