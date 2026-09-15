<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { approveRule, deleteRule, draftRule, listRules, rejectRule, updateRule } from '@/api/aiRules';
import { getErrorMessage } from '@/utils/error';
import type { RuleDraftResponse } from '@/types/aiRules';

const loading = ref(false);
const submitting = ref(false);
const actingId = ref('');
const editingId = ref('');
const deletingId = ref('');
const rules = ref<RuleDraftResponse[]>([]);
const statusFilter = ref('');
const message = ref('');
const messageType = ref<'success' | 'error'>('success');

const draftForm = reactive({
  requirement: '',
  deviceId: '',
  eventType: '',
  actionType: '',
  actionPayload: ''
});

const editForm = reactive({
  conditionEventType: '',
  conditionDeviceId: '',
  actionType: '',
  actionPayload: ''
});

const approveDrafts = reactive<Record<string, { approver: string; comment: string }>>({});
const rejectDrafts = reactive<Record<string, string>>({});

function setMessage(text: string, type: 'success' | 'error' = 'success') {
  message.value = text;
  messageType.value = type;
}

async function loadRules() {
  loading.value = true;
  try {
    rules.value = await listRules(statusFilter.value || undefined);
    rules.value.forEach((rule) => {
      if (!approveDrafts[rule.ruleId]) {
        approveDrafts[rule.ruleId] = { approver: '', comment: '' };
      }
      if (!rejectDrafts[rule.ruleId]) {
        rejectDrafts[rule.ruleId] = '';
      }
    });
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    loading.value = false;
  }
}

async function submitDraft() {
  if (!draftForm.requirement.trim()) {
    setMessage('需求描述不能为空', 'error');
    return;
  }
  submitting.value = true;
  try {
    const rule = await draftRule({
      requirement: draftForm.requirement.trim(),
      deviceId: draftForm.deviceId.trim() || undefined,
      eventType: draftForm.eventType.trim() || undefined,
      actionType: draftForm.actionType.trim() || undefined,
      actionPayload: draftForm.actionPayload.trim() || undefined
    });
    setMessage(`规则草稿已生成：${rule.ruleId}`, 'success');
    draftForm.requirement = '';
    draftForm.deviceId = '';
    draftForm.eventType = '';
    draftForm.actionType = '';
    draftForm.actionPayload = '';
    await loadRules();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    submitting.value = false;
  }
}

async function approve(rule: RuleDraftResponse) {
  const draft = approveDrafts[rule.ruleId] || { approver: '', comment: '' };
  if (!draft.approver.trim()) {
    setMessage('请先填写审批人', 'error');
    return;
  }
  actingId.value = rule.ruleId;
  try {
    await approveRule(rule.ruleId, {
      approver: draft.approver.trim(),
      comment: draft.comment.trim() || undefined
    });
    delete approveDrafts[rule.ruleId];
    setMessage(`规则 ${rule.ruleId} 已审批通过`, 'success');
    await loadRules();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    actingId.value = '';
  }
}

async function reject(rule: RuleDraftResponse) {
  const reason = rejectDrafts[rule.ruleId]?.trim();
  actingId.value = rule.ruleId;
  try {
    await rejectRule(rule.ruleId, { rejectReason: reason || undefined });
    delete rejectDrafts[rule.ruleId];
    setMessage(`规则 ${rule.ruleId} 已驳回`, 'success');
    await loadRules();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    actingId.value = '';
  }
}

function startEdit(rule: RuleDraftResponse) {
  editingId.value = rule.ruleId;
  editForm.conditionEventType = rule.conditionEventType || '';
  editForm.conditionDeviceId = rule.conditionDeviceId || '';
  editForm.actionType = rule.actionType || '';
  editForm.actionPayload = rule.actionPayload || '';
}

function cancelEdit() {
  editingId.value = '';
}

async function saveEdit(rule: RuleDraftResponse) {
  editingId.value = rule.ruleId;
  try {
    await updateRule(rule.ruleId, {
      conditionEventType: editForm.conditionEventType.trim() || undefined,
      conditionDeviceId: editForm.conditionDeviceId.trim() || undefined,
      actionType: editForm.actionType.trim() || undefined,
      actionPayload: editForm.actionPayload.trim() || undefined
    });
    setMessage(`规则 ${rule.ruleId} 已更新`, 'success');
    editingId.value = '';
    await loadRules();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  }
}

async function removeRule(rule: RuleDraftResponse) {
  if (!window.confirm(`确定删除规则「${rule.ruleId}」吗？`)) return;
  deletingId.value = rule.ruleId;
  try {
    await deleteRule(rule.ruleId);
    setMessage(`规则 ${rule.ruleId} 已删除`, 'success');
    await loadRules();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    deletingId.value = '';
  }
}

