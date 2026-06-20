import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { getToken, removeToken } from './auth'

const request = axios.create({
  baseURL: '/api',   // vite proxy 转发到 localhost:8080
  timeout: 60000     // 60s: 兼容大练习提交/批量导入等耗时操作
})

request.interceptors.request.use(config => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
}, error => Promise.reject(error))

request.interceptors.response.use(response => {
  const res = response.data
  // blob/arraybuffer 跳过 R 包装检查
  if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
    return response
  }
  if (res.code !== 200) {
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message))
  }
  return res
}, error => {
  // UX-NEW-6 修复:区分网络断连/超时与服务器错误
  if (!error.response) {
    if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络后重试')
    } else {
      ElMessage.error('网络连接异常，请检查网络后重试')
    }
    return Promise.reject(error)
  }
  const status = error.response?.status
  if (status === 401) {
    removeToken()
    if (router.currentRoute.value.path !== '/login') {
      const currentPath = router.currentRoute.value.fullPath
      router.push({ path: '/login', query: { redirect: currentPath } })
      ElMessage.warning('登录已过期，请重新登录')
    } else {
      // 已经在登录页 → 显示具体错误（如"用户名或密码错误"）
      const msg = error.response?.data?.message || '用户名或密码错误'
      ElMessage.error(msg)
    }
  } else if (status === 403) {
    ElMessage.error('无权访问该资源')
  } else if (status >= 500) {
    ElMessage.error('服务器错误，请稍后重试')
  } else {
    ElMessage.error(error.response?.data?.message || '请求失败')
  }
  return Promise.reject(error)
})

export default request