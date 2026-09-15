<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const menuGroups = [
  { label: '概览', items: [{ label: '工作台', path: '/dashboard' }] },
  { label: '组织域', items: [{ label: '家庭管理', path: '/homes' }, { label: '房间管理', path: '/rooms' }] },
  {
    label: '设备域',
    items: [
      { label: '产品建模', path: '/products' },
      { label: '设备台账', path: '/devices' },
      { label: 'OTA治理', path: '/ota' }
    ]
  },
  {
    label: '运维域',
    items: [
      { label: '告警中心', path: '/alarms' },
      { label: '工单中心', path: '/work-orders' },
      { label: '审计中心', path: '/audits' },
      { label: '死信队列', path: '/dead-letters' }
    ]
  },
  {
    label: '智能域',
    items: [
      { label: 'AI治理', path: '/ai-governance' },
      { label: 'AI规则', path: '/ai-rules' }
    ]
  }
];

const selectedHomeId = computed({
  get: () => authStore.selectedHomeId,
  set: (value: string) => authStore.setSelectedHomeId(value)
});

async function logout() {
  authStore.clearSession();
  await router.push('/auth/login');
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <div class="admin-logo">
        <h1>AIOT</h1>
        <p>Admin Web</p>
      </div>
      <nav class="admin-nav">
        <div v-for="group in menuGroups" :key="group.label" class="admin-nav-group">
          <p class="admin-nav-group-label">{{ group.label }}</p>
          <RouterLink
            v-for="menu in group.items"
            :key="menu.path"
            :to="menu.path"
            class="admin-nav-link"
            :class="{ active: route.path === menu.path }"
          >
            {{ menu.label }}
          </RouterLink>
        </div>
      </nav>
    </aside>
    <div class="admin-main">
      <header class="admin-header">
        <div>
          <h2>AIoT 后台专业工作台</h2>
          <p>独立前端模块，统一消费 admin-console 聚合契约。</p>
        </div>
        <div class="admin-header-actions">
          <span class="admin-user">{{ authStore.nickname }}</span>
          <input
            v-model="selectedHomeId"
            class="admin-input"
            placeholder="homeId，可留空使用默认家庭"
          />
          <button class="admin-button admin-button-secondary" @click="logout">退出</button>
        </div>
      </header>
      <main class="admin-content">
        <RouterView />
      </main>
    </div>
  </div>
</template>
