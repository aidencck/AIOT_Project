<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getDeadLetters, purgeDeadLetters, replayDeadLetter } from '@/api/ops';
import { getErrorMessage } from '@/utils/error';
import type { DeadLetterListResp, DeadLetterRecord } from '@/types/ops';

const loading = ref(false);
const replayingId = ref('');
const purging = ref(false);
const list = ref<DeadLetterListResp>({ count: 0, deadLetters: [] });
const message = ref('');
const messageType = ref<'success' | 'error'>('success');

function truncate(text?: string, len = 120) {
  if (!text) return '-';
  return text.length > len ? `${text.slice(0, len)}...` : text;
}

function setMessage(text: string, type: 'success' | 'error' = 'success') {
  message.value = text;
  messageType.value = type;
}

async function loadDeadLetters() {
  loading.value = true;
  try {
    list.value = await getDeadLetters(100);
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    loading.value = false;
  }
}

async function replay(item: DeadLetterRecord) {
  replayingId.value = item.recordId;
  try {
    const result = await replayDeadLetter(item.recordId);
    setMessage(`重放成功：${result.recordId} → ${result.replayedRecordId}`, 'success');
    await loadDeadLetters();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    replayingId.value = '';
  }
}

async function purge() {
  if (!window.confirm('确定清空全部死信吗？该操作不可恢复。')) return;
  purging.value = true;
  try {
    const result = await purgeDeadLetters();
    setMessage(`已清空 ${result.purgedCount} 条死信`, 'success');
    await loadDeadLetters();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    purging.value = false;
  }
}

onMounted(() => {
  void loadDeadLetters();
});
</script>

<template>
  <section class="page-section">
    <div class="page-header">
      <div>
        <h3>死信队列</h3>
        <p>查看堆积的失败消息并按需重放，避免链路断裂。</p>
      </div>
      <button class="admin-button" :disabled="loading" @click="loadDeadLetters">
        {{ loading ? '加载中...' : '刷新死信' }}
      </button>
      <button class="admin-button admin-button-secondary" :disabled="purging" @click="purge">
        {{ purging ? '清空中...' : '清空死信' }}
      </button>
    </div>

    <p v-if="message" class="flash" :class="messageType === 'success' ? 'flash-success' : 'flash-error'">
      {{ message }}
    </p>

    <div class="stats-grid">
      <article class="panel stat-panel">
        <span class="panel-label">当前堆积数</span>
        <strong class="panel-value">{{ list.count }}</strong>
      </article>
    </div>

    <article class="panel">
      <table class="admin-table">
        <thead>
          <tr>
            <th>记录ID</th>
            <th>Payload</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in list.deadLetters" :key="item.recordId">
            <td>{{ item.recordId }}</td>
            <td class="payload-cell">{{ truncate(item.payload) }}</td>
            <td>
              <button
                class="admin-button"
                :disabled="replayingId === item.recordId"
                @click="replay(item)"
              >
                {{ replayingId === item.recordId ? '重放中...' : '重放' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="table-footer">共 {{ list.deadLetters.length }} 条死信</p>
    </article>
  </section>
</template>
