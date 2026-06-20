/**
 * 购物车 Store
 * 基于 localStorage 持久化
 */
import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'

const STORAGE_KEY = 'micro_course_cart'

export const useCartStore = defineStore('cart', () => {
  // 从 localStorage 恢复
  const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
  const items = ref(saved)

  // 持久化
  watch(items, (val) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(val))
  }, { deep: true })

  const count = computed(() => items.value.length)
  const totalPrice = computed(() => items.value.reduce((s, i) => s + (i.price || 0), 0))
  const hasItems = computed(() => items.value.length > 0)

  function addItem(course) {
    if (items.value.some(i => i.courseId === course.id)) return false
    items.value.push({
      courseId: course.id,
      title: course.title,
      coverUrl: course.coverUrl,
      price: course.price || 0,
      isFree: course.isFree ?? (course.price == null || course.price === 0),
      teacherName: course.teacherName || '',
    })
    return true
  }

  function removeItem(courseId) {
    items.value = items.value.filter(i => i.courseId !== courseId)
  }

  function clear() {
    items.value = []
  }

  return { items, count, totalPrice, hasItems, addItem, removeItem, clear }
})
