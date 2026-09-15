<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getAudits } from '@/api/ops';
import { getErrorMessage } from '@/utils/error';
import type { AuditRecord } from '@/types/ops';

const loading = ref(false);
const audits = ref<AuditRecord[]>([]);
const message = ref('');
const messageType = ref<'success' | 'error'>('success');

function formatTime(ts?: number) {
  return ts ? new Date(ts).toLocaleString() : '-';
}

function setMessage(text: string, type: 'success' | 'error' = 'success') {
  message.value = text;
  messageType.value = type;
}

async function loadAudits() {
  loading.value = true;
  try {
    audits.value = await getAudits();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadAudits();
});
</script>

<template>
  <section class="page-section">
    <div class="page-header">
      <div>
        <h3>审计中心</h3>
        <p>汇聚关键操作的审计轨迹，形成可追溯的管理闭环。</p>
      </div>
      <button class="admin-button" :disabled="loading" @click="loadAudits">
        {{ loading ? '加载中...' : '刷新审计' }}
      </button>
    </div>

    <p v-if="message" class="flash" :class="messageType === 'success' ? 'flash-success' : 'flash-error'">
      {{ message }}
    </p>

    <article class="panel">
      <table class="admin-table">
        <thead>
          <tr>
            <th>审计ID</th>
            <th>事件类型</th>
            <th>操作人</th>
            <th>目标ID</th>
            <th>详情</th>
            <th>TraceID</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in audits" :key="item.auditId">
            <td>{{ item.auditId }}</td>
            <td>{{ item.eventType || '-' }}</td>
            <td>{{ item.operator || '-' }}</td>
            <td>{{ item.targetId || '-' }}</td>
            <td>{{ item.details || '-' }}</td>
            <td>{{ item.traceId || '-' }}</td>
            <td>{{ formatTime(item.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <p class="table-footer">共 {{ audits.length }} 条记录</p>
    </article>
  </section>
</template>
