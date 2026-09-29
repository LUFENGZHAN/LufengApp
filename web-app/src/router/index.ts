import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guest: true, title: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { guest: true, title: '注册' },
  },
  {
    path: '/',
    component: () => import('@/views/ChatLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/chat' },
      { path: 'chat/:id?', name: 'chat', component: () => import('@/views/ChatView.vue') },
      {
        // 好友列表在左侧列表栏（ContactList），右侧只渲染选中好友的资料
        path: 'contacts/:userId?',
        name: 'contacts',
        component: () => import('@/views/ContactDetailView.vue'),
      },
      { path: 'profile', name: 'profile', component: () => import('@/views/ProfileView.vue') },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/chat' },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // 已登录访问登录/注册页 → 直接回主界面
  if (to.meta.guest && auth.isLogin) {
    return { path: '/' }
  }

  if (to.meta.requiresAuth && !auth.isLogin) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `麓风 · ${to.meta.title}` : '麓风聊天'
})

export default router
