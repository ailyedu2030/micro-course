/**
 * 购物车 Store — P2-16 服务端同步版本
 * 登录后从后端 /api/cart 拉取；离线降级到 localStorage
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
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
    // 本地立即显示
    if (!items.value.some(i => i.courseId === course.id)) {
      items.value.push({
        courseId: course.id,
        title: course.title,
        coverUrl: course.coverUrl,
        price: course.price || 0,
        isFree: course.isFree ?? (course.price == null || course.price === 0),
        teacherName: course.teacherName || '',
      })
    }
    // 异步同步到服务端
    if (synced.value) {
      try { await addCartItem(course.id, 1) } catch (e) { /* silent */ }
    }
    return true
  }

  async function removeItem(courseId) {
    const item = items.value.find(i => i.courseId === courseId)
    items.value = items.value.filter(i => i.courseId !== courseId)
    if (synced.value && item) {
      try { await apiRemove(item.id) } catch (e) { /* silent */ }
    }
  }

  async function clear() {
    items.value = []
    if (synced.value) {
      try { await apiClear() } catch (e) { /* silent */ }
    }
  }

  return { items, count, totalPrice, hasItems, synced, loadFromServer, addItem, removeItem, clear }
})
