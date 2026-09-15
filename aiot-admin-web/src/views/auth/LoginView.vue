<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { login, register } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';

type Mode = 'login' | 'register';

const router = useRouter();
const authStore = useAuthStore();

const mode = ref<Mode>('login');
const loading = ref(false);
const error = ref('');

const form = reactive({
  phone: '',
  password: '',
  nickname: ''
});

function switchMode(next: Mode) {
  mode.value = next;
  error.value = '';
}

function validateCommon(): string {
  if (!/^1\d{10}$/.test(form.phone)) {
    return '请输入 11 位手机号';
  }
  if (!form.password || form.password.length < 6) {
    return '密码长度至少 6 位';
  }
  return '';
}

async function submitLogin() {
  error.value = '';
  const invalid = validateCommon();
  if (invalid) {
    error.value = invalid;
    return;
  }
  loading.value = true;
  try {
    const resp = await login({ phone: form.phone, password: form.password });
    authStore.setSession(resp);
    await router.push('/dashboard');
  } catch (e) {
    error.value = extractMessage(e);
  } finally {
    loading.value = false;
  }
}

async function submitRegister() {
  error.value = '';
  const invalid = validateCommon();
  if (invalid) {
    error.value = invalid;
    return;
  }
  if (!form.nickname.trim()) {
    error.value = '请输入昵称';
    return;
  }
  loading.value = true;
  try {
    await register({ phone: form.phone, password: form.password, nickname: form.nickname.trim() });
    // 注册成功后直接登录进入后台
    const resp = await login({ phone: form.phone, password: form.password });
    authStore.setSession(resp);
    await router.push('/dashboard');
  } catch (e) {
    error.value = extractMessage(e);
  } finally {
    loading.value = false;
  }
}

function extractMessage(e: unknown): string {
  const err = e as { response?: { data?: { message?: string } }; message?: string };
  return err?.response?.data?.message || err?.message || '请求失败，请稍后重试';
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h1>AIoT Admin Web</h1>
      <p>独立前端控制台，统一消费 admin-console 聚合契约。</p>

      <div class="auth-tabs">
        <button
          class="auth-tab"
          :class="{ active: mode === 'login' }"
          @click="switchMode('login')"
        >
          登录
        </button>
        <button
          class="auth-tab"
          :class="{ active: mode === 'register' }"
          @click="switchMode('register')"
        >
          注册
        </button>
      </div>

      <form class="auth-form" @submit.prevent="mode === 'login' ? submitLogin() : submitRegister()">
        <label class="auth-field">
          <span>手机号</span>
          <input
            v-model="form.phone"
            class="admin-input"
            inputmode="numeric"
            maxlength="11"
            placeholder="11 位手机号"
            autocomplete="username"
          />
        </label>

        <label class="auth-field">
          <span>密码</span>
          <input
            v-model="form.password"
            class="admin-input"
            type="password"
            placeholder="至少 6 位"
            autocomplete="current-password"
          />
        </label>

        <label v-if="mode === 'register'" class="auth-field">
          <span>昵称</span>
          <input
            v-model="form.nickname"
            class="admin-input"
            placeholder="展示名称"
            autocomplete="nickname"
          />
        </label>

        <p v-if="error" class="auth-error">{{ error }}</p>

        <button class="admin-button auth-submit" type="submit" :disabled="loading">
          {{ loading ? '处理中…' : mode === 'login' ? '登录' : '注册并登录' }}
        </button>
      </form>
    </div>
  </div>
</template>
