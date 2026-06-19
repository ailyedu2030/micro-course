import { defineStore } from 'pinia'
import { login as loginApi, getCurrentUser, logout as logoutApi } from '../api/auth'
import { setToken, removeToken } from '../utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({ token: sessionStorage.getItem('micro_course_token') || '', userInfo: null }),
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
      setToken(token)
      this.token = token
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
    async logout() {
      try { await logoutApi() } catch (e) { console.warn(e); }
      removeToken()
      // 清全部 micro_course_ 前缀 storage + userRole
      sessionStorage.removeItem('userRole')
      Object.keys(sessionStorage).filter(k => k.startsWith('micro_course_')).forEach(k => sessionStorage.removeItem(k))
      this.token = ''
      this.userInfo = null
    }
  }
})