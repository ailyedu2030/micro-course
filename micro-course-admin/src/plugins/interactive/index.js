export default {
  id: 'interactive',
  name: '课件课程',
  version: '1.0.0',
  enabled: import.meta.env.VITE_PLUGIN_INTERACTIVE !== 'false',

  routes: [
    {
      path: '/teacher/courses/:courseId/slides/manage',
      name: 'SlideManage',
      component: () => import('./views/teacher/SlideManage.vue'),
      meta: { requiresAuth: true, roles: ['TEACHER', 'ADMIN'] }
    },
    {
      path: '/student/courses/:id/slides/player',
      name: 'SlidePlayer',
      component: () => import('../../views/student/SlidePlayer.vue'),
      meta: { requiresAuth: true, roles: ['STUDENT'] }
    }
  ],

  courseCardConfig: {
    typeLabel: '课件',
    /* A11Y(2026-08-05): #67c23a + 白字对比度 2.24:1(serious)；
       改为 #2e7d32（白字 5.13:1 达标），保持绿色语义 */
    typeColor: '#2e7d32',
    typeIcon: 'Present'
  },

  editors: {
    INTERACTIVE: () => import('./components/InteractiveLessonEditor.vue'),
  },

  properties: {
    INTERACTIVE: () => import('./components/InteractiveLessonProperties.vue'),
  },
}
