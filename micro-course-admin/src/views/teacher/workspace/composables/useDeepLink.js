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
    } else if (route.query.sectionId) {
      store.selectedItem = { type: 'lesson', id: Number(route.query.sectionId) }
    }
  }

  watch(() => store.selectedItem, (item) => {
    const query = { ...route.query }
    delete query.chapterId
    delete query.sectionId
    if (item?.type === 'chapter') query.chapterId = String(item.id)
    if (item?.type === 'lesson') query.sectionId = String(item.id)
    router.replace({ query })
  }, { deep: true })

  return { initFromRoute }
}
