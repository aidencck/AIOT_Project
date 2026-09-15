<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { claimWorkOrder, checkWorkOrderSla, getWorkOrders, resolveWorkOrder } from '@/api/ops';
import { useAuthStore } from '@/stores/auth';
import { getErrorMessage } from '@/utils/error';
import type { WorkOrderRecord } from '@/types/ops';

const authStore = useAuthStore();
const loading = ref(false);
const slaChecking = ref(false);
const actingId = ref('');
const orders = ref<WorkOrderRecord[]>([]);
const message = ref('');
const messageType = ref<'success' | 'error'>('success');
const resolveDrafts = reactive<Record<string, string>>({});

const query = reactive({
  status: '',
  assignee: '',
  deviceId: '',
  deviceIdentity: ''
});

function workOrderStatusLabel(status?: string) {
  switch (status) {
    case 'OPEN':
      return '待认领';
    case 'IN_PROGRESS':
      return '处理中';
    case 'RESOLVED':
      return '已解决';
    case 'SLA_BREACHED':
      return 'SLA超时';
    default:
      return status || '-';
  }
}

function setMessage(text: string, type: 'success' | 'error' = 'success') {
  message.value = text;
  messageType.value = type;
}

async function loadWorkOrders() {
  loading.value = true;
  try {
    orders.value = await getWorkOrders({
      status: query.status || undefined,
      assignee: query.assignee || undefined,
      deviceId: query.deviceId || undefined,
      deviceIdentity: query.deviceIdentity || undefined
    });
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    loading.value = false;
  }
}

async function claim(order: WorkOrderRecord) {
  actingId.value = order.workOrderId;
  try {
    await claimWorkOrder(order.workOrderId, authStore.nickname);
    setMessage(`工单 ${order.workOrderId} 已认领`, 'success');
    await loadWorkOrders();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    actingId.value = '';
  }
}

async function resolve(order: WorkOrderRecord) {
  const result = resolveDrafts[order.workOrderId]?.trim();
  if (!result) {
    setMessage('请先填写处理结果', 'error');
    return;
  }
  actingId.value = order.workOrderId;
  try {
    await resolveWorkOrder(order.workOrderId, {
      operator: authStore.nickname,
      result
    });
    delete resolveDrafts[order.workOrderId];
    setMessage(`工单 ${order.workOrderId} 已解决`, 'success');
    await loadWorkOrders();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    actingId.value = '';
  }
}

async function checkSla() {
  slaChecking.value = true;
  try {
    const count = await checkWorkOrderSla();
    setMessage(`SLA 超时工单数：${count}`, 'success');
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    slaChecking.value = false;
  }
}

onMounted(() => {
  void loadWorkOrders();
});
</script>

<template>
  <section class="page-section">
    <div class="page-header">
      <div>
        <h3>工单中心</h3>
        <p>认领并解决告警衍生工单，形成运维处理闭环。</p>
      </div>
      <button class="admin-button" :disabled="loading" @click="loadWorkOrders">
        {{ loading ? '加载中...' : '刷新工单' }}
      </button>
      <button class="admin-button admin-button-secondary" :disabled="slaChecking" @click="checkSla">
        {{ slaChecking ? '检查中...' : 'SLA检查' }}
      </button>
    </div>

    <p v-if="message" class="flash" :class="messageType === 'success' ? 'flash-success' : 'flash-error'">
      {{ message }}
    </p>

    <article class="panel">
      <div class="filter-row">
        <input v-model="query.status" class="admin-input" placeholder="status（如 OPEN/RESOLVED）" />
        <input v-model="query.assignee" class="admin-input" placeholder="assignee" />
        <input v-model="query.deviceId" class="admin-input" placeholder="deviceId" />
        <input v-model="query.deviceIdentity" class="admin-input" placeholder="deviceIdentity" />
        <button class="admin-button" @click="loadWorkOrders">查询</button>
      </div>
      <table class="admin-table">
        <thead>
          <tr>
            <th>工单ID</th>
            <th>告警ID</th>
            <th>设备ID</th>
            <th>设备SN</th>
            <th>优先级</th>
            <th>状态</th>
            <th>处理人</th>
            <th>处理结果</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in orders" :key="item.workOrderId">
            <td>{{ item.workOrderId }}</td>
            <td>{{ item.alarmId || '-' }}</td>
            <td>{{ item.deviceId || '-' }}</td>
            <td>{{ item.deviceSn || '-' }}</td>
            <td>{{ item.priority || '-' }}</td>
            <td>{{ workOrderStatusLabel(item.status) }}</td>
            <td>{{ item.assignee || '-' }}</td>
            <td>{{ item.result || '-' }}</td>
            <td class="action-cell">
              <button
                class="admin-button"
                :disabled="actingId === item.workOrderId || item.status !== 'OPEN'"
                @click="claim(item)"
              >
                {{ actingId === item.workOrderId ? '处理中...' : '认领工单' }}
              </button>
              <input
                v-model="resolveDrafts[item.workOrderId]"
                class="admin-input admin-input-inline"
                placeholder="处理结果"
                :disabled="item.status === 'RESOLVED'"
              />
              <button
                class="admin-button"
                :disabled="actingId === item.workOrderId || item.status === 'RESOLVED'"
                @click="resolve(item)"
              >
                解决工单
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="table-footer">共 {{ orders.length }} 条记录</p>
    </article>
  </section>
</template>
