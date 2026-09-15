import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import MockAdapter from 'axios-mock-adapter';
import http from '@/api/http';
import { useAuthStore } from '@/stores/auth';
import type { LoginResp } from '@/types/auth';

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }));

vi.mock('@/router', () => ({
  default: { push: pushMock }
}));

function createSession(overrides: Partial<LoginResp> = {}): LoginResp {
  return {
    token: 'abc123',
    userId: 'user-1',
    globalUserId: 'global-1',
    nickname: '测试用户',
    ...overrides
  };
}

describe('http', () => {
  let mock: MockAdapter;

  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
    mock = new MockAdapter(http);
    pushMock.mockClear();
  });

  afterEach(() => {
    mock.restore();
  });

  describe('请求拦截器', () => {
    it('bearerToken 存在时注入 Authorization 头', async () => {
      const store = useAuthStore();
      store.setSession(createSession());

      let captured: { headers: { Authorization?: string } } | undefined;
      mock.onAny().reply((config) => {
        captured = config as unknown as { headers: { Authorization?: string } };
        return [200, { data: 'ok' }];
      });

      await http.get('/test');

      expect(captured?.headers.Authorization).toBe('Bearer abc123');
    });

    it('无 token 时不注入 Authorization 头', async () => {
      let captured: { headers: { Authorization?: string } } | undefined;
      mock.onAny().reply((config) => {
        captured = config as unknown as { headers: { Authorization?: string } };
        return [200, { data: 'ok' }];
      });

      await http.get('/test');

      expect(captured?.headers.Authorization).toBeUndefined();
    });
  });

  describe('响应拦截器', () => {
    it('对 { data: {...} } 解包返回 payload.data', async () => {
      mock.onGet('/test').reply(200, { data: { hello: 'world' } });

      const result = await http.get('/test');

      expect(result).toEqual({ hello: 'world' });
    });

    it('401 响应触发 clearToken 且调用 router.push', async () => {
      const store = useAuthStore();
      store.setSession(createSession());

      mock.onGet('/test').reply(401);

      await expect(http.get('/test')).rejects.toBeTruthy();

      expect(store.bearerToken).toBe('');
      expect(pushMock).toHaveBeenCalledWith('/auth/login');
    });
  });
});
