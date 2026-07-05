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
 */
export function useUrlPagination() {
  const route = useRoute()
  const router = useRouter()

  function bindToQuery(pageRef, sizeRef, formRef, formKeys = null) {
    const keys = formKeys || Object.keys(formRef)

    // 1. mount 时从 query 初始化
    onMounted(() => {
      const q = route.query
      if (q.page) pageRef.value = Number(q.page) || 1
      if (q.size) sizeRef.value = Number(q.size) || 10
      if (formRef) {
        for (const k of keys) {
          if (q[k] !== undefined) {
            formRef[k] = ['categoryId', 'status'].includes(k) && q[k] === '' ? formRef[k] : q[k]
          }
        }
      }
    })

    // 2. watch 变化时同步到 URL（replace 不留历史）
    watch([pageRef, sizeRef, formRef].filter(Boolean), () => {
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
  }

  return { bindToQuery }
}
