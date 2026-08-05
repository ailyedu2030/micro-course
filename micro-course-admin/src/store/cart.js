/**
 * 购物车 Store — P2-16 服务端同步版本
 * 登录后从后端 /api/cart 拉取；离线降级到 localStorage
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { logger } from '@/utils/logger'
import { getCart, addCartItem, removeCartItem as apiRemove, clearCart as apiClear } from '@/api/cart'
import { getCourseById } from '@/api/course'

const STORAGE_KEY = 'micro_course_cart'

export const useCartStore = defineStore('cart', () => {
  const items = ref([])
  const synced = ref(false)
  /** 正在提交中的 courseId 集合，防止快速双击导致重复 */
  const _pendingAdds = new Set()

  // 服务端拉取
  async function loadFromServer() {
    try {
      const res = await getCart()
      const rawItems = res.data || []
      // P1-C 修复 (2026-08-04): 服务端购物车仅存 courseId/quantity，不含课程标题/价格/封面，
      // 原逻辑直接赋值 → 结算页表格行空白、合计 ¥0、支付按钮金额错误。
      // 修复：并行拉取课程详情合并到购物车条目。
      const enriched = await Promise.all(rawItems.map(async (it) => {
        try {
          const { data: course } = await getCourseById(it.courseId)
          return {
            id: it.id,
            courseId: it.courseId,
            quantity: it.quantity,
            title: course?.title || '',
            price: course?.price != null ? Number(course.price) : (course?.listPrice != null ? Number(course.listPrice) : 0),
            coverUrl: course?.coverUrl || '',
            teacherName: course?.teacherName || '',
            isFree: course?.isFree ?? (course?.price == null || Number(course.price) === 0)
          }
        } catch {
          return { id: it.id, courseId: it.courseId, quantity: it.quantity, title: '', price: 0, coverUrl: '', teacherName: '', isFree: true }
        }
      }))
      items.value = enriched
      synced.value = true
      // 同步到 localStorage 兜底
      localStorage.setItem(STORAGE_KEY, JSON.stringify(items.value))
    } catch (e) {
      // 离线/未登录：降级到 localStorage
      items.value = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
      synced.value = false
    }
  }

  const count = computed(() => items.value.length)
  const totalPrice = computed(() => items.value.reduce((s, i) => s + (i.price || 0), 0))
  const hasItems = computed(() => items.value.length > 0)

  async function addItem(course) {
    // 串行化守卫: 同一 course 的 in-flight 提交直接忽略
    if (_pendingAdds.has(course.id)) return false
    // P0 修复 (2026-08-04): 首次加入购物车时 synced=false（store 未加载过服务端），
    // 原逻辑直接跳过服务端写入只存 localStorage → 结算页 loadFromServer() 用服务端
    // 空数据覆盖本地 → "购物车为空"跳回广场，购物车功能整体不可用。
    // 修复：写入前先同步服务端状态，确保后续 addCartItem 走服务端持久化。
    if (!synced.value) {
      try {
        await loadFromServer()
      } catch {
        // 网络异常时继续本地降级（loadFromServer 内部已有 localStorage 兜底）
      }
    }
    // 检查是否已在购物车
    const exists = items.value.some(i => i.courseId === course.id)
    if (exists) {
      // 异步同步到服务端（确保服务端也有该记录）
      if (synced.value) {
        try { await addCartItem(course.id, 1) } catch (e) {
          logger.error('[cart] 服务端同步失败', e)
          ElMessage.warning('购物车同步失败，请刷新页面')
        }
      }
      return false  // 已存在，不重复添加
    }
    // 标记正在提交
    _pendingAdds.add(course.id)
    try {
      // 先同步到服务端获取 cartItem.id（P0-002: 保存服务端返回的 cartItem.id）
      let cartItemId = null
      let serverSuccess = false
      if (synced.value) {
        try {
          const res = await addCartItem(course.id, 1)
          cartItemId = res.data?.id
          serverSuccess = true
        } catch (e) {
          logger.error('[cart] 服务端同步失败', e)
          ElMessage.warning('购物车同步失败，请刷新页面')
        }
      }
      // 本地显示
      const newItem = {
        id: cartItemId,  // P0-002: 保存 cartItem.id 供 removeItem 使用
        courseId: course.id,
        title: course.title,
        coverUrl: course.coverUrl,
        price: course.price || 0,
        isFree: course.isFree ?? (course.price == null || course.price === 0),
        teacherName: course.teacherName || '',
      }
      items.value.push(newItem)
      // P2-002: 如果已同步但服务端添加失败，从本地列表中回滚移除
      if (synced.value && !serverSuccess) {
        items.value = items.value.filter(i => i.courseId !== course.id)
        return false
      }
      return true
    } finally {
      _pendingAdds.delete(course.id)
    }
  }

  async function removeItem(courseId) {
    // P0-002: 用 item.id（cartItem.id）而非 courseId 调用删除 API
    const item = items.value.find(i => i.courseId === courseId)
    items.value = items.value.filter(i => i.courseId !== courseId)
    if (synced.value && item?.id) {
      try { await apiRemove(item.id) } catch (e) {
        logger.error('[cart] 服务端同步失败', e)
        ElMessage.warning('购物车同步失败，请刷新页面')
      }
    }
  }

  async function clear() {
    items.value = []
    if (synced.value) {
      try { await apiClear() } catch (e) {
        logger.error('[cart] 服务端同步失败', e)
        ElMessage.warning('购物车同步失败，请刷新页面')
      }
    }
  }

  return { items, count, totalPrice, hasItems, synced, loadFromServer, addItem, removeItem, clear }
})
