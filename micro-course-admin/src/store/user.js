import { defineStore } from 'pinia'
import { login as loginApi, getCurrentUser, logout as logoutApi } from '../api/auth'
import { setToken, removeToken } from '../utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({ token: localStorage.getItem('micro_course_token') || '', userInfo: null }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    role: (state) => state.userInfo?.role || '',
    realName: (state) => state.userInfo?.realName || '',
    username: (state) => state.userInfo?.username || ''
  },
  actions: {
    async login(loginData) {
      const res = await loginApi(loginData)
      const token = res.data.accessToken
      setToken(token)
      this.token = token
      await this.getInfo()
      return res
    },
    async getInfo() {
      const res = await getCurrentUser()
      this.userInfo = res.data
      if (res.data?.role) {
        localStorage.setItem('userRole', res.data.role)
      }
      return res.data
    },
    async logout() {
      try { await logoutApi() } catch { /* ignore */ }
      removeToken()
      localStorage.removeItem('userRole')
      this.token = ''
      this.userInfo = null
    }
  }
})