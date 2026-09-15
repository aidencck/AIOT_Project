import axios from 'axios';
import { appEnv } from '@/config/env';
import router from '@/router';
import { useAuthStore } from '@/stores/auth';

const http = axios.create({
  baseURL: appEnv.apiBaseUrl,
  timeout: 15000
});

http.interceptors.request.use((config) => {
  const authStore = useAuthStore();
  if (authStore.bearerToken) {
    config.headers.Authorization = authStore.bearerToken;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const payload = response.data;
    if (payload && typeof payload === 'object' && 'data' in payload) {
      return payload.data;
    }
    return payload;
  },
  async (error) => {
    if (error?.response?.status === 401) {
      const authStore = useAuthStore();
      authStore.clearToken();
      await router.push('/auth/login');
    }
    return Promise.reject(error);
  }
);

export default http;
