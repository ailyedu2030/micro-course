import { computed, unref } from 'vue'

function getRoleValue(userRoleRef) {
  return unref(userRoleRef) || ''
}

export function useCourseWorkspaceRoutes(options = {}) {
  const {
    userRoleRef
  } = options

  const isTeacherWorkspace = computed(() => getRoleValue(userRoleRef) === 'TEACHER')
  const courseListPath = computed(() => (isTeacherWorkspace.value ? '/teacher/courses' : '/courses'))

  function courseDetailPath(courseId) {
    return `${courseListPath.value}/${courseId}`
  }

  function courseEditPath(courseId) {
    return `${courseDetailPath(courseId)}/edit`
  }

  function slideManagePath(courseId) {
    return `/teacher/courses/${courseId}/slides/manage`
  }

  function chapterManagePath(courseId, chapterId, type) {
    return `/teacher/courses/${courseId}/chapters/${chapterId}/${type}`
  }

  return {
    isTeacherWorkspace,
    courseListPath,
    courseDetailPath,
    courseEditPath,
    slideManagePath,
    chapterManagePath
  }
}
