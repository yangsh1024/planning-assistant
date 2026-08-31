import { defineStore } from 'pinia';

/** 保存页面初始化期间的登录校验结果，避免未确认身份时闪现对话界面。 */
export const useAuthStore = defineStore('auth', {
  state: () => ({ authenticated: false, loading: true }),
  actions: {
    setAuthenticated(value: boolean) { this.authenticated = value; },
    setLoading(value: boolean) { this.loading = value; }
  }
});
