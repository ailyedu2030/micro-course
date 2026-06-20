import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWorkspaceStore } from '../workspace.store'

export function useDeepLink() {
  const route = useRoute()
  const router = useRouter()
  const store = useWorkspaceStore()

  function initFromRoute() {
    if (route.query.chapterId) {
      store.selectedItem = { type: 'chapter', id: Number(route.query.chapterId) }
    } else if (route.query.lessonId) {
      store.selectedItem = { type: 'lesson', id: Number(route.query.lessonId) }
    }
  }

  watch(() => store.selectedItem, (item) => {
    const query = { ...route.query }
    delete query.chapterId
    delete query.lessonId
    if (item?.type === 'chapter') query.chapterId = String(item.id)
    if (item?.type === 'lesson') query.lessonId = String(item.id)
    router.replace({ query })
  }, { deep: true })

  return { initFromRoute }
}
