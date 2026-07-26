import { computed, ref } from 'vue'
import { describe, expect, it } from 'vitest'

import { useCourseWorkspaceRoutes } from '@/composables/useCourseWorkspaceRoutes'

describe('useCourseWorkspaceRoutes', () => {
  it('builds teacher workspace routes for teacher role', () => {
    const userRole = ref('TEACHER')
    const routes = useCourseWorkspaceRoutes({
      userRoleRef: computed(() => userRole.value)
    })

    expect(routes.isTeacherWorkspace.value).toBe(true)
    expect(routes.courseListPath.value).toBe('/teacher/courses')
    expect(routes.courseDetailPath(12)).toBe('/teacher/courses/12')
    expect(routes.courseEditPath(12)).toBe('/teacher/courses/12/edit')
    expect(routes.slideManagePath(12)).toBe('/teacher/courses/12/slides/manage')
    expect(routes.chapterManagePath(12, 5, 'manage-videos')).toBe('/teacher/courses/12/chapters/5/manage-videos')
  })

  it('builds shared admin academic routes outside the teacher workspace', () => {
    const userRole = ref('ADMIN')
    const routes = useCourseWorkspaceRoutes({
      userRoleRef: computed(() => userRole.value)
    })

    expect(routes.isTeacherWorkspace.value).toBe(false)
    expect(routes.courseListPath.value).toBe('/courses')
    expect(routes.courseDetailPath(20)).toBe('/courses/20')
    expect(routes.courseEditPath(20)).toBe('/courses/20/edit')

    userRole.value = 'ACADEMIC'
    expect(routes.courseListPath.value).toBe('/courses')
    expect(routes.courseDetailPath(21)).toBe('/courses/21')
  })
})
