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
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/dashboard',
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/DashboardView.vue'),
      },
      {
        path: 'workorders',
        name: 'WorkOrderList',
        component: () => import('@/views/workorder/WorkOrderListView.vue'),
      },
      {
        path: 'workorders/create',
        name: 'WorkOrderCreate',
        component: () => import('@/views/workorder/WorkOrderCreateView.vue'),
      },
      {
        path: 'workorders/:id',
        name: 'WorkOrderDetail',
        component: () => import('@/views/workorder/WorkOrderDetailView.vue'),
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/UserView.vue'),
      },
      {
        path: 'teams',
        name: 'Teams',
        component: () => import('@/views/TeamView.vue'),
      },
      {
        path: 'inspection',
        name: 'Inspection',
        component: () => import('@/views/InspectionView.vue'),
      },
      {
        path: 'mes',
        name: 'Mes',
        component: () => import('@/views/MesView.vue'),
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
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
      next('/dashboard')
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
