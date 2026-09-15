import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import type { LoginResp } from '@/types/auth';

const TOKEN_KEY = 'aiot.admin.token';
const USER_KEY = 'aiot.admin.user';
const HOME_KEY = 'aiot.admin.homeId';

interface SessionUser {
  userId: string;
  globalUserId: string;
  nickname: string;
}

const EMPTY_USER: SessionUser = { userId: '', globalUserId: '', nickname: '' };

function readUser(): SessionUser {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? { ...EMPTY_USER, ...JSON.parse(raw) } : { ...EMPTY_USER };
  } catch {
    return { ...EMPTY_USER };
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '');
  const user = ref<SessionUser>(readUser());
  const selectedHomeId = ref(localStorage.getItem(HOME_KEY) || '');

  const bearerToken = computed(() => {
    const value = token.value.trim();
    if (!value) {
      return '';
    }
    return value.startsWith('Bearer ') ? value : `Bearer ${value}`;
  });

  const isAuthenticated = computed(() => Boolean(bearerToken.value));

  const nickname = computed(() => user.value.nickname || '管理员');

  function setSession(session: LoginResp) {
    token.value = session.token.trim();
    localStorage.setItem(TOKEN_KEY, token.value);
    user.value = {
      userId: session.userId || '',
      globalUserId: session.globalUserId || '',
      nickname: session.nickname || ''
    };
    localStorage.setItem(USER_KEY, JSON.stringify(user.value));
  }

  function clearSession() {
    token.value = '';
    user.value = { ...EMPTY_USER };
    selectedHomeId.value = '';
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(HOME_KEY);
  }

  // 兼容 http 拦截器 401 处理
  function clearToken() {
    clearSession();
  }

  function setSelectedHomeId(value: string) {
    selectedHomeId.value = value.trim();
    if (selectedHomeId.value) {
      localStorage.setItem(HOME_KEY, selectedHomeId.value);
      return;
    }
    localStorage.removeItem(HOME_KEY);
  }

  return {
    token,
    user,
    selectedHomeId,
    bearerToken,
    isAuthenticated,
    nickname,
    setSession,
    clearSession,
    clearToken,
    setSelectedHomeId
  };
});
