import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/dashboard'
    },
    {
      path: '/auth/login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: {
        public: true
      }
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      children: [
        {
          path: '/dashboard',
          component: () => import('@/views/dashboard/AdminDashboardView.vue')
        },
        {
          path: '/homes',
          component: () => import('@/views/homes/FamilyManagementView.vue')
        },
        {
          path: '/rooms',
          component: () => import('@/views/homes/RoomManagementView.vue')
        },
        {
          path: '/products',
          component: () => import('@/views/products/ProductManagementView.vue')
        },
        {
          path: '/devices',
          component: () => import('@/views/devices/AdminDevicesView.vue')
        },
        {
          path: '/ota',
          component: () => import('@/views/ota/AdminOtaView.vue')
        },
        {
          path: '/alarms',
          component: () => import('@/views/ops/AlarmCenterView.vue')
        },
        {
          path: '/work-orders',
          component: () => import('@/views/ops/WorkOrderCenterView.vue')
        },
        {
          path: '/audits',
          component: () => import('@/views/ops/AuditCenterView.vue')
        },
        {
          path: '/dead-letters',
          component: () => import('@/views/ops/DeadLetterView.vue')
        },
        {
          path: '/ai-governance',
          component: () => import('@/views/ai/AdminAiGovernanceView.vue')
        },
        {
          path: '/ai-rules',
          component: () => import('@/views/ai/AiRuleGovernanceView.vue')
        }
      ]
    }
  ]
});

router.beforeEach((to) => {
  const authStore = useAuthStore();
  if (to.meta.public) {
    return true;
  }
  if (!authStore.bearerToken) {
    return '/auth/login';
  }
  return true;
});

export default router;
