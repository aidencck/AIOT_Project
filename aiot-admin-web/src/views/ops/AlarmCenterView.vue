<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { acknowledgeAlarm, getAlarms, resolveAlarm } from '@/api/ops';
import { useAuthStore } from '@/stores/auth';
import { getErrorMessage } from '@/utils/error';
import type { AlarmRecord } from '@/types/ops';

const authStore = useAuthStore();
const loading = ref(false);
const ackingId = ref('');
const resolvingId = ref('');
const alarms = ref<AlarmRecord[]>([]);
const message = ref('');
const messageType = ref<'success' | 'error'>('success');

const query = reactive({
  status: '',
  deviceId: '',
  deviceIdentity: ''
});

function formatTime(ts?: number) {
  return ts ? new Date(ts).toLocaleString() : '-';
}

function alarmStatusLabel(status?: string) {
  if (status === 'ACKED') return '已确认';
  if (status === 'NEW') return '待确认';
  if (status === 'RESOLVED') return '已解决';
  return status || '-';
}

function setMessage(text: string, type: 'success' | 'error' = 'success') {
  message.value = text;
  messageType.value = type;
}

async function loadAlarms() {
  loading.value = true;
  try {
    alarms.value = await getAlarms({
      status: query.status || undefined,
      deviceId: query.deviceId || undefined,
      deviceIdentity: query.deviceIdentity || undefined
    });
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    loading.value = false;
  }
}

async function ack(alarm: AlarmRecord) {
  ackingId.value = alarm.alarmId;
  try {
    await acknowledgeAlarm(alarm.alarmId, authStore.nickname);
    setMessage(`告警 ${alarm.alarmId} 已确认`, 'success');
    await loadAlarms();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    ackingId.value = '';
  }
}

async function resolve(alarm: AlarmRecord) {
  resolvingId.value = alarm.alarmId;
  try {
    await resolveAlarm(alarm.alarmId, authStore.nickname);
    setMessage(`告警 ${alarm.alarmId} 已解决`, 'success');
    await loadAlarms();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    resolvingId.value = '';
  }
}

onMounted(() => {
  void loadAlarms();
});
</script>

<template>
  <section class="page-section">
    <div class="page-header">
      <div>
        <h3>告警中心</h3>
        <p>聚合规则引擎告警记录，支持人工确认闭环。</p>
      </div>
      <button class="admin-button" :disabled="loading" @click="loadAlarms">
        {{ loading ? '加载中...' : '刷新告警' }}
      </button>
    </div>

    <p v-if="message" class="flash" :class="messageType === 'success' ? 'flash-success' : 'flash-error'">
      {{ message }}
    </p>

    <article class="panel">
      <div class="filter-row">
        <input v-model="query.status" class="admin-input" placeholder="status（如 NEW/ACKED）" />
        <input v-model="query.deviceId" class="admin-input" placeholder="deviceId" />
        <input v-model="query.deviceIdentity" class="admin-input" placeholder="deviceIdentity" />
        <button class="admin-button" @click="loadAlarms">查询</button>
      </div>
      <table class="admin-table">
        <thead>
          <tr>
            <th>告警ID</th>
            <th>设备ID</th>
            <th>设备SN</th>
            <th>事件类型</th>
            <th>级别</th>
            <th>状态</th>
            <th>发生时间</th>
            <th>确认人</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in alarms" :key="item.alarmId">
            <td>{{ item.alarmId }}</td>
            <td>{{ item.deviceId || '-' }}</td>
            <td>{{ item.deviceSn || '-' }}</td>
            <td>{{ item.eventType || '-' }}</td>
            <td>{{ item.level || '-' }}</td>
            <td>{{ alarmStatusLabel(item.status) }}</td>
            <td>{{ formatTime(item.occurredAt) }}</td>
            <td>{{ item.acknowledgedBy || '-' }}</td>
            <td class="action-cell">
              <button
                class="admin-button"
                :disabled="ackingId === item.alarmId || item.status === 'ACKED'"
                @click="ack(item)"
              >
                {{ ackingId === item.alarmId ? '确认中...' : '确认告警' }}
              </button>
              <button
                v-if="item.status !== 'RESOLVED'"
                class="admin-button"
                :disabled="resolvingId === item.alarmId"
                @click="resolve(item)"
              >
                {{ resolvingId === item.alarmId ? '解决中...' : '解决' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="table-footer">共 {{ alarms.length }} 条记录</p>
    </article>
  </section>
</template>
