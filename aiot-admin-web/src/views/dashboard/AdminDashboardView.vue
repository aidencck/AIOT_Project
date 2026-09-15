<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { getWorkbench } from '@/api/admin';
import { getDashboardOverview } from '@/api/ops';
import { useAuthStore } from '@/stores/auth';
import type { AdminWorkbench } from '@/types/admin';
import type { DashboardOverview } from '@/types/ops';

const authStore = useAuthStore();
const loading = ref(false);
const workbench = ref<AdminWorkbench | null>(null);
const opsOverview = ref<DashboardOverview | null>(null);

function dash(value?: number) {
  return value == null ? '-' : `${value}`;
}

const topStats = computed(() => {
  const overview = workbench.value?.overview;
  const base: Array<{ label: string; value: string }> = [];
  if (overview) {
    base.push(
      { label: '闭环评分', value: `${workbench.value?.closureScore ?? 0}` },
      { label: '家庭数', value: `${overview.homeCount}` },
      { label: '成员数', value: `${overview.memberCount}` },
      { label: '产品数', value: `${overview.productCount}` },
      { label: '设备数', value: `${overview.deviceCount}` },
      { label: '待处理工单', value: `${overview.pendingWorkOrderCount}` }
    );
  }
  base.push(
    { label: '在线设备', value: dash(opsOverview.value?.onlineDeviceCount) },
    { label: '离线设备', value: dash(opsOverview.value?.offlineDeviceCount) },
    { label: '今日告警', value: dash(opsOverview.value?.todayAlarmCount) },
    { label: '一次性解决率', value: dash(opsOverview.value?.oneTimeResolveRate) }
  );
  return base;
});

async function loadWorkbench() {
  loading.value = true;
  try {
    const [data, opsData] = await Promise.all([
      getWorkbench(authStore.selectedHomeId || undefined),
      getDashboardOverview()
    ]);
    workbench.value = data;
    opsOverview.value = opsData;
    if (!authStore.selectedHomeId && data.selectedHomeId) {
      authStore.setSelectedHomeId(data.selectedHomeId);
    }
  } finally {
    loading.value = false;
  }
}

watch(() => authStore.selectedHomeId, () => {
  void loadWorkbench();
});

onMounted(() => {
  void loadWorkbench();
});
</script>

<template>
  <section class="page-section">
    <div class="page-header">
      <div>
        <h3>管理工作台</h3>
        <p>聚合家庭、设备、OTA、工单与 AI 治理，作为控制面统一入口。</p>
      </div>
      <button class="admin-button" :disabled="loading" @click="loadWorkbench">
        {{ loading ? '加载中...' : '刷新工作台' }}
      </button>
    </div>

    <div class="stats-grid">
      <article v-for="item in topStats" :key="item.label" class="panel stat-panel">
        <span class="panel-label">{{ item.label }}</span>
        <strong class="panel-value">{{ item.value }}</strong>
      </article>
    </div>

    <div class="two-column-grid">
      <article class="panel">
        <h4>闭环阶段</h4>
        <div class="stage-grid">
          <div
            v-for="stage in workbench?.closureStages || []"
            :key="stage.stageKey"
            class="stage-card"
            :class="stage.status.toLowerCase()"
          >
            <div class="stage-title">
              <span>{{ stage.stageName }}</span>
              <strong>{{ stage.status }}</strong>
            </div>
            <p>{{ stage.summary }}</p>
            <small>{{ stage.actionHint }}</small>
          </div>
        </div>
      </article>

      <article class="panel">
        <h4>工作台快照</h4>
        <ul class="snapshot-list">
          <li>selectedHomeId: {{ workbench?.selectedHomeId || '-' }}</li>
          <li>设备纳管: {{ workbench?.devices.length || 0 }}</li>
          <li>OTA任务: {{ workbench?.otaTasks.length || 0 }}</li>
          <li>AI报告: {{ workbench?.aiEvalReports.length || 0 }}</li>
          <li>读模式: {{ workbench?.overview.aiPersistenceReadMode || '-' }}</li>
          <li>配置模式: {{ workbench?.overview.aiPersistenceConfiguredReadMode || '-' }}</li>
        </ul>
      </article>
    </div>

    <article class="panel">
      <h4>AI 门禁摘要</h4>
      <table class="admin-table">
        <thead>
          <tr>
            <th>场景</th>
            <th>报告存在</th>
            <th>门禁结果</th>
            <th>生成时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in workbench?.aiEvalReports || []" :key="item.sceneType">
            <td>{{ item.sceneType }}</td>
            <td>{{ item.exists ? '是' : '否' }}</td>
            <td>{{ item.gatePassed ? '通过' : '未通过' }}</td>
            <td>{{ item.generatedAt || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </article>
  </section>
</template>