onMounted(() => {
  void loadRules();
});
</script>

<template>
  <section class="page-section">
    <div class="page-header">
      <div>
        <h3>AI 规则治理</h3>
        <p>从自然语言生成规则草稿，经人工审批后进入自动化闭环。</p>
      </div>
      <button class="admin-button" :disabled="loading" @click="loadRules">
        {{ loading ? '加载中...' : '刷新规则' }}
      </button>
    </div>

    <p v-if="message" class="flash" :class="messageType === 'success' ? 'flash-success' : 'flash-error'">
      {{ message }}
    </p>

    <article class="panel">
      <h4>生成规则草稿</h4>
      <div class="filter-row">
        <input v-model="draftForm.requirement" class="admin-input" placeholder="需求描述（必填）" />
        <input v-model="draftForm.deviceId" class="admin-input" placeholder="设备ID（可选）" />
        <input v-model="draftForm.eventType" class="admin-input" placeholder="事件类型（可选）" />
        <input v-model="draftForm.actionType" class="admin-input" placeholder="动作类型（可选）" />
        <input v-model="draftForm.actionPayload" class="admin-input" placeholder="动作参数（可选）" />
        <button class="admin-button" :disabled="submitting" @click="submitDraft">
          {{ submitting ? '生成中...' : '生成草稿' }}
        </button>
      </div>
    </article>

    <article class="panel">
      <div class="filter-row">
        <input v-model="statusFilter" class="admin-input" placeholder="status（可选过滤）" />
        <button class="admin-button" @click="loadRules">查询</button>
      </div>
      <table class="admin-table">
        <thead>
          <tr>
            <th>规则ID</th>
            <th>条件事件类型</th>
            <th>条件设备ID</th>
            <th>动作类型</th>
            <th>动作参数</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="item in rules" :key="item.ruleId">
            <tr>
              <td>{{ item.ruleId }}</td>
              <td>{{ item.conditionEventType || '-' }}</td>
              <td>{{ item.conditionDeviceId || '-' }}</td>
              <td>{{ item.actionType || '-' }}</td>
              <td>{{ item.actionPayload || '-' }}</td>
              <td>{{ item.status || '-' }}</td>
              <td class="action-cell">
                <input
                  v-model="approveDrafts[item.ruleId].approver"
                  class="admin-input admin-input-inline"
                  placeholder="审批人"
                  :disabled="actingId === item.ruleId"
                />
                <input
                  v-model="approveDrafts[item.ruleId].comment"
                  class="admin-input admin-input-inline"
                  placeholder="审批意见"
                  :disabled="actingId === item.ruleId"
                />
                <button
                  class="admin-button"
                  :disabled="actingId === item.ruleId"
                  @click="approve(item)"
                >
                  {{ actingId === item.ruleId ? '处理中...' : '审批' }}
                </button>
                <input
                  v-model="rejectDrafts[item.ruleId]"
                  class="admin-input admin-input-inline"
                  placeholder="驳回原因"
                  :disabled="actingId === item.ruleId"
                />
                <button
                  class="admin-button admin-button-secondary"
                  :disabled="actingId === item.ruleId"
                  @click="reject(item)"
                >
                  驳回
                </button>
                <button
                  class="admin-button"
                  :disabled="editingId === item.ruleId"
                  @click="startEdit(item)"
                >
                  编辑
                </button>
                <button
                  class="admin-button admin-button-secondary"
                  :disabled="deletingId === item.ruleId"
                  @click="removeRule(item)"
                >
                  {{ deletingId === item.ruleId ? '删除中...' : '删除' }}
                </button>
              </td>
            </tr>
            <tr v-if="editingId === item.ruleId">
              <td :colspan="7">
                <div class="filter-row">
                  <input v-model="editForm.conditionEventType" class="admin-input" placeholder="条件事件类型" />
                  <input v-model="editForm.conditionDeviceId" class="admin-input" placeholder="条件设备ID" />
                  <input v-model="editForm.actionType" class="admin-input" placeholder="动作类型" />
                  <input v-model="editForm.actionPayload" class="admin-input" placeholder="动作参数" />
                  <button class="admin-button" @click="saveEdit(item)">保存</button>
                  <button class="admin-button admin-button-secondary" @click="cancelEdit">取消</button>
                </div>
              </td>
            </tr>
          </template>
        </tbody>
      </table>
      <p class="table-footer">共 {{ rules.length }} 条规则</p>
    </article>
  </section>
</template>
