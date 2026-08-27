import { defineStore } from 'pinia';

export const useAuthStore = defineStore('auth', {
  state: () => ({ authenticated: false, loading: true }),
  actions: {
    setAuthenticated(value: boolean) { this.authenticated = value; },
    setLoading(value: boolean) { this.loading = value; }
  }
});
