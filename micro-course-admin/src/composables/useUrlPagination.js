import { watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/**
 * P2-14: URL 分页同步 composable
 *
 * 双向同步 page/size/searchForm 到 URL query，刷新后保留状态。
 *
 * 使用:
 *   const { page, size, bindToQuery } = useUrlPagination()
 *   bindToQuery(page, size, searchForm)
 *   bindToQuery(page, size, searchForm, null, ['courseId', 'chapterId']) // 指定数字字段
 */
export function useUrlPagination() {
  const route = useRoute()
  const router = useRouter()

  function bindToQuery(pageRef, sizeRef, formRef, formKeys = null, numberKeys = []) {
    const keys = formKeys || Object.keys(formRef)
    // P2-2026-08-21: 防止 URL→状态 与 状态→URL 双向 watch 回声循环
    let applyingFromUrl = false

    // 1. mount 时从 query 初始化
    onMounted(() => {
      const q = route.query
      if (q.page) pageRef.value = Number(q.page) || 1
      if (q.size) sizeRef.value = Number(q.size) || 10
      if (formRef) {
        for (const k of keys) {
          if (q[k] !== undefined) {
            if (numberKeys.includes(k)) {
              formRef[k] = q[k] === '' ? formRef[k] : Number(q[k])
            } else {
              formRef[k] = ['categoryId', 'status'].includes(k) && q[k] === '' ? formRef[k] : q[k]
            }
          }
        }
      }
    })

    // 2. watch 变化时同步到 URL（replace 不留历史）
    watch([pageRef, sizeRef, formRef].filter(Boolean), () => {
      if (applyingFromUrl) return
      const query = { ...route.query }
      query.page = String(pageRef.value)
      query.size = String(sizeRef.value)
      if (formRef) {
        for (const k of keys) {
          query[k] = formRef[k] === '' || formRef[k] == null ? undefined : String(formRef[k])
        }
      }
      router.replace({ query }).catch(() => { /* 防止重复导航错误 */ })
    }, { deep: true })

    // 3. P2-2026-08-21: 浏览器前进/后退改变 URL → 恢复状态并触发列表刷新（原仅 mount 初始化一次，单向同步）
    watch(() => route.query, (q) => {
      if (q.page) pageRef.value = Number(q.page) || 1
      if (q.size) sizeRef.value = Number(q.size) || 10
      if (formRef) {
        applyingFromUrl = true
        for (const k of keys) {
          if (q[k] !== undefined) {
            formRef[k] = numberKeys.includes(k) ? (q[k] === '' ? formRef[k] : Number(q[k])) : q[k]
          }
        }
        applyingFromUrl = false
      }
    })
  }

  return { bindToQuery }
}
