<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import {
  addHomeMember,
  createHome,
  deleteHome,
  getHomeMembers,
  getHomes,
  removeHomeMember,
  updateHome,
  updateHomeMemberRole
} from '@/api/homes';
import { getErrorMessage } from '@/utils/error';
import type { HomeMemberResp, HomeResp } from '@/types/homes';

interface MemberForm {
  userId: string;
  role: number;
}

const loading = ref(false);
const submitting = ref(false);
const homeActing = ref('');
const memberActing = ref('');
const roleActingId = ref('');
const renamingId = ref('');
const renameOpen = reactive<Record<string, boolean>>({});
const renameForms = reactive<Record<string, { name: string; location: string }>>({});
const homes = ref<HomeResp[]>([]);
const membersMap = reactive<Record<string, HomeMemberResp[]>>({});
const memberForms = reactive<Record<string, MemberForm>>({});
const message = ref('');
const messageType = ref<'success' | 'error'>('success');

const createForm = reactive({
  name: '',
  location: ''
});

function roleLabel(role?: number) {
  switch (role) {
    case 1:
      return '拥有者';
    case 2:
      return '管理员';
    case 3:
      return '成员';
    default:
      return role == null ? '-' : String(role);
  }
}

function setMessage(text: string, type: 'success' | 'error' = 'success') {
  message.value = text;
  messageType.value = type;
}

async function loadHomes() {
  loading.value = true;
  try {
    homes.value = await getHomes();
    homes.value.forEach((home) => {
      if (!memberForms[home.id]) {
        memberForms[home.id] = { userId: '', role: 3 };
      }
    });
    await Promise.all(homes.value.map((home) => loadMembers(home.id)));
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    loading.value = false;
  }
}

async function loadMembers(homeId: string) {
  try {
    membersMap[homeId] = await getHomeMembers(homeId);
  } catch (error) {
    membersMap[homeId] = [];
    setMessage(getErrorMessage(error), 'error');
  }
}

async function submit() {
  if (!createForm.name.trim()) {
    setMessage('家庭名称不能为空', 'error');
    return;
  }
  submitting.value = true;
  try {
    await createHome({
      name: createForm.name.trim(),
      location: createForm.location.trim() || undefined
    });
    setMessage('家庭创建成功', 'success');
    createForm.name = '';
    createForm.location = '';
    await loadHomes();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    submitting.value = false;
  }
}

async function removeHome(home: HomeResp) {
  if (!window.confirm(`确定删除家庭「${home.name || home.id}」吗？`)) return;
  homeActing.value = home.id;
  try {
    await deleteHome(home.id);
    setMessage('家庭删除成功', 'success');
    await loadHomes();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    homeActing.value = '';
  }
}

function openRename(home: HomeResp) {
  renameForms[home.id] = {
    name: home.name || '',
    location: home.location || ''
  };
  renameOpen[home.id] = !renameOpen[home.id];
}

async function submitRename(home: HomeResp) {
  const form = renameForms[home.id];
  if (!form || !form.name.trim()) {
    setMessage('家庭名称不能为空', 'error');
    return;
  }
  renamingId.value = home.id;
  try {
    await updateHome(home.id, {
      name: form.name.trim(),
      location: form.location.trim() || undefined
    });
    setMessage('家庭信息已更新', 'success');
    renameOpen[home.id] = false;
    await loadHomes();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    renamingId.value = '';
  }
}

async function addMember(home: HomeResp) {
  const form = memberForms[home.id];
  if (!form.userId.trim()) {
    setMessage('请填写用户ID', 'error');
    return;
  }
  memberActing.value = home.id;
  try {
    await addHomeMember(home.id, { userId: form.userId.trim(), role: form.role });
    setMessage('成员添加成功', 'success');
    form.userId = '';
    form.role = 3;
    await loadMembers(home.id);
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    memberActing.value = '';
  }
}

async function removeMember(home: HomeResp, userId: string) {
  if (!window.confirm(`确定移除成员「${userId}」吗？`)) return;
  memberActing.value = home.id;
  try {
    await removeHomeMember(home.id, userId);
    setMessage('成员移除成功', 'success');
    await loadMembers(home.id);
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    memberActing.value = '';
  }
}

