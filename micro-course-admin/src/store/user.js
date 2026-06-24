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
      const res = await getCurrentUser()
      this.userInfo = res.data
      return res.data
    },
    async refreshAccessToken() {
      try {
        const res = await refreshTokenApi(this.refreshToken)
        const newToken = res.data.accessToken
        const newRefreshToken = res.data.refreshToken
        setToken(newToken)
        setRefreshToken(newRefreshToken)
        this.token = newToken
        this.refreshToken = newRefreshToken
        return newToken
      } catch {
        this.logout()
        return null
      }
    },
    async logout() {
      try { await logoutApi() } catch (e) { console.warn(e); }
      removeToken()
      removeRefreshToken()
      Object.keys(localStorage).filter(k => k.startsWith('micro_course_')).forEach(k => localStorage.removeItem(k))
      this.token = ''
      this.refreshToken = ''
      this.userInfo = null
    }
  }
})