<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { getOtaPage } from '@/api/admin';
import { cancelOtaTask, deleteFirmwarePackage, listFirmwarePackages } from '@/api/ota';
import { useAuthStore } from '@/stores/auth';
import { getErrorMessage } from '@/utils/error';
import type { AdminOtaTaskRecord, PageResponse } from '@/types/admin';
import type { FirmwarePackageResp } from '@/types/ota';

const authStore = useAuthStore();
const loading = ref(false);
const packagesLoading = ref(false);
const deletingPackageId = ref('');
const cancellingTaskId = ref('');
const packages = ref<FirmwarePackageResp[]>([]);
const packageFilter = ref('');
const message = ref('');
const messageType = ref<'success' | 'error'>('success');
const page = ref<PageResponse<AdminOtaTaskRecord>>({
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

function setMessage(text: string, type: 'success' | 'error' = 'success') {
  message.value = text;
  messageType.value = type;
}

async function loadOtaTasks() {
  loading.value = true;
  try {
    page.value = await getOtaPage({
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

async function loadPackages() {
  packagesLoading.value = true;
  try {
    packages.value = await listFirmwarePackages(packageFilter.value.trim() || undefined);
  } catch (error) {
    packages.value = [];
    setMessage(getErrorMessage(error), 'error');
  } finally {
    packagesLoading.value = false;
  }
}

async function removePackage(item: FirmwarePackageResp) {
  if (!window.confirm(`确定删除固件包「${item.version || item.packageId}」吗？`)) return;
  deletingPackageId.value = item.packageId;
  try {
    await deleteFirmwarePackage(item.packageId);
    setMessage('固件包删除成功', 'success');
    await loadPackages();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    deletingPackageId.value = '';
  }
}

async function cancelTask(item: AdminOtaTaskRecord) {
  if (!window.confirm(`确定取消任务「${item.taskId}」吗？`)) return;
  cancellingTaskId.value = item.taskId;
  try {
    await cancelOtaTask(item.taskId);
    setMessage(`任务 ${item.taskId} 已取消`, 'success');
    await loadOtaTasks();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    cancellingTaskId.value = '';
  }
}

watch(() => authStore.selectedHomeId, () => {
  void loadOtaTasks();
});

onMounted(() => {
  void loadOtaTasks();
  void loadPackages();
});
</script>

<template>
  <section class="page-section">
    <div class="page-header">
      <div>
        <h3>OTA 治理</h3>
        <p>聚焦版本任务治理，不把升级控制逻辑下沉到前端页面。</p>
      </div>
      <button class="admin-button" :disabled="loading" @click="loadOtaTasks">
        {{ loading ? '加载中...' : '刷新 OTA' }}
      </button>
    </div>

    <p v-if="message" class="flash" :class="messageType === 'success' ? 'flash-success' : 'flash-error'">
      {{ message }}
    </p>

    <article class="panel">
      <div class="panel-row">
        <h4>固件包</h4>
        <div class="filter-row">
          <input v-model="packageFilter" class="admin-input" placeholder="productKey（可选过滤）" />
          <button class="admin-button" :disabled="packagesLoading" @click="loadPackages">
            {{ packagesLoading ? '加载中...' : '查询' }}
          </button>
        </div>
      </div>
      <table class="admin-table">
        <thead>
          <tr>
            <th>packageId</th>
            <th>productKey</th>
            <th>版本</th>
            <th>下载地址</th>
            <th>校验和</th>
            <th>发布说明</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in packages" :key="item.packageId">
            <td>{{ item.packageId }}</td>
            <td>{{ item.productKey || '-' }}</td>
            <td>{{ item.version || '-' }}</td>
            <td>{{ item.downloadUrl || '-' }}</td>
            <td>{{ item.checksum || '-' }}</td>
            <td>{{ item.releaseNotes || '-' }}</td>
            <td>{{ item.status ?? '-' }}</td>
            <td class="action-cell">
              <button
                class="admin-button admin-button-secondary"
                :disabled="deletingPackageId === item.packageId"
                @click="removePackage(item)"
              >
                {{ deletingPackageId === item.packageId ? '删除中...' : '删除' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="table-footer">共 {{ packages.length }} 个固件包</p>
    </article>

    <article class="panel">
      <div class="filter-row">
        <input v-model="query.productKey" class="admin-input" placeholder="productKey" />
        <select v-model="query.status" class="admin-input">
          <option value="">全部状态</option>
          <option value="1">进行中</option>
          <option value="2">已完成</option>
        </select>
        <button class="admin-button" @click="loadOtaTasks">查询</button>
      </div>
      <table class="admin-table">
        <thead>
          <tr>
            <th>任务ID</th>
            <th>homeId</th>
            <th>productKey</th>
            <th>目标版本</th>
            <th>状态</th>
            <th>成功/失败/总数</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in page.records" :key="item.taskId">
            <td>{{ item.taskId }}</td>
            <td>{{ item.homeId || '-' }}</td>
            <td>{{ item.productKey || '-' }}</td>
            <td>{{ item.targetVersion || '-' }}</td>
            <td>{{ item.status ?? '-' }}</td>
            <td>{{ item.successCount || 0 }}/{{ item.failedCount || 0 }}/{{ item.totalCount || 0 }}</td>
            <td class="action-cell">
              <button
                class="admin-button admin-button-secondary"
                :disabled="cancellingTaskId === item.taskId || item.status !== 1"
                @click="cancelTask(item)"
              >
                {{ cancellingTaskId === item.taskId ? '取消中...' : '取消' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="table-footer">total={{ page.total }} pageNo={{ page.pageNo }} pageSize={{ page.pageSize }}</p>
    </article>
  </section>
</template>
