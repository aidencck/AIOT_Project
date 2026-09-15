// Node 25 内置了实验性的 localStorage 全局对象（一个不含 Storage 方法的空对象），
// 会遮蔽 jsdom 的 localStorage，导致 store 中直接使用 localStorage 时拿到残缺实现。
// 这里在测试环境初始化时注入一个符合 Storage 接口的内存实现。
class MemoryStorage implements Storage {
  private store = new Map<string, string>();

  get length(): number {
    return this.store.size;
  }

  clear(): void {
    this.store.clear();
  }

  getItem(key: string): string | null {
    return this.store.has(key) ? (this.store.get(key) as string) : null;
  }

  key(index: number): string | null {
    return Array.from(this.store.keys())[index] ?? null;
  }

  removeItem(key: string): void {
    this.store.delete(key);
  }

  setItem(key: string, value: string): void {
    this.store.set(key, String(value));
  }
}

const storage = new MemoryStorage();

Object.defineProperty(globalThis, 'localStorage', {
  value: storage,
  configurable: true,
  writable: true
});

try {
  Object.defineProperty(window, 'localStorage', {
    value: storage,
    configurable: true,
    writable: true
  });
} catch {
  // 部分环境下 window.localStorage 不可重定义，忽略即可，store 直接使用全局 localStorage。
}
