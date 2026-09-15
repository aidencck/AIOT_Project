import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useAuthStore } from './auth';
import type { LoginResp } from '@/types/auth';

const TOKEN_KEY = 'aiot.admin.token';
const USER_KEY = 'aiot.admin.user';
const HOME_KEY = 'aiot.admin.homeId';

function createSession(overrides: Partial<LoginResp> = {}): LoginResp {
  return {
    token: 'abc123',
    userId: 'user-1',
    globalUserId: 'global-1',
    nickname: '测试用户',
    ...overrides
  };
}

describe('useAuthStore', () => {
  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
  });

  describe('bearerToken 前缀归一化', () => {
    it('无前缀 token 自动添加 Bearer', () => {
      const store = useAuthStore();
      store.setSession(createSession({ token: 'abc123' }));
      expect(store.bearerToken).toBe('Bearer abc123');
    });

    it('已有 Bearer 前缀不重复添加', () => {
      const store = useAuthStore();
      store.setSession(createSession({ token: 'Bearer abc123' }));
      expect(store.bearerToken).toBe('Bearer abc123');
    });
  });

  describe('isAuthenticated', () => {
    it('有 token 时为 true', () => {
      const store = useAuthStore();
      store.setSession(createSession());
      expect(store.isAuthenticated).toBe(true);
    });

    it('无 token 时为 false', () => {
      const store = useAuthStore();
      expect(store.isAuthenticated).toBe(false);
    });
  });

  describe('setSession', () => {
    it('写入 token 到 localStorage', () => {
      const store = useAuthStore();
      store.setSession(createSession());
      expect(localStorage.getItem(TOKEN_KEY)).toBe('abc123');
    });

    it('写入用户信息到 localStorage', () => {
      const store = useAuthStore();
      store.setSession(createSession());
      const saved = JSON.parse(localStorage.getItem(USER_KEY) || '{}');
      expect(saved).toEqual({
        userId: 'user-1',
        globalUserId: 'global-1',
        nickname: '测试用户'
      });
    });
  });

  describe('clearSession', () => {
    it('清空 token、用户与 homeId', () => {
      const store = useAuthStore();
      store.setSession(createSession());
      store.setSelectedHomeId('home-1');
      store.clearSession();

      expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
      expect(localStorage.getItem(USER_KEY)).toBeNull();
      expect(localStorage.getItem(HOME_KEY)).toBeNull();
      expect(store.isAuthenticated).toBe(false);
    });
  });

  describe('setSelectedHomeId', () => {
    it('有值时写入 localStorage 并去除首尾空格', () => {
      const store = useAuthStore();
      store.setSelectedHomeId('  home-1  ');
      expect(store.selectedHomeId).toBe('home-1');
      expect(localStorage.getItem(HOME_KEY)).toBe('home-1');
    });

    it('空值时移除 localStorage 中的 homeId', () => {
      const store = useAuthStore();
      store.setSelectedHomeId('home-1');
      store.setSelectedHomeId('');
      expect(store.selectedHomeId).toBe('');
      expect(localStorage.getItem(HOME_KEY)).toBeNull();
    });
  });
});
