export default {
  id: 'interactive',
  name: '互动课程',
  version: '1.0.0',
  enabled: import.meta.env.VITE_PLUGIN_INTERACTIVE !== 'false',

  routes: [
    {
      path: '/teacher/courses/:id/slides',
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
    typeLabel: '互动课',
    typeColor: '#67c23a',
    typeIcon: 'Present'
  },

  editors: {
    INTERACTIVE: () => import('./components/InteractiveLessonEditor.vue'),
  },

  properties: {
    INTERACTIVE: () => import('./components/InteractiveLessonProperties.vue'),
  },
}
