import { describe, expect, it } from 'vitest';
import { getErrorMessage } from './error';

describe('getErrorMessage', () => {
  it('优先返回 response.data.message', () => {
    const error = {
      response: { data: { message: '服务端错误' } },
      message: '请求错误'
    };
    expect(getErrorMessage(error)).toBe('服务端错误');
  });

  it('无 response.data.message 时返回 message', () => {
    const error = {
      response: { data: {} },
      message: '网络错误'
    };
    expect(getErrorMessage(error)).toBe('网络错误');
  });

  it('既无 response.data.message 也无 message 时返回 fallback', () => {
    const error = { response: { data: {} } };
    expect(getErrorMessage(error)).toBe('操作失败');
  });

  it('null 回退 fallback', () => {
    expect(getErrorMessage(null)).toBe('操作失败');
  });

  it('undefined 回退 fallback', () => {
    expect(getErrorMessage(undefined)).toBe('操作失败');
  });

  it('非对象回退 fallback', () => {
    expect(getErrorMessage('字符串错误')).toBe('操作失败');
    expect(getErrorMessage(123)).toBe('操作失败');
  });

  it('支持自定义 fallback', () => {
    expect(getErrorMessage(null, '自定义错误')).toBe('自定义错误');
  });
});
