<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue';
import { getHomes } from '@/api/homes';
import { createRoom, deleteRoom, listRooms, renameRoom } from '@/api/rooms';
import { getErrorMessage } from '@/utils/error';
import type { HomeResp } from '@/types/homes';
import type { RoomResp } from '@/types/rooms';

const loading = ref(false);
const submitting = ref(false);
const actingId = ref('');
const homes = ref<HomeResp[]>([]);
const selectedHomeId = ref('');
const rooms = ref<RoomResp[]>([]);
const createName = ref('');
const renameDrafts = reactive<Record<string, string>>({});
const message = ref('');
const messageType = ref<'success' | 'error'>('success');

function setMessage(text: string, type: 'success' | 'error' = 'success') {
  message.value = text;
  messageType.value = type;
}

async function loadRooms() {
  if (!selectedHomeId.value) {
    rooms.value = [];
    return;
  }
  try {
    rooms.value = await listRooms(selectedHomeId.value);
    rooms.value.forEach((room) => {
      if (!renameDrafts[room.id]) {
        renameDrafts[room.id] = room.name || '';
      }
    });
  } catch (error) {
    rooms.value = [];
    setMessage(getErrorMessage(error), 'error');
  }
}

async function loadHomes() {
  loading.value = true;
  try {
    homes.value = await getHomes();
    if (!selectedHomeId.value && homes.value.length) {
      selectedHomeId.value = homes.value[0].id;
    }
    await loadRooms();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    loading.value = false;
  }
}

watch(selectedHomeId, () => {
  void loadRooms();
});

async function submit() {
  if (!selectedHomeId.value) {
    setMessage('请先选择家庭', 'error');
    return;
  }
  if (!createName.value.trim()) {
    setMessage('房间名称不能为空', 'error');
    return;
  }
  submitting.value = true;
  try {
    await createRoom({
      homeId: selectedHomeId.value,
      name: createName.value.trim()
    });
    setMessage('房间创建成功', 'success');
    createName.value = '';
    await loadRooms();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    submitting.value = false;
  }
}

async function rename(room: RoomResp) {
  const name = (renameDrafts[room.id] || '').trim();
  if (!name) {
    setMessage('房间名称不能为空', 'error');
    return;
  }
  actingId.value = room.id;
  try {
    await renameRoom(room.id, selectedHomeId.value, name);
    delete renameDrafts[room.id];
    setMessage(`房间 ${room.id} 已改名`, 'success');
    await loadRooms();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    actingId.value = '';
  }
}

async function remove(room: RoomResp) {
  if (!window.confirm(`确定删除房间「${room.name || room.id}」吗？`)) return;
  actingId.value = room.id;
  try {
    await deleteRoom(room.id, selectedHomeId.value);
    delete renameDrafts[room.id];
    setMessage('房间删除成功', 'success');
    await loadRooms();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    actingId.value = '';
  }
}

onMounted(() => {
  void loadHomes();
});
</script>

<template>
  <section class="page-section">
    <div class="page-header">
      <div>
        <h3>房间管理</h3>
        <p>在家庭下新增、改名与删除房间，完善空间拓扑闭环。</p>
      </div>
      <button class="admin-button" :disabled="loading" @click="loadHomes">
        {{ loading ? '加载中...' : '刷新房间' }}
      </button>
    </div>

    <p v-if="message" class="flash" :class="messageType === 'success' ? 'flash-success' : 'flash-error'">
      {{ message }}
    </p>

    <article class="panel">
      <h4>选择家庭</h4>
      <div class="filter-row form-row">
        <select v-model="selectedHomeId" class="admin-input">
          <option value="" disabled>请选择家庭</option>
          <option v-for="home in homes" :key="home.id" :value="home.id">
            {{ home.name || home.id }}
          </option>
        </select>
      </div>
    </article>

    <article class="panel">
      <h4>创建房间</h4>
      <div class="filter-row form-row">
        <input v-model="createName" class="admin-input" placeholder="房间名称（必填）" />
        <button class="admin-button" :disabled="submitting" @click="submit">
          {{ submitting ? '创建中...' : '创建房间' }}
        </button>
      </div>
    </article>

    <article class="panel">
      <h4>房间列表</h4>
      <table class="admin-table">
        <thead>
          <tr>
            <th>房间ID</th>
            <th>家庭ID</th>
            <th>名称</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="room in rooms" :key="room.id">
            <td>{{ room.id }}</td>
            <td>{{ room.homeId || '-' }}</td>
            <td>{{ room.name || '-' }}</td>
            <td class="action-cell">
              <input
                v-model="renameDrafts[room.id]"
                class="admin-input admin-input-inline"
                placeholder="新名称"
                :disabled="actingId === room.id"
              />
              <button
                class="admin-button"
                :disabled="actingId === room.id"
                @click="rename(room)"
              >
                {{ actingId === room.id ? '处理中...' : '改名' }}
              </button>
              <button
                class="admin-button admin-button-secondary"
                :disabled="actingId === room.id"
                @click="remove(room)"
              >
                删除
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="table-footer">共 {{ rooms.length }} 个房间</p>
    </article>
  </section>
</template>
