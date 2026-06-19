import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import { getUnreadCount, getNotifications, markAsRead, markAllAsRead } from '../api/notification'
import { isAuthenticated } from '../utils/auth'

/** 退避间隔上限（5 分钟） */
const MAX_BACKOFF_MS = 300_000
/** 连续失败多少次弹出报警 */
const FAIL_ALARM_THRESHOLD = 3

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    unreadCount: 0,
    list: [],
    totalElements: 0,
    pollingTimer: null,
    /** @private 基础轮询间隔 */
    _baseInterval: 30_000,
    /** @private 连续失败次数（用于指数退避 + 报警） */
    _failCount: 0,
    /** @private visibilitychange 回调引用 */
    _visibilityHandler: null,
    /** @private 轮询是否被 visibility 暂停 */
    _pausedByVisibility: false
  }),

  actions: {
    // ------------------------------------------------------------------
    // 数据拉取
    // ------------------------------------------------------------------
    async fetchUnreadCount() {
      try {
        const res = await getUnreadCount()
        this.unreadCount = res.data || 0
        this._failCount = 0
      } catch (e) {
        this._handlePollError(e)
      }
    },

    async fetchList(params = {}) {
      try {
        const res = await getNotifications(params)
        this.list = res.data?.items || []
        this.totalElements = res.data?.totalElements || 0
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

    // ------------------------------------------------------------------
    // 轮询（含指数退避 + visibility 暂停 + 401 熔断）
    // ------------------------------------------------------------------

    /** P0-3: 根据连续失败次数计算退避间隔 */
    _getBackoffInterval() {
      if (this._failCount <= 0) return this._baseInterval
      const backoff = this._baseInterval * Math.pow(2, this._failCount)
      return Math.min(backoff, MAX_BACKOFF_MS)
    },

    /** P0-4 + P1: 轮询错误处理 —— 401/403 熔断，连续失败报警 */
    _handlePollError(e) {
      const status = e?.response?.status
      if (status === 401 || status === 403) {
        console.warn('[notification] 认证失效，停止轮询', status)
        this.stopPolling()
        return
      }
      this._failCount++
      console.warn(`[notification] fetchUnreadCount failed (连续第 ${this._failCount} 次)`, e)
      if (this._failCount === FAIL_ALARM_THRESHOLD) {
        ElMessage.warning('通知服务连接异常，已自动降低刷新频率')
      }
    },

    /** 内部轮询循环 */
    async _pollLoop() {
      if (!isAuthenticated()) {
        this.stopPolling()
        return
      }
      await this.fetchUnreadCount()
      const interval = this._getBackoffInterval()
      this.pollingTimer = setTimeout(() => this._pollLoop(), interval)
    },

    /** P0-2: 启动 / 恢复实际轮询定时器 */
    _startPollingInternal() {
      if (this.pollingTimer) return // 已在运行
      if (!isAuthenticated()) return
      this._pollLoop()
    },

    /** P0-2: 暂停实际轮询定时器（不清理 visibility 监听） */
    _stopPollingInternal() {
      if (this.pollingTimer) {
        clearTimeout(this.pollingTimer)
        this.pollingTimer = null
      }
    },

    /** P0-2: 注册 visibilitychange 监听 */
    _startVisibilityListener() {
      if (this._visibilityHandler) return
      this._visibilityHandler = () => {
        if (document.hidden) {
          this._pausedByVisibility = true
          this._stopPollingInternal()
        } else {
          this._pausedByVisibility = false
          this._startPollingInternal()
        }
      }
      document.addEventListener('visibilitychange', this._visibilityHandler)
    },

    _stopVisibilityListener() {
      if (this._visibilityHandler) {
        document.removeEventListener('visibilitychange', this._visibilityHandler)
        this._visibilityHandler = null
      }
    },

    /** 公开 API：启动轮询 */
    startPolling(intervalMs = 30_000) {
      if (!isAuthenticated()) return
      this._baseInterval = intervalMs
      this._failCount = 0
      this._startVisibilityListener()
      this._startPollingInternal()
    },

    /** 公开 API：停止轮询（完全清理） */
    stopPolling() {
      this._stopPollingInternal()
      this._stopVisibilityListener()
      this._pausedByVisibility = false
    }
  }
})
