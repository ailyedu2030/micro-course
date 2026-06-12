import { defineStore } from 'pinia'
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
      } catch {
        /* silent */
      }
    },
    async fetchList(params = {}) {
      try {
        const res = await getNotifications(params)
        this.list = res.data?.items || []
        return res.data
      } catch {
        return { items: [], totalElements: 0 }
      }
    },
    async markRead(id) {
      try {
        await markAsRead(id)
        this.unreadCount = Math.max(0, this.unreadCount - 1)
        const item = this.list.find(n => n.id === id)
        if (item) item.isRead = true
      } catch {
        /* silent */
      }
    },
    async markAllRead() {
      try {
        await markAllAsRead()
        this.unreadCount = 0
        this.list.forEach(n => { n.isRead = true })
      } catch {
        // silent
      }
    },
    startPolling(intervalMs = 30000) {
      if (!isAuthenticated()) return
      this.fetchUnreadCount()
      this.pollingTimer = setInterval(() => {
        this.fetchUnreadCount()
      }, intervalMs)
    },
    stopPolling() {
      if (this.pollingTimer) {
        clearInterval(this.pollingTimer)
        this.pollingTimer = null
      }
    }
  }
})