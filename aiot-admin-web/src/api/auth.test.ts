import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import MockAdapter from 'axios-mock-adapter';
import http from '@/api/http';
import { login, register } from './auth';

vi.mock('@/router', () => ({
  default: { push: vi.fn() }
}));

function parseBody(config: { data?: unknown }): Record<string, unknown> {
  return typeof config.data === 'string' ? JSON.parse(config.data) : (config.data as Record<string, unknown>);
}

describe('auth api', () => {
  let mock: MockAdapter;

  beforeEach(() => {
    localStorage.clear();
    setActivePinia(createPinia());
    mock = new MockAdapter(http);
  });

  afterEach(() => {
    mock.restore();
  });

  it('login 返回解包后的 data', async () => {
    mock.onPost('/api/v1/users/login').reply((config) => {
      expect(parseBody(config)).toEqual({ phone: '13800138000', password: 'secret' });
      return [
        200,
        {
          data: {
            token: 'tok',
            userId: 'u1',
            globalUserId: 'g1',
            nickname: 'nick'
          }
        }
      ];
    });

    const resp = await login({ phone: '13800138000', password: 'secret' });

    expect(resp).toEqual({
      token: 'tok',
      userId: 'u1',
      globalUserId: 'g1',
      nickname: 'nick'
    });
    expect(mock.history.post).toHaveLength(1);
    expect(mock.history.post[0].url).toBe('/api/v1/users/login');
  });

  it('register 调用路径与参数正确', async () => {
    mock.onPost('/api/v1/users/register').reply((config) => {
      expect(parseBody(config)).toEqual({
        phone: '13800138000',
        password: 'secret',
        nickname: 'nick'
      });
      return [200, {}];
    });

    await register({ phone: '13800138000', password: 'secret', nickname: 'nick' });

    expect(mock.history.post).toHaveLength(1);
    expect(mock.history.post[0].url).toBe('/api/v1/users/register');
  });
});
