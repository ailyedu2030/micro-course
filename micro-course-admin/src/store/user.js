import { defineStore } from 'pinia'
import { login as loginApi, getCurrentUser, logout as logoutApi, refreshToken as refreshTokenApi } from '../api/auth'
import { setToken, getToken, removeToken } from '../utils/auth'
import request from '../utils/request'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: sessionStorage.getItem('micro_course_token') || '',
    refreshToken: sessionStorage.getItem('micro_course_refresh_token') || '',
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
      this.token = token
      this.refreshToken = refreshToken
      sessionStorage.setItem('micro_course_refresh_token', refreshToken)
      await this.getInfo()
      return res
    },
    async getInfo() {
      const res = await getCurrentUser()
      this.userInfo = res.data
      if (res.data?.role) {
        sessionStorage.setItem('userRole', res.data.role)
      }
      return res.data
    },
    async refreshAccessToken() {
      try {
        const res = await refreshTokenApi(this.refreshToken)
        const newToken = res.data.accessToken
        const newRefreshToken = res.data.refreshToken
        setToken(newToken)
        this.token = newToken
        this.refreshToken = newRefreshToken
        sessionStorage.setItem('micro_course_refresh_token', newRefreshToken)
        return newToken
      } catch {
        this.logout()
        return null
      }
    },
    async logout() {
      try { await logoutApi() } catch (e) { console.warn(e); }
      removeToken()
      sessionStorage.removeItem('userRole')
      sessionStorage.removeItem('micro_course_refresh_token')
      Object.keys(sessionStorage).filter(k => k.startsWith('micro_course_')).forEach(k => sessionStorage.removeItem(k))
      localStorage.removeItem('micro_course_cart')
      this.token = ''
      this.refreshToken = ''
      this.userInfo = null
    }
  }
})