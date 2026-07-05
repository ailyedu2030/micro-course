/**
 * 购物车 Store — P2-16 服务端同步版本
 * 登录后从后端 /api/cart 拉取；离线降级到 localStorage
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { logger } from '@/utils/logger'
import { getCart, addCartItem, removeCartItem as apiRemove, clearCart as apiClear } from '@/api/cart'

const STORAGE_KEY = 'micro_course_cart'

export const useCartStore = defineStore('cart', () => {
  const items = ref([])
  const synced = ref(false)

  // 服务端拉取
  async function loadFromServer() {
    try {
      const res = await getCart()
      items.value = res.data || []
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