async function updateRole(home: HomeResp, member: HomeMemberResp, role: number) {
  roleActingId.value = `${home.id}:${member.userId}`;
  try {
    await updateHomeMemberRole(home.id, member.userId, { role });
    setMessage(`成员 ${member.nickname || member.userId} 角色已更新`, 'success');
    await loadMembers(home.id);
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    roleActingId.value = '';
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
        <h3>家庭管理</h3>
        <p>管理家庭、成员与角色，打通组织域闭环。</p>
      </div>
      <button class="admin-button" :disabled="loading" @click="loadHomes">
        {{ loading ? '加载中...' : '刷新家庭' }}
      </button>
    </div>

    <p v-if="message" class="flash" :class="messageType === 'success' ? 'flash-success' : 'flash-error'">
      {{ message }}
    </p>

    <article class="panel">
      <h4>创建家庭</h4>
      <div class="filter-row">
        <input v-model="createForm.name" class="admin-input" placeholder="家庭名称（必填）" />
        <input v-model="createForm.location" class="admin-input" placeholder="家庭位置（可选）" />
        <button class="admin-button" :disabled="submitting" @click="submit">
          {{ submitting ? '创建中...' : '创建家庭' }}
        </button>
      </div>
    </article>

    <article v-for="home in homes" :key="home.id" class="panel">
      <div class="panel-row">
        <div>
          <h4>{{ home.name || '-' }}</h4>
          <small class="table-footer">{{ home.id }} · {{ home.location || '未填写位置' }}</small>
        </div>
        <div class="filter-row">
          <span class="admin-user">我的角色：{{ roleLabel(home.role) }}</span>
          <button class="admin-button" :disabled="homeActing === home.id" @click="openRename(home)">
            改名
          </button>
          <button class="admin-button admin-button-secondary" :disabled="homeActing === home.id" @click="removeHome(home)">
            {{ homeActing === home.id ? '删除中...' : '删除家庭' }}
          </button>
        </div>
      </div>

      <div v-if="renameOpen[home.id]" class="filter-row form-row">
        <input v-model="renameForms[home.id].name" class="admin-input" placeholder="家庭名称" />
        <input v-model="renameForms[home.id].location" class="admin-input" placeholder="家庭位置" />
        <button class="admin-button" :disabled="renamingId === home.id" @click="submitRename(home)">
          {{ renamingId === home.id ? '保存中...' : '保存' }}
        </button>
      </div>

      <div class="filter-row form-row">
        <input v-model="memberForms[home.id].userId" class="admin-input" placeholder="成员 userId" />
        <select v-model="memberForms[home.id].role" class="admin-input">
          <option :value="2">管理员</option>
          <option :value="3">成员</option>
        </select>
        <button class="admin-button" :disabled="memberActing === home.id" @click="addMember(home)">
          {{ memberActing === home.id ? '添加中...' : '添加成员' }}
        </button>
      </div>

      <table class="admin-table">
        <thead>
          <tr>
            <th>用户ID</th>
            <th>昵称</th>
            <th>手机号</th>
            <th>角色</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="member in membersMap[home.id]" :key="member.userId">
            <td>{{ member.userId }}</td>
            <td>{{ member.nickname || '-' }}</td>
            <td>{{ member.phone || '-' }}</td>
            <td>{{ roleLabel(member.role) }}</td>
            <td class="action-cell">
              <template v-if="member.role !== 1">
                <button
                  class="admin-button"
                  :disabled="roleActingId === `${home.id}:${member.userId}` || member.role === 2"
                  @click="updateRole(home, member, 2)"
                >
                  设为管理员
                </button>
                <button
                  class="admin-button"
                  :disabled="roleActingId === `${home.id}:${member.userId}` || member.role === 3"
                  @click="updateRole(home, member, 3)"
                >
                  设为成员
                </button>
              </template>
              <button
                class="admin-button admin-button-secondary"
                :disabled="memberActing === home.id"
                @click="removeMember(home, member.userId)"
              >
                移除
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="table-footer">共 {{ (membersMap[home.id] || []).length }} 名成员</p>
    </article>
  </section>
</template>
