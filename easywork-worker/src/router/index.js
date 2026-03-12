import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/',
    redirect: '/workorders',
  },
  {
    path: '/workorders',
    name: 'WorkOrderList',
    component: () => import('@/views/WorkOrderListView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/workorders/:id',
    name: 'WorkOrderDetail',
    component: () => import('@/views/WorkOrderDetailView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/scan',
    name: 'Scan',
    component: () => import('@/views/ScanView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/call',
    name: 'Call',
    component: () => import('@/views/CallView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/workorders',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')

  if (to.meta.requiresAuth === false) {
    if (token && to.path === '/login') {
      next('/workorders')
    } else {
      next()
    }
    return
  }

  if (!token) {
    next('/login')
    return
  }

  next()
})

export default router
