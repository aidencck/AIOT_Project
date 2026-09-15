<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { createProduct, deleteProduct, getProducts } from '@/api/products';
import { getErrorMessage } from '@/utils/error';
import type { ProductResp } from '@/types/products';

const loading = ref(false);
const submitting = ref(false);
const actingKey = ref('');
const products = ref<ProductResp[]>([]);
const message = ref('');
const messageType = ref<'success' | 'error'>('success');

const form = reactive({
  productKey: '',
  name: '',
  description: '',
  nodeType: 1,
  thingModelJson: '',
  deviceModelJson: ''
});

function nodeTypeLabel(nodeType?: number) {
  switch (nodeType) {
    case 1:
      return '直连设备';
    case 2:
      return '网关';
    case 3:
      return '子设备';
    default:
      return nodeType == null ? '-' : String(nodeType);
  }
}

function setMessage(text: string, type: 'success' | 'error' = 'success') {
  message.value = text;
  messageType.value = type;
}

async function loadProducts() {
  loading.value = true;
  try {
    products.value = await getProducts();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    loading.value = false;
  }
}

async function submit() {
  if (!form.name.trim()) {
    setMessage('产品名称不能为空', 'error');
    return;
  }
  if (!form.thingModelJson.trim() && !form.deviceModelJson.trim()) {
    setMessage('thingModelJson 或 deviceModelJson 至少填写一个', 'error');
    return;
  }
  submitting.value = true;
  try {
    const productKey = await createProduct({
      productKey: form.productKey.trim() || undefined,
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      nodeType: form.nodeType,
      thingModelJson: form.thingModelJson.trim() || undefined,
      deviceModelJson: form.deviceModelJson.trim() || undefined
    });
    setMessage(`产品创建成功：${productKey}`, 'success');
    form.productKey = '';
    form.name = '';
    form.description = '';
    form.nodeType = 1;
    form.thingModelJson = '';
    form.deviceModelJson = '';
    await loadProducts();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    submitting.value = false;
  }
}

async function removeProduct(product: ProductResp) {
  const productKey = product.productKey;
  if (!productKey) {
    setMessage('该产品缺少 productKey，无法删除', 'error');
    return;
  }
  if (!window.confirm(`确定删除产品「${product.name || productKey}」吗？`)) return;
  actingKey.value = productKey;
  try {
    await deleteProduct(productKey);
    setMessage('产品删除成功', 'success');
    await loadProducts();
  } catch (error) {
    setMessage(getErrorMessage(error), 'error');
  } finally {
    actingKey.value = '';
  }
}

onMounted(() => {
  void loadProducts();
});
</script>

<template>
  <section class="page-section">
    <div class="page-header">
      <div>
        <h3>产品建模</h3>
        <p>定义产品节点类型与物模型，支撑设备域纳管。</p>
      </div>
      <button class="admin-button" :disabled="loading" @click="loadProducts">
        {{ loading ? '加载中...' : '刷新产品' }}
      </button>
    </div>

    <p v-if="message" class="flash" :class="messageType === 'success' ? 'flash-success' : 'flash-error'">
      {{ message }}
    </p>

    <article class="panel">
      <h4>创建产品</h4>
      <div class="form-grid">
        <input v-model="form.name" class="admin-input" placeholder="产品名称（必填）" />
        <input v-model="form.productKey" class="admin-input" placeholder="productKey（可选，留空自动生成）" />
        <input v-model="form.description" class="admin-input" placeholder="产品描述（可选）" />
        <select v-model="form.nodeType" class="admin-input">
          <option :value="1">直连设备</option>
          <option :value="2">网关</option>
          <option :value="3">子设备</option>
        </select>
        <textarea v-model="form.thingModelJson" class="admin-input admin-textarea" placeholder="thingModelJson（物模型 JSON）"></textarea>
        <textarea v-model="form.deviceModelJson" class="admin-input admin-textarea" placeholder="deviceModelJson（设备模型 JSON）"></textarea>
      </div>
      <div class="form-actions">
        <button class="admin-button" :disabled="submitting" @click="submit">
          {{ submitting ? '创建中...' : '创建产品' }}
        </button>
        <small class="table-footer">thingModelJson 或 deviceModelJson 至少填写一个</small>
      </div>
    </article>

    <article class="panel">
      <h4>产品列表</h4>
      <table class="admin-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>productKey</th>
            <th>名称</th>
            <th>描述</th>
            <th>节点类型</th>
            <th>deviceModelKey</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in products" :key="item.id">
            <td>{{ item.id }}</td>
            <td>{{ item.productKey || '-' }}</td>
            <td>{{ item.name || '-' }}</td>
            <td>{{ item.description || '-' }}</td>
            <td>{{ nodeTypeLabel(item.nodeType) }}</td>
            <td>{{ item.deviceModelKey || '-' }}</td>
            <td class="action-cell">
              <button
                class="admin-button admin-button-secondary"
                :disabled="actingKey === item.productKey || !item.productKey"
                @click="removeProduct(item)"
              >
                {{ actingKey === item.productKey ? '删除中...' : '删除' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="table-footer">共 {{ products.length }} 个产品</p>
    </article>
  </section>
</template>
