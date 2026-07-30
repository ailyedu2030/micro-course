import { defineStore } from 'pinia'
import { login as loginApi, getCurrentUser, logout as logoutApi, refreshToken as refreshTokenApi } from '../api/auth'
import { setToken, getToken, removeToken, setRefreshToken, getRefreshToken, removeRefreshToken } from '../utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    refreshToken: getRefreshToken() || '',
    userInfo: null
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    userId: (state) => state.userInfo?.id || null,
    role: (state) => state.userInfo?.role || '',
    realName: (state) => state.userInfo?.realName || '',
    phone: (state) => state.userInfo?.phone || '',
    username: (state) => state.userInfo?.username || ''
  },
  actions: {
    async login(loginData) {
      const res = await loginApi(loginData)
      const token = res.data.accessToken
      const refreshToken = res.data.refreshToken
      setToken(token)
      setRefreshToken(refreshToken)
      this.token = token
      this.refreshToken = refreshToken
      await this.getInfo()
      return res
    },
    async getInfo() {
      if (!this.token) return null  // 无token时不发起请求,避免401
      const res = await getCurrentUser()
      this.userInfo = res.data
      return res.data
    },
    async refreshUserInfo() {
      try {
        const res = await getCurrentUser()
        this.userInfo = res.data
        return res.data
      } catch {
        return null
      }
    },
    async refreshAccessToken() {
      if (!this.refreshToken) return null
      try {
        const res = await refreshTokenApi(this.refreshToken)
        const newToken = res.data.accessToken
        const newRefreshToken = res.data.refreshToken
        setToken(newToken)
        setRefreshToken(newRefreshToken)
        this.token = newToken
        this.refreshToken = newRefreshToken
        return newToken
      } catch (e) {
        // P1-I: 瞬态网络错误/5xx 不攻击性清空 localStorage，
        // 保留 token 让调用方决定是否重试，而非直接被强制登出。
        if (!e?.response || e?.response?.status >= 500 || e?.code === 'ECONNABORTED') {
          return null  // 保留 token，调用方重试
        }
        // 401/403: refresh token 失效，清理凭证
        removeToken()
        removeRefreshToken()
        this.token = ''
        this.refreshToken = ''
        this.userInfo = null
        return null
      }
    },
    async logout() {
      const token = getToken()
      if (token) {
        try { await logoutApi() } catch (e) { console.warn(e); }
      }
      removeToken()
      removeRefreshToken()
      Object.keys(localStorage).filter(k => k.startsWith('micro_course_')).forEach(k => localStorage.removeItem(k))
      this.token = ''
      this.refreshToken = ''
      this.userInfo = null
    }
  }
})