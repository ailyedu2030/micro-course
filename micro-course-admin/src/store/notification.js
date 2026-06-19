import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { getUnreadCount, getNotifications, markAsRead, markAllAsRead } from '../api/notification'
import { isAuthenticated } from '../utils/auth'

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    unreadCount: 0,
    list: [],
    pollingTimer: null
  }),
  actions: {
    async fetchUnreadCount() {
      try {
        const res = await getUnreadCount()
        this.unreadCount = res.data || 0
      } catch (e) {
        console.warn('[notification] fetchUnreadCount failed', e)
      }
    },
    async fetchList(params = {}) {
      try {
        const res = await getNotifications(params)
        this.list = res.data?.items || []
        return res.data
      } catch (e) {
        console.warn('[notification] fetchList failed', e)
        return { items: [], totalElements: 0, error: true }
      }
    },
    async markRead(id) {
      try {
        await markAsRead(id)
        this.unreadCount = Math.max(0, this.unreadCount - 1)
        const item = this.list.find(n => n.id === id)
        if (item) item.isRead = true
      } catch (e) {
        console.warn('[notification] markRead failed id=', id, e)
        ElMessage.warning('标记已读失败,请稍后重试')
      }
    },
    async markAllRead() {
      try {
        await markAllAsRead()
        this.unreadCount = 0
        this.list.forEach(n => { n.isRead = true })
      } catch (e) {
        console.warn('[notification] markAllRead failed', e)
        ElMessage.warning('全部标记已读失败,请稍后重试')
      }
    },
    async _pollLoop(intervalMs) {
      await this.fetchUnreadCount()
      this.pollingTimer = setTimeout(() => this._pollLoop(intervalMs), intervalMs)
    },
    startPolling(intervalMs = 30000) {
      if (!isAuthenticated()) return
      this._pollLoop(intervalMs)
    },
    stopPolling() {
      if (this.pollingTimer) {
        clearTimeout(this.pollingTimer)
        this.pollingTimer = null
      }
    }
  }
})