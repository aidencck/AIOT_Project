<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import {
  getAiBusinessLiveFlow,
  getAiEvalReports,
  getAiPersistenceQuery
} from '@/api/admin';
import { drainAiControlPlane } from '@/api/ops';
import { useAuthStore } from '@/stores/auth';
import { getErrorMessage } from '@/utils/error';
import type {
  AdminAiEvalReport,
  AdminBusinessLiveFlow,
  AdminHistoryItem
} from '@/types/admin';
import type { ControlPlaneDrainResp } from '@/types/ops';

const authStore = useAuthStore();
const loading = ref(false);
const draining = ref(false);
const reports = ref<AdminAiEvalReport[]>([]);
const businessLiveFlow = ref<AdminBusinessLiveFlow | null>(null);
const history = ref<AdminHistoryItem[]>([]);
const message = ref('');
const messageType = ref<'success' | 'error'>('success');
const drainResult = ref<ControlPlaneDrainResp | null>(null);

const drainForm = reactive({
  dryRun: true,
  batchSize: undefined as number | undefined,
  stores: ''
});

function setMessage(text: string, type: 'success' | 'error' = 'success') {
  message.value = text;
  messageType.value = type;
}

async function loadAiData() {
  loading.value = true;
  try {
    const [reportData, liveFlowData, queryData] = await Promise.all([
      getAiEvalReports(),
      getAiBusinessLiveFlow(),
      getAiPersistenceQuery()
    ]);
    reports.value = reportData;
    businessLiveFlow.value = liveFlowData;
    history.value = [
      ...((queryData.businessLiveFlow?.recentHistory || []) as AdminHistoryItem[]),
      ...((queryData.controlPlaneDrain?.recentHistory || []) as AdminHistoryItem[])
    ].sort((left, right) => (right.occurredAt || 0) - (left.occurredAt || 0));
  } finally {
    loading.value = false;
  }
}

async function drain() {
  draining.value = true;
  try {
    drainResult.value = await drainAiControlPlane({
      operator: authStore.nickname,
      dryRun: drainForm.dryRun,
      batchSize: drainForm.batchSize || undefined,
      stores: drainForm.stores
        ? drainForm.stores.split(',').map((item) => item.trim()).filter(Boolean)
        : undefined
    });
    setMessage('控制面排空已提交', 'success');
    await loadAiData();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    draining.value = false;
  }
}

onMounted(() => {
  void loadAiData();
});
</script>

<template>
  <section class="page-section">
    <div class="page-header">
      <div>
        <h3>AI 治理</h3>
        <p>统一承接 AI 门禁、活体验证和持久化历史，不在前端拼接底层实现。</p>
      </div>
      <button class="admin-button" :disabled="loading" @click="loadAiData">
        {{ loading ? '加载中...' : '刷新 AI 治理' }}
      </button>
    </div>

    <p v-if="message" class="flash" :class="messageType === 'success' ? 'flash-success' : 'flash-error'">
      {{ message }}
    </p>

    <div class="two-column-grid">
      <article class="panel">
        <h4>AI 业务活体验证</h4>
        <ul class="snapshot-list">
          <li>报告存在: {{ businessLiveFlow?.reportExists ? '是' : '否' }}</li>
          <li>验证成功: {{ businessLiveFlow?.success ? '是' : '否' }}</li>
          <li>场景: {{ businessLiveFlow?.scene || '-' }}</li>
          <li>全局设备ID: {{ businessLiveFlow?.globalDeviceId || '-' }}</li>
          <li>认证标识: {{ businessLiveFlow?.authIdentity || '-' }}</li>
          <li>事件ID: {{ businessLiveFlow?.eventId || '-' }}</li>
        </ul>
      </article>

      <article class="panel">
        <h4>AI 门禁结果</h4>
        <table class="admin-table">
          <thead>
            <tr>
              <th>场景</th>
              <th>报告存在</th>
              <th>门禁结果</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in reports" :key="item.sceneType">
              <td>{{ item.sceneType }}</td>
              <td>{{ item.exists ? '是' : '否' }}</td>
              <td>{{ item.gatePassed ? '通过' : '未通过' }}</td>
            </tr>
          </tbody>
        </table>
      </article>
    </div>

    <article class="panel">
      <h4>控制面排空</h4>
      <div class="filter-row">
        <label class="admin-input">
          <input v-model="drainForm.dryRun" type="checkbox" />
          dryRun 演练
        </label>
        <input
          v-model.number="drainForm.batchSize"
          class="admin-input"
          type="number"
          placeholder="batchSize（可选）"
        />
        <input v-model="drainForm.stores" class="admin-input" placeholder="stores（逗号分隔，可选）" />
        <button class="admin-button" :disabled="draining" @click="drain">
          {{ draining ? '排空中...' : '执行排空' }}
        </button>
      </div>
      <ul v-if="drainResult" class="snapshot-list">
        <li>accepted: {{ drainResult.accepted == null ? '-' : (drainResult.accepted ? '是' : '否') }}</li>
        <li>dryRun: {{ drainResult.dryRun ? 'DRY_RUN' : 'APPLY' }}</li>
        <li>batchSize: {{ drainResult.batchSize ?? '-' }}</li>
        <li>message: {{ drainResult.message || '-' }}</li>
      </ul>
    </article>

    <article class="panel">
      <h4>AI 持久化历史</h4>
      <table class="admin-table">
        <thead>
          <tr>
            <th>类型</th>
            <th>时间</th>
            <th>执行人</th>
            <th>场景</th>
            <th>结果</th>
            <th>模式</th>
            <th>信息</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in history" :key="`${item.reportType}-${item.occurredAt}`">
            <td>{{ item.reportType }}</td>
            <td>{{ item.occurredAt }}</td>
            <td>{{ item.operator || '-' }}</td>
            <td>{{ item.scene || '-' }}</td>
            <td>{{ item.success === true || item.accepted === true ? '成功' : '待确认' }}</td>
            <td>{{ item.dryRun ? 'DRY_RUN' : 'APPLY' }}</td>
            <td>{{ item.message || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </article>
  </section>
</template>
