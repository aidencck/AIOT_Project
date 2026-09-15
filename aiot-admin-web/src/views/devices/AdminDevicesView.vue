<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { createDevice, deleteDevice, getDevicePage } from '@/api/admin';
import { useAuthStore } from '@/stores/auth';
import { getErrorMessage } from '@/utils/error';
import type { PageResponse, AdminDeviceRecord } from '@/types/admin';

const authStore = useAuthStore();
const loading = ref(false);
const submitting = ref(false);
const deletingId = ref('');
const message = ref('');
const messageType = ref<'success' | 'error'>('success');
const page = ref<PageResponse<AdminDeviceRecord>>({
  total: 0,
  pageNo: 1,
  pageSize: 20,
  records: []
});

const query = reactive({
  productKey: '',
  status: '',
  pageNo: 1,
  pageSize: 20
});

const createForm = reactive({
  deviceName: '',
  productKey: '',
  homeId: authStore.selectedHomeId,
  deviceSn: '',
  authIdentity: '',
  globalDeviceId: '',
  roomId: '',
  gatewayId: '',
  firmwareVersion: ''
});

function setMessage(text: string, type: 'success' | 'error' = 'success') {
  message.value = text;
  messageType.value = type;
}

async function loadDevices() {
  loading.value = true;
  try {
    page.value = await getDevicePage({
      homeId: authStore.selectedHomeId || undefined,
      productKey: query.productKey || undefined,
      status: query.status || undefined,
      pageNo: query.pageNo,
      pageSize: query.pageSize
    });
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    loading.value = false;
  }
}

async function submitDevice() {
  if (!createForm.deviceName.trim()) {
    setMessage('设备名称不能为空', 'error');
    return;
  }
  if (!createForm.productKey.trim()) {
    setMessage('productKey 不能为空', 'error');
    return;
  }
  if (!createForm.homeId.trim()) {
    setMessage('homeId 不能为空', 'error');
    return;
  }
  submitting.value = true;
  try {
    await createDevice({
      deviceName: createForm.deviceName.trim(),
      productKey: createForm.productKey.trim(),
      homeId: createForm.homeId.trim(),
      deviceSn: createForm.deviceSn.trim() || undefined,
      authIdentity: createForm.authIdentity.trim() || undefined,
      globalDeviceId: createForm.globalDeviceId.trim() || undefined,
      roomId: createForm.roomId.trim() || undefined,
      gatewayId: createForm.gatewayId.trim() || undefined,
      firmwareVersion: createForm.firmwareVersion.trim() || undefined
    });
    setMessage('设备纳管成功', 'success');
    createForm.deviceName = '';
    createForm.productKey = '';
    createForm.deviceSn = '';
    createForm.authIdentity = '';
    createForm.globalDeviceId = '';
    createForm.roomId = '';
    createForm.gatewayId = '';
    createForm.firmwareVersion = '';
    await loadDevices();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    submitting.value = false;
  }
}

async function removeDevice(deviceId: string) {
  if (!window.confirm(`确定删除设备「${deviceId}」吗？`)) return;
  deletingId.value = deviceId;
  try {
    await deleteDevice(deviceId);
    setMessage('设备删除成功', 'success');
    await loadDevices();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    deletingId.value = '';
  }
}

watch(() => authStore.selectedHomeId, (value) => {
  createForm.homeId = value;
  void loadDevices();
});

onMounted(() => {
  createForm.homeId = authStore.selectedHomeId;
  void loadDevices();
});
</script>

<template>
  <section class="page-section">
    <div class="page-header">
      <div>
        <h3>设备台账</h3>
        <p>纳管设备并维护台账，支持删除回收。</p>
      </div>
      <button class="admin-button" :disabled="loading" @click="loadDevices">
        {{ loading ? '加载中...' : '刷新设备' }}
      </button>
    </div>

    <p v-if="message" class="flash" :class="messageType === 'success' ? 'flash-success' : 'flash-error'">
      {{ message }}
    </p>

    <article class="panel">
      <h4>纳管设备</h4>
      <div class="form-grid">
        <input v-model="createForm.deviceName" class="admin-input" placeholder="设备名称（必填）" />
        <input v-model="createForm.productKey" class="admin-input" placeholder="productKey（必填）" />
        <input v-model="createForm.homeId" class="admin-input" placeholder="homeId（必填）" />
        <input v-model="createForm.deviceSn" class="admin-input" placeholder="deviceSn（可选）" />
        <input v-model="createForm.authIdentity" class="admin-input" placeholder="authIdentity（可选）" />
        <input v-model="createForm.globalDeviceId" class="admin-input" placeholder="globalDeviceId（可选）" />
        <input v-model="createForm.roomId" class="admin-input" placeholder="roomId（可选）" />
        <input v-model="createForm.gatewayId" class="admin-input" placeholder="gatewayId（子设备必填）" />
        <input v-model="createForm.firmwareVersion" class="admin-input" placeholder="firmwareVersion（可选）" />
      </div>
      <div class="form-actions">
        <button class="admin-button" :disabled="submitting" @click="submitDevice">
          {{ submitting ? '纳管中...' : '纳管设备' }}
        </button>
      </div>
    </article>

    <article class="panel">
      <div class="filter-row">
        <input v-model="query.productKey" class="admin-input" placeholder="productKey" />
        <select v-model="query.status" class="admin-input">
          <option value="">全部状态</option>
          <option value="0">未激活</option>
          <option value="1">在线</option>
          <option value="2">离线</option>
        </select>
        <button class="admin-button" @click="loadDevices">查询</button>
      </div>
      <table class="admin-table">
        <thead>
          <tr>
            <th>设备ID</th>
            <th>名称</th>
            <th>homeId</th>
            <th>productKey</th>
            <th>状态</th>
            <th>固件版本</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in page.records" :key="item.id">
            <td>{{ item.id }}</td>
            <td>{{ item.deviceName || '-' }}</td>
            <td>{{ item.homeId || '-' }}</td>
            <td>{{ item.productKey || '-' }}</td>
            <td>{{ item.status ?? '-' }}</td>
            <td>{{ item.firmwareVersion || '-' }}</td>
            <td>
              <button
                class="admin-button admin-button-secondary"
                :disabled="deletingId === item.id"
                @click="removeDevice(item.id)"
              >
                {{ deletingId === item.id ? '删除中...' : '删除' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="table-footer">total={{ page.total }} pageNo={{ page.pageNo }} pageSize={{ page.pageSize }}</p>
    </article>
  </section>
</template>
